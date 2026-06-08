package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.domain.model.UserRoleDomainModel
import com.reference.implementation.messages.domain.repository.RoleRepository

class RoleRepositoryImpl(
    private val sessionManager: SessionManager
) : RoleRepository {
    override suspend fun getRoleInfo(onRetry: suspend (Int) -> Unit): NetworkResult<UserRoleDomainModel> {
        val userRoleNames = when (val userRolesSessionResult = sessionManager.getSessionRoleNames()) {
            is SessionResult.Authenticated -> userRolesSessionResult.data
            else -> emptyList()
        }
        val userRoleDomainModel = UserRoleDomainModel(userRoleNames)
        return NetworkResult.Success(userRoleDomainModel)
    }
}