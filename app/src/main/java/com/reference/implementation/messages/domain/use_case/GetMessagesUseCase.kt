package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import okio.IOException
import retrofit2.HttpException

class GetMessagesUseCase(private val repo: MessageRepository) {
    suspend operator fun invoke(onRetry: suspend (Int) -> Unit): Resource<List<MessageDomainModel>> {
        return when (val getMessagesNetworkResult = repo.getMessages(onRetry)) {
            is NetworkResult.Loading -> Resource.Loading
            is NetworkResult.Success -> {
                Resource.Success(data = getMessagesNetworkResult.data) // pass the domain models
            }

            is NetworkResult.Error -> {
                getResourceErrorByCode("Message Details", getMessagesNetworkResult.code)
            }

            is NetworkResult.Exception -> {
                when (getMessagesNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode("Message Details", getMessagesNetworkResult.e.code())
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }
}
