package com.reference.implementation.messages.presentation.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.AuthSessionManager
import com.reference.implementation.messages.data.manager.RoleManager
import com.reference.implementation.messages.data.manager.UserRoleState
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
                    Audit.createInstance().writeLog("Root App Navigation: first visit to Root App Navigation")
                }
                is UserRoleState.Loading -> {
                    Audit.createInstance().writeLog("Root App Navigation: still loading ...")
                }
                is UserRoleState.Unknown -> {
                    Audit.createInstance().writeLog("Root App Navigation: The user role is cleared - logout event")
                }
                else -> {
                    Log.d("RootViewModel", "No logging needed for authenticated states")
                }
            }
        }
    }
}
