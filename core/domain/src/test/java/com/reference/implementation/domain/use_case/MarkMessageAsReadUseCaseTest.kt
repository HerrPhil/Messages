package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkMessageAsReadUseCaseTest {

    private val messageCacheRepository: MessageCacheRepository = mockk()
    private lateinit var useCase: MarkMessageAsReadUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkMessageAsReadUseCase(messageCacheRepository)
    }

    @Test
    fun `markAsRead calls repository with exact message ID`() = runTest {
        // Arrange
        val targetMessageId = 456
        coEvery { messageCacheRepository.markMessageAsRead(targetMessageId) } returns Unit

        // Act
        useCase.invoke(messageId = targetMessageId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            messageCacheRepository.markMessageAsRead(eq(targetMessageId))
        }
    }
}