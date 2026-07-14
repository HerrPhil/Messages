package com.reference.implementation.messages.data.repository

import android.util.Log
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.MarkMessageAsReadDto
import com.reference.implementation.messages.data.remote.MarkMessageAsUnreadDto
import com.reference.implementation.messages.data.remote.toMessageDomainModel
import com.reference.implementation.messages.data.remote.toMessageRequestDto
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.presentation.screens.message.MessageUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

class MessageCacheRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : MessageCacheRepository {

    private val _uiEventChannel = Channel<MessageUiEvent>(Channel.BUFFERED)

    // 1. The Local Memory Cache (The Single Source of Truth)
    private val _messagesByUserCache =
        MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(NetworkResult.Loading)

    // 2(a). The Read-Only Stream: Anyone can listen to this at any time
    override fun getMessagesByUser(): Flow<NetworkResult<List<MessageDomainModel>>> =
        _messagesByUserCache.asStateFlow()

    // 2(b). The Other Read-Only Stream (flavour): Anyone can listen to this at any time too
    val messagesByUserCache: StateFlow<NetworkResult<List<MessageDomainModel>>> =
        _messagesByUserCache.asStateFlow()

    override fun getMessageUiEvents(): Flow<MessageUiEvent> =
        _uiEventChannel.receiveAsFlow()

    override val uiEvents = _uiEventChannel.receiveAsFlow()


