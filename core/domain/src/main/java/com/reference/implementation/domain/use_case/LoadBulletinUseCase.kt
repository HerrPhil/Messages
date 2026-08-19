package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.BulletinCacheRepository

class LoadBulletinUseCase(private val repo: BulletinCacheRepository) {
    suspend operator fun invoke(
        bulletinId: Int,
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshBulletin(bulletinId, onRetry)
    }
}