package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.di.AuthNetwork
import com.reference.implementation.data.di.IoDispatcher
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BulletinRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher, // Hilt provides it
    @AuthNetwork private val apiService: ApiService
) : BulletinRepository {
    override fun getBulletinCount(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<Int>> =
        flow {

            emit(NetworkResult.Loading)

            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getBulletins()
                if (res.code() >= 500) {
                    throw HttpException(res) // Force retryIO's catch block to trigger!
                }
                res
            }

            val body = response.body()
            if (response.isSuccessful && body != null) { // 200 response
                emit(NetworkResult.Success(body.size)) // number of bulletins
            } else { // 4xx errors
                emit(NetworkResult.Error(response.code(), response.message()))
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            auditLog(e.message ?: "no messages")
            emit(NetworkResult.Exception(e)) // 5xx errors
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get messages ended")
            }
        }.flowOn(ioDispatcher) // Note: Dispatchers.IO is better suited for Network/API calls!

}