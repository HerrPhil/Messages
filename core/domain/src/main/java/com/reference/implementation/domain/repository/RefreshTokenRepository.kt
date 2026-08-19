package com.reference.implementation.domain.repository

import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.RefreshTokenDomainModel

interface RefreshTokenRepository {
    suspend fun refreshToken(tokenUsedByRequest: String): NetworkResult<RefreshTokenDomainModel>
}