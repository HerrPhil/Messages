package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.flow.Flow

interface BulletinCacheRepository {
    fun getAllBulletins(): Flow<NetworkResult<List<BulletinDomainModel>>>
    suspend fun refreshBulletins(onRetry: suspend (Int) -> Unit)
    fun getBulletin(): Flow<NetworkResult<BulletinDomainModel>>
    suspend fun refreshBulletin(bulletinId: Int, onRetry: suspend (Int) -> Unit)
}