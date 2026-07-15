package com.reference.implementation.messages.presentation.screens.bulletin

import com.reference.implementation.messages.presentation.screens.message.MessageUiDetail

sealed interface BulletinUiState {
    object Idle : BulletinUiState
    object Loading : BulletinUiState
    data class Retrying(val attempt: Int) : BulletinUiState
    data class Success(
        val list: List<BulletinUiDetail>
    ) : BulletinUiState

    //    data class Warning(val message: String) : HomeUiState()
    data class Error(val message: String) : BulletinUiState
}