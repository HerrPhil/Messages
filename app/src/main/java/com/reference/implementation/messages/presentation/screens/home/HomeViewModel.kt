package com.reference.implementation.messages.presentation.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.data.manager.RoleManager
import com.reference.implementation.messages.domain.model.toUserUiState
import com.reference.implementation.messages.domain.use_case.LogoutUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeViewModel(roleManager: RoleManager) : ViewModel() {

    // Expose the application-layer user role state directly to the Home screen composition
    val userRoleState = roleManager.roleState

    var uiState by mutableStateOf<HomeUiState>(HomeUiState.Idle)

    fun cancel() {
        uiState = HomeUiState.Idle
    }
}
