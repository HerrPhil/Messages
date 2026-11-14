package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllBulletinsUseCase @Inject constructor(
    private val bulletinRepository: BulletinRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    operator fun invoke(): Flow<Resource<List<BulletinDomainModel>>> {
        // 1. Grab the raw streams from the repositories
        return combine(
            bulletinRepository.getBulletins(),
            userPreferencesRepository.getBookmarkBulletinIds()
        ) { bulletinRes, bookmarkedIds ->
            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val bulletinStream = bulletinRes as CachePayload.ActiveStream

            // THE RESILIENCE FIX: Only crash the UI if the Sync task failed
            // AND there is absolutely zero local cache data available to show!
            val isCatastrophicFailure =
                bulletinStream.syncError != null && bulletinStream.data.isEmpty()

            if (isCatastrophicFailure) {
                Resource.Error("Unable to load bulletins")
            } else {

                // Extract the data safely if Success: otherwise keep null

                // No safe-casting required anymore! Read the data property directly.
                val bulletins = bulletinStream.data
                    .sortedByDescending { bulletinDomainModel -> // newest-to-oldest
                        bulletinDomainModel.timestampInstant
                    }.map { bulletin ->
                        bulletin.copy(isBookmark = bulletin.id.toString() in bookmarkedIds)
                    }

                Resource.Success(data = bulletins)
            }
        }
            .onStart { emit(Resource.Loading) }
            .flowOn(Dispatchers.Default)
    }
}