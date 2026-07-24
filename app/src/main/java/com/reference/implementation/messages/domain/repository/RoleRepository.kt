package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserRoleDomainModel
import kotlinx.coroutines.flow.Flow

interface RoleRepository {
    fun getRoleInfoFlow(): Flow<NetworkResult<UserRoleDomainModel>>
}