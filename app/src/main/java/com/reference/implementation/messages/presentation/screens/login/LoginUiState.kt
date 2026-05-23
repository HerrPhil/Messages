package com.reference.implementation.messages.presentation.screens.login

import com.reference.implementation.messages.presentation.screens.user.UserUiState

// Just thought of something, do I want to retry logins?
// I do want to retry message-based calls.
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Retrying(val attempt: Int) : LoginUiState()
    data class Success(val userUiState: UserUiState) : LoginUiState()
//    data class Warning(val message: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
