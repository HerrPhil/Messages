package com.reference.implementation.messages.presentation.screens.message

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.reference.implementation.messages.domain.model.toMessageUiDetail
import com.reference.implementation.messages.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.messages.domain.use_case.GetActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MessageDetailViewModel(
    savedStateHandle: SavedStateHandle,
    getActiveMessagesUseCase: GetActiveMessagesUseCase,
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase,
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase
) : ViewModel() {

    // Automatically extracts the 'id' from the strongly-typed MessageDetail route!
    private val messageId: Int = checkNotNull(savedStateHandle.toRoute<Route.MessageDetail>().id)

    val uiState: StateFlow<MessageDetailUiState> =
        getActiveMessagesUseCase().map { resourceResult ->
            when (resourceResult) {
                is Resource.Loading -> {
                    MessageDetailUiState.Loading
                }

                is Resource.Error -> MessageDetailUiState.Error(resourceResult.message)
                is Resource.Success -> {
                    val message = resourceResult.data.find { it.id == messageId }
                    if (message != null) {
                        MessageDetailUiState.Success(data = message.toMessageUiDetail())
                    } else {
                        MessageDetailUiState.Error("Message not found")
                    }
                }
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MessageDetailUiState.Loading
            )

    init {
        Log.d(
            "MessageDetailViewModel",
            "Hey, looks like the saved state handle delivered a message ID = $messageId"
        )
        markMessageAsReadOnNavigate()
    }

    fun onDeleteMessage(messageId: Int) {
        viewModelScope.launch {
            deleteMessageUseCase(messageId)
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

    private fun markMessageAsReadOnNavigate() {
        viewModelScope.launch {
            markMessageAsReadUseCase(messageId)
        }
    }
}