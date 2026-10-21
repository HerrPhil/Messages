package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.LoginUserDomainModel
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserInfoFlow(): Flow<NetworkResult<LoginUserDomainModel>>
    fun getUserCount(): Flow<CachePayload<Int>>
    fun getUsers(): Flow<CachePayload<List<LoginUserDomainModel>>>
}