package com.reference.implementation.messages.domain.model

import com.reference.implementation.messages.presentation.screens.message.MessageUiDetail
import java.time.Instant

data class MessageDomainModel(
    val id: Int,
    val subject: String,
    val body: String,
    val read: Boolean,
    val userId: Int,
    val createdAt: String,
    val createdAtInstant: Instant,
    val isImportant: Boolean = false
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