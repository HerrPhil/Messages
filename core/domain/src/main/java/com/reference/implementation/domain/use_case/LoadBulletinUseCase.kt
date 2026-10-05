package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.BulletinCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

// TODO remove 'open' reserved words after retryIO() code smell is fixed
@Singleton
open class LoadBulletinUseCase @Inject constructor(
    private val repo: BulletinCacheRepository
) {
    open suspend operator fun invoke(
        bulletinId: Int,
        onRetry: (Int) -> Unit
    ) {
        repo.refreshBulletin(bulletinId, onRetry)
    }
}