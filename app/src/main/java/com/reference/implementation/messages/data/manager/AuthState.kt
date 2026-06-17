package com.reference.implementation.messages.data.manager

enum class UnauthReason {
    MANUAL, // User voluntarily clicked logout
    FORCE_LOGOUT // 403 Forbidden / Expired Refresh Token
}
sealed interface AuthState {
    object Authenticated : AuthState
    data class Unauthenticated(val reason: UnauthReason) : AuthState
}