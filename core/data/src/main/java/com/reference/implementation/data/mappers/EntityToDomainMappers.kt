package com.reference.implementation.data.mappers

import com.reference.implementation.database.model.BulletinEntity
import com.reference.implementation.database.model.MessageEntity
import com.reference.implementation.database.model.UserEntity
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.model.MessageDomainModel
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

fun UserEntity.toDomainModel() =
    LoginUserDomainModel(
        id = this.id,
        name = this.name,
        email = this.email,
        age = this.age
    )

fun MessageEntity.toDomainModel() =
    MessageDomainModel(
        id = this.id,
        subject = this.subject,
        body = this.body,
        read = this.read,
        userId = this.userId,
        createdAt = this.createdAt,
        createdAtInstant = try {
            Instant.parse(this.createdAt)
        } catch (_: Exception) {
            Instant.EPOCH
        }

    )

fun MessageDomainModel.toEntity() =
    MessageEntity(
        id = this.id,
        subject = this.subject,
        body = this.body,
        read = this.read,
        userId = this.userId,
        createdAt = this.createdAt
    )