package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.mappers.toBulletinDomainModel
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.repository.BulletinCacheRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class BulletinCacheRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiService: ApiService
) : BulletinCacheRepository {

    // The Local Memory Cache of a list of bulletins (The Single Source of Truth)
    private val _bulletinsCache =
        MutableStateFlow<NetworkResult<List<BulletinDomainModel>>>(NetworkResult.Loading)

    // The Local Memory Cache of a selected bulletin (The Single Source of Truth)
    private val _bulletinCache =
        MutableStateFlow<NetworkResult<BulletinDomainModel>>(NetworkResult.Loading)

    // The Read-Only List Stream: Anyone can listen to this at any time
    override fun getAllBulletins(): Flow<NetworkResult<List<BulletinDomainModel>>> =
        _bulletinsCache.asStateFlow()

    // The Read-Only Object Stream: Anyone can listen to this at any time
    override fun getBulletin(): Flow<NetworkResult<BulletinDomainModel>> =
        _bulletinCache.asStateFlow()

    override suspend fun refreshBulletins(onRetry: suspend (Int) -> Unit) {
        // Force the cache to show "Loading" if it is a manual refresh/retry action
        _bulletinsCache.value = NetworkResult.Loading

        withContext(ioDispatcher) {// work on the IO coroutine
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.getBulletins()
                    if (res.code() >= 500) {
                        throw HttpException(res) // Force retryIO's catch block to trigger!
                    }
                    res
                }
                if (response.isSuccessful && response.body() != null) { // 200 response
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    _bulletinsCache.value = NetworkResult.Success(
                        data = response.body()!!.map { dto -> dto.toBulletinDomainModel() })
                } else { // 4xx errors
                    // Transform unsuccessful Retrofit call (4xx errors)
                    _bulletinsCache.value =
                        NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auditLog(e.message ?: "no message")
                _bulletinsCache.value = NetworkResult.Exception(e) // 5xx errors
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} refresh bulletins ended")
                }
            }
        }
    }

    override suspend fun refreshBulletin(
        bulletinId: Int,
        onRetry: suspend (Int) -> Unit
    ) {
        // Force the cache to show "Loading" if it is a manual refresh/retry action
        _bulletinCache.value = NetworkResult.Loading

        withContext(ioDispatcher) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.getBulletin(bulletinId)
                    if (res.code() >= 500) {
                        throw HttpException(res) // Force retryIO's catch block to trigger!
                    }
                    res
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    _bulletinCache.value = NetworkResult.Success(
                        data = response.body()!!.toBulletinDomainModel()
                    )
                } else {
                    // Transform unsuccessful Retrofit call (4xx errors)
                    _bulletinCache.value =
                        NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auditLog(e.message ?: "no message")
                _bulletinCache.value = NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} refresh bulletin ended")
                }
            }
        }
    }
}