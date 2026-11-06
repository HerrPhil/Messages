package com.reference.implementation.messages.presentation.screens.adminmessage

import androidx.lifecycle.SavedStateHandle
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.model.UserOptionDomainModel
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.GetAdminUserInformationUseCase
import com.reference.implementation.domain.use_case.GetMessageEventsUseCase
import com.reference.implementation.domain.use_case.GetMessagesForActiveUserUseCase
import com.reference.implementation.domain.use_case.GetMessagesForAdminToViewUseCase
import com.reference.implementation.domain.use_case.GetMessagesForSelectedUserUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsNotImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.domain.use_case.RefreshMessagesUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.domain.use_case.RestoreMessageUseCase
import com.reference.implementation.messages.presentation.screens.message.toMessageUiDetail
import com.reference.implementation.messages.presentation.screens.util.FakeRefreshMessagesUseCase
import com.reference.implementation.messages.presentation.screens.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
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

    private val refreshMessagesUseCase: RefreshMessagesUseCase = mockk(relaxed = true)
    private val getMessagesForActiveUserUseCase: GetMessagesForActiveUserUseCase =
        mockk(relaxed = true)
    private val getMessagesForSelectedUserUseCase: GetMessagesForSelectedUserUseCase =
        mockk(relaxed = true)
    private val getAdminUserInformationUseCase: GetAdminUserInformationUseCase = mockk()
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase = mockk(relaxed = true)
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase = mockk(relaxed = true)
    private val deleteMessageUseCase: DeleteMessageUseCase = mockk(relaxed = true)
    private val restoreMessageUseCase: RestoreMessageUseCase = mockk(relaxed = true)
    private val markMessageAsImportantUseCase: MarkMessageAsImportantUseCase = mockk(relaxed = true)
    private val markMessageAsNotImportantUseCase: MarkMessageAsNotImportantUseCase =
        mockk(relaxed = true)
    private val getMessageEventsUseCase: GetMessageEventsUseCase = mockk(relaxed = true)

    private val adminUserId = 678345
    private val adminUserName = "sysadmin"
    private val regularUserId = 567234
    private val regularUserName = "user"

    private lateinit var adminMessageViewModel: AdminMessageViewModel
    private lateinit var getMessagesForAdminToViewUseCase: GetMessagesForAdminToViewUseCase

    @Before
    fun setUp() {
        getMessagesForAdminToViewUseCase = GetMessagesForAdminToViewUseCase(
            getMessagesForActiveUserUseCase,
            getMessagesForSelectedUserUseCase
        )
    }

    @Test
    fun `init fetches active user messages from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

            // Wake up WhileSubscribed - add active subscriber to mimic UI
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

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 0) { getMessagesForSelectedUserUseCase(any()) }
        }

    @Test
    fun `init fetches selected admin messages from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages() // both active and selected messages
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

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
            // Expect this to trigger selectedUserId.flatMapLatest {} to reload messages!
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

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForSelectedUserUseCase(eq(adminUserId)) }
        }

    @Test
    fun `init fetches selected user messages from use case and immediately collects success data`() =
        runTest {

            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            // app user selects regular user from user options
            // simulate user item onclick selection in dropdown after landing on screen
            adminMessageViewModel.onUserOptionClicked(regularUserId, false)
            adminMessageViewModel.onUserOptionQueryChanged("user")
            // Expect this to trigger selectedUserId.flatMapLatest {} to reload messages!
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

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForSelectedUserUseCase(eq(regularUserId)) }
        }

    @Test
    fun `init fetches important active user messages from use case and immediately collects success data`() =
        runTest {

            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            // Mimic the user clicking the important filter chip
            adminMessageViewModel.onImportantOnlyToggled(true)
            // Expect this to trigger combine() {} to reload messages!
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

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 0) { getMessagesForSelectedUserUseCase(any()) }
        }

    @Test
    fun `init fetches important selected user messages from use case and immediately collects success data`() =
        runTest {

            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

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
            // Expect this to trigger selectedUserId.flatMapLatest {} to reload messages!
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

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForSelectedUserUseCase(eq(regularUserId)) }
        }

    @Test
    fun `init fetches by search query active user messages from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Baseline Success Admin state is firmly established!

            adminMessageViewModel.onSearchChanged("upgrade")
            // Expect this to trigger combine() {} to reload messages!
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

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 0) { getMessagesForSelectedUserUseCase(any()) }
        }

    @Test
    fun `init fetches by search query selected user messages from use case and immediately collects success data`() =
        runTest {

            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

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
            // Expect this to trigger combine() {} to reload messages!
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

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForSelectedUserUseCase(any()) }
        }

    @Test
    fun `when pull to refresh is active and active messages use case emits Loading, scan block preserves previous success list`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
            val fakeRefreshMessagesUseCase = FakeRefreshMessagesUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = fakeRefreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

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
            mockActiveMessageRepositoryStream.value = Resource.Loading

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
            assertEquals(1, fakeRefreshMessagesUseCase.invocationCount)

            // expect 2 calls - init {} and onRefresh()
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 0) { getMessagesForSelectedUserUseCase(any()) }
        }

    @Test
    fun `init emits Loading UI state when use case stream initializes with Loading resource`() =
        runTest {
            // 1. Arrange: Seed your hot stream directly with the Loading state token
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Loading
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Loading
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

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

            // expect 2 calls - init {} and onRefresh()
            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 0) { getMessagesForSelectedUserUseCase(any()) }
        }

    @Test
    fun `when pull to refresh is active and active messages use case emits Error, scan block preserves previous success list`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessages = createActiveMessages()
            val mockSelectedMessages = createSelectedMessages()
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockActiveMessages)
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Success(data = mockSelectedMessages)
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
            val fakeRefreshMessagesUseCase = FakeRefreshMessagesUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            adminMessageViewModel = AdminMessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = fakeRefreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // 💡 THE SENIOR TIMELINE FIX:
            // Step 2: Trigger the refresh gesture FIRST. This locks _isRefreshing to true inside the delay.
            adminMessageViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // Step 3: Now that isRefreshing is safely locked to true, drop the stream to Error!
            mockActiveMessageRepositoryStream.value = Resource.Error("Network Timeout")

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
            assertEquals(1, fakeRefreshMessagesUseCase.invocationCount)

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 0) { getMessagesForSelectedUserUseCase(any()) }
        }

    @Test
    fun `init emits Error UI state when use case stream initializes with Error resource`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            val mockActiveMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Error("Cache Fail")
                )
            val mockSelectedMessageRepositoryStream =
                MutableStateFlow<Resource<List<MessageDomainModel>>>(
                    Resource.Error("Cache Fail")
                )
            every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
            every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
            // StateFlow selectedUserId.value remains null!

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                adminMessageViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            assertEquals(
                expected = AdminMessageUiState.Error("Cache Fail"),
                actual = adminMessageViewModel.uiState.value,
            )

            @Suppress("UnusedFlow")
            coVerify(exactly = 1) { getMessagesForActiveUserUseCase() }
            @Suppress("UnusedFlow")
            coVerify(exactly = 0) { getMessagesForSelectedUserUseCase(any()) }

        }

    @Test
    fun `on delete message calls a use case`() =
        runTest {
            // 1. Arrange: Start with a populated lists in Success State
            arrange()

            // no selected user = active user = admin
            // StateFlow selectedUserId.value remains null!

            // act
            adminMessageViewModel.onDeleteMessage(789456)

            // assert
            coVerify(exactly = 1) { deleteMessageUseCase(eq(789456)) }
        }

    @Test
    fun `on Restore message calls a use case`() =
        runTest {
            // Arrange
            arrange()

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

            // Act
            adminMessageViewModel.onRestoreMessage(messageDomainModel)

            // Assert
            coVerify(exactly = 1) { restoreMessageUseCase(eq(messageDomainModel)) }
        }

    @Test
    fun `on toggle read status will mark message as read`() =
        runTest {
            // Arrange
            arrange()

            // Act
            adminMessageViewModel.onToggleReadStatus(567234, true)

            // Assert
            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(567234)) }
        }

    @Test
    fun `on toggle read status will mark message as unread`() =
        runTest {
            // Arrange
            arrange()

            // Act
            adminMessageViewModel.onToggleReadStatus(567234, false)

            // Assert
            coVerify(exactly = 1) { markMessageAsUnreadUseCase(eq(567234)) }
        }

    @Test
    fun `on toggle important message click will mark message as important`() =
        runTest {
            // Arrange
            arrange()

            // Act
            adminMessageViewModel.onToggleImportantMessageClicked(123456, true)

            // Assert
            coVerify(exactly = 1) { markMessageAsImportantUseCase(eq(123456)) }
        }

    @Test
    fun `on toggle important message click will mark message as not important`() =
        runTest {
            // Arrange
            arrange()

            // Act
            adminMessageViewModel.onToggleImportantMessageClicked(123456, false)

            // Assert
            coVerify(exactly = 1) { markMessageAsNotImportantUseCase(eq(123456)) }

        }

    private fun arrange() {
        // 1. Arrange: Start with a populated lists in Success State
        val mockActiveMessages = createActiveMessages()
        val mockSelectedMessages = createSelectedMessages()
        val mockActiveMessageRepositoryStream =
            MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
        val mockSelectedMessageRepositoryStream =
            MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockSelectedMessages)
            )
        every { getMessagesForActiveUserUseCase() } returns mockActiveMessageRepositoryStream
        every { getMessagesForSelectedUserUseCase(any()) } returns mockSelectedMessageRepositoryStream

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
            refreshMessagesUseCase = refreshMessagesUseCase,
            getMessagesForAdminToViewUseCase = getMessagesForAdminToViewUseCase,
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
        // StateFlow selectedUserId.value remains null!
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
                id = 456123,
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
