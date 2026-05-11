package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import okio.IOException
import retrofit2.HttpException

class GetMessagesUseCase(private val repo: MessageRepository) {
    suspend operator fun invoke(onRetry: suspend (Int) -> Unit): Resource<List<MessageDomainModel>> {
        return when (val getMessagesNetworkResult = repo.getMessages(onRetry)) {
            is NetworkResult.Success -> {
                Resource.Success(data = getMessagesNetworkResult.data) // pass the domain models
            }

            is NetworkResult.Error -> {
                getResourceErrorByCode(getMessagesNetworkResult.code)
            }

            is NetworkResult.Exception -> {
                when (getMessagesNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode(getMessagesNetworkResult.e.code())
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }

    private fun getResourceErrorByCode(code: Int): Resource<Nothing> {
        return when (code) {
            400 -> Resource.Error("Something went wrong") // BAD REQUEST
            401 -> Resource.Error("Get messages not authorized")
            403 -> Resource.Error("Get messages forbidden")
            404 -> Resource.Error("Get messages not found")
            405 -> Resource.Error("Get messages method not allowed")
            408 -> Resource.Error("Get messages request timeout")
            429 -> Resource.Error("Too many get messages requests")
            500 -> Resource.Error("Server is having a bad day") // INTERNAL SERVER ERROR
            501 -> Resource.Error("Get messages not implemented")
            502 -> Resource.Error("Something went wrong") // BAD GATEWAY
            503 -> Resource.Error("Get messages is unavailable")
            504 -> Resource.Error("Something went wrong") // GATEWAY TIMEOUT
            else -> Resource.Error("Something went wrong")
        }
    }
}
