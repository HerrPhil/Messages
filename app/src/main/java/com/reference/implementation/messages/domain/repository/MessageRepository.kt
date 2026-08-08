package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getSummaryMessages(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<String>>
    fun getMessagesByUserFlow( onRetry: suspend (Int) -> Unit): Flow<NetworkResult<List<MessageDomainModel>>>
}
