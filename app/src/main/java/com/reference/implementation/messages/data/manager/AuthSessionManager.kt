package com.reference.implementation.messages.data.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthSessionManager {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun startSession() {
        // Like, when the login call is successful!
        _authState.value = AuthState.Authenticated
    }

    fun stopSession() {
        // Like, when the user clicks logout!
        _authState.value = AuthState.Unauthenticated
    }
}