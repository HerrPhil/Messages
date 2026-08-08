package com.reference.implementation.messages.presentation.screens.adminhome

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