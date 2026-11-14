package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.model.UserOptionDomainModel
import com.reference.implementation.domain.repository.UserRepository
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetAdminUserInformationUseCaseTest {

    private val userRepository: UserRepository = mockk()

    private lateinit var useCase: GetAdminUserInformationUseCase

    @Before
    fun setUp() {
        useCase = GetAdminUserInformationUseCase(
            userRepository
        )
    }

    @Test
    fun `invoke starts on Resource Loading`() =
        runTest {

            // Arrange
            val usersFlow =
                MutableStateFlow<CachePayload<List<LoginUserDomainModel>>>(
                    value = CachePayload.ActiveStream(
                        data = emptyList(),
                        isSyncing = false,
                        syncError = null
                    )
                )
            val userAuthenticatedInfoFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Loading
            )

            every { userRepository.getUsers() } returns usersFlow
            every { userRepository.getUserAuthenticatedInfoFlow() } returns userAuthenticatedInfoFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines users stream and authenticated user info stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val user1 = LoginUserDomainModel(
                id = 1234,
                name = "test user1",
                email = "test_user1@test.com",
                age = 56
            )
            val user2 = LoginUserDomainModel(
                id = 5678,
                name = "test user2",
                email = "test_user2@test.com",
                age = 56
            )
            val users = listOf(user1, user2)

            val usersFlow =
                MutableStateFlow<CachePayload<List<LoginUserDomainModel>>>(
                    value = CachePayload.ActiveStream(
                        data = users,
                        isSyncing = false,
                        syncError = null
                    )
                )
            val userAuthenticatedInfoFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Success(data = user1)
            )

            every { userRepository.getUsers() } returns usersFlow
            every { userRepository.getUserAuthenticatedInfoFlow() } returns userAuthenticatedInfoFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualUserOptions = (nextItem as Resource.Success).data
                assertIs<List<UserOptionDomainModel>>(actualUserOptions)
                assertEquals(2, actualUserOptions.size)
                assertEquals(
                    expected = UserOptionDomainModel(
                        id = 1234,
                        name = "test user1",
                        isAdmin = true
                    ),
                    actual = actualUserOptions[0]
                )
                assertEquals(
                    expected = UserOptionDomainModel(
                        id = 5678,
                        name = "test user2",
                        isAdmin = false
                    ),
                    actual = actualUserOptions[1]
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines slow user stream and authenticated user info stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val user1 = LoginUserDomainModel(
                id = 1234,
                name = "test user1",
                email = "test_user1@test.com",
                age = 56
            )

            val usersFlow =
                MutableStateFlow<CachePayload<List<LoginUserDomainModel>>>(
                    value = CachePayload.ActiveStream(
                        data = emptyList(),
                        isSyncing = true,
                        syncError = null
                    )
                )
            val userAuthenticatedInfoFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Success(data = user1)
            )

            every { userRepository.getUsers() } returns usersFlow
            every { userRepository.getUserAuthenticatedInfoFlow() } returns userAuthenticatedInfoFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualUserOptions = (nextItem as Resource.Success).data
                assertIs<List<UserOptionDomainModel>>(actualUserOptions)
                assertEquals(0, actualUserOptions.size)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines user stream and slow authenticated user info stream into enriched Resource Success`() =
        runTest {
            val user1 = LoginUserDomainModel(
                id = 1234,
                name = "test user1",
                email = "test_user1@test.com",
                age = 56
            )
            val user2 = LoginUserDomainModel(
                id = 5678,
                name = "test user2",
                email = "test_user2@test.com",
                age = 56
            )
            val users = listOf(user1, user2)

            val usersFlow =
                MutableStateFlow<CachePayload<List<LoginUserDomainModel>>>(
                    value = CachePayload.ActiveStream(
                        data = users,
                        isSyncing = false,
                        syncError = null
                    )
                )
            val userAuthenticatedInfoFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Loading
            )

            every { userRepository.getUsers() } returns usersFlow
            every { userRepository.getUserAuthenticatedInfoFlow() } returns userAuthenticatedInfoFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualUserOptions = (nextItem as Resource.Success).data
                assertIs<List<UserOptionDomainModel>>(actualUserOptions)
                assertEquals(2, actualUserOptions.size)
                assertEquals(
                    expected = UserOptionDomainModel(
                        id = 1234,
                        name = "test user1",
                        isAdmin = false // do not yet if this is the authenticated user!
                    ),
                    actual = actualUserOptions[0]
                )
                assertEquals(
                    expected = UserOptionDomainModel(
                        id = 5678,
                        name = "test user2",
                        isAdmin = false
                    ),
                    actual = actualUserOptions[1]
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines 1 Error and 1 Exception to Resource Success correctly`() =
        runTest {

            // Arrange
            val usersFlow =
                MutableStateFlow<CachePayload<List<LoginUserDomainModel>>>(
                    value = CachePayload.ActiveStream(
                        data = emptyList(),
                        isSyncing = false,
                        syncError = HttpException(
                            Response.error<Int>(
                                503,
                                "Service Unavailable"
                                    .toResponseBody(
                                        "application/json".toMediaTypeOrNull()
                                    )
                            )
                        )
                    )
                )
            val userAuthenticatedInfoFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Exception(Exception("Something went wrong"))
            )

            every { userRepository.getUsers() } returns usersFlow
            every { userRepository.getUserAuthenticatedInfoFlow() } returns userAuthenticatedInfoFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualUserOptions = (nextItem as Resource.Success).data
                assertIs<List<UserOptionDomainModel>>(actualUserOptions)
                assertEquals(0, actualUserOptions.size)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines 2 Errors to Resource Success correctly`() =
        runTest {

            // Arrange
            val usersFlow =
                MutableStateFlow<CachePayload<List<LoginUserDomainModel>>>(
                    value = CachePayload.ActiveStream(
                        data = emptyList(),
                        isSyncing = false,
                        syncError = HttpException(
                            Response.error<Int>(
                                503,
                                "Service Unavailable"
                                    .toResponseBody(
                                        "application/json".toMediaTypeOrNull()
                                    )
                            )
                        )
                    )
                )
            val userAuthenticatedInfoFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Error(code = 503, message = "Service is unavailable")
            )

            every { userRepository.getUsers() } returns usersFlow
            every { userRepository.getUserAuthenticatedInfoFlow() } returns userAuthenticatedInfoFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Error)

                val errorMessage = (nextItem as Resource.Error).message
                Assert.assertEquals("Unable to load user list data.", errorMessage)

                cancelAndIgnoreRemainingEvents()
            }
        }


}