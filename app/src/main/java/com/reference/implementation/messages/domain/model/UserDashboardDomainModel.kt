package com.reference.implementation.messages.domain.model

import com.reference.implementation.messages.presentation.screens.home.HomeUiState

data class UserDashboardDomainModel(
    val userName: String,
    val userEmail: String,
    val unreadMessages: Int,
    val readMessages: Int,
    val roles: List<String>,
    val permissions: List<String>
)

fun UserDashboardDomainModel.toHomeUiState(): HomeUiState =
    HomeUiState.Success(
        userName = this.userName,
        userEmail = this.userEmail,
        unreadMessages = this.unreadMessages,
        readMessages = this.readMessages,
        roles = this.roles,
        permissions = this.permissions
    )
