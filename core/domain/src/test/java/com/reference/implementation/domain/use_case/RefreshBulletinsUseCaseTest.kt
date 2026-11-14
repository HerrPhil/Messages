package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.BulletinRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RefreshBulletinsUseCaseTest {

    private val bulletinRepository: BulletinRepository = mockk()

    private lateinit var useCase: RefreshBulletinsUseCase // under test

    @Before
    fun setUp() {
        useCase = RefreshBulletinsUseCase(bulletinRepository)
    }

    @Test
    fun `invoke syncs the bulletins`() =
        runTest {
            coEvery { bulletinRepository.syncBulletins() } returns Unit
            useCase()
            coVerify(exactly = 1) { bulletinRepository.syncBulletins() }
        }
}