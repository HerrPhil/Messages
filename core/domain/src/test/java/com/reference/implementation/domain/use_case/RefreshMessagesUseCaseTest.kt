package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RefreshMessagesUseCaseTest {

    private val messageRepository: MessageRepository = mockk()

    private lateinit var useCase: RefreshMessagesUseCase // under test

    @Before
    fun setUp() {
        useCase = RefreshMessagesUseCase(messageRepository)
    }

    @Test
    fun `invoke syncs the messages`() =
        runTest {
            coEvery { messageRepository.syncMessages() } returns Unit
            useCase()
            coVerify(exactly = 1) { messageRepository.syncMessages() }
        }
}