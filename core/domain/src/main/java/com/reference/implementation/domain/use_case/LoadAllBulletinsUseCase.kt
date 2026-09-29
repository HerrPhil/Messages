package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.BulletinCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

// TODO remove 'open' reserved words after retryIO() code smell is fixed
@Singleton
open class LoadAllBulletinsUseCase @Inject constructor(
    private val repo: BulletinCacheRepository
) {
    open suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.refreshBulletins(onRetry)
    }
}