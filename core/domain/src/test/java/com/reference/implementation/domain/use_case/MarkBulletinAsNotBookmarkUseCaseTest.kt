package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkBulletinAsNotBookmarkUseCaseTest {

    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: MarkBulletinAsNotBookmarkUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkBulletinAsNotBookmarkUseCase(userPreferencesRepository)
    }

    @Test
    fun `markAsNotBookmark calls repository with exact message ID`() = runTest {
        // Arrange
        val targetBulletinId = 456
        coEvery { userPreferencesRepository.markBulletinAsNotBookmark(targetBulletinId) } returns Unit

        // Act
        useCase.invoke(bulletinId = targetBulletinId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            userPreferencesRepository.markBulletinAsNotBookmark(eq(targetBulletinId))
        }
    }
}