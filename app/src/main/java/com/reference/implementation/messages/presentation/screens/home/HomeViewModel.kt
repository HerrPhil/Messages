package com.reference.implementation.messages.presentation.screens.home

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

class HomeViewModel(
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    var uiState by mutableStateOf<HomeUiState>(HomeUiState.Idle)

    private var logoutJob: Job? = null // Reference to the active work

    // No parameters - just initiate the log-out!
    fun logout() {

        // Cancel any existing job before starting a new one
        logoutJob?.cancel()

        logoutJob = viewModelScope.launch {

            // move the UI state to "Loading..."
            uiState = HomeUiState.Loading

            val resource = logoutUseCase()

            uiState = when(resource) {
                is Resource.Success -> HomeUiState.LogoutComplete(
                    userUiState = resource.data.toUserUiState()
                )
                is  Resource.Error -> HomeUiState.Error(resource.message)
                else -> HomeUiState.Error("something went wrong")
            }
        }
    }

    fun reset() {
        uiState = HomeUiState.Idle
    }

    // I think it does not make sense to implement a 'cancelLoading()' method for logout.
    // I expect it to complete, quickly.
    // Why? It is clearing the access token, at its core. Simple.
    // I thought of a case where I want to reset the UI state to "Idle".
    // If any errors are thrown, and I show an error message in a toast,
    // then I will reset the logout UI state to "Idle" to re-enable the menu item.
    fun cancel() {
        logoutJob?.cancel()
        uiState = HomeUiState.Idle
    }
}
