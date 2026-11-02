package com.reference.implementation.messages.presentation.screens.message

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.GetMessageByIdUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getMessageByIdUseCase: GetMessageByIdUseCase,
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase,
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase
) : ViewModel() {

    // Automatically extracts the 'id' from the strongly-typed MessageDetail route!
    private val messageId: Int = checkNotNull(savedStateHandle.toRoute<Route.MessageDetail>().id)

    val uiState: StateFlow<MessageDetailUiState> =
        getMessageByIdUseCase(messageId).map { messageResource ->
            when (messageResource) {
                is Resource.Loading -> {
                    MessageDetailUiState.Loading
                }

                is Resource.Error -> MessageDetailUiState.Error(messageResource.message)
                is Resource.Success -> {
                    val message = messageResource.data
                    if (message != null) {
                        MessageDetailUiState.Success(data = message.toMessageUiDetail())
                    } else {
                        // THE TRAP-DOOR RESOLUTION: If the row is deleted, pass this
                        // clean state token so your Composable pops back safely without flashing a red error screen!
                        MessageDetailUiState.MessageDeletedBackNavigate
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