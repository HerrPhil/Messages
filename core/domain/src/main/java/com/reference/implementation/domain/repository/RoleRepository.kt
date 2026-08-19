package com.reference.implementation.domain.repository

import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.UserRoleDomainModel
import kotlinx.coroutines.flow.Flow

interface RoleRepository {
    fun getRoleInfoFlow(): Flow<NetworkResult<UserRoleDomainModel>>
}