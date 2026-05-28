package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.remote.RoleDto
import com.reference.implementation.messages.data.remote.UserDto
import com.reference.implementation.messages.data.remote.toDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel
import kotlinx.coroutines.flow.MutableStateFlow

class SessionRepositoryImpl(private val tokenManager: TokenManager) : SessionRepository {

    // 1. Define a private MutableStateFlow to hold the current state of "session user"
    //    returned by login.
    private val _sessionUserFlow =
        MutableStateFlow<NetworkSessionState>(NetworkSessionState.NoSession)

    private val _sessionUserRoleFlow =
        MutableStateFlow<RoleSessionState>(RoleSessionState.NoRole)

    // 2. Expose the immutable Flow to consumer (eg. Home page ViewModel/UI)
    // 2.a. After more consideration, this public StateFlow is unnecessary.
    //      Unlike business repositories that server view models,
    //      this repo only serves other repositories in the data layer.
//    val sessionUserFlow: StateFlow<NetworkSessionState> = _sessionUserFlow

    override suspend fun isLoggedIn(): Boolean {
        return tokenManager.getToken() != null
    }

    override fun getSessionUser(): NetworkResult<UserDomainModel> {
        return when (val currentSessionState = _sessionUserFlow.value) {
            is NetworkSessionState.NoSession -> {
                NetworkResult.Error(0, "no session user")
            }

            is NetworkSessionState.ActiveSession -> {
                NetworkResult.Success(currentSessionState.data.toDomainModel())
            }
        }
    }

    override fun updateSessionUser(newSessionUserDto: UserDto) {
        _sessionUserFlow.value = NetworkSessionState.ActiveSession(newSessionUserDto)
    }

    override fun updateUserRole(newUserRoleDto: RoleDto) {
        _sessionUserRoleFlow.value = RoleSessionState.UserRole(newUserRoleDto)
    }

    override suspend fun isAdministrator(): Boolean {
        return when (val currentRoleState = _sessionUserRoleFlow.value) {
            is RoleSessionState.UserRole -> {
                currentRoleState.data.role.lowercase() == "system administrator"
            }

            else -> false
        }
    }

    override suspend fun logout(): NetworkResult<UserDomainModel> {

        val loggedOutUser = getSessionUser()

        // 1. Delegate to token manager to manage keystore and preferences.
        tokenManager.logout()

        // 2. Remove the session user information - no one is logged in
        _sessionUserFlow.value = NetworkSessionState.NoSession

        return loggedOutUser
    }
}