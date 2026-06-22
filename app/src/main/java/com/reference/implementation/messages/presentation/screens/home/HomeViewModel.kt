package com.reference.implementation.messages.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.toHomeUiState
import com.reference.implementation.messages.domain.use_case.GetUserDashboardUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getUserDashboardUseCase: GetUserDashboardUseCase,
) : ViewModel() {

    // 1. Define your private mutable state backing property
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)

    // 2. Expose it as an immutable read-only state flow to the UI
    val uiState = _uiState.asStateFlow()

    init {
        // 3. Automatically kick off the data loading when the ViewModel is created
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            getUserDashboardUseCase(onRetryString = { attempt ->
                _uiState.value = HomeUiState.Retrying(attempt)
            }).collectLatest { resource ->
                // 5. Explicitly map your resource over to your ViewModel's UI State
                _uiState.value = when (resource) {
                    is Resource.Loading -> HomeUiState.Loading
                    is Resource.Error -> HomeUiState.Error(resource.message)
                    is Resource.Success -> resource.data.toHomeUiState()
                    else -> {
                        HomeUiState.Error("something went wrong")
                    }
                }
            }
        }
    }
}
