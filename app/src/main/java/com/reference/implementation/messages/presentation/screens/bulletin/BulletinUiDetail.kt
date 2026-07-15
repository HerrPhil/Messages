package com.reference.implementation.messages.presentation.screens.bulletin

data class BulletinUiDetail(
    val id: Int,
    val userId: Int,
    val title: String,
    val post: String,
    val timestamp: String
)