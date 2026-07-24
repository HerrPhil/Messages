package com.reference.implementation.messages.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.toHomeUiState
import com.reference.implementation.messages.domain.use_case.GetUserDashboardUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState.Loading
    )
}
