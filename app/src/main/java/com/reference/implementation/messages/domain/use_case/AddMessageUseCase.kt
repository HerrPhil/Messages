package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import okio.IOException
import retrofit2.HttpException

class AddMessageUseCase(private val repo: MessageRepository) {
    suspend operator fun invoke(
        message: MessageDomainModel,
        onRetry: suspend (Int) -> Unit
    ): Resource<MessageDomainModel> {
        return when (val addMessageNetworkResult = repo.addMessage(message, onRetry)) {
            is NetworkResult.Success -> {
                Resource.Success(data = addMessageNetworkResult.data) // pass the domain model
            }

            is NetworkResult.Error -> {
                getResourceErrorByCode(addMessageNetworkResult.code)
            }

            is NetworkResult.Exception -> {
                when (addMessageNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode(addMessageNetworkResult.e.code())
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }

    private fun getResourceErrorByCode(code: Int): Resource<Nothing> {
        return when (code) {
            400 -> Resource.Error("Something went wrong") // BAD REQUEST
            401 -> Resource.Error("Add message not authorized")
            403 -> Resource.Error("Add message forbidden")
            404 -> Resource.Error("Add message not found")
            405 -> Resource.Error("Add message method not allowed")
            408 -> Resource.Error("Add message request timeout")
            429 -> Resource.Error("Too many add message requests")
            500 -> Resource.Error("Server is having a bad day") // INTERNAL SERVER ERROR
            501 -> Resource.Error("Add message not implemented")
            502 -> Resource.Error("Something went wrong") // BAD GATEWAY
            503 -> Resource.Error("Add message is unavailable")
            504 -> Resource.Error("Something went wrong") // GATEWAY TIMEOUT
            else -> Resource.Error("Something went wrong")
        }
    }
}
