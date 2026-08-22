package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.UserPermissionDomainModel
import com.reference.implementation.domain.repository.PermissionRepository
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

class PermissionRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
            auditLog(e.message ?: "no permission info")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get permission info ended")
            }
        }.flowOn(ioDispatcher) // Keeps network execution on the IO thread pool

}