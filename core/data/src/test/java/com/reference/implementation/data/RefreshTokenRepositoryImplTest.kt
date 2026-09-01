package com.reference.implementation.data

import android.util.Log
import com.reference.implementation.data.dtos.RefreshTokenDto
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.manager.RefreshTokenManager
import com.reference.implementation.data.repositoryimpl.RefreshTokenRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.RefreshTokenDomainModel
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RefreshTokenRepositoryImplTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private val accessTokenManager: AccessTokenManager = mockk(relaxed = true)
    private val refreshTokenManager: RefreshTokenManager = mockk(relaxed = true)
    private lateinit var repository: RefreshTokenRepositoryImpl
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
        mockWebServer.start()

        val contentType = "application/json".toMediaType()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)

        repository = RefreshTokenRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = apiService,
            accessTokenManager = accessTokenManager,
            refreshTokenManager = refreshTokenManager
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `refreshToken when the token in storage is different from the token used by request then return token in storage`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = "access-token-1234"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage

            var retryCount = 0

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Success & Domain Model
            assertIs<NetworkResult.Success<RefreshTokenDomainModel>>(result)
            val domainModel = result.data
            assertIs<RefreshTokenDomainModel>(domainModel)
            val actual = domainModel.newAccessToken
            assertEquals(tokenInStorage, actual)

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
            }

            // Never reaches API request
            assertEquals(0, retryCount)
            assertEquals(0, mockWebServer.requestCount)

        }

    @Test
    fun `refreshToken retries 500 error 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"
            val newAccessToken = "new-access-token-6789"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleRefreshTokenResponse(newAccessToken)) // Response<RefreshTokenDto>
            )

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Success & Domain Model
            assertIs<NetworkResult.Success<RefreshTokenDomainModel>>(result)
            val domainModel = result.data
            assertIs<RefreshTokenDomainModel>(domainModel)
            val actual = domainModel.newAccessToken
            assertEquals(newAccessToken, actual)

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(3, retryCount, "2 bad request, 1 successful request")
            assertEquals(3, mockWebServer.requestCount, "API contacted 3x")

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken handles malformed JSON string`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer responses (e.g., 1 HTTP 200)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{accessToken = token-1234}""") // Malformed JSON
            )

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Exception>(result)
            assertNotNull(result.e.message)
            val error = result.e.message
            assertTrue(
                actual =
                    error?.startsWith("Unexpected JSON token at offset 1")
                        ?: false
            )

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(0, retryCount, "NO retries")
            assertEquals(1, mockWebServer.requestCount, "API contacted 1x")

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken handles empty body of new access token`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("") // empty new access token response from server
            )

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Exception>(result)
            assertNotNull(result.e.message)
            val error = result.e.message
            assertTrue(
                actual =
                    error?.startsWith("Expected start of the object '{'")
                        ?: false
            )

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(3, retryCount, "2 bad request, 1 good request")
            assertEquals(3, mockWebServer.requestCount, "API contacted 3x")

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken handles no body of new access token`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                // no body
            )

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Exception>(result)
            assertNotNull(result.e.message)
            val error = result.e.message
            assertTrue(
                actual =
                    error?.startsWith("Expected start of the object '{'")
                        ?: false
            )

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(3, retryCount, "2 bad request, 1 good request")
            assertEquals(3, mockWebServer.requestCount, "API contacted 3x")

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken handles HTTP 500 3x`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Exception>(result)
            assertEquals("HTTP 500 Server Error", result.e.message)

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(3, retryCount, "3 bad requests")
            assertEquals(3, mockWebServer.requestCount, "API contacted 3x")

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken retries IOException error 2x and recovers to return Success when API returns HTTP 200`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"
            val newAccessToken = "new-access-token-6789"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer responses (e.g., 2 HTTP 500s then 1 HTTP 200)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(createSampleRefreshTokenResponse(newAccessToken)) // Response<RefreshTokenDto>
            )

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Success & Domain Model
            assertIs<NetworkResult.Success<RefreshTokenDomainModel>>(result)
            val domainModel = result.data
            assertIs<RefreshTokenDomainModel>(domainModel)
            val actual = domainModel.newAccessToken
            assertEquals(newAccessToken, actual)

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(3, retryCount, "2 bad request, 1 successful request")
            assertEquals(3, mockWebServer.requestCount, "API contacted 3x")

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken handles IOException 3x`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer responses (e.g., 3 HTTP IOExceptions)
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Exception>(result)
            assertIs<IOException>(result.e)

            val errorMessage = result.e.message
            if (errorMessage != null) {
                assertTrue(
                    errorMessage.startsWith("Connection reset") ||
                            errorMessage.startsWith("unexpected end of stream")
                )
            }

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(3, retryCount, "3 bad requests")
            assertEquals(3, mockWebServer.requestCount, "API contacted 3x")

            // Pop the 2 failed socket attempts
            mockWebServer.takeRequest()
            mockWebServer.takeRequest()

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken fails fast on HTTP 401 without retrying`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer response (HTTP 401)
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Error>(result) // Caught by repository's try/catch block
            assertEquals(401, result.code)
            assertEquals("Client Error", result.message)

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(0, retryCount, "1 HTTP 401")
            assertEquals(1, mockWebServer.requestCount, "API contacted 1x")

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken fails fast on HTTP 403 without retrying`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer response (HTTP 401)
            mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Unauthorized"))

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Error>(result) // Caught by repository's try/catch block
            assertEquals(403, result.code)
            assertEquals("Client Error", result.message)

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(0, retryCount, "1 HTTP 403")
            assertEquals(1, mockWebServer.requestCount, "API contacted 1x")

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken fails fast when service is unavailable`() =
        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"
            val tokenInStorage = tokenUsedByRequest
            val refreshToken = "refresh-token-3456"

            // 1. Mock session returns Authenticated user with ID = 42
            coEvery { accessTokenManager.getToken() } returns tokenInStorage
            coEvery { refreshTokenManager.getToken() } returns refreshToken

            var retryCount = 0

            // 1. Enqueue MockWebServer response (HTTP 503) 3x
            repeat(3) {
                mockWebServer.enqueue(
                    MockResponse().setResponseCode(503).setBody("Message service is unavailable")
                )
            }

            // 2. Perform Refresh Token
            val result = repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = { attempt ->
                    retryCount = attempt
                }
            )

            // 3. Assert Error
            assertIs<NetworkResult.Exception>(result)
            assertEquals("HTTP 503 Server Error", result.e.message)

            // 4. Verify Token Operations & State Updates
            coVerifyOrder {
                accessTokenManager.getToken()
                refreshTokenManager.getToken()
            }

            // API requests
            assertEquals(3, retryCount, "3 HTTP 503")
            assertEquals(3, mockWebServer.requestCount, "API contacted 3x")

            // 7. Verify request payload contract on MockWebServer
            val recordedRequest = mockWebServer.takeRequest(1, TimeUnit.MILLISECONDS)
            assertEquals("POST", recordedRequest?.method)
            assertEquals("/auth/refresh", recordedRequest?.path)
            assertEquals(
                "[text={\"refreshToken\":\"$refreshToken\"}]",
                recordedRequest?.body.toString()
            )

        }

    @Test
    fun `refreshToken cancels cleanly without emitting NetworkResult Exception`() {

        val contentType = "application/json".toMediaType()

        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS) // Strict connect timeout
            .readTimeout(100, TimeUnit.MILLISECONDS)    // Strict read timeout
            .writeTimeout(100, TimeUnit.MILLISECONDS)   // Strict write timeout
            .build();

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)

        repository = RefreshTokenRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = apiService,
            accessTokenManager = accessTokenManager,
            refreshTokenManager = refreshTokenManager
        )

        runTest(testDispatcher) {

            val tokenUsedByRequest = "access-token-6789"

            // 1. Enqueue a delayed response so the call stays suspended on the server
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(3, TimeUnit.SECONDS) // indicate cancellation (timeout)
                    .setBody("""{"accessToken":"token-1234"}""")
            )

            // 2. Perform Refresh Token
            repository.refreshToken(
                tokenUsedByRequest = tokenUsedByRequest,
                onRetry = {}
            )

            // If the repository mistakenly swallowed CancellationException and emitted
            // NetworkResult.Exception, runTest would have thrown an unconsumed event error above!
        }

    }

    private fun createSampleRefreshTokenDto(accessToken: String): RefreshTokenDto {
        return RefreshTokenDto(
            accessToken = accessToken
        )
    }

    // Fabricate the list of messages to get.
    // Notice by making the Json instance create the raw JSON string
    // that it relieves us from balancing array brackets, squiggly braces, and commas
    // of a traditional raw """[...]""" list.
    private fun createSampleRefreshTokenResponse(tokenUnderTest: String): String =
        json.encodeToString(
            createSampleRefreshTokenDto(
                accessToken = tokenUnderTest
            )
        )

}