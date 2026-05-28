package com.reference.implementation.messages.data.remote

import com.reference.implementation.messages.domain.model.LoginDomainModel
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: Int,
    val body: String,
    val userId: Int
)

data class MessageRequestDto(
    val body: String,
    val userId: Int
)

@Serializable
data class PartialMessageRequestDto(
    val body: String
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class LoginDto(
    val accessToken: String,
    @SerialName("user") // This matches the JSON key from the server
    val userDto: UserDto
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
    val role: String,
    val targetUserId: Int,
    val permissionId: Int,
    val userId: Int
)

fun LoginDto.toDomainModel() : LoginDomainModel = LoginDomainModel(this.accessToken, this.userDto.toDomainModel())

fun UserDto.toDomainModel() : UserDomainModel = UserDomainModel(this.id, this.email, this.name, this.age)

fun MessageDto.toMessageDomainModel() : MessageDomainModel = MessageDomainModel(this.id, this. body, this.userId)

fun MessageDomainModel.toMessageDto() : MessageDto = MessageDto(this.id, this.body, this.userId)

fun MessageDomainModel.toPartialMessageRequestDto() : PartialMessageRequestDto = PartialMessageRequestDto(this.body)

fun MessageDomainModel.toMessageRequestDto() : MessageRequestDto = MessageRequestDto(this.body, this.userId)
