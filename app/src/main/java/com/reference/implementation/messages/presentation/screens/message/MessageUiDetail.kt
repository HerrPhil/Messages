package com.reference.implementation.messages.presentation.screens.message

data class MessageUiDetail(
    val id: Int,
    val body: String,
    val read: Boolean,
    val userId: Int
)
