package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext

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
            onRetry(attempt + 1) // add 1 for readability; repeat() is zero-based
            // 1. Only retry on IO/Network exceptions
            // Do not retry on logic errors (like 401 Unauthorized)
            Audit.createInstance()
                .writeLog("Retry on IO exception: ".plus(io.message ?: "no message"))
        } catch (http: HttpException) {
            val code = http.code()
            if (code >= 500) {
                onRetry(attempt + 1) // add 1 for readability; repeat() is zero-based
                // 1. Only retry on IO/Network exceptions
                // Do not retry on logic errors (like 401 Unauthorized)
                Audit.createInstance()
                    .writeLog("Retry on http 5** exception: ".plus(http.message ?: "no message"))
            }
        }

        // 2. CRITICAL: check if the CoroutineScope is still alive
        // If the user closed the screen, we stop retrying immediately.
        // block from withContext() of repository that has currentContext() within launch block
        currentContext().ensureActive()

        // 3. Exponential Backoff
        delay(currentDelay)
        // Note: delay() is cancellation-aware.
        // If the coroutine canceled during this sleep,
        // then it throws a CancellationException immediately

        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }

    // Final attempt: if this fails, then the exception propagates up
    return block()
}


private suspend fun currentContext() = coroutineContext

internal fun auditLogTimestamp(): String {
    // Get the current date and time
    val now = LocalDateTime.now()

    // Define the format pattern (e.g., yyyy-MM-dd HH:mm:ss)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Format the timestamp
    return now.format(formatter)
}
