package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.presentation.screens.message.MessageUiEvent
import kotlinx.coroutines.flow.Flow

class GetMessageUiEventsUseCase(private val repo: MessageCacheRepository) {
    operator fun invoke(): Flow<MessageUiEvent> {
        return repo.getMessageUiEvents()
    }
}