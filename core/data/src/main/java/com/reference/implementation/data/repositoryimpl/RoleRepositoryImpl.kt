package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.domain.model.UserRoleDomainModel
import com.reference.implementation.domain.repository.RoleRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class RoleRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
            auditLog(e.message ?: "no role info")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get role info ended")
            }
        }.flowOn(ioDispatcher)

}