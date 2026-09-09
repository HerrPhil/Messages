package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.dtos.RoleDto
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.manager.UserRoleState
import com.reference.implementation.data.mappers.toUserDto
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.model.UserRoleDomainModel
import com.reference.implementation.domain.repository.RoleRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class RoleRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiService: ApiService,
    private val roleManager: RoleManager, // Global state source (Application Layer)
    private val sessionManager: SessionManager
) : RoleRepository {

    override fun getRoleInfoFlow(): Flow<NetworkResult<UserRoleDomainModel>> =
        flow {
            emit(NetworkResult.Loading)

            val userRoleNames = when (val res = sessionManager.getSessionRoleNames()) {
                is SessionResult.Authenticated -> res.data
                else -> emptyList()
            }

            val userRoleDomainModel = UserRoleDomainModel(userRoleNames)

            emit(NetworkResult.Success(userRoleDomainModel))
        }.catch { e ->
            if (e is CancellationException) throw e
            auditLog(e.message ?: "no role info")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get role info ended")
            }
        }.flowOn(ioDispatcher)

    override suspend fun configureUserProfile(
        loginUser: LoginUserDomainModel,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<Unit> = withContext(ioDispatcher) {

        roleManager.updateRole(UserRoleState.Loading) // from Idle to Loading

        try {
            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getRoles(targetUserId = loginUser.id)
                if (res.code() >= 500) {
                    throw HttpException(res)
                }
                res
            }
            val roles = response.body() // List<RoleDto>
            if (response.isSuccessful && roles != null) {
                loadRoleManager(roles)
                loadSessionManager(loginUser, roles)
                NetworkResult.Success(data = Unit)
            } else { // 4xx errors
                NetworkResult.Error(response.code(), response.message())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) { // 5xx errors
            auditLog(e.message ?: "no message")
            NetworkResult.Exception(e)
        } finally {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get role call ended")
            }
        }
    }

    private fun loadRoleManager(
        roles: List<RoleDto>
    ) {
        val userRoleState = getUserRoleState(roles)
        roleManager.updateRole(userRoleState)
    }

    private fun loadSessionManager(
        loginUser: LoginUserDomainModel,
        roles: List<RoleDto>
    ) {
        sessionManager.updateSession(loginUser.toUserDto(), roles)
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
}