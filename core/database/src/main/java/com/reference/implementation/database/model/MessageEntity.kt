package com.reference.implementation.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Int,
    val subject: String,
    val body: String,
    val read: Boolean,
    val userId: Int,
    val createdAt: String // Kept as raw ISO 8601 String (e.g. 2026-07-13T22:28:56.321Z = GMT+0)
)

