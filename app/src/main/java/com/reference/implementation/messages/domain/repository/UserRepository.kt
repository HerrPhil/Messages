package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel

interface UserRepository {
    suspend fun getUserInfo(onRetry: suspend (Int) -> Unit): NetworkResult<LoginUserDomainModel>
}