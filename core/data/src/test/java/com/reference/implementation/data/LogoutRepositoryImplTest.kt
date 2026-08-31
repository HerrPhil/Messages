package com.reference.implementation.data

import android.util.Log
import com.reference.implementation.data.manager.AuthSessionManager
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.repositoryimpl.LogoutRepositoryImpl
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutRepositoryImplTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope

    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val authSessionManager: AuthSessionManager = mockk(relaxed = true)
    private val roleManager: RoleManager = mockk(relaxed = true)

    private lateinit var repository: LogoutRepositoryImpl

    @Before
    fun setUp() {
        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)
        testScope = TestScope(testDispatcher)

        // Mock static calls to android.util.Log if logged during session cleanup
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0

        repository = LogoutRepositoryImpl(
            externalScope = testScope,
            ioDispatcher = testDispatcher,
            sessionManager = sessionManager,
            authSessionManager = authSessionManager,
            roleManager = roleManager
        )
    }

    @After
    fun tearDown() {
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `logout triggers standard session cleanup on external scope`() = runTest(testDispatcher) {
        // Execute logout
        repository.logout()

        // Advance virtual time to complete the launched coroutine inside externalScope
        testScheduler.advanceUntilIdle()

        // Verify sequential execution of standard session termination
        coVerifyOrder {
            sessionManager.logout()
            authSessionManager.stopSession()
            roleManager.clear()
        }
    }

    @Test
    fun `forceLogout triggers immediate forced session termination on external scope`() = runTest(testDispatcher) {
        // Execute forceLogout
        repository.forceLogout()

        // Advance virtual time
        testScheduler.advanceUntilIdle()

        // Verify sequential execution of forced session termination
        coVerifyOrder {
            sessionManager.logout()
            authSessionManager.forceStopSession()
            roleManager.clear()
        }
    }
}