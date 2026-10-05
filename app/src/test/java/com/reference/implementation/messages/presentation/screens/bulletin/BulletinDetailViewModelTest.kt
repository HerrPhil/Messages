package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.use_case.GetBulletinUseCase
import com.reference.implementation.domain.use_case.LoadBulletinUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import com.reference.implementation.messages.presentation.screens.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BulletinDetailViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Create a relaxed mock to act as your "blank slate" placeholder
    private val loadBulletinUseCase: LoadBulletinUseCase = mockk(relaxed = true)
    private val getBulletinUseCase: GetBulletinUseCase = mockk()
    private lateinit var bulletinDetailViewModel: BulletinDetailViewModel

    @Test
    fun `init fetches bulletin ID from SavedStateHandle and immediately collects success state`() =
        runTest {
            // Arrange
            val testId = 789456
            val mockData = BulletinDomainModel(
                id = testId,
                userId = 1234,
                title = "bulletin today",
                post = "here is a test post",
                timestamp = "2026-07-13T22:28:56.321Z",
                timestampInstant = Instant.parse("2026-07-13T22:28:56.321Z"),
                isBookmark = false
            )
            // This map mimics exactly what Compose Navigation build behind the scenes
            val savedStateHandle = SavedStateHandle(mapOf("id" to testId))

            // Use 'every' instead of 'coEvery' because calling the function
            // returns a Flow instance immediately; the execution happens when it's collected.
            // We use flowOf() to emit the Success state immediately on stream creation.
            // THE SENIOR FIX: Use a hot MutableStateFlow as your mock stream provider.
            // Unlike flowOf(), a StateFlow keeps the value cached in memory dynamically.
            // When stateIn() subscribes, it will immediately read the Success value!
//            every { getBulletinUseCase() } returns flowOf(Resource.Success(mockData))
            val mockRepositoryStream = MutableStateFlow<Resource<BulletinDomainModel>>(
                Resource.Success(mockData)
            )
            every { getBulletinUseCase() } returns mockRepositoryStream

            // Act - calls init() inherently
            bulletinDetailViewModel = BulletinDetailViewModel(
                savedStateHandle = savedStateHandle,
                loadBulletinUseCase = loadBulletinUseCase,
                getBulletinUseCase = getBulletinUseCase
            )

            // THE SENIOR FIX: Create an active subscriber on the test's backgroundScope.
            // This forces WhileSubscribed(5000) to wake up and run the flatMapLatest pipeline!
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                bulletinDetailViewModel.uiState.collect()
            }

            // Assert
            // SNEAKY PRO MOVE: By verifying that the state contains our mockData,
            // we prove that the init block executed flawlessly
            // without needing to read the private bulletinId!
            // Now the stream safely collects, updating stateIn to Success instantly!
            assertEquals(
                expected = BulletinDetailUiState.Success(data = mockData.toBulletinUiDetail()),
                actual = bulletinDetailViewModel.uiState.value
            )
        }

    @Test
    fun `init immediately emits Loading UI state when use case is mid-flight`() =
        runTest {
            // Arrange
            val testId = 789456
            val savedStateHandle = SavedStateHandle(mapOf("id" to testId))

            // THE TRICK: Use the flow {} builder to emit Loading,
            // then call awaitCancellation() to stall the stream in-flight!
            val stallingFlow = flow<Resource<BulletinDomainModel>> {
                emit(Resource.Loading)
                kotlinx.coroutines.awaitCancellation()
            }
            every { getBulletinUseCase() } returns stallingFlow

            // Act
            bulletinDetailViewModel = BulletinDetailViewModel(
                savedStateHandle = savedStateHandle,
                loadBulletinUseCase = loadBulletinUseCase,
                getBulletinUseCase = getBulletinUseCase
            )

            // Wake up the WhileSubscribed operator
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                bulletinDetailViewModel.uiState.collect()
            }

            // Assert: It correctly maps attempt 0 to BulletinDetailUiState.Loading
            assertEquals(
                expected = BulletinDetailUiState.Loading,
                actual = bulletinDetailViewModel.uiState.value
            )
        }

    /**
     * Preamble on why coAnswers is necessary:
     * The specific error - suspendConversion0 not resolved - is a known compiler compatibility
     * issue. It pops up when using a newer Kotlin compiler alongside a version of MockK that
     * struggles with structural reflection on high-order suspend lambdas passed as functional
     * arguments (like onRetry: suspend (Int) -> Unit).
     *
     * When you pass a lambda matching a signature parameter, the Kotlin compiler generates an
     * internal helper method (suspendConversion0) to bridge the standard lambda type into a
     * coroutine continuation context. MockK attempts to inspect this with Java reflection during
     * the capture() step, hits a missing signature lookup, and crashes.
     *
     * The cleanest senior workaround is to use a relaxed mock to automatically record the execution
     * or use an explicit coAnswers block instead of a strict matching coEvery ... returns Unit loop.
     */
    @Test
    fun `when use case triggers retry lambda, uiState emits Retrying state with correct attempt number`() =
        runTest {
            // 1. Arrange
            val testId = 789456

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // Explicitly force toRoute<Route.MessageDetail>() to return your mock route object!
            val mockRoute = Route.BulletinDetail(id = testId)
            every { savedStateHandle.toRoute<Route.BulletinDetail>() } returns mockRoute

            // Set up our mock repository flow to hold on Loading
            val mockRepositoryStream =
                MutableStateFlow<Resource<BulletinDomainModel>>(Resource.Loading)
            every { getBulletinUseCase() } returns mockRepositoryStream

            // THE TRICK: Define a slot to capture your production onRetry lambda function
            val retryLambdaSlot = slot<(Int) -> Unit>()
            // Stub loadBulletinUseCase to catch the lambda when it gets called inside init
            coEvery {
                loadBulletinUseCase(
                    bulletinId = any(),
                    onRetry = capture(retryLambdaSlot)
                )
            } returns Unit

            // 2. Act part 1: Construct the ViewModel (this invokes loadBulletinDetailData internally)
            bulletinDetailViewModel = BulletinDetailViewModel(
                savedStateHandle = savedStateHandle,
                loadBulletinUseCase = loadBulletinUseCase,
                getBulletinUseCase = getBulletinUseCase
            )

            // Wake up WhileSubscribed
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                bulletinDetailViewModel.uiState.collect()
            }

            // 💡 THE SENIOR FIX: Flush the queue immediately after constructor injection!
            // This forces the viewModelScope.launch block to fire, letting MockK capture the lambda.
            runCurrent()

            // Assert Initial State is Loading (attempt is 0)
            assertEquals(
                expected = BulletinDetailUiState.Loading,
                actual = bulletinDetailViewModel.uiState.value
            )

            // 3. Act part 2: Invoke the captured lambda to simulate an in-flight retry!
            // This executes: _loadTrigger.value = 3
            retryLambdaSlot.captured.invoke(3)
            runCurrent()

            // 4. Assert Final State
            // flatMapLatest catches the 3, gets the stalled Resource.Loading,
            // and maps to Retrying(3)
            assertEquals(
                expected = BulletinDetailUiState.Retrying(attempt = 3),
                actual = bulletinDetailViewModel.uiState.value
            )

            // 💡 BEHAVIORAL SIDE-EFFECT VERIFICATION:
            // This will now pass flawlessly! It proves the view model successfully
            // dispatched the initialization request down to the use-case layer.
            coVerify(exactly = 1) {
                loadBulletinUseCase(
                    bulletinId = eq(testId), // 👈 CRITICAL: Must use any() here too to match the '0' runtime delivery!
                    onRetry = retryLambdaSlot.captured
                )
            }

        }

    @Test
    fun `when use case emits error resource, uiState transforms to Error state immediately`() =
        runTest {
            // 1. Arrange
            val testId = 789456
            val errorMessage = "Failed to load bulletin from cache. Please try again."
            val savedStateHandle = SavedStateHandle(mapOf("id" to testId))

            // Seed our hot mock stream with a localized Error resource wrapper
            val mockRepositoryStream = MutableStateFlow<Resource<BulletinDomainModel>>(
                Resource.Error(message = errorMessage)
            )
            every { getBulletinUseCase() } returns mockRepositoryStream

            // 2. Act
            bulletinDetailViewModel = BulletinDetailViewModel(
                savedStateHandle = savedStateHandle,
                loadBulletinUseCase = mockk(relaxed = true),
                getBulletinUseCase = getBulletinUseCase
            )

            // 3. Wake Up the Pipeline (The Subscriber Truth)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                bulletinDetailViewModel.uiState.collect()
            }

            // 4. Assert: Verify the domain error was safely transformed for the UI layer
            assertEquals(
                expected = BulletinDetailUiState.Error(message = errorMessage),
                actual = bulletinDetailViewModel.uiState.value
            )
        }

}
