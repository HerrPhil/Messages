package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.flow.Flow

interface MessageCacheRepository {
    val uiEvents: Flow<MessageDomainEvent>
    fun getMessagesByUser(): Flow<NetworkResult<List<MessageDomainModel>>>
    suspend fun refreshMessagesOfActiveUser(onRetry: (Int) -> Unit)
    suspend fun refreshMessagesOfSelectedUser(userId:Int, onRetry: (Int) -> Unit)
    suspend fun markMessageAsRead(messageId: Int, onRetry: (Int) -> Unit)
    suspend fun markMessageAsUnread(messageId: Int, onRetry: (Int) -> Unit)
    suspend fun deleteMessage(messageId: Int, onRetry: (Int) -> Unit)
    suspend fun restoreMessage(deletedMessage: MessageDomainModel, onRetry: (Int) -> Unit)
    fun getMessageDomainEvents(): Flow<MessageDomainEvent>
}
