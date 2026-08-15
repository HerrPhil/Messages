package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository

class LoadSelectedMessagesUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(
        userId: Int,
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshMessagesOfSelectedUser(userId, onRetry)
    }
}