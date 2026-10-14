package com.reference.implementation.data.mappers

import com.reference.implementation.data.dtos.BulletinDto
import com.reference.implementation.database.model.BulletinEntity
import com.reference.implementation.domain.model.BulletinDomainModel
import java.time.Instant

fun BulletinEntity.toDomainModel(): BulletinDomainModel =
    BulletinDomainModel(
        id = this.id,
        userId = this.userId,
        title = this.title,
        post = this.post,
        timestamp = this.timestamp,
        timestampInstant = Instant.parse(this.timestamp),
        isBookmark = this.isBookmark
    )