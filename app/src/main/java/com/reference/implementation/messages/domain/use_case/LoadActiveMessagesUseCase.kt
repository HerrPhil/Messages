package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository

class LoadActiveMessagesUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshMessagesByUser(onRetry)
    }
}