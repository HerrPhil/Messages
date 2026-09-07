package com.reference.implementation.messages.di

import android.util.Log
import com.reference.implementation.data.dtos.toDto
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.repositoryimpl.RefreshTokenRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.use_case.ForceLogoutUseCase
import com.reference.implementation.domain.use_case.RefreshTokenUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

@OptIn(ExperimentalCoroutinesApi::class)
class TokenAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService

    private val mockAccessTokenManager: AccessTokenManager = mockk(relaxed = true)
    private val mockForceLogoutUseCase: ForceLogoutUseCase = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val json: Json
        get() {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            return json
        }

    @Before
    fun setUp() {

        // Mock static calls to android.util.Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.isLoggable(any(), any()) } returns false

        mockWebServer = MockWebServer()
        mockWebServer.start()

        // 1. Initial storage state: Returns old expired token
        var currentToken = "expired_access_token_123"
        every { mockAccessTokenManager.getToken() } answers { currentToken }
        every { mockAccessTokenManager.saveToken(any()) } answers {
            currentToken = firstArg()
            true
        }

        // 2. Build Retrofit / OkHttp stack wired with real Interceptors & Authenticator
        val baseUrl = mockWebServer.url("/").toString()
        val contentType = "application/json".toMediaType()
        val jsonConverterFactory = json.asConverterFactory(contentType)

        // Bare Retrofit instance for RefreshTokenRepositoryImpl
        // Create temporary Retrofit instance for UseCase dependency resolution
        val refreshApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(jsonConverterFactory) // Use Kotlinx Serialization!
            .build()
            .create(ApiService::class.java)

        val repository = RefreshTokenRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = refreshApi,
            accessTokenManager = mockAccessTokenManager,
            refreshTokenManager = mockk(relaxed = true)
        )

        val refreshTokenUseCase = RefreshTokenUseCase(repository)

        // 3. Instantiate Authenticator with provider lambdas
        val tokenAuthenticator = TokenAuthenticator(
            externalScope = testScope,
            refreshTokenUseCaseProvider = { refreshTokenUseCase },
            forceLogoutUseCaseProvider = { mockForceLogoutUseCase }
        )

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(mockAccessTokenManager))
            .authenticator(tokenAuthenticator)
            .build()

        // Main Retrofit instance for ApiService
        apiService = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(jsonConverterFactory) // Use Kotlinx Serialization!
            .build()
            .create(ApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `when 401 occurs, authenticator refreshes token and retries request with new token`() =
        runTest {
            // 1. Queue sequence of responses:
            // Attempt 1: 401 Unauthorized for getMessages
            mockWebServer.enqueue(MockResponse().setResponseCode(401))

            // Attempt 2: 200 OK for the token refresh endpoint call
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"accessToken": "new_refreshed_token_789"}""")
            )

            // Attempt 3: 200 OK for the retried getMessages call
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(createSampleDomainMessages())
            )

            // 2. Execute original API call
            val response = apiService.getMessages(userId = 42)

            // ASSERTIONS:
            // A. Final response delivered to caller is successful
            assertEquals(200, response.code())

            // B. Inspect First Request (Initial call with expired token)
            val request1 = mockWebServer.takeRequest()
            assertEquals("/messages/userId/42", request1.path)
            // Confirms the AuthInterceptor correctly attaches the initial Bearer token
            assertEquals(
                "Bearer expired_access_token_123",
                request1.getHeader("Authorization")
            )

            // C. Inspect Second Request (Token Refresh call executed by Authenticator)
            val request2 = mockWebServer.takeRequest()
            assertEquals("/auth/refresh", request2.path)

            // D. Inspect Third Request (Retried call automatically dispatched with new token)
            val request3 = mockWebServer.takeRequest()
            assertEquals("/messages/userId/42", request3.path)
            // Confirms the TokenAuthenticator correctly attaches the new Bearer token.
            // That is, the expired token set by AuthInterceptor
            // is replaced by the refreshed token fetched by TokenAuthenticator.
            assertEquals(
                "Bearer new_refreshed_token_789",
                request3.getHeader("Authorization")
            )
        }

    @Test
    fun `when 3 concurrent requests return 401, only ONE refresh call is executed`() =
        runTest {

            // 1. Initial 401s for 3 concurrent incoming requests
            mockWebServer.enqueue(MockResponse().setResponseCode(401))
            mockWebServer.enqueue(MockResponse().setResponseCode(401))
            mockWebServer.enqueue(MockResponse().setResponseCode(401))

            // 2. Response for the single token refresh call (delayed slightly  to hold the lock open)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"accessToken": "concurrent_refreshed_token_999"}""")
                    .setBodyDelay(100, TimeUnit.MILLISECONDS)
            )

            // 3. Response for the 3 retried requests
            val sampleJson = createSampleDomainMessages()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(sampleJson)
            )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(sampleJson)
            )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(sampleJson)
            )

            // 4. Fire 3 request in parallel on background threads
            withContext(Dispatchers.IO) {
                val deferred1 = async { apiService.getMessages(userId = 42) }
                val deferred2 = async { apiService.getMessages(userId = 42) }
                val deferred3 = async { apiService.getMessages(userId = 42) }

                val res1 = deferred1.await()
                val res2 = deferred2.await()
                val res3 = deferred3.await()

                // Assert all 3 calls ultimately succeeded
                assertEquals(200, res1.code())
                assertEquals(200, res2.code())
                assertEquals(200, res3.code())
            }

            // 5. Inspect total requests captured by MockWebServer
            val capturedRequests = (1..7).map { mockWebServer.takeRequest() }

            // Count how many calls hit the /auth/refresh endpoint
            val refreshRequests = capturedRequests.filter { it.path == "/auth/refresh" }

            // ASSERTION: Exactly 1 refresh request was made across all 3 failing threads!
            assertEquals(
                1,
                refreshRequests.size,
                "Expected exactly 1 refresh call, but found ${refreshRequests.size}"
            )

            // Verify all retried calls used the newly refreshed token.
            // That is, the expired token set by AuthInterceptor
            // is replaced by the refreshed token fetched by TokenAuthenticator.
            val retriedRequests = capturedRequests.filter {
                it.path == "/messages/userId/42" && it.getHeader("Authorization") == "Bearer concurrent_refreshed_token_999"
            }
            assertEquals(3, retriedRequests.size, "All 3 retried requests must use the new token")
        }

    @Test
    fun `when refresh returns 403, authenticator aborts retry and invokes ForceLogoutUseCase`() =
        runTest {

            // Enqueue 401 Unauthorized for the initial resource request
            mockWebServer.enqueue(
                MockResponse().setResponseCode(401)
            )

            // 2. Enqueue 403 Forbidden for the token refresh attempt
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message": "Refresh token expired or revoked}""")
            )

            // 3. Execute the initial API call
            val response = apiService.getMessages(userId = 42)

            // 4. Advance time or yield to allow externalScope coroutine jobs to execute
            testScope.testScheduler.advanceUntilIdle()

            // ASSERTIONS

            // A. The original call returns the 401 response (retry was aborted)
            //    Specifically, the repo.refreshToken() in RefreshTokenUseCase!
            assertEquals(401, response.code())

            // B. Verify exactly 2 requests reached  the server (Initial Call + Refresh Attempt)
            val request1 = mockWebServer.takeRequest()
            assertEquals("/messages/userId/42", request1.path)

            val request2 = mockWebServer.takeRequest()
            assertEquals("/auth/refresh", request2.path)

            // Ensure NO third retried request was made to /messages/userId/42
            assertEquals(2, mockWebServer.requestCount)

            // C. Verify ForceLogoutUseCase was executed on the externalScope
            coVerify(exactly = 1) { mockForceLogoutUseCase.invoke() }
        }

// *********************************************************************************************
// *********************************************************************************************
// *********************************************************************************************
// *********************************************************************************************

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