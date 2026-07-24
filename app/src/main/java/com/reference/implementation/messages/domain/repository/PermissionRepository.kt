package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserPermissionDomainModel
import kotlinx.coroutines.flow.Flow

interface PermissionRepository {
    fun getPermissionInfoFlow(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<UserPermissionDomainModel>>
}
