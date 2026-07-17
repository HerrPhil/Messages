package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.BulletinCacheRepository

class LoadBulletinUseCase(private val repo: BulletinCacheRepository) {
    suspend operator fun invoke(
        bulletinId: Int,
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshBulletin(bulletinId, onRetry)
    }
}