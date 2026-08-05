package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.LogoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ForceLogoutUseCaseTest {

    private val logoutRepository: LogoutRepository = mockk()
    private lateinit var useCase: ForceLogoutUseCase // under test

    @Before
    fun setUp() {
        useCase = ForceLogoutUseCase(logoutRepository)
    }

    @Test
    fun `invoke delegates the force logout to the repository correctly`() = runTest {

        coEvery { logoutRepository.forceLogout() } returns Unit

        // Act
        useCase.invoke()

        // Assert
        coVerify(exactly = 1) {
            logoutRepository.forceLogout()
        }
    }
}