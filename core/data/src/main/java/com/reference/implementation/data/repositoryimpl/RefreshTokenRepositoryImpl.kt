package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.dtos.RefreshTokenRequestDto
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.manager.RefreshTokenManager
import com.reference.implementation.data.mappers.toDomainModel
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.RefreshTokenDomainModel
import com.reference.implementation.domain.repository.RefreshTokenRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class RefreshTokenRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiService: ApiService,
    private val accessTokenManager: AccessTokenManager, // an application scope
    private val refreshTokenManager: RefreshTokenManager
) : RefreshTokenRepository {
    override suspend fun refreshToken(
        tokenUsedByRequest: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<RefreshTokenDomainModel> {

        return withContext(ioDispatcher) {
            // The MAGIC CHECK: If the token in storage has changed,
            // then another thread already fixed it!
            // DO NOT call the refresh endpoint again.
            val tokenInStorage = accessTokenManager.getToken() ?: ""
            if (tokenInStorage.isNotEmpty() && tokenInStorage != tokenUsedByRequest) {
                return@withContext NetworkResult.Success(RefreshTokenDomainModel(tokenInStorage))
            }

            try {
                val refreshToken = refreshTokenManager.getToken() ?: ""

                val refreshTokenRequestDto = RefreshTokenRequestDto(refreshToken)

                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.refreshAccessToken(refreshTokenRequestDto)
                    if (res.code() >= 500) {
                        throw HttpException(res)
                    }
                    res
                }
                val refreshTokenDto = response.body() // expect RefreshTokenDto
                if (response.isSuccessful && refreshTokenDto != null) {

                    // The token is a technical detail of the data layer.
                    // It never leaves this layer!
                    // Function is saveToken is a suspend function; inside withContext coroutine scope - OK
                    accessTokenManager.saveToken(response.body()!!.accessToken)

                    // DTO never leave the data layer - transforms to domain model
                    return@withContext NetworkResult.Success(refreshTokenDto.toDomainModel())
                } else { // 4XX errors
                    // Transform unsuccessful Retrofit calls.
                    return@withContext NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) { // 5xx errors
                if (e is CancellationException) throw e
                auditLog(e.message ?: "no message")
                return@withContext NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} refresh token call ended")
                }
            }
        }
    }

}