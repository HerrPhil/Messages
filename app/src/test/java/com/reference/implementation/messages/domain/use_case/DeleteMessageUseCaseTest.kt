package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteMessageUseCaseTest {

    private val messageCacheRepository: MessageCacheRepository = mockk()
    private lateinit var useCase: DeleteMessageUseCase // under test

    @Before
    fun setUp() {
        useCase = DeleteMessageUseCase(messageCacheRepository)
    }

    @Test
    fun `mock DeleteMessage calls repository with exact message ID`() = runTest {
        // Arrange
        val targetMessageId = 987
        coEvery { messageCacheRepository.deleteMessage(targetMessageId) } returns Unit

        // Act
          useCase.invoke(messageId = targetMessageId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            messageCacheRepository.deleteMessage(eq(targetMessageId))
        }
    }
}