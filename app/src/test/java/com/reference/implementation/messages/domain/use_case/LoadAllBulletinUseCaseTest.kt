package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.BulletinCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LoadAllBulletinUseCaseTest {

    private val bulletinCacheRepository: BulletinCacheRepository = mockk()
    private lateinit var useCase: LoadAllBulletinsUseCase // under test

    @Before
    fun setUp() {
        useCase = LoadAllBulletinsUseCase(bulletinCacheRepository)
    }

    @Test
    fun `invoke delegates the refresh all bulletins to the repository correctly`() = runTest {

        coEvery { bulletinCacheRepository.refreshBulletins(any()) } returns Unit

        // Act
        useCase.invoke({})

        // Assert
        coVerify(exactly = 1) {
            bulletinCacheRepository.refreshBulletins(any())
        }
    }
}