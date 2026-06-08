package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
import okio.IOException
import retrofit2.HttpException

class LoginUseCase(private val repo: LoginRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): Resource<LoginUserDomainModel> {
        return when (val loginNetworkResult = repo.login(email, password, onRetry)) {
            is NetworkResult.Success -> {
                Resource.Success(data = loginNetworkResult.data) // pass the user domain model
            }

            is NetworkResult.Error -> {
                getResourceErrorByCode("login", loginNetworkResult.code)
            }

            is NetworkResult.Exception -> {
                when (loginNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode("login", loginNetworkResult.e.code())
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }
}
