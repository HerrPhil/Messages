package com.reference.implementation.data

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.PermissionDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.repositoryimpl.PermissionRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.UserPermissionDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PermissionRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private lateinit var repository: PermissionRepositoryImpl
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
        repository = PermissionRepositoryImpl(
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
    fun `getPermissionInfoFlow emits Loading then retries 500 error 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSamplePermissionDtos())
            )

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<UserPermissionDomainModel>>(successItem)
                val data = successItem.data
                assertIs<UserPermissionDomainModel>(data)
                val permissions = data.permissions
                assertIs<List<String>>(permissions)
                repeat(3) { task ->
                    assertContains(permissions, "work task #${task + 1}")
                }

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 2 500 responses, 1 200 response")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)

        }

    @Test
    fun `getPermissionInfoFlow fetches no permissions when not authenticated to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Mock session returns NoValue when not authenticated
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.NoValue

            // 1. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]")
            )

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<UserPermissionDomainModel>>(successItem)
                val data = successItem.data
                assertIs<UserPermissionDomainModel>(data)
                val permissions = data.permissions
                assertIs<List<String>>(permissions)
                assertEquals(0, permissions.size)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "NO retries")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions", recordedRequest?.path)

        }

    @Test
    fun `getPermissionInfoFlow handles malformed JSON string`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":"1"}]""") // malformed JSON response - missing fields
            )

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)
                assertNotNull(errorItem.e.message)
                val error = errorItem.e.message
                assertTrue(
                    actual =
                        error?.startsWith("Fields [task, userId] are required")
                            ?: false
                )

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "No retries on serialization failure")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionInfoFlow handles empty body of permissions`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("")
            )

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val errorItem = awaitItem()
                // JsonDecodingException handled as NetworkResult.Exception
                assertIs<NetworkResult.Exception>(errorItem)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "No retries on empty body")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionInfoFlow handles no body of permissions`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                // no body() set
            )

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val errorItem = awaitItem()
                // JsonDecodingException handled as NetworkResult.Exception
                assertIs<NetworkResult.Exception>(errorItem)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "No retries on empty body")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionInfoFlow emits Loading then returns Error when API returns HTTP 500 3x`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP 500 responses)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)

                assertEquals("HTTP 500 Server Error", errorItem.e.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered HTTP 500 response 3x")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }

            // 7. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)

        }

    @Test
    fun `getPermissionInfoFlow emits Loading then retries IOException 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue MockWebServer responses (e.g., 2 IOExceptions then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSamplePermissionDtos())
            )

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<UserPermissionDomainModel>>(successItem)
                val data = successItem.data
                assertIs<UserPermissionDomainModel>(data)
                val permissions = data.permissions
                assertIs<List<String>>(permissions)
                repeat(3) { task ->
                    assertContains(permissions, "work task #${task + 1}")
                }

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 2 IOException responses, 1 200 response")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }

            // 7. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)

        }

    @Test
    fun `getPermissionInfoFlow emits Loading then retries IOException and returns Exception when API returns IOException 3x`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP IOExceptions)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)
                assertIs<IOException>(errorItem.e)

                val errorMessage = errorItem.e.message
                if (errorMessage != null) {
                    assertTrue(
                        errorMessage.startsWith("Connection reset") ||
                                errorMessage.startsWith("unexpected end of stream")
                    )
                }

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 3 HTTP IOException responses")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)

        }

    @Test
    fun `getPermissionInfoFlow fails fast on HTTP 401 without retrying`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
                Log.d("retry", "$attempt")
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Assert fast failure
                val errorState = awaitItem()
                assertIs<NetworkResult.Error>(errorState) // Caught by repository's try/catch block
                assertEquals(401, errorState.code)
                assertEquals("Client Error", errorState.message)

                // Assert NO retries occurred (only 1 HTTP request made)
                assertEquals(0, retryCount)
                assertEquals(1, mockWebServer.requestCount)

                cancelAndIgnoreRemainingEvents()
            }

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionInfoFlow fails fast on HTTP 403 without retrying`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
                Log.d("retry", "$attempt")
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Assert fast failure
                val errorState = awaitItem()
                assertIs<NetworkResult.Error>(errorState) // Caught by repository's try/catch block
                assertEquals(403, errorState.code)
                assertEquals("Client Error", errorState.message)

                // Assert NO retries occurred (only 1 HTTP request made)
                assertEquals(0, retryCount)
                assertEquals(1, mockWebServer.requestCount)

                cancelAndIgnoreRemainingEvents()
            }

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionInfoFlow fails fast when service is unavailable`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            repeat(3) {
                mockWebServer.enqueue(
                    MockResponse().setResponseCode(503).setBody("Message service is unavailable")
                )
            }

            var retryCount = 0

            repository.getPermissionInfoFlow(onRetry = { attempt ->
                retryCount = attempt
                Log.d("retry", "$attempt")
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Assert fast failure
                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)
                assertEquals("HTTP 503 Server Error", errorItem.e.message)

                // Assert NO retries occurred (only 1 HTTP request made)
                assertEquals(3, retryCount)
                assertEquals(3, mockWebServer.requestCount)

                cancelAndIgnoreRemainingEvents()
            }

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionInfoFlow cancels cleanly without emitting NetworkResult Exception`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                listOf(101, 102, 103)
            )

            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(5, TimeUnit.SECONDS) // indicate cancellation (timeout)
                    .setBody(createSamplePermissionDtos())
            )

            // 2. Observe the flow with Turbine
            repository.getPermissionInfoFlow(onRetry = {}).test {

                // Assert initial state
                assertEquals(NetworkResult.Loading, awaitItem())

                // 3. Cancel the flow subscriber while suspended on the network call
                cancelAndIgnoreRemainingEvents()
            }

            testScheduler.advanceUntilIdle()

            // If the repository mistakenly swallowed CancellationException and emitted
            // NetworkResult.Exception, Turbine would have thrown an unconsumed event error above!
        }

    private fun createSamplePermissionDto(id: Int, task: String, userId: Int): PermissionDto {
        return PermissionDto(
            id = id,
            task = task,
            userId = userId
        )
    }

    // Fabricate the list of permissions to get.
    // Notice by making the Json instance create the raw JSON string
    // that it relieves us from balancing array brackets, squiggly braces, and commas
    // of a traditional raw """[...]""" list.
    private fun createSamplePermissionDtos(): String =
        json.encodeToString(
            MutableList(3) { index ->
                createSamplePermissionDto(
                    id = 100 + index + 1,
                    task = "work task #${index + 1}",
                    userId = 200 + (index + 1) * 2
                )
            }
        )

}