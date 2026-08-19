package com.reference.implementation.domain.model

sealed interface MessageDomainEvent {
    data object MessageMarkReadFailureFeedback: MessageDomainEvent
    data object MessageMarkUnReadFailureFeedback: MessageDomainEvent
    data class MessageDeleteSuccessFeedback(val data: MessageDomainModel): MessageDomainEvent
    data object MessageDeleteFailureFeedback: MessageDomainEvent
    data object MessageRestoreSuccessFeedback: MessageDomainEvent
    data object MessageRestoreFailureFeedback: MessageDomainEvent
}