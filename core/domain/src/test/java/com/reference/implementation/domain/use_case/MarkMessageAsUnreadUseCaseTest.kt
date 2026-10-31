package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkMessageAsUnreadUseCaseTest {

    private val messageRepository: MessageRepository = mockk()
    private lateinit var useCase: MarkMessageAsUnreadUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkMessageAsUnreadUseCase(messageRepository)
    }

    @Test
    fun `markAsUnread calls repository with exact message ID`() = runTest {
        // Arrange
        val targetMessageId = 456
        coEvery { messageRepository.markMessageAsUnread(targetMessageId) } returns Unit

        // Act
        useCase.invoke(messageId = targetMessageId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            messageRepository.markMessageAsUnread(eq(targetMessageId))
        }
    }
}