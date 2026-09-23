package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.repository.MessageCacheRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetMessageEventsUseCase @Inject constructor(
    private val repo: MessageCacheRepository
) {
    operator fun invoke(): Flow<MessageDomainEvent> {
        return repo.getMessageDomainEvents()
    }
}