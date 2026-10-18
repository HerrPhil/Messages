package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.di.ApplicationScope
import com.reference.implementation.data.di.AuthNetwork
import com.reference.implementation.data.di.IoDispatcher
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.mappers.toMessageDomainModel
import com.reference.implementation.data.model.SyncState
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.MessageDao
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    @AuthNetwork private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher, // Hilt provides it
    private val sessionManager: SessionManager,
    private val messageDao: MessageDao,
    @ApplicationScope private val externalScope: CoroutineScope
) : MessageRepository {

    // THE STATUS SCRATCHPAD: Tracks only the active network sync state internally.
    // Starts as completely idle (No errors, not syncing).
    private val syncStatus = MutableStateFlow<SyncState>(SyncState.Idle)

    // THE DEPTH-FIRST ENGINE: Connects use cases natively to the database pipe.
    // Your public stream now yields a unified payload containing BOTH local data and sync health!
    // Note: CachePayload is the evolution from NetworkResult.
    // Note: There is NO .onCompletion {} - the Room stream is infinite.
    // Note; There is NO .catch {} - the network health check is information in CachePayload
    override fun getSummaryMessages(onRetry: (Int) -> Unit): Flow<CachePayload<String>> =
        flow {

            // THE CONFLUENCE ENGINE:
            // We combine Room's infinite stream with our private background sync status flow!
            messageDao.getMessagesStream()
                .combine(syncStatus) { entities, status ->

                    val unreadMessages = entities.count { !it.read }
                    val allMessages = entities.size
                    val summaryMessage = "$unreadMessages / $allMessages"

                    CachePayload.ActiveStream(
                        data = summaryMessage,
                        isSyncing = status is SyncState.Loading,
                        syncError = (status as? SyncState.Error)?.exception // propagates 4xx/5xx errors
                    )
                }.collect { unifiedPayload ->
                    // Emit the combined model down your existing public pipeline channels!
                    emit(unifiedPayload)
                }
        }.onStart {
            // Emitters documentation says:
            // This action is called before the upstream flow is started.
            // Trigger background sync exactly once on collection entry
            externalScope.launch {
                syncMessages(onRetry)
            }
        }.flowOn(ioDispatcher)

    /**
     * This is Phase 1 code of my re-factor of GetUserDashboardUseCase.
     * Phase 2 moves to leveraging the getMessagesByUser() function of MessageCacheRepository.
     */
    override fun getMessagesByUserFlow(onRetry: (Int) -> Unit): Flow<NetworkResult<List<MessageDomainModel>>> =
        flow {
            emit(NetworkResult.Loading)

            val userId = when (val userIdResult = sessionManager.getSessionUserId()) {
                is SessionResult.Authenticated -> userIdResult.data
                else -> 0
            }

            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getMessages(userId)
                if (res.code() >= 500) {
                    throw HttpException(res) // Force retryIO's catch block to trigger!
                }
                res
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(NetworkResult.Success(body.map { it.toMessageDomainModel() }))
            } else {
                emit(NetworkResult.Error(response.code(), response.message()))
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            auditLog(e.message ?: "no messages")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get messages ended")
            }
        }.flowOn(ioDispatcher) // Note: Dispatchers.IO is better suited for Network/API calls!

    private suspend fun syncMessages(onRetry: (Int) -> Unit) {
        try {

            // Payload refactor away from NetworkResult

            // 1. Mark status scratchpad as Loading
            syncStatus.value = SyncState.Loading

            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getMessages()
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
                messageDao.insertMessages(localEntities)

                // Payload refactor away from NetworkResult
                // 2. Clear out any error memory states on structural network success
                syncStatus.value = SyncState.Idle
            } else { // 4xx errors
                auditLog("Sync failed with client error: ${response.code()}")
                // Payload refactor away from NetworkResult
                // 3. Capture 4xx Client Errors safely inside the state flow
                syncStatus.value = SyncState.Error(exception = HttpException(response))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            auditLog("Background network sync silently deferred: ${e.message}")
            // Payload refactor away from NetworkResult
            // 4. Capture 5xx Network Errors or IO timeouts without breaking the repository!
            syncStatus.value = SyncState.Error(exception = e)
        }
    }

}