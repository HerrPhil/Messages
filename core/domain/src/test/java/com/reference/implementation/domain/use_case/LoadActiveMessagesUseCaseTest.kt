package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LoadActiveMessagesUseCaseTest {

    private val messageCacheRepository: MessageCacheRepository = mockk()
    private lateinit var useCase: LoadActiveMessagesUseCase // under test

    @Before
    fun setUp() {
        useCase = LoadActiveMessagesUseCase(messageCacheRepository)
    }

    @Test
    fun `invoke delegates the refresh all bulletins to the repository correctly`() = runTest {

        coEvery { messageCacheRepository.refreshMessagesOfActiveUser(any()) } returns Unit

        // Act
        useCase.invoke({})

        // Assert
        coVerify(exactly = 1) {
            messageCacheRepository.refreshMessagesOfActiveUser(any())
        }
    }
}