    override suspend fun refreshMessagesByUser(onRetry: suspend (Int) -> Unit) {

        // Force the cache to show "Loading" if it a manual retry/refresh action
        _messagesByUserCache.value = NetworkResult.Loading

        // force the execution onto the IO thread pool
        withContext(Dispatchers.IO) {
            try {
                val userId = when (val userIdResult = sessionManager.getSessionUserId()) {
                    is SessionResult.Authenticated -> userIdResult.data
                    else -> 0
                }
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getMessages(userId)
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    _messagesByUserCache.value =
                        NetworkResult.Success(response.body()!!.map { it.toMessageDomainModel() })
                } else {
                    // Transform unsuccessful Retrofit calls.
                    // Update the SSOT cache with the network result error!
                    _messagesByUserCache.value =
                        NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                // Update the SSOT cache with the network result exception!
                _messagesByUserCache.value =
                    NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} get messages ended")
                }
            }
        }
    }

    override suspend fun markMessageAsRead(messageId: Int) {
        val response = withContext(Dispatchers.IO) {
            try {
                val response = retryIO(times = 3, onRetry = { attempt ->
                    Log.d("markMessageAsRead", "number of retries is $attempt")
                    // TODO play with passing a UIEvent of Retrying
                }) {
                    apiService.markMessageAsRead(messageId, MarkMessageAsReadDto())
                }
                if (response.isSuccessful) {
                    // Success! We have a MessageDto with the updated 'read' value
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    response.body()?.let { messageDto ->
                        NetworkResult.Success(messageDto.toMessageDomainModel())
                    } ?: NetworkResult.Error(400, "Response body was empty")
                } else {
                    // Transform unsuccessful Retrofit calls.
                    // Update the SSOT cache with the network result error!
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} mark message as read ended")
                }
            }
        }

        if (response is NetworkResult.Success) {
            toggleReadStatus(messageId) // Internal update of hot Status Flow
        } else {
            Log.d("markMessageAsRead", "send a message UI event")
            _uiEventChannel.send(MessageUiEvent.showToast("Unable to update read status"))
        }
    }

    override suspend fun markMessageAsUnread(messageId: Int) {
        val response = withContext(Dispatchers.IO) {
            try {
                val response = retryIO(times = 3, onRetry = { attempt ->
                    Log.d("markMessageAsRead", "number of retries is $attempt")
                    // TODO play with passing a UIEvent of Retrying
                }) {
                    apiService.markMessageAsUnread(messageId, MarkMessageAsUnreadDto())
                }
                if (response.isSuccessful) {
                    // Success! We have a MessageDto with the updated 'read' value
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    response.body()?.let { messageDto ->
                        NetworkResult.Success(messageDto.toMessageDomainModel())
                    } ?: NetworkResult.Error(400, "Response body was empty")
                } else {
                    // Transform unsuccessful Retrofit calls.
                    // Update the SSOT cache with the network result error!
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} mark message as read ended")
                }
            }
        }

        if (response is NetworkResult.Success) {
            toggleReadStatus(messageId) // Internal update of hot Status Flow
        } else {
            Log.d("markMessageAsUnread", "send a message UI event")
            _uiEventChannel.send(MessageUiEvent.showToast("Unable to toggle read status"))
        }
    }

    override suspend fun toggleReadStatus(messageId: Int) {
        val currentResult = _messagesByUserCache.value // NetworkResult<List<MessageDomainModel>>
        // via smart-casting
        if (currentResult is NetworkResult.Success) {
            val updatedList = currentResult.data.map { messageDomainModel ->
                if (messageDomainModel.id == messageId) {
                    // Return a copy with the INVERSE read status
                    messageDomainModel.copy(read = !messageDomainModel.read)
                } else {
                    // Leave other messages untouched
                    messageDomainModel
                }
            }
            _messagesByUserCache.value = NetworkResult.Success(updatedList)
        }
    }

    override suspend fun deleteMessage(messageId: Int) {
        // 1. Capture the safety net snapshot of the current state
        val originalState = _messagesByUserCache.value

        // 2. OPTIMISTIC UPDATE: Apply the filter immediately so the UI snaps closed
        if (originalState is NetworkResult.Success) { // due diligence - proceed when previously got data

            // 3. Find the target message first and grab its reference; otherwise return
            val targetMessage = originalState.data.find { it.id == messageId } ?: return

            // 4. Perform your optimistic update by filtering the list
            val updatedList = originalState.data.filter { it.id != messageId }
            _messagesByUserCache.value = NetworkResult.Success(updatedList)

            // This is inside the originalState is NetworkResult.Success if-condition
            // because the deletion action is DEPENDENT on of the state of _messagesByUserCache.value.
            // The message must exist to delete.
            withContext(Dispatchers.IO) {
                try {
                    // 3. Make your live network call to Express backend
                    val response = retryIO(times = 3, onRetry = { attempt ->
                        Log.d("markMessageAsRead", "number of retries is $attempt")
                        // TODO play with passing a UIEvent of Retrying
                    }) {
                        apiService.removeMessage(messageId)
                    }

                    if (response.isSuccessful) {
                        // Success! We have an empty JSON object, {}.
                        _uiEventChannel.send(MessageUiEvent.showDeleteSnackbar(targetMessage))
                    } else {
                        // 4. ROLLBACK: Put the original state back into the Flow.
                        // The UI will automatically detect this and smoothly animate the card back
                        _messagesByUserCache.value = originalState

                        // 5. Alert the user via your one-shot event channel
                        _uiEventChannel.send(MessageUiEvent.showToast("Unable to delete message. Please try again."))
                    }
                } catch (e: Exception) {
                    Audit.createInstance().writeLog(e.message ?: "no message")
                    // 4. ROLLBACK: Put the original state back into the Flow.
                    // The UI will automatically detect this and smoothly animate the card back
                    _messagesByUserCache.value = originalState

                    // 5. Alert the user via your one-shot event channel
                    _uiEventChannel.send(MessageUiEvent.showToast("Unable to delete message. Please try again."))
                } finally {
                    withContext(NonCancellable) {
                        Audit.createInstance()
                            .writeLog("${auditLogTimestamp()} delete message ended")
                    }
                }
            }
        }
    }

    override suspend fun restoreMessage(deletedMessage: MessageDomainModel) {
        // 1. Snapshot the current state in case the network restore network call fails
        val backupState = _messagesByUserCache.value

        // 2. OPTIMISTIC RESTORE: Shove the message back into the list manually
        if (backupState is NetworkResult.Success) {
            // Re-insert it - append it
            val restoredList = backupState.data + deletedMessage
            _messagesByUserCache.value = NetworkResult.Success(restoredList)
        }

        // This is outside the backupState is NetworkResult.Success if-condition
        // because the domain is already in hand and needs to restore
        // INDEPENDENT of the state of _messagesByUserCache.value.
        withContext(Dispatchers.IO) {
            try {
                val messageRequestDto = deletedMessage.toMessageRequestDto()
                val response = retryIO(times = 3, onRetry = { attempt ->
                    Log.d("markMessageAsRead", "number of retries is $attempt")
                    // TODO play with passing a UIEvent of Retrying
                }) {
                    apiService.addMessage(messageRequestDto)
                }
                if (response.isSuccessful) {
                    _uiEventChannel.send(MessageUiEvent.showToast("Deleted message restored"))
                } else {
                    // ROLLBACK UNDO: If the server failed to restore, then remove it again
                    _messagesByUserCache.value = backupState
                    _uiEventChannel.send(MessageUiEvent.showToast("Could not restore message"))
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                // ROLLBACK UNDO: If the server failed to restore, then remove it again
                _messagesByUserCache.value = backupState
                _uiEventChannel.send(MessageUiEvent.showToast("Could not restore message"))
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} restore message ended")
                }
            }
        }
    }
}