package com.reference.implementation.messages.presentation.screens.bulletin

import com.reference.implementation.domain.model.AdminDashboardDomainModel
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.UserDashboardDomainModel
import com.reference.implementation.domain.use_case.FetchNewUserProfileUseCase
import com.reference.implementation.domain.use_case.GetAdminDashboardUseCase
import com.reference.implementation.domain.use_case.GetCachedMessagesUseCase
import com.reference.implementation.domain.use_case.GetUserDashboardUseCase
import com.reference.implementation.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.domain.use_case.LoadAllBulletinsUseCase
import com.reference.implementation.domain.use_case.LoadBulletinUseCase
import com.reference.implementation.domain.use_case.LoadSelectedMessagesUseCase
import com.reference.implementation.domain.use_case.LoginUseCase
import com.reference.implementation.domain.use_case.Resource
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

// 1. Create a handwritten Fake LoadBulletinUseCase
class FakeLoadBulletinUseCase : LoadBulletinUseCase(repo = mockk(relaxed = true)) {
    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    override suspend fun invoke(bulletinId: Int, onRetry: suspend (Int) -> Unit) {
        // Capture the lambda reference exactly when the ViewModel executes it on init
        capturedOnRetry = onRetry
    }
}

// 2. Create a handwritten Fake LoadAllBulletinsUseCase
// Update the Fake to simulate actual operational latency
class FakeLoadAllBulletinsUseCase : LoadAllBulletinsUseCase(repo = mockk(relaxed = true)) {
    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    override suspend fun invoke(onRetry: suspend (Int) -> Unit) {
        capturedOnRetry = onRetry
        // Leave completely blank! Runs instantly in 0ms.
        delay(1000.milliseconds)
    }
}

// 3. Create a handwritten Fake GetAdminDashboardUseCase
// Update the Fake to simulate actual operational latency
class FakeGetAdminDashboardUseCase : GetAdminDashboardUseCase(
    userRepository = mockk(relaxed = true),
    messageRepository = mockk(relaxed = true),
    bulletinRepository = mockk(relaxed = true)
) {
    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    // 💡 THE FIX: Use a hot flow container seeded with Resource.Loading
    // to keep the combine block initialized and listening!
    val mockStream = MutableStateFlow<Resource<AdminDashboardDomainModel>>(Resource.Loading)

    override fun invoke(onRetry: suspend (Int) -> Unit): Flow<Resource<AdminDashboardDomainModel>> {
        capturedOnRetry = onRetry
        return mockStream
    }
}

// 4. Create a handwritten Fake GetCachedMessagesUseCase
// Update the Fake to simulate actual operational latency
class FakeGetCachedMessagesUseCase : GetCachedMessagesUseCase(
    messageCacheRepository = mockk(relaxed = true),
    userPreferencesRepository = mockk(relaxed = true)
) {

    // 💡 THE FIX: Use a hot flow container seeded with Resource.Loading
    // to keep the combine block initialized and listening!
    val mockStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(Resource.Loading)

    override fun invoke(): Flow<Resource<List<MessageDomainModel>>> {
        return mockStream
    }
}


// 5. Create a handwritten Fake LoadAllBulletinsUseCase
// Update the Fake to simulate actual operational latency
class FakeLoadActiveMessagesUseCase : LoadActiveMessagesUseCase(repo = mockk(relaxed = true)) {
    var invocationCount = 0

    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    override suspend fun invoke(onRetry: suspend (Int) -> Unit) {
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
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    override suspend fun invoke(userId: Int, onRetry: suspend (Int) -> Unit) {
        invocationCount++
        capturedOnRetry = onRetry
        // Leave completely blank! Runs instantly in 0ms.
        delay(1000.milliseconds)
    }
}

// 7. Create a handwritten Fake GetUserDashboardUseCase
// Update the Fake to simulate actual operational latency
class FakeGetUserDashboardUseCase : GetUserDashboardUseCase(
    userRepository = mockk(relaxed = true),
    messageRepository = mockk(relaxed = true),
    roleRepository = mockk(relaxed = true),
    permissionRepository = mockk(relaxed = true),
) {
    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    // 💡 THE FIX: Use a hot flow container seeded with Resource.Loading
    // to keep the combine block initialized and listening!
    val mockStream = MutableStateFlow<Resource<UserDashboardDomainModel>>(Resource.Loading)

    override fun invoke(onRetry: suspend (Int) -> Unit): Flow<Resource<UserDashboardDomainModel>> {
        capturedOnRetry = onRetry
        return mockStream
    }
}

// 8. Create a handwritten Fake LoginUseCase
// Update the Fake to simulate actual operational latency
class FakeLoginUseCase : LoginUseCase(
    repo = mockk(relaxed = true)
) {
    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    override suspend fun invoke(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): Resource<LoginUserDomainModel> {
        capturedOnRetry = onRetry
        return Resource.Loading
    }
}

// 9. Create a handwritten Fake LoginUseCase
// Update the Fake to simulate actual operational latency
class FakeFetchNewUserProfileUseCase : FetchNewUserProfileUseCase(
    roleRepo = mockk(relaxed = true)
) {
    // A clean, type-safe property to catch the lambda reference when called
    var capturedOnRetry: (suspend (Int) -> Unit)? = null

    override suspend fun invoke(
        loginUser: LoginUserDomainModel,
        onRetry: suspend (Int) -> Unit
    ): Resource<Unit> {
        capturedOnRetry = onRetry
        return Resource.Loading
    }
}