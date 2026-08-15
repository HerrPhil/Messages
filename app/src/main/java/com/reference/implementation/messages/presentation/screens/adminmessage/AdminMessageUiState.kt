package com.reference.implementation.messages.presentation.screens.adminmessage

import com.reference.implementation.messages.presentation.screens.message.MessageUiDetail

sealed interface AdminMessageUiState {
    object Idle : AdminMessageUiState
    object Loading : AdminMessageUiState
    data class Retrying(val attempt: Int) : AdminMessageUiState
    data class Success(
        val list: List<MessageUiDetail>,
        val userOptions: List<UserUiDetail>?,
        val isRefreshing: Boolean,
        val isImportantOnly: Boolean,
        val isAdminSelected: Boolean
    ) : AdminMessageUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : AdminMessageUiState
}