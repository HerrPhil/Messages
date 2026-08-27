package com.reference.implementation.data

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.toDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.repositoryimpl.MessageCacheRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MessageCacheRepositoryImplTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var repository: MessageCacheRepositoryImpl

    @Before
    fun setUp() {
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

        repository = MessageCacheRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = apiService,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // Function 1: refreshMessagesOfActiveUser (and refreshMessagesOfSelectedUser)
    // =========================================================================

    @Test
    fun `refreshMessagesOfActiveUser fetches user id from session and updates cache with Success`() =
        runTest(testDispatcher) {
            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(42)

            // 2. Enqueue MockWebServer response for targetUserId = 42
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                    [
                        {
                            "id": 101,
                            "subject": "Welcome",
                            "body": "Hello World",
                            "read": false,
                            "userId": 42,
                            "createdAt": "2026-07-13T22:28:56.321Z"
                        }
                    ]
                    """.trimIndent()
                    )
            )

            var retryCount = 0

            // 3. Collect StateFlow stream via Turbine while executing refresh
            repository.getMessagesByUser().test {
                // Initial State before refresh is NetworkResult.Loading
                assertEquals(NetworkResult.Loading, awaitItem())

                // Trigger refresh in backgroundScope or directly inside test
                repository.refreshMessagesOfActiveUser(
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Assert StateFlow updates to Success with mapped domain model
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<List<MessageDomainModel>>>(successItem)
                assertEquals(1, successItem.data.size)
                assertEquals(101, successItem.data.first().id)
                assertEquals("Welcome", successItem.data.first().subject)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when user unauthenticated")

                cancelAndIgnoreRemainingEvents()
            }

            // Verify request was sent to path matching user 42
            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/messages/userId/42", recordedRequest.path)
        }

    @Test
    fun `refreshMessagesOfActiveUser defaults to NO_VALUE when unauthenticated`() =
        runTest(testDispatcher) {
            // Mock unauthenticated session
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.NoValue

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]")
            )

            var retryCount = 0

            repository.refreshMessagesOfActiveUser(
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 6. Assert retry callbacks and HTTP request counts
            assertEquals(0, retryCount, "no retries when user unauthenticated")

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/messages/userId/0", recordedRequest.path)
        }

    @Test
    fun `refreshMessagesOfSelectedUser fails update cache when messages not found`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("Not Found")
            )

            var retryCount = 0

            repository.getMessagesByUser().test {
                assertEquals(NetworkResult.Loading, awaitItem())

                repository.refreshMessagesOfSelectedUser(
                    userId = 42,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()
                assertIs<NetworkResult.Error>(errorItem)
                assertEquals(404, errorItem.code)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when messages not found")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshMessagesOfSelectedUser fails update cache when service is unavailable`() =
        runTest(testDispatcher) {
            // Enqueue 3 error responses so attempts 1, 2, and 3 get instant responses
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(503))
            }

            var retryCount = 0

            repository.getMessagesByUser().test {
                assertEquals(NetworkResult.Loading, awaitItem())

                repository.refreshMessagesOfSelectedUser(
                    userId = 42,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)
                assertEquals("HTTP 503 Server Error", errorItem.e.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 retries when message service unavailable")

                cancelAndIgnoreRemainingEvents()
            }
        }


    // =========================================================================
    // Function 2 & 3: markMessageAsRead and markMessageAsUnread
    // =========================================================================

    @Test
    fun `markMessageAsRead updates local cache when network call succeeds`() =
        runTest(testDispatcher) {
            // 1. Seed initial state into cache (1 unread message, id=101)
            seedCacheWithMessages(
                listOf(
                    createSampleDomainMessage(id = 101, read = false)
                )
            )

            // 2. Enqueue 200 response returning updated DTO (read = true)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleMessageJson(id = 101, read = true))
            )

            var retryCount = 0

            // 3. Observe the cache stream while marking read
            repository.getMessagesByUser().test {
                // Initial state before operation
                val initialItem = awaitItem()
                assertIs<NetworkResult.Success<List<MessageDomainModel>>>(initialItem)
                assertEquals(false, initialItem.data.first().read)

                repository.markMessageAsRead(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Cache reflects updated read state = true
                val updatedItem = awaitItem()
                assertIs<NetworkResult.Success<List<MessageDomainModel>>>(updatedItem)
                assertEquals(true, updatedItem.data.first().read)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 200 success response")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markMessageAsUnread updates local cache when network call succeeds`() =
        runTest(testDispatcher) {
            // 1. Seed initial state into cache (1 read message, id=101)
            seedCacheWithMessages(
                listOf(
                    createSampleDomainMessage(id = 101, read = true)
                )
            )

            // 2. Enqueue 200 response returning updated DTO (read = true)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleMessageJson(id = 101, read = false))
            )

            var retryCount = 0

            // 3. Observe the cache stream while marking read
            repository.getMessagesByUser().test {
                // Initial state before operation
                val initialItem = awaitItem()
                assertIs<NetworkResult.Success<List<MessageDomainModel>>>(initialItem)
                assertEquals(true, initialItem.data.first().read)

                repository.markMessageAsUnread(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Cache reflects updated read state = true
                val updatedItem = awaitItem()
                assertIs<NetworkResult.Success<List<MessageDomainModel>>>(updatedItem)
                assertEquals(false, updatedItem.data.first().read)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 200 success response")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markMessageAsRead emits failure event when message not found`() = runTest(testDispatcher) {
        // 1. Seed initial unread state
        seedCacheWithMessages(
            listOf(
                createSampleDomainMessage(id = 101, read = false)
            )
        )

        // 2. Enqueue 404 response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )

        var retryCount = 0

        // 3. Test UI event channel emissions using Turbine
        repository.uiEvents.test {
            repository.markMessageAsRead(
                messageId = 101,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 4. Advance virtual time to drain all delay() calls inside retryIO
            testScheduler.advanceUntilIdle()

            val event = awaitItem()
            assertEquals(MessageDomainEvent.MessageMarkReadFailureFeedback, event)

            // 6. Assert retry callbacks and HTTP request counts
            assertEquals(0, retryCount, "no retries when 401 error response")

            cancelAndIgnoreRemainingEvents()
        }

        // Ensure cache state remained unchanged (still unread)
        val cacheState = repository.getMessagesByUser().first()
        assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
        assertEquals(false, cacheState.data.first().read)
    }

    @Test
    fun `markMessageAsUnread emits failure event when message not found`() =
        runTest(testDispatcher) {
            // 1. Seed initial unread state
            seedCacheWithMessages(
                listOf(
                    createSampleDomainMessage(id = 101, read = true)
                )
            )

            // 2. Enqueue 404 response
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("Not Found")
            )

            var retryCount = 0

            // 3. Test UI event channel emissions using Turbine
            repository.uiEvents.test {
                repository.markMessageAsUnread(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageMarkUnReadFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 401 error response")

                cancelAndIgnoreRemainingEvents()
            }

            // Ensure cache state remained unchanged (still unread)
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(true, cacheState.data.first().read)
        }

    @Test
    fun `markMessageAsRead emits failure event when service unavailable`() = runTest(testDispatcher) {
        // 1. Seed initial unread state
        seedCacheWithMessages(
            listOf(
                createSampleDomainMessage(id = 101, read = false)
            )
        )

        // 2. Enqueue 500 error from server
        // Enqueue 3 error responses so attempts 1, 2, and 3 get instant responses
        repeat(3) {
            mockWebServer.enqueue(MockResponse().setResponseCode(503))
        }

        var retryCount = 0

        // 3. Test UI event channel emissions using Turbine
        repository.uiEvents.test {
            repository.markMessageAsRead(
                messageId = 101,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 4. Advance virtual time to drain all delay() calls inside retryIO
            testScheduler.advanceUntilIdle()

            val event = awaitItem()
            assertEquals(MessageDomainEvent.MessageMarkReadFailureFeedback, event)

            // 6. Assert retry callbacks and HTTP request counts
            assertEquals(3, retryCount, "3 retries when 503 error response")

            cancelAndIgnoreRemainingEvents()
        }

        // Ensure cache state remained unchanged (still unread)
        val cacheState = repository.getMessagesByUser().first()
        assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
        assertEquals(false, cacheState.data.first().read)
    }

    @Test
    fun `markMessageAsUnread emits failure event when service unavailable`() =
        runTest(testDispatcher) {
            // 1. Seed initial unread state
            seedCacheWithMessages(
                listOf(
                    createSampleDomainMessage(id = 101, read = true)
                )
            )

            // 2. Enqueue 500 error from server
            // Enqueue 3 error responses so attempts 1, 2, and 3 get instant responses
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(503))
            }

            var retryCount = 0

            // 3. Test UI event channel emissions using Turbine
            repository.uiEvents.test {
                repository.markMessageAsUnread(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageMarkUnReadFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 retries when 500 error response")

                cancelAndIgnoreRemainingEvents()
            }

            // Ensure cache state remained unchanged (still unread)
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(true, cacheState.data.first().read)
        }

    @Test
    fun `markMessageAsRead emits failure event on JSON response exception`() =
        runTest(testDispatcher) {
            seedCacheWithMessages(
                listOf(
                    createSampleDomainMessage(id = 101, read = false)
                )
            )

            // Enqueue malformed JSON to trigger deserialization Exception
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{ invalid_json }") // the 'Exception'
            )

            var retryCount = 0

            repository.uiEvents.test {
                repository.markMessageAsRead(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageMarkReadFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when JSON serialization error response")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markMessageAsUnread emits failure event on JSON response exception`() =
        runTest(testDispatcher) {
            seedCacheWithMessages(
                listOf(
                    createSampleDomainMessage(id = 101, read = true)
                )
            )

            // Enqueue malformed JSON to trigger deserialization Exception
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{ invalid_json }") // the 'Exception'
            )

            var retryCount = 0

            repository.uiEvents.test {
                repository.markMessageAsUnread(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageMarkUnReadFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 401 error response")

                cancelAndIgnoreRemainingEvents()
            }
        }

// =========================================================================
// Function 4 & 5: deleteMessage and restoreMessage (Optimistic UI & Rollback)
// =========================================================================

    @Test
    fun `deleteMessage optimistically removes message and emits success event on network 200`() =
        runTest(testDispatcher) {
            // 1. Seed cache with 2 messages (IDs 101 and 102)
            val message101 = createSampleDomainMessage(id = 101, read = true)
            val message102 = createSampleDomainMessage(id = 102, read = false)
            seedCacheWithMessages(listOf(message101, message102))

            // 2. Enqueue successful deletion response from MockWebServer
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}")
            )

            var retryCount = 0

            // 3. Test UI event stream while executing delete
            repository.uiEvents.test {
                repository.deleteMessage(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Verify success feedback event emitted with deleted message reference
                val event = awaitItem()
                assertIs<MessageDomainEvent.MessageDeleteSuccessFeedback>(event)
                assertEquals(101, event.data.id)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 200 success response")

                cancelAndIgnoreRemainingEvents()
            }

            // 4. Assert local cache retained message 102 and removed 101
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(1, cacheState.data.size)
            assertEquals(102, cacheState.data.first().id)
        }

    @Test
    fun `deleteMessage rolls back cache and emits failure event on unauthorized error`() =
        runTest(testDispatcher) {
            // 1. Seed initial cache state with 2 messages
            val message101 = createSampleDomainMessage(id = 101, read = true)
            val message102 = createSampleDomainMessage(id = 102, read = false)
            seedCacheWithMessages(listOf(message101, message102))

            // 2. Enqueue 401 error from server
            mockWebServer.enqueue(MockResponse().setResponseCode(401)) // unauthorized

            var retryCount = 0

            // 3. Observe event channel
            repository.uiEvents.test {
                repository.deleteMessage(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageDeleteFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 401 error response")

                cancelAndIgnoreRemainingEvents()
            }

            // 4. ROLLBACK ASSERTION: Verify state was reverted back to containing BOTH messages
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(2, cacheState.data.size)
        }

    @Test
    fun `deleteMessage rolls back cache and emits failure event when service unavailable`() =
        runTest(testDispatcher) {
            // 1. Seed initial cache state with 2 messages
            val message101 = createSampleDomainMessage(id = 101, read = true)
            val message102 = createSampleDomainMessage(id = 102, read = false)
            seedCacheWithMessages(listOf(message101, message102))

            // 2. Enqueue 503 error from server
            // Enqueue 3 error responses so attempts 1, 2, and 3 get instant responses
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(503))
            }

            var retryCount = 0

            // 3. Observe event channel
            repository.uiEvents.test {
                repository.deleteMessage(
                    messageId = 101,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageDeleteFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 retries when 503 error response")

                cancelAndIgnoreRemainingEvents()
            }

            // 4. ROLLBACK ASSERTION: Verify state was reverted back to containing BOTH messages
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(2, cacheState.data.size)
        }

    @Test
    fun `restoreMessage optimistically adds message and emits success event on network 200`() =
        runTest(testDispatcher) {
            // 1. Seed cache with 1 message (ID 102)
            val message102 = createSampleDomainMessage(id = 102, read = false)
            seedCacheWithMessages(listOf(message102))

            // 2. Enqueue successful addMessage response
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleMessageJson(id = 101, read = true))
            )

            var retryCount = 0

            val messageToRestore = createSampleDomainMessage(id = 101, read = true)

            repository.uiEvents.test {
                repository.restoreMessage(
                    deletedMessage = messageToRestore,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageRestoreSuccessFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 200 success response")

                cancelAndIgnoreRemainingEvents()
            }

            // Assert restored message (101) was appended to cache state
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(2, cacheState.data.size)
        }

    @Test
    fun `restoreMessage rolls back cache and emits failure event on unauthorized failure`() =
        runTest(testDispatcher) {
            // 1. Seed cache with 1 message (ID 102)
            val message102 = createSampleDomainMessage(id = 102, read = false)
            seedCacheWithMessages(listOf(message102))

            // 2. Enqueue network failure
            mockWebServer.enqueue(MockResponse().setResponseCode(401))

            var retryCount = 0

            val messageToRestore = createSampleDomainMessage(id = 101, read = true)

            repository.uiEvents.test {
                repository.restoreMessage(
                    deletedMessage = messageToRestore,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageRestoreFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(0, retryCount, "no retries when 401 error response")

                cancelAndIgnoreRemainingEvents()
            }

            // ROLLBACK ASSERTION: Verify message 101 was removed, reverting cache back to 1 item
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(1, cacheState.data.size)
            assertEquals(102, cacheState.data.first().id)
        }

    @Test
    fun `restoreMessage rolls back cache and emits failure event on internal server error`() =
        runTest(testDispatcher) {
            // 1. Seed cache with 1 message (ID 102)
            val message102 = createSampleDomainMessage(id = 102, read = false)
            seedCacheWithMessages(listOf(message102))

            // 2. Enqueue network failure - 500 is 'catch-all' internal server error
            // Enqueue 3 error responses so attempts 1, 2, and 3 get instant responses
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(500))
            }

            var retryCount = 0

            val messageToRestore = createSampleDomainMessage(id = 101, read = true)

            repository.uiEvents.test {
                repository.restoreMessage(
                    deletedMessage = messageToRestore,
                    onRetry = { attempt ->
                        retryCount = attempt
                    }
                )

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                val event = awaitItem()
                assertEquals(MessageDomainEvent.MessageRestoreFailureFeedback, event)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "3 retries when 500 error response")

                cancelAndIgnoreRemainingEvents()
            }

            // ROLLBACK ASSERTION: Verify message 101 was removed, reverting cache back to 1 item
            val cacheState = repository.getMessagesByUser().first()
            assertIs<NetworkResult.Success<List<MessageDomainModel>>>(cacheState)
            assertEquals(1, cacheState.data.size)
            assertEquals(102, cacheState.data.first().id)
        }


// *********************************************************************************************
// *********************************************************************************************
// *********************************************************************************************
// *********************************************************************************************


    private fun seedCacheWithMessages(messages: List<MessageDomainModel>) {
        // We can pre-populate cache by calling refresh against mock endpoint or
        // using mockWebServer to seed the initial getMessages call.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json.encodeToString(messages.map { it.toDto() })) // Or raw JSON string helper
        )
        runTest(testDispatcher) {
            repository.refreshMessagesOfSelectedUser(userId = 1, onRetry = {})
        }
    }

    private fun createSampleDomainMessage(id: Int, read: Boolean): MessageDomainModel {
        return MessageDomainModel(
            id = id,
            userId = 1,
            subject = "Test Subject",
            body = "Test Body",
            read = read,
            createdAt = "2026-08-26T10:00:00Z",
            createdAtInstant = Instant.parse("2026-08-26T10:00:00Z"),
        )
    }

    @Suppress("all")
    private fun createSampleMessageJson(id: Int, read: Boolean): String {
        return """
    {
        "id": $id,
        "userId": 1,
        "subject": "Test Subject",
        "body": "Test Body",
        "read": $read,
        "createdAt": "2026-08-26T10:00:00Z"
    }
    """.trimIndent()
    }

}