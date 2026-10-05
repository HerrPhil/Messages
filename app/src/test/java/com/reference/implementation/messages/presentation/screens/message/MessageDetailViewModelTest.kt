package com.reference.implementation.messages.presentation.screens.message

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.reference.implementation.domain.model.MessageDomainModel
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.GetCachedMessagesUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import com.reference.implementation.messages.presentation.screens.bulletin.MainDispatcherRule
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

    private val getCachedMessagesUseCase: GetCachedMessagesUseCase = mockk()
    private val markMessageAsReadUseCase: MarkMessageAsReadUseCase = mockk(relaxed = true)
    private val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase = mockk(relaxed = true)
    private val deleteMessageUseCase: DeleteMessageUseCase = mockk(relaxed = true)
    private val regularUserId = 567234

    private lateinit var messageDetailViewModel: MessageDetailViewModel

    @Test
    fun `init fetches message ID from SavedStateHandle and immediately collects success state`() =
        runTest {
            // Arrange
            val testId = 678345
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = testId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
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
            val expectedDetail = mockActiveMessages
                .filter { it.id == testId }
                .map { it.toMessageUiDetail() }[0]
            val expectedState = MessageDetailUiState.Success(data = expectedDetail)
            assertEquals(
                expected = expectedState,
                actual = messageDetailViewModel.uiState.value,
                "Success state matched"
            )

            // Business rule: when a user views a message then it is marked as read
            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(testId)) }
        }

    @Test
    fun `init immediately emits Loading UI state when use case is mid-flight`() =
        runTest {
            val testId = 678345

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = testId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // THE TRICK: Use the flow {} builder to emit Loading,
            // then call awaitCancellation() to stall the stream in-flight!
            val stallingFlow = flow<Resource<List<MessageDomainModel>>> {
                emit(Resource.Loading)
                kotlinx.coroutines.awaitCancellation()
            }
            every { getCachedMessagesUseCase() } returns stallingFlow

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
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

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(testId)) }
        }


    @Test
    fun `when use case emits error resource, uiState transforms to Error state immediately`() =
        runTest {
            // Arrange
            val testId = 678345
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Error("Messages failed to load")
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = testId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
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

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(testId)) }
        }

    @Test
    fun `when use case emits error resource if ID not found, uiState transforms to Error state immediately`() =
        runTest {
            // Arrange
            val testId = 999999 // DOES NOT EXIST
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = testId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
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
            val expectedState = MessageDetailUiState.Error("Message not found")
            assertEquals(
                expected = expectedState,
                actual = messageDetailViewModel.uiState.value,
                "Error state matched"
            )

            coVerify(exactly = 1) { markMessageAsReadUseCase(eq(testId)) }
        }

    @Test
    fun `on deleted message call the correct use case`() =
        runTest {
            val testId = 789456 // DOES NOT EXIST
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = testId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute

            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )

            messageDetailViewModel.onDeleteMessage(testId)

            coVerify(exactly = 1) { deleteMessageUseCase(eq(testId)) }
        }

    @Test
    fun `on toggle message as read call the correct use case`() =
        runTest {
            val testId = 789456 // DOES NOT EXIST
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = testId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute


            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )

            messageDetailViewModel.onToggleReadStatus(testId, true)

            // one for init, one for onToggleReadStatus
            coVerify(exactly = 2) { markMessageAsReadUseCase(eq(testId)) }
        }

    @Test
    fun `on toggle message as unread call the correct use case`() =
        runTest {
            val testId = 789456 // DOES NOT EXIST
            val mockActiveMessages = createActiveMessages()
            val mockMessageRepositoryStream = MutableStateFlow<Resource<List<MessageDomainModel>>>(
                Resource.Success(data = mockActiveMessages)
            )
            every { getCachedMessagesUseCase() } returns mockMessageRepositoryStream

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.MessageDetail(id = testId)
            every { savedStateHandle.toRoute<Route.MessageDetail>() } returns mockRoute


            // Act
            messageDetailViewModel = MessageDetailViewModel(
                savedStateHandle = savedStateHandle,
                getCachedMessagesUseCase = getCachedMessagesUseCase,
                markMessageAsReadUseCase = markMessageAsReadUseCase,
                markMessageAsUnreadUseCase = markMessageAsUnreadUseCase,
                deleteMessageUseCase = deleteMessageUseCase
            )

            messageDetailViewModel.onToggleReadStatus(testId, false)

            coVerify(exactly = 1) { markMessageAsUnreadUseCase(eq(testId)) }
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