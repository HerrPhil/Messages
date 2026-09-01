package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.RefreshTokenDomainModel
import com.reference.implementation.domain.repository.RefreshTokenRepository
import com.reference.implementation.domain.util.NetworkResult

class RefreshTokenUseCase(private val repo: RefreshTokenRepository) {
    suspend operator fun invoke(
        tokenUsedByRequest: String
    ): Resource<RefreshTokenDomainModel> {
        val refreshTokenNetworkResult = repo.refreshToken(
            tokenUsedByRequest = tokenUsedByRequest,
            onRetry = { /* helps in unit testing */ }
        )
        if (refreshTokenNetworkResult is NetworkResult.Error && refreshTokenNetworkResult.code == 403) {
            // shows expired refresh token message from server, more informative than "Refresh Token forbidden"
            return Resource.Error("Refresh Token ${refreshTokenNetworkResult.message}")
        }
        return refreshTokenNetworkResult.toResource("Refresh Token") { refreshTokenDomainModel ->
            refreshTokenDomainModel
        }
    }
}