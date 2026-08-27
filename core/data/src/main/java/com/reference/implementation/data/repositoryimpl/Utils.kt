package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal suspend fun <T> retryIO(
    times: Int = 3,
    initialDelay: Long = 100, // 0.1 second
    maxDelay: Long = 1000, // 1 second
    factor: Double = 2.0,
    onRetry: suspend (Int) -> Unit,
    block: suspend () -> T
): T {

    var currentDelay = initialDelay
    repeat(times - 1) { attempt ->
        try {
            // Attempt the actual work
            return block()
        } catch (io: IOException) {

            // CRITICAL: If the IOException was caused by coroutine cancellation,
            // throw a CancellationException NOW!

            // ensureActive() is a function in Kotlin Coroutines used to
            // enable cooperative cancellation for long-running or non-suspending tasks.
            // It checks the state of the current Job within the coroutine's context and
            // immediately throws a CancellationException if the job is no longer active
            // (i.e., cancelled, cancelling, or completed).
            currentCoroutineContext().ensureActive()

            onRetry(attempt + 1) // add 1 for readability; repeat() is zero-based
            // 1. Only retry on IO/Network exceptions
            auditLog("Retry on IO exception: ".plus(io.message ?: "no message"))
        } catch (http: HttpException) {
            val code = http.code()
            if (code >= 500) {
                onRetry(attempt + 1) // add 1 for readability; repeat() is zero-based
                // 1.a. Do not retry on logic errors (like 401 Unauthorized)
                auditLog("Retry on http 5** exception: ".plus(http.message ?: "no message"))
            } else {
                auditLog("Retry on http 4** exception: ".plus(http.message ?: "no message"))
                throw http // Re-throw 4xx client errors
            }
        }

        // 2. CRITICAL: check if the CoroutineScope is still alive.
        // If the user closed the screen, we stop retrying immediately.
        // block from withContext() of repository that has currentContext() within launch block
        currentContext().ensureActive()

        // 3. Exponential Backoff
        // Note: delay() is cancellation-aware.
        // If the coroutine cancelled during this sleep,
        // then it throws a CancellationException immediately
        delay(currentDelay)

        // Calculate the next exponential backoff value.
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }

    // Final attempt: if this fails, then the exception propagates up
    auditLog("Final retry")
    onRetry(3)
    return block()
}


private suspend fun currentContext() = currentCoroutineContext()
//private suspend fun currentContext() = coroutineContext

internal fun auditLogTimestamp(): String {
    // Get the current date and time
    val now = LocalDateTime.now()

    // Define the format pattern (e.g., yyyy-MM-dd HH:mm:ss)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Format the timestamp
    return now.format(formatter)
}