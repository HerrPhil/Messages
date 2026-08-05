package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
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
import java.util.concurrent.TimeoutException

class LoginUseCaseTest {

    private val loginRepository: LoginRepository = mockk()
    private lateinit var useCase: LoginUseCase // under test

    @Before
    fun setUp() {
        useCase = LoginUseCase(loginRepository)
    }

    @Test
    fun `invoke maps NetworkResult Success to the Resource Success correctly`() = runTest {

        val email = "test@turbine.com"
        val name = "qa tester"

        val loginUser = LoginUserDomainModel(
            email = email,
            name = name
        )

        coEvery { loginRepository.login(any(), any(), any()) } returns NetworkResult.Success(
            loginUser
        )

        // Act
        val resourceResult = useCase.invoke(email, name, {})

        // Assert
        coVerify(exactly = 1) {
            loginRepository.login(eq(email), eq(name), any())
        }
        assertTrue(resourceResult is Resource.Success)
        val loggedInUser = (resourceResult as Resource.Success).data
        assertEquals(email, loggedInUser.email)
        assertEquals(name, loggedInUser.name)
    }

    @Test
    fun `invoke maps NetworkResult Error to the Resource correctly`() = runTest {

        val email = "test@turbine.com"
        val name = "qa tester"

        val serviceUnavailableCode = 503
        val serviceUnavailableMessage = "Service unavailable"

        coEvery { loginRepository.login(any(), any(), any()) } returns NetworkResult.Error(
            serviceUnavailableCode,
            serviceUnavailableMessage
        )

        // Act
        val resourceResult = useCase.invoke(email, name, {})

        // Assert
        coVerify(exactly = 1) {
            loginRepository.login(eq(email), eq(name), any())
        }
        assertTrue(resourceResult is Resource.Error)
        val message = (resourceResult as Resource.Error).message
        assertEquals("Login is unavailable", message)
    }

    @Test
    fun `invokes maps NetworkResult IO Exception to Resource Error correctly`() = runTest {

        val email = "test@turbine.com"
        val name = "qa tester"

        val error = "Network down"
        val networkDown = IOException(error)

        coEvery { loginRepository.login(any(), any(), any()) } returns NetworkResult.Exception(
            networkDown
        )

        // Act
        val resourceResult = useCase.invoke(email, name, {})

        // Assert
        coVerify(exactly = 1) { loginRepository.login(eq(email), eq(name), any()) }
        val message = (resourceResult as Resource.Error).message
        assertEquals("No internet connection", message)
    }

    @Test
    fun `invokes maps NetworkResult HTTP Exception to Resource Error correctly`() = runTest {

        val email = "test@turbine.com"
        val name = "qa tester"

        val httpException = mockk<HttpException>()

        coEvery { loginRepository.login(any(), any(), any()) } returns NetworkResult.Exception(
            httpException
        )
        coEvery { httpException.code() } returns 500

        // Act
        val resourceResult = useCase.invoke(email, name, {})

        // Assert
        coVerify(exactly = 1) { loginRepository.login(eq(email), eq(name), any()) }
        val message = (resourceResult as Resource.Error).message
        assertEquals("Server is having a bad day", message)
    }

    @Test
    fun `invokes maps NetworkResult Other Exception to Resource Error correctly`() = runTest {

        val email = "test@turbine.com"
        val name = "qa tester"

        val timeoutException = mockk<TimeoutException>()

        coEvery { loginRepository.login(any(), any(), any()) } returns NetworkResult.Exception(
            timeoutException
        )

        // Act
        val resourceResult = useCase.invoke(email, name, {})

        // Assert
        coVerify(exactly = 1) { loginRepository.login(eq(email), eq(name), any()) }
        val message = (resourceResult as Resource.Error).message
        assertEquals("Unknown error occurred", message)
    }

}
