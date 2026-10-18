package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.AdminDashboardDomainModel
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAdminDashboardUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val bulletinRepository: BulletinRepository
) {
    operator fun invoke(
        onRetry: (Int) -> Unit = {}
    ): Flow<Resource<AdminDashboardDomainModel>> {
        return combine(
            userRepository.getUserCount(onRetry),
            messageRepository.getSummaryMessages(onRetry),
            bulletinRepository.getBulletinCount(onRetry)
        ) { userRes, messageRes, bulletinRes ->

            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val userStream = userRes as CachePayload.ActiveStream
            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val messageStream = messageRes as CachePayload.ActiveStream
            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val bulletinStream = bulletinRes as CachePayload.ActiveStream

            // Check for catastrophic hard error (e.g. if ALL streams failed)
            // Like, server(s) down, internet down, etc.
            val allFailed =
                messageStream.syncError != null
                        && userStream.syncError != null
                        && bulletinStream.syncError != null

            if (allFailed) {
                Resource.Error("Unable to load dashboard data.")
            } else {

                // Extract the data safely if Success: otherwise keep null

                // No safe-casting required anymore! Read the data property directly.
                val usersCount = userStream.data

                val summaryMessages = messageStream.data

                // No safe-casting required anymore! Read the data property directly.
                val bulletinsCount = bulletinStream.data

                Resource.Success(
                    AdminDashboardDomainModel(
                        usersCount = usersCount,
                        summaryMessages = summaryMessages,
                        bulletinsCount = bulletinsCount
                    )
                )
            }
        }
            .onStart { emit(Resource.Loading) }
            .flowOn(Dispatchers.Default)
    }
}