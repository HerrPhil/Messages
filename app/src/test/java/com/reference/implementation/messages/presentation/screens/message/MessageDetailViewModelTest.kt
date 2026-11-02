package com.reference.implementation.messages.presentation.screens.message

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.GetMessageByIdUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import com.reference.implementation.messages.presentation.screens.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)

class MessageDetailViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getMessageByIdUseCase: GetMessageByIdUseCase = mockk()
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase = mockk(relaxed = true)
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase = mockk(relaxed = true)
    private val deleteMessageUseCase: DeleteMessageUseCase = mockk(relaxed = true)
    private val regularUserId = 567234

    private lateinit var messageDetailViewModel: MessageDetailViewModel

    @Test
    fun `init fetches message ID from SavedStateHandle and immediately collects success state`() =
        runTest {
            // Arrange
            val messageId = 678345
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Success(data = mockActiveMessages[1]) // message with messageId 678345
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )
            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert
            val expectedDetail = mockActiveMessages[1].toMessageUiDetail()
            val expectedState = MessageDetailUiState.Success(data = expectedDetail)
            assertEquals(
                expected = expectedState,
                actual = messageDetailViewModel.uiState.value,
                "Success state matched"
            )

            // Business rule: when a user views a message then it is marked as read
            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(messageId)) }
        }

    @Test
    fun `init immediately emits Loading UI state when use case is mid-flight`() =
        runTest {
            val messageId = 678345

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // THE TRICK: Use the flow {} builder to emit Loading,
            // then call awaitCancellation() to stall the stream in-flight!
            val stallingFlow = flow<Resource<MessageDomainModel?>> {
                emit(Resource.Loading)
                kotlinx.coroutines.awaitCancellation()
            }
            every { getMessageByIdUseCase(messageId) } returns stallingFlow

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )
            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert
            assertEquals(
                expected = MessageDetailUiState.Loading,
                actual = messageDetailViewModel.uiState.value,
                "Loading state matched"
            )

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(messageId)) }
        }


    @Test
    fun `when use case emits error resource, uiState transforms to Error state immediately`() =
        runTest {
            // Arrange
            val messageId = 678345
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Error("Messages failed to load")
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )
            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert
            val expectedState = MessageDetailUiState.Error("Messages failed to load")
            assertEquals(
                expected = expectedState,
                actual = messageDetailViewModel.uiState.value,
                "Error state matched"
            )

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(messageId)) }
        }

    @Test
    fun `when use case emits error resource if ID not found and there is no Room cache data, uiState transforms to Error state immediately`() =
        runTest {
            // Arrange
            val messageId = 999999 // look for a message that does not exist
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Error("Unable to load message details.") // mimic no repo data in use case
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )
            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert
            val expectedState = MessageDetailUiState.Error("Unable to load message details.")
            assertEquals(
                expected = expectedState,
                actual = messageDetailViewModel.uiState.value,
                "Error state matched"
            )

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(messageId)) }
        }

    @Test
    fun `when use case emits error resource if ID not found and there is Room cache data, uiState transforms to Success of previous UI state`() =
        runTest {
            // Arrange
            val messageId = 999999 // look for a message that does not exist
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Success(data = mockActiveMessages[0]) // any message <> 999999
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )
            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert
            val expectedState =
                MessageDetailUiState.Success(data = mockActiveMessages[0].toMessageUiDetail())
            assertEquals(
                expected = expectedState,
                actual = messageDetailViewModel.uiState.value,
                "Error state matched"
            )

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(messageId)) }
        }

    @Test
    fun `when use case emits success resource has null data from repo (Room), uiState transforms to message deleted back navigate`() =
        runTest {
            // Arrange
            val messageId = 999999 // look for a message that does not exist
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Success(data = null) // another process removed message from Room
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )
            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                messageDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert
            val expectedState = MessageDetailUiState.MessageDeletedBackNavigate
            assertEquals(
                expected = expectedState,
                actual = messageDetailViewModel.uiState.value,
                "message deleted back navigate now"
            )

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(messageId)) }
        }

    @Test
    fun `on deleted message call the correct use case`() =
        runTest {
            val messageId = 789456 // message to be deleted
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Success(data = mockActiveMessages[0]) // [0] = 789456
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )

            messageDetailViewModel.onDeleteMessage(messageId)

            coVerify(exactly = 1) { deleteMessageUseCase(eq(messageId)) }
        }

    @Test
    fun `on toggle message as read call the correct use case`() =
        runTest {
            val messageId = 789456 // message to be marked as READ
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Success(data = mockActiveMessages[0]) // [0] = 789456
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute


            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )

            messageDetailViewModel.onToggleReadStatus(messageId, true)

            // one for init, one for onToggleReadStatus
            coVerify(exactly = 2) { markMessageAsReadUseCase(eq(messageId)) }
        }

    @Test
    fun `on toggle message as unread call the correct use case`() =
        runTest {
            val messageId = 789456 // DOES NOT EXIST
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<MessageDomainModel?>>(
                Resource.Success(data = mockActiveMessages[0]) // [0] = 789456
            )
            every { getMessageByIdUseCase(messageId) } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = messageId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute


            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getMessageByIdUseCase = getMessageByIdUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )

            messageDetailViewModel.onToggleReadStatus(messageId, false)

            coVerify(exactly = 1) { markMessageAsUnreadUseCase(eq(messageId)) }
        }

    private fun createActiveMessages(): List<MessageDomainModel> =
        listOf(
            MessageDomainModel(
                id = 789456,
                subject = "project meeting",
                body = "plan epic",
                read = false,
                userId = regularUserId,
                createdAt = "2026-07-13T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-13T22:28:56.321Z"),
                isImportant = true
            ),
            MessageDomainModel(
                id = 678345,
                subject = "story planning",
                body = "brainstorm",
                read = true,
                userId = regularUserId,
                createdAt = "2026-07-12T22:28:56.321Z",
                createdAtInstant = Instant.parse("2026-07-12T22:28:56.321Z"),
                isImportant = false
            )
        )
}