package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.domain.model.UserRoleDomainModel
import com.reference.implementation.messages.domain.repository.RoleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class RoleRepositoryImpl(
    private val sessionManager: SessionManager
) : RoleRepository {

    override fun getRoleInfoFlow(): Flow<NetworkResult<UserRoleDomainModel>> =
        flow {
            emit(NetworkResult.Loading)

            val userRoleNames = when (val res = sessionManager.getSessionRoleNames()) {
                is SessionResult.Authenticated -> res.data
                else -> emptyList()
            }

            val userRoleDomainModel = UserRoleDomainModel(userRoleNames)

            emit(NetworkResult.Success(userRoleDomainModel))
        }.catch { e ->
            if (e is CancellationException) throw e
            Audit.createInstance().writeLog(e.message ?: "no role info")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                Audit.createInstance().writeLog("${auditLogTimestamp()} get role info ended")
            }
        }.flowOn(Dispatchers.Default)

}