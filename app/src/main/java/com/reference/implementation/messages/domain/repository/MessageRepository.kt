package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel

interface MessageRepository {
    suspend fun getMessages(onRetry: suspend (Int) -> Unit): NetworkResult<List<MessageDomainModel>>
    suspend fun getMessage(
        id: Int,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel>

    suspend fun addMessage(
        message: MessageDomainModel,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel>

    suspend fun updateMessage(
        message: MessageDomainModel,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel>

    suspend fun partialUpdateMessage(
        message: MessageDomainModel,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel>

    suspend fun removeMessage(id: Int, onRetry: suspend (Int) -> Unit): NetworkResult<Boolean>
}
