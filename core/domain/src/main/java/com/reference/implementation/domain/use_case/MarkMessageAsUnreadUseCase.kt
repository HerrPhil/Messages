package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkMessageAsUnreadUseCase @Inject constructor(
    private val repo: MessageCacheRepository
) {
    suspend operator fun invoke(messageId: Int) {
        repo.markMessageAsUnread(
            messageId = messageId,
            onRetry = { /* supports unit testing */ }
        )
    }
}