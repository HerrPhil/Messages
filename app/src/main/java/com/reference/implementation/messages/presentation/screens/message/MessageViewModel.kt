package com.reference.implementation.messages.presentation.screens.message

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.toMessageUiDetail
import com.reference.implementation.messages.domain.use_case.GetActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessageViewModel(
    private val loadActiveMessagesUseCase: LoadActiveMessagesUseCase,
    getActiveMessagesUseCase: GetActiveMessagesUseCase,
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase,
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    // Used in the screen where onValueChanged() is clicked
    val searchQuery = _searchQuery.asStateFlow()

    val uiState: StateFlow<MessageUiState> = getActiveMessagesUseCase()
        .combine(_searchQuery) { resourceResult, query ->
            // ViewModel only worries about user text filtering on top of the clean data!
            when (resourceResult) {
                is Resource.Loading -> MessageUiState.Loading
                is Resource.Error -> MessageUiState.Error(resourceResult.message)
                is Resource.Success -> {
                    val filteredList =
                        resourceResult.data
                            .map { messageDomainModel -> messageDomainModel.toMessageUiDetail() }
                            .filter {
                                it.body.contains(query, ignoreCase = true)
                            }
                    MessageUiState.Success(filteredList)
                }

                else -> {
                    MessageUiState.Error("Something went wrong")
                }
            }

        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MessageUiState.Loading
        )

    init {
        loadMessageData()
    }

    fun onSearchChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onDeleteMessage(messageId: Int) {
        Log.d(
            "MessageViewModel",
            "TODO complete the use case and repository to delete message with ID $messageId"
        )
    }

    fun onToggleReadStatus(messageId: Int, newReadStatus: Boolean) {

        // I had a idea. I want to follow a suggestion from Gemini AI.
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

    private fun loadMessageData() {
        viewModelScope.launch {
            loadActiveMessagesUseCase(onRetry = { attempt ->
                MessageUiState.Retrying(attempt)
            })
        }
    }
}