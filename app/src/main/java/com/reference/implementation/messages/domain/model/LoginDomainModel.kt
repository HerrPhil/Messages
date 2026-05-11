package com.reference.implementation.messages.domain.model

data class LoginDomainModel(
    val accessToken: String,
    val user: UserDomainModel
)