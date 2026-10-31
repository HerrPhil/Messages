package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import com.reference.implementation.domain.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteMessageUseCase @Inject constructor(
    private val repo: MessageRepository
) {
    suspend operator fun invoke(messageId: Int) {
        repo.deleteMessage(messageId = messageId)
    }
}