package com.reference.implementation.messages.presentation.screens.adminhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.domain.use_case.GetAdminDashboardUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.data.audit.Audit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class AdminHomeViewModel(
    getAdminDashboardUseCase: GetAdminDashboardUseCase
) : ViewModel() {

    private val _retryAttempt = MutableStateFlow(0)

    val uiState: StateFlow<AdminHomeUiState> = getAdminDashboardUseCase(
        onRetry = { attempt -> _retryAttempt.value = attempt }
    ).combine(_retryAttempt) { resourceResult, attempt ->
        when (resourceResult) {
            is Resource.Loading -> {
                if (attempt > 0) {
                    AdminHomeUiState.Retrying(attempt)
                } else {
                    AdminHomeUiState.Loading
                }
            }

            is Resource.Success -> resourceResult.data.toAdminHomeUiState()

            is Resource.Error -> AdminHomeUiState.Error(resourceResult.message)
        }
    }.onStart {
        // Fires EVERY TIME collectAsStateWithLifecycle() connects!
        Audit.createInstance()
            .writeLog("AdminHomeViewModel UI subscribed: HomeUiState flow collection started")
    }.onCompletion {
        // Fires when the UI unsubscribes (or after WhileSubscribed timeout)
        Audit.createInstance()
            .writeLog("AdminHomeViewModel unsubscribed: HomeUiState flow collection ended")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdminHomeUiState.Loading
    ).also {
        Audit.createInstance().writeLog("AdminHomeViewModel declaration of uiState completed.")
    }
}

