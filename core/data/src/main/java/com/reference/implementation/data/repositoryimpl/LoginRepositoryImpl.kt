package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.dtos.LoginRequestDto
import com.reference.implementation.data.dtos.RoleDto
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.manager.AuthSessionManager
import com.reference.implementation.data.manager.RefreshTokenManager
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.UserRoleState
import com.reference.implementation.data.mappers.toDomainModel
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.repository.LoginRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class LoginRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiService: ApiService,
    private val accessTokenManager: AccessTokenManager, // an application scope
    private val refreshTokenManager: RefreshTokenManager, // an application scope
    private val authSessionManager: AuthSessionManager, // Global state source (Application Layer)
) : LoginRepository {

    override suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<LoginUserDomainModel> {

        return withContext(ioDispatcher) {
            try {
                val loginRequestDto = LoginRequestDto(email, password)
                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.login(loginRequestDto)
                    if (res.code() >= 500) {
                        throw HttpException(res)
                    }
                    res
                }
                val body = response.body()
                if (response.isSuccessful && body != null) {

                    // The token is a technical detail of the data layer.
                    // It never leaves this layer!
                    // Function is saveToken is a suspend function; inside withContext coroutine scope - OK
                    accessTokenManager.saveToken(body.accessToken)
                    refreshTokenManager.saveToken(body.refreshToken)

                    // Make a note that the auth session is "Authenticated"!
                    authSessionManager.startSession()

                    val userDto = body.userDto
                    // This never triggers re-composition - it only logs the success!
                    NetworkResult.Success(userDto.toDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                auditLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} login call ended")
                }
            }
        }
    }
}