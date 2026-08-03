package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.UserPreferencesRepository

class MarkBulletinAsNotBookmarkUseCase(private val repo: UserPreferencesRepository) {
    suspend operator fun invoke(bulletinId: Int) {
        repo.markBulletinAsNotBookmark(bulletinId)
    }
}