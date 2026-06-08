package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.domain.model.UserPermissionDomainModel
import com.reference.implementation.messages.domain.repository.PermissionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class PermissionRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
) : PermissionRepository {
    override suspend fun getPermissionInfo(onRetry: suspend (Int) -> Unit): NetworkResult<UserPermissionDomainModel> {
        // force the execution onto the IO thread pool
        return withContext(Dispatchers.IO) {
            val permissionIds =
                when (val userPermissionsSessionResult = sessionManager.getSessionPermissionIds()) {
                    is SessionResult.Authenticated -> userPermissionsSessionResult.data
                    else -> emptyList()
                }
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getPermissions(permissionIds)
                }
                if (response.isSuccessful && response.body() != null) {
                    val permissionTasks = response.body()!!.map { it.task }
                    // DTO never leaves this layer!
                    NetworkResult.Success(UserPermissionDomainModel(permissionTasks))
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no permission info")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} get permission info ended")
                }
            }
        }
    }
}