package com.reference.implementation.messages.presentation.screens.home

sealed interface HomeUiState {
    object Idle : HomeUiState
    object Loading : HomeUiState
    data class Retrying(val attempt: Int) : HomeUiState
    data class Success(
        val userName: String,
        val userEmail: String,
        val unreadMessages: Int,
        val readMessages: Int,
        val roles: List<String>,
        val permissions: List<String>
    ) : HomeUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState
}