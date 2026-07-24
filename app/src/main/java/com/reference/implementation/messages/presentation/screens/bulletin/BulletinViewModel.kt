package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.toBulletinUiDetail
import com.reference.implementation.messages.domain.use_case.GetAllBulletinsUseCase
import com.reference.implementation.messages.domain.use_case.LoadAllBulletinsUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BulletinViewModel(
    private val loadAllBulletinsUseCase: LoadAllBulletinsUseCase,
    getAllBulletinsUseCase: GetAllBulletinsUseCase
) : ViewModel() {

    // Tracks the active loading attempt reported by the repository's retryIO()
    private val _loadTrigger = MutableStateFlow(0)

    val uiState: StateFlow<BulletinUiState> = _loadTrigger
        .flatMapLatest { attempt ->
            getAllBulletinsUseCase().map { resourceResult ->
                // Create UI state from the domain layer resource result
                when (resourceResult) {
                    is Resource.Loading -> {
                        if (attempt > 0) {
                            BulletinUiState.Retrying(attempt)
                        } else {
                            BulletinUiState.Loading
                        }
                    }

                    is Resource.Error -> BulletinUiState.Error(resourceResult.message)
                    is Resource.Success -> {
                        val uiDetailList =
                            resourceResult.data.map { bulletinDomainModel -> bulletinDomainModel.toBulletinUiDetail() }
                        BulletinUiState.Success(list = uiDetailList)
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BulletinUiState.Loading
        )

    init {
        loadBulletinData()
    }

    private fun loadBulletinData() {
        viewModelScope.launch {
            loadAllBulletinsUseCase(onRetry = { attempt ->
                _loadTrigger.value = attempt
            })
        }
    }

}