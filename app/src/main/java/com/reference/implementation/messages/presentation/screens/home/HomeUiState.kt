package com.reference.implementation.messages.presentation.screens.home

import com.reference.implementation.messages.presentation.screens.user.UserUiState

sealed class HomeUiState {
    object Idle : HomeUiState()
    object Loading : HomeUiState()
    //    data class Retrying(val attempt: Int) : HomeUiState()
    data class Success(val userUiState: UserUiState) : HomeUiState()
    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}