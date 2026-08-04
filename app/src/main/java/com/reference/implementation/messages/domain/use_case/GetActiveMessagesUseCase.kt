package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetActiveMessagesUseCase(
    private val messageCacheRepository: MessageCacheRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    operator fun invoke(): Flow<Resource<List<MessageDomainModel>>> {
        // 1. Grab the raw streams from the repositories
        return combine(
            messageCacheRepository.getMessagesByUser(),
            userPreferencesRepository.getImportantMessageIds()
        ) { networkResult, importantIds ->
            networkResult.toResource("Message Details") { messages ->
                // 3. Apply pure domain business logic transformations
                // (eg) show most recent messages first.
                messages.sortedByDescending { messageDomainModel ->
                    messageDomainModel.createdAtInstant
                }.map { message ->
                    message.copy(isImportant = message.id.toString() in importantIds)
                }
            }
        }
    }
}