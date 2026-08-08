package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.domain.repository.BulletinRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class BulletinRepositoryImpl(
    private val apiService: ApiService
) : BulletinRepository {
    override fun getBulletinCount(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<Int>> =
        flow {

            emit(NetworkResult.Loading)

            val response = retryIO(times = 3, onRetry = onRetry) {
                apiService.getBulletins()
            }

            delay(9000)

            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(NetworkResult.Success(body.size)) // number of bulletins
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