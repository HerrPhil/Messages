package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.flow.Flow

interface MessageCacheRepository {
    val uiEvents: Flow<MessageDomainEvent>
    fun getMessagesByUser(): Flow<NetworkResult<List<MessageDomainModel>>>
    suspend fun refreshMessagesOfActiveUser(onRetry: suspend (Int) -> Unit)
    suspend fun refreshMessagesOfSelectedUser(userId:Int, onRetry: suspend (Int) -> Unit)
    suspend fun markMessageAsRead(messageId: Int, onRetry: suspend (Int) -> Unit)
    suspend fun markMessageAsUnread(messageId: Int, onRetry: suspend (Int) -> Unit)
    suspend fun deleteMessage(messageId: Int, onRetry: suspend (Int) -> Unit)
    suspend fun restoreMessage(deletedMessage: MessageDomainModel, onRetry: suspend (Int) -> Unit)
    fun getMessageDomainEvents(): Flow<MessageDomainEvent>
}
