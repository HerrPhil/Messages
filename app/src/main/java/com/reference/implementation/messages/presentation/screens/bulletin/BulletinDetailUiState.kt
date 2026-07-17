package com.reference.implementation.messages.presentation.screens.bulletin

import com.reference.implementation.messages.presentation.screens.message.MessageUiDetail

sealed interface BulletinDetailUiState {
    object Idle : BulletinDetailUiState
    object Loading : BulletinDetailUiState
    data class Retrying(val attempt: Int) : BulletinDetailUiState
    data class Success(
        val data: BulletinUiDetail
    ) : BulletinDetailUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : BulletinDetailUiState
}