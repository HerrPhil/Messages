package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.UserPreferencesRepository

class MarkMessageAsNotImportantUseCase(private val repo: UserPreferencesRepository) {
    suspend operator fun invoke(messageId: Int) {
        repo.markMessageAsNotImportant(messageId)
    }
}