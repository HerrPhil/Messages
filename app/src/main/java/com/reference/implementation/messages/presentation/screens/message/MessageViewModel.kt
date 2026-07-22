package com.reference.implementation.messages.presentation.screens.message

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.model.toMessageUiDetail
import com.reference.implementation.messages.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.messages.domain.use_case.GetActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.GetMessageUiEventsUseCase
import com.reference.implementation.messages.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import com.reference.implementation.messages.domain.use_case.RestoreMessageUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessageViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val loadActiveMessagesUseCase: LoadActiveMessagesUseCase,
    getActiveMessagesUseCase: GetActiveMessagesUseCase,
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase,
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val restoreMessageUseCase: RestoreMessageUseCase,
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

    val uiState: StateFlow<MessageUiState> = getActiveMessagesUseCase()
        .combine(searchQuery) { resourceResult, query ->
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

    val uiEvents = getMessageUiEventsUseCase()

    init {
        loadMessageData()
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