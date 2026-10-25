package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.UserMessageReadUnreadDomainModel
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getSummaryMessages(): Flow<CachePayload<String>>
    fun getUserReadUnreadCounts(): Flow<CachePayload<UserMessageReadUnreadDomainModel>>
}