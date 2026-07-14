package com.reference.implementation.messages.domain.repository

import android.os.Message
import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.presentation.screens.message.MessageUiEvent
import kotlinx.coroutines.flow.Flow

interface MessageCacheRepository {
    val uiEvents: Flow<MessageUiEvent>
    fun getMessagesByUser(): Flow<NetworkResult<List<MessageDomainModel>>>
    suspend fun refreshMessagesByUser(onRetry: suspend (Int) -> Unit)
    suspend fun markMessageAsRead(messageId: Int)
    suspend fun markMessageAsUnread(messageId: Int)
    suspend fun toggleReadStatus(messageId: Int)
    suspend fun deleteMessage(messageId: Int)
    suspend fun restoreMessage(deletedMessage: MessageDomainModel)
    fun getMessageUiEvents(): Flow<MessageUiEvent>
}
