package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.LogoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LogoutUseCaseTest {

    private val logoutRepository: LogoutRepository = mockk()
    private lateinit var useCase: LogoutUseCase // under test

    @Before
    fun setUp() {
        useCase = LogoutUseCase(logoutRepository)
    }

    @Test
    fun `invoke delegates the logout to the repository correctly`() = runTest {

        coEvery { logoutRepository.logout() } returns Unit

        // Act
        useCase.invoke()

        // Assert
        coVerify(exactly = 1) {
            logoutRepository.logout()
        }
    }
}