package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.util.NetworkResult

interface LoginRepository {
    suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<LoginUserDomainModel>
}
