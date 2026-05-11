package com.reference.implementation.messages.presentation.screens.user

import com.reference.implementation.messages.presentation.screens.login.LoginUiState

sealed class UserUiState {
    object Idle: UserUiState()
    object Loading: UserUiState()
    data class Retrying(val attempt: Int): UserUiState()
    data class Success(
        val userId: Int,
        val email: String,
        val name: String,
        val age: Int
    ): UserUiState()
    data class Information(val message: String): UserUiState()
    data class Warning(val message: String): UserUiState()
    data class Error(val message: String): UserUiState()
}