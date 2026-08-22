package com.reference.implementation.data.mappers

import com.reference.implementation.data.dtos.BulletinDto
import com.reference.implementation.data.dtos.MessageDto
import com.reference.implementation.data.dtos.MessageRequestDto
import com.reference.implementation.data.dtos.RefreshTokenDto
import com.reference.implementation.data.dtos.UserDto
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.model.RefreshTokenDomainModel
import java.time.Instant

fun UserDto.toDomainModel(): LoginUserDomainModel = LoginUserDomainModel(this.email, this.name, this.id)

fun MessageDto.toMessageDomainModel(): MessageDomainModel =
    MessageDomainModel(
        this.id,
        this.subject,
        this.body,
        this.read,
        this.userId,
        this.createdAt,
        createdAtInstant = try {
            Instant.parse(this.createdAt)
        } catch (_: Exception) {
            Instant.EPOCH
        }
    )

fun MessageDomainModel.toMessageRequestDto(): MessageRequestDto =
    MessageRequestDto(this.id, this.body, this.subject, this.read, this.userId, this.createdAt)

fun RefreshTokenDto.toDomainModel(): RefreshTokenDomainModel =
    RefreshTokenDomainModel(this.accessToken)

fun BulletinDto.toBulletinDomainModel(): BulletinDomainModel =
    BulletinDomainModel(
        this.id,
        this.userId,
        this.title,
        this.post,
        this.timestamp,
        timestampInstant = try {
            Instant.parse(this.timestamp)
        } catch (_: Exception) {
            Instant.EPOCH
        }
    )