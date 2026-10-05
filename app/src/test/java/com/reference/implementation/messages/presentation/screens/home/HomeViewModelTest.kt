package com.reference.implementation.messages.presentation.screens.home

import com.reference.implementation.domain.model.UserDashboardDomainModel
import com.reference.implementation.domain.use_case.GetUserDashboardUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.screens.bulletin.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class HomeViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getUserDashboardUseCase: GetUserDashboardUseCase = mockk()

    private lateinit var homeViewModel: HomeViewModel

    @Test
    fun `init fetches dashboard info from use case and immediately collects success ui state`() =
        runTest {

            // Arrange
            val mockDashboard = createDashboardInformation()

            val mockRepositoryStream = MutableStateFlow<Resource<UserDashboardDomainModel>>(
                Resource.Success(data = mockDashboard)
            )
            every { getUserDashboardUseCase(any()) } returns mockRepositoryStream

            // Act
            homeViewModel = HomeViewModel(getUserDashboardUseCase)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                homeViewModel.uiState.collect()
            }
            runCurrent()

            // Assert
            val expectedStateValue = HomeUiState.Success(
                userName = mockDashboard.userName,
                userEmail = mockDashboard.userEmail,
                unreadMessages = mockDashboard.unreadMessages,
                readMessages = mockDashboard.readMessages,
                roles = mockDashboard.roles,
                permissions = mockDashboard.permissions
            )
            assertEquals(
                expected = expectedStateValue,
                actual = homeViewModel.uiState.value
            )
        }

    @Test
    fun `init fetches dashboard resource loading state from use case and collects loading ui state`() =
        runTest {

            // Arrange
            val mockRepositoryStream = MutableStateFlow<Resource<UserDashboardDomainModel>>(
                Resource.Loading
            )
            every { getUserDashboardUseCase(any()) } returns mockRepositoryStream

            // Act
            homeViewModel = HomeViewModel(getUserDashboardUseCase)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                homeViewModel.uiState.collect()
            }
            runCurrent()

            // Assert
            assertEquals(
                expected = HomeUiState.Loading,
                actual = homeViewModel.uiState.value
            )
        }

    @Test
    fun `when use case triggers retry lambda, uiState emits Retrying state with correct attempt number`() =
        runTest {

            // Arrange
            val mockRepositoryStream = MutableStateFlow<Resource<UserDashboardDomainModel>>(
                Resource.Loading
            )
            // THE TRICK: Define a slot to capture your production onRetry lambda function
            val retryLambdaSlot = slot<(Int) -> Unit>()
            // THE SENIOR FIX: Pass any() to match the use case invoke() signature
            // that accepts the onRetry lambda!
            every {
                getUserDashboardUseCase(onRetry = capture(retryLambdaSlot))
            } returns mockRepositoryStream

            homeViewModel = HomeViewModel(getUserDashboardUseCase)

            // Wake up WhileSubscribed(5000)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                homeViewModel.uiState.collect()
            }
            runCurrent() // Flushes initial state pass (attempt = 0 -> HomeUiState.Loading)

            // Verify initial baseline state is Loading
            assertEquals(
                expected = HomeUiState.Loading,
                actual = homeViewModel.uiState.value
            )

            // Act: Invoke the captured lambda to simulate an in-flight retry!
            retryLambdaSlot.captured.invoke(2)
            runCurrent() // flush the combine pipeline

            // Assert
            assertEquals(
                expected = HomeUiState.Retrying(2),
                actual = homeViewModel.uiState.value
            )
        }


    @Test
    fun `init fetches dashboard resource error state from use case and collects error ui state`() =
        runTest {

            // Arrange
            val mockRepositoryStream = MutableStateFlow<Resource<UserDashboardDomainModel>>(
                Resource.Error("Network dropped")
            )
            every { getUserDashboardUseCase(any()) } returns mockRepositoryStream

            // Act
            homeViewModel = HomeViewModel(getUserDashboardUseCase)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                homeViewModel.uiState.collect()
            }
            runCurrent()

            // Assert
            assertEquals(
                expected = HomeUiState.Error("Network dropped"),
                actual = homeViewModel.uiState.value
            )
        }


    private fun createDashboardInformation(): UserDashboardDomainModel =
        UserDashboardDomainModel(
            userName = "test user",
            userEmail = "test.user@learn.com",
            unreadMessages = 5,
            readMessages = 7,
            roles = listOf("programmer", "tester", "lead"),
            permissions = listOf("coding", "testing", "reviewing")
        )


}