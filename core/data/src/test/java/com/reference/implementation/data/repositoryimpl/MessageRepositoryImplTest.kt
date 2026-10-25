package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.MessageDao
import com.reference.implementation.database.model.MessageEntity
import com.reference.implementation.database.model.MessageReadUnreadQueryResult
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.UserMessageReadUnreadDomainModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    // 💡 THE CHRONOLOGICAL REMEDY: Add a hot stream placeholder matching your custom query model!
    private lateinit var daoAggregationMockStream: MutableStateFlow<MessageReadUnreadQueryResult>
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
        every { Log.isLoggable(any(), any()) } returns false

        // Instantiate hot mocking data channels
        messageDao = mockk(relaxed = true)

        daoMockStream = MutableStateFlow(emptyList())

        // Initialize your aggregate data class stream baseline (unread=0, read=0)
        daoAggregationMockStream = MutableStateFlow(MessageReadUnreadQueryResult(unreadCount = 0, readCount = 0))

        // Bind the mock use case getter to pipe our StateFlow records natively!
        every { messageDao.getMessagesStream() } returns daoMockStream

        // THE RESOLUTION: Instruct MockK exactly how to satisfy your custom query call!
        every { messageDao.getUserMessageReadUnreadStream(any()) } returns daoAggregationMockStream

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

    // Requires StandardTestDispatcher for resilience test
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getUserReadUnreadCounts stream remains open and active after background synchronization encounters network 500 error`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(42)

            // 1. Arrange: Enqueue exactly 3 HTTP 500 failure responses on MockWebServer
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            // 2. Act & Assert: Open the stream subscription channel pipe using Turbine
            repository.getUserReadUnreadCounts().test {

                // Event 1: Initial state of the flow must emit loading baseline at time zero
                // Thanks to StandardTestDispatcher, the network hasn't even executed a single line yet!
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserMessageReadUnreadDomainModel>>(loadingItem)
                val initialDomainModel = UserMessageReadUnreadDomainModel(0,0)
                assertEquals(
                    initialDomainModel,
                    loadingItem.data,
                    "Room cache contains nothing"
                )

                assertTrue(loadingItem.isSyncing, "The sync state begins as true")
                assertEquals(null, loadingItem.syncError)

                // CLOCK STEP 1: Move the virtual test clock forward completely!
                // This drives all retryIO background delays, executes the 3 attempts,
                // and flushes your SyncState.Error state update natively down the pipeline.
                testScheduler.advanceUntilIdle()

                // Event 2: Network Sync Failure State Snapshot!
                // Turbine cleanly intercepts the intermediate network error update frame.
                val errorStateItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserMessageReadUnreadDomainModel>>(errorStateItem)
                assertFalse(
                    errorStateItem.isSyncing,
                    "The background network synchronization has ended"
                )
                assertIs<HttpException>(
                    errorStateItem.syncError,
                    "Exposes the HttpException payload info"
                )
                assertEquals(
                    initialDomainModel,
                    loadingItem.data,
                    "Room cache contains nothing"
                )

                // 💡 CLOCK STEP 2: Trigger an on-demand database write mutation!
                // This simulates an offline user interaction modifying records post-failure.
                launch {
                    daoAggregationMockStream.value = MessageReadUnreadQueryResult(
                        unreadCount = 0,
                        readCount = 1
                    )
                }

                // Flush the scheduler one final time to step the clock and dispatch the mutation task
                testScheduler.advanceUntilIdle()

                // Event 3: The Ultimate Resiliency Proof!
                // Turbine cleanly intercepts the database write because the stream stayed 100% alive!
                val resilientCacheEmission = awaitItem()
                assertIs<CachePayload.ActiveStream<UserMessageReadUnreadDomainModel>>(resilientCacheEmission)
                val successModel = UserMessageReadUnreadDomainModel(1, 0)
                assertFalse(resilientCacheEmission.isSyncing)
                assertEquals(
                    successModel,
                    resilientCacheEmission.data,
                    "Room cache contains 1"
                )
                assertIs<HttpException>(
                    resilientCacheEmission.syncError,
                    "The background network error info persists"
                )

                cancelAndIgnoreRemainingEvents()
            }

            // 3. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }

// ################################################################################################
// ################################################################################################
// ################################################################################################
// ################################################################################################

    // Requires StandardTestDispatcher for resilience test
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getSummaryMessages stream remains open and active after background synchronization encounters network 500 error`() =
        runTest(testDispatcher) {

            // 1. Arrange: Enqueue exactly 3 HTTP 500 failure responses on MockWebServer
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            // 2. Act & Assert: Open the stream subscription channel pipe using Turbine
            repository.getSummaryMessages().test {

                // Event 1: Initial state of the flow must emit loading baseline at time zero
                // Thanks to StandardTestDispatcher, the network hasn't even executed a single line yet!
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(loadingItem)
                assertEquals("0 / 0", loadingItem.data, "Room cache contains nothing initially")
                assertTrue(loadingItem.isSyncing, "The sync state begins as true")
                assertEquals(null, loadingItem.syncError)

                // CLOCK STEP 1: Move the virtual test clock forward completely!
                // This drives all retryIO background delays, executes the 3 attempts,
                // and flushes your SyncState.Error state update natively down the pipeline.
                testScheduler.advanceUntilIdle()

                // Event 2: Network Sync Failure State Snapshot!
                // Turbine cleanly intercepts the intermediate network error update frame.
                val errorStateItem = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(errorStateItem)
                assertFalse(
                    errorStateItem.isSyncing,
                    "The background network synchronization has ended"
                )
                assertIs<HttpException>(
                    errorStateItem.syncError,
                    "Exposes the HttpException payload info"
                )
                assertEquals("0 / 0", errorStateItem.data, "Cache count remains clean at 0 rows")

                // 💡 CLOCK STEP 2: Trigger an on-demand database write mutation!
                // This simulates an offline user interaction modifying records post-failure.
                launch {
                    val sampleEntityList = listOf(
                        MessageEntity(
                            id = 99,
                            subject = "nightly outage",
                            body = "there is an outage",
                            read = true,
                            userId = 1234,
                            createdAt = "2026-07-13T22:28:56.321Z"
                        )
                    )
                    daoMockStream.value = sampleEntityList
                }

                // Flush the scheduler one final time to step the clock and dispatch the mutation task
                testScheduler.advanceUntilIdle()

                // Event 3: The Ultimate Resiliency Proof!
                // Turbine cleanly intercepts the database write because the stream stayed 100% alive!
                val resilientCacheEmission = awaitItem()
                assertIs<CachePayload.ActiveStream<String>>(resilientCacheEmission)
                assertFalse(resilientCacheEmission.isSyncing)
                assertEquals(
                    expected = "0 / 1",
                    actual = resilientCacheEmission.data,
                    "Stream successfully caught the mutation post-crash!"
                )
                assertIs<HttpException>(
                    resilientCacheEmission.syncError,
                    "The background network error info persists"
                )

                cancelAndIgnoreRemainingEvents()
            }

            // 3. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/messages", recordedRequest?.path)
        }
}