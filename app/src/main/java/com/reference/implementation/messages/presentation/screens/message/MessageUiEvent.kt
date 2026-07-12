package com.reference.implementation.messages.presentation.screens.message

interface MessageUiEvent {
    data class showToast(val message: String) : MessageUiEvent
    data class showAlertDialog(val message: String) : MessageUiEvent
}