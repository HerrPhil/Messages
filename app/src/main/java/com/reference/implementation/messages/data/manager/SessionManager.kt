package com.reference.implementation.messages.data.manager

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.remote.RoleDto
import com.reference.implementation.messages.data.remote.UserDto
import com.reference.implementation.messages.data.repository.NetworkSessionState
import kotlinx.coroutines.flow.MutableStateFlow

class SessionManager(
    private val tokenManager: TokenManager,
    private val refreshManager: RefreshManager
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

//    fun getSessionRoleTargetUserId(): SessionResult<Int> {
//        return when (val currentSessionState = _sessionFlow.value) {
//            is NetworkSessionState.NoSession -> {
//                SessionResult.NoValue
//            }
//
//            is NetworkSessionState.ActiveSession -> {
//                SessionResult.Authenticated(currentSessionState.role.id)
//            }
//        }
//    }
//
//    fun getSessionRolePermissionId(): SessionResult<Int> {
//        return when (val currentSessionState = _sessionFlow.value) {
//            is NetworkSessionState.NoSession -> {
//                SessionResult.NoValue
//            }
//
//            is NetworkSessionState.ActiveSession -> {
//                SessionResult.Authenticated(currentSessionState.role.permissionId)
//            }
//        }
//    }

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

        // 1. Delegate to token manager to manage keystore and preferences.
        tokenManager.logout()

        // 2. Delegate to refresh manager to manage keystore and preferences.
        refreshManager.logout()

        // 3. Remove the session user information - no one is logged in
        _sessionFlow.value = NetworkSessionState.NoSession

        Audit.createInstance().writeLog("$sessionUserName is logged out!")
    }
}