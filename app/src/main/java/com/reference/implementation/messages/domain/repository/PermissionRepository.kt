package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserPermissionDomainModel
import com.reference.implementation.messages.domain.model.UserRoleDomainModel

interface PermissionRepository {
    suspend fun getPermissionInfo(onRetry: suspend (Int) -> Unit): NetworkResult<UserPermissionDomainModel>
}
