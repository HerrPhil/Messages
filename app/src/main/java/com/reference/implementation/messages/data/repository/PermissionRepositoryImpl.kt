package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.domain.model.UserPermissionDomainModel
import com.reference.implementation.messages.domain.repository.PermissionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class PermissionRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
) : PermissionRepository {

    /**
     * Fetches user permissions based on session IDs and emits a domain model.
     */
    override fun getPermissionInfoFlow(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<UserPermissionDomainModel>> =
        flow {
            emit(NetworkResult.Loading)

            val permissionIds = when (val res = sessionManager.getSessionPermissionIds()) {
                is SessionResult.Authenticated -> res.data
                else -> emptyList()
            }

            val response = retryIO(times = 3, onRetry = onRetry) {
                apiService.getPermissions(permissionIds)
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                val permissionTasks = body.map { it.task }
                emit(NetworkResult.Success(UserPermissionDomainModel(permissionTasks)))
            } else {
                emit(NetworkResult.Error(response.code(), response.message()))
            }

        }.catch { e ->
            if (e is CancellationException) throw e
            Audit.createInstance().writeLog(e.message ?: "no permission info")
            emit(NetworkResult.Exception(e))
        }.flowOn(Dispatchers.IO) // Keeps network execution on the IO thread pool

}