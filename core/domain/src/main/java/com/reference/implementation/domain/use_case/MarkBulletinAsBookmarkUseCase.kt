package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkBulletinAsBookmarkUseCase @Inject constructor(
    private val repo: UserPreferencesRepository
) {
    suspend operator fun invoke(bulletinId: Int) {
        repo.markBulletinAsBookmark(bulletinId)
    }
}