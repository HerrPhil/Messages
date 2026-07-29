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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BulletinViewModel(
    private val loadAllBulletinsUseCase: LoadAllBulletinsUseCase,
    getAllBulletinsUseCase: GetAllBulletinsUseCase
) : ViewModel() {

    // Tracks the active loading attempt reported by the repository's retryIO()
    private val _loadTrigger = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<BulletinUiState> = combine(
        // Subtle but important feature of flatMapLatest:
        // if a new trigger value comes down _loadTrigger (e.g. attempt 1 ---> attempt 2),
        // it cancels the previous database/network stream execution and starts a fresh one.
        // Standard combine does not cancel in-flight work when values change!
        _loadTrigger.flatMapLatest { getAllBulletinsUseCase() },
        _isRefreshing
    ) { resourceResult, isRefreshing ->
        // Create UI state from the domain layer resource result
        when (resourceResult) {
            is Resource.Loading -> {
                if (_loadTrigger.value > 0) {
                    BulletinUiState.Retrying(_loadTrigger.value)
                } else {
                    BulletinUiState.Loading
                }
            }

            is Resource.Error -> BulletinUiState.Error(resourceResult.message)
            is Resource.Success -> {
                val uiDetailList = resourceResult.data.map { bulletinDomainModel ->
                        bulletinDomainModel.toBulletinUiDetail() }
                BulletinUiState.Success(
                    list = uiDetailList,
                    isRefreshing = isRefreshing)
            }
        }
    }.stateIn(
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

    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true // turn ON refreshing
            try {
                loadAllBulletinsUseCase(onRetry = { attempt ->
                    _loadTrigger.value = attempt
                }) // suspending call - wait until done
            } finally { // turn OFF refreshing
                _isRefreshing.value = false
            }
        }
    }
}