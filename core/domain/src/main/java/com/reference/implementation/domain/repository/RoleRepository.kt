package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.UserRoleDomainModel
import kotlinx.coroutines.flow.Flow

interface RoleRepository {
    fun getRoleInfoFlow(): Flow<NetworkResult<UserRoleDomainModel>>
    suspend fun configureUserProfile(loginUser: LoginUserDomainModel, onRetry: suspend (Int) -> Unit): NetworkResult<Unit>
}