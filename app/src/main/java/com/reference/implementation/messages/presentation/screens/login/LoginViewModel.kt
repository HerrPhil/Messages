package com.reference.implementation.messages.presentation.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.toUserUiState
import com.reference.implementation.messages.domain.use_case.LoginUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginViewModel(private val loginUseCase: LoginUseCase) : ViewModel() {

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    private var loginJob: Job? = null // Reference to the active work

    fun login(email: String, password: String) {

        // Cancel any existing job before starting a new one
        loginJob?.cancel()

        loginJob = viewModelScope.launch {

            uiState = LoginUiState.Loading

            // My "server" is local and blazingly fast.
            // Without this, loading state is flickering.
            val minimumLoadingVisibility = async { delay(300) }
            minimumLoadingVisibility.await()

            val resource = loginUseCase(
                email,
                password,
                onRetry = { attempt -> uiState = LoginUiState.Retrying(attempt) }
            )

            uiState = when (resource) {
                is Resource.Success -> LoginUiState.Success(
                    userUiState = resource.data.toUserUiState()
                )

                is Resource.Error -> LoginUiState.Error(resource.message)
                else -> LoginUiState.Error("Something went wrong")
            }
        }
    }

    fun cancel() {
        loginJob?.cancel() // This triggers the CancellationException in the loop.
        uiState = LoginUiState.Idle
    }
}