package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.BulletinCacheRepository

class LoadAllBulletinsUseCase(private val repo: BulletinCacheRepository) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshBulletins(onRetry)
    }
}