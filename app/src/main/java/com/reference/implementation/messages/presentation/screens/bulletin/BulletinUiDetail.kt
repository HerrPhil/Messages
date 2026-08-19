package com.reference.implementation.messages.presentation.screens.bulletin

import com.reference.implementation.domain.model.BulletinDomainModel

data class BulletinUiDetail(
    val id: Int,
    val userId: Int,
    val title: String,
    val post: String,
    val timestamp: String,
    val isBookmark: Boolean
)

fun BulletinDomainModel.toBulletinUiDetail(): BulletinUiDetail =
    BulletinUiDetail(
        id = this.id,
        userId = this.userId,
        title = this.title,
        post = this.post,
        timestamp = this.timestamp,
        isBookmark = this.isBookmark
    )