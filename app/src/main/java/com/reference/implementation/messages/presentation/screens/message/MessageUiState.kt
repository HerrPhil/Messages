package com.reference.implementation.messages.presentation.screens.message

sealed interface MessageUiState {
    object Idle : MessageUiState
    object Loading : MessageUiState
    data class Retrying(val attempt: String) : MessageUiState
    data class Success( //  TODO figure out what successful data is for the UI
        val placeholder: String // TODO Data classes require values - this is a placeholder
    ) : MessageUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : MessageUiState
}