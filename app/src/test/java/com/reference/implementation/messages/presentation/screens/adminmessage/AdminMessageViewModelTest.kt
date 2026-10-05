package com.reference.implementation.messages.presentation.screens.adminmessage

import androidx.lifecycle.SavedStateHandle
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.UserOptionDomainModel
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.GetAdminUserInformationUseCase
import com.reference.implementation.domain.use_case.GetCachedMessagesUseCase
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
import com.reference.implementation.messages.presentation.screens.bulletin.FakeLoadActiveMessagesUseCase
import com.reference.implementation.messages.presentation.screens.bulletin.FakeLoadSelectedMessagesUseCase
import com.reference.implementation.messages.presentation.screens.bulletin.MainDispatcherRule
import com.reference.implementation.messages.presentation.screens.message.toMessageUiDetail
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AdminMessageViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loadActiveMessagesUseCase: LoadActiveMessagesUseCase = mockk(relaxed = true)
    private val loadSelectedMessagesUseCase: LoadSelectedMessagesUseCase = mockk(relaxed = true)
    private val getCachedMessagesUseCase: GetCachedMessagesUseCase = mockk()
    private val loadAllUsersUseCase: LoadAllUsersUseCase = mockk(relaxed = true)
    private val getAdminUserInformationUseCase: GetAdminUserInformationUseCase = mockk()
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase = mockk(relaxed = true)
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase = mockk(relaxed = true)
    private val deleteMessageUseCase: DeleteMessageUseCase = mockk(relaxed = true)
    private val restoreMessageUseCase: RestoreMessageUseCase = mockk(relaxed = true)
    private val markMessageAsImportantUseCase: MarkMessageAsImportantUseCase = mockk(relaxed = true)
    private val markMessageAsNotImportantUseCase: MarkMessageAsNotImportantUseCase =
        mockk(relaxed = true)

    // do not care about events for combine/scan tests - can make it strict locally in event test
    private val getMessageEventsUseCase: GetMessageEventsUseCase = mockk(relaxed = true)

    private val adminUserId = 678345
    private val adminUserName = "sysadmin"
    private val regularUserId = 567234
    private val regularUserName = "user"

    private lateinit var adminMessageViewModel: AdminMessageViewModel

    @Test
    fun `init fetches active user messages from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - calls init() inherently
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // no selected user = active user = admin

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }

            // THE FIX: Force the unconfined scheduler to flush any pending lambda
            // inside the combine/scan pipeline immediately!
            runCurrent()

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = AdminMessageUiState.Success(
                list = mockActiveMessages.map { it.toMessageUiDetail() },
                userOptions = mockUserInformation.map { it.toUserUiDetail() },
                isRefreshing = false,
                isImportantOnly = false,
                isAdminSelected = true // defaults to true for authenticated admin user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )

            coVerify(exactly = 1) { loadActiveMessagesUseCase(any())}
            coVerify(exactly = 1) { loadAllUsersUseCase(any()) }
        }

    @Test
    fun `init fetches selected admin messages from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - calls init() inherently
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }

            // THE FIX: Force the unconfined scheduler to flush any pending lambda
            // inside the combine/scan pipeline immediately!
            runCurrent()

            // app user selects admin from user options
            // simulate user selection in dropdown after landing on screen
            adminMessageViewModel.onUserOptionClicked(adminUserId, true)
            adminMessageViewModel.onUserOptionQueryChanged(adminUserName)
            adminMessageViewModel.loadSelectedMessageData()
            runCurrent()

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = AdminMessageUiState.Success(
                list = mockActiveMessages.map { it.toMessageUiDetail() },
                userOptions = mockUserInformation
                    .filter { userOption ->
                        userOption.name.contains(adminUserName, ignoreCase = true)
                    }
                    .map { userOptionDomainModel ->
                        userOptionDomainModel.toUserUiDetail()
                    },
                isRefreshing = false,
                isImportantOnly = false,
                isAdminSelected = true // defaults to true for authenticated admin user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )

            coVerify(exactly = 1) { loadSelectedMessagesUseCase(eq(adminUserId), any())}
        }

    @Test
    fun `init fetches selected user messages from use case and immediately collects success data`() =
        runTest {

            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream
            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - calls init() inherently
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            // app user selects regular user from user options
            // simulate user item onclick selection in dropdown after landing on screen
            adminMessageViewModel.onUserOptionClicked(regularUserId, false)
            adminMessageViewModel.onUserOptionQueryChanged("user")

            // SWAP THE STREAM DATA: Push the new regular user payload down to the hot fake!
            mockMessageRepositoryStream.value = Resource.Success(data = mockSelectedMessages)

            adminMessageViewModel.loadSelectedMessageData()
            runCurrent() // Flush the combine/scan pipeline

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = AdminMessageUiState.Success(
                list = mockSelectedMessages.map { it.toMessageUiDetail() },
                userOptions = mockUserInformation
                    .filter { userOption ->
                        userOption.name.contains(regularUserName, ignoreCase = true)
                    }
                    .map { userOptionDomainModel ->
                        userOptionDomainModel.toUserUiDetail()
                    },
                isRefreshing = false,
                isImportantOnly = false,
                isAdminSelected = false // align with selected regular user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )

            coVerify(exactly = 1) {
                loadSelectedMessagesUseCase(eq(regularUserId), any())
            }
        }

    @Test
    fun `init fetches important active user messages from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream
            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - calls init() inherently
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // no selected user = active user = admin

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            adminMessageViewModel.onImportantOnlyToggled(true)
            runCurrent() // Flush the combine/scan pipeline

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = AdminMessageUiState.Success(
                list = mockActiveMessages
                    .filter { it.isImportant }
                    .map { it.toMessageUiDetail() },
                userOptions = mockUserInformation.map { it.toUserUiDetail() },
                isRefreshing = false,
                isImportantOnly = true,
                isAdminSelected = true // defaults to true for authenticated admin user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )
        }

    @Test
    fun `init fetches important selected user messages from use case and immediately collects success data`() =
        runTest {

            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream
            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - calls init() inherently
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            // app user selects regular user from user options
            // simulate user item onclick selection in dropdown after landing on screen
            adminMessageViewModel.onUserOptionClicked(regularUserId, false)
            adminMessageViewModel.onUserOptionQueryChanged("user")
            adminMessageViewModel.onImportantOnlyToggled(true)

            // SWAP THE STREAM DATA: Push the new regular user payload down to the hot fake!
            mockMessageRepositoryStream.value = Resource.Success(data = mockSelectedMessages)

            adminMessageViewModel.loadSelectedMessageData()
            runCurrent() // Flush the combine/scan pipeline

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = AdminMessageUiState.Success(
                list = mockSelectedMessages
                    .filter { it.isImportant }
                    .map { it.toMessageUiDetail() },
                userOptions = mockUserInformation
                    .filter { userOption ->
                        userOption.name.contains(regularUserName, ignoreCase = true)
                    }
                    .map { userOptionDomainModel ->
                        userOptionDomainModel.toUserUiDetail()
                    },
                isRefreshing = false,
                isImportantOnly = true,
                isAdminSelected = false // align with selected regular user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )

            coVerify(exactly = 1) {
                loadSelectedMessagesUseCase(eq(regularUserId), any())
            }
        }

    @Test
    fun `init fetches by search query active user messages from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - calls init() inherently
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // no selected user = active user = admin

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            adminMessageViewModel.onSearchChanged("upgrade")
            runCurrent() // Flush the combine/scan pipeline

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = AdminMessageUiState.Success(
                list = mockActiveMessages
                    .filter { it.body.contains("upgrade") }
                    .map { it.toMessageUiDetail() },
                userOptions = mockUserInformation.map { it.toUserUiDetail() },
                isRefreshing = false,
                isImportantOnly = false,
                isAdminSelected = true // defaults to true for authenticated admin user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )
        }

    @Test
    fun `init fetches by search query selected user messages from use case and immediately collects success data`() =
        runTest {

            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - calls init() inherently
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            // app user selects regular user from user options
            // simulate user item onclick selection in dropdown after landing on screen
            adminMessageViewModel.onUserOptionClicked(regularUserId, false)
            adminMessageViewModel.onUserOptionQueryChanged("user")
            adminMessageViewModel.onSearchChanged("meeting")

            // SWAP THE STREAM DATA: Push the new regular user payload down to the hot fake!
            mockMessageRepositoryStream.value = Resource.Success(data = mockSelectedMessages)

            adminMessageViewModel.loadSelectedMessageData()
            runCurrent() // Flush the combine/scan pipeline

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = AdminMessageUiState.Success(
                list = mockSelectedMessages
                    .filter { it.body.contains("meeting") }
                    .map { it.toMessageUiDetail() },
                userOptions = mockUserInformation
                    .filter { userOption ->
                        userOption.name.contains(regularUserName, ignoreCase = true)
                    }
                    .map { userOptionDomainModel ->
                        userOptionDomainModel.toUserUiDetail()
                    },
                isRefreshing = false,
                isImportantOnly = false,
                isAdminSelected = false // align with selected regular user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )

            coVerify(exactly = 1) {
                loadSelectedMessagesUseCase(eq(regularUserId), any())
            }
        }

    @Test
    fun `when pull to refresh is active and active messages use case emits Loading, scan block preserves previous success list`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Instantiate your handwritten fake contract
            val fakeLoadActiveMessagesUseCase = FakeLoadActiveMessagesUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = fakeLoadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // 💡 THE SENIOR TIMELINE FIX:
            // Step 2: Trigger the refresh gesture FIRST. This locks _isRefreshing to true inside the delay.
            adminMessageViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // Step 3: Now that isRefreshing is safely locked to true, drop the stream to Loading!
            mockMessageRepositoryStream.value = Resource.Loading

            // Step 4: Advance the clock slightly to process the composite stream mapping.
            // Important: Advance less than 1000 milliseconds of the delay in the
            // fakeLoadActiveMessagesUseCase, called by onRefresh()!
            advanceTimeBy(500.milliseconds)

            // Step 5. Assert: The scan block looks back at history, catches Success,
            // notices isRefreshing is true, and carries the list forward perfectly!
            val expectedState = AdminMessageUiState.Success(
                list = mockActiveMessages.map { it.toMessageUiDetail() },
                userOptions = mockUserInformation.map { it.toUserUiDetail() },
                isRefreshing = true, // Must be ON!
                isImportantOnly = false,
                isAdminSelected = true // defaults to true for authenticated admin user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )

            // expect 2 calls - init {} and onRefresh()
            assertEquals(2, fakeLoadActiveMessagesUseCase.invocationCount)
        }

    @Test
    fun `when pull to refresh activated on active user and use case emits Retrying`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Instantiate your handwritten fake contract
            val fakeLoadActiveMessagesUseCase = FakeLoadActiveMessagesUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = fakeLoadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // 3. Act Part 2: Update the repository stream value to Loading
            mockMessageRepositoryStream.value = Resource.Loading

            // 4. Act Step 3: Trigger the manual refresh gesture
            adminMessageViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // 5. Act Part 4: Manually fire your captured retry callback through the fake!
            // This executes: _loadTrigger.value = 1, forcing flatMapLatest to transition streams!
            val targetRetryCallback = checkNotNull(fakeLoadActiveMessagesUseCase.capturedOnRetry)
            targetRetryCallback.invoke(1)

            // 4. Assert: Verify your custom scan carrier rules!
            // The state should be Success with refreshing = true, keeping the list completely visible!
            val expectedState = AdminMessageUiState.Retrying(attempt = 1)

            assertEquals(expectedState, adminMessageViewModel.uiState.value)

            // expect 2 calls - init {} and onRefresh()
            assertEquals(2, fakeLoadActiveMessagesUseCase.invocationCount)

        }

    @Test
    fun `when pull to refresh activated on selected user and use case emits Retrying`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockSelectedMessages = createSelectedMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockSelectedMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Instantiate your handwritten fake contract
            val fakeLoadSelectedMessagesUseCase = FakeLoadSelectedMessagesUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = fakeLoadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // configure selected user
            adminMessageViewModel.onUserOptionClicked(regularUserId, false)
            adminMessageViewModel.onUserOptionQueryChanged("user")

            // 3. Act Part 2: Update the repository stream value to Loading
            mockMessageRepositoryStream.value = Resource.Loading

            // 4. Act Step 3: Trigger the manual refresh gesture
            adminMessageViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // 5. Act Part 4: Manually fire your captured retry callback through the fake!
            // This executes: _loadTrigger.value = 1, forcing flatMapLatest to transition streams!
            val targetRetryCallback = checkNotNull(fakeLoadSelectedMessagesUseCase.capturedOnRetry)
            targetRetryCallback.invoke(1)

            // 4. Assert: Verify your custom scan carrier rules!
            // The state should be Success with refreshing = true, keeping the list completely visible!
            val expectedState = AdminMessageUiState.Retrying(attempt = 1)

            assertEquals(expectedState, adminMessageViewModel.uiState.value)

            // expect 2 calls - init {} and onRefresh()
            coVerify(exactly = 1) { loadActiveMessagesUseCase(any()) }
            assertEquals(1, fakeLoadSelectedMessagesUseCase.invocationCount)
        }


    @Test
    fun `init emits Loading UI state when use case stream initializes with Loading resource`() =
        runTest {
            // 1. Arrange: Seed your hot stream directly with the Loading state token
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Loading
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // Assert
            assertEquals(
                AdminMessageUiState.Loading,
                adminMessageViewModel.uiState.value
            )
        }

    @Test
    fun `when pull to refresh is active and active messages use case emits Error, scan block preserves previous success list`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Instantiate your handwritten fake contract
            val fakeLoadActiveMessagesUseCase = FakeLoadActiveMessagesUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = fakeLoadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // 💡 THE SENIOR TIMELINE FIX:
            // Step 2: Trigger the refresh gesture FIRST. This locks _isRefreshing to true inside the delay.
            adminMessageViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // Step 3: Now that isRefreshing is safely locked to true, drop the stream to Loading!
            mockMessageRepositoryStream.value = Resource.Error("Network Timeout")

            // Step 4: Advance the clock slightly to process the composite stream mapping.
            // Important: Advance less than 1000 milliseconds of the delay in the
            // fakeLoadActiveMessagesUseCase, called by onRefresh()!
            advanceTimeBy(500.milliseconds)

            // Step 5. Assert: The scan block looks back at history, catches Success,
            // notices isRefreshing is true, and carries the list forward perfectly!
            val expectedState = AdminMessageUiState.Success(
                list = mockActiveMessages.map { it.toMessageUiDetail() },
                userOptions = mockUserInformation.map { it.toUserUiDetail() },
                isRefreshing = true, // Must be ON!
                isImportantOnly = false,
                isAdminSelected = true // defaults to true for authenticated admin user
            )

            assertEquals(
                expected = expectedState,
                actual = adminMessageViewModel.uiState.value
            )

            // expect 2 calls - init {} and onRefresh()
            assertEquals(2, fakeLoadActiveMessagesUseCase.invocationCount)
        }

    @Test
    fun `init emits Error UI state when use case stream initializes with Error resource`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Error("Cache Fail")
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()
            // Act Step 1: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            assertEquals(
                expected = AdminMessageUiState.Error("Cache Fail"),
                actual = adminMessageViewModel.uiState.value,
            )

        }

    @Test
    fun `on delete message calls a use case`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // act
            adminMessageViewModel.onDeleteMessage(789456)

            // assert
            coVerify(exactly = 1) { deleteMessageUseCase(eq(789456)) }
        }

    @Test
    fun `on Restore message calls a use case`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            val messageDomainModel = MessageDomainModel(
                id = 0,
                subject = "",
                body = "",
                read = false,
                userId = 456123,
                createdAt = "2026-07-13T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-13T22:28:56.321Z"),
                isImportant = false
            )

            // act
            adminMessageViewModel.onRestoreMessage(messageDomainModel)

            // assert
            coVerify(exactly = 1) { restoreMessageUseCase(eq(messageDomainModel)) }
        }

    @Test
    fun `on toggle read status will mark message as read`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            adminMessageViewModel.onToggleReadStatus(567234, true)

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(567234)) }
        }

    @Test
    fun `on toggle read status will mark message as unread`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            adminMessageViewModel.onToggleReadStatus(567234, false)

            coVerify(exactly = 1) { markMessageAsUnreadUseCase(eq(567234)) }
        }

    @Test
    fun `on toggle important message click will mark message as important`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            adminMessageViewModel.onToggleImportantMessageClicked(123456, true)

            coVerify(exactly = 1) { markMessageAsImportantUseCase(eq(123456)) }

        }

    @Test
    fun `on toggle important message click will mark message as not important`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            val mockUserInformation = createAllUsers()
            val mockUserRepositoryStream = MutableStateFlow<Resource<List<UserOptionDomainModel>>>(
                Resource.Success(data = mockUserInformation)
            )
            every { getAdminUserInformationUseCase() } returns mockUserRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                loadActiveMessagesUseCase = loadActiveMessagesUseCase,
                loadSelectedMessagesUseCase = loadSelectedMessagesUseCase,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                loadAllUsersUseCase = loadAllUsersUseCase,
                getAdminUserInformationUseCase = getAdminUserInformationUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            adminMessageViewModel.onToggleImportantMessageClicked(123456, false)

            coVerify(exactly = 1) { markMessageAsNotImportantUseCase(eq(123456)) }

        }

    private fun createActiveMessages(): List<MessageDomainModel> =
        listOf(
            MessageDomainModel(
                id = 789456,
                subject = "software upgrade",
                body = "there is an upgrade",
                read = false,
                userId = adminUserId,
                createdAt = "2026-07-13T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-13T22:28:56.321Z"),
                isImportant = true
            ),
            MessageDomainModel(
                id = 678345,
                subject = "server outage",
                body = "there is an outage",
                read = true,
                userId = adminUserId,
                createdAt = "2026-07-12T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-12T22:28:56.321Z"),
                isImportant = false
            )
        )

    private fun createSelectedMessages(): List<MessageDomainModel> =
        listOf(
            MessageDomainModel(
                id = 890567,
                subject = "review meeting",
                body = "there is a meeting",
                read = false,
                userId = regularUserId,
                createdAt = "2026-07-11T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-11T22:28:56.321Z"),
                isImportant = true
            ),
            MessageDomainModel(
                id = 789456,
                subject = "deploy app",
                body = "there is an app deployment",
                read = true,
                userId = regularUserId,
                createdAt = "2026-07-10T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-10T22:28:56.321Z"),
                isImportant = false
            )
        )

    private fun createAllUsers(): List<UserOptionDomainModel> =
        listOf(
            UserOptionDomainModel(
                id = adminUserId,
                name = adminUserName,
                isAdmin = true
            ),
            UserOptionDomainModel(
                id = regularUserId,
                name = regularUserName,
                isAdmin = false
            )
        )
}
