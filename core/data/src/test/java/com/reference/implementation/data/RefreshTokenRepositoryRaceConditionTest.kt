package com.reference.implementation.data

import android.util.Log
import com.reference.implementation.data.dtos.RefreshTokenDto
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.manager.RefreshTokenManager
import com.reference.implementation.data.repositoryimpl.RefreshTokenRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshTokenRepositoryRaceConditionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApi: ApiService = mockk()
    private val mockAccessTokenManager: AccessTokenManager = mockk(relaxed = true)
    private val mockRefreshTokenManager: RefreshTokenManager = mockk(relaxed = true)
    private lateinit var repository: RefreshTokenRepositoryImpl

    @Before
    fun setUp() {

        // Mock static calls to android.util.Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.isLoggable(any(), any()) } returns false

        repository = RefreshTokenRepositoryImpl(
            ioDispatcher = testDispatcher,
            apiService = mockApi,
            accessTokenManager = mockAccessTokenManager,
            refreshTokenManager = mockRefreshTokenManager
        )
    }

    @After
    fun tearDown() {
        // clean up static mocks to prevent pollution across test files
        unmockkStatic(Log::class)
    }

    @Test
    fun `concurrent refreshToken calls perform only one network request and deduplicate results`() =
        runTest(testDispatcher) {
            val refreshToken = "refresh-token-3456"
            val expiredToken = "expired_access_token"
            val newToken = "new_refreshed_access_token"

            // 1. provide the refresh token
            coEvery { mockRefreshTokenManager.getToken() } returns refreshToken

            // 2. Storage initially has the expired token
            var storedToken = expiredToken
            coEvery { mockAccessTokenManager.getToken() } answers {
                storedToken
            }
            coEvery { mockAccessTokenManager.saveToken(any()) } answers {
                storedToken = firstArg()
            }

            // 2. Use CompletableDeferred to freeze the API response mid-flight
            val networkGate = CompletableDeferred<Response<RefreshTokenDto>>()
            coEvery { mockApi.refreshAccessToken(any()) } coAnswers {
                networkGate.await() // Holds the coroutine until we manually complete it
            }

            // 3. Launch Request A concurrently
            val requestA = async {
                repository.refreshToken(tokenUsedByRequest = expiredToken, onRetry = {})
            }
            runCurrent() // Yield execution so Request A reaches the networkGate.await() line

            // 4. Launch Request B concurrently with the same expired token
            val requestB = async {
                repository.refreshToken(tokenUsedByRequest = expiredToken, onRetry = {})
            }
            runCurrent() // Request B now enters the method

            // VERIFY MID-FLIGHT STATE:
            // Even though 2 requests were initiated, the network API was called ONLY ONCE
            coVerify(exactly = 1) { mockApi.refreshAccessToken(any()) }

            // 5. Unblock the network gate to complete the HTTP request
            val successResponseBody = RefreshTokenDto(accessToken = newToken)
            networkGate.complete(Response.success(successResponseBody))

            // 6. Resume all suspended coroutines to completion
            runCurrent()

            // 7. Assert both requests received the same successful result
            val resultA = requestA.await()
            val resultB = requestB.await()

            assertTrue(resultA is NetworkResult.Success)
            assertTrue(resultB is NetworkResult.Success)
            assertEquals(newToken, (resultA as NetworkResult.Success).data.newAccessToken)
            assertEquals(newToken, (resultB as NetworkResult.Success).data.newAccessToken)

            // FINAL VERIFICATION: Network was still called only once across both coroutines
            coVerify(exactly = 1) { mockApi.refreshAccessToken(any()) }
        }

    @Test
    fun `trailing request short-circuits immediately if storage was updated by earlier request`() =
        runTest(testDispatcher) {
            val expiredToken = "expired_token_123"
            val newToken = "already_refreshed_token_456"

            // Local storage has ALREADY been updated to newToken by another thread
            coEvery { mockAccessTokenManager.getToken() } returns newToken

            // Trailing request arrives with the OLD token it held when its HTTP call failed 401
            val result = repository.refreshToken(tokenUsedByRequest = expiredToken, onRetry = {})

            // Should exit early without calling API
            assertTrue(result is NetworkResult.Success)
            assertEquals(newToken, (result as NetworkResult.Success).data.newAccessToken)

            coVerify(exactly = 0) { mockApi.refreshAccessToken(any()) }
        }
}