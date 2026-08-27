package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository

class DeleteMessageUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(messageId: Int) {
        repo.deleteMessage(
            messageId = messageId,
            onRetry = { /* supports unit testing */ }
        )
    }
}