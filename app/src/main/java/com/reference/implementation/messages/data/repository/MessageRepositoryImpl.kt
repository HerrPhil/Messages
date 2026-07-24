package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.toMessageDomainModel
import com.reference.implementation.messages.data.remote.toMessageDto
import com.reference.implementation.messages.data.remote.toMessageRequestDto
import com.reference.implementation.messages.data.remote.toPartialMessageRequestDto
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class MessageRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : MessageRepository {

    /**
     * This is Phase 1 code of my re-factor of GetUserDashboardUseCase.
     * Phase 2 moves to leveraging the getMessagesByUser() function of MessageCacheRepository.
     */
    override fun getMessagesByUserFlow(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<List<MessageDomainModel>>> =
        flow {
            emit(NetworkResult.Loading)

            val userId = when (val userIdResult = sessionManager.getSessionUserId()) {
                is SessionResult.Authenticated -> userIdResult.data
                else -> 0
            }

            val response = retryIO(times = 3, onRetry = onRetry) {
                apiService.getMessages(userId)
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(NetworkResult.Success(body.map { it.toMessageDomainModel() }))
            } else {
                emit(NetworkResult.Error(response.code(), response.message()))
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            Audit.createInstance().writeLog(e.message ?: "no messages")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                Audit.createInstance().writeLog("${auditLogTimestamp()} get messages ended")
            }
        }.flowOn(Dispatchers.IO) // Note: Dispatchers.IO is better suited for Network/API calls!

}