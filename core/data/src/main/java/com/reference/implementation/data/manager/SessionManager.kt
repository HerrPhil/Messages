package com.reference.implementation.data.manager

import com.reference.implementation.data.dtos.RoleDto
import com.reference.implementation.data.dtos.UserDto
import com.reference.implementation.data.audit.auditLog
import kotlinx.coroutines.flow.MutableStateFlow

class SessionManager(
    private val accessTokenManager: AccessTokenManager,
    private val refreshTokenManager: RefreshTokenManager
) {

    // 1. Define a private MutableStateFlow to hold the current state of "session user"
    //    returned by login.
    private val _sessionFlow =
        MutableStateFlow<NetworkSessionState>(NetworkSessionState.NoSession)

    fun getSessionUserName(): SessionResult<String> {
        return when (val currentSessionState = _sessionFlow.value) {
            is NetworkSessionState.NoSession -> {
                SessionResult.NoValue
            }

            is NetworkSessionState.ActiveSession -> {
                SessionResult.Authenticated(currentSessionState.user.name)
            }
        }
    }

    fun getSessionUserEmail(): SessionResult<String> {
        return when (val currentSessionState = _sessionFlow.value) {
            is NetworkSessionState.NoSession -> {
                SessionResult.NoValue
            }

            is NetworkSessionState.ActiveSession -> {
                SessionResult.Authenticated(currentSessionState.user.email)
            }
        }
    }

    fun getSessionUserId(): SessionResult<Int> {
        return when (val currentSessionState = _sessionFlow.value) {
            is NetworkSessionState.NoSession -> {
                SessionResult.NoValue
            }

            is NetworkSessionState.ActiveSession -> {
                SessionResult.Authenticated(currentSessionState.user.id)
            }
        }
    }

    fun getSessionRoleNames(): SessionResult<List<String>> {
        return when (val currentSessionState = _sessionFlow.value) {
            is NetworkSessionState.NoSession -> {
                SessionResult.NoValue
            }

            is NetworkSessionState.ActiveSession -> {
                val names = currentSessionState.roles.map { it.name }
                SessionResult.Authenticated(names)
            }
        }
    }

    fun getSessionPermissionIds(): SessionResult<List<Int>> {
        return when (val currentSessionState = _sessionFlow.value) {
            is NetworkSessionState.NoSession -> {
                SessionResult.NoValue
            }

            is NetworkSessionState.ActiveSession -> {
                val permissionIds = currentSessionState.roles.map { it.permissions }
                val flattenedPermissionIds = permissionIds.flatten()
                val distinctPermissionIds = flattenedPermissionIds.distinct()
                SessionResult.Authenticated(distinctPermissionIds)
            }
        }
    }

    /**
     * The session user DTO
     * and session role DTO
     * get stored at the application level
     * for other screens/use cases/repositories
     * that want to look up
     * data based on
     * the session user ID,
     * the session role (name),
     * the session role target user ID, and
     * the session role permission ID.
     */
    fun updateSession(newUserDto: UserDto, newRoles: List<RoleDto>) {
        _sessionFlow.value = NetworkSessionState.ActiveSession(newUserDto, newRoles)
    }

    suspend fun logout() {

        val sessionUserName = getSessionUserName()

        // 1. Delegate to access token manager to manage keystore and preferences.
        accessTokenManager.logout()

        // 2. Delegate to refresh token manager to manage keystore and preferences.
        refreshTokenManager.logout()

        // 3. Remove the session user information - no one is logged in
        _sessionFlow.value = NetworkSessionState.NoSession

        auditLog("$sessionUserName is logged out!")
    }
}