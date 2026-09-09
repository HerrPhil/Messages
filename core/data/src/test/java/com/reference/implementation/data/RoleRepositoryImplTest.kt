package com.reference.implementation.data

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.RoleDto
import com.reference.implementation.data.dtos.UserDto
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.manager.UserRoleState
import com.reference.implementation.data.repositoryimpl.RoleRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.model.UserRoleDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class RoleRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var repository: RoleRepositoryImpl
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val roleManager: RoleManager = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

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

        mockWebServer = MockWebServer()
        mockWebServer.start()

        val contentType = "application/json".toMediaType()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)

        // Inject StandardTestDispatcher into repository to control virtual time
        repository = RoleRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = apiService,
            roleManager = roleManager,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `getRoleInfoFlow emits Loading then retries 500 error 2x and recovers to return Success when API returns HTTP 200`() =
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
    fun `getRoleInfoFlow emits Loading then returns no values when not authenticated returns Success`() =
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
    fun `getRoleInfoFlow cancels cleanly without emitting NetworkResult Exception`() =
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


    @Test
    fun `configureUserProfile emits Loading then retries 500 error 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleRolesJsonWithAdmin())
            )

            var retryAttempt = 0

            val result = repository.configureUserProfile(
                loginUser = createSampleLoginUserDomainModel(),
                onRetry = { attempt -> retryAttempt = attempt }
            )

            assertIs<NetworkResult.Success<*>>(result)
            assertEquals(3, retryAttempt)
            assertEquals(3, mockWebServer.requestCount) // 2 for 500, 1 for login
        }

    @Test
    fun `configureUserProfile retries on IO error and succeeds on second attempt with Administrator Role`() =
        runTest(testDispatcher) {

            // Attempt 1 fails with 500, Attempt 2 succeeds
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(createSampleRolesJsonWithAdmin())
            )

            var retryAttempt = 0

            val result = repository.configureUserProfile(
                loginUser = createSampleLoginUserDomainModel(),
                onRetry = { attempt -> retryAttempt = attempt }
            )

            assertIs<NetworkResult.Success<*>>(result)
            assertEquals(1, retryAttempt)
            assertEquals(2, mockWebServer.requestCount) // 2 for 500, 1 for login

            coVerify(exactly = 1) { roleManager.updateRole(eq(UserRoleState.Loading)) }
            coVerify(exactly = 1) { roleManager.updateRole(eq(UserRoleState.Administrator)) }

            val userSlot = slot<UserDto>()
            val rolesSlot = slot<List<RoleDto>>()

            coVerify(exactly = 1) {
                sessionManager.updateSession(
                    newUserDto = capture(userSlot),
                    newRoles = capture(rolesSlot)
                )
            }

            // Assert on captured contents
            assertEquals(2, rolesSlot.captured.size)
            assertEquals("System Administrator", rolesSlot.captured.first().name)
        }

    @Test
    fun `configureUserProfile retries on IO error and succeeds on second attempt with Regular User Role`() =
        runTest(testDispatcher) {

            // Attempt 1 fails with 500, Attempt 2 succeeds
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(createSampleRolesJsonWithoutAdmin())
            )

            var retryAttempt = 0

            val result = repository.configureUserProfile(
                loginUser = createSampleLoginUserDomainModel(),
                onRetry = { attempt -> retryAttempt = attempt }
            )

            assertIs<NetworkResult.Success<*>>(result)
            assertEquals(1, retryAttempt)
            assertEquals(2, mockWebServer.requestCount) // 2 for 500, 1 for login

            coVerify(exactly = 1) { roleManager.updateRole(eq(UserRoleState.Loading)) }
            coVerify(exactly = 1) { roleManager.updateRole(eq(UserRoleState.RegularUser)) }

            val userSlot = slot<UserDto>()
            val rolesSlot = slot<List<RoleDto>>()

            coVerify(exactly = 1) {
                sessionManager.updateSession(
                    newUserDto = capture(userSlot),
                    newRoles = capture(rolesSlot)
                )
            }

            // Assert on captured contents
            assertEquals(1, rolesSlot.captured.size)
            assertEquals("Average User", rolesSlot.captured.first().name)
        }

    @Test
    fun `configureUserProfile fast fails on 401 Unauthorized without retrying`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            var retryAttempt = 0

            val result = repository.configureUserProfile(
                loginUser = createSampleLoginUserDomainModel(),
                onRetry = { attempt -> retryAttempt = attempt }
            )
            assertIs<NetworkResult.Error>(result)
            assertEquals(401, result.code)
            assertEquals(0, retryAttempt)
            assertEquals(1, mockWebServer.requestCount)

            // Assert token managers were NEVER called on error
            coVerify(exactly = 1) { roleManager.updateRole(eq(UserRoleState.Loading)) }
            coVerify(exactly = 0) { sessionManager.updateSession(any(), any()) }
        }

    @Test
    fun `configureUserProfile fast fails on 503 system unavailable with retry 3x`() =
        runTest(testDispatcher) {
            repeat(3) {
                mockWebServer.enqueue(
                    MockResponse().setResponseCode(503).setBody("Roles are unavailable")
                )
            }

            var retryAttempt = 0

            val result = repository.configureUserProfile(
                loginUser = createSampleLoginUserDomainModel(),
                onRetry = { attempt -> retryAttempt = attempt }
            )
            assertIs<NetworkResult.Exception>(result)
            val message = result.e.message
            assertEquals("HTTP 503 Server Error", message)
            assertEquals(3, retryAttempt)
            assertEquals(3, mockWebServer.requestCount)

            // Assert token managers were NEVER called on error
            coVerify(exactly = 1) { roleManager.updateRole(eq(UserRoleState.Loading)) }
            coVerify(exactly = 0) { sessionManager.updateSession(any(), any()) }
        }

    @Test
    fun `configureUserProfile cancels cleanly without emitting NetworkResult Exception`() {
        try {
            runTest(testDispatcher) {

                coEvery { roleManager.updateRole(any()) } throws CancellationException("coroutine exited")

                repository.configureUserProfile(
                    loginUser = createSampleLoginUserDomainModel(),
                    onRetry = {}
                )
            }
            fail("something ate the CancellationException")
        } catch (e: CancellationException) {
            assertEquals("coroutine exited", e.message)
        }
    }

// #############################################################################################
// #############################################################################################
// #############################################################################################
// #############################################################################################

    private fun createSampleAdminRoleDto(): RoleDto =
        RoleDto(
            id = 1,
            name = "System Administrator",
            targetUserId = 1, // admin user
            permissions = emptyList(),
            userId = 1 // admin user owns the role
        )

    private fun createSampleAverageUserRoleDto(): RoleDto =
        RoleDto(
            id = 2,
            name = "Average User",
            targetUserId = 3, // non-admin user
            permissions = emptyList(),
            userId = 1 // admin user owns the role
        )

    private fun createSampleRolesJsonWithAdmin(): String =
        json.encodeToString(
            mutableListOf(
                createSampleAdminRoleDto(),
                createSampleAverageUserRoleDto()
            )
        )

    private fun createSampleRolesJsonWithoutAdmin(): String =
        json.encodeToString(
            mutableListOf(
                createSampleAverageUserRoleDto()
            )
        )

    private fun createSampleLoginUserDomainModel(): LoginUserDomainModel =
        LoginUserDomainModel(
            id = 3,
            name = "test user",
            email = "test.user@learn.com",
            age = 56
        )

}