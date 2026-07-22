package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.reference.implementation.messages.domain.model.toBulletinUiDetail
import com.reference.implementation.messages.domain.use_case.GetBulletinUseCase
import com.reference.implementation.messages.domain.use_case.LoadBulletinUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BulletinDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val loadBulletinUseCase: LoadBulletinUseCase,
    getBulletinUseCase: GetBulletinUseCase
) : ViewModel() {

    // Automatically extracts the 'id' from the strongly-typed BulletinDetail route!
    private val bulletinId: Int = checkNotNull(savedStateHandle.toRoute<Route.BulletinDetail>().id)

    // Tracks the active loading attempt reported by the repository's retryIO()
    private val _loadTrigger = MutableStateFlow(0)

    val uiState: StateFlow<BulletinDetailUiState> = _loadTrigger
        .flatMapLatest { attempt -> // needs Opt-in
            // Simply map the database/resource cache stream
            getBulletinUseCase().map { resourceResult ->
                when(resourceResult) {
                    is Resource.Loading -> {
                        if (attempt > 0) {
                            BulletinDetailUiState.Retrying(attempt)
                        } else {
                            BulletinDetailUiState.Loading
                        }
                    }
                    is Resource.Error -> BulletinDetailUiState.Error(resourceResult.message)
                    is Resource.Success -> {
                        val uiBulletinDetail = resourceResult.data.toBulletinUiDetail()
                        BulletinDetailUiState.Success(data = uiBulletinDetail)
                    }

                    else -> BulletinDetailUiState.Error("Something went wrong")
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BulletinDetailUiState.Loading
        )

    init {
        loadBulletinDetailData()
    }

    private fun loadBulletinDetailData() {
        viewModelScope.launch {
            loadBulletinUseCase(bulletinId, onRetry = { attempt ->
                _loadTrigger.value = attempt
            })
        }
    }
}