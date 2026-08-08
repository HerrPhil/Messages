package com.reference.implementation.messages.domain.model

import com.reference.implementation.messages.presentation.screens.adminhome.AdminHomeUiState

data class AdminDashboardDomainModel(
    val usersCount: Int?,
    val summaryMessages: String?,
    val bulletinsCount: Int?
)

fun AdminDashboardDomainModel.toAdminHomeUiState(): AdminHomeUiState =
    AdminHomeUiState.Success(
        usersCount = this.usersCount,
        summaryMessages = this.summaryMessages,
        bulletinsCount = this.bulletinsCount
    )