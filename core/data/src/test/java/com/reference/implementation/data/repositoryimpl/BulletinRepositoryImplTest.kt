package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.BulletinDao
import com.reference.implementation.database.model.BulletinEntity
import com.reference.implementation.domain.model.CachePayload
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class BulletinRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService
    // THE IMMUTABLE FIX: Mock the DAO contract interfaces natively!
    private lateinit var bulletinDao: BulletinDao
    // A hot stream placeholder that perfectly mimics Room's active table emissions
    private lateinit var daoMockStream: MutableStateFlow<List<BulletinEntity>>
    private lateinit var repository: BulletinRepositoryImpl
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
        bulletinDao = mockk(relaxed = true)
        daoMockStream = MutableStateFlow(emptyList())

        // Bind the mock use case getter to pipe our StateFlow records natively!
        every { bulletinDao.getBulletinsStream() } returns daoMockStream

        // Mock the bookmarks preservation map lookup to return an empty baseline array
        coEvery { bulletinDao.getBookmarkedIds() } returns emptyList()

        mockWebServer = MockWebServer()
        mockWebServer.start() // automated tests should always use port 0 to avoid flakiness

        val contentType = "application/json".toMediaType()
        api = Retrofit.Builder()
            // The url() function implicitly starts the mockWebServer, if not started.
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)

        repository = BulletinRepositoryImpl(
            apiService = api,
            ioDispatcher = testDispatcher,
            bulletinDao = bulletinDao, // PASS THE REAL IN-MEMORY DAO HERE
            externalScope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        mockWebServer.close()
        unmockkStatic(Log::class)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getBulletinCount emits Loading then fetches from network and updates local cache query stream`() =
        runTest(testDispatcher) {

            // Given the JSON response of bulletins
            val jsonResponse = """
                [
                    {
                        "id": 6,
                        "userId": 1,
                        "title": "Software Patch",
                        "post": "Security patch enforcement test.",
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

            // 2. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { bulletinDao.insertBulletins(any()) } answers {
                val incomingList = firstArg<List<BulletinEntity>>()
                daoMockStream.value = incomingList // Update the hot stream immediately!
            }

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Room fills infinite pipeline with new bulletins.
                val successItem = awaitItem()

                // 6. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<Int>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized bulletins"
                )
                assertEquals(
                    1,
                    successItem.data,
                    "Room counts 1 bulletin"
                )
                assertEquals(null, successItem.syncError) // no problems for successful syncs!

                // 7. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { bulletinDao.insertBulletins(any()) }

            // 8. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
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

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Synchronization of bulletins updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    0,
                    errorItem.data,
                    "Room counts 0 bulletins"
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
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { bulletinDao.insertBulletins(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `handles empty body of bulletin count`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("") // empty body
            )

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Synchronization of bulletins updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    0,
                    errorItem.data,
                    "Room counts 0 bulletins"
                )
                assertIs<JsonDecodingException>(errorItem.syncError) // different exception
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual = errorMessage
                            .startsWith("Expected start of the array '['")
                    )
                } else {
                    fail("expected an error message")
                }

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { bulletinDao.insertBulletins(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `handles no body of bulletin count`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                // NO BODY
            )

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Synchronization of bulletins updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    0,
                    errorItem.data,
                    "Room counts 0 bulletins"
                )
                assertIs<JsonDecodingException>(errorItem.syncError) // different exception
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual = errorMessage
                            .startsWith("Expected start of the array '['")
                    )
                } else {
                    fail("expected an error message")
                }

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { bulletinDao.insertBulletins(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @Test
    fun `getBulletinCount emits Loading then returns Error when API returns HTTP 500 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP 500s)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Synchronization of bulletins updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    0,
                    errorItem.data,
                    "Room counts 0 bulletins"
                )
                assertIs<HttpException>(errorItem.syncError)
                assertEquals("HTTP 500 Server Error", errorItem.syncError?.message)

                // 6. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { bulletinDao.insertBulletins(any()) }

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

            // 2. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { bulletinDao.insertBulletins(any()) } answers {
                val incomingList = firstArg<List<BulletinEntity>>()
                daoMockStream.value = incomingList // Update the hot stream immediately!
            }

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Room fills infinite pipeline with new bulletins.
                val successItem = awaitItem()

                // 6. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<Int>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized bulletins"
                )
                assertEquals(
                    1,
                    successItem.data,
                    "Room counts 1 bulletin"
                )
                assertEquals(null, successItem.syncError) // no problems for successful syncs!

                // 7. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 8. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/", recordedRequest?.path)
        }

    @Test
    fun `getBulletinCount emits Loading then retries IOException and returns Exception when API returns IOException 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP IO Exceptions)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Synchronization of bulletins updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    0,
                    errorItem.data,
                    "Room counts 0 bulletins"
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
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { bulletinDao.insertBulletins(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/", recordedRequest?.path)
        }

    @Test
    fun `refreshBulletins fails fast on HTTP 401 without retrying`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 1 HTTP 401 Unauthorized error)
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Synchronization of bulletins updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    0,
                    errorItem.data,
                    "Room counts 0 bulletins"
                )
                assertIs<HttpException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                val errorCode = (errorItem.syncError as? HttpException)?.code()
                if (errorMessage != null) {
                    assertEquals("HTTP 401 Client Error", errorMessage)
                    assertEquals(401, errorCode)
                }

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { bulletinDao.insertBulletins(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @Test
    fun `getBulletinCount fails fast on HTTP 403 without retrying`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 1 HTTP 403 Forbidden error)
            mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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
                // Synchronization of bulletins updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    0,
                    errorItem.data,
                    "Room counts 0 bulletins"
                )
                assertIs<HttpException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                val errorCode = (errorItem.syncError as? HttpException)?.code()
                if (errorMessage != null) {
                    assertEquals("HTTP 403 Client Error", errorMessage)
                    assertEquals(403, errorCode)
                }

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 0) { bulletinDao.insertBulletins(any()) }

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @Test
    fun `getBulletinCount cancels cleanly without emitting SyncError in CachePayload Exception`() =
        runTest(testDispatcher) {
            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(5, TimeUnit.SECONDS)
                    .setBody("""[{"id":"1"}]""")
            )

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletinCount().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<Int>>(loadingItem)
                assertEquals(
                    0,
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

}
