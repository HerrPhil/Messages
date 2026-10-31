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

class GetMessagesForActiveUserUseCaseTest {

    private val messageRepository: MessageRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: GetMessagesForActiveUserUseCase // under test

    @Before
    fun setUp() {
        useCase = GetMessagesForActiveUserUseCase(messageRepository, userPreferencesRepository)
    }

    @Test
    fun `invoke combines message stream and preferences stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val message1 = MessageDomainModel(
                id = 101,
                subject = "Hey there",
                body = "this is test message one",
                read = false,
                userId = 123,
                createdAt = "2026-08-01T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-01T10:00:00Z"),
                isImportant = false
            )
            val message2 = MessageDomainModel(
                id = 102,
                subject = "Good bye",
                body = "this is test message two",
                read = false,
                userId = 987,
                createdAt = "2026-08-02T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-02T10:00:00Z"),
                isImportant = false
            )

            val messagesFlow = MutableStateFlow<CachePayload<List<MessageDomainModel>>>(
                value = CachePayload.ActiveStream(
                    data = listOf(message1, message2),
                    isSyncing = false,
                    syncError = null
                )
            )

            val importantIdsFlow = MutableStateFlow<Set<String>>(emptySet())

            every { messageRepository.getMessagesForActiveUser() } returns messagesFlow
            every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Initial emission: Neither message is important, list is sorted by date descending (message2 first)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val nextList = (nextItem as Resource.Success).data
                assertEquals(2, nextList.size)
                assertEquals(102, nextList[0].id)
                assertFalse(nextList[0].isImportant)
                assertFalse(nextList[1].isImportant)

                // 3. Simulate the user toggling 'Important' on message 102 in DataStore
                importantIdsFlow.value = setOf("102")

                // 4. Reactively await second emission
                val updatedItem = awaitItem()
                assertTrue(updatedItem is Resource.Success)

                val updatedList = (updatedItem as Resource.Success).data
                assertTrue(updatedList.first { it.id == 102 }.isImportant)
                assertFalse(updatedList.first { it.id == 101 }.isImportant)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke maps NetworkResult Error to Resource Error correctly`() = runTest {

        // Arrange
        val messagesFlow = flowOf<CachePayload<List<MessageDomainModel>>>(
            value = CachePayload.ActiveStream(
                data = emptyList(),
                isSyncing = false,
                syncError = IOException("Network down")
            )
        )
        val importantIdsFlow = flowOf(emptySet<String>())

        every { messageRepository.getMessagesForActiveUser() } returns messagesFlow
        every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

        // Act & Assert
        useCase().test {
            // 1. Initial emission: onStart outputs Loading
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Loading)

            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Unable to load messages", (item as Resource.Error).message)
            awaitComplete()
        }
    }

    @Test
    fun `invoke maps NetworkResult not authorized Error to Resource Error correctly`() = runTest {

        // Arrange
        val messagesFlow = flowOf<CachePayload<List<MessageDomainModel>>>(
            value = CachePayload.ActiveStream(
                data = emptyList(),
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

        every { messageRepository.getMessagesForActiveUser() } returns messagesFlow
        every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

        // Act & Assert
        useCase().test {
            // 1. Initial emission: onStart outputs Loading
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Loading)

            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Unable to load messages", (item as Resource.Error).message)
            awaitComplete()
        }
    }
}

