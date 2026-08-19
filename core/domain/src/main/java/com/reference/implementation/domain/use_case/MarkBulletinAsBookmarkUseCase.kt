package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.UserPreferencesRepository

class MarkBulletinAsBookmarkUseCase(private val repo: UserPreferencesRepository) {
    suspend operator fun invoke(bulletinId: Int) {
        repo.markBulletinAsBookmark(bulletinId)
    }
}