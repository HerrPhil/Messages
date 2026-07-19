package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.toBulletinDomainModel
import com.reference.implementation.messages.domain.model.BulletinDomainModel
import com.reference.implementation.messages.domain.repository.BulletinCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class BulletinCacheRepositoryImpl(
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

        withContext(Dispatchers.IO) {// work on the IO coroutine
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getBulletins()
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    _bulletinsCache.value = NetworkResult.Success(
                        data = response.body()!!.map { it.toBulletinDomainModel() })
                } else {
                    // Transform unsuccessful Retrofit call
                    _bulletinsCache.value =
                        NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                _bulletinsCache.value = NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} refresh bulletins ended")
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

        withContext(Dispatchers.IO) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getBulletin(bulletinId)
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer - see the DTO extension function!
                    // Update the SSOT cache with fresh data!
                    _bulletinCache.value = NetworkResult.Success(
                        data = response.body()!!.toBulletinDomainModel()
                    )
                } else {
                    // Transform unsuccessful Retrofit call
                    _bulletinCache.value =
                        NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Exception) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                _bulletinCache.value = NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} refresh bulletin ended")
                }
            }
        }
    }
}