package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.AdminDashboardDomainModel
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart

class GetAdminDashboardUseCase(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val bulletinRepository: BulletinRepository
) {
    operator fun invoke(
        onRetry: suspend (Int) -> Unit = {}
    ): Flow<Resource<AdminDashboardDomainModel>> {
        return combine(
            userRepository.getUserCount(onRetry),
            messageRepository.getSummaryMessages(onRetry),
            bulletinRepository.getBulletinCount(onRetry)
        ) { userRes,  messageRes, bulletinRes ->

            // Check for catastrophic hard error (e.g. if ALL streams failed)
            // Like, server(s) down, internet down, etc.
            val allFailed = listOf(
                userRes, messageRes, bulletinRes
            ).all { it is NetworkResult.Error }

            if (allFailed) {
                Resource.Error("Unable to load dashboard data.")
            } else {

                // Extract the data safely if Success: otherwise keep null
                val users = (userRes as? NetworkResult.Success)?.data
                val messages = (messageRes as? NetworkResult.Success)?.data
                val bulletins = (bulletinRes as? NetworkResult.Success)?.data

                Resource.Success(
                    AdminDashboardDomainModel(
                        usersCount = users,
                        summaryMessages = messages,
                        bulletinsCount = bulletins
                    )
                )
            }
        }
            .onStart { emit(Resource.Loading) }
            .flowOn(Dispatchers.Default)
    }
}