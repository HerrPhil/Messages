package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.BulletinDomainModel
import kotlinx.coroutines.flow.Flow

interface BulletinCacheRepository {
    fun getAllBulletins(): Flow<NetworkResult<List<BulletinDomainModel>>>
    suspend fun refreshBulletins(onRetry: suspend (Int) -> Unit)
    fun getBulletin(): Flow<NetworkResult<BulletinDomainModel>>
    suspend fun refreshBulletin(bulletinId: Int, onRetry: suspend (Int) -> Unit)
}