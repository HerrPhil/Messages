package com.reference.implementation.messages.presentation.screens.bulletin

import com.reference.implementation.domain.model.BulletinDomainModel
import com.reference.implementation.domain.use_case.GetAllBulletinsUseCase
import com.reference.implementation.domain.use_case.LoadAllBulletinsUseCase
import com.reference.implementation.domain.use_case.MarkBulletinAsBookmarkUseCase
import com.reference.implementation.domain.use_case.MarkBulletinAsNotBookmarkUseCase
import com.reference.implementation.domain.use_case.Resource
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
class BulletinViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Create a relaxed mock to act as your "blank slate" placeholder
    private val loadAllBulletinsUseCase: LoadAllBulletinsUseCase = mockk(relaxed = true)
    private val getAllBulletinsUseCase: GetAllBulletinsUseCase = mockk()
    private val markBulletinAsBookmarkUseCase: MarkBulletinAsBookmarkUseCase = mockk(relaxed = true)
    private val markBulletinAsNotBookmarkUseCase: MarkBulletinAsNotBookmarkUseCase =
        mockk(relaxed = true)
    private lateinit var bulletinViewModel: BulletinViewModel

    @Test
    fun `init fetches all bulletins from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockBulletins = createBulletins()
            val mockRepositoryStream = MutableStateFlow<Resource<List<BulletinDomainModel>>>(
                Resource.Success(mockBulletins)
            )
            every { getAllBulletinsUseCase() } returns mockRepositoryStream

            // 1. Act - calls init() inherently
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = loadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                bulletinViewModel.uiState.collect {}
            }

            // WHY THE FIX: To let combine block finish evaluating its data right after the initial
            // .scan() baseline emission, you just need to yield control of the test engine
            // for a single frame
            //
            // runCurrent() tells UnconfinedTestDispatcher "Hey, hold on - let any pending
            // calculations inside the combine and scan lambda blocks finish their execution
            // right now before I run my assertion."

            // THE FIX: Force the unconfined scheduler to flush any pending lambda
            // inside the combine/scan pipeline immediately!
            runCurrent()

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = BulletinUiState.Success(
                list = mockBulletins.map { it.toBulletinUiDetail() },
                isRefreshing = false,
                isBookmarkOnly = false
            )

            assertEquals(expectedState, bulletinViewModel.uiState.value)
        }

    @Test
    fun `init fetches bookmarked bulletins from use case and immediately collects success data`() =
        runTest {
            // Arrange
            val mockBulletins = createBulletins()
            val mockRepositoryStream = MutableStateFlow<Resource<List<BulletinDomainModel>>>(
                Resource.Success(mockBulletins)
            )
            every { getAllBulletinsUseCase() } returns mockRepositoryStream

            // 1. Act - calls init() inherently
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = loadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )
            bulletinViewModel.onBookmarkOnlyToggled(true)

            // 2. Wake up WhileSubscribed - add active subscriber to mimic UI
            backgroundScope.launch(testScheduler) {
                bulletinViewModel.uiState.collect {}
            }

            // WHY THE FIX: To let combine block finish evaluating its data right after the initial
            // .scan() baseline emission, you just need to yield control of the test engine
            // for a single frame
            //
            // runCurrent() tells UnconfinedTestDispatcher "Hey, hold on - let any pending
            // calculations inside the combine and scan lambda blocks finish their execution
            // right now before I run my assertion."

            // THE FIX: Force the unconfined scheduler to flush any pending lambda
            // inside the combine/scan pipeline immediately!
            runCurrent()

            // Assert: Verify that the list mapped to Success and matches our conversion expectation
            val expectedState = BulletinUiState.Success(
                list = mockBulletins
                    .filter { bulletin -> bulletin.isBookmark }
                    .map { it.toBulletinUiDetail() },
                isRefreshing = false,
                isBookmarkOnly = true
            )

            assertEquals(expectedState, bulletinViewModel.uiState.value)
        }

    @Test
    fun `when pull to refresh is active and use case emits Loading, scan block preserves previous success list`() =
        runTest {
            // 1. Arrange: Start with a populated list in Success State
            val mockBulletins = createBulletins()
            val mockRepositoryStream = MutableStateFlow<Resource<List<BulletinDomainModel>>>(
                Resource.Success(mockBulletins)
            )
            every { getAllBulletinsUseCase() } returns mockRepositoryStream

            // Instantiate your hand-written fake contract
            val fakeLoadAllBulletinsUseCase = FakeLoadAllBulletinsUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = fakeLoadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // Wake up WhileSubscribed to establish the baseline Success state context
            backgroundScope.launch(testScheduler) {
                bulletinViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // 💡 THE SENIOR TIMELINE FIX:
            // Step 2: Trigger the refresh gesture FIRST. This locks _isRefreshing to true inside the delay.
            bulletinViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // Step 3: Now that isRefreshing is safely locked to true, drop the stream to Loading!
            mockRepositoryStream.value = Resource.Loading

            // Step 4: Advance the clock slightly to process the composite stream mapping.
            // Important: Advance less than 1000 milliseconds of the delay in the
            // fakeLoadAllBulletinsUseCase, called by onRefresh()!
            advanceTimeBy(500.milliseconds)

            // Step 5. Assert: The scan block looks back at history, catches Success,
            // notices isRefreshing is true, and carries the list forward perfectly!
            val expectedState = BulletinUiState.Success(
                list = mockBulletins.map { it.toBulletinUiDetail() },
                isRefreshing = true,
                isBookmarkOnly = false
            )

            assertEquals(expectedState, bulletinViewModel.uiState.value)
        }

    @Test
    fun `when pull to refresh is active and use case emits Retrying`() =
        runTest {
            // 1. Arrange: Start with a populated list in Success State
            val mockBulletins = createBulletins()
            val mockRepositoryStream = MutableStateFlow<Resource<List<BulletinDomainModel>>>(
                Resource.Success(mockBulletins)
            )
            every { getAllBulletinsUseCase() } returns mockRepositoryStream

            // Instantiate your hand-written fake contract
            val fakeLoadAllBulletinsUseCase = FakeLoadAllBulletinsUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // 2. Act Step 1: Initialize the ViewModel
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = fakeLoadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // Wake up WhileSubscribed to establish the baseline Success state context
            backgroundScope.launch(testScheduler) {
                bulletinViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // 3. Act Part 2: Update the repository stream value to Loading
            mockRepositoryStream.value = Resource.Loading // Change 1: Stream drops to Loading

            // 4. Act Step 3: Trigger the manual refresh gesture
            bulletinViewModel.onRefresh()                 // Change 2: Spinner sets to True
            runCurrent()

            // 5. Act Part 4: Manually fire your captured retry callback through the fake!
            // This executes: _loadTrigger.value = 1, forcing flatMapLatest to transition streams!
            val targetRetryCallback = checkNotNull(fakeLoadAllBulletinsUseCase.capturedOnRetry)
            targetRetryCallback.invoke(1)

            // 4. Assert: Verify your custom scan carrier rules!
            // The state should be Success with refreshing = true, keeping the list completely visible!
            val expectedState = BulletinUiState.Retrying(attempt = 1)

            assertEquals(expectedState, bulletinViewModel.uiState.value)
        }

    @Test
    fun `init emits Loading UI state when use case stream initializes with Loading resource`() =
        runTest {
            // 1. Arrange: Seed your hot stream directly with the Loading state token
            val mockRepositoryStream = MutableStateFlow<Resource<List<BulletinDomainModel>>>(
                Resource.Loading
            )
            every { getAllBulletinsUseCase() } returns mockRepositoryStream

            // 2. Act: Construct the ViewModel (We can use a simple relaxed mock here!)
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = mockk(relaxed = true),
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // 3. Wake Up the Pipeline (The Subscriber Truth)
            backgroundScope.launch(testScheduler) {
                bulletinViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined thread to evaluate the initial pass

            // 4. Assert: Verify the stream rests securely on the Loading state representation
            assertEquals(
                expected = BulletinUiState.Loading,
                actual = bulletinViewModel.uiState.value
            )
        }


    @Test
    fun `when pull to refresh is active and use case emits Error, scan block preserves previous success list`() =
        runTest {
            // 1. Arrange: Start with a populated list in Success State
            val mockBulletins = createBulletins()
            val mockRepositoryStream = MutableStateFlow<Resource<List<BulletinDomainModel>>>(
                Resource.Success(mockBulletins)
            )
            every { getAllBulletinsUseCase() } returns mockRepositoryStream

            // Instantiate your hand-written fake contract
            val fakeLoadAllBulletinsUseCase = FakeLoadAllBulletinsUseCase()

            // Prevent isRefreshing flapping
            // THE WHY: To capture the state while the pull-to-refresh action is actively in flight,
            // we need to pause the loadAllBulletinsUseCase execution mid-flight inside the
            // test body. This forces _isRefreshing to hold its true value open long enough
            // for our scan assertion to capture it.

            // Act Step 1: Initialize the ViewModel
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = fakeLoadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // Wake up WhileSubscribed to establish the baseline Success state context
            backgroundScope.launch(testScheduler) {
                bulletinViewModel.uiState.collect {}
            }
            runCurrent() // Success state is firmly established as 'previousState'

            // 💡 THE SENIOR TIMELINE FIX:
            // Step 1: Trigger the refresh gesture FIRST. This locks _isRefreshing to true inside the delay.
            bulletinViewModel.onRefresh()
            runCurrent() // Flushes the combine pipeline so the state machine registers isRefreshing = true

            // Step 2: Now that isRefreshing is safely locked to true, drop the stream to Loading!
            mockRepositoryStream.value = Resource.Error("Network Timeout")

            // Step 3: Advance the clock slightly to process the composite stream mapping.
            // Important: Advance less than 1000 milliseconds of the delay in the
            // fakeLoadAllBulletinsUseCase, called by onRefresh()!
            advanceTimeBy(500.milliseconds)

            // 5. Assert: The scan block looks back at history, catches Success,
            // notices isRefreshing is true, and carries the list forward perfectly!
            val expectedState = BulletinUiState.Success(
                list = mockBulletins.map { it.toBulletinUiDetail() },
                isRefreshing = true,
                isBookmarkOnly = false
            )

            assertEquals(expectedState, bulletinViewModel.uiState.value)
        }

    @Test
    fun `init emits Error UI state when use case stream initializes with Error resource`() =
        runTest {
            // 1. Arrange: Seed your hot stream directly with the Loading state token
            val mockRepositoryStream = MutableStateFlow<Resource<List<BulletinDomainModel>>>(
                Resource.Error("Cache Fail")
            )
            every { getAllBulletinsUseCase() } returns mockRepositoryStream

            // 2. Act: Construct the ViewModel (We can use a simple relaxed mock here!)
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = loadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // 3. Wake Up the Pipeline (The Subscriber Truth)
            backgroundScope.launch(testScheduler) {
                bulletinViewModel.uiState.collect {}
            }
            runCurrent() // Flush the unconfined thread to evaluate the initial pass

            // 4. Assert: Verify the stream rests securely on the Loading state representation
            assertEquals(
                expected = BulletinUiState.Error("Cache Fail"),
                actual = bulletinViewModel.uiState.value
            )
        }

    @Test
    fun `when a bulletin item toggles bookmark to be added then execute markBulletinAsBookmarkUseCase`() =
        runTest {

            // Arrange
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = loadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // Act
            bulletinViewModel.onToggleBookmarkBulletinClicked(789456, true)

            // Assert
            // Use coVerify since the use case is launched in a coroutine by viewModelScope
            coVerify(exactly = 1) { markBulletinAsBookmarkUseCase(eq(789456)) }
        }

    @Test
    fun `when a bulletin item toggles bookmark to be removed then execute markBulletinAsNotBookmarkUseCase`() =
        runTest {

            // Arrange
            bulletinViewModel = BulletinViewModel(
                loadAllBulletinsUseCase = loadAllBulletinsUseCase,
                getAllBulletinsUseCase = getAllBulletinsUseCase,
                markBulletinAsBookmarkUseCase = markBulletinAsBookmarkUseCase,
                markBulletinAsNotBookmarkUseCase = markBulletinAsNotBookmarkUseCase
            )

            // Act
            bulletinViewModel.onToggleBookmarkBulletinClicked(789456, false)

            // Assert
            // Use coVerify since the use case is launched in a coroutine by viewModelScope
            coVerify(exactly = 1) { markBulletinAsNotBookmarkUseCase(eq(789456)) }
        }

    private fun createBulletins(): List<BulletinDomainModel> =
        listOf(
            BulletinDomainModel(
                id = 789456,
                userId = 1234,
                title = "bulletin today",
                post = "here is a test post",
                timestamp = "2026-07-13T22:28:56.321Z",
                timestampInstant = Instant.parse("2026-07-13T22:28:56.321Z"),
                isBookmark = false
            ),
            BulletinDomainModel(
                id = 456123,
                userId = 1234,
                title = "bulletin yesterday",
                post = "here is an old post",
                timestamp = "2026-07-12T22:28:56.321Z",
                timestampInstant = Instant.parse("2026-07-12T22:28:56.321Z"),
                isBookmark = true
            )
        )
}