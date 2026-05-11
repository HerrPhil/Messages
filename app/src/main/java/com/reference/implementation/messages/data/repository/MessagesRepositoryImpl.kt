package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.toMessageDomainModel
import com.reference.implementation.messages.data.remote.toMessageDto
import com.reference.implementation.messages.data.remote.toMessageRequestDto
import com.reference.implementation.messages.data.remote.toPartialMessageRequestDto
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class MessagesRepositoryImpl(private val apiService: ApiService) : MessageRepository {

    override suspend fun getMessages(
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<List<MessageDomainModel>> {
        // force the execution onto the IO thread pool
        return withContext(Dispatchers.IO) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getMessages()
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer!
                    NetworkResult.Success(response.body()!!.map { it.toMessageDomainModel() })
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} get messages ended")
                }
            }
        }
    }

    override suspend fun getMessage(
        id: Int,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel> {
        return withContext(Dispatchers.IO) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getMessage(id)
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer!
                    NetworkResult.Success(response.body()!!.toMessageDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} get message ended")
                }
            }
        }
    }

    override suspend fun addMessage(
        message: MessageDomainModel,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel> {
        return withContext(Dispatchers.IO) {
            try {
                val messageRequestDto = message.toMessageRequestDto()
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.addMessage(messageRequestDto)
                }
                if (response.isSuccessful) {
                    // DTO never leaves this layer!
                    NetworkResult.Success(response.body()!!.toMessageDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} add message ended")
                }
            }
        }
    }

    override suspend fun updateMessage(
        message: MessageDomainModel,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel> {
        return withContext(Dispatchers.IO) {
            try {
                val id = message.id
                val messageDto = message.toMessageDto()
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.updateMessage(id, messageDto)
                }
                if (response.isSuccessful) {
                    // DTO never leaves this layer!
                    NetworkResult.Success(response.body()!!.toMessageDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} update message ended")
                }
            }
        }
    }

    override suspend fun partialUpdateMessage(
        message: MessageDomainModel,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<MessageDomainModel> {
        return withContext(Dispatchers.IO) {
            try {
                val id = message.id
                val partialMessageRequestDto = message.toPartialMessageRequestDto()
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.partialUpdateMessage(id, partialMessageRequestDto)
                }
                if (response.isSuccessful) {
                    // DTO never leaves this layer!
                    NetworkResult.Success(response.body()!!.toMessageDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} partial update message ended")
                }
            }
        }
    }

    override suspend fun removeMessage(
        id: Int,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.removeMessage(id)
                }
                if (response.isSuccessful) {
                    // in a coroutine-based API service call the result is Response<Unit> for delete
                    NetworkResult.Success(true)
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} remove message ended")
                }
            }
        }
    }

}