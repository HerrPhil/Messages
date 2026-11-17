package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.flow.Flow

@Deprecated("Obsolete with migration to Room cache")
interface BulletinCacheRepository {
    fun getAllBulletins(): Flow<NetworkResult<List<BulletinDomainModel>>>
    suspend fun refreshBulletins(onRetry: (Int) -> Unit)
    fun getBulletin(): Flow<NetworkResult<BulletinDomainModel>>
    suspend fun refreshBulletin(bulletinId: Int, onRetry: (Int) -> Unit)
}