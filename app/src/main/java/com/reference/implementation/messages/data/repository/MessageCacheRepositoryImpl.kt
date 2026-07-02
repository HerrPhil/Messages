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
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MessageCacheRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) : MessageCacheRepository {

    // 1. The Local Memory Cache (The Single Source of Truth)
    private val _messagesByUserCache =
        MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(NetworkResult.Loading)

    // 2(a). The Read-Only Stream: Anyone can listen to this at any time
    override fun getMessagesByUser(): Flow<NetworkResult<List<MessageDomainModel>>> =
        _messagesByUserCache.asStateFlow()

    // 2(b). The Other Read-Only Stream (flavour): Anyone can listen to this at any time too
    val messagesByUserCache: StateFlow<NetworkResult<List<MessageDomainModel>>> =
        _messagesByUserCache.asStateFlow()

    override suspend fun refreshMessagesByUser(onRetry: suspend (Int) -> Unit) {

        // Force the cache to show "Loading" if it a manual retry/refresh action
        _messagesByUserCache.value = NetworkResult.Loading

        // force the execution onto the IO thread pool
        withContext(Dispatchers.IO) {
            try {
                val userId = when (val userIdResult = sessionManager.getSessionUserId()) {
                    is SessionResult.Authenticated -> userIdResult.data
                    else -> 0
                }
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getMessages(userId)
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    _messagesByUserCache.value =
                        NetworkResult.Success(response.body()!!.map { it.toMessageDomainModel() })
                } else {
                    // Transform unsuccessful Retrofit calls.
                    // Update the SSOT cache with the network result error!
                    _messagesByUserCache.value =
                        NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                // Update the SSOT cache with the network result exception!
                _messagesByUserCache.value =
                    NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} get messages ended")

                }
            }
        }
    }
}