package com.reference.implementation.messages.presentation.screens.message

import android.util.Log
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

    // Automatically extracts the 'id' from the strongly-typed:
    // MessageDetail, AdminMessageDetail, or AdminMonitorMessageDetail route!

    // Explanation of elegant side effect
    // This uncovers exactly how Jetpack Navigation's type-safe routing engine coordinates with
    // kotlin.serialization under the hood.
    //
    // The AdminMonitorMessageDetail succeeds for all 3 routes because isMonitorMode has a default
    // value ( = false) in the (data) class constructor definition, which allows the deserializer
    // to safely bypass the missing argument bundle key without throwing an exception.
    //
    // When a regular user navigates using Route.MessageDetail(id = 123), the Jetpack Navigation
    // Library packages (verb) the arguments map inside the destination's SavedStateHandle with
    // exactly one key-value pair:
    // * "id" -> 123
    // When the app boots up the shared ViewModel and parses the route statement:
    // val route = savedStateHandle.toRoute<Route.AdminMonitorMessageDetail>()
    // The underlying kotlinx.serialization decoder scans the bundle map to construct an instance
    // of AdminMonitorMessageDetail:
    // 1. It looks for "id": it finds "id" inside the map, extracts 123, and binds it to the class
    // parameter.
    // 2. It looks for "isMonitorMode": It realizes this key is completely missing from the regular
    // user's SavedStateHandle  bundle map.
    // 3. The Safe Defused Rollback: Ordinarily, a missing non-nullable property forces an immediate
    // crash (MissingFieldException). However, because there is a default value
    // (isMonitorMode: Boolean = false), the deserializer ignores the missing key and fills the
    // property with the default parameter value (false) instead!
    //
    // Because the deserializer can successfully populate every single property needed to build
    // the class, the expression resolves as a complete success without ever throwing a runtime
    // exception.
    //
    // In summary:
    // * For Admins Monitoring: The bundle contains "id" and "isMonitorMode" = true. The route
    //   statement extracts both, turning isMonitorMode to true (Read-tracking disabled!).
    // * For Regular Users: The bundle contains only "id". The route statement extracts the ID and
    // defaults isMonitorMode to false (Read-tracking remains enabled!).
    //
    // Because the default value fallback of AdminMonitorMessageDetail matches the explicit
    // setup configurations of Route.MessageDetail, the business rules remain completely
    // protected across all user roles.

    // THE SENIOR STANDARD: One single line handles all navigation entries!
    // Regular user routes default 'isMonitorMode' to false natively under the hood.
    val route = savedStateHandle.toRoute<Route.AdminMonitorMessageDetail>()
    val messageId = route.id
    val isMonitorMode = route.isMonitorMode

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
        // THE SAFE GUARD: Automatically respects monitor boundaries regardless of the route!
        // That is:
        // 1. admin account looking at admin message detail - mark message as read.
        // 2. admin account monitoring a regular user message detail - do not mark message as read.
        // 3. regular user account looking at message detail - mark message as read.
        if (!isMonitorMode) {
            markMessageAsReadOnNavigate()
        }
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