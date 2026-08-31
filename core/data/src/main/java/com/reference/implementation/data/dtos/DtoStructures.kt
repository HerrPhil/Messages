package com.reference.implementation.data.dtos

import com.reference.implementation.domain.model.MessageDomainModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class MessageDto(
    val id: Int,
    val subject: String,
    val body: String,
    val read: Boolean,
    val userId: Int,
    val createdAt: String // Kept as raw ISO 8601 String (e.g. 2026-07-13T22:28:56.321Z = GMT+0)
)

@Serializable
data class MessageRequestDto(
    val id: Int,
    val body: String,
    val subject: String,
    val read: Boolean,
    val userId: Int,
    val createdAt: String // Kept as raw ISO 8601 String (e.g. 2026-07-13T22:28:56.321Z = GMT+0)
)

@Serializable
data class MarkMessageAsReadDto(
    val read: Boolean = true
)

@Serializable
data class MarkMessageAsUnreadDto(
    val read: Boolean = false
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class LoginDto(
    val accessToken: String,
    val refreshToken: String,
    @SerialName("user") // This matches the JSON key from the server
    val userDto: UserDto
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String
)

@Serializable
data class RefreshTokenDto(
    val accessToken: String
)

@Serializable
data class UserDto(
    val id: Int,
    val email: String,
    val name: String,
    val age: Int
)

@Serializable
data class RoleDto(
    val id: Int,
    val name: String,
    val targetUserId: Int,
    val permissions: List<Int>,
    val userId: Int // data owner aka administrator
)

@Serializable
data class PermissionDto(
    val id: Int,
    val task: String,
    val userId: Int // data owner aka administrator
)

@Serializable
data class BulletinDto(
    val id: Int,
    val userId: Int,
    val title: String,
    val post: String,
    val timestamp: String // Kept as raw ISO 8601 String (e.g. 2026-07-13T22:28:56.321Z = GMT+0)
)

fun MessageDomainModel.toDto(): MessageDto =
    MessageDto(
        id = this.id,
        subject = this.subject,
        body = this.body,
        read = this.read,
        userId = this.userId,
        createdAt = this.createdAt
    )

