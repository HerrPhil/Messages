package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.repository.BulletinRepository
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

class GetAllBulletinsUseCaseTest {

    private val bulletinRepository: BulletinRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: GetAllBulletinsUseCase // under test

    @Before
    fun setUp() {
        useCase = GetAllBulletinsUseCase(bulletinRepository, userPreferencesRepository)
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

            val bulletinsFlow = MutableStateFlow<CachePayload<List<BulletinDomainModel>>>(
                value = CachePayload.ActiveStream(
                    data = listOf(bulletin1, bulletin2),
                    isSyncing = false,
                    syncError = null
                )
            )

            val bookmarkedIdsFlow = MutableStateFlow<Set<String>>(emptySet())

            every { bulletinRepository.getBulletins() } returns bulletinsFlow
            every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Initial emission: Neither bulletin is important, list is sorted by date descending (bulletin2 first)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val nextList = (nextItem as Resource.Success).data
                assertEquals(2, nextList.size)
                assertEquals(102, nextList[0].id)
                assertFalse(nextList[0].isBookmark)
                assertFalse(nextList[1].isBookmark)

                // 2. Simulate the user toggling 'Bookmark' on bulletin 102 in DataStore
                bookmarkedIdsFlow.value = setOf("102")

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
    fun `invoke combines empty bulletin stream and preferences stream into enriched Resource Success`() =
        runTest {
            // Arrange
            val bulletinsFlow = MutableStateFlow<CachePayload<List<BulletinDomainModel>>>(
                value = CachePayload.ActiveStream(
                    data = emptyList(),
                    isSyncing = false,
                    syncError = null
                )
            )

            val bookmarkedIdsFlow = MutableStateFlow<Set<String>>(emptySet())

            every { bulletinRepository.getBulletins() } returns bulletinsFlow
            every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Initial emission: Neither message is bookmarked, list is sorted by date descending (bulletin2 is first)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val nextList = (nextItem as Resource.Success).data
                assertEquals(0, nextList.size)

                // 3. Simulate the user toggling 'Bookmark' on bulletin 102 in DataStore
                bookmarkedIdsFlow.value = setOf("102")

                // 4. Reactively await second emission
                val updatedItem = awaitItem()
                assertTrue(updatedItem is Resource.Success)

                val updatedList = (updatedItem as Resource.Success).data
                assertEquals(0, updatedList.size)

                @Suppress("UnusedFlow")
                coVerify(exactly = 1) { bulletinRepository.getBulletins() }
                @Suppress("UnusedFlow")
                coVerify(exactly = 1) { userPreferencesRepository.getBookmarkBulletinIds() }

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke maps CachePayload to Resource Error correctly`() = runTest {
        // Arrange
        val bulletinsFlow = flowOf<CachePayload<List<BulletinDomainModel>>>(
            value = CachePayload.ActiveStream(
                data = emptyList(),
                isSyncing = false,
                syncError = IOException("Network down")
            )
        )

        val bookmarkedIdsFlow = flowOf(emptySet<String>())

        every { bulletinRepository.getBulletins() } returns bulletinsFlow
        every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

        // Act & Assert
        useCase().test {
            // 1. Initial emission: onStart outputs Loading
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Loading)

            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Unable to load bulletins", (item as Resource.Error).message)

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { bulletinRepository.getBulletins() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { userPreferencesRepository.getBookmarkBulletinIds() }

            awaitComplete()
        }
    }

    @Test
    fun `gets bulletin stream when error is not catastrophic`() = runTest {
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

        val bulletinsFlow = flowOf<CachePayload<List<BulletinDomainModel>>>(
            value = CachePayload.ActiveStream(
                data = listOf(bulletin1,bulletin2),
                isSyncing = false,
                syncError = IOException("Network down")
            )
        )

        val bookmarkedIdsFlow = flowOf(emptySet<String>())

        every { bulletinRepository.getBulletins() } returns bulletinsFlow
        every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

        // Act & Assert
        useCase().test {
            // 1. Initial emission: onStart outputs Loading
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Loading)

            val item = awaitItem()
            assertTrue(item is Resource.Success)
            val itemData = (item as Resource.Success).data
            val expectedSortedList = listOf(bulletin2, bulletin1)
            assertEquals(expectedSortedList, itemData)

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { bulletinRepository.getBulletins() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { userPreferencesRepository.getBookmarkBulletinIds() }

            awaitComplete()
        }
    }

    @Test
    fun `invoke maps CachePayload not authorized to Resource Error correctly`() = runTest {
        // Arrange
        val bulletinsFlow = flowOf<CachePayload<List<BulletinDomainModel>>>(
            value = CachePayload.ActiveStream(
                data = emptyList(),
                isSyncing = false,
                syncError = HttpException(
                    Response.error<Int>(
                        401,
                        "not authorized"
                            .toResponseBody(
                                contentType = "application/json".toMediaTypeOrNull()
                            )
                    )
                )
            )
        )

        val bookmarkedIdsFlow = flowOf(emptySet<String>())

        every { bulletinRepository.getBulletins() } returns bulletinsFlow
        every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

        // Act & Assert
        useCase().test {
            // 1. Initial emission: onStart outputs Loading
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Loading)

            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Unable to load bulletins", (item as Resource.Error).message)

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { bulletinRepository.getBulletins() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { userPreferencesRepository.getBookmarkBulletinIds() }

            awaitComplete()
        }
    }
}













