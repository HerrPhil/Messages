package com.reference.implementation.messages.domain.model

import com.reference.implementation.messages.presentation.screens.message.MessageUiDetail

data class MessageDomainModel(
    val id: Int,
    val body: String,
    val read: Boolean,
    val userId: Int
)

fun MessageDomainModel.toMessageUiDetail(): MessageUiDetail =
    MessageUiDetail(
        id = this.id,
        body = this.body,
        read = this.read,
        userId = this.userId
    )