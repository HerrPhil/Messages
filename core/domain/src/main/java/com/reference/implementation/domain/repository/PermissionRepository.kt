package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.UserPermissionDomainModel
import kotlinx.coroutines.flow.Flow

interface PermissionRepository {
    fun getPermissionsFlow(): Flow<CachePayload<UserPermissionDomainModel?>>
}