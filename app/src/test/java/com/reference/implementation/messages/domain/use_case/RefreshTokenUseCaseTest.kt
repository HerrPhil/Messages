package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.RefreshTokenDomainModel
import com.reference.implementation.messages.domain.repository.RefreshTokenRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class RefreshTokenUseCaseTest {

    private val refreshTokenRepository: RefreshTokenRepository = mockk()
    private lateinit var useCase: RefreshTokenUseCase // under test

    @Before
    fun setUp() {
        useCase = RefreshTokenUseCase(refreshTokenRepository)
    }

    @Test
    fun `invokes maps NetworkResult refresh token into Resource Success`() = runTest {
        val tokenUsedByRequest = "secret-access-token-1234"

        val refreshToken = RefreshTokenDomainModel(
            newAccessToken = "new-secret-access-token-0987"
        )

        coEvery { refreshTokenRepository.refreshToken(tokenUsedByRequest) } returns NetworkResult.Success(
            data = refreshToken
        )

        // Act
        val resourceResult = useCase.invoke(tokenUsedByRequest)

        // Assert
        coVerify(exactly = 1) {
            refreshTokenRepository.refreshToken(eq(tokenUsedByRequest))
        }
        assertTrue(resourceResult is Resource.Success)
        val newRefreshToken = (resourceResult as Resource.Success).data
        assertEquals("new-secret-access-token-0987", newRefreshToken.newAccessToken)


    }

    @Test
    fun `invokes maps NetworkResult 403 Error to Resource Error correctly`() = runTest {
        val tokenUsedByRequest = "secret-access-token-1234"

        val forbiddenCode = 403
        val forbiddenMessage = "Token has expired"

        coEvery { refreshTokenRepository.refreshToken(tokenUsedByRequest) } returns NetworkResult.Error(
            forbiddenCode,
            forbiddenMessage
        )

        // Act
        val resourceResult = useCase.invoke(tokenUsedByRequest)

        // Assert
        coVerify(exactly = 1) {
            refreshTokenRepository.refreshToken(eq(tokenUsedByRequest))
        }
        assertTrue(resourceResult is Resource.Error)
        val resourceErrorMessage = (resourceResult as Resource.Error).message
        assertEquals("RefreshTokenUseCase $forbiddenMessage", resourceErrorMessage)

    }

    @Test
    fun `invokes maps NetworkResult other 400 series Error to Resource Error correctly`() =
        runTest {
            val tokenUsedByRequest = "secret-access-token-1234"

            //NetworkResult<RefreshTokenDomainModel>
            val unauthorizedCode = 401
            val unauthorizedMessage = "not authorized"

            coEvery { refreshTokenRepository.refreshToken(tokenUsedByRequest) } returns NetworkResult.Error(
                unauthorizedCode,
                unauthorizedMessage
            )

            // Act
            val resourceResult = useCase.invoke(tokenUsedByRequest)

            // Assert
            coVerify(exactly = 1) {
                refreshTokenRepository.refreshToken(eq(tokenUsedByRequest))
            }
            assertTrue(resourceResult is Resource.Error)
            val resourceErrorMessage = (resourceResult as Resource.Error).message
            assertEquals("RefreshTokenUseCase $unauthorizedMessage", resourceErrorMessage)

        }

    @Test
    fun `invokes maps NetworkResult IO Exception to Resource Error correctly`() = runTest {
        val tokenUsedByRequest = "secret-access-token-1234"

        val message = "Network down"
        val networkDown = IOException(message)

        coEvery { refreshTokenRepository.refreshToken(tokenUsedByRequest) } returns NetworkResult.Exception(
            networkDown
        )

        // Act
        val resourceResult = useCase.invoke(tokenUsedByRequest)

        // Assert
        coVerify(exactly = 1) {
            refreshTokenRepository.refreshToken(eq(tokenUsedByRequest))
        }
        assertTrue(resourceResult is Resource.Error)
        val resourceErrorMessage = (resourceResult as Resource.Error).message
        assertEquals("No internet connection", resourceErrorMessage)

    }

    @Test
    fun `invokes maps NetworkResult HTTP Exception to Resource Error correctly`() = runTest {
        val tokenUsedByRequest = "secret-access-token-1234"

        val httpException = mockk<HttpException>()

        coEvery { refreshTokenRepository.refreshToken(tokenUsedByRequest) } returns NetworkResult.Exception(
            httpException
        )
        coEvery { httpException.code() } returns 400

        // Act
        val resourceResult = useCase.invoke(tokenUsedByRequest)

        // Assert
        coVerify(exactly = 1) {
            refreshTokenRepository.refreshToken(eq(tokenUsedByRequest))
        }
        assertTrue(resourceResult is Resource.Error)
        val resourceErrorMessage = (resourceResult as Resource.Error).message
        assertEquals("Something went wrong", resourceErrorMessage)
    }

    @Test
    fun `invokes maps NetworkResult Other Exception to Resource Error correctly`() = runTest {
        val tokenUsedByRequest = "secret-access-token-1234"

        val illegalAccessException = mockk<IllegalAccessException>()

        coEvery { refreshTokenRepository.refreshToken(tokenUsedByRequest) } returns NetworkResult.Exception(
            illegalAccessException
        )

        // Act
        val resourceResult = useCase.invoke(tokenUsedByRequest)

        // Assert
        coVerify(exactly = 1) {
            refreshTokenRepository.refreshToken(eq(tokenUsedByRequest))
        }
        assertTrue(resourceResult is Resource.Error)
        val resourceErrorMessage = (resourceResult as Resource.Error).message
        assertEquals("Unknown error occurred", resourceErrorMessage)
    }
}