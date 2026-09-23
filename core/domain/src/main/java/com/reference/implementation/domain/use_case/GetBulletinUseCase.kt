package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.repository.BulletinCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetBulletinUseCase @Inject constructor(
    private val repo: BulletinCacheRepository
) {
    operator fun invoke(): Flow<Resource<BulletinDomainModel>> {
        // 1. Grab the raw stream from the repository
        return repo.getBulletin()
            .map { networkResult ->
                networkResult.toResource("Bulletin Detail") { bulletin ->
                    bulletin
                }
            }
    }
}