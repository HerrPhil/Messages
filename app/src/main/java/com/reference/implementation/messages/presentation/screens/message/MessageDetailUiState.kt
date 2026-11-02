package com.reference.implementation.messages.presentation.screens.message

sealed interface MessageDetailUiState {
    object Idle : MessageDetailUiState
    object Loading : MessageDetailUiState
    object MessageDeletedBackNavigate: MessageDetailUiState

    // TODO deprecated - remove after MonitorMessageDetailScreen is refactored.
    data class Retrying(val attempt: Int) : MessageDetailUiState

    data class Success(
        val data: MessageUiDetail
    ) : MessageDetailUiState

    data class Error(val message: String) : MessageDetailUiState
}