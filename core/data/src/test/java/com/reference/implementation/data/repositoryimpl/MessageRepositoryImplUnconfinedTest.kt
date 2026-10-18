package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.toDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.MessageDao
import com.reference.implementation.database.model.MessageEntity
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecodingException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MessageRepositoryImplUnconfinedTest {

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {

        // Bind the dispatcher to an explicit scheduler instance
        testScheduler = TestCoroutineScheduler()
        testDispatcher = UnconfinedTestDispatcher(testScheduler)

        // Mock static calls to android.util.Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
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
            apiService = api,
            ioDispatcher = testDispatcher,
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
    fun `getSummaryMessage emits Loading and sees 2 HTTP 500s then fetches from network and updates local cache query stream`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDomainMessages()) // creates 3 sample messages
            )

            // 2. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { messageDao.insertMessages(any()) } answers {
                val incomingList = firstArg<List<MessageEntity>>()
                daoMockStream.value = incomingList // Update the hot stream immediately!
            }

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Modern Data Sync Success!
                // The sync task hits the 200 payload.
                // DAO updates the list.
                // Room fills infinite pipeline with new messages.
                val successItem = awaitItem()

                // 6. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<String>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized messages"
                )
                assertEquals(
                    "1 / 3",
                    successItem.data,
                    "Room counts 3 messages"
                )
                assertEquals(null, successItem.syncError) // no problems for successful syncs!

                // 7. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 2 500 responses, 1 200 response")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { messageDao.insertMessages(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }


    @OptIn(ExperimentalSerializationApi::class)
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

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize messages"
                )
                assertEquals(
                    "0 / 0",
                    errorItem.data,
                    "Room counts 0 messages"
                )
                assertIs<JsonDecodingException>(errorItem.syncError) // different exception
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual = errorMessage
                            .startsWith("Unexpected JSON token at offset 0")
                    )
                } else {
                    fail("expected an error message")
                }

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(0, retryCount)
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { messageDao.insertMessages(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
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

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Background Data Sync Failure Execution Block!
                // The sync task hits 1 401 exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val noResultItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(noResultItem)
                assertFalse(
                    noResultItem.isSyncing,
                    "onStart() tried to synchronize messages"
                )
                assertEquals(
                    "0 / 0",
                    noResultItem.data,
                    "Room counts 0 messages"
                )
                assertNull(noResultItem.syncError)

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(0, retryCount)
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 1) { messageDao.insertMessages(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
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

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Background Data Sync Failure Execution Block!
                // The sync task hits 1 401 exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize messages"
                )
                assertEquals(
                    "0 / 0",
                    errorItem.data,
                    "Room counts 0 messages"
                )
                assertIs<JsonDecodingException>(errorItem.syncError) // different exception
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual = errorMessage
                            .startsWith("Expected start of the array '['") // might break
                    )
                } else {
                    fail("expected an error message")
                }

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(0, retryCount)
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { messageDao.insertMessages(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
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

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Background Data Sync Failure Execution Block!
                // The sync task hits 1 401 exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize messages"
                )
                assertEquals(
                    "0 / 0",
                    errorItem.data,
                    "Room counts 0 messages"
                )
                assertIs<JsonDecodingException>(errorItem.syncError) // different exception
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual = errorMessage
                            .startsWith("Expected start of the array '['") // might break
                    )
                } else {
                    fail("expected an error message")
                }

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(0, retryCount)
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { messageDao.insertMessages(any()) }

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

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Background Data Sync Failure Execution Block!
                // The sync task hits 3 500 - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize messages"
                )
                assertEquals(
                    "0 / 0",
                    errorItem.data,
                    "Room counts 0 messages"
                )
                assertIs<HttpException>(errorItem.syncError)
                assertEquals("HTTP 500 Server Error", errorItem.syncError?.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 3 500 responses")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { messageDao.insertMessages(any()) }

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

            // 2. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { messageDao.insertMessages(any()) } answers {
                val incomingList = firstArg<List<MessageEntity>>()
                daoMockStream.value = incomingList // Update the hot stream immediately!
            }

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Modern Data Sync Success!
                // The sync task hits the 200 payload.
                // DAO updates the list.
                // Room fills infinite pipeline with new messages.
                val successItem = awaitItem()

                // 6. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<String>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized bulletins"
                )
                assertEquals(
                    "1 / 3",
                    successItem.data,
                    "Room counts 1 of 3 unread messages"
                )
                assertEquals(null, successItem.syncError) // no problems for successful syncs!

                // 7. Assert retry callbacks and HTTP request counts
                assertEquals(3, retryCount, "encountered 2 500 responses, 1 200 response")
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { messageDao.insertMessages(any()) }

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

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            var retryCount = 0

            repository.getSummaryMessages(onRetry = { attempt ->
                retryCount = attempt
            }).test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Background Data Sync Failure Execution Block!
                // The sync task hits 3 IO exceptions - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    "0 / 0",
                    errorItem.data,
                    "Room counts 0 messages"
                )
                assertIs<IOException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
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

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { messageDao.insertMessages(any()) }

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

        // 2. Do not intercept your repository's transactional writes
        // to manually feed our mock channel - retryIO limit was reached (no data)!

        var retryCount = 0

        repository.getSummaryMessages(onRetry = { attempt ->
            retryCount = attempt
            Log.d("retry", "$attempt")
        }).test {

            // 3. Immediate emission must be Loading baseline
            val loadingItem = awaitItem()
            assertIs<CachePayload.ActiveStream<String>>(loadingItem)
            assertEquals(
                "0 / 0",
                loadingItem.data,
                "Room cache contains nothing"
            )
            assertTrue(loadingItem.isSyncing)
            assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

            // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
            testScheduler.advanceUntilIdle()

            // 5. Background Data Sync Failure Execution Block!
            // The sync task hits 1 401 exception - no payload.
            // Combine consolidates baseline 0 entities with syncError
            // Synchronization of messages updates syncError.
            val errorItem = awaitItem()
            assertIs<CachePayload.ActiveStream<String>>(errorItem)
            assertFalse(
                errorItem.isSyncing,
                "onStart() tried to synchronize bulletins"
            )
            assertEquals(
                "0 / 0",
                errorItem.data,
                "Room counts 0 messages"
            )
            assertIs<HttpException>(errorItem.syncError)
            val errorMessage = errorItem.syncError?.message
            val errorCode = (errorItem.syncError as? HttpException)?.code()
            if (errorMessage != null) {
                assertEquals("HTTP 401 Client Error", errorMessage)
                assertEquals(401, errorCode)
            }

            // 6. Assert NO retry callbacks and 1 HTTP request count
            assertEquals(0, retryCount)
            assertEquals(1, mockWebServer.requestCount)

            cancelAndIgnoreRemainingEvents()
        }

        // Never reached when retryIO response is NOT successful!
        coVerify(exactly = 0) { messageDao.insertMessages(any()) }

        val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
        assertEquals("GET", recordedRequest?.method)
        assertEquals("/messages", recordedRequest?.path)
    }

    @Test
    fun `getSummaryMessage fails fast on HTTP 403 without retrying`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        // 2. Do not intercept your repository's transactional writes
        // to manually feed our mock channel - retryIO limit was reached (no data)!

        var retryCount = 0

        repository.getSummaryMessages(onRetry = { attempt ->
            retryCount = attempt
            Log.d("retry", "$attempt")
        }).test {

            // 3. Immediate emission must be Loading baseline
            val loadingItem = awaitItem()
            assertIs<CachePayload.ActiveStream<String>>(loadingItem)
            assertEquals(
                "0 / 0",
                loadingItem.data,
                "Room cache contains nothing"
            )
            assertTrue(loadingItem.isSyncing)
            assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

            // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
            testScheduler.advanceUntilIdle()

            // 5. Background Data Sync Failure Execution Block!
            // The sync task hits 1 403 exception - no payload.
            // Combine consolidates baseline 0 entities with syncError
            // Synchronization of messages updates syncError.
            val errorItem = awaitItem()
            assertIs<CachePayload.ActiveStream<String>>(errorItem)
            assertFalse(
                errorItem.isSyncing,
                "onStart() tried to synchronize users"
            )
            assertEquals(
                "0 / 0",
                errorItem.data,
                "Room counts 0 users"
            )
            assertIs<HttpException>(errorItem.syncError)
            val errorMessage = errorItem.syncError?.message
            val errorCode = (errorItem.syncError as? HttpException)?.code()
            if (errorMessage != null) {
                assertEquals("HTTP 403 Client Error", errorMessage)
                assertEquals(403, errorCode)
            }

            // 6. Assert NO retry callbacks and 1 HTTP request count
            assertEquals(0, retryCount)
            assertEquals(1, mockWebServer.requestCount)

            cancelAndIgnoreRemainingEvents()
        }

        // Never reached when retryIO response is NOT successful!
        coVerify(exactly = 0) { messageDao.insertMessages(any()) }

        // 7. Verify request payload contract on MockWebServer
        val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
        assertEquals("GET", recordedRequest?.method)
        assertEquals("/messages", recordedRequest?.path)
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

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals(
                    "0 / 0",
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

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