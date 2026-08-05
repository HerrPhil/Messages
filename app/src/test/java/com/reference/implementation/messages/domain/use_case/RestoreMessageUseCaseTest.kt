package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RestoreMessageUseCaseTest {

    private val messageCacheRepository: MessageCacheRepository = mockk()
    private lateinit var useCase: RestoreMessageUseCase // under test

    @Before
    fun setUp() {
        useCase = RestoreMessageUseCase(messageCacheRepository)
    }

    @Test
    fun `mock restoreMessage calls repository with exact message domain model`() = runTest {
        // Arrange
        val deletedMessage = mockk<MessageDomainModel>()
        coEvery { messageCacheRepository.restoreMessage(deletedMessage) } returns Unit

        // Act
        useCase.invoke(deletedMessage = deletedMessage)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            messageCacheRepository.restoreMessage(eq(deletedMessage))
        }
    }
}