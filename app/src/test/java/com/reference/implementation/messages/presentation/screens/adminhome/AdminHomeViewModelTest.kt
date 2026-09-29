package com.reference.implementation.messages.presentation.screens.adminhome

import com.reference.implementation.domain.model.AdminDashboardDomainModel
import com.reference.implementation.domain.use_case.GetAdminDashboardUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.screens.bulletin.FakeGetAdminDashboardUseCase
import com.reference.implementation.messages.presentation.screens.bulletin.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AdminHomeViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getAdminDashboardUseCase: GetAdminDashboardUseCase = mockk()

    private lateinit var adminHomeViewModel: AdminHomeViewModel

    @Test
    fun `init fetches dashboard info from use case and immediately collects success ui state`() =
        runTest {

            // Arrange
            val mockDashboard = createDashboardInformation()

            val mockRepositoryStream = MutableStateFlow<Resource<AdminDashboardDomainModel>>(
                Resource.Success(data = mockDashboard)
            )
            // THE SENIOR FIX: Pass any() to match the use case invoke() signature
            // that accepts the onRetry lambda!
            every { getAdminDashboardUseCase(onRetry = any()) } returns mockRepositoryStream

            // Act
            adminHomeViewModel = AdminHomeViewModel(
                getAdminDashboardUseCase = getAdminDashboardUseCase
            )
            // THE SENIOR FIX: Create an active subscriber on the test's backgroundScope.
            // This forces WhileSubscribed(5000) to wake up and run the flatMapLatest pipeline!
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                adminHomeViewModel.uiState.collect()
            }

            // WHY THE FIX: To let combine block finish evaluating its data right after
            // the initial getAdminDashboardUseCase baseline emission,
            // you just need to yield control of the test engine for a single frame.
            //
            // runCurrent() tells UnconfinedTestDispatcher "Hey, hold on - let any pending
            // calculations inside the combine lambda block finish their execution
            // right now before I run my assertion."

            // THE FIX: Force the unconfined scheduler to flush any
            // pending lambda inside the combine pipeline immediately!
            runCurrent()

            // Assert
            assertEquals(
                expected = AdminHomeUiState.Success(
                    usersCount = mockDashboard.usersCount,
                    summaryMessages = mockDashboard.summaryMessages,
                    bulletinsCount = mockDashboard.bulletinsCount
                ),
                actual = adminHomeViewModel.uiState.value
            )
        }

    @Test
    fun `init fetches dashboard resource loading state from use case and collects loading ui state`() =
        runTest {

            // Arrange
            val mockRepositoryStream = MutableStateFlow<Resource<AdminDashboardDomainModel>>(
                Resource.Loading
            )
            // THE SENIOR FIX: Pass any() to match the use case invoke() signature
            // that accepts the onRetry lambda!
            every { getAdminDashboardUseCase(onRetry = any()) } returns mockRepositoryStream

            // Act
            adminHomeViewModel = AdminHomeViewModel(
                getAdminDashboardUseCase = getAdminDashboardUseCase
            )
            // THE SENIOR FIX: Create an active subscriber on the test's backgroundScope.
            // This forces WhileSubscribed(5000) to wake up and run the flatMapLatest pipeline!
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                adminHomeViewModel.uiState.collect()
            }

            // WHY THE FIX: To let combine block finish evaluating its data right after
            // the initial getAdminDashboardUseCase baseline emission,
            // you just need to yield control of the test engine for a single frame.
            //
            // runCurrent() tells UnconfinedTestDispatcher "Hey, hold on - let any pending
            // calculations inside the combine lambda block finish their execution
            // right now before I run my assertion."

            // THE FIX: Force the unconfined scheduler to flush any
            // pending lambda inside the combine pipeline immediately!
            runCurrent()

            // Assert
            assertEquals(
                expected = AdminHomeUiState.Loading,
                actual = adminHomeViewModel.uiState.value
            )
        }

    @Test
    fun `when use case triggers retry lambda, uiState emits Retrying state with correct attempt number`() =
        runTest {
            // 1. Arrange
            val fakeUseCase = FakeGetAdminDashboardUseCase()

            adminHomeViewModel = AdminHomeViewModel(
                getAdminDashboardUseCase = fakeUseCase
            )

            // Wake up WhileSubscribed(5000)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                adminHomeViewModel.uiState.collect()
            }
            runCurrent() // Flushes initial state pass (attempt = 0 -> AdminHomeUiState.Loading)

            // Verify initial baseline state is Loading
            assertEquals(AdminHomeUiState.Loading, adminHomeViewModel.uiState.value)

            // 2. Act: Invoke your captured lambda to simulate an in-flight retry!
            val targetCallback = checkNotNull(fakeUseCase.capturedOnRetry)
            targetCallback.invoke(2) // Simulates second retry attempt
            runCurrent()

            // 3. Assert
            // The combine block catches attempt = 2 paired with Resource.Loading,
            // mapping flawlessly to Retrying(2)!
            assertEquals(
                expected = AdminHomeUiState.Retrying(attempt = 2),
                actual = adminHomeViewModel.uiState.value
            )
        }

    @Test
    fun `init fetches dashboard resource error state from use case and collects error ui state`() =
        runTest {

            // Arrange
            val mockRepositoryStream = MutableStateFlow<Resource<AdminDashboardDomainModel>>(
                Resource.Error("bulletin service offline")
            )
            // THE SENIOR FIX: Pass any() to match the use case invoke() signature
            // that accepts the onRetry lambda!
            every { getAdminDashboardUseCase(onRetry = any()) } returns mockRepositoryStream

            // Act
            adminHomeViewModel = AdminHomeViewModel(
                getAdminDashboardUseCase = getAdminDashboardUseCase
            )
            // THE SENIOR FIX: Create an active subscriber on the test's backgroundScope.
            // This forces WhileSubscribed(5000) to wake up and run the flatMapLatest pipeline!
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                adminHomeViewModel.uiState.collect()
            }

            // WHY THE FIX: To let combine block finish evaluating its data right after
            // the initial getAdminDashboardUseCase baseline emission,
            // you just need to yield control of the test engine for a single frame.
            //
            // runCurrent() tells UnconfinedTestDispatcher "Hey, hold on - let any pending
            // calculations inside the combine lambda block finish their execution
            // right now before I run my assertion."

            // THE FIX: Force the unconfined scheduler to flush any
            // pending lambda inside the combine pipeline immediately!
            runCurrent()

            // Assert
            assertEquals(
                expected = AdminHomeUiState.Error("bulletin service offline"),
                actual = adminHomeViewModel.uiState.value
            )
        }

    private fun createDashboardInformation(): AdminDashboardDomainModel =
        AdminDashboardDomainModel(
            usersCount = 10,
            summaryMessages = "test admin dashboard messages",
            bulletinsCount = 13
        )

}