package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LoadActiveMessagesUseCase @Inject constructor(
    private val repo: MessageCacheRepository
) {
    open suspend operator fun invoke(
        onRetry: (Int) -> Unit
    ) {
        repo.refreshMessagesOfActiveUser(onRetry)
    }
}