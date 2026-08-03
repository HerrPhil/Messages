package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.UserPreferencesRepository

class MarkBulletinAsBookmarkUseCase(private val repo: UserPreferencesRepository) {
    suspend operator fun invoke(bulletinId: Int) {
        repo.markBulletinAsBookmark(bulletinId)
    }
}