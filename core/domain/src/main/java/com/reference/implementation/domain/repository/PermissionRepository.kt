package com.reference.implementation.domain.repository

import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.UserPermissionDomainModel
import kotlinx.coroutines.flow.Flow

interface PermissionRepository {
    fun getPermissionInfoFlow(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<UserPermissionDomainModel>>
}