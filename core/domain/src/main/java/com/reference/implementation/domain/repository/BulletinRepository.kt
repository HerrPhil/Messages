package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.model.CachePayload
import kotlinx.coroutines.flow.Flow

interface BulletinRepository {
    fun getBulletinCount(): Flow<CachePayload<Int>>
    fun getBulletins(): Flow<CachePayload<List<BulletinDomainModel>>>
    fun getBulletinByIdFlow(bulletinId: Int): Flow<CachePayload<BulletinDomainModel?>>
    suspend fun syncBulletins()
}