package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.BulletinDomainModel
import com.reference.implementation.messages.domain.repository.BulletinCacheRepository
import com.reference.implementation.messages.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import okio.IOException
import retrofit2.HttpException

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
            // 2. use the map operator to look inside the data stream
            when (networkResult) {
                is NetworkResult.Loading -> Resource.Loading
                is NetworkResult.Success -> {
                    // 3. Apply pure domain business logic transformations
                    // (eg) show most recent messages first.
                    val transformedList =
                        networkResult.data.sortedByDescending { bulletinDomainModel ->
                            bulletinDomainModel.timestampInstant
                        }.map { bulletin ->
                            bulletin.copy(isBookmark = bulletin.id.toString() in bookmarkIds)
                        }
                    Resource.Success(data = transformedList)
                }

                is NetworkResult.Error -> {
                    getResourceErrorByCode("Bulletin Details", networkResult.code)
                }

                is NetworkResult.Exception -> {
                    when (networkResult.e) {
                        is IOException -> Resource.Error("No internet connection")
                        is HttpException -> {
                            getResourceErrorByCode("Bulletin Details", networkResult.e.code())
                        }

                        else -> Resource.Error("Unknown error occurred")
                    }
                }
            }
        }
    }
}