package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreMessageUseCase @Inject constructor(
    private val repo: MessageCacheRepository
) {
    suspend operator fun invoke(
        deletedMessage: MessageDomainModel
    ) {
        repo.restoreMessage(
            deletedMessage = deletedMessage,
            onRetry = { /* supports unit testing */ }
        )
    }
}