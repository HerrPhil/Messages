package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.toDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.MessageDao
import com.reference.implementation.database.model.MessageEntity
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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

    // THE IMMUTABLE FIX: Mock the DAO contract interfaces natively!
    private lateinit var messageDao: MessageDao

    // A hot stream placeholder that perfectly mimics Room's active table emissions
    private lateinit var daoMockStream: MutableStateFlow<List<MessageEntity>>

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

        // Instantiate hot mocking data channels
        messageDao = mockk(relaxed = true)
        daoMockStream = MutableStateFlow(emptyList())

        // Bind the mock use case getter to pipe our StateFlow records natively!
        every { messageDao.getMessagesStream() } returns daoMockStream

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
            sessionManager = sessionManager,
            messageDao = messageDao,
            externalScope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        mockWebServer.close()
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
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