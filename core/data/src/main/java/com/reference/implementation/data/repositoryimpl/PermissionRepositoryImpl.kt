package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.di.ApplicationScope
import com.reference.implementation.data.di.AuthNetwork
import com.reference.implementation.data.di.IoDispatcher
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.model.SyncState
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.PermissionDao
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.UserPermissionDomainModel
import com.reference.implementation.domain.repository.PermissionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher, // Hilt provides it
    @AuthNetwork private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val permissionDao: PermissionDao,
    @ApplicationScope private val externalScope: CoroutineScope
) : PermissionRepository {

    // THE STATUS SCRATCHPAD: Tracks only the active network sync state internally.
    // Starts as completely idle (No errors, not syncing).
    private val syncStatus = MutableStateFlow<SyncState>(SyncState.Idle)

    /**
     * Fetches user permissions based on session IDs and emits a domain model.
     */
    override fun getPermissionsFlow(): Flow<CachePayload<UserPermissionDomainModel?>> =
        flow {

            // THE CONFLUENCE ENGINE
            // We combine Room's infinite stream with our private background sync status flow!
            // Technical Note: A Flow<List<PermissionEntity>> will return an empty list when
            //   no permissions are found.
            permissionDao.getPermissionsStream()
                .combine(syncStatus) { result, status ->
                    // Only care about the task names of permissions.
                    val domainModel = UserPermissionDomainModel(
                        permissions = result.map { it.task }
                    )
                    CachePayload.ActiveStream(
                        data = domainModel,
                        isSyncing = status is SyncState.Loading,
                        syncError = (status as? SyncState.Error)?.exception // safe-casting
                    )
                }.collect { unifiedPayload ->
                    emit(unifiedPayload)
                }

        }.onStart {
            // Emitters documentation says:
            // This action is called before the upstream flow is started.
            // Trigger background sync exactly once on collection entry
            externalScope.launch {
                syncPermissions()
            }

        }.flowOn(ioDispatcher)

    private suspend fun syncPermissions() {

        val onRetry: (Int) -> Unit =
            { attempt -> auditLog("getSummaryMessages has retry attempt # $attempt.") }

        val permissionIds = when (val res = sessionManager.getSessionPermissionIds()) {
            is SessionResult.Authenticated -> res.data
            else -> emptyList()
        }

        try {

            // 1. Mark status scratchpad as Loading
            syncStatus.value = SyncState.Loading

            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getPermissions(permissionIds)
                if (res.code() >= 500) {
                    throw HttpException(res) // Force retryIO's catch block to trigger!
                }
                res
            }

            val body = response.body()

            if (response.isSuccessful && body != null) {

                // Map incoming cloud DTOs straight to the physical BulletinEntity rows.
                val localEntities = body.map { dto -> dto.toEntity() }

                // Room Integration: Execute the atomic update-or-insert transaction loop
                // Dump directly into Room! SQLite handles the OnConflictStrategy.REPLACE upsert
                // natively, safely refreshing values while updating your open streams instantly.
                permissionDao.insertPermissions(localEntities)

                // Payload refactor away from NetworkResult
                // 2. Clear out any error memory states on structural network success
                syncStatus.value = SyncState.Idle
            } else {
                auditLog("Sync failed with client error: ${response.code()}")
                // Payload refactor away from NetworkResult
                // 3. Capture 4xx Client Errors safely inside the state flow
                syncStatus.value = SyncState.Error(exception = HttpException(response))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            auditLog("Background network sync silently deferred: ${e.message}")
            // 4. Capture 5xx Network Errors or IO timeouts without breaking the repository!
            syncStatus.value = SyncState.Error(exception = e)
        }
    }
}