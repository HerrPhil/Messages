package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.repository.BulletinCacheRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class GetAllBulletinsUseCaseTest {

    private val bulletinCacheRepository: BulletinCacheRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: GetAllBulletinsUseCase // under test

    @Before
    fun setUp() {
        useCase = GetAllBulletinsUseCase(bulletinCacheRepository, userPreferencesRepository)
    }

    @Test
    fun `invoke combines bulletins stream and preferences stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val bulletin1 = BulletinDomainModel(
                id = 101,
                userId = 123,
                title = "Outage",
                post = "There is an outage tonight",
                timestamp = "2026-08-01T10:00:00Z",
                timestampInstant = Instant.parse("2026-08-01T10:00:00Z"),
                isBookmark = false
            )
            val bulletin2 = BulletinDomainModel(
                id = 102,
                userId = 987,
                title = "Upgrade",
                post = "There is an upgrade tonight",
                timestamp = "2026-08-02T10:00:00Z",
                timestampInstant = Instant.parse("2026-08-02T10:00:00Z"),
                isBookmark = false
            )

            val bulletinsFlow = MutableStateFlow<NetworkResult<List<BulletinDomainModel>>>(
                NetworkResult.Success(listOf(bulletin1, bulletin2))
            )

            val importantIdsFlow = MutableStateFlow<Set<String>>(emptySet())

            every { bulletinCacheRepository.getAllBulletins() } returns bulletinsFlow
            every { userPreferencesRepository.getBookmarkBulletinIds() } returns importantIdsFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: Neither message is important
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Success)

                val initialList = (initialItem as Resource.Success).data
                assertEquals(2, initialList.size)
                assertEquals(102, initialList[0].id)
                assertFalse(initialList[0].isBookmark)
                assertFalse(initialList[1].isBookmark)

                // 2. Simulate the user toggling 'Bookmark' on bulletin 102 in DataStore
                importantIdsFlow.value = setOf("102")

                // 3. Reactively await second emission
                val updatedItem = awaitItem()
                assertTrue(updatedItem is Resource.Success)

                val updatedList = (updatedItem as Resource.Success).data
                assertTrue(updatedList.first { it.id == 102 }.isBookmark)
                assertFalse(updatedList.first { it.id == 101 }.isBookmark)

                cancelAndIgnoreRemainingEvents()
            }

        }

    @Test
    fun `invoke maps NetworkResult to Resource Error correctly`() = runTest {

        // Arrange
        val bulletinsFlow = flowOf<NetworkResult<List<BulletinDomainModel>>>(
            NetworkResult.Exception(IOException("Network down"))
        )
        val importantIdsFlow = flowOf(emptySet<String>())

        every { bulletinCacheRepository.getAllBulletins() } returns bulletinsFlow
        every { userPreferencesRepository.getBookmarkBulletinIds() } returns importantIdsFlow

        // Act & Assert
        useCase().test {
            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("No internet connection", (item as Resource.Error).message)
            awaitComplete()
        }
    }

    @Test
    fun `invoke maps NetworkResult not authorized to Resource Error correctly`() = runTest {

        // Arrange
        val bulletinsFlow = flowOf<NetworkResult<List<BulletinDomainModel>>>(
            NetworkResult.Error(401, "not authorized")
        )
        val importantIdsFlow = flowOf(emptySet<String>())

        every { bulletinCacheRepository.getAllBulletins() } returns bulletinsFlow
        every { userPreferencesRepository.getBookmarkBulletinIds() } returns importantIdsFlow

        // Act & Assert
        useCase().test {
            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Bulletin Details not authorized", (item as Resource.Error).message)
            awaitComplete()
        }
    }
}