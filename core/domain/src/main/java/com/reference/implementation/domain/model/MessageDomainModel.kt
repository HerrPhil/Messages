package com.reference.implementation.domain.model

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