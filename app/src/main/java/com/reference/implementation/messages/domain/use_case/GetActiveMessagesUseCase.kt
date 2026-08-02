package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import okio.IOException
import retrofit2.HttpException

class GetActiveMessagesUseCase(
    private val messageCacheRepository: MessageCacheRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    operator fun invoke(): Flow<Resource<List<MessageDomainModel>>> {
        // 1. Grab the raw streams from the repositories
        return combine(
            messageCacheRepository.getMessagesByUser(),
            userPreferencesRepository.getImportantMessageIds()
        ) { networkResult, importantIds ->
            // 2. use the map operator to look inside the data stream
            when (networkResult) {
                is NetworkResult.Loading -> Resource.Loading
                is NetworkResult.Success -> {
                    // 3. Apply pure domain business logic transformations
                    // (eg) show most recent messages first.

                    val transformedList =
                        networkResult.data.sortedByDescending { messageDomainModel ->
                            messageDomainModel.createdAtInstant
                        }.map { message ->
                            message.copy(isImportant = message.id.toString() in importantIds)
                        }
                    Resource.Success(data = transformedList)
                }

                is NetworkResult.Error -> {
                    getResourceErrorByCode("Message Details", networkResult.code)
                }

                is NetworkResult.Exception -> {
                    when (networkResult.e) {
                        is IOException -> Resource.Error("No internet connection")
                        is HttpException -> {
                            getResourceErrorByCode("Message Details", networkResult.e.code())
                        }

                        else -> Resource.Error("Unknown error occurred")
                    }
                }
            }
        }
    }
}