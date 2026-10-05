package com.reference.implementation.messages.presentation.screens.login

import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.use_case.FetchNewUserProfileUseCase
import com.reference.implementation.domain.use_case.LoginUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.screens.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    // Hook up the UnconfinedTestDispatcher to rule the main thread execution context
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    val loginUseCase: LoginUseCase = mockk(relaxed = true)

    val fetchNewUserProfileUseCase: FetchNewUserProfileUseCase = mockk()

    private lateinit var loginViewModel: LoginViewModel

    @Test
    fun `validate login form default state when no email and password then submit enabled is false`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                loginViewModel.isSubmitEnabled.collect {}
            }

            // on first landing on the login screen, the email and password default to empty string

            // Assert
            assertFalse(loginViewModel.isSubmitEnabled.value, "credentials incomplete")
        }

    @Test
    fun `validate login form state when blank email and blank password then submit enabled is false`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                loginViewModel.isSubmitEnabled.collect {}
            }

            // on first landing on the login screen, the email and password default to empty string
            loginViewModel.onEmailChange("")
            loginViewModel.onPasswordChange("")
            runCurrent() // flush the combine pipeline

            // Assert
            assertFalse(loginViewModel.isSubmitEnabled.value, "credentials incomplete")
        }

    @Test
    fun `validate login form state when non-blank email and blank password then submit enabled is false`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                loginViewModel.isSubmitEnabled.collect {}
            }

            // on first landing on the login screen, the email and password default to empty string
            loginViewModel.onEmailChange("test.user@learn.com")
            loginViewModel.onPasswordChange("")
            runCurrent() // flush the combine pipeline

            // Assert
            assertFalse(loginViewModel.isSubmitEnabled.value, "credentials incomplete")
        }

    @Test
    fun `validate login form state when blank email and non-blank password then submit enabled is false`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                loginViewModel.isSubmitEnabled.collect {}
            }

            // on first landing on the login screen, the email and password default to empty string
            loginViewModel.onEmailChange("")
            loginViewModel.onPasswordChange("p@s5w0rd")
            runCurrent() // flush the combine pipeline

            // Assert
            assertFalse(loginViewModel.isSubmitEnabled.value, "credentials incomplete")
        }

    @Test
    fun `validate login form state when non-blank email and non-blank password then submit enabled is true`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                loginViewModel.isSubmitEnabled.collect {}
            }

            // on first landing on the login screen, the email and password default to empty string
            loginViewModel.onEmailChange("test@learn.com")
            loginViewModel.onPasswordChange("p@s5w0rd")
            runCurrent() // flush the combine pipeline

            // Assert
            assertTrue(loginViewModel.isSubmitEnabled.value, "credentials set")
        }

    @Test
    fun `validate login default ui state is idle`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            // Assert
            assertEquals(
                expected = LoginUiState.Idle,
                actual = loginViewModel.uiState
            )
        }

    @Test
    fun `init login ui state is Loading`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            // there is 300ms delay in the loginUseCase to fix a UI flicker
            loginViewModel.login("test.user@learn.com", "p@s5w0rd")

            // Assert
            assertEquals(
                expected = LoginUiState.Loading,
                actual = loginViewModel.uiState
            )
        }

    @Test
    fun `init login with credentials and immediately returns ui success`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            // there is 300ms delay in the loginUseCase to fix a UI flicker
            loginViewModel.login("test.user@learn.com", "p@s5w0rd")
            // delay test longer than flicker delay to let viewModelScope coroutine execute the success case
            delay(500.milliseconds)

            // Assert
            assertEquals(
                expected = LoginUiState.Success(name = "test user", email = "test.user@learn.com"),
                actual = loginViewModel.uiState
            )
        }

    @Test
    fun `init login with bad credentials and immediately returns ui error`() =
        runTest {
            // Arrange
            val loginResource = Resource.Error("credentials are invalid")
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            // there is 300ms delay in the loginUseCase to fix a UI flicker
            loginViewModel.login("test.user@learn.com", "p@s5w0rd")
            // delay test longer than flicker delay to let viewModelScope coroutine execute the success case
            delay(500.milliseconds)

            // Assert
            assertEquals(
                expected = LoginUiState.Error("credentials are invalid"),
                actual = loginViewModel.uiState
            )
        }

    @Test
    fun `init login and load user profile fails and immediately returns ui error`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Error("cannot load user profile")

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            // there is 300ms delay in the loginUseCase to fix a UI flicker
            loginViewModel.login("test.user@learn.com", "p@s5w0rd")
            // delay test longer than flicker delay to let viewModelScope coroutine execute the success case
            delay(500.milliseconds)

            // Assert
            assertEquals(
                expected = LoginUiState.Error("cannot load user profile"),
                actual = loginViewModel.uiState
            )
        }

    @Test
    fun `init login io response is retrying credentials and immediately returns ui retrying`() =
        runTest {
            // Arrange
            val loginResource = Resource.Loading
            // THE TRICK: Define a slot to capture your production onRetry lambda function
            val retryLambdaSlot = slot<(Int) -> Unit>()
            coEvery {
                loginUseCase(
                    any(),
                    any(),
                    capture(retryLambdaSlot)
                )
            } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            // there is 300ms delay in the loginUseCase to fix a UI flicker
            loginViewModel.login("test.user@learn.com", "p@s5w0rd")
            // delay test longer than flicker delay to let viewModelScope coroutine execute the success case
            delay(500.milliseconds)

            // 5. Act Part 2: Manually fire your captured retry callback through the fake!
            // This executes: _loadTrigger.value = 1, forcing flatMapLatest to transition streams!
            retryLambdaSlot.captured.invoke(1)

            // Assert
            val expectedState = LoginUiState.Retrying(1)
            assertEquals(
                expected = expectedState,
                actual = loginViewModel.uiState
            )
        }


    @Test
    fun `init login io response is retrying load user profile and immediately returns ui retrying`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery {
                loginUseCase(any(), any(), any())
            } returns loginResource
            val retryLambdaSlot = slot<(Int) -> Unit>()
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    capture(retryLambdaSlot)
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            // there is 300ms delay in the loginUseCase to fix a UI flicker
            loginViewModel.login("test.user@learn.com", "p@s5w0rd")
            // delay test longer than flicker delay to let viewModelScope coroutine execute the success case
            delay(500.milliseconds)

            // 5. Act Part 2: Manually fire your captured retry callback through the fake!
            // This executes: _loadTrigger.value = 1, forcing flatMapLatest to transition streams!
            retryLambdaSlot.captured.invoke(1)

            // Assert
            val expectedState = LoginUiState.Retrying(1)
            assertEquals(
                expected = expectedState,
                actual = loginViewModel.uiState
            )
        }


    @Test
    fun `cancel login on navigate away and immediately returns ui idle`() =
        runTest {
            // Arrange
            val loginResults = createLoginResults()
            val loginResource = Resource.Success(data = loginResults)
            coEvery { loginUseCase(any(), any(), any()) } returns loginResource
            coEvery {
                fetchNewUserProfileUseCase(
                    any(),
                    any()
                )
            } returns Resource.Success(data = Unit)

            // Act
            loginViewModel = LoginViewModel(
                loginUseCase,
                fetchNewUserProfileUseCase
            )

            loginViewModel.cancel()

            // Assert
            assertEquals(
                expected = LoginUiState.Idle,
                actual = loginViewModel.uiState
            )
        }

    private fun createLoginResults(): LoginUserDomainModel =
        LoginUserDomainModel(
            id = 789456,
            name = "test user",
            email = "test.user@learn.com",
            age = 22
        )
}