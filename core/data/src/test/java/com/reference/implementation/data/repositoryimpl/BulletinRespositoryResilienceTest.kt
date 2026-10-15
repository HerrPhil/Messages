package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.BulletinDao
import com.reference.implementation.database.model.BulletinEntity
import com.reference.implementation.domain.model.CachePayload
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

class BulletinRepositoryResilienceTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService
    private lateinit var bulletinDao: BulletinDao
    private lateinit var daoMockStream: MutableStateFlow<List<BulletinEntity>>
    private lateinit var repository: BulletinRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        testScheduler = TestCoroutineScheduler()
        // Use a clean lazy standard dispatcher to regain absolute timeline control
        testDispatcher = StandardTestDispatcher(testScheduler)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.isLoggable(any(), any()) } returns false

        bulletinDao = mockk(relaxed = true)
        daoMockStream = MutableStateFlow(emptyList())

        every { bulletinDao.getBulletinsStream() } returns daoMockStream
        coEvery { bulletinDao.getBookmarkedIds() } returns emptyList()

        mockWebServer = MockWebServer()
        mockWebServer.start()

        val contentType = "application/json".toMediaType()
        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)

        repository = BulletinRepositoryImpl(
            apiService = api,
            ioDispatcher = testDispatcher,
            bulletinDao = bulletinDao,
            externalScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        mockWebServer.close()
        unmockkStatic(Log::class)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getBulletinCount stream remains open and active after background synchronization encounters network 500 error`() =
        runTest(testDispatcher) {

            // 1. Arrange: Enqueue exactly 3 HTTP 500 failure responses on MockWebServer
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            // 2. Act & Assert: Open the stream subscription channel pipe using Turbine
            repository.getBulletinCount(onRetry = {}).test {

                // Event 1: Initial state of the flow must emit loading baseline at time zero
                // Thanks to StandardTestDispatcher, the network hasn't even executed a single line yet!
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(0, loadingItem.data, "Room cache contains nothing initially")
                assertTrue(loadingItem.isSyncing, "The sync state begins as true")
                assertEquals(null, loadingItem.syncError)

                // CLOCK STEP 1: Move the virtual test clock forward completely!
                // This drives all retryIO background delays, executes the 3 attempts,
                // and flushes your SyncState.Error state update natively down the pipeline.
                testScheduler.advanceUntilIdle()

                // Event 2: Network Sync Failure State Snapshot!
                // Turbine cleanly intercepts the intermediate network error update frame.
                val errorStateItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorStateItem)
                assertFalse(errorStateItem.isSyncing, "The background network synchronization has ended")
                assertIs<HttpException>(errorStateItem.syncError, "Exposes the HttpException payload info")
                assertEquals(0, errorStateItem.data, "Cache count remains clean at 0 rows")

                // 💡 CLOCK STEP 2: Trigger an on-demand database write mutation!
                // This simulates an offline user interaction modifying records post-failure.
                launch {
                    val sampleEntityList = listOf(
                        BulletinEntity(
                            id = 99,
                            userId = 1,
                            title = "Local Addition",
                            post = "Testing stream resilience.",
                            timestamp = "2026-10-10T12:00:00Z",
                            isBookmark = false
                        )
                    )
                    daoMockStream.value = sampleEntityList
                }

                // Flush the scheduler one final time to step the clock and dispatch the mutation task
                testScheduler.advanceUntilIdle()

                // Event 3: The Ultimate Resiliency Proof!
                // Turbine cleanly intercepts the database write because the stream stayed 100% alive!
                val resilientCacheEmission = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(resilientCacheEmission)
                assertFalse(resilientCacheEmission.isSyncing)
                assertEquals(1, resilientCacheEmission.data, "Stream successfully caught the mutation post-crash!")
                assertIs<HttpException>(resilientCacheEmission.syncError, "The background network error info persists")

                cancelAndIgnoreRemainingEvents()
            }

            // 3. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }
}
