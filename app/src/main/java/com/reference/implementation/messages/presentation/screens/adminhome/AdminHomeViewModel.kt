package com.reference.implementation.messages.presentation.screens.adminhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.domain.use_case.GetAdminDashboardUseCase
import com.reference.implementation.domain.use_case.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    getAdminDashboardUseCase: GetAdminDashboardUseCase
) : ViewModel() {

    val uiState: StateFlow<AdminHomeUiState> = getAdminDashboardUseCase()
        .map { resourceResult ->
            when (resourceResult) {
                is Resource.Loading -> {
                    AdminHomeUiState.Loading
                }

                is Resource.Success -> {
                    resourceResult.data.toAdminHomeUiState()
                }

                is Resource.Error -> AdminHomeUiState.Error(resourceResult.message)
            }
        }.onStart {
            // Fires EVERY TIME collectAsStateWithLifecycle() connects!
            auditLog("AdminHomeViewModel UI subscribed: HomeUiState flow collection started")
        }.onCompletion {
            // Fires when the UI unsubscribes (or after WhileSubscribed timeout)
            auditLog("AdminHomeViewModel unsubscribed: HomeUiState flow collection ended")
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AdminHomeUiState.Loading
        ).also {
            auditLog("AdminHomeViewModel declaration of uiState completed.")
        }
}