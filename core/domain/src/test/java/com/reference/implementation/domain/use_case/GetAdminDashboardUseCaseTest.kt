package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class GetAdminDashboardUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val messageRepository: MessageRepository = mockk()
    private val bulletinRepository: BulletinRepository = mockk()
    private lateinit var useCase: GetAdminDashboardUseCase // under test

    @Before
    fun setUp() {
        useCase = GetAdminDashboardUseCase(
            userRepository,
            messageRepository,
            bulletinRepository
        )
    }

    @Test
    fun `invoke starts on Resource Loading`() = runTest {

        val userFlow = MutableStateFlow<CachePayload<Int>>(
            CachePayload.ActiveStream(
                0,
                true,
                null
            )
        )
        val messagesFlow = MutableStateFlow<CachePayload<String>>(
            CachePayload.ActiveStream(
                "0 / 0",
                true,
                null
            )
        )
        val bulletinFlow = MutableStateFlow<CachePayload<Int>>(
            CachePayload.ActiveStream(
                0,
                true,
                null
            )
        )

        every { userRepository.getUserCount(any()) } returns userFlow
        every { messageRepository.getSummaryMessages(any()) } returns messagesFlow
        every { bulletinRepository.getBulletinCount(any()) } returns bulletinFlow

        useCase().test {
            val initialItem = awaitItem()
            assertEquals(initialItem, Resource.Loading)
            cancelAndIgnoreRemainingEvents()
        }

    }

    @Test
    fun `invoke combines user stream and message stream and bulletin stream into enriched Resource Success`() =
        runTest {
            val numberOfUsers = 7
            val userFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    numberOfUsers,
                    false,
                    null
                )
            )
            val messagesSummary = "4 / 8"
            val messageFlow = MutableStateFlow<CachePayload<String>>(
                CachePayload.ActiveStream(
                    messagesSummary,
                    false,
                    null
                )
            )
            val numberOfBulletins = 9
            val bulletinFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    numberOfBulletins,
                    false,
                    null
                )
            )

            every { userRepository.getUserCount(any()) } returns userFlow
            every { messageRepository.getSummaryMessages(any()) } returns messageFlow
            every { bulletinRepository.getBulletinCount(any()) } returns bulletinFlow

            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 1. Next emission: Resource.Success(AdminDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 2. the user count
                assertEquals(numberOfUsers, actualDashboard.usersCount)

                // 3. the message info
                assertEquals(messagesSummary, actualDashboard.summaryMessages)

                // 4. the bulletin count
                assertEquals(numberOfBulletins, actualDashboard.bulletinsCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines slow user stream and message stream and bulletin stream into enriched Resource Success`() =
        runTest {
            val userFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    0,
                    true,
                    null
                )
            )
            val messagesSummary = "4 / 8"
            val messageFlow = MutableStateFlow<CachePayload<String>>(
                CachePayload.ActiveStream(
                    messagesSummary,
                    false,
                    null
                )
            )
            val numberOfBulletins = 9
            val bulletinFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    numberOfBulletins,
                    false,
                    null
                )
            )

            every { userRepository.getUserCount(any()) } returns userFlow
            every { messageRepository.getSummaryMessages(any()) } returns messageFlow
            every { bulletinRepository.getBulletinCount(any()) } returns bulletinFlow

            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(AdminDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertEquals(0, actualDashboard.usersCount)

                // 4. the message info
                assertEquals(messagesSummary, actualDashboard.summaryMessages)

                // 5. the bulletin count
                assertEquals(numberOfBulletins, actualDashboard.bulletinsCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines user stream and slow message stream and bulletin stream into enriched Resource Success`() =
        runTest {
            val numberOfUsers = 7
            val userFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    numberOfUsers,
                    false,
                    null
                )
            )
            val messageFlow = MutableStateFlow<CachePayload<String>>(
                CachePayload.ActiveStream(
                    "0 / 0",
                    true,
                    null
                )
            )
            val numberOfBulletins = 9
            val bulletinFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    numberOfBulletins,
                    false,
                    null
                )
            )

            every { userRepository.getUserCount(any()) } returns userFlow
            every { messageRepository.getSummaryMessages(any()) } returns messageFlow
            every { bulletinRepository.getBulletinCount(any()) } returns bulletinFlow

            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(AdminDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertEquals(numberOfUsers, actualDashboard.usersCount)

                // 4. the message info
                assertEquals("0 / 0", actualDashboard.summaryMessages)

                // 5. the bulletin count
                assertEquals(numberOfBulletins, actualDashboard.bulletinsCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines user stream and message stream and slow bulletin stream into enriched Resource Success`() =
        runTest {
            val numberOfUsers = 7
            val userFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    numberOfUsers,
                    false,
                    null
                )
            )
            val messagesSummary = "4 / 8"
            val messageFlow = MutableStateFlow<CachePayload<String>>(
                CachePayload.ActiveStream(
                    messagesSummary,
                    false,
                    null
                )
            )
            val bulletinFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    0,
                    true,
                    null
                )
            )

            every { userRepository.getUserCount(any()) } returns userFlow
            every { messageRepository.getSummaryMessages(any()) } returns messageFlow
            every { bulletinRepository.getBulletinCount(any()) } returns bulletinFlow

            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(AdminDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertEquals(numberOfUsers, actualDashboard.usersCount)

                // 4. the message info
                assertEquals(messagesSummary, actualDashboard.summaryMessages)

                // 5. the bulletin count
                assertEquals(0, actualDashboard.bulletinsCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines CachePayload 2 Errors and 1 Exception to Resource Success correctly`() =
        runTest {
            val userFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    0,
                    false,
                    HttpException(
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
            val messageFlow = MutableStateFlow<CachePayload<String>>(
                CachePayload.ActiveStream(
                    "0 / 0",
                    false,
                    IOException("Network down")
                )
            )
            val bulletinFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    0,
                    false,
                    HttpException(
                        Response.error<Int>(
                            400,
                            "bad request of permissions"
                                .toResponseBody(
                                    "application/json".toMediaTypeOrNull()
                                )
                        )
                    )
                )
            )

            every { userRepository.getUserCount(any()) } returns userFlow
            every { messageRepository.getSummaryMessages(any()) } returns messageFlow
            every { bulletinRepository.getBulletinCount(any()) } returns bulletinFlow

            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Error)

                val errorMessage = (nextItem as Resource.Error).message

                // 3. the user info
                assertEquals("Unable to load dashboard data.", errorMessage)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines NetworkResult 3 Errors to Resource Success correctly`() =
        runTest {
            val userFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    0,
                    false,
                    HttpException(
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
            val messageFlow = MutableStateFlow<CachePayload<String>>(
                CachePayload.ActiveStream(
                    "0 / 0",
                    false,
                    HttpException(
                        Response.error<Int>(
                            403,
                            "Messages forbidden"
                                .toResponseBody(
                                    "application/json".toMediaTypeOrNull()
                                )
                        )
                    )
                )
            )
            val bulletinFlow = MutableStateFlow<CachePayload<Int>>(
                CachePayload.ActiveStream(
                    0,
                    false,
                    HttpException(
                        Response.error<Int>(
                            400,
                            "bad request of permissions"
                                .toResponseBody(
                                    "application/json".toMediaTypeOrNull()
                                )
                        )
                    )
                )
            )

            every { userRepository.getUserCount(any()) } returns userFlow
            every { messageRepository.getSummaryMessages(any()) } returns messageFlow
            every { bulletinRepository.getBulletinCount(any()) } returns bulletinFlow

            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 1. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Error)

                val errorMessage = (nextItem as Resource.Error).message
                assertEquals("Unable to load dashboard data.", errorMessage)

                cancelAndIgnoreRemainingEvents()
            }
        }
}