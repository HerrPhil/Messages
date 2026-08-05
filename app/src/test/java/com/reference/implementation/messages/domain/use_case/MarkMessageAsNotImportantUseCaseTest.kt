package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkMessageAsNotImportantUseCaseTest {

    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: MarkMessageAsNotImportantUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkMessageAsNotImportantUseCase(userPreferencesRepository)
    }

    @Test
    fun `markAsNotImportant calls repository with exact message ID`() = runTest {
        // Arrange
        val targetMessageId = 456
        coEvery { userPreferencesRepository.markMessageAsNotImportant(targetMessageId) } returns Unit

        // Act
        useCase.invoke(messageId = targetMessageId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            userPreferencesRepository.markMessageAsNotImportant(eq(targetMessageId))
        }
    }
}