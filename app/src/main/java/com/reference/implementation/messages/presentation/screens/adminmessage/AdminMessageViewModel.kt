package com.reference.implementation.messages.presentation.screens.adminmessage

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.UserOptionDomainModel
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.GetCachedMessagesUseCase
import com.reference.implementation.domain.use_case.GetAdminUserInformationUseCase
import com.reference.implementation.domain.use_case.GetMessageEventsUseCase
import com.reference.implementation.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.domain.use_case.LoadAllUsersUseCase
import com.reference.implementation.domain.use_case.LoadSelectedMessagesUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsNotImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.domain.use_case.RestoreMessageUseCase
import com.reference.implementation.messages.presentation.screens.message.toMessageUiDetail
import com.reference.implementation.messages.presentation.screens.message.toMessageUiEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AdminMessageViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val loadActiveMessagesUseCase: LoadActiveMessagesUseCase,
    private val loadSelectedMessagesUseCase: LoadSelectedMessagesUseCase,
    getCachedMessagesUseCase: GetCachedMessagesUseCase,
    private val loadAllUsersUseCase: LoadAllUsersUseCase,
    getAdminUserInformationUseCase: GetAdminUserInformationUseCase,
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase,
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val restoreMessageUseCase: RestoreMessageUseCase,
    private val markMessageAsImportantUseCase: MarkMessageAsImportantUseCase,
    private val markMessageAsNotImportantUseCase: MarkMessageAsNotImportantUseCase,
    getMessageEventsUseCase: GetMessageEventsUseCase
) : ViewModel() {

    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_USER_OPTION_QUERY = "user_option_query"
        private const val KEY_SELECTED_USER_ID = "selected_user_id"
        private const val KEY_IS_ADMIN_SELECTED = "is_admin_selected"
    }

    // Replaces BOTH _searchQuery and searchQuery
    // Your screen reads this exactly like it did before
    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(
        key = KEY_SEARCH_QUERY,
        initialValue = ""
    )

    val userOptionQuery: StateFlow<String> = savedStateHandle.getStateFlow(
        key = KEY_USER_OPTION_QUERY,
        initialValue = ""
    )

    val selectedUserId: StateFlow<Int?> = savedStateHandle.getStateFlow(
        key = KEY_SELECTED_USER_ID, initialValue = null
    )

    val isAdminSelected: StateFlow<Boolean> = savedStateHandle.getStateFlow(
        key = KEY_IS_ADMIN_SELECTED, initialValue = true
    )

    // Tracks the active loading attempt reported by the repository's retryIO()
    private val _loadTrigger = MutableStateFlow(0)

    // The _isRefreshing must stay private to the view model
    // Expose isRefreshing via MessageUiState.Success (e.g. AuthenticatedMainParameterHub)
    private val _isRefreshing = MutableStateFlow(false)

    private val _isImportantOnly = MutableStateFlow(false)

    // This exists because the newest combine() parameters
    // pushed past the 5 parameter static limit of combine().
    // The addition of isAdminSelected maxes out the combine parameters.
    // If any more are added, then the explicit array casting pattern must be used.
    private val filterPreferencesFlow = combine(
        searchQuery,
        _isImportantOnly,
        userOptionQuery,
        selectedUserId,
        isAdminSelected
    ) { search, important, userOption, userId, isAdminSelected ->
        UserFilterPreferences(search, important, userOption, userId, isAdminSelected)
    }

    val uiState: StateFlow<AdminMessageUiState> = combine(
        // Subtle but important feature of flatMapLatest:
        // if a new trigger value comes down _loadTrigger (e.g. attempt 1 ---> attempt 2),
        // it cancels the previous database/network stream execution and starts a fresh one.
        // Standard combine does not cancel in-flight work when values change!
        _loadTrigger.flatMapLatest { getCachedMessagesUseCase() },
        _loadTrigger.flatMapLatest { getAdminUserInformationUseCase() },
        _isRefreshing,
        filterPreferencesFlow
    ) { resourceResult, allUsersResult, isRefreshing, filterPreferences ->//, query, isImportantOnly ->
        // 1. Bundle up the raw values as they emit
        MessageInput(
            resourceResult,
            allUsersResult,
            isRefreshing,
            filterPreferences.searchQuery,
            filterPreferences.userOptionQuery,
            filterPreferences.isImportantOnly,
            filterPreferences.selectedUserId,
            filterPreferences.isAdminSelected
        )
    }.scan<MessageInput, AdminMessageUiState>(
        // 2. Set the initial state before any emission occurs
        initial = AdminMessageUiState.Loading
    ) // trailing Slot API accumulator operation of scan()
    { previousState, input ->

        // smart-cast variables to individual values
        val (
            resourceResult,
            allUsersResult,
            isRefreshing,
            query,
            userOptionQuery,
            isImportantOnly,
            selectedUserId,
            isAdminSelected
        ) = input

        // ViewModel only worries about user text filtering on top of the clean data!
        when (resourceResult) {
            is Resource.Loading -> {
                // IF we are refreshing, do NOT drop back to full-screen Loading!
                // Preserve the existing list (if available) or keep Success state active.
                val previousSuccess = previousState as? AdminMessageUiState.Success
                if (isRefreshing && previousSuccess != null) {
                    // carry forward the previous Success state list, keeping the spinner alive
                    previousSuccess.copy(isRefreshing = true)
                } else if (_loadTrigger.value > 0) {
                    AdminMessageUiState.Retrying(_loadTrigger.value)
                } else {
                    AdminMessageUiState.Loading
                }
            }

            is Resource.Error -> {
                // Preserve the existing list (if available) or keep Success state active.
                val previousSuccess = previousState as? AdminMessageUiState.Success
                if (isRefreshing && previousSuccess != null) {
                    // carry forward the previous Success state list, keeping the spinner alive
                    previousSuccess.copy(isRefreshing = true)
                } else {
                    AdminMessageUiState.Error(resourceResult.message)
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

                val allUserList = (allUsersResult as? Resource.Success)?.data
                var userOptions: List<UserUiDetail>? = null
                if (allUserList != null) {
                    userOptions = allUserList
                        .filter { userOption ->
                            userOption.name.contains(userOptionQuery, ignoreCase = true)
                        }
                        .map { userOptionDomainModel ->
                            userOptionDomainModel.toUserUiDetail()
                        }
                }

                AdminMessageUiState.Success(
                    list = filteredList,
                    userOptions = userOptions,
                    isRefreshing = isRefreshing,
                    isImportantOnly = isImportantOnly,
                    isAdminSelected = isAdminSelected
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdminMessageUiState.Loading
    )

    val uiEvents = getMessageEventsUseCase()
        .map { messageDomainEvent ->
            messageDomainEvent.toMessageUiEvent()
        }

    init {
        // Initialize messages
        if (selectedUserId.value == null) {
            loadAdminMessageData()
        } else {
            loadSelectedMessageData()
        }
        // Initialize user information for SearchBar
        loadAllUsersData()
    }

    private fun loadAdminMessageData() {
        viewModelScope.launch {
            loadActiveMessagesUseCase(onRetry = { attempt ->
                _loadTrigger.value = attempt
            })
        }
    }

    private fun loadAllUsersData() {
        viewModelScope.launch {
            loadAllUsersUseCase(onRetry = { attempt ->
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

        // TODO refresh with selected user ID or default active (admin) user ID
        // TODO test this on my device at home - emulator cannot pull-to-refresh

        viewModelScope.launch {
            _isRefreshing.value = true // turn ON refreshing
            try {

                val currentUserId: Int? = selectedUserId.value

                if (currentUserId == null) {
                    loadActiveMessagesUseCase(onRetry = { attempt ->
                        _loadTrigger.value = attempt
                    }) // suspending call - wait until done
                } else {
                    loadSelectedMessagesUseCase(
                        currentUserId,
                        onRetry = { attempt ->
                            _loadTrigger.value = attempt
                        })
                }
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

    fun onUserOptionQueryChanged(newQuery: String) {
        savedStateHandle[KEY_USER_OPTION_QUERY] = newQuery
    }

    fun onUserOptionClicked(newUserId: Int, newIsAdmin: Boolean) {
        savedStateHandle[KEY_SELECTED_USER_ID] = newUserId
        savedStateHandle[KEY_IS_ADMIN_SELECTED] = newIsAdmin
    }

    fun loadSelectedMessageData() {
        val newUserId: Int = savedStateHandle[KEY_SELECTED_USER_ID] ?: return
        viewModelScope.launch {
            loadSelectedMessagesUseCase(
                newUserId,
                onRetry = { attempt ->
                    _loadTrigger.value = attempt
                })
        }
    }

}

private data class MessageInput(
    val resource: Resource<List<MessageDomainModel>>,
    val allUsersResult: Resource<List<UserOptionDomainModel>?>,
    val isRefreshing: Boolean,
    val query: String,
    val userOptionQuery: String,
    val isImportantOnly: Boolean,
    val selectedUserId: Int?,
    val isAdminSelected: Boolean
)

data class UserFilterPreferences(
    val searchQuery: String,
    val isImportantOnly: Boolean,
    val userOptionQuery: String,
    val selectedUserId: Int?,
    val isAdminSelected: Boolean
)