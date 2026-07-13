package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository

class MarkMessageAsReadUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(
        messageId: Int
    ) {
        repo.markMessageAsRead(messageId)
    }
}