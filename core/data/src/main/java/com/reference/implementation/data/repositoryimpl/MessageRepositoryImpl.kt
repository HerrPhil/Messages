package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.di.ApplicationScope
import com.reference.implementation.data.di.AuthNetwork
import com.reference.implementation.data.di.IoDispatcher
import com.reference.implementation.data.dtos.MarkMessageAsReadDto
import com.reference.implementation.data.dtos.MarkMessageAsUnreadDto
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.mappers.toDomainModel
import com.reference.implementation.data.mappers.toEntity
import com.reference.implementation.data.mappers.toMessageRequestDto
import com.reference.implementation.data.model.SyncState
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.MessageDao
import com.reference.implementation.domain.model.AdminMessageUnreadTotalDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.UserMessageReadUnreadDomainModel
import com.reference.implementation.domain.repository.MessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _uiEventChannel = Channel<MessageDomainEvent>(Channel.BUFFERED)

    // THE STATUS SCRATCHPAD: Tracks only the active network sync state internally.
    // Starts as completely idle (No errors, not syncing).
    private val syncStatus = MutableStateFlow<SyncState>(SyncState.Idle)

    override fun getMessageDomainEvents(): Flow<MessageDomainEvent> =
        _uiEventChannel.receiveAsFlow()

    /**
     * THE DEPTH-FIRST ENGINE: Connects use cases natively to the database pipe.
     * Your public stream now yields a unified payload containing BOTH local data and sync health!
     * Note: CachePayload is the evolution from NetworkResult.
     * Note: There is NO .onCompletion {} - the Room stream is infinite.
     * Note: There is NO .catch {} - the network health check is information in CachePayload.
     * Note: Called by Admin Dashboard - that is why it syncs all messages.
     */
    override fun getAdminUnreadTotalCounts(): Flow<CachePayload<AdminMessageUnreadTotalDomainModel>> =
        flow {

            // THE CONFLUENCE ENGINE:
            // We combine Room's infinite stream with our private background sync status flow!
            messageDao.getAdminMessageUnreadTotalStream()
                .combine(syncStatus) { result, status ->

                    val domainModel = AdminMessageUnreadTotalDomainModel(
                        unreadCount = result.unreadCount,
                        totalCount = result.totalCount
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

    override fun getMessagesForActiveUser(): Flow<CachePayload<List<MessageDomainModel>>> {
        val userId = when (val userIdResult = sessionManager.getSessionUserId()) {
            is SessionResult.Authenticated -> userIdResult.data
            else -> 0
        }
        return getMessagesForUser(userId)
    }

    override fun getMessagesForSelectedUser(userId: Int): Flow<CachePayload<List<MessageDomainModel>>> {
        return getMessagesForUser(userId)
    }

    override fun getMessageByIdFlow(messageId: Int): Flow<CachePayload<MessageDomainModel?>> =
        messageDao.getMessageByIdStream(messageId)
            .combine(syncStatus) { entity, status ->
                CachePayload.ActiveStream(
                    data = entity?.toDomainModel(), // Map the single database row cleanly to a Domain model
                    isSyncing = status is SyncState.Loading,
                    syncError = (status as? SyncState.Error)?.exception
                )
            }.flowOn(ioDispatcher)

    override suspend fun markMessageAsRead(messageId: Int) {

        // 1. THE OPTIMISTIC UPDATE: Update local database state authority immediately!
        // The local Room flow stream auto-detects this change and updates the UI instantly.
        messageDao.updateReadStatus(messageId, isRead = true)

        // 2. ASYNCHRONOUS BACKEND SYNC: Execute the network task
        // independently on the background thread pool.
        externalScope.launch {
            val onRetry: (Int) -> Unit =
                { attempt -> auditLog("markMessageAsRead has retry attempt # $attempt.") }

            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.markMessageAsRead(messageId, MarkMessageAsReadDto())
                    if (res.code() >= 500) {
                        throw HttpException(res) // Force retryIO's catch block to trigger!
                    }
                    res
                }
                if (!response.isSuccessful) { // 4xx errors
                    // 3. CLIENT ERROR ROLLBACK: The server rejected it!
                    // Flip the flag back to false.
                    messageDao.updateReadStatus(messageId, isRead = false)
                    _uiEventChannel.send(MessageDomainEvent.MessageMarkReadFailureFeedback)
                }

            } catch (e: Exception) { // 5xx errors
                if (e is CancellationException) throw e
                auditLog("Cloud mark read failed: ${e.message}")

                // 4. THE NETWORK OUTAGE ROLLBACK: Server timeout or network drop!
                messageDao.updateReadStatus(messageId, isRead = false)
                _uiEventChannel.send(MessageDomainEvent.MessageMarkReadFailureFeedback)
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} delete message ended")
                }
            }
        }
    }

    override suspend fun markMessageAsUnread(messageId: Int) {

        // 1. THE OPTIMISTIC UPDATE: Update local database state authority immediately!
        // The local Room flow stream auto-detects this change and updates the UI instantly.
        messageDao.updateReadStatus(messageId, isRead = false)

        // 2. ASYNCHRONOUS BACKEND SYNC: Execute the network task
        // independently on the background thread pool.
        externalScope.launch {
            val onRetry: (Int) -> Unit =
                { attempt -> auditLog("markMessageAsRead has retry attempt # $attempt.") }

            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.markMessageAsUnread(messageId, MarkMessageAsUnreadDto())
                    if (res.code() >= 500) {
                        throw HttpException(res) // Force retryIO's catch block to trigger!
                    }
                    res
                }
                if (!response.isSuccessful) { // 4xx errors
                    // 3. CLIENT ERROR ROLLBACK: The server rejected it!
                    // Flip the flag back to false.
                    messageDao.updateReadStatus(messageId, isRead = true)
                    _uiEventChannel.send(MessageDomainEvent.MessageMarkReadFailureFeedback)
                }

            } catch (e: Exception) { // 5xx errors
                if (e is CancellationException) throw e
                auditLog("Cloud mark unread failed: ${e.message}")

                // 4. THE NETWORK OUTAGE ROLLBACK: Server timeout or network drop!
                messageDao.updateReadStatus(messageId, isRead = true)
                _uiEventChannel.send(MessageDomainEvent.MessageMarkReadFailureFeedback)
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} delete message ended")
                }
            }
        }
    }

    override suspend fun deleteMessage(messageId: Int) {

        // One-shot read/write actions do not automatically switch dispatcher in Room.

        // THE DISPATCHER RESOLUTION: Force the one-shot lookup and destruction
        // onto the IO thread pool!

        withContext(ioDispatcher) {

            // Step 1: Query Room locally to fetch the target entity BEFORE it is destroyed.
            // This takes microseconds because it is a fast primary-key lookup in SQLite!
            val messageToBackup = messageDao.getMessageById(messageId)

            // Step 2: Now, execute the local database state authority destruction!
            // the row is deleted, and the UI card vanishes instantly.
            messageDao.deleteMessageById(messageId)

            // Step 3: Launch the background network task independently
            externalScope.launch {
                val onRetry: (Int) -> Unit =
                    { attempt -> auditLog("deleteMessage has retry attempt # $attempt.") }
                try {
                    // 3. Make your live network call to Express backend
                    val response = retryIO(times = 3, onRetry = onRetry) {
                        val res = apiService.removeMessage(messageId)
                        if (res.code() >= 500) {
                            throw HttpException(res) // Force retryIO's catch block to trigger!
                        }
                        res
                    }
                    if (messageToBackup != null) {
                        if (response.isSuccessful) {
                            // The Google AI suggestion does not know about my UNDO (restore) feaure.
                            // The event channel needs to offer the UI the value to restore
                            // when the user clicks UNDO. It must pass the target message as its
                            // domain model state.
                            _uiEventChannel.send(
                                MessageDomainEvent.MessageDeleteSuccessFeedback(
                                    messageToBackup.toDomainModel()
                                )
                            )
                        } else { // 4xx errors
                            // Roll back the database natively if the cloud rejects the deletion.
                            messageDao.insertMessages(listOf(messageToBackup))
                            // Alert the user via your one-shot event channel
                            _uiEventChannel.send(MessageDomainEvent.MessageDeleteFailureFeedback)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Roll back the database natively on network/timeout outages
                    if (messageToBackup != null) {
                        messageDao.insertMessages(listOf(messageToBackup))
                    }
                    _uiEventChannel.send(MessageDomainEvent.MessageDeleteFailureFeedback)
                } finally {
                    withContext(NonCancellable) {
                        auditLog("${auditLogTimestamp()} delete message ended")
                    }
                }
            }
        }
    }

    /**
     * RE: Local Database State Authority is the architectural core for offline-first.
     * The local SQLite database dictates the immediate reality for the user.
     * When a user taps "UNDO", they expect the message card to snap back onto the screen instantly.
     * Optimistic Restore Lifecycle:
     * 1. The Immediate Local Action: Instantly map the MessageDomainModel back into a MessageEntity
     * and insert it into SQLite. Because the UI listens to a live Room query stream, the card
     * instantly reappears on the screen in milliseconds.
     * 2. The Asynchronous Cloud Synchronization: Launch the background network task independently.
     * 3. The Isolated Rollback Pass: If the server explicitly rejects the restoration
     * (e.g. a 4xx/5xx error) or times out, execute a targeted database deletion command to remove
     * only that specific message from SQLite. The card vanishes back off the screen, and alert the
     * user via the one-shot event channel.
     */
    override suspend fun restoreMessage(deletedMessage: MessageDomainModel) {

        // One-shot read/write actions do not automatically switch dispatcher in Room.

        // THE DISPATCHER RESOLUTION: Force the one-shot lookup and destruction
        // onto the IO thread pool!

        withContext(ioDispatcher) {

            // Map the domain model back into a physical database row entity.
            val entityToRestore = deletedMessage.toEntity()

            // 1. OPTIMISTIC INSERTION: Force the local database state authority update immediately!
            // This causes the card to snap back onto the user's screen in a fraction of a second.
            messageDao.insertMessages(listOf(entityToRestore))

            val onRetry: (Int) -> Unit =
                { attempt -> auditLog("restoreMessage has retry attempt # $attempt.") }

            // 2. ASYNCHRONOUS BACKEND SYNC: Execute the cloud task
            // on the background application scope pool.
            externalScope.launch {
                try {
                    val messageRequestDto = deletedMessage.toMessageRequestDto()
                    val response = retryIO(times = 3, onRetry = onRetry) {
                        val res = apiService.addMessage(messageRequestDto)
                        if (res.code() >= 500) {
                            throw HttpException(res) // Force retryIO's catch block to trigger!
                        }
                        res
                    }
                    if (response.isSuccessful) {
                        // SUCCESS! The cloud server has accepted the item.
                        // Fire the stateless presentation snackbar success
                        _uiEventChannel.send(MessageDomainEvent.MessageRestoreSuccessFeedback)
                    } else { // 4xx errors
                        // 3. CLIENT ERROR ROLLBACK: The server explicitly rejected the insertion!
                        // Execute a targeted database deletion to remove this specific row.
                        messageDao.deleteMessageById(deletedMessage.id)
                        _uiEventChannel.send(MessageDomainEvent.MessageRestoreFailureFeedback)
                    }

                } catch (e: Exception) { // 5xx errors
                    if (e is CancellationException) throw e
                    auditLog("Cloud restore failed: ${e.message ?: "no message"}")
                    // 4. NETWORK OUTAGE ROLLBACK: Server timed out or internet is completely down!
                    // Pull the item back out of SQLite natively
                    // to keep the local cache perfectly synced.
                    messageDao.deleteMessageById(deletedMessage.id)
                    _uiEventChannel.send(MessageDomainEvent.MessageRestoreFailureFeedback)
                } finally {
                    withContext(NonCancellable) {
                        auditLog("${auditLogTimestamp()} restore message ended")
                    }
                }
            }
        }
    }

    private fun getMessagesForUser(userId: Int): Flow<CachePayload<List<MessageDomainModel>>> =
        flow {

            // THE CONFLUENCE ENGINE
            // We combine Room's infinite stream with our private background sync status flow!
            messageDao.getMessagesForUserStream(userId)
                .combine(syncStatus) { result, status ->
                    val filteredData = result
                        .filter { it.userId == userId }
                        .map { it.toDomainModel() }
                    CachePayload.ActiveStream(
                        data = filteredData,
                        isSyncing = status is SyncState.Loading,
                        syncError = (status as? SyncState.Error)?.exception
                    )
                }.collect { unifiedPayload ->
                    emit(unifiedPayload)
                }
        }.onStart {
            // Emitter's documentation says:
            // This action is called before the upstream flow is started.
            // Trigger background sync exactly once on collection entry.
            externalScope.launch {
                syncMessages()
            }
        }.flowOn(ioDispatcher)

    override suspend fun syncMessages() {

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