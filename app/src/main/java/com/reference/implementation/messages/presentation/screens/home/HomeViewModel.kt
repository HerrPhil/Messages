package com.reference.implementation.messages.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.domain.model.toHomeUiState
import com.reference.implementation.messages.domain.use_case.GetUserDashboardUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    getUserDashboardUseCase: GetUserDashboardUseCase,
) : ViewModel() {

    private val _retryAttempt = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = getUserDashboardUseCase(
        onRetry = { attempt -> _retryAttempt.value = attempt }
    ).combine(_retryAttempt) { resourceResult, attempt ->
        when (resourceResult) {
            is Resource.Loading -> {
                if (attempt > 0) {
                    HomeUiState.Retrying(attempt)
                } else {
                    HomeUiState.Loading
                }
            }

            is Resource.Error -> HomeUiState.Error(resourceResult.message)
            is Resource.Success -> resourceResult.data.toHomeUiState()
        }
    }.onStart {
        // 👈 Fires EVERY TIME collectAsStateWithLifecycle() connects!
        Audit.createInstance()
            .writeLog("HomeViewModel UI subscribed: HomeUiState flow collection started")
    }.onCompletion {
        // 👈 Fires when the UI unsubscribes (or after WhileSubscribed timeout)
        Audit.createInstance()
            .writeLog("HomeViewModelUI unsubscribed: HomeUiState flow collection ended")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    ).also {
        Audit.createInstance().writeLog("HomeViewModel declaration of uiState completed.")
    }

    override fun onCleared() {
        super.onCleared()
        Audit.createInstance().writeLog("HomeViewModel cleared from memory")
    }

}
