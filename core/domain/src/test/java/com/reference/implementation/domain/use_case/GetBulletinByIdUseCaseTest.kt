package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.repository.BulletinRepository
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

class GetBulletinByIdUseCaseTest {

    private val bulletinRepository: BulletinRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: GetBulletinByIdUseCase // under test

    @Before
    fun setUp() {
        useCase = GetBulletinByIdUseCase(bulletinRepository, userPreferencesRepository)
    }

    @Test
    fun `invoke gets bulletin stream and puts it in Resource Success`() =
        runTest {
            // Arrange
            val expectedBulletinId = 101

            val bulletin1 = BulletinDomainModel(
                id = expectedBulletinId,
                userId = 123,
                title = "test bulletin title",
                post = "The post of the bulletin",
                timestamp = "2026-11-12T13:24:45Z",
                timestampInstant = Instant.parse("2026-11-12T13:24:45Z"),
                isBookmark = false
            )
            val bulletinFlow = MutableStateFlow<CachePayload<BulletinDomainModel?>>(
                value = CachePayload.ActiveStream(
                    data = bulletin1,
                    isSyncing = false,
                    syncError = null
                )
            )
            val bookmarkedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
            every { bulletinRepository.getBulletinByIdFlow(bulletinId = any()) } returns bulletinFlow
            every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

            // Act & Assert
            useCase(expectedBulletinId).test {
                // 1. Note: this use case does not have a Loading emission

                // 2. Initial emission: Bulletin is not bookmarked
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)
                val nextBulletin = (nextItem as Resource.Success).data
                assertEquals(expectedBulletinId, nextBulletin?.id)
                assertFalse(nextBulletin?.isBookmark ?: true)

                // 3. Simulate the user toggling 'Bookmark' on bulletin 101
                bookmarkedIdsFlow.value = setOf(expectedBulletinId.toString())

                // 4. Reactively await second emission
                val updatedItem = awaitItem()
                assertTrue(updatedItem is Resource.Success)
                val updatedBulletin = (updatedItem as Resource.Success).data
                assertTrue(updatedBulletin?.isBookmark ?: false)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke maps CachePayload Error to Resource Error correctly`() =
        runTest {
            // Arrange
            val expectedBulletinId = 101

            val bulletinFlow = MutableStateFlow<CachePayload<BulletinDomainModel?>>(
                value = CachePayload.ActiveStream(
                    data = null,
                    isSyncing = false,
                    syncError = IOException("Network down")
                )
            )
            val bookmarkedIdsFlow = flowOf(emptySet<String>())
            every { bulletinRepository.getBulletinByIdFlow(bulletinId = any()) } returns bulletinFlow
            every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

            // Act & Assert
            useCase(expectedBulletinId).test {
                val item = awaitItem()
                assertTrue(item is Resource.Error)
                assertEquals("Unable to load bulletin details.", (item as Resource.Error).message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke gets bulletin stream when error is not catastrophic`() =
        runTest {
            // Arrange
            val expectedBulletinId = 101

            val bulletin1 = BulletinDomainModel(
                id = expectedBulletinId,
                userId = 123,
                title = "test bulletin title",
                post = "The post of the bulletin",
                timestamp = "2026-11-12T13:24:45Z",
                timestampInstant = Instant.parse("2026-11-12T13:24:45Z"),
                isBookmark = false
            )

            val bulletinFlow = MutableStateFlow<CachePayload<BulletinDomainModel?>>(
                value = CachePayload.ActiveStream(
                    data = bulletin1,
                    isSyncing = false,
                    syncError = IOException("Network down")
                )
            )
            val bookmarkedIdsFlow = flowOf(emptySet<String>())
            every { bulletinRepository.getBulletinByIdFlow(bulletinId = any()) } returns bulletinFlow
            every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

            // Act & Assert
            useCase(expectedBulletinId).test {
                val item = awaitItem()
                assertTrue(item is Resource.Success)
                val itemData = (item as Resource.Success).data
                assertEquals(bulletin1, itemData)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke maps CachePayload not authorized Error to Resource Error correctly`() =
        runTest {
            // Arrange
            val expectedBulletinId = 101

            val bulletinFlow = MutableStateFlow<CachePayload<BulletinDomainModel?>>(
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
            val bookmarkedIdsFlow = flowOf(emptySet<String>())

            every { bulletinRepository.getBulletinByIdFlow(bulletinId = expectedBulletinId) } returns bulletinFlow
            every { userPreferencesRepository.getBookmarkBulletinIds() } returns bookmarkedIdsFlow

            // Act & Assert
            useCase(expectedBulletinId).test {
                val item = awaitItem()
                assertTrue(item is Resource.Error)
                assertEquals("Unable to load bulletin details.", (item as Resource.Error).message)
                cancelAndIgnoreRemainingEvents()
            }
        }


}