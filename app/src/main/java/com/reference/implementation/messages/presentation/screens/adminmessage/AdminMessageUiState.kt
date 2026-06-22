package com.reference.implementation.messages.presentation.screens.adminmessage

sealed interface AdminMessageUiState {
    object Idle : AdminMessageUiState
    object Loading : AdminMessageUiState
    data class Retrying(val attempt: String) : AdminMessageUiState
    data class Success( //  TODO figure out what successful data is for the UI
        val placeholder: String // TODO Data classes require values - this is a placeholder
    ) : AdminMessageUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : AdminMessageUiState
}