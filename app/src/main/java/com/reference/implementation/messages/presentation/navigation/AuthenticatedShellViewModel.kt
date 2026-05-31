package com.reference.implementation.messages.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.toUserUiState
import com.reference.implementation.messages.domain.use_case.LogoutUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class  AuthenticatedShellViewModel(
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    var uiState by mutableStateOf<AuthenticatedShellUiState>(AuthenticatedShellUiState.Idle)

    private var logoutJob: Job? = null // Reference to the active work

    // No parameters - just initiate the log-out!
    fun logout() {

        // Cancel any existing job before starting a new one
        logoutJob?.cancel()

        logoutJob = viewModelScope.launch {

            // move the UI state to "Loading..."
            uiState = AuthenticatedShellUiState.Loading

            val resource = logoutUseCase()

            uiState = when(resource) {
                is Resource.Success -> AuthenticatedShellUiState.LogoutComplete(
                    userUiState = resource.data.toUserUiState()
                )
                is  Resource.Error -> AuthenticatedShellUiState.Error(resource.message)
                else -> AuthenticatedShellUiState.Error("something went wrong")
            }
        }
    }

    // I think it does not make sense to implement a 'cancelLoading()' method for logout.
    // I expect it to complete, quickly.
    // Why? It is clearing the access token, at its core. Simple.
    // I thought of a case where I want to reset the UI state to "Idle".
    // If any errors are thrown, and I show an error message in a toast,
    // then I will reset the logout UI state to "Idle" to re-enable the menu item.
    fun cancel() {
        logoutJob?.cancel()
        uiState = AuthenticatedShellUiState.Idle
    }
}
