package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.RefreshTokenDomainModel
import com.reference.implementation.messages.domain.repository.RefreshTokenRepository
import okio.IOException
import retrofit2.HttpException

class RefreshTokenUseCase(private val repo: RefreshTokenRepository) {
    suspend operator fun invoke(
        tokenUsedByRequest: String
    ): Resource<RefreshTokenDomainModel> {
        return when (val refreshTokenNetworkResult = repo.refreshToken(tokenUsedByRequest)) {
            is NetworkResult.Loading -> Resource.Loading
            is NetworkResult.Success -> {
                Resource.Success(data = refreshTokenNetworkResult.data)
            }

            is NetworkResult.Error -> {
                if (refreshTokenNetworkResult.code == 403) { // Get force logout message from backend
                    Resource.Error("RefreshTokenUseCase ${refreshTokenNetworkResult.message}")
                } else {
                    getResourceErrorByCode("RefreshTokenUseCase", refreshTokenNetworkResult.code)
                }
            }

            is NetworkResult.Exception -> {
                when (refreshTokenNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode(
                            "RefreshTokenUseCase",
                            refreshTokenNetworkResult.e.code()
                        )
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }
}