package com.reference.implementation.messages.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.use_case.LogoutUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthenticatedShellViewModel(
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    // 1. Single source of truth for Shell visibility states
    private val _isTopBarVisible = MutableStateFlow(true)
    val isTopBarVisible = _isTopBarVisible.asStateFlow()

    // 2. Clear, expressive API for your screens to interact with
    fun setTopBarVisibility(visible: Boolean) {
        _isTopBarVisible.value = visible
    }

    private var logoutJob: Job? = null // Reference to the active work

    // No parameters - just initiate the log-out!
    fun logout() {

        // Cancel any existing job before starting a new one
        logoutJob?.cancel()

        logoutJob = viewModelScope.launch {
            logoutUseCase()
        }
    }
}
