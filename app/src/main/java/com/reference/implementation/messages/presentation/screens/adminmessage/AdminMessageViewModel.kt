package com.reference.implementation.messages.presentation.screens.adminmessage

import androidx.lifecycle.ViewModel
import com.reference.implementation.messages.presentation.screens.adminhome.AdminHomeUiState
import com.reference.implementation.messages.presentation.screens.message.MessageUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdminMessageViewModel: ViewModel() {

    private val _uiState = MutableStateFlow<AdminMessageUiState>(AdminMessageUiState.Idle)

    val uiState = _uiState.asStateFlow()

    init {
        loadAdminMessageData()
    }

    private fun loadAdminMessageData() {
        // TODO Repeat the same pattern we followed to load dashboard data on the home screen.
    }
}