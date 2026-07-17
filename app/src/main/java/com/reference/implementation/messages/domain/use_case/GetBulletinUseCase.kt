package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.BulletinDomainModel
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.BulletinCacheRepository
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.IOException
import retrofit2.HttpException
import java.time.Instant

class GetBulletinUseCase(private val repo: BulletinCacheRepository) {
    operator fun invoke(): Flow<Resource<BulletinDomainModel>> {
        // 1. Grab the raw stream from the repository
        return repo.getBulletin().map { networkResult ->
            // 2. use the map operator to look inside the data stream
            when (networkResult) {
                is NetworkResult.Loading -> Resource.Loading
                is NetworkResult.Success -> {
                    Resource.Success(data = networkResult.data)
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