package com.reference.implementation.messages.data.remote

import com.reference.implementation.messages.domain.model.LoginDomainModel
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel
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
    val userDto: UserDto
)

@Serializable
data class UserDto(
    val id: Int,
    val email: String,
    val name: String,
    val age: Int
)

fun LoginDto.toDomainModel() : LoginDomainModel = LoginDomainModel(this.accessToken, this.userDto.toDomainModel())

fun UserDto.toDomainModel() : UserDomainModel = UserDomainModel(this.id, this.email, this.name, this.age)

fun MessageDto.toMessageDomainModel() : MessageDomainModel = MessageDomainModel(this.id, this. body, this.userId)

fun MessageDomainModel.toMessageDto() : MessageDto = MessageDto(this.id, this.body, this.userId)

fun MessageDomainModel.toPartialMessageRequestDto() : PartialMessageRequestDto = PartialMessageRequestDto(this.body)

fun MessageDomainModel.toMessageRequestDto() : MessageRequestDto = MessageRequestDto(this.body, this.userId)
