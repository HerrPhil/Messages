package com.reference.implementation.messages.presentation.screens.bulletin

import com.reference.implementation.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.domain.use_case.LoadAllBulletinsUseCase
import com.reference.implementation.domain.use_case.LoadSelectedMessagesUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.time.Duration.Companion.milliseconds

/**
 * Clean reusable Test Rule tailored for state collection testing.
 * Leverages UnconfinedTestDispatcher so Flow state transitions stream seamlessly
 * into assertions without manual clock/queue pumping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }

}

// 2. Create a handwritten Fake LoadAllBulletinsUseCase
// Update the Fake to simulate actual operational latency
class FakeLoadAllBulletinsUseCase : LoadAllBulletinsUseCase(repo = mockk(relaxed = true)) {
    var invocationCount = 0

    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: ((Int) -> Unit)? = null

    override suspend fun invoke(onRetry: (Int) -> Unit) {
        invocationCount++
        capturedOnRetry = onRetry
        // Leave completely blank! Runs instantly in 0ms.
        delay(1000.milliseconds)
    }
}


// 5. Create a handwritten Fake LoadAllBulletinsUseCase
// Update the Fake to simulate actual operational latency
class FakeLoadActiveMessagesUseCase : LoadActiveMessagesUseCase(repo = mockk(relaxed = true)) {
    var invocationCount = 0

    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: ((Int) -> Unit)? = null

    override suspend fun invoke(onRetry: (Int) -> Unit) {
        invocationCount++
        capturedOnRetry = onRetry
        // Leave completely blank! Runs instantly in 0ms.
        delay(1000.milliseconds)
    }
}

// 6. Create a handwritten Fake LoadAllBulletinsUseCase
// Update the Fake to simulate actual operational latency
class FakeLoadSelectedMessagesUseCase : LoadSelectedMessagesUseCase(repo = mockk(relaxed = true)) {
    var invocationCount = 0

    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: ((Int) -> Unit)? = null

    override suspend fun invoke(userId: Int, onRetry: (Int) -> Unit) {
        invocationCount++
        capturedOnRetry = onRetry
        // Leave completely blank! Runs instantly in 0ms.
        delay(1000.milliseconds)
    }
}

