package com.reference.implementation.messages.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.domain.use_case.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthenticatedShellViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

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
