package com.reference.implementation.messages.data.remote

import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import com.reference.implementation.messages.domain.model.RefreshTokenDomainModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: Int,
    val subject: String,
    val body: String,
    val read: Boolean,
    val userId: Int
)

@Serializable
data class MessageRequestDto(
    val id: Int,
    val body: String,
    val subject: String,
    val read: Boolean,
    val userId: Int
)

@Serializable
data class PartialMessageRequestDto(
    val body: String
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

// TODO left off here. create the Role DTO to receive the role, for example, by target User ID
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

fun UserDto.toDomainModel(): LoginUserDomainModel = LoginUserDomainModel(this.email, this.name)

fun MessageDto.toMessageDomainModel(): MessageDomainModel =
    MessageDomainModel(this.id, this.subject, this.body, this.read, this.userId)

fun MessageDomainModel.toMessageDto(): MessageDto =
    MessageDto(this.id, this.subject, this.body, this.read, this.userId)

fun MessageDomainModel.toPartialMessageRequestDto(): PartialMessageRequestDto =
    PartialMessageRequestDto(this.body)

fun MessageDomainModel.toMessageRequestDto(): MessageRequestDto =
    MessageRequestDto(this.id, this.body, this.subject, this.read, this.userId)

fun RefreshTokenDto.toDomainModel(): RefreshTokenDomainModel = RefreshTokenDomainModel(this.accessToken)