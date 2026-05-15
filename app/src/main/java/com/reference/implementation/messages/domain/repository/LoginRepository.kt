package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel

interface LoginRepository {
    suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<UserDomainModel>
}
