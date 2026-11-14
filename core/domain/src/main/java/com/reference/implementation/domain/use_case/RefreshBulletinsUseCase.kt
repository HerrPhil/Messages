package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.BulletinRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RefreshBulletinsUseCase @Inject constructor(
    private val repo: BulletinRepository
) {
    open suspend operator fun invoke() {
        repo.syncBulletins()
    }
}