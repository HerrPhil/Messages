package com.reference.implementation.data


import android.util.Log
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.manager.AuthSessionManager
import com.reference.implementation.data.manager.RefreshTokenManager
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.UserRoleState
import com.reference.implementation.data.repositoryimpl.LoginRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class LoginRepositoryImplTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService

    private val accessTokenManager: AccessTokenManager = mockk(relaxed = true)
    private val refreshTokenManager: RefreshTokenManager = mockk(relaxed = true)
    private val authSessionManager: AuthSessionManager = mockk(relaxed = true)
    private val roleManager: RoleManager = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private lateinit var repository: LoginRepositoryImpl
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

        repository = LoginRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = apiService,
            accessTokenManager = accessTokenManager,
            refreshTokenManager = refreshTokenManager,
            authSessionManager = authSessionManager,
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
    fun `login success saves tokens, updates session state, and detects Administrator role`() =
        runTest(testDispatcher) {

            // 1. Enqueue Login Response
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                    {
                        "accessToken": "access_token_123",
                        "refreshToken": "refresh_token_abc",
                        "user": {
                            "id": 1,
                            "email": "admin@example.com",
                            "name": "Admin User",
                            "age": 56
                        }
                    }
                    """.trimIndent()
                    )
            )

            // 2. Enqueue getRoles Response (User is an Administrator)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                    [
                        {
                            "id": 1,
                            "name": "System Administrator",
                            "targetUserId": 1,
                            "permissions": [],
                            "userId": 1
                        },
                        {
                            "id": 2,
                            "name": "Average User",
                            "targetUserId": 3,
                            "permissions": [],
                            "userId": 1
                        }
                    ]
                    """.trimIndent()
                    )
            )

            // 3. Perform Login
            val result = repository.login("admin@example.com", "password123", onRetry = {})

            // 4. Assert Success & Domain Model
            assertIs<NetworkResult.Success<*>>(result)

            // 5. Verify Token Operations & State Updates
            coVerifyOrder {
                roleManager.updateRole(UserRoleState.Loading)
                accessTokenManager.saveToken("access_token_123")
                refreshTokenManager.saveToken("refresh_token_abc")
                roleManager.updateRole(UserRoleState.Administrator)
                sessionManager.updateSession(any(), any())
                authSessionManager.startSession()
            }

            assertEquals(2, mockWebServer.requestCount)
        }

    @Test
    fun `login success assigns RegularUser state when System Administrator role is absent`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                    {
                        "accessToken": "acc",
                        "refreshToken": "ref",
                        "user": {
                            "id": 3,
                            "email": "user@test.com",
                            "name": "Standard User",
                            "age": 56
                        }
                    }
                    """.trimIndent()
                    )
            )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                    [
                        {
                            "id": 2,
                            "name": "Standard User",
                            "targetUserId": 3,
                            "permissions": [],
                            "userId": 1
                        }
                    ]
                    """.trimIndent()
                    )
            )

            val result = repository.login("user@test.com", "password123", onRetry = {})

            assertIs<NetworkResult.Success<*>>(result)

            // Verify RegularUser role mapping
            coVerify { roleManager.updateRole(UserRoleState.RegularUser) }
        }

    @Test
    fun `login retries on 500 server error and succeeds on second attempt`() =
        runTest(testDispatcher) {
            // Attempt 1 fails with 500, Attempt 2 succeeds
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                    {
                        "accessToken": "acc",
                        "refreshToken": "ref",
                        "user": {
                            "id": 3,
                            "email": "retry@test.com",
                            "name": "Retry User",
                            "age": 56
                        }
                    }
                    """.trimIndent()
                    )
            )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                    [
                        {
                            "id": 2,
                            "name": "User",
                            "targetUserId": 3,
                            "permissions": [],
                            "userId": 1
                        }
                    ]
                    """.trimIndent()
                    )
            )

            var retryAttempt = 0

            val result = repository.login("retry@test.com", "password123", onRetry = { attempt ->
                retryAttempt = attempt
            })

            assertIs<NetworkResult.Success<*>>(result)
            assertEquals(1, retryAttempt)
            assertEquals(3, mockWebServer.requestCount) // 2 for login + 1 for roles
        }

    @Test
    fun `login retries on IO error and succeeds on second attempt`() = runTest(testDispatcher) {
        // Attempt 1 fails with 500, Attempt 2 succeeds
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "accessToken": "acc",
                        "refreshToken": "ref",
                        "user": {
                            "id": 3,
                            "email": "retry@test.com",
                            "name": "Retry User",
                            "age": 56
                        }
                    }
                    """.trimIndent()
                )
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                        {
                            "id": 2,
                            "name": "User",
                            "targetUserId": 3,
                            "permissions": [],
                            "userId": 1
                        }
                    ]
                    """.trimIndent()
                )
        )

        var retryAttempt = 0

        val result = repository.login("retry@test.com", "password123", onRetry = { attempt ->
            retryAttempt = attempt
        })

        assertIs<NetworkResult.Success<*>>(result)
        assertEquals(1, retryAttempt)
        assertEquals(3, mockWebServer.requestCount) // 2 for login + 1 for roles
    }

    @Test
    fun `login fast fails on 401 Unauthorized without retrying`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        var retryAttempt = 0

        val result = repository.login("wrong@test.com", "badpassword", onRetry = { attempt ->
            retryAttempt = attempt
        })

        assertIs<NetworkResult.Error>(result)
        assertEquals(401, result.code)
        assertEquals(0, retryAttempt)
        assertEquals(1, mockWebServer.requestCount)

        // Assert token managers were NEVER called on error
        coVerify(exactly = 0) { accessTokenManager.saveToken(any()) }
        coVerify(exactly = 0) { authSessionManager.startSession() }
    }
}