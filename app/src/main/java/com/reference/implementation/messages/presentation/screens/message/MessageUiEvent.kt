package com.reference.implementation.messages.presentation.screens.message

import com.reference.implementation.messages.domain.model.MessageDomainModel

interface MessageUiEvent {
    data class showToast(val message: String) : MessageUiEvent
    data class showAlertDialog(val message: String) : MessageUiEvent
    data class showDeleteSnackbar(val deletedMessage: MessageDomainModel) : MessageUiEvent
}