package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.RefreshTokenDomainModel
import com.reference.implementation.messages.domain.repository.RefreshTokenRepository

class RefreshTokenUseCase(private val repo: RefreshTokenRepository) {
    suspend operator fun invoke(
        tokenUsedByRequest: String
    ): Resource<RefreshTokenDomainModel> {
        val refreshTokenNetworkResult = repo.refreshToken(tokenUsedByRequest)
        if (refreshTokenNetworkResult is NetworkResult.Error && refreshTokenNetworkResult.code == 403) {
            // shows expired refresh token message from server, more informative than "Refresh Token forbidden"
            return Resource.Error("Refresh Token ${refreshTokenNetworkResult.message}")
        }
        return refreshTokenNetworkResult.toResource("Refresh Token") { refreshTokenDomainModel ->
            refreshTokenDomainModel
        }
    }
}