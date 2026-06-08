package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserRoleDomainModel

interface RoleRepository {
    suspend fun getRoleInfo(onRetry: suspend (Int) -> Unit): NetworkResult<UserRoleDomainModel>
}