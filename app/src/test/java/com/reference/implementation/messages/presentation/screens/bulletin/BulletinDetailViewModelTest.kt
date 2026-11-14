package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.use_case.GetBulletinByIdUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import com.reference.implementation.messages.presentation.screens.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val getBulletinByIdUseCase: GetBulletinByIdUseCase = mockk(relaxed = true)

    private lateinit var bulletinDetailViewModel: BulletinDetailViewModel

    @Test
    fun `getBulletinByIdUseCase fetches bulletin ID from SavedStateHandle and immediately collects success state`() =
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
            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // To make the test pass, the saved state handle toRoute() is mocked in the context
            // after Jetpack Navigation has sent a Route.MessageDetail bundle.
            val mockRoute = Route.BulletinDetail(id = testId)
            every { savedStateHandle.toRoute<Route.BulletinDetail>() } returns mockRoute

            // Use 'every' instead of 'coEvery' because calling the function
            // returns a Flow instance immediately; the execution happens when it's collected.
            // We use flowOf() to emit the Success state immediately on stream creation.
            // THE SENIOR FIX: Use a hot MutableStateFlow as your mock stream provider.
            // Unlike flowOf(), a StateFlow keeps the value cached in memory dynamically.
            // When stateIn() subscribes, it will immediately read the Success value!
            val mockRepositoryStream = MutableStateFlow<Resource<BulletinDomainModel>>(
                Resource.Success(mockData)
            )
            every { getBulletinByIdUseCase(bulletinId = testId) } returns mockRepositoryStream

            // Act - calls init() inherently
            bulletinDetailViewModel = BulletinDetailViewModel(
                savedStateHandle = savedStateHandle,
                getBulletinByIdUseCase = getBulletinByIdUseCase
            )

            // THE SENIOR FIX: Create an active subscriber on the test's backgroundScope.
            // This forces WhileSubscribed(5000) to wake up and run the flatMapLatest pipeline!
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                bulletinDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert
            // SNEAKY PRO MOVE: By verifying that the state contains our mockData,
            // we prove that the getBulletinByIdUseCase() executed flawlessly
            // without needing to read the private bulletinId!
            // Now the stream safely collects, updating stateIn to Success instantly!
            assertEquals(
                expected = BulletinDetailUiState.Success(data = mockData.toBulletinUiDetail()),
                actual = bulletinDetailViewModel.uiState.value
            )

            @Suppress("UnusedFlow")
            verify(exactly = 1) { getBulletinByIdUseCase(eq(testId)) }
        }

    @Test
    fun `getBulletinByIdUseCase immediately emits Loading UI state when use case is mid-flight`() =
        runTest {
            // Arrange
            val testId = 789456

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // To make the test pass, the saved state handle toRoute() is mocked in the context
            // after Jetpack Navigation has sent a Route.MessageDetail bundle.
            val mockRoute = Route.BulletinDetail(id = testId)
            every { savedStateHandle.toRoute<Route.BulletinDetail>() } returns mockRoute

            // THE TRICK: Use the flow {} builder to emit Loading,
            // then call awaitCancellation() to stall the stream in-flight!
            val stallingFlow = flow<Resource<BulletinDomainModel>> {
                emit(Resource.Loading)
                kotlinx.coroutines.awaitCancellation()
            }
            every { getBulletinByIdUseCase(bulletinId = testId) } returns stallingFlow

            // Act
            bulletinDetailViewModel = BulletinDetailViewModel(
                savedStateHandle = savedStateHandle,
                getBulletinByIdUseCase = getBulletinByIdUseCase
            )

            // Wake up the WhileSubscribed operator
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                bulletinDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // Assert: It correctly maps attempt 0 to BulletinDetailUiState.Loading
            assertEquals(
                expected = BulletinDetailUiState.Loading,
                actual = bulletinDetailViewModel.uiState.value
            )

            @Suppress("UnusedFlow")
            verify(exactly = 1) { getBulletinByIdUseCase(eq(testId)) }
        }

    @Test
    fun `when use case emits error resource, uiState transforms to Error state immediately`() =
        runTest {
            // 1. Arrange
            val testId = 789456

            val errorMessage = "Failed to load bulletin from cache. Please try again."

            // 💡 THE ULTIMATE BYPASS FIX: Mock the static navigation extension file
            mockkStatic("androidx.navigation.SavedStateHandleKt")
            val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
            // To make the test pass, the saved state handle toRoute() is mocked in the context
            // after Jetpack Navigation has sent a Route.MessageDetail bundle.
            val mockRoute = Route.BulletinDetail(id = testId)
            every { savedStateHandle.toRoute<Route.BulletinDetail>() } returns mockRoute

            // Seed our hot mock stream with a localized Error resource wrapper
            val mockRepositoryStream = MutableStateFlow<Resource<BulletinDomainModel>>(
                Resource.Error(message = errorMessage)
            )
            every { getBulletinByIdUseCase(bulletinId = testId) } returns mockRepositoryStream

            // 2. Act
            bulletinDetailViewModel = BulletinDetailViewModel(
                savedStateHandle = savedStateHandle,
                getBulletinByIdUseCase = getBulletinByIdUseCase
            )

            // 3. Wake Up the Pipeline (The Subscriber Truth)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                bulletinDetailViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined engine thread loop immediately!

            // 4. Assert: Verify the domain error was safely transformed for the UI layer
            assertEquals(
                expected = BulletinDetailUiState.Error(message = errorMessage),
                actual = bulletinDetailViewModel.uiState.value
            )

            @Suppress("UnusedFlow")
            verify(exactly = 1) { getBulletinByIdUseCase(eq(testId)) }
        }

}
