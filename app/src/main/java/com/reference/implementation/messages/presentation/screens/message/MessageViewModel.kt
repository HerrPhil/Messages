package com.reference.implementation.messages.presentation.screens.message

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MessageViewModel: ViewModel() {

    private val _uiState = MutableStateFlow<MessageUiState>(MessageUiState.Idle)

    val uiState = _uiState.asStateFlow()

    init {
        loadMessageData()
    }

    private fun loadMessageData() {
        // TODO Repeat the same pattern we followed to load dashboard data on the home screen.
    }
}