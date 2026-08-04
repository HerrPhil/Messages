package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.model.BulletinDomainModel
import com.reference.implementation.messages.domain.repository.BulletinCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetBulletinUseCase(private val repo: BulletinCacheRepository) {
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