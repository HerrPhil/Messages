package com.reference.implementation.domain.repository

import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.flow.Flow

interface BulletinRepository {
    fun getBulletinCount(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<Int>>
}