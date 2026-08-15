package com.reference.implementation.messages.domain.model

import com.reference.implementation.messages.presentation.screens.adminmessage.UserUiDetail

data class UserOptionDomainModel(
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