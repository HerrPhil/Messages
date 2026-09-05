package com.reference.implementation.data

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.toDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.repositoryimpl.MessageRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.MessageDomainModel
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
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private lateinit var repository: MessageRepositoryImpl
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
        repository = MessageRepositoryImpl(
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
    fun `getSummaryMessage emits Loading then retries 500 error 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDomainMessages())
            )

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<String>>(successItem)

                with(successItem.data) {
                    assertIs<String>(this)
                    assertEquals("1 / 3", this)
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
            assertEquals("/messages", recordedRequest?.path)
        }

    @Test
    fun `getSummaryMessage emits Loading then emits correct message for zero messages when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[]""") // ZERO messages implies "0 / 0"
            )

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<String>>(successItem)

                with(successItem.data) {
                    assertIs<String>(this)
                    assertEquals("0 / 0", this)
                }

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "No retries, 200 response")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }

    @Test
    fun `getSummaryMessage handles empty 200 OK body safely`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (HTTP 200 with empty body)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("")
            )

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
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
                assertEquals(0, retryCount, "NO retries, 200 response")
                assertEquals(1, mockWebServer.requestCount, "called api 1x times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }

    @Test
    fun `getSummaryMessage handles no 200 OK body safely`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (HTTP 200 with empty body)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                // no body() set
            )

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
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
                assertEquals(0, retryCount, "No retries, 200 response")
                assertEquals(1, mockWebServer.requestCount, "called api 1x times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }

    @Test
    fun `getSummaryMessage emits Loading then returns Error when API returns HTTP 500 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
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
                assertEquals(3, retryCount, "encountered 3 500 responses")
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
            assertEquals("/messages", recordedRequest?.path)

        }


    @Test
    fun `getSummaryMessage emits Loading then retries IOException and recovers to add Success to the cache when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP IOExceptions  then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDomainMessages())
            )

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<String>>(successItem)

                with(successItem.data) {
                    assertIs<String>(this)
                    assertEquals("1 / 3", this)
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
            assertEquals("/messages", recordedRequest?.path)

        }

    @Test
    fun `getSummaryMessage emits Loading then retries IOException and returns Exception when API returns IOException 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
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
                assertEquals(3, retryCount, "encountered 3 IOException responses")
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
            assertEquals("/messages", recordedRequest?.path)

        }

    @Test
    fun `getSummaryMessage fails fast on HTTP 401 without retrying`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        var retryCount = 0

        repository.getSummaryMessages(onRetry = { attempt ->
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
        assertEquals("/messages", recordedRequest?.path)
    }

    @Test
    fun `getSummaryMessage fails fast on HTTP 403 without retrying`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        var retryCount = 0

        repository.getSummaryMessages(onRetry = { attempt ->
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
    }

    @Test
    fun `getSummaryMessage fails fast on service is unavailable`() = runTest(testDispatcher) {

        mockWebServer.enqueue(
            MockResponse().setResponseCode(503).setBody("Message service is unavailable")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(503).setBody("Message service is unavailable")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(503).setBody("Message service is unavailable")
        )

        var retryCount = 0

        repository.getSummaryMessages(onRetry = { attempt ->
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

            // Assert 3 retries occurred for 503 Service Unavailable
            assertEquals(3, retryCount)
            assertEquals(3, mockWebServer.requestCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSummaryMessage cancels cleanly without emitting NetworkResult Exception`() =
        runTest(testDispatcher) {
            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(5, TimeUnit.SECONDS) // indicate cancellation (timeout)
                    .setBody("""[{"id":"1"}]""")
            )

            // 2. Observe the flow with Turbine
            repository.getSummaryMessages(onRetry = {}).test {
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
    fun `getMessagesByUserFlow fetches user id from session and returns Success with results`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(42)

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDomainMessages())
            )

            var retryCount = 0

            repository.getMessagesByUserFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<List<MessageDomainModel>>>(successItem)
                assertEquals(3, successItem.data.size)
                assertEquals(100, successItem.data.first().id)
                assertEquals("Test Subject", successItem.data.first().subject)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when user is authenticated")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/messages/userId/42", recordedRequest.path)

        }

    @Test
    fun `getMessagesByUserFlow fetches user id of NO_VALUE from session and returns Success with no results`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.NoValue

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]")
            )

            var retryCount = 0

            repository.getMessagesByUserFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<List<MessageDomainModel>>>(successItem)
                assertEquals(0, successItem.data.size)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when user is authenticated")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/messages/userId/0", recordedRequest.path)

        }

    @Test
    fun `getMessagesByUserFlow handles malformed JSON string`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(42)

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"id":"1"}]""") // malformed JSON response - missing fields
            )

            var retryCount = 0

            repository.getMessagesByUserFlow(onRetry = { attempt ->
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
                        error?.startsWith("Fields [subject, body, read, userId, createdAt] are required")
                            ?: false
                )

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "No retries on serialization failure")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()
            }

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/messages/userId/42", recordedRequest.path)
        }

    @Test
    fun `getMessagesByUserFlow fails to get messages when messages not found`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(42)

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("Not Found")
            )

            var retryCount = 0

            repository.getMessagesByUserFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()
                val errorItem = awaitItem()
                assertIs<NetworkResult.Error>(errorItem)
                assertEquals(404, errorItem.code)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when messages not found")
                assertEquals(1, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getMessagesByUserFlow fails to get messages when service is unavailable`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(42)

            // Enqueue 3 error responses so attempts 1, 2, and 3 get instant responses
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(503))
            }

            var retryCount = 0

            repository.getMessagesByUserFlow(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)
                assertEquals("HTTP 503 Server Error", errorItem.e.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 retries when message service unavailable")
                assertEquals(3, mockWebServer.requestCount, "called api 1x")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getMessagesByUserFlow cancels cleanly without emitting NetworkResult Exception`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(42)

            // 2. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setHeadersDelay(5, TimeUnit.SECONDS) // indicate cancellation (timeout)
                    .setBody(createSampleDomainMessages())
            )

            // 3. Observe the flow with Turbine
            repository.getMessagesByUserFlow(onRetry = {}).test {

                // Assert initial state
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Cancel the flow subscriber while suspended on the network call
                cancelAndIgnoreRemainingEvents()
            }

            testScheduler.advanceUntilIdle()

            // If the repository mistakenly swallowed CancellationException and emitted
            // NetworkResult.Exception, Turbine would have thrown an unconsumed event error above!
        }

    private fun createSampleDomainMessage(id: Int, read: Boolean): MessageDomainModel {
        return MessageDomainModel(
            id = 100 + id,
            userId = 1,
            subject = "Test Subject",
            body = "Test Body",
            read = read,
            createdAt = "2026-08-27T18:00:00Z",
            createdAtInstant = Instant.parse("2026-08-27T18:00:00Z"),
        )
    }

    // Fabricate the list of messages to get.
    // Notice by making the Json instance create the raw JSON string
    // that it relieves us from balancing array brackets, squiggly braces, and commas
    // of a traditional raw """[...]""" list.
    private fun createSampleDomainMessages(): String =
        json.encodeToString(MutableList(3) { index ->
            createSampleDomainMessage(
                index,
                (index % 2 == 0)
            )
        }.map { it.toDto() })

}