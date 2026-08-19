package com.reference.implementation.messages.presentation.screens.adminhome

import com.reference.implementation.domain.model.AdminDashboardDomainModel

sealed interface AdminHomeUiState {
    object Idle : AdminHomeUiState
    object Loading : AdminHomeUiState
    data class Retrying(val attempt: Int) : AdminHomeUiState
    data class Success(
        val usersCount: Int?,
        val summaryMessages: String?,
        val bulletinsCount: Int?,
        val systemStatus: String = "Operational"
    ) : AdminHomeUiState
    data class Error(val message: String) : AdminHomeUiState
}

fun AdminDashboardDomainModel.toAdminHomeUiState(): AdminHomeUiState =
    AdminHomeUiState.Success(
        usersCount = this.usersCount,
        summaryMessages = this.summaryMessages,
        bulletinsCount = this.bulletinsCount
    )