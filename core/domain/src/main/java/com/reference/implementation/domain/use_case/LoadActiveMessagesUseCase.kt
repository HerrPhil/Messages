package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadActiveMessagesUseCase @Inject constructor(
    private val repo: MessageCacheRepository
) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshMessagesOfActiveUser(onRetry)
    }
}