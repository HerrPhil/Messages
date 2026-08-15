package com.reference.implementation.messages.presentation.screens.message

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.model.toMessageUiDetail
import com.reference.implementation.messages.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.messages.domain.use_case.GetCachedMessagesUseCase
import com.reference.implementation.messages.domain.use_case.GetMessageUiEventsUseCase
import com.reference.implementation.messages.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsImportantUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsNotImportantUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import com.reference.implementation.messages.domain.use_case.RestoreMessageUseCase
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
class MessageViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val loadActiveMessagesUseCase: LoadActiveMessagesUseCase,
    getCachedMessagesUseCase: GetCachedMessagesUseCase,
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase,
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val restoreMessageUseCase: RestoreMessageUseCase,
    private val markMessageAsImportantUseCase: MarkMessageAsImportantUseCase,
    private val markMessageAsNotImportantUseCase: MarkMessageAsNotImportantUseCase,
    getMessageUiEventsUseCase: GetMessageUiEventsUseCase
) : ViewModel() {

    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
    }

    // Replaces BOTH _searchQuery and searchQuery
    // Your screen reads this exactly like it did before
    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(
        key = KEY_SEARCH_QUERY,
        initialValue = ""
    )

    // Tracks the active loading attempt reported by the repository's retryIO()
    private val _loadTrigger = MutableStateFlow(0)

    // The _isRefreshing must stay private to the view model
    // Expose isRefreshing via MessageUiState.Success (e.g. AuthenticatedMainParameterHub)
    private val _isRefreshing = MutableStateFlow(false)

    private val _isImportantOnly = MutableStateFlow(false)

    val uiState: StateFlow<MessageUiState> = combine(
        // Subtle but important feature of flatMapLatest:
        // if a new trigger value comes down _loadTrigger (e.g. attempt 1 ---> attempt 2),
        // it cancels the previous database/network stream execution and starts a fresh one.
        // Standard combine does not cancel in-flight work when values change!
        _loadTrigger.flatMapLatest { getCachedMessagesUseCase() },
        _isRefreshing,
        searchQuery,
        _isImportantOnly
    ) { resourceResult, isRefreshing, query, isImportantOnly ->
        // 1. Bundle up the raw values as they emit
        MessageInput(
            resourceResult,
            isRefreshing,
            query,
            isImportantOnly
        )
    }.scan<MessageInput, MessageUiState>(
        // 2. Set the initial state before any emission occurs
        initial = MessageUiState.Loading
    ) // trailing Slot API accumulator operation of scan()
    { previousState, input ->

        val (resourceResult, isRefreshing, query, isImportantOnly) = input

        // ViewModel only worries about user text filtering on top of the clean data!
        when (resourceResult) {
            is Resource.Loading -> {
                // IF we are refreshing, do NOT drop back to full-screen Loading!
                // Preserve the existing list (if available) or keep Success state active.
                val previousSuccess = previousState as? MessageUiState.Success
                if (isRefreshing && previousSuccess != null) {
                    // carry forward the previous Success state list, keeping the spinner alive
                    previousSuccess.copy(isRefreshing = true)
                } else if (_loadTrigger.value > 0) {
                    MessageUiState.Retrying(_loadTrigger.value)
                } else {
                    MessageUiState.Loading
                }
            }

            is Resource.Error -> {
                // Preserve the existing list (if available) or keep Success state active.
                val previousSuccess = previousState as? MessageUiState.Success
                if (isRefreshing && previousSuccess != null) {
                    // carry forward the previous Success state list, keeping the spinner alive
                    previousSuccess.copy(isRefreshing = true)
                } else {
                    MessageUiState.Error(resourceResult.message)
                }
            }

            is Resource.Success -> {
                val filteredList =
                    resourceResult.data
                        .filter { message ->
                            !isImportantOnly || message.isImportant
                        }
                        .map { messageDomainModel -> messageDomainModel.toMessageUiDetail() }
                        .filter {
                            it.body.contains(query, ignoreCase = true) ||
                                    it.subject.contains(query, ignoreCase = true)
                        }
                MessageUiState.Success(
                    list = filteredList,
                    isRefreshing = isRefreshing,
                    isImportantOnly = isImportantOnly
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MessageUiState.Loading
    )

    val uiEvents = getMessageUiEventsUseCase()

    init {
        loadMessageData()
    }

    private fun loadMessageData() {
        viewModelScope.launch {
            loadActiveMessagesUseCase(onRetry = { attempt ->
                _loadTrigger.value = attempt
            })
        }
    }

    fun onSearchChanged(newQuery: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = newQuery
    }

    fun onDeleteMessage(messageId: Int) {
        viewModelScope.launch {
            deleteMessageUseCase(messageId)
        }
    }

    fun onRestoreMessage(deletedMessage: MessageDomainModel) {
        viewModelScope.launch {
            restoreMessageUseCase(deletedMessage)
        }
    }

    fun onToggleReadStatus(messageId: Int, newReadStatus: Boolean) {

        // I had an idea. I want to follow a suggestion from Gemini AI.
        // The new read status, a UI copy of the original data, never leaves the UI/viewModel.
        // The use-case/repository will provide to calls
        // markMessageAsRead(messageId)
        // markMessageAsUnread(messagesId)
        // The repository code inherently uses the correct Boolean values.
        // Read = true
        // Unread = false

        viewModelScope.launch {
            if (newReadStatus) {
                markMessageAsReadUseCase(messageId)
            } else {
                markMessageAsUnreadUseCase(messageId)
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true // turn ON refreshing
            try {
                loadActiveMessagesUseCase(onRetry = { attempt ->
                    _loadTrigger.value = attempt
                }) // suspending call - wait until done
            } finally { // turn OFF refreshing
                _isRefreshing.value = false
            }
        }
    }

    fun onImportantOnlyToggled(enabled: Boolean) {
        Log.d("MessageViewModel", "start onImportantOnlyTogged")
        _isImportantOnly.value = enabled
        Log.d("MessageViewModel", "end onImportantOnlyTogged")
    }

    fun onToggleImportantMessageClicked(messageId: Int, newIsImportant: Boolean) {

        // I had an idea. I want to follow a suggestion from Gemini AI.
        // The new 'important' flag, a UI copy of the original data, never leaves the UI/viewModel.
        // The use-case/repository will provide to calls
        // markMessageAsImportant(messageId)
        // markMessageAsNotImportant(messagesId)

        viewModelScope.launch {
            if (newIsImportant) {
                markMessageAsImportantUseCase(messageId)
            } else {
                markMessageAsNotImportantUseCase(messageId)
            }
        }
    }

}

private data class MessageInput(
    val resource: Resource<List<MessageDomainModel>>,
    val isRefreshing: Boolean,
    val query: String,
    val isImportantOnly: Boolean
)