package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadSelectedMessagesUseCase @Inject constructor(
    private val repo: MessageCacheRepository
) {
    suspend operator fun invoke(
        userId: Int,
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshMessagesOfSelectedUser(userId, onRetry)
    }
}