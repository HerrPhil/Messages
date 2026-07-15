package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.BulletinDomainModel
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinUiEvent
import kotlinx.coroutines.flow.Flow

interface BulletinCacheRepository {
    val uiEvents: Flow<BulletinUiEvent>
    fun getAllBulletins(): Flow<NetworkResult<List<BulletinDomainModel>>>
    fun getBulletinUiEvents(): Flow<BulletinUiEvent>
    suspend fun refreshBulletins(onRetry: suspend (Int) -> Unit)
}