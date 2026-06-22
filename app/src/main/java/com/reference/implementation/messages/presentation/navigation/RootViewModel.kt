package com.reference.implementation.messages.presentation.navigation

import androidx.lifecycle.ViewModel
import com.reference.implementation.messages.data.manager.AuthSessionManager
import com.reference.implementation.messages.data.manager.RoleManager

class RootViewModel(
    authSessionManager: AuthSessionManager,
    roleManager: RoleManager
) : ViewModel() {
    // Expose the application-layer authentication state directly to the NavHost composition
    val authState = authSessionManager.authState
    val userRoleState = roleManager.roleState
}
