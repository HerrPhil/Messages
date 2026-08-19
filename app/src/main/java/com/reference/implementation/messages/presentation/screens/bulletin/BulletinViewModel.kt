package com.reference.implementation.messages.presentation.screens.bulletin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.use_case.GetAllBulletinsUseCase
import com.reference.implementation.domain.use_case.LoadAllBulletinsUseCase
import com.reference.implementation.domain.use_case.MarkBulletinAsBookmarkUseCase
import com.reference.implementation.domain.use_case.MarkBulletinAsNotBookmarkUseCase
import com.reference.implementation.domain.use_case.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BulletinViewModel(
    private val loadAllBulletinsUseCase: LoadAllBulletinsUseCase,
    getAllBulletinsUseCase: GetAllBulletinsUseCase,
    private val markBulletinAsBookmarkUseCase: MarkBulletinAsBookmarkUseCase,
    private val markBulletinAsNotBookmarkUseCase: MarkBulletinAsNotBookmarkUseCase,
) : ViewModel() {

    // Tracks the active loading attempt reported by the repository's retryIO()
    private val _loadTrigger = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isBookmarkOnly = MutableStateFlow<Boolean>(false)

    val uiState: StateFlow<BulletinUiState> = combine(
        // Subtle but important feature of flatMapLatest:
        // if a new trigger value comes down _loadTrigger (e.g. attempt 1 ---> attempt 2),
        // it cancels the previous database/network stream execution and starts a fresh one.
        // Standard combine does not cancel in-flight work when values change!
        _loadTrigger.flatMapLatest { getAllBulletinsUseCase() },
        _isRefreshing,
        _isBookmarkOnly
    ) { resourceResult, isRefreshing, isBookmarkOnly ->
        // 1. Bundle up the raw values as they emit
        BulletinInput(
            resourceResult,
            isRefreshing,
            isBookmarkOnly
        )
    }.scan<BulletinInput, BulletinUiState>(
        // 2. Set the initial state before any emission occurs
        initial = BulletinUiState.Loading
    ) // trailing Slot API accumulator operation of scan()
    { previousState, input ->

        val (resourceResult, isRefreshing, isBookmarkOnly) = input

        // Create UI state from the domain layer resource result
        when (resourceResult) {
            is Resource.Loading -> {
                // IF we are refreshing, do NOT drop back to full-screen Loading!
                // Preserve the existing list (if available) or keep Success state active.
                val previousSuccess = previousState as? BulletinUiState.Success
                if (isRefreshing && previousSuccess != null) {
                    // carry forward the previous Success state list, keeping the spinner alive
                    previousSuccess.copy(isRefreshing = true)
                } else if (_loadTrigger.value > 0) {
                    BulletinUiState.Retrying(_loadTrigger.value)
                } else {
                    BulletinUiState.Loading
                }
            }

            is Resource.Error -> {
                // Preserve the existing list (if available) or keep Success state active.
                val previousSuccess = previousState as? BulletinUiState.Success
                if (isRefreshing && previousSuccess != null) {
                    // carry forward the previous Success state list, keeping the spinner alive
                    previousSuccess.copy(isRefreshing = true)
                } else {
                    BulletinUiState.Error(resourceResult.message)
                }
            }

            is Resource.Success -> {
                val uiDetailList = resourceResult.data
                    .filter { bulletin ->
                        !isBookmarkOnly || bulletin.isBookmark
                    }
                    .map { bulletinDomainModel ->
                        bulletinDomainModel.toBulletinUiDetail()
                    }
                BulletinUiState.Success(
                    list = uiDetailList,
                    isRefreshing = isRefreshing,
                    isBookmarkOnly = isBookmarkOnly
                )
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

    fun onBookmarkOnlyToggled(enabled: Boolean) {
        Log.d("BulletinViewModel", "start onImportantOnlyTogged")
        _isBookmarkOnly.value = enabled
        Log.d("BulletinViewModel", "end onImportantOnlyTogged")
    }

    fun onToggleBookmarkBulletinClicked(bulletinId: Int, newIsBookmark: Boolean) {

        // I had a idea. I want to follow a suggestion from Gemini AI.
        // The new 'important' flag, a UI copy of the original data, never leaves the UI/viewModel.
        // The use-case/repository will provide to calls
        // markMessageAsImportant(messageId)
        // markMessageAsNotImportant(messagesId)

        viewModelScope.launch {
            if (newIsBookmark) {
                markBulletinAsBookmarkUseCase(bulletinId)
            } else {
                markBulletinAsNotBookmarkUseCase(bulletinId)
            }
        }
    }
}


private data class BulletinInput(
    val resource: Resource<List<BulletinDomainModel>>,
    val isRefreshing: Boolean,
    val isBookmarkOnly: Boolean
)

