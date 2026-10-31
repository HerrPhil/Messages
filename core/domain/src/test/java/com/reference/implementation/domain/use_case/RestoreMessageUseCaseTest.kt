package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageCacheRepository
import com.reference.implementation.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RestoreMessageUseCaseTest {

    private val messageRepository: MessageRepository = mockk()
    private lateinit var useCase: RestoreMessageUseCase // under test

    @Before
    fun setUp() {
        useCase = RestoreMessageUseCase(messageRepository)
    }

    @Test
    fun `mock restoreMessage calls repository with exact message domain model`() = runTest {
        // Arrange
        val deletedMessage = mockk<MessageDomainModel>()
        coEvery { messageRepository.restoreMessage(deletedMessage) } returns Unit

        // Act
        useCase.invoke(deletedMessage = deletedMessage)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            messageRepository.restoreMessage(eq(deletedMessage))
        }
    }
}