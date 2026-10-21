package com.reference.implementation.domain.repository

import com.reference.implementation.domain.model.CachePayload
import kotlinx.coroutines.flow.Flow

interface BulletinRepository {
    fun getBulletinCount(): Flow<CachePayload<Int>>
}