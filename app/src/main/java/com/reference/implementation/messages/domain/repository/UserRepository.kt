package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserInfoFlow(): Flow<NetworkResult<LoginUserDomainModel>>
}