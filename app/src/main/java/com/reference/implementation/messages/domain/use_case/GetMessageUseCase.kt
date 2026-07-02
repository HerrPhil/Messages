package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import okio.IOException
import retrofit2.HttpException

class GetMessageUseCase(private val repo: MessageRepository) {
    suspend operator fun invoke(
        id: Int,
        onRetry: suspend (Int) -> Unit
    ): Resource<MessageDomainModel> {
        return when (val getMessageNetworkResult = repo.getMessage(id, onRetry)) {
            is NetworkResult.Loading -> Resource.Loading
            is NetworkResult.Success -> {
                Resource.Success(data = getMessageNetworkResult.data) // pass the domain model
            }

            is NetworkResult.Error -> {
                getResourceErrorByCode("Message Edit", getMessageNetworkResult.code)
            }

            is NetworkResult.Exception -> {
                when (getMessageNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode("Message Edit", getMessageNetworkResult.e.code())
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }
}
