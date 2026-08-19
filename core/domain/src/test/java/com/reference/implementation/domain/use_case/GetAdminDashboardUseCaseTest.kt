package com.reference.implementation.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.UserRepository
import com.reference.implementation.domain.util.NetworkResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

        val userFlow = MutableStateFlow<NetworkResult<Int>>(
            NetworkResult.Loading
        )
        val messagesFlow = MutableStateFlow<NetworkResult<String>>(
            NetworkResult.Loading
        )
        val bulletinFlow = MutableStateFlow<NetworkResult<Int>>(
            NetworkResult.Loading
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
            val userFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Success(numberOfUsers)
            )
            val messagesSummary = "4 / 8"
            val messageFlow = MutableStateFlow<NetworkResult<String>>(
                NetworkResult.Success(messagesSummary)
            )
            val numberOfBulletins = 9
            val bulletinFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Success(numberOfBulletins)
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
            val userFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Loading
            )
            val messagesSummary = "4 / 8"
            val messageFlow = MutableStateFlow<NetworkResult<String>>(
                NetworkResult.Success(messagesSummary)
            )
            val numberOfBulletins = 9
            val bulletinFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Success(numberOfBulletins)
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
                assertNull(actualDashboard.usersCount)

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
            val userFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Success(numberOfUsers)
            )
            val messageFlow = MutableStateFlow<NetworkResult<String>>(
                NetworkResult.Loading
            )
            val numberOfBulletins = 9
            val bulletinFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Success(numberOfBulletins)
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
                assertNull(actualDashboard.summaryMessages)

                // 5. the bulletin count
                assertEquals(numberOfBulletins, actualDashboard.bulletinsCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines user stream and message stream and slow bulletin stream into enriched Resource Success`() =
        runTest {
            val numberOfUsers = 7
            val userFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Success(numberOfUsers)
            )
            val messagesSummary = "4 / 8"
            val messageFlow = MutableStateFlow<NetworkResult<String>>(
                NetworkResult.Success(messagesSummary)
            )
            val bulletinFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Loading
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
                assertNull(actualDashboard.bulletinsCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines NetworkResult 2 Errors and 1 Exception to Resource Success correctly`() =
        runTest {
            val userFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Error(503, "Service Unavailable")
            )
            val messageFlow = MutableStateFlow<NetworkResult<String>>(
                NetworkResult.Exception(IOException("Network down"))
            )
            val bulletinFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Error(400, "Bad Request of Permissions")
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
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertNull(actualDashboard.usersCount)

                // 4. the message info
                assertNull(actualDashboard.summaryMessages)

                // 5. the bulletin count
                assertNull(actualDashboard.bulletinsCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines NetworkResult 3 Errors to Resource Success correctly`() =
        runTest {
            val userFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Error(503, "Service Unavailable")
            )
            val messageFlow = MutableStateFlow<NetworkResult<String>>(
                NetworkResult.Error(403, "Messages forbidden")
            )
            val bulletinFlow = MutableStateFlow<NetworkResult<Int>>(
                NetworkResult.Error(400, "Bad Request of Permissions")
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