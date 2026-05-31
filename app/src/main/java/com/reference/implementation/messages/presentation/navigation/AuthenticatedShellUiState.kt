package com.reference.implementation.messages.presentation.navigation

import com.reference.implementation.messages.presentation.screens.user.UserUiState

sealed class AuthenticatedShellUiState {

    object Idle : AuthenticatedShellUiState()
    object Loading : AuthenticatedShellUiState()

    data class Success(val userUiState: UserUiState) : AuthenticatedShellUiState()
    data class LogoutComplete(val userUiState: UserUiState) : AuthenticatedShellUiState()
    data class Error(val message: String) : AuthenticatedShellUiState()
}