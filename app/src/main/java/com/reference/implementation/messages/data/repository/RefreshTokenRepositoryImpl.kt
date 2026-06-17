package com.reference.implementation.messages.data.repository

import android.util.Log
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.RefreshManager
import com.reference.implementation.messages.data.manager.TokenManager
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.RefreshTokenRequestDto
import com.reference.implementation.messages.data.remote.toDomainModel
import com.reference.implementation.messages.domain.model.RefreshTokenDomainModel
import com.reference.implementation.messages.domain.repository.RefreshTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RefreshTokenRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager, // an application scope
    private val refreshManager: RefreshManager // an application scope
) : RefreshTokenRepository {
    override suspend fun refreshToken(tokenUsedByRequest: String): NetworkResult<RefreshTokenDomainModel> {

        return withContext(Dispatchers.IO) {
            // The MAGIC CHECK: If the token in storage has changed,
            // then another thread already fixed it!
            // DO NOT call the refresh endpoint again.
            val tokenInStorage = tokenManager.getToken() ?: ""
            if (tokenInStorage != tokenUsedByRequest) {
                NetworkResult.Success(RefreshTokenDomainModel(tokenInStorage))
            }

            try {
                val refreshToken = refreshManager.getRefresh() ?: ""

                val refreshTokenRequestDto = RefreshTokenRequestDto(refreshToken)

                val onRetry: suspend (Int) -> Unit = { attempt ->
                    Log.d("RefreshTokenRepository", "refresh token attempt number $attempt")
                }
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.refreshAccessToken(refreshTokenRequestDto)
                    // FOR TESTING: You can programmatically inject your mock scenario header here
                    // apiService.refreshAccessToken(refreshTokenRequestDto, scenario = "expired-refresh-token")
                }
                if (response.isSuccessful && response.body() != null) {

                    // The token is a technical detail of the data layer.
                    // It never leaves this layer!
                    // Function is saveToken is a suspend function; inside withContext coroutine scope - OK
                    tokenManager.saveToken(response.body()!!.accessToken)

                    val refreshTokenDto = response.body()!!

                    // DTO never leave the data layer - transforms to domain model
                    NetworkResult.Success(refreshTokenDto.toDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance()
                        .writeLog("${auditLogTimestamp()} refresh token call ended")
                }
            }
        }
    }
}