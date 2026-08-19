package com.reference.implementation.domain.repository

import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.MessageDomainModel
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getSummaryMessages(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<String>>
    fun getMessagesByUserFlow( onRetry: suspend (Int) -> Unit): Flow<NetworkResult<List<MessageDomainModel>>>
}