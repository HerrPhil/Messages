package com.reference.implementation.messages.presentation.screens.adminhome

import androidx.lifecycle.ViewModel
import com.reference.implementation.messages.presentation.screens.message.MessageUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdminHomeViewModel: ViewModel() {

    private val _uiState = MutableStateFlow<AdminHomeUiState>(AdminHomeUiState.Idle)

    val uiState = _uiState.asStateFlow()

    init {
        loadAdminDashboardData()
    }

    private fun loadAdminDashboardData() {
        // TODO Repeat the same pattern we followed to load dashboard data on the home screen.
    }
}