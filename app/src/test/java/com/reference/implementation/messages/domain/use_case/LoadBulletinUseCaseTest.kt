package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.BulletinCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LoadBulletinUseCaseTest {

    private val bulletinCacheRepository: BulletinCacheRepository = mockk()
    private lateinit var useCase: LoadBulletinUseCase // under test

    @Before
    fun setUp() {
        useCase = LoadBulletinUseCase(bulletinCacheRepository)
    }

    @Test
    fun `invoke delegates the refresh bulletin to the repository correctly`() = runTest {

        val bulletinId = 123

        coEvery { bulletinCacheRepository.refreshBulletin(any(), any()) } returns Unit

        // Act
        useCase.invoke(bulletinId, {})

        // Assert
        coVerify(exactly = 1) {
            bulletinCacheRepository.refreshBulletin(eq(bulletinId), any())
        }
    }
}