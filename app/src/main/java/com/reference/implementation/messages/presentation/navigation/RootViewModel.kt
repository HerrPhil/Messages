package com.reference.implementation.messages.presentation.navigation

import androidx.lifecycle.ViewModel
import com.reference.implementation.messages.data.manager.AuthSessionManager

class RootViewModel(authSessionManager: AuthSessionManager): ViewModel() {
    // Expose the application-layer authentication state directly to the NavHost composition
    val authState = authSessionManager.authState
}
