package com.reference.implementation.messages.presentation.screens.message

import com.reference.implementation.domain.model.MessageDomainModel

data class MessageUiDetail(
    val id: Int,
    val subject: String,
    val body: String,
    val read: Boolean,
    val userId: Int,
    val createdAt: String,
    val isImportant: Boolean
)

fun MessageDomainModel.toMessageUiDetail(): MessageUiDetail =
    MessageUiDetail(
        id = this.id,
        subject = this.subject,
        body = this.body,
        read = this.read,
        userId = this.userId,
        createdAt = this.createdAt,
        isImportant = isImportant
    )