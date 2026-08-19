package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.repository.BulletinCacheRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetAllBulletinsUseCase(
    private val bulletinCacheRepository: BulletinCacheRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    operator fun invoke(): Flow<Resource<List<BulletinDomainModel>>> {
        // 1. Grab the raw stream from the repository
        return combine(
            bulletinCacheRepository.getAllBulletins(),
            userPreferencesRepository.getBookmarkBulletinIds()
        ) { networkResult, bookmarkIds ->
            networkResult.toResource("Bulletin Details") { bulletins ->
                bulletins.sortedByDescending { bulletinDomainModel ->
                    bulletinDomainModel.timestampInstant
                }.map { bulletin ->
                    bulletin.copy(isBookmark = bulletin.id.toString() in bookmarkIds)
                }
            }
        }
    }
}