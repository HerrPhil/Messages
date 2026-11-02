package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.AdminMessageUnreadTotalDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.UserMessageReadUnreadDomainModel
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getAdminUnreadTotalCounts(): Flow<CachePayload<AdminMessageUnreadTotalDomainModel>>
    fun getUserReadUnreadCounts(): Flow<CachePayload<UserMessageReadUnreadDomainModel>>
    fun getMessagesForActiveUser(): Flow<CachePayload<List<MessageDomainModel>>>
    fun getMessagesForSelectedUser(userId: Int): Flow<CachePayload<List<MessageDomainModel>>>
    fun getMessageByIdFlow(messageId: Int): Flow<CachePayload<MessageDomainModel?>>
    suspend fun markMessageAsRead(messageId: Int)
    suspend fun markMessageAsUnread(messageId: Int)
    suspend fun deleteMessage(messageId: Int)
    suspend fun restoreMessage(deletedMessage: MessageDomainModel)
    fun getMessageDomainEvents(): Flow<MessageDomainEvent>
    suspend fun syncMessages()
}