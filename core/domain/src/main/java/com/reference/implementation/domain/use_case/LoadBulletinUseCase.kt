package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.BulletinCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadBulletinUseCase @Inject constructor(
    private val repo: BulletinCacheRepository
) {
    suspend operator fun invoke(
        bulletinId: Int,
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshBulletin(bulletinId, onRetry)
    }
}