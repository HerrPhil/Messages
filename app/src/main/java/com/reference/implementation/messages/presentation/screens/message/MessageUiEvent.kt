package com.reference.implementation.messages.presentation.screens.message

import com.reference.implementation.domain.model.MessageDomainEvent
import com.reference.implementation.domain.model.MessageDomainModel

interface MessageUiEvent {
    data class showToast(val message: String) : MessageUiEvent
    data class showDeleteSnackbar(val deletedMessage: MessageDomainModel) : MessageUiEvent
}


fun MessageDomainEvent.toMessageUiEvent(): MessageUiEvent =
    when (this) {
        is MessageDomainEvent.MessageMarkReadFailureFeedback -> MessageUiEvent.showToast("Unable to mark message as read")
        is MessageDomainEvent.MessageMarkUnReadFailureFeedback -> MessageUiEvent.showToast("Unable to mark the messages as unread")
        is MessageDomainEvent.MessageDeleteSuccessFeedback -> MessageUiEvent.showDeleteSnackbar(this.data)
        is MessageDomainEvent.MessageDeleteFailureFeedback -> MessageUiEvent.showToast("Unable to delete message. Please try again.")
        is MessageDomainEvent.MessageRestoreSuccessFeedback -> MessageUiEvent.showToast("Deleted message restored")
        is MessageDomainEvent.MessageRestoreFailureFeedback -> MessageUiEvent.showToast("Could not restore message")
    }