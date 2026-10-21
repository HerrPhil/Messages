package com.reference.implementation.data.repositoryimpl

import android.util.Log
import app.cash.turbine.test
import com.reference.implementation.data.dtos.UserDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.UserDao
import com.reference.implementation.database.model.UserEntity
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
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
import java.io.InvalidObjectException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserRepositoryImplTest {

    // Declare the scheduler and dispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: ApiService
    private lateinit var userDao: UserDao // Stub for now!
    private lateinit var daoMockStream: MutableStateFlow<List<UserEntity>>
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private lateinit var repository: UserRepositoryImpl
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
        userDao = mockk(relaxed = true)
        daoMockStream = MutableStateFlow(emptyList())

        every { userDao.getUsersStream() } returns daoMockStream

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
        repository = UserRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = api,
            sessionManager = sessionManager,
            userDao = userDao,
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
    fun `getPermissionInfoFlow emits Loading and when Authenticated return Success`() =
        runTest(testDispatcher) {

            val expectedUserName = "Bob Squarepants"
            val expectedUserEmail = "b.sqrpnts@test.com"
            val expectedUserID = 9999

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } returns SessionResult.Authenticated(
                expectedUserName
            )
            coEvery { sessionManager.getSessionUserEmail() } returns SessionResult.Authenticated(
                expectedUserEmail
            )
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.Authenticated(
                expectedUserID
            )

            repository.getUserInfoFlow().test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<LoginUserDomainModel>>(successItem)
                val data = successItem.data
                assertIs<LoginUserDomainModel>(data)
                val userName = data.name
                val userEmail = data.email
                val userID = data.id
                assertIs<String>(userName)
                assertIs<String>(userEmail)
                assertIs<Int>(userID)
                assertEquals(expectedUserName, userName)
                assertEquals(expectedUserEmail, userEmail)
                assertEquals(expectedUserID, userID)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }
        }

    @Test
    fun `getPermissionInfoFlow emits Loading and when Not Authenticated return Success`() =
        runTest(testDispatcher) {

            val expectedUserName = "no name"
            val expectedUserEmail = "no email"
            val expectedUserID = -1

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } returns SessionResult.NoValue
            coEvery { sessionManager.getSessionUserEmail() } returns SessionResult.NoValue
            coEvery { sessionManager.getSessionUserId() } returns SessionResult.NoValue

            repository.getUserInfoFlow().test {

                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // 5. Assert UI state and total attempt counts
                val successItem = awaitItem()
                assertIs<NetworkResult.Success<LoginUserDomainModel>>(successItem)
                val data = successItem.data
                assertIs<LoginUserDomainModel>(data)
                val userName = data.name
                val userEmail = data.email
                val userID = data.id
                assertIs<String>(userName)
                assertIs<String>(userEmail)
                assertIs<Int>(userID)
                assertEquals(expectedUserName, userName)
                assertEquals(expectedUserEmail, userEmail)
                assertEquals(expectedUserID, userID)

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()

            }
        }

    @Test
    fun `getPermissionInfoFlow emits Loading and when throws CancellationException gracefully cancels`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } throws CancellationException("ending")
            coEvery { sessionManager.getSessionUserEmail() } throws CancellationException("ending")
            coEvery { sessionManager.getSessionUserId() } throws CancellationException("ending")

            repository.getUserInfoFlow().test {
                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // Clean up Turbine collection
                cancelAndIgnoreRemainingEvents()
            }

            // 4. Advance virtual time to drain all delay() calls inside retryIO
            testScheduler.advanceUntilIdle()
        }

    @Test
    fun `getPermissionInfoFlow emits Loading and when throws Exception gracefully cancels`() =
        runTest(testDispatcher) {

            // 1. Mock session returns Authenticated user name and id
            coEvery { sessionManager.getSessionUserName() } throws InvalidObjectException("bad value")
            coEvery { sessionManager.getSessionUserEmail() } throws InvalidObjectException("bad value")
            coEvery { sessionManager.getSessionUserId() } throws InvalidObjectException("bad value")

            repository.getUserInfoFlow().test {
                // Initial state of the cache flow
                assertEquals(NetworkResult.Loading, awaitItem())

                // 4. Advance virtual time to drain all delay() calls inside retryIO
                testScheduler.advanceUntilIdle()

                // Assert fast failure
                val errorItem = awaitItem()
                assertIs<NetworkResult.Exception>(errorItem)
                assertEquals("bad value", errorItem.e.message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // Requires StandardTestDispatcher for resilience test
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getUserCount stream remains open and active after background synchronization encounters network 500 error`() =
        runTest(testDispatcher) {

            // 1. Arrange: Enqueue exactly 3 HTTP 500 failure responses on MockWebServer
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            // 2. Act & Assert: Open the stream subscription channel pipe using Turbine
            repository.getUserCount().test {

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
                assertFalse(
                    errorStateItem.isSyncing,
                    "The background network synchronization has ended"
                )
                assertIs<HttpException>(
                    errorStateItem.syncError,
                    "Exposes the HttpException payload info"
                )
                assertEquals(0, errorStateItem.data, "Cache count remains clean at 0 rows")

                // 💡 CLOCK STEP 2: Trigger an on-demand database write mutation!
                // This simulates an offline user interaction modifying records post-failure.
                launch {
                    val sampleEntityList = listOf(
                        UserEntity(
                            id = 99,
                            name = "test user",
                            email = "test.user@learn.com",
                            22
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
                assertEquals(
                    1,
                    resilientCacheEmission.data,
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
            assertEquals("/users", recordedRequest?.path)
        }

    // Requires StandardTestDispatcher for resilience test
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getUsers stream remains open and active after background synchronization encounters network 500 error`() =
        runTest(testDispatcher) {

            // 1. Arrange: Enqueue exactly 3 HTTP 500 failure responses on MockWebServer
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            // 2. Act & Assert: Open the stream subscription channel pipe using Turbine
            repository.getUsers().test {

                // Event 1: Initial state of the flow must emit loading baseline at time zero
                // Thanks to StandardTestDispatcher, the network hasn't even executed a single line yet!
                val loadingItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<LoginUserDomainModel>>>(loadingItem)
                assertEquals(emptyList(), loadingItem.data, "Room cache contains nothing initially")
                assertTrue(loadingItem.isSyncing, "The sync state begins as true")
                assertEquals(null, loadingItem.syncError)

                // CLOCK STEP 1: Move the virtual test clock forward completely!
                // This drives all retryIO background delays, executes the 3 attempts,
                // and flushes your SyncState.Error state update natively down the pipeline.
                testScheduler.advanceUntilIdle()

                // Event 2: Network Sync Failure State Snapshot!
                // Turbine cleanly intercepts the intermediate network error update frame.
                val errorStateItem = awaitItem()
                assertIs<CachePayload.ActiveStream<List<LoginUserDomainModel>>>(errorStateItem)
                assertFalse(
                    errorStateItem.isSyncing,
                    "The background network synchronization has ended"
                )
                assertIs<HttpException>(
                    errorStateItem.syncError,
                    "Exposes the HttpException payload info"
                )
                assertEquals(
                    emptyList(),
                    errorStateItem.data,
                    "Cache count remains clean at 0 rows"
                )

                val expectedUserEntity = UserEntity(
                    id = 99,
                    name = "test user",
                    email = "test.user@learn.com",
                    22
                )

                // 💡 CLOCK STEP 2: Trigger an on-demand database write mutation!
                // This simulates an offline user interaction modifying records post-failure.
                launch {
                    val sampleEntityList = listOf(expectedUserEntity)
                    daoMockStream.value = sampleEntityList
                }

                // Flush the scheduler one final time to step the clock and dispatch the mutation task
                testScheduler.advanceUntilIdle()

                // Event 3: The Ultimate Resiliency Proof!
                // Turbine cleanly intercepts the database write because the stream stayed 100% alive!
                val resilientCacheEmission = awaitItem()
                assertIs<CachePayload.ActiveStream<List<LoginUserDomainModel>>>(
                    resilientCacheEmission
                )
                assertFalse(resilientCacheEmission.isSyncing)
                assertEquals(
                    listOf(expectedUserEntity).map { it.toDomainModel() },
                    resilientCacheEmission.data,
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
            assertEquals("/users", recordedRequest?.path)
        }

// #############################################################################################
// #############################################################################################
// #############################################################################################
// #############################################################################################

    @Suppress("SameParameterValue")
    private fun createSampleUserDto(id: Int, email: String, name: String, age: Int): UserDto {
        return UserDto(
            id = id,
            email = email,
            name = name,
            age = age
        )
    }
}