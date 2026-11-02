package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

class GetMessageByIdUseCaseTest {

    private val messageRepository: MessageRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: GetMessageByIdUseCase // under test

    @Before
    fun setUp() {
        useCase = GetMessageByIdUseCase(messageRepository, userPreferencesRepository)
    }

    @Test
    fun `invoke gets message stream and puts it in Resource Success`() =
        runTest {

            val expectedMessageId = 101

            // Arrange
            val message1 = MessageDomainModel(
                id = expectedMessageId,
                subject = "Hey there",
                body = "this is test message one",
                read = false,
                userId = 123,
                createdAt = "2026-08-01T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-01T10:00:00Z"),
                isImportant = false
            )
            val messagesFlow = MutableStateFlow<CachePayload<MessageDomainModel?>>(
                value = CachePayload.ActiveStream(
                    data = message1,
                    isSyncing = false,
                    syncError = null
                )
            )
            val importantIdsFlow = MutableStateFlow<Set<String>>(emptySet())
            every { messageRepository.getMessageByIdFlow(messageId = any()) } returns messagesFlow
            every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

            // Act & Assert
            useCase(expectedMessageId).test {
                // 1. Note: this use case does not have a Loading emission.

                // 2. Initial emission: Neither message is important, list is sorted by date descending (message2 first)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)
                val nextMessage = (nextItem as Resource.Success).data
                assertEquals(expectedMessageId, nextMessage?.id)
                assertFalse(nextMessage?.isImportant ?: true)

                // 3. Simulate the user toggling 'Important' on message 102 in DataStore
                importantIdsFlow.value = setOf(expectedMessageId.toString())

                // 4. Reactively await second emission
                val updatedItem = awaitItem()
                assertTrue(updatedItem is Resource.Success)
                val updatedMessage = (updatedItem as Resource.Success).data
                assertTrue(updatedMessage?.isImportant ?: false)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke maps NetworkResult Error to Resource Error correctly`() =
        runTest {

            // Arrange
            val expectedMessageId = 101

            val messagesFlow = MutableStateFlow<CachePayload<MessageDomainModel?>>(
                value = CachePayload.ActiveStream(
                    data = null,
                    isSyncing = false,
                    syncError = IOException("Network down")
                )
            )
            val importantIdsFlow = flowOf(emptySet<String>())
            every { messageRepository.getMessageByIdFlow(messageId = any()) } returns messagesFlow
            every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

            // Act & Assert
            useCase(expectedMessageId).test {
                val item = awaitItem()
                assertTrue(item is Resource.Error)
                assertEquals("Unable to load message details.", (item as Resource.Error).message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke maps NetworkResult not authorized Error to Resource Error correctly`() = runTest {

        // Arrange
        val expectedMessageId = 101

        val messagesFlow = MutableStateFlow<CachePayload<MessageDomainModel?>>(
            value = CachePayload.ActiveStream(
                data = null,
                isSyncing = false,
                syncError = HttpException(
                    Response.error<Int>(
                        401,
                        "not authorized"
                            .toResponseBody(
                                "application/json".toMediaTypeOrNull()
                            )
                    )
                )

            )
        )
        val importantIdsFlow = flowOf(emptySet<String>())

        every { messageRepository.getMessageByIdFlow(messageId = any()) } returns messagesFlow
        every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

        // Act & Assert
        useCase(expectedMessageId).test {
            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Unable to load message details.", (item as Resource.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}