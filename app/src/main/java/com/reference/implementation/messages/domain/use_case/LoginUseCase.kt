package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
import okio.IOException
import retrofit2.HttpException

class LoginUseCase(private val repo: LoginRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): Resource<LoginDomainModel> {
        return when (val loginNetworkResult = repo.login(email, password, onRetry)) {
            is NetworkResult.Success -> {
                Resource.Success(data = loginNetworkResult.data) // pass the domain model
            }
            is NetworkResult.Error -> {
                getResourceErrorByCode(loginNetworkResult.code)
            }
            is NetworkResult.Exception -> {
                when (loginNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode(loginNetworkResult.e.code())
                    }
                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }

    private fun getResourceErrorByCode(code: Int): Resource<Nothing> {
        return when (code) {
            400 -> Resource.Error("Something went wrong") // BAD REQUEST
            401 -> Resource.Error("Login not authorized")
            403 -> Resource.Error("Login forbidden")
            404 -> Resource.Error("Login not found")
            405 -> Resource.Error("Login method not allowed")
            408 -> Resource.Error("Login request timeout")
            429 -> Resource.Error("Too many login requests")
            500 -> Resource.Error("Server is having a bad day") // INTERNAL SERVER ERROR
            501 -> Resource.Error("Login not implemented")
            502 -> Resource.Error("Something went wrong") // BAD GATEWAY
            503 -> Resource.Error("Login is unavailable")
            504 -> Resource.Error("Something went wrong") // GATEWAY TIMEOUT
            else -> Resource.Error("Something went wrong")
        }
    }
}
