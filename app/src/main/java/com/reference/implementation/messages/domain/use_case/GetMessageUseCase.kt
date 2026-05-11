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
            is NetworkResult.Success -> {
                Resource.Success(data = getMessageNetworkResult.data) // pass the domain model
            }

            is NetworkResult.Error -> {
                getResourceErrorByCode(getMessageNetworkResult.code)
            }

            is NetworkResult.Exception -> {
                when (getMessageNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode(getMessageNetworkResult.e.code())
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }

    private fun getResourceErrorByCode(code: Int): Resource<Nothing> {
        return when (code) {
            400 -> Resource.Error("Something went wrong") // BAD REQUEST
            401 -> Resource.Error("Get message not authorized")
            403 -> Resource.Error("Get message forbidden")
            404 -> Resource.Error("Get message not found")
            405 -> Resource.Error("Get message method not allowed")
            408 -> Resource.Error("Get message request timeout")
            429 -> Resource.Error("Too many get message requests")
            500 -> Resource.Error("Server is having a bad day") // INTERNAL SERVER ERROR
            501 -> Resource.Error("Get message not implemented")
            502 -> Resource.Error("Something went wrong") // BAD GATEWAY
            503 -> Resource.Error("Get message is unavailable")
            504 -> Resource.Error("Something went wrong") // GATEWAY TIMEOUT
            else -> Resource.Error("Something went wrong")
        }
    }


//    = repo.getMessage(id)
}
