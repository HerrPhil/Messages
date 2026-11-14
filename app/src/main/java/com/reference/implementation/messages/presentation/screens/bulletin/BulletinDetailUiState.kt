package com.reference.implementation.messages.presentation.screens.bulletin

sealed interface BulletinDetailUiState {

    object Idle : BulletinDetailUiState
    object Loading : BulletinDetailUiState
    object BulletinDeletedBackNavigate : BulletinDetailUiState

    data class Success(
        val data: BulletinUiDetail
    ) : BulletinDetailUiState

    data class Error(val message: String) : BulletinDetailUiState
}