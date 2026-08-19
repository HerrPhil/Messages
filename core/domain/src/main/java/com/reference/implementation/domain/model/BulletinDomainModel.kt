package com.reference.implementation.domain.model

import java.time.Instant

data class BulletinDomainModel(
    val id: Int,
    val userId: Int,
    val title: String,
    val post: String,
    val timestamp: String,
    val timestampInstant: Instant,
    val isBookmark: Boolean = false
)