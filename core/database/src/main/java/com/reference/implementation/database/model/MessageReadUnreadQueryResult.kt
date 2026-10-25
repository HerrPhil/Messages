package com.reference.implementation.database.model

data class MessageReadUnreadQueryResult(
    val readCount: Int,
    val unreadCount: Int
)