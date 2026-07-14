package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageCacheRepository

class RestoreMessageUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(
        deletedMessage: MessageDomainModel
    ) {
        repo.restoreMessage(deletedMessage)
    }
}