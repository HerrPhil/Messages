package com.reference.implementation.messages.data.manager

sealed interface AuthState {
    object Authenticated : AuthState
    object Unauthenticated : AuthState
}