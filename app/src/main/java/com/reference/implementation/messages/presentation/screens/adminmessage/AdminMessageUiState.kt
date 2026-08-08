package com.reference.implementation.messages.presentation.screens.adminmessage

import com.reference.implementation.messages.domain.model.MessageDomainModel

sealed interface AdminMessageUiState {
    object Idle : AdminMessageUiState
    object Loading : AdminMessageUiState
    data class Success(val messages: List<MessageDomainModel>) : AdminMessageUiState
    data class Error(val message: String) : AdminMessageUiState
}