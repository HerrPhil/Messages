package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import io.mockk.coVerify
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

class GetMessagesForSelectedUserUseCaseTest {

    private val messageRepository: MessageRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val userId = 678345
    private lateinit var useCase: GetMessagesForSelectedUserUseCase // under test

    @Before
    fun setUp() {
        useCase = GetMessagesForSelectedUserUseCase(messageRepository, userPreferencesRepository)
    }

    @Test
    fun `invoke combines message stream and preferences stream into enriched Resource Success`() =
        runTest {

            val messagesFlow = MutableStateFlow<CachePayload<List<MessageDomainModel>>>(
                value = CachePayload.ActiveStream(
                    data = createSampleDomainMessages(),
                    isSyncing = false,
                    syncError = null
                )
            )

            val importantIdsFlow = MutableStateFlow<Set<String>>(emptySet())

            every { messageRepository.getMessagesForSelectedUser(userId) } returns messagesFlow
            every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

            useCase(userId).test {

                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Initial emission: Neither message is important, list is sorted by date descending (message2 first)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val nextList = (nextItem as Resource.Success).data
                assertEquals(3, nextList.size)
                assertEquals(100, nextList[0].id)
                assertFalse(nextList[0].isImportant)
                assertFalse(nextList[1].isImportant)
                assertFalse(nextList[2].isImportant)

                // 3. Simulate the user toggling 'Important' on message 102 in DataStore
                importantIdsFlow.value = setOf("102")

                // 4. Reactively await second emission
                val updatedItem = awaitItem()
                assertTrue(updatedItem is Resource.Success)

                val updatedList = (updatedItem as Resource.Success).data
                assertTrue(updatedList.first { it.id == 102 }.isImportant)
                assertFalse(updatedList.first { it.id == 101 }.isImportant)
                assertFalse(updatedList.first { it.id == 100 }.isImportant)

                @Suppress("UnusedFlow")
                coVerify(exactly = 1) {messageRepository.getMessagesForSelectedUser(eq(userId))}
                @Suppress("UnusedFlow")
                coVerify(exactly = 1) {userPreferencesRepository.getImportantMessageIds()}

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines empty message stream and preferences stream into enriched Resource Success`() =
        runTest {

            // Arrange

            val messagesFlow = MutableStateFlow<CachePayload<List<MessageDomainModel>>>(
                value = CachePayload.ActiveStream(
                    data = emptyList(),
                    isSyncing = false,
                    syncError = null
                )
            )

            val importantIdsFlow = MutableStateFlow<Set<String>>(emptySet())

            every { messageRepository.getMessagesForSelectedUser(userId) } returns messagesFlow
            every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

            // Act & Assert
            useCase(userId).test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Initial emission: Neither message is important, list is sorted by date descending (message2 first)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val nextList = (nextItem as Resource.Success).data
                assertEquals(0, nextList.size)

                // 3. Simulate the user toggling 'Important' on message 102 in DataStore
                importantIdsFlow.value = setOf("102")

                // 4. Reactively await second emission
                val updatedItem = awaitItem()
                assertTrue(updatedItem is Resource.Success)

                val updatedList = (updatedItem as Resource.Success).data
                assertEquals(0, updatedList.size)

                @Suppress("UnusedFlow")
                coVerify(exactly = 1) {messageRepository.getMessagesForSelectedUser(eq(userId))}
                @Suppress("UnusedFlow")
                coVerify(exactly = 1) {userPreferencesRepository.getImportantMessageIds()}

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke maps NetworkResult Error to Resource Error correctly`() =
        runTest {

            // Arrange
            val messagesFlow = flowOf<CachePayload<List<MessageDomainModel>>>(
                value = CachePayload.ActiveStream(
                    data = emptyList(),
                    isSyncing = false,
                    syncError = IOException("Network down")
                )
            )
            val importantIdsFlow = flowOf(emptySet<String>())

            every { messageRepository.getMessagesForSelectedUser(userId) } returns messagesFlow
            every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

            // Act & Assert
            useCase(userId).test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                val item = awaitItem()
                assertTrue(item is Resource.Error)
                assertEquals("Unable to load messages", (item as Resource.Error).message)

                @Suppress("UnusedFlow")
                coVerify(exactly = 1) {messageRepository.getMessagesForSelectedUser(eq(userId))}
                @Suppress("UnusedFlow")
                coVerify(exactly = 1) {userPreferencesRepository.getImportantMessageIds()}

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

        every { messageRepository.getMessagesForSelectedUser(userId) } returns messagesFlow
        every { userPreferencesRepository.getImportantMessageIds() } returns importantIdsFlow

        // Act & Assert
        useCase(userId).test {
            // 1. Initial emission: onStart outputs Loading
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Loading)

            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Unable to load messages", (item as Resource.Error).message)

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) {messageRepository.getMessagesForSelectedUser(eq(userId))}
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) {userPreferencesRepository.getImportantMessageIds()}

            awaitComplete()
        }
    }

    private fun createSampleDomainMessage(id: Int, read: Boolean): MessageDomainModel {
        return MessageDomainModel(
            id = 100 + id,
            userId = userId,
            subject = "Test Subject",
            body = "Test Body",
            read = read,
            isImportant = false,
            createdAt = "2026-08-26T10:00:00Z",
            createdAtInstant = Instant.parse("2026-08-26T10:00:00Z"),
        )
    }

    private fun createSampleDomainMessages(): List<MessageDomainModel> =
        MutableList(3) { index ->
            createSampleDomainMessage(
                index,
                (index % 2 == 0)
            )
        }
}