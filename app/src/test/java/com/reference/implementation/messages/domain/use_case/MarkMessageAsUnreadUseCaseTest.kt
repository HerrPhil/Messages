package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkMessageAsUnreadUseCaseTest {

    private val messageCacheRepository: MessageCacheRepository = mockk()
    private lateinit var useCase: MarkMessageAsUnreadUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkMessageAsUnreadUseCase(messageCacheRepository)
    }

    @Test
    fun `markAsUnread calls repository with exact message ID`() = runTest {
        // Arrange
        val targetMessageId = 456
        coEvery { messageCacheRepository.markMessageAsUnread(targetMessageId) } returns Unit

        // Act
        useCase.invoke(messageId = targetMessageId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            messageCacheRepository.markMessageAsUnread(eq(targetMessageId))
        }
    }
}