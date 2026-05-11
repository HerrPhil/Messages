package com.reference.implementation.messages.domain.model

import com.reference.implementation.messages.presentation.screens.user.UserUiState

data class UserDomainModel(
    val id: Int,
    val email: String,
    val name: String,
    val age: Int
)

fun UserDomainModel.toUserUiState() : UserUiState = UserUiState.Success(this.id, this.email, this.name, this.age)
