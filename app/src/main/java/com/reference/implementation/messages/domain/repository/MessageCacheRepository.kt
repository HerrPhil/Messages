package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.MessageDomainModel
import kotlinx.coroutines.flow.Flow

interface MessageCacheRepository {
    fun getMessagesByUser(

    ): Flow<NetworkResult<List<MessageDomainModel>>>
    suspend fun refreshMessagesByUser(onRetry: suspend (Int) -> Unit)
}
