package com.reference.implementation.messages.presentation.screens.message

sealed interface MessageUiState {
    object Idle : MessageUiState
    object Loading : MessageUiState
    data class Retrying(val attempt: Int) : MessageUiState
    data class Success(
        val list: List<MessageUiDetail>
    ) : MessageUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : MessageUiState
}