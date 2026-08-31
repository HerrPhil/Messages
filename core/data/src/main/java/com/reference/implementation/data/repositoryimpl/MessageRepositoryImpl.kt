package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.mappers.toMessageDomainModel
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class MessageRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : MessageRepository {

    override fun getSummaryMessages(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<String>> =
        flow {

            emit(NetworkResult.Loading)

            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getMessages()
                if (res.code() >= 500 ) {
                    throw HttpException(res) // Force retryIO's catch block to trigger!
                }
                res
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                val unreadMessages = body.count { !it.read }
                val allMessages = body.size
                val summaryMessage = "$unreadMessages / $allMessages"

                emit(NetworkResult.Success(summaryMessage))
            } else {
                emit(NetworkResult.Error(response.code(), response.message()))
            }


        }.catch { e ->
            if (e is CancellationException) throw e
            auditLog(e.message ?: "no messages")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get messages ended")
            }
        }.flowOn(ioDispatcher) // Note: Dispatchers.IO is better suited for Network/API calls!

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
                val res = apiService.getMessages(userId)
                if (res.code() >= 500) {
                    throw HttpException(res) // Force retryIO's catch block to trigger!
                }
                res
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(NetworkResult.Success(body.map { it.toMessageDomainModel() }))
            } else {
                emit(NetworkResult.Error(response.code(), response.message()))
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            auditLog(e.message ?: "no messages")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get messages ended")
            }
        }.flowOn(ioDispatcher) // Note: Dispatchers.IO is better suited for Network/API calls!

}