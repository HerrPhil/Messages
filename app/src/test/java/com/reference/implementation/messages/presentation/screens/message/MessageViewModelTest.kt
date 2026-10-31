package com.reference.implementation.messages.presentation.screens.message

import androidx.lifecycle.SavedStateHandle
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.GetMessageEventsUseCase
import com.reference.implementation.domain.use_case.GetMessagesForActiveUserUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsNotImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.domain.use_case.RefreshMessagesUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.domain.use_case.RestoreMessageUseCase
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
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MessageViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val refreshMessagesUseCase: RefreshMessagesUseCase = mockk(relaxed = true)
    private val getMessagesForActiveUserUseCase: GetMessagesForActiveUserUseCase = mockk()
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase = mockk(relaxed = true)
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase = mockk(relaxed = true)
    private val deleteMessageUseCase: DeleteMessageUseCase = mockk(relaxed = true)
    private val restoreMessageUseCase: RestoreMessageUseCase = mockk(relaxed = true)
    private val markMessageAsImportantUseCase: MarkMessageAsImportantUseCase = mockk(relaxed = true)
    private val markMessageAsNotImportantUseCase: MarkMessageAsNotImportantUseCase =
        mockk(relaxed = true)

    // do not care about events for combine/scan tests - can make it strict locally in event test
    private val getMessageEventsUseCase: GetMessageEventsUseCase = mockk(relaxed = true)

    private val regularUserId = 567234

    private lateinit var messageViewModel: MessageViewModel

    @Test
    fun `getMessagesForActiveUserUseCase fetches active user messages and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageViewModel.uiState.collect {}
            }

            // THE FIX: Force the unconfirmed scheduler to flush any pending lambda inside the
            // combine/scan pipeline immediately!
            runCurrent()

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = MessageUiState.Success(
                list = mockActiveMessages.map { it.toMessageUiDetail() },
                isRefreshing = false,
                isImportantOnly = false
            )

            assertEquals(
                expected = expectedState,
                actual = messageViewModel.uiState.value
            )

            coVerify(exactly = 1) {
                val unusedFlow = getMessagesForActiveUserUseCase()
            }
        }

    @Test
    fun `getMessagesForActiveUserUseCase fetches important active user messages and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageViewModel.uiState.collect {}
            }

            // THE FIX: Force the unconfirmed scheduler to flush any pending lambda inside the
            // combine/scan pipeline immediately!
            runCurrent()

            // Mimic the user clicking the important filter chip.
            messageViewModel.onImportantOnlyToggled(true)
            runCurrent() // Flush the combine/scan pipeline

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = MessageUiState.Success(
                list = mockActiveMessages
                    .filter { it.isImportant }
                    .map { it.toMessageUiDetail() },
                isRefreshing = false,
                isImportantOnly = true
            )
            assertEquals(
                expected = expectedState,
                actual = messageViewModel.uiState.value
            )

            coVerify(exactly = 1) {
                val unusedFlow = getMessagesForActiveUserUseCase()
            }
        }

    @Test
    fun `getMessagesForActiveUserUseCase fetches by search query active user messages and immediately collects success data`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageViewModel.uiState.collect {}
            }

            // THE FIX: Force the unconfirmed scheduler to flush any pending lambda inside the
            // combine/scan pipeline immediately!
            runCurrent()

            messageViewModel.onSearchChanged("upgrade")
            runCurrent()

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = MessageUiState.Success(
                list = mockActiveMessages
                    .filter { it.body.contains("upgrade") }
                    .map { it.toMessageUiDetail() },
                isRefreshing = false,
                isImportantOnly = false
            )
            assertEquals(
                expected = expectedState,
                actual = messageViewModel.uiState.value
            )

            coVerify(exactly = 1) {
                val unusedFlow = getMessagesForActiveUserUseCase()
            }
        }

    @Test
    fun `when pull to refresh is active and refresh messages use case emits Loading, scan block preserves previous success list`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Instantiate your handwritten fake contract
            val fakeRefreshMessagesUseCase = FakeRefreshMessagesUseCase()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = fakeRefreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageViewModel.uiState.collect {}
            }

            // 💡 THE SENIOR TIMELINE FIX:
            // Step 2: Trigger the refresh gesture FIRST. This locks _isRefreshing to true inside the delay.
            messageViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // Step 3: Now that isRefreshing is safely locked to true, drop the stream to Loading!
            mockMessageRepositoryStream.value = Resource.Loading

            // Step 4: Advance the clock slightly to process the composite stream mapping.
            // Important: Advance less than 1000 milliseconds of the delay in the
            // fakeRefreshMessagesUseCase, called by onRefresh()!
            advanceTimeBy(500.milliseconds)

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = MessageUiState.Success(
                list = mockActiveMessages.map { it.toMessageUiDetail() },
                isRefreshing = true, // VALUE UNDER TEST
                isImportantOnly = false
            )
            assertEquals(
                expected = expectedState,
                actual = messageViewModel.uiState.value
            )

            assertEquals(1, fakeRefreshMessagesUseCase.invocationCount)
        }

    @Test
    fun `getMessagesForActiveUserUseCase emits Loading UI state when use case stream initializes with Loading resource`() =
        runTest {
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Loading
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageViewModel.uiState.collect {}
            }

            // THE FIX: Force the unconfirmed scheduler to flush any pending lambda inside the
            // combine/scan pipeline immediately!
            runCurrent() // Success state is firmly established as 'previousState'

            // Assert
            assertEquals(
                MessageUiState.Loading,
                messageViewModel.uiState.value
            )
        }

    @Test
    fun `when pull to refresh is active and refresh messages use case emits Error, scan block preserves previous success list`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Instantiate your handwritten fake contract
            val fakeRefreshMessagesUseCase = FakeRefreshMessagesUseCase()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = fakeRefreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageViewModel.uiState.collect {}
            }

            // 💡 THE SENIOR TIMELINE FIX:
            // Step 2: Trigger the refresh gesture FIRST. This locks _isRefreshing to true inside the delay.
            messageViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // Step 3: Now that isRefreshing is safely locked to true, drop the stream to Error!
            mockMessageRepositoryStream.value = Resource.Error("Network Timeout")

            // Step 4: Advance the clock slightly to process the composite stream mapping.
            // Important: Advance less than 1000 milliseconds of the delay in the
            // fakeRefreshMessagesUseCase, called by onRefresh()!
            advanceTimeBy(500.milliseconds)

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = MessageUiState.Success(
                list = mockActiveMessages.map { it.toMessageUiDetail() },
                isRefreshing = true, // VALUE UNDER TEST
                isImportantOnly = false
            )
            assertEquals(
                expected = expectedState,
                actual = messageViewModel.uiState.value
            )

            assertEquals(1, fakeRefreshMessagesUseCase.invocationCount)
        }

    @Test
    fun `getMessagesForActiveUserUseCase emits Error UI state when use case stream initializes with Error resource`() =
        runTest {
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Error("Room Fail")
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageViewModel.uiState.collect {}
            }

            // THE FIX: Force the unconfirmed scheduler to flush any pending lambda inside the
            // combine/scan pipeline immediately!
            runCurrent() // Success state is firmly established as 'previousState'

            // Assert
            assertEquals(
                expected = MessageUiState.Error("Room Fail"),
                messageViewModel.uiState.value
            )
        }

    @Test
    fun `on delete message calls a use case`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            // act
            messageViewModel.onDeleteMessage(messageId = 789456)

            // assert
            coVerify(exactly = 1) { deleteMessageUseCase(messageId = eq(789456)) }
        }

    @Test
    fun `on restore message calls a use case`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

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
            messageViewModel.onRestoreMessage(messageDomainModel)

            // assert
            coVerify(exactly = 1) { restoreMessageUseCase(eq(messageDomainModel)) }
        }

    @Test
    fun `on toggle read status will mark message as read`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            messageViewModel.onToggleReadStatus(messageId = 567234, true)

            coVerify(exactly = 1) { markMessageAsReadUseCase(messageId = eq(567234)) }
        }

    @Test
    fun `on toggle read status will mark message as unread`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            messageViewModel.onToggleReadStatus(messageId = 567234, false)

            coVerify(exactly = 1) { markMessageAsUnreadUseCase(messageId = eq(567234)) }
        }

    @Test
    fun `on toggle important message click will mark message as important`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            messageViewModel.onToggleImportantMessageClicked(messageId = 123456, true)

            coVerify(exactly = 1) { markMessageAsImportantUseCase(messageId = eq(123456)) }
        }

    @Test
    fun `on toggle important message click will mark message as not important`() =
        runTest {
            // Arrange
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getMessagesForActiveUserUseCase() } returns mockMessageRepositoryStream

            // This map mimics exactly what Compose Navigation build behind the scenes
            // There is no navigation value passed in - this ViewModel uses SavedStateHandle
            // as lifecycle-aware storage of user-selected values on the UI
            val savedStateHandle = SavedStateHandle()

            // Act - create the ViewModel
            messageViewModel = MessageViewModel(
                savedStateHandle = savedStateHandle,
                refreshMessagesUseCase = refreshMessagesUseCase,
                getMessagesForActiveUserUseCase = getMessagesForActiveUserUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase,
                restoreMessageUseCase = restoreMessageUseCase,
                markMessageAsImportantUseCase = markMessageAsImportantUseCase,
                markMessageAsNotImportantUseCase = markMessageAsNotImportantUseCase,
                getMessageEventsUseCase = getMessageEventsUseCase
            )

            // user selected is inherent authenticated user

            messageViewModel.onToggleImportantMessageClicked( messageId = 123456, false)

            coVerify(exactly = 1) { markMessageAsNotImportantUseCase(messageId = eq(123456)) }
        }

    private fun createActiveMessages(): List<MessageDomainModel> =
        listOf(
            MessageDomainModel(
                id = 789456,
                subject = "software upgrade",
                body = "there is an upgrade",
                read = false,
                userId = regularUserId,
                createdAt = "2026-07-13T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-13T22:28:56.321Z"),
                isImportant = true
            ),
            MessageDomainModel(
                id = 678345,
                subject = "server outage",
                body = "there is an outage",
                read = true,
                userId = regularUserId,
                createdAt = "2026-07-12T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-12T22:28:56.321Z"),
                isImportant = false
            )
        )

}