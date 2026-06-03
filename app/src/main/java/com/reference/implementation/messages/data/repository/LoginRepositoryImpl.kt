package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.AuthSessionManager
import com.reference.implementation.messages.data.manager.RoleManager
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.TokenManager
import com.reference.implementation.messages.data.manager.UserRoleState
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.LoginRequestDto
import com.reference.implementation.messages.data.remote.RoleDto
import com.reference.implementation.messages.data.remote.toDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class LoginRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager, // an application scope
    private val authSessionManager: AuthSessionManager, // Global state source (Application Layer)
    private val roleManager: RoleManager, // Global state source (Application Layer)
    private val sessionManager: SessionManager
) : LoginRepository {

    override suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<UserDomainModel> {

        roleManager.updateRole(UserRoleState.Loading)

        return withContext(Dispatchers.IO) {
            try {
                val loginRequestDto = LoginRequestDto(email, password)
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.login(loginRequestDto)
                }
                if (response.isSuccessful && response.body() != null) {

                    // The token is a technical detail of the data layer.
                    // It never leaves this layer!
                    // Function is saveToken is a suspend function; inside withContext coroutine scope - OK
                    tokenManager.saveToken(response.body()!!.accessToken)

                    val userDto = response.body()!!.userDto

                    val roleDeferred = async { getRole(userDto.id, onRetry) }
                    val networkResultRole = roleDeferred.await()

                    if (networkResultRole is NetworkResult.Success) {
                        val roleDto = networkResultRole.data
                        sessionManager.updateSession(userDto, roleDto)
                        val userRoleState = getUserRoleState(roleDto)
                        roleManager.updateRole(userRoleState)
                    }

                    // Make a note that the auth session is "Authenticated"!
                    authSessionManager.startSession()

                    // This never triggers re-composition - it only logs the success!
                    NetworkResult.Success(userDto.toDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} login call ended")
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
    private suspend fun getRole(
        userId: Int,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<RoleDto> {
        return withContext(Dispatchers.IO) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.getRole(targetUserId = userId)
                }
                if (response.isSuccessful && response.body() != null) {
                    val roleDto = response.body()!!
                    NetworkResult.Success(roleDto)
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} get role call ended")
                }
            }
        }
    }

}

private fun getUserRoleState(roleDto: RoleDto): UserRoleState =
    when (roleDto.name.lowercase() == "system administrator") {
        true -> {
            UserRoleState.Administrator
        }

        false -> {
            UserRoleState.RegularUser(roleDto.name)
        }
    }
