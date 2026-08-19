package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository

class MarkMessageAsReadUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(messageId: Int) {
        repo.markMessageAsRead(messageId)
    }
}