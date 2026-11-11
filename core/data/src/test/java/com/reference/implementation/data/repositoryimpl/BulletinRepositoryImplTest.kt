package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.BulletinDto
import com.reference.implementation.data.mappers.toBulletinDomainModel
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.BulletinDao
import com.reference.implementation.database.model.BulletinEntity
import com.reference.implementation.domain.model.BulletinDomainModel
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
import kotlin.test.assertNull
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
    private lateinit var daoBulletinByIdMockStream: MutableStateFlow<BulletinEntity?>
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
        daoBulletinByIdMockStream = MutableStateFlow(null)

        // Bind the mock use case getter to pipe our StateFlow records natively!
        every { bulletinDao.getBulletinsStream() } returns daoMockStream

        // Bind the mock DAO getter to pipe our StateFlow entity natively!
        every { bulletinDao.getBulletinByIdStream(any()) } returns daoBulletinByIdMockStream

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

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDtoBulletinsEncodedString())
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
                    3,
                    successItem.data,
                    "Room counts 3 bulletins"
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
    fun `getBulletinCount handles malformed JSON string`() =
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
    fun `getBulletinCount handles empty body of bulletin count`() =
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
    fun `getBulletinCount handles no body of bulletin count`() =
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

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP IOExceptions  then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDtoBulletinsEncodedString())
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
                    3,
                    successItem.data,
                    "Room counts 3 bulletins"
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
    fun `getBulletinCount fails fast on HTTP 401 without retrying`() =
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

    // ############################################################################################
    // ############################################################################################
    // ############################################################################################
    // ############################################################################################

    @Test
    fun `getBulletins emits Loading and sees 2 HTTP 500s then fetches from the network and updates local cache query stream`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDtoBulletinsEncodedString()) // create 3 sample bulletins
            )

            // 2. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { bulletinDao.insertBulletins(any()) } answers {
                val incomingList = firstArg<List<BulletinEntity>>()
                assertIs<List<BulletinEntity>>(incomingList) // dto-to-entity successful
                daoMockStream.value = incomingList // mimic stream of fake dao query
            }

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialList = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialList,
                    loadingItem.data,
                    "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 4. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 5. Modern Data Sync Success!
                // The sync task hits the 200 payload
                // DAO updates the list.
                // Room fills infinite pipeline with new bulletins.
                val successItem = awaitItem()

                // 6. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized bulletins"
                )
                val expectedListOfDomainModels =
                    createSampleDtoBulletins().map { it.toBulletinDomainModel() }
                assertEquals(
                    expected = expectedListOfDomainModels,
                    actual = successItem.data,
                    message = "Room gets 3 bulletins"
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
    fun `getBulletins handles malformed JSON string`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{ "oranges" : 789 }""")
            )

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    expected = initialDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError)

                // 4. Pump the virtual clock frame to execute the retryIO delayer block completely!
                testScheduler.advanceUntilIdle()

                // 5. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize messages"
                )
                assertEquals(
                    expected = initialDomainModel,
                    actual = errorItem.data,
                    message = "Room counts 0 messages"
                )
                assertIs<JsonDecodingException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual =
                            errorMessage.startsWith(("Unexpected JSON token at offset 0"))
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
    fun `getBulletins emits Loading then emits correct message for no messages when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[]""") // ZERO messages from API getMessages()
            )

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(noResultItem)
                assertFalse(
                    noResultItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    expected = initialDomainModel,
                    actual = noResultItem.data,
                    "Room counts 0 bulletins"
                )
                assertNull(noResultItem.syncError)

                // 6. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // Never reached when retryIO response is NOT successful!
            coVerify(exactly = 1) { bulletinDao.insertBulletins(any()) }

            // 87 Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `getBulletins handles empty 200 OK body safely`() =
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

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    initialDomainModel,
                    errorItem.data,
                    "Room counts 0 bulletins"
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
    fun `getBulletins handles no 200 OK body safely`() =
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

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    expected = initialDomainModel,
                    actual = errorItem.data,
                    "Room counts 0 bulletins"
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
    fun `getBulletins emits Loading then returns Error when API returns HTTP 500 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP 500s)
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(500))
            }

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    initialDomainModel,
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

            // 8. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @Test
    fun `getBulletins emits Loading then retries IOException and recovers to add Success to the cache when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP IOExceptions  then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDtoBulletinsEncodedString())
            )

            // 2. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { bulletinDao.insertBulletins(any()) } answers {
                val incomingList = firstArg<List<BulletinEntity>>()
                assertIs<List<BulletinEntity>>(incomingList) // dto-to-entity successful
                daoMockStream.value = incomingList // mimic stream of fake dao query
            }

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized bulletins"
                )
                val expectedDomainModel =
                    createSampleDtoBulletins().map { it.toBulletinDomainModel() }
                assertEquals(
                    expectedDomainModel,
                    successItem.data,
                    "Room gets 3 bulletins"
                )
                assertEquals(null, successItem.syncError) // no problems for successful syncs!

                // 7. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { bulletinDao.insertBulletins(any()) }

            // 9. Verify request payload contract on MockWebServer

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @Test
    fun `getBulletins emits Loading then retries IOException and returns Exception when API returns IOException 3x`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            }

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    initialDomainModel,
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

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/bulletins", recordedRequest?.path)
        }

    @Test
    fun `getBulletins fails fast on HTTP 401 without retrying`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 1 HTTP 401)
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    initialDomainModel,
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
    fun `getBulletins fails fast on HTTP 403 without retrying`() =
        runTest(testDispatcher) {

            // 1. Enqueue MockWebServer responses (e.g., 1 HTTP 401)
            mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

            // 2. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getBulletins().test {

                // 3. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                val initialDomainModel = emptyList<BulletinDomainModel>()
                assertEquals(
                    initialDomainModel,
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
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() tried to synchronize bulletins"
                )
                assertEquals(
                    initialDomainModel,
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
    fun `getBulletins cancels cleanly without emitting NetworkResult Exception`() =
        runTest(testDispatcher) {

            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(5, TimeUnit.SECONDS) // indicate cancellation (timeout)
                    .setBody("""[{"id":"1"}]""")
            )

            // 2. Observe the flow with Turbine
            repository.getBulletins().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<BulletinDomainModel>>>(loadingItem)
                assertTrue(loadingItem.isSyncing)
                assertEquals(null, loadingItem.syncError) // no problems for successful syncs!

                // 5. Cancel the flow subscriber while suspended on the network call
                cancelAndIgnoreRemainingEvents()
            }

            testScheduler.advanceUntilIdle()

            // If the repository mistakenly swallowed CancellationException and emitted
            // NetworkResult.Exception, Turbine would have thrown an unconsumed event error above!
        }

    // ############################################################################################
    // ############################################################################################
    // ############################################################################################
    // ############################################################################################

    @Test
    fun `getBulletinByIdFlow finds the loaded bulletin by ID`() =
        runTest(testDispatcher) {
            // Arrange
            val bulletinId = 101
            val testEntity = createSampleDtoBulletin(bulletinId).toEntity()
            daoBulletinByIdMockStream.value = testEntity
            // No need to expose and set SyncStatus.
            // Its default value Idle aligns with the list load worked

            // Act
            repository.getBulletinByIdFlow(bulletinId).test {
                val successItem = awaitItem()

                // Assert
                assertIs<CachePayload.ActiveStream<BulletinDomainModel?>>(successItem)
                assertFalse(
                    actual = successItem.isSyncing,
                    message = "bulletin by ID was synced on list screen"
                )
                val expectedDomainModel =
                    createSampleDtoBulletin(bulletinId).toBulletinDomainModel()
                assertEquals(
                    expected = expectedDomainModel,
                    actual = successItem.data,
                    message = "Room found a matching bulletin by ID"
                )
                assertEquals(null, successItem.syncError)

                cancelAndIgnoreRemainingEvents()
            }

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { bulletinDao.getBulletinByIdStream(eq(bulletinId)) }
        }

    @Test
    fun `getBulletinByIdFlow does not find the loaded message by ID`() =
        runTest {
            // Arrange
            val bulletinId = 101
            daoBulletinByIdMockStream.value = null
            // No need to expose and set SyncStatus.
            // Its default value Idle aligns with the list load worked

            // Act
            repository.getBulletinByIdFlow(bulletinId).test {
                val nullItem = awaitItem()

                // Assert
                assertIs<CachePayload.ActiveStream<BulletinDomainModel?>>(nullItem)
                assertFalse(
                    nullItem.isSyncing,
                    "message by ID was synced on list screen"
                )
                val expectedDomainModel = null
                assertEquals(
                    expected = expectedDomainModel,
                    actual = nullItem.data,
                    message = "Room did not find a matching bulletin by ID"
                )
                assertNull(nullItem.syncError)

                cancelAndIgnoreRemainingEvents()
            }

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { bulletinDao.getBulletinByIdStream(eq(bulletinId)) }
        }

    @Test
    fun `getBulletinByIdFlow can see sync error values`() =
        runTest(testDispatcher) {
            // Arrange
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            val bulletinId = 101
            daoBulletinByIdMockStream.value = null

            // MECHANISM: Siphon and consume the list stream to completely flush the sync loop!

            repository.getBulletins().test {
                awaitItem() // 1. Catch the initial loading baseline state
                testScheduler.advanceUntilIdle() // 2. Pump the virtual clock to let syncBulletins() run completely!
                awaitItem() // 3. Wait for the list stream to emit the final error state
                cancelAndIgnoreRemainingEvents()
            }

            // At this exact point in time, syncStatus is guaranteed to be SyncState.Error(401)

            // Act
            repository.getBulletinByIdFlow(bulletinId).test {
                val nullItem = awaitItem()

                // Assert
                assertIs<CachePayload.ActiveStream<BulletinDomainModel?>>(nullItem)
                assertFalse(
                    actual = nullItem.isSyncing,
                    message = "bulletin by ID was synced on list screen"
                )
                val expectedDomainModel = null
                assertEquals(
                    expected = expectedDomainModel,
                    actual = nullItem.data,
                    message = "Room did not find a matching message by ID"
                )
                // THE RESILIENT STANDARD: Code constants are ironclad!
                assertIs<HttpException>(nullItem.syncError)
                val httpException = nullItem.syncError as HttpException

                assertEquals(
                    expected = 401,
                    actual = httpException.code(),
                    message = "The network boundary successfully captured the 401 authentication drop"
                )

                cancelAndIgnoreRemainingEvents()
            }

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { bulletinDao.getBulletinByIdStream(eq(bulletinId)) }
        }

    // ############################################################################################
    // ############################################################################################
    // ############################################################################################
    // ############################################################################################

    // Fabricate the list of messages to get.
    // Notice by making the Json instance create the raw JSON string
    // that it relieves us from balancing array brackets, squiggly braces, and commas
    // of a traditional raw """[...]""" list.
    private fun createSampleDtoBulletin(id: Int): BulletinDto =
        BulletinDto(
            id = 100 + id,
            userId = 42,
            title = "Test Subject",
            post = "Test Body",
            timestamp = "2026-10-22T13:19:29Z"
        )

    private fun createSampleDtoBulletins(): List<BulletinDto> =
        MutableList(3) { index ->
            createSampleDtoBulletin(index)
        }

    private fun createSampleDtoBulletinsEncodedString(): String =
        json.encodeToString(
            value = createSampleDtoBulletins()
        )

}
