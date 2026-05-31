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
) : ViewModel() {

    var uiState by mutableStateOf<HomeUiState>(HomeUiState.Idle)

    fun cancel() {
        uiState = HomeUiState.Idle
    }
}
