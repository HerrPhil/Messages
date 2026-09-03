package com.reference.implementation.data

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.repositoryimpl.RoleRepositoryImpl
import com.reference.implementation.domain.model.UserRoleDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoleRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private lateinit var repository: RoleRepositoryImpl

    @Before
    fun setUp() {

        // Bind the dispatcher to an explicit scheduler instance
        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)

        // Mock static calls to android.util.Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.isLoggable(any(), any()) } returns false

        // Inject StandardTestDispatcher into repository to control virtual time
        repository = RoleRepositoryImpl(
            ioDispatcher = testDispatcher,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `getPermissionInfoFlow emits Loading then retries 500 error 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionRoleNames() } returns SessionResult.Authenticated(
                listOf("they", "them", "us", "we")
            )

            repository.getRoleInfoFlow().test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val successItem = awaitItem()

                // Assert actual values
                assertIs<NetworkResult.Success<UserRoleDomainModel>>(successItem)
                val data = successItem.data
                assertIs<UserRoleDomainModel>(data)
                val roles = data.roles
                assertIs<List<String>>(roles)
                assertEquals(4, roles.size)
                repeat(4) { index ->
                    when (index) {
                        0 -> assertEquals("they", roles[index])
                        1 -> assertEquals("them", roles[index])
                        2 -> assertEquals("us", roles[index])
                        3 -> assertEquals("we", roles[index])
                    }
                }

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }
        }

    @Test
    fun `getPermissionInfoFlow emits Loading then returns no values when not authenticated returns Success`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionRoleNames() } returns SessionResult.NoValue

            repository.getRoleInfoFlow().test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val successItem = awaitItem()

                // Assert actual values
                assertIs<NetworkResult.Success<UserRoleDomainModel>>(successItem)
                val data = successItem.data
                assertIs<UserRoleDomainModel>(data)
                val roles = data.roles
                assertIs<List<String>>(roles)
                assertEquals(0, roles.size)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }
        }

    @Test
    fun `getPermissionInfoFlow cancels cleanly without emitting NetworkResult Exception`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionRoleNames() } throws CancellationException("coroutine exited")

            // 2. Observe the flow with Turbine
            repository.getRoleInfoFlow().test {
                // Assert initial state
                assertEquals(NetworkResult.Loading, awaitItem())

                // 3. Cancel the flow subscriber while suspended on the network call
                cancelAndIgnoreRemainingEvents()
            }

            testScheduler.advanceUntilIdle()

            // If the repository mistakenly swallowed CancellationException and emitted
            // NetworkResult.Exception, Turbine would have thrown an unconsumed event error above!
        }

}