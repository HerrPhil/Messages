package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetBulletinByIdUseCase @Inject constructor(
    private val bulletinRepository: BulletinRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    operator fun invoke(bulletinId: Int): Flow<Resource<BulletinDomainModel?>> {
        // 1. Grab the raw streams from the repositories
        return combine(
            bulletinRepository.getBulletinByIdFlow(bulletinId),
            userPreferencesRepository.getBookmarkBulletinIds()
        ) { bulletinRes, bookmarkedIds ->
            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val bulletinStream = bulletinRes as CachePayload.ActiveStream

            // THE RESILIENCE RULE: Only fail completely if the local cache is empty!
            val isCatastrophicFailure =
                bulletinStream.syncError != null && bulletinStream.data == null

            if (isCatastrophicFailure) {
                Resource.Error("Unable to load bulletin details.")
            } else {

                // Extract the data safely if Success: otherwise keep null

                // No safe-casting required anymore! Read the data property. directly.
                val bulletin = bulletinStream.data
                val bookmarkedBulletin =
                    bulletin?.copy(isBookmark = bulletin.id.toString() in bookmarkedIds)

                Resource.Success(data = bookmarkedBulletin)
            }
        }
    }
}