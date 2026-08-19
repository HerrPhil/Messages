package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.repository.BulletinCacheRepository
import com.reference.implementation.domain.use_case.GetBulletinUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class GetBulletinUseCaseTest {

    private val bulletinCacheRepository: BulletinCacheRepository = mockk()
    private lateinit var useCase: GetBulletinUseCase

    @Before
    fun setUp() {
        useCase = GetBulletinUseCase(bulletinCacheRepository)
    }

    @Test
    fun `invoke maps bulletin stream into Resource Success`() = runTest {

        // Arrange
        val bulletin = BulletinDomainModel(
            id = 101,
            userId = 123,
            title = "Outage",
            post = "There is an outage tonight",
            timestamp = "2026-08-01T10:00:00Z",
            timestampInstant = Instant.parse("2026-08-01T10:00:00Z"),
            isBookmark = false
        )

        val bulletinFlow = MutableStateFlow<NetworkResult<BulletinDomainModel>>(
            NetworkResult.Success(bulletin)
        )

        every { bulletinCacheRepository.getBulletin() } returns bulletinFlow

        // Act & Assert
        useCase().test {
            // 1. initial (only) emission: Neither message is important
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Success)

            val bulletin = (initialItem as Resource.Success).data
            assertEquals(101, bulletin.id)
            assertFalse(bulletin.isBookmark)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke maps NetworkResult to Resource Error correctly`() = runTest {

        // Arrange
        val bulletinFlow = flowOf<NetworkResult<BulletinDomainModel>>(
            NetworkResult.Exception(IOException("Network down"))
        )

        every { bulletinCacheRepository.getBulletin() } returns bulletinFlow

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
        val bulletinFlow = flowOf<NetworkResult<BulletinDomainModel>>(
            NetworkResult.Error(401, "not authorized")
        )

        every { bulletinCacheRepository.getBulletin() } returns bulletinFlow

        // Act & Assert
        useCase().test {
            val item = awaitItem()
            assertTrue(item is Resource.Error)
            assertEquals("Bulletin Detail not authorized", (item as Resource.Error).message)
            awaitComplete()
        }
    }
}