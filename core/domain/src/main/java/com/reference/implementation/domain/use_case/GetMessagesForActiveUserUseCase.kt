package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class GetMessagesForActiveUserUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    open operator fun invoke(): Flow<Resource<List<MessageDomainModel>>> {
        // 1. Grab the raw streams from the repositories
        return combine(
            messageRepository.getMessagesForActiveUser(),
            userPreferencesRepository.getImportantMessageIds()
        ) { messageRes, importantIds ->
            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val messageStream = messageRes as CachePayload.ActiveStream

            // THE RESILIENCE FIX: Only crash the UI if the sync task failed
            // AND there is absolutely zero local cache data available to show!
            val isCatastrophicFailure =
                messageStream.syncError != null && messageStream.data.isEmpty()

            if (isCatastrophicFailure) {
                Resource.Error("Unable to load messages")
            } else {

                // Extract the data safely if Success: otherwise keep null

                // No safe-casting required anymore! Read the data property directly.
                val messages = messageStream.data
                    .sortedByDescending { messageDomainModel -> // newest-to-oldest
                        messageDomainModel.createdAtInstant
                    }.map { message ->
                        message.copy(isImportant = message.id.toString() in importantIds)
                    }

                Resource.Success(data = messages)
            }
        }
            .onStart { emit(Resource.Loading) }
            .flowOn(Dispatchers.Default)
    }
}