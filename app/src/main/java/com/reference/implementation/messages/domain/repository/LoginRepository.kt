package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginDomainModel

interface LoginRepository {
    suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<LoginDomainModel>
}
