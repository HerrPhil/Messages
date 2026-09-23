package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkMessageAsImportantUseCase @Inject constructor(
    private val repo: UserPreferencesRepository
) {
    suspend operator fun invoke(messageId: Int) {
        repo.markMessageAsImportant(messageId)
    }
}