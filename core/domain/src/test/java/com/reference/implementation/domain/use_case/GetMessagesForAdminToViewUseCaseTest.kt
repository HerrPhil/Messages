package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GetMessagesForAdminToViewUseCaseTest {

    private val getMessagesForActiveUserUseCase: GetMessagesForActiveUserUseCase =
        mockk(relaxed = true)
    private val getMessagesForSelectedUserUseCase: GetMessagesForSelectedUserUseCase =
        mockk(relaxed = true)
    private val userId = 678345
    private lateinit var useCase: GetMessagesForAdminToViewUseCase

    @Before
    fun setUp() {
        useCase = GetMessagesForAdminToViewUseCase(
            getMessagesForActiveUserUseCase,
            getMessagesForSelectedUserUseCase
        )
    }

    @Test
    fun `when user id is null then the active admin user id is used to get messages`() =
        runTest {
            @Suppress("UnusedFlow")
            useCase(null).test {
                verify(exactly = 1) { getMessagesForActiveUserUseCase() }
                verify(exactly = 0) { getMessagesForSelectedUserUseCase(userId) }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user id is not null then the selected user id is used to get messages`() =
        runTest {
            @Suppress("UnusedFlow")
            useCase(userId).test {
                verify(exactly = 0) { getMessagesForActiveUserUseCase() }
                verify(exactly = 1) { getMessagesForSelectedUserUseCase(userId) }
                cancelAndIgnoreRemainingEvents()
            }
        }

}