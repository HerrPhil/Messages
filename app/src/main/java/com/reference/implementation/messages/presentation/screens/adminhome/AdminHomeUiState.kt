package com.reference.implementation.messages.presentation.screens.adminhome

sealed interface AdminHomeUiState {
    object Idle : AdminHomeUiState
    object Loading : AdminHomeUiState
    data class Retrying(val attempt: String) : AdminHomeUiState
    data class Success( //  TODO figure out what successful data is for the UI
        val placeholder: String // TODO Data classes require values - this is a placeholder
    ) : AdminHomeUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : AdminHomeUiState
}