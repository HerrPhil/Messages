package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkMessageAsImportantUseCaseTest {

    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: MarkMessageAsImportantUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkMessageAsImportantUseCase(userPreferencesRepository)
    }

    @Test
    fun `markAsImportant calls repository with exact message ID`() = runTest {
        // Arrange
        val targetMessageId = 456
        coEvery { userPreferencesRepository.markMessageAsImportant(targetMessageId) } returns Unit

        // Act
        useCase.invoke(messageId = targetMessageId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            userPreferencesRepository.markMessageAsImportant(eq(targetMessageId))
        }
    }
}