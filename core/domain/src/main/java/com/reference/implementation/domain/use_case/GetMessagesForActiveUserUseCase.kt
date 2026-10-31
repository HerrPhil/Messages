package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageCacheRepository
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

            // Check for catastrophic hard error (e.g. if ALL streams failed)
            // Like, server(s) down, internet down, etc.
            val messagesFailed = messageStream.syncError != null

            if (messagesFailed) {
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

//            networkResult.toResource("Message Details") { messages ->
//                // 3. Apply pure domain business logic transformations
//                // (eg) show most recent messages first.
//                messages.sortedByDescending { messageDomainModel ->
//                    messageDomainModel.createdAtInstant
//                }.map { message ->
//                    message.copy(isImportant = message.id.toString() in importantIds)
//                }
//            }
        }
            .onStart { emit(Resource.Loading) }
            .flowOn(Dispatchers.Default)
    }
}