package com.reference.implementation.data

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.UserDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.repositoryimpl.UserRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.awaits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.io.InvalidObjectException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private lateinit var repository: UserRepositoryImpl
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
        mockWebServer.start() // automated tests should always use port 0 to avoid flakiness

        val contentType = "application/json".toMediaType()

        api = Retrofit.Builder()
            // The url() function implicitly starts the mockWebServer, if not started.
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)

        // Inject StandardTestDispatcher into repository to control virtual time
        repository = UserRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = api,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        mockWebServer.close()
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `getPermissionInfoFlow emits Loading and when Authenticated return Success`() =
        runTest(testDispatcher) {

            val expectedUserName = "Bob Squarepants"
            val expectedUserEmail = "b.sqrpnts@test.com"
            val expectedUserID = 9999

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } returns SessionResult.Authenticated(
                expectedUserName
            )
            coEvery { sessionManager.getSessionUserEmail() } returns SessionResult.Authenticated(
                expectedUserEmail
            )
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(
                expectedUserID
            )

            repository.getUserInfoFlow().test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<LoginUserDomainModel>>(successItem)
                val data = successItem.data
                assertIs<LoginUserDomainModel>(data)
                val userName = data.name
                val userEmail = data.email
                val userID = data.id
                assertIs<String>(userName)
                assertIs<String>(userEmail)
                assertIs<Int>(userID)
                assertEquals(expectedUserName, userName)
                assertEquals(expectedUserEmail, userEmail)
                assertEquals(expectedUserID, userID)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }
        }

    @Test
    fun `getPermissionInfoFlow emits Loading and when Not Authenticated return Success`() =
        runTest(testDispatcher) {

            val expectedUserName = "no name"
            val expectedUserEmail = "no email"
            val expectedUserID = -1

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } returns SessionResult.NoValue
            coEvery { sessionManager.getSessionUserEmail() } returns SessionResult.NoValue
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.NoValue

            repository.getUserInfoFlow().test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<LoginUserDomainModel>>(successItem)
                val data = successItem.data
                assertIs<LoginUserDomainModel>(data)
                val userName = data.name
                val userEmail = data.email
                val userID = data.id
                assertIs<String>(userName)
                assertIs<String>(userEmail)
                assertIs<Int>(userID)
                assertEquals(expectedUserName, userName)
                assertEquals(expectedUserEmail, userEmail)
                assertEquals(expectedUserID, userID)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }
        }

    @Test
    fun `getPermissionInfoFlow emits Loading and when throws CancellationException gracefully cancels`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } throws CancellationException("ending")
            coEvery { sessionManager.getSessionUserEmail() } throws CancellationException("ending")
            coEvery { sessionManager.getSessionUserId() } throws CancellationException("ending")

            repository.getUserInfoFlow().test {
                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 4. Advance virtual time to drain all delay() calls inside retryIO
            testScheduler.advanceUntilIdle()
        }

    @Test
    fun `getPermissionInfoFlow emits Loading and when throws Exception gracefully cancels`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } throws InvalidObjectException("bad value")
            coEvery { sessionManager.getSessionUserEmail() } throws InvalidObjectException("bad value")
            coEvery { sessionManager.getSessionUserId() } throws InvalidObjectException("bad value")

            repository.getUserInfoFlow().test {
                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Assert fast failure
                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)
                assertEquals("bad value", errorItem.e.message)

                cancelAndIgnoreRemainingEvents()
            }
        }

// #############################################################################################
// #############################################################################################
// #############################################################################################
// #############################################################################################

    @Test
    fun `getUserCount emits Loading then retries 500 error 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleUserDtos())
            )

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<Int>>(successItem)
                val actualUserCount = successItem.data
                assertIs<Int>(actualUserCount)
                assertEquals(3, actualUserCount)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 2 500 responses, 1 200 response")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `handles malformed JSON string`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"oranges":789}""") // malformed JSON string
            )

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                assertIs<NetworkResult.Exception>(errorItem)
                assertNotNull(errorItem.e.message)
                val error = errorItem.e.message
                assertTrue(
                    actual =
                        error?.startsWith("Unexpected JSON token at offset 0")
                            ?: false
                )

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "NO retries encountered")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `handles empty body of user count`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("")
            )

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                // JsonDecodingException handled as NetworkResult.Exception
                assertIs<NetworkResult.Exception>(errorItem)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "NO retries encountered")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `handles no body of user count`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                // no Body()
            )

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                // JsonDecodingException handled as NetworkResult.Exception
                assertIs<NetworkResult.Exception>(errorItem)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "NO retries encountered")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `getUserCount emits Loading then returns Error when API returns HTTP 500 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP 500 responses)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                // JsonDecodingException handled as NetworkResult.Exception
                assertIs<NetworkResult.Exception>(errorItem)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "NO retries encountered")
                assertEquals(3, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `getUserCount emits Loading then retries IOException 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 IOExceptions then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleUserDtos())
            )

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val successItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                assertIs<NetworkResult.Success<Int>>(successItem)
                val actualUserCount = successItem.data
                assertIs<Int>(actualUserCount)
                assertEquals(3, actualUserCount)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "2 IOException retries, 1 HTTP 200 encountered")
                assertEquals(3, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `getUserCount emits Loading then retries IOException and returns Exception when API returns IOException 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP IOExceptions)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                assertIs<NetworkResult.Exception>(errorItem)
                assertIs<IOException>(errorItem.e)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 IOException retries")
                assertEquals(3, mockWebServer.requestCount, "called api 3x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `getUserCount fails fast on HTTP 401 without retrying`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., HTTP 401)
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                assertIs<NetworkResult.Error>(errorItem) // Caught by repository's try/catch block
                assertEquals(401, errorItem.code)
                assertEquals("Client Error", errorItem.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "1 HTTP 401 encountered")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `getUserCount fails fast on HTTP 403 without retrying`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., HTTP 403)
            mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                assertIs<NetworkResult.Error>(errorItem) // Caught by repository's try/catch block
                assertEquals(403, errorItem.code)
                assertEquals("Client Error", errorItem.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "1 HTTP 403 encountered")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `getUserCount fails fast when service is unavailable`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP 503 responses)
            repeat(3) {
                mockWebServer.enqueue(
                    MockResponse().setResponseCode(503).setBody("User service is unavailable")
                )
            }

            var retryCount = 0

            repository.getUserCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // 5. Assert UI state and total attempt counts
                assertIs<NetworkResult.Exception>(errorItem)
                assertEquals("HTTP 503 Server Error", errorItem.e.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 HTTP 503 encountered")
                assertEquals(3, mockWebServer.requestCount, "called api 3x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/users", recordedRequest?.path)

        }

    @Test
    fun `getUserCount cancels cleanly without emitting NetworkResult Exception`() =
        runTest(testDispatcher) {
            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(5, TimeUnit.SECONDS) // indicate cancellation (timeout)
                    .setBody("""[{"id":"1"}]""")
            )

            // 2. Observe the flow with Turbine
            repository.getUserCount(onRetry = {}).test {
                // Assert initial state
                assertEquals(NetworkResult.Loading, awaitItem())

                // 3. Cancel the flow subscriber while suspended on the network call
                cancelAndIgnoreRemainingEvents()
            }

            testScheduler.advanceUntilIdle()

            // If the repository mistakenly swallowed CancellationException and emitted
            // NetworkResult.Exception, Turbine would have thrown an unconsumed event error above!
        }


// #############################################################################################
// #############################################################################################
// #############################################################################################
// #############################################################################################

    @Test
    fun `getUsers updates user cache with Success`() =
        runTest(testDispatcher) {

            // 2. Enqueue MockWebServer response for targetUserId = 42
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleUserDtos())
            )

            var retryCount = 0


            // 3. Collect StateFlow stream via Turbine while executing refresh
            repository.getUsers().test {
                // Initial State before refresh is NetworkResult.Loading
                assertEquals(NetworkResult.Loading, awaitItem())

                // Trigger refresh in backgroundScope or directly inside test
                repository.loadAllUsers(
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val successItem = awaitItem()

                // Assert StateFlow updates to Success with mapped domain model
                assertIs<NetworkResult.Success<List<LoginUserDomainModel>>>(successItem)
                assertEquals(3, successItem.data.size)
                assertEquals(101, successItem.data.first().id)
                assertEquals("email.1@test.com", successItem.data.first().email)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when user authenticated")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()

            }

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/users", recordedRequest.path)

        }

    @Test
    fun `getUsers service is up but user records found then update user cache with Success and no users`() =
        runTest(testDispatcher) {

            // 2. Enqueue MockWebServer response
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]")
            )

            var retryCount = 0


            // 3. Collect StateFlow stream via Turbine while executing refresh
            repository.getUsers().test {
                // Initial State before refresh is NetworkResult.Loading
                assertEquals(NetworkResult.Loading, awaitItem())

                // Trigger refresh in backgroundScope or directly inside test
                repository.loadAllUsers(
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val successItem = awaitItem()

                // Assert StateFlow updates to Success with mapped domain model
                assertIs<NetworkResult.Success<List<LoginUserDomainModel>>>(successItem)
                assertEquals(0, successItem.data.size)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when user authenticated")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()

            }

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/users", recordedRequest.path)

        }

    @Test
    fun `getUsers fails update cache when messages not found`() =
        runTest(testDispatcher) {

            // 2. Enqueue MockWebServer response - users API not found
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("Not Found")
            )

            var retryCount = 0

            // 3. Collect StateFlow stream via Turbine while executing refresh
            repository.getUsers().test {
                // Initial State before refresh is NetworkResult.Loading
                assertEquals(NetworkResult.Loading, awaitItem())

                // Trigger refresh in backgroundScope or directly inside test
                repository.loadAllUsers(
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // Assert StateFlow updates to Success with mapped domain model
                assertIs<NetworkResult.Error>(errorItem)
                assertEquals(404, errorItem.code)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when user authenticated")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()

            }

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/users", recordedRequest.path)

        }

    @Test
    fun `getUsers fails update cache when users service is unavailable`() =
        runTest(testDispatcher) {

            // Enqueue 3 error responses so attempts 1, 2, and 3 get instant responses
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(503))
            }

            var retryCount = 0

            // 3. Collect StateFlow stream via Turbine while executing refresh
            repository.getUsers().test {
                // Initial State before refresh is NetworkResult.Loading
                assertEquals(NetworkResult.Loading, awaitItem())

                // Trigger refresh in backgroundScope or directly inside test
                repository.loadAllUsers(
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()

                // Assert StateFlow updates to Success with mapped domain model
                assertIs<NetworkResult.Exception>(errorItem)
                assertEquals("HTTP 503 Server Error", errorItem.e.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 retries when users service is not available")
                assertEquals(3, mockWebServer.requestCount, "called api 3x")

                cancelAndIgnoreRemainingEvents()

            }

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/users", recordedRequest.path)

        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getUsers cancels cleanly without emitting NetworkResult Exception`() {
        runTest(testDispatcher) {

            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleUserDtos())
            )

            // 2. Launch the operation with timeout in a deferred context
            val deferredResult = async {
                try {
                    withTimeout(200) {
                        // 2. Observe the flow with Turbine
                        repository.getUsers().test {

                            // Assert initial state
                            assertEquals(NetworkResult.Loading, awaitItem())

                            // Trigger refresh in backgroundScope or directly inside test
                            repository.loadAllUsers(onRetry = {})
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Result.failure<TimeoutCancellationException>(e)
                }
            }

            // 3. Advance virtual time past the timeout
            testScheduler.advanceTimeBy(200)

            // 4. Execute pending tasks and verify the exception
            testScheduler.runCurrent()

            val result = deferredResult.await()
            println("test")
            assertTrue((result as Result<*>).isFailure)
            assertTrue(result.exceptionOrNull() is TimeoutCancellationException)

            testScheduler.advanceUntilIdle()

            // If the repository mistakenly swallowed CancellationException and emitted
            // NetworkResult.Exception, Turbine would have thrown an unconsumed event error above!
        }
    }

    private fun createSampleUserDto(id: Int, email: String, name: String, age: Int): UserDto {
        return UserDto(
            id = id,
            email = email,
            name = name,
            age = age
        )
    }

    // Fabricate the list of users to get.
    // Notice by making the Json instance create the raw JSON string
    // that it relieves us from balancing array brackets, squiggly braces, and commas
    // of a traditional raw """[...]""" list.
    private fun createSampleUserDtos(): String =
        json.encodeToString(
            MutableList(3) { index ->
                createSampleUserDto(
                    id = 100 + index + 1,
                    email = "email.${index + 1}@test.com",
                    name = "name.${index + 1}",
                    age = 22
                )
            }
        )

}