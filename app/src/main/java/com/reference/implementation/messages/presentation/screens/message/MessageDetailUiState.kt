package com.reference.implementation.messages.presentation.screens.message

import com.reference.implementation.messages.presentation.screens.bulletin.BulletinUiDetail
import com.reference.implementation.messages.presentation.screens.message.MessageUiDetail

sealed interface MessageDetailUiState {
    object Idle : MessageDetailUiState
    object Loading : MessageDetailUiState
    data class Retrying(val attempt: Int) : MessageDetailUiState
    data class Success(
        val data: MessageUiDetail
    ) : MessageDetailUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : MessageDetailUiState
}