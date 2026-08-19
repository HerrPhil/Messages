package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.BulletinCacheRepository

class LoadAllBulletinsUseCase(private val repo: BulletinCacheRepository) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshBulletins(onRetry)
    }
}