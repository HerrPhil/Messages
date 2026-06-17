package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.RefreshTokenDomainModel

interface RefreshTokenRepository {
    suspend fun refreshToken(tokenUsedByRequest: String): NetworkResult<RefreshTokenDomainModel>
}