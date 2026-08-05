package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MarkBulletinAsBookmarkUseCaseTest {

    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var useCase: MarkBulletinAsBookmarkUseCase // under test

    @Before
    fun setUp() {
        useCase = MarkBulletinAsBookmarkUseCase(userPreferencesRepository)
    }

    @Test
    fun `markAsBookmark calls repository with exact message ID`() = runTest {
        // Arrange
        val targetBulletinId = 456
        coEvery { userPreferencesRepository.markBulletinAsBookmark(targetBulletinId) } returns Unit

        // Act
        useCase.invoke(bulletinId = targetBulletinId)

        // Assert parameter delivery
        coVerify(exactly = 1) {
            userPreferencesRepository.markBulletinAsBookmark(eq(targetBulletinId))
        }
    }
}