package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.BulletinCacheRepository
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinUiEvent
import com.reference.implementation.messages.presentation.screens.message.MessageUiEvent
import kotlinx.coroutines.flow.Flow

class GetBulletinUiEventsUseCase(private val repo: BulletinCacheRepository) {
    operator fun invoke(): Flow<BulletinUiEvent> {
        return repo.getBulletinUiEvents()
    }
}