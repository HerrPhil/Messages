package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.repository.MessageCacheRepository
import kotlinx.coroutines.flow.Flow

class GetMessageEventsUseCase(private val repo: MessageCacheRepository) {
    operator fun invoke(): Flow<MessageDomainEvent> {
        return repo.getMessageDomainEvents()
    }
}