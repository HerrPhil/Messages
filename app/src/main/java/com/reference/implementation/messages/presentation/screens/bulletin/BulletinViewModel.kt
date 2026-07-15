package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.toBulletinUiDetail
import com.reference.implementation.messages.domain.use_case.GetAllBulletinsUseCase
import com.reference.implementation.messages.domain.use_case.GetBulletinUiEventsUseCase
import com.reference.implementation.messages.domain.use_case.LoadAllBulletinsUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BulletinViewModel(
    private val loadAllBulletinsUseCase: LoadAllBulletinsUseCase,
    getAllBulletinsUseCase: GetAllBulletinsUseCase,
    getBulletinUiEventsUseCase: GetBulletinUiEventsUseCase
) : ViewModel() {

    val uiState: StateFlow<BulletinUiState> = getAllBulletinsUseCase()
        .map { resourceResult ->
            // Create UI state from the domain layer resource result
            when (resourceResult) {
                is Resource.Loading -> BulletinUiState.Loading
                is Resource.Error -> BulletinUiState.Error(resourceResult.message)
                is Resource.Success -> {
                    val uiDetailList =
                        resourceResult.data.map { bulletinDomainModel -> bulletinDomainModel.toBulletinUiDetail() }
                    BulletinUiState.Success(list = uiDetailList)
                }

                else -> BulletinUiState.Error("Something went wrong")

            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BulletinUiState.Loading
        )


    val uiEvents = getBulletinUiEventsUseCase()

    init {
        loadBulletinData()
    }

    private fun loadBulletinData() {
        viewModelScope.launch {
            loadAllBulletinsUseCase(onRetry = { attempt ->
                BulletinUiState.Retrying(attempt)
            })
        }
    }

}