package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.di.ApplicationScope
import com.reference.implementation.data.di.AuthNetwork
import com.reference.implementation.data.di.IoDispatcher
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.model.SyncState
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.MessageDao
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.UserMessageReadUnreadDomainModel
import com.reference.implementation.domain.repository.MessageRepository
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

    /**
     * THE DEPTH-FIRST ENGINE: Connects use cases natively to the database pipe.
     * Your public stream now yields a unified payload containing BOTH local data and sync health!
     * Note: CachePayload is the evolution from NetworkResult.
     * Note: There is NO .onCompletion {} - the Room stream is infinite.
     * Note: There is NO .catch {} - the network health check is information in CachePayload.
     * Note: Called by Admin Dashboard - that is why it syncs all messages.
     */
    override fun getSummaryMessages(): Flow<CachePayload<String>> =
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
                syncMessages()
            }
        }.flowOn(ioDispatcher)

    /**
     * This is Phase 1 code of my re-factor of GetUserDashboardUseCase.
     * Phase 2 moves to leveraging the getMessagesByUser() function of MessageCacheRepository.
     * Note: Called by User Dashboard - that is why it syncs messages by user ID.
     *
     * Learning:
     * The function getMessagesByUserFlow(onRetry: (Int) -> Unit) was called once. Once.
     * It was a stop-gap measure for the absence of the Room  cache.
     * The reason not due to caching though. It was due to no query feature in the RESTful API,
     * short of writing a custom middleware.
     *
     * Now, with Room, this function can use a customized DAO to roll up the read/unread integers.
     * GetUserDashboardUseCase, the one user of this function, only uses counts
     * of the list of messages.
     */
    override fun getUserReadUnreadCounts(): Flow<CachePayload<UserMessageReadUnreadDomainModel>> =
        flow {

            val userId = when (val userIdResult = sessionManager.getSessionUserId()) {
                is SessionResult.Authenticated -> userIdResult.data
                else -> 0
            }

            // THE CONFLUENCE ENGINE:
            // We combine Room's infinite stream with our private background sync status flow!
            messageDao.getUserMessageReadUnreadStream(userId)
                .combine(syncStatus) { result, status ->
                    val domainModel = UserMessageReadUnreadDomainModel(
                        readCount = result.readCount,
                        unreadCount = result.unreadCount
                    )
                    CachePayload.ActiveStream(
                        data = domainModel,
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
                syncMessages()
            }
        }.flowOn(ioDispatcher)

    private suspend fun syncMessages() {

        val onRetry: (Int) -> Unit =
            { attempt -> auditLog("getSummaryMessages has retry attempt # $attempt.") }

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