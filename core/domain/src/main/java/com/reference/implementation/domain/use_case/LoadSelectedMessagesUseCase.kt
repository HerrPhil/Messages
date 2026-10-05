package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LoadSelectedMessagesUseCase @Inject constructor(
    private val repo: MessageCacheRepository
) {
    open suspend operator fun invoke(
        userId: Int,
        onRetry: (Int) -> Unit
    ) {
        repo.refreshMessagesOfSelectedUser(userId, onRetry)
    }
}