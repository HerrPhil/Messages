package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageCacheRepository

class RestoreMessageUseCase(private val repo: MessageCacheRepository) {
    suspend operator fun invoke(
        deletedMessage: MessageDomainModel
    ) {
        repo.restoreMessage(deletedMessage)
    }
}