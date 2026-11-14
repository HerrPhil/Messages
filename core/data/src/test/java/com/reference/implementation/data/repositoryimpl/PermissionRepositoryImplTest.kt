package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.PermissionDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.PermissionDao
import com.reference.implementation.database.model.MessageEntity
import com.reference.implementation.database.model.PermissionEntity
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.UserPermissionDomainModel
import io.mockk.coEvery
import io.mockk.coVerify
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

class PermissionRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService

    // THE IMMUTABLE FIX: Mock the DAO contract interfaces natively!
    private lateinit var permissionDao: PermissionDao

    // A hot stream placeholder that perfectly mimics Room's active table emissions
    private lateinit var daoPermissionsMockStream: MutableStateFlow<List<PermissionEntity>>

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
        every { Log.isLoggable(any(), any()) } returns false

        // Instantiate hot mocking data channels
        permissionDao = mockk(relaxed = true)

        // Initialize your list data class stream baseline (empty list aka empty SQLite table)
        daoPermissionsMockStream = MutableStateFlow(emptyList())

        // Bind the mock DAO getter to pipe our StateFlow list natively!
        every { permissionDao.getPermissionsStream() } returns daoPermissionsMockStream

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
            sessionManager = sessionManager,
            permissionDao = permissionDao,
            externalScope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        mockWebServer.close()
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    // ############################################################################################
    // ############################################################################################
    // ############################################################################################
    // ############################################################################################
    // ROOM TESTS

    @Test
    fun `getPermissionsFlow emits Loading and sees 2 HTTP 500s then fetches from the network and updates local cache query stream`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDtoPermissionsEncodedString()) // create 3 sample permissions
            )

            // 3. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { permissionDao.insertPermissions(any()) } answers {
                val incomingList = firstArg<List<PermissionEntity>>()
                assertIs<List<MessageEntity>>(incomingList) // dto-to-entity successful
                daoPermissionsMockStream.value = incomingList // mimic stream of fake dao query
            }

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedLoadingDomainModel = UserPermissionDomainModel(emptyList())
                assertEquals(
                    expected = expectedLoadingDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Modern Data Sync Success!
                // The sync task hits the 200 payload
                // DAO updates the list.
                // Room fills infinite pipeline with new messages.
                val successItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                val expectedDomainModel = UserPermissionDomainModel(
                    permissions = createSamplePermissionDtos().map { it.task }
                )
                assertEquals(
                    expected = expectedDomainModel,
                    actual = successItem.data,
                    message = "Room gets 3 permissions"
                )
                assertEquals(null, successItem.syncError) // no problems for successful syncs!

                // 8. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { permissionDao.insertPermissions(any()) }

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `getPermissionsFlow handles malformed JSON string`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{ "oranges" : 789 }""")
            )

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = errorItem.data,
                    message = "Room gets 0 permissions"
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

                // 8. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { permissionDao.insertPermissions(any()) }

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `getPermissionsFlow emits Loading then emits correct message for no messages when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[]""") // ZERO messages from API getPermissions()
            )

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val noResultItem = awaitItem()
                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(noResultItem)
                assertFalse(
                    noResultItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = noResultItem.data,
                    message = "Room gets 0 permissions"
                )

                // 8. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { permissionDao.insertPermissions(any()) }

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `getPermissionsFlow handles empty 200 OK body safely`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("") // ZERO messages from API getPermissions()
            )

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = errorItem.data,
                    message = "Room gets 0 permissions"
                )
                assertIs<JsonDecodingException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual = errorMessage
                            .startsWith("Expected start of the array '['")
                    )
                } else {
                    fail("expected an error message")
                }

                // 8. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { permissionDao.insertPermissions(any()) }

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `getPermissionsFlow handles no 200 OK body safely`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer response (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                // no body() set
            )

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = errorItem.data,
                    message = "Room gets 0 permissions"
                )
                assertIs<JsonDecodingException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        actual = errorMessage
                            .startsWith("Expected start of the array '['")
                    )
                } else {
                    fail("expected an error message")
                }

                // 8. Assert NO retry callbacks and 1 HTTP request count
                assertEquals(1, mockWebServer.requestCount)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { permissionDao.insertPermissions(any()) }

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionsFlow emits Loading then returns Error when API returns HTTP 500 3x`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer responses (e.g., 3 HTTP 500s)
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(500))
            }

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = errorItem.data,
                    message = "Room gets 0 permissions"
                )
                assertIs<HttpException>(errorItem.syncError)
                assertEquals("HTTP 500 Server Error", errorItem.syncError?.message)

                // 7. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { permissionDao.insertPermissions(any()) }

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionsFlow emits Loading then retries IOException and recovers to add Success to the cache when API returns HTTP 200`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer responses (e.g., 2 HTTP IOExceptions  then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleDtoPermissionsEncodedString()) // create 3 sample permissions
            )

            // 3. Intercept your repository's transactional writes to manually feed our mock channel!
            coEvery { permissionDao.insertPermissions(any()) } answers {
                val incomingList = firstArg<List<PermissionEntity>>()
                assertIs<List<MessageEntity>>(incomingList) // dto-to-entity successful
                daoPermissionsMockStream.value = incomingList // mimic stream of fake dao query
            }

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedLoadingDomainModel = UserPermissionDomainModel(emptyList())
                assertEquals(
                    expected = expectedLoadingDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Modern Data Sync Success!
                // The sync task hits the 200 payload
                // DAO updates the list.
                // Room fills infinite pipeline with new messages.
                val successItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(successItem)
                assertFalse(
                    successItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                val expectedDomainModel = UserPermissionDomainModel(
                    permissions = createSamplePermissionDtos().map { it.task }
                )
                assertEquals(
                    expected = expectedDomainModel,
                    actual = successItem.data,
                    message = "Room gets 3 permissions"
                )
                assertEquals(null, successItem.syncError) // no problems for successful syncs!

                // 8. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { permissionDao.insertPermissions(any()) }

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionsFlow emits Loading then retries IOException and returns Exception when API returns IOException 3x`() =
        runTest(testDispatcher) {


            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer responses (e.g., 3 HTTP IOExceptions)
            repeat(3) {
                mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            }

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = errorItem.data,
                    message = "Room gets 0 permissions"
                )
                assertIs<IOException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                if (errorMessage != null) {
                    assertTrue(
                        errorMessage.startsWith("Connection reset") ||
                                errorMessage.startsWith("unexpected end of stream")
                    )
                }

                // 8. Assert retry callbacks and HTTP request counts
                assertEquals(3, mockWebServer.requestCount, "called api 3 times")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { permissionDao.insertPermissions(any()) }

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionsFlow fails fast on HTTP 401 without retrying`() =
        runTest(testDispatcher) {


            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer responses (e.g., 1 HTTP 401)
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = errorItem.data,
                    message = "Room gets 0 permissions"
                )
                assertIs<HttpException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                val errorCode = (errorItem.syncError as? HttpException)?.code()
                if (errorMessage != null) {
                    assertEquals("HTTP 401 Client Error", errorMessage)
                    assertEquals(401, errorCode)
                }

                // 8. Assert retry callbacks and HTTP request counts
                assertEquals(1, mockWebServer.requestCount, "called api 1 time")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { permissionDao.insertPermissions(any()) }

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionsFlow fails fast on HTTP 403 without retrying`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue MockWebServer responses (e.g., 1 HTTP 401)
            mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

            // 3. Do not intercept your repository's transactional writes
            // to manually feed our mock channel - retryIO limit was reached (no data)!

            repository.getPermissionsFlow().test {

                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Pump the virtual clock frame to execute the retryIO delayer blocks completely
                testScheduler.advanceUntilIdle()

                // 6. Background Data Sync Failure Execution Block!
                // The sync task hits 1 JSON decoding exception - no payload.
                // Combine consolidates baseline 0 entities with syncError
                // Synchronization of messages updates syncError.
                val errorItem = awaitItem()

                // 7. Assert CachePayload state
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(errorItem)
                assertFalse(
                    errorItem.isSyncing,
                    "onStart() successfully synchronized permissions"
                )
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = errorItem.data,
                    message = "Room gets 0 permissions"
                )
                assertIs<HttpException>(errorItem.syncError)
                val errorMessage = errorItem.syncError?.message
                val errorCode = (errorItem.syncError as? HttpException)?.code()
                if (errorMessage != null) {
                    assertEquals("HTTP 403 Client Error", errorMessage)
                    assertEquals(403, errorCode)
                }

                // 8. Assert retry callbacks and HTTP request counts
                assertEquals(1, mockWebServer.requestCount, "called api 1 time")

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { permissionDao.insertPermissions(any()) }

            // 9. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("GET", recordedRequest?.method)
            assertEquals("/permissions?id=101&id=102&id=103", recordedRequest?.path)
        }

    @Test
    fun `getPermissionsFlow cancels cleanly without emitting CachePayload Exception`() =
        runTest(testDispatcher) {

            // 1. Mock session returns permission IDs of the Authenticated user.
            coEvery { sessionManager.getSessionPermissionIds() } returns SessionResult.Authenticated(
                data = listOf(101, 102, 103)
            )

            // 2. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(5, TimeUnit.SECONDS) // indicate cancellation (timeout)
                    .setBody("""[{"id":"1"}]""")
            )

            // 3. Observe the flow with Turbine
            repository.getPermissionsFlow().test {
                // 4. Immediate emission must be Loading baseline
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<UserPermissionDomainModel>>(loadingItem)
                val expectedEmptyDomainModel = UserPermissionDomainModel(permissions = emptyList())
                assertEquals(
                    expected = expectedEmptyDomainModel,
                    actual = loadingItem.data,
                    message = "Room cache contains nothing"
                )
                assertTrue(loadingItem.isSyncing)
                assertNull(loadingItem.syncError)

                // 5. Cancel the flow subscriber while suspended on the network call
                cancelAndIgnoreRemainingEvents()
            }

            testScheduler.advanceUntilIdle()

            // If the repository mistakenly swallowed CancellationException and emitted
            // CachePayload.ActiveStream with syncError,
            // Turbine would have thrown an unconsumed event error above!
        }

// ############################################################################################
// ############################################################################################
// ############################################################################################
// ############################################################################################

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
    private fun createSamplePermissionDtos(): List<PermissionDto> =
        MutableList(3) { index ->
            createSamplePermissionDto(
                id = 100 + index + 1,
                task = "work task #${index + 1}",
                userId = 200 + (index + 1) * 2
            )
        }

    private fun createSampleDtoPermissionsEncodedString(): String =
        json.encodeToString(
            value = createSamplePermissionDtos()
        )
}