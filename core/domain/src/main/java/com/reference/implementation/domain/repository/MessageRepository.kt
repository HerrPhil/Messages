package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.MessageDomainModel
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getSummaryMessages(): Flow<CachePayload<String>>
    fun getMessagesByUserFlow( onRetry: (Int) -> Unit): Flow<NetworkResult<List<MessageDomainModel>>>
}