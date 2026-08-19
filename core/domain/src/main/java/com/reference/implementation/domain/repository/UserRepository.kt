package com.reference.implementation.domain.repository

import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.LoginUserDomainModel
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserInfoFlow(): Flow<NetworkResult<LoginUserDomainModel>>
    fun getUserCount(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<Int>>
    fun getUsers(): Flow<NetworkResult<List<LoginUserDomainModel>>>
    // The following has no return value - stores result in cached Flow
    suspend fun loadAllUsers(onRetry: suspend (Int) -> Unit)
}