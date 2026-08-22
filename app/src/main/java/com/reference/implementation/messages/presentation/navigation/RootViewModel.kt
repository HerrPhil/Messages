package com.reference.implementation.messages.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.data.manager.AuthSessionManager
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.UserRoleState
import com.reference.implementation.data.audit.auditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RootViewModel(
    authSessionManager: AuthSessionManager,
    roleManager: RoleManager
) : ViewModel() {
    // Expose the application-layer authentication state directly to the NavHost composition
    val authState = authSessionManager.authState
    val userRoleState = roleManager.roleState

    fun logRoleStateTransition(state: UserRoleState) {
        viewModelScope.launch(Dispatchers.IO) {
            when(state) {
                is UserRoleState.Idle -> {
                    auditLog("Root App Navigation: first visit to Root App Navigation")
                }
                is UserRoleState.Loading -> {
                    auditLog("Root App Navigation: still loading ...")
                }
                is UserRoleState.Unknown -> {
                    auditLog("Root App Navigation: The user role is cleared - logout event")
                }
                else -> {
                    auditLog("No logging needed for authenticated states")
                }
            }
        }
    }
}