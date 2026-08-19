package com.reference.implementation.messages.presentation.screens.adminmessage

import com.reference.implementation.domain.model.UserOptionDomainModel

data class UserUiDetail(
    val id: Int,
    val name: String,
    val isAdmin: Boolean
)

fun UserOptionDomainModel.toUserUiDetail(): UserUiDetail =
    UserUiDetail(
        id = this.id,
        name = this.name,
        isAdmin = this.isAdmin
    )