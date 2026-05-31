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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoginViewModel(private val loginUseCase: LoginUseCase) : ViewModel() {

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    // 1. Private mutable state backing login properties
    private val _email = MutableStateFlow("")
    private val _password = MutableStateFlow("")

    // 2. Public read-only state flows exposed to the UI
    val email: StateFlow<String> = _email.asStateFlow()
    val password: StateFlow<String> = _password.asStateFlow()

    // 3. Derived state using the private backing login properties
    val isSubmitEnabled: StateFlow<Boolean> = combine(_email, _password) { email, pass ->
        email.isNotBlank() && pass.isNotBlank()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private var loginJob: Job? = null // Reference to the active work

    // 4. Public events to safely mutate the state
    fun onEmailChange(newValue: String) {
        _email.value = newValue
    }

    fun onPasswordChange(newValue: String) {
        _password.value = newValue
    }

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