package com.reference.implementation.messages.domain.model

import com.reference.implementation.messages.presentation.screens.bulletin.BulletinUiDetail
import com.reference.implementation.messages.presentation.screens.message.MessageUiDetail

data class BulletinDomainModel(
    val id: Int,
    val userId: Int,
    val title: String,
    val post: String,
    val timestamp: String
)

fun BulletinDomainModel.toBulletinUiDetail(): BulletinUiDetail =
    BulletinUiDetail(
        id = this.id,
        userId = this.userId,
        title = this.title,
        post = this.post,
        timestamp = this.timestamp
    )