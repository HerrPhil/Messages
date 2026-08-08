package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import kotlinx.coroutines.flow.Flow

interface BulletinRepository {
    fun getBulletinCount(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<Int>>
}