package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.AdminMessageUnreadTotalDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.UserMessageReadUnreadDomainModel
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getAdminUnreadTotalCounts(): Flow<CachePayload<AdminMessageUnreadTotalDomainModel>>
//    fun getSummaryMessages(): Flow<CachePayload<String>>
    fun getUserReadUnreadCounts(): Flow<CachePayload<UserMessageReadUnreadDomainModel>>
}