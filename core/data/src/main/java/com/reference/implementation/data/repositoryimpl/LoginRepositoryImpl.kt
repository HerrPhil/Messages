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
    private val roleManager: RoleManager, // Global state source (Application Layer)
    private val sessionManager: SessionManager
) : LoginRepository {

    override suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<LoginUserDomainModel> {

        roleManager.updateRole(UserRoleState.Loading)

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
                if (response.isSuccessful && response.body() != null) {

                    // The token is a technical detail of the data layer.
                    // It never leaves this layer!
                    // Function is saveToken is a suspend function; inside withContext coroutine scope - OK
                    accessTokenManager.saveToken(response.body()!!.accessToken)
                    refreshTokenManager.saveToken(response.body()!!.refreshToken)

                    val userDto = response.body()!!.userDto

                    val rolesDeferred = async { getRoles(userDto.id, onRetry) }
                    val networkResultRole = rolesDeferred.await()

                    var roles = emptyList<RoleDto>()
                    if (networkResultRole is NetworkResult.Success) {
                        roles = networkResultRole.data
                        val userRoleState = getUserRoleState(roles)
                        roleManager.updateRole(userRoleState)
                    }
                    sessionManager.updateSession(userDto, roles)

                    // Make a note that the auth session is "Authenticated"!
                    authSessionManager.startSession()

                    // This never triggers re-composition - it only logs the success!
                    NetworkResult.Success(userDto.toDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) {
                auditLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} login call ended")
                }
            }
        }
    }

    /**
     * This method is private for two reasons.
     * For now, it only services the login() function to determine whether the session user
     * is an administrator.
     * Since it scoped to this repository, there is no need to return a "RoleDomainModel".
     * In this instance, the result is not bubbling up to a use case.
     */
    private suspend fun getRoles(
        userId: Int,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<List<RoleDto>> {
        return withContext(ioDispatcher) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.getRoles(targetUserId = userId)
                    if (res.code() >= 500) {
                        throw HttpException(res)
                    }
                    res
                }
                if (response.isSuccessful && response.body() != null) {
                    val roles = response.body()!!
                    NetworkResult.Success(roles)
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) {
                auditLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} get role call ended")
                }
            }
        }
    }

}

private fun getUserRoleState(roles: List<RoleDto>): UserRoleState =
    when (roles.any { roleDto ->
        roleDto.name.lowercase() == "system administrator"
    }) {
        true -> {
            UserRoleState.Administrator
        }

        false -> {
            UserRoleState.RegularUser
        }
    }