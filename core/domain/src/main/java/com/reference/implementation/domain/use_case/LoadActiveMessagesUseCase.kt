package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository

class LoadActiveMessagesUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshMessagesOfActiveUser(onRetry)
    }
}