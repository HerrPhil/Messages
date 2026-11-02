package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

open class GetMessageByIdUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userPreferencesRepository: UserPreferencesRepository

) {
    open operator fun invoke(messageId: Int): Flow<Resource<MessageDomainModel?>> {
        // 1. Grab the raw streams from the repositories
        return combine(
            messageRepository.getMessageByIdFlow(messageId),
            userPreferencesRepository.getImportantMessageIds()
        ) { messageRes, importantIds ->
            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val messageStream = messageRes as CachePayload.ActiveStream

            // THE RESILIENCE RULE: Only fail completely if the local cache is empty!
            val isCatastrophicFailure =
                messageStream.syncError != null && messageStream.data == null

            if (isCatastrophicFailure) {
                Resource.Error("Unable to load message details.")
            } else {

                // Extract the data safely if Success: otherwise keep null

                // No safe-casting required anymore! Read the data property directly.
                val message = messageStream.data
                val importantMessage =
                    message?.copy(isImportant = message.id.toString() in importantIds)

                Resource.Success(data = importantMessage)
            }
        }
    }
}