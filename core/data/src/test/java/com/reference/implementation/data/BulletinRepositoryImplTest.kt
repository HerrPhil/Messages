package com.reference.implementation.data

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.repositoryimpl.BulletinRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.every
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BulletinRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService
    private lateinit var repository: BulletinRepositoryImpl
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
        repository = BulletinRepositoryImpl(
            apiService = api,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        mockWebServer.close()
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `getBulletinCount emits Loading then retries 500 Error and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // Given the JSON response of bulletins
            val jsonResponse = """
                [
                    {
                        "id": 6,
                        "userId": 1,
                        "title": "Software Patch",
                        "post": "Please take the time to apply the newest security patches. The recent news of cyber attacks means we all have to do our part. The Desktop Support team will be locking access on any account that  are not compliant by Friday end of business.",
                        "timestamp": "2026-07-14T23:32:12.345Z"
                    }
                ]
            """.trimIndent()

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(jsonResponse)
            )

            var retryCount = 0

            repository.getBulletinCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<Int>>(successItem)

                with(successItem.data) {
                    assertIs<Int>(this)
                    assertEquals(this, 1)
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
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @Test
    fun `getBulletinCount emits Loading then returns Error when API returns HTTP 500 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            var retryCount = 0

            repository.getBulletinCount(onRetry = { attempt ->
                retryCount = attempt
                Log.d("retry", "$attempt")
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
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }


    @Test
    fun `getBulletinCount emits Loading then retries IOException and recovers to add Success to the cache when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // Given the JSON response of bulletins
            val jsonResponse = """
                [
                    {
                        "id": 6,
                        "userId": 1,
                        "title": "Software Patch",
                        "post": "Please take the time to apply the newest security patches. The recent news of cyber attacks means we all have to do our part. The Desktop Support team will be locking access on any account that  are not compliant by Friday end of business.",
                        "timestamp": "2026-07-14T23:32:12.345Z"
                    }
                ]
            """.trimIndent()

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP IOExceptions  then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(jsonResponse)
            )

            var retryCount = 0

            repository.getBulletinCount(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<Int>>(successItem)

                with(successItem.data) {
                    assertIs<Int>(this)
                    assertEquals(this, 1)
                }

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 2 IOException responses, 1 200 response")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/", recordedRequest?.path)
        }


    @Test
    fun `getBulletinCount emits Loading then retries IOException and returns Exception when API returns IOException 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            var retryCount = 0

            repository.getBulletinCount(onRetry = { attempt ->
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
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/", recordedRequest?.path)
        }

    @Test
    fun `refreshBulletins fails fast on HTTP 401 without retrying`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        var retryCount = 0

        repository.getBulletinCount(onRetry = { attempt ->
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
    }

    @Test
    fun `refreshBulletins fails fast on HTTP 403 without retrying`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        var retryCount = 0

        repository.getBulletinCount(onRetry = { attempt ->
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
    fun `getBulletinCount cancels cleanly without emitting NetworkResult Exception`() =
        runTest(testDispatcher) {
            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(5, TimeUnit.SECONDS)
                    .setBody("""[{"id":"1"}]""")
            )

            // 2. Observe the flow with Turbine
            repository.getBulletinCount(onRetry = {}).test {
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
