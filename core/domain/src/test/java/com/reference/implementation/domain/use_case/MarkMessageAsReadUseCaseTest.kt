package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkMessageAsReadUseCaseTest {

    private val messageRepository: MessageRepository = mockk()
    private lateinit var useCase: MarkMessageAsReadUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkMessageAsReadUseCase(messageRepository)
    }

    @Test
    fun `markAsRead calls repository with exact message ID`() = runTest {
        // Arrange
        val targetMessageId = 456
        coEvery { messageRepository.markMessageAsRead(targetMessageId) } returns Unit

        // Act
        useCase.invoke(messageId = targetMessageId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            messageRepository.markMessageAsRead(eq(targetMessageId))
        }
    }
}