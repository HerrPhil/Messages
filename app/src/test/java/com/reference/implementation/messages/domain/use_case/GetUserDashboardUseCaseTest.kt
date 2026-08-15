package com.reference.implementation.messages.domain.use_case

import app.cash.turbine.test
import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.domain.model.UserPermissionDomainModel
import com.reference.implementation.messages.domain.model.UserRoleDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import com.reference.implementation.messages.domain.repository.PermissionRepository
import com.reference.implementation.messages.domain.repository.RoleRepository
import com.reference.implementation.messages.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class GetUserDashboardUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val messageRepository: MessageRepository = mockk()
    private val roleRepository: RoleRepository = mockk()
    private val permissionRepository: PermissionRepository = mockk()
    private lateinit var useCase: GetUserDashboardUseCase // under test

    @Before
    fun setUp() {
        useCase = GetUserDashboardUseCase(
            userRepository,
            messageRepository,
            roleRepository,
            permissionRepository
        )
    }

    @Test
    fun `invoke starts on Resource Loading`() = runTest {

        // Arrange
        val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
            NetworkResult.Loading
        )

        val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
            NetworkResult.Loading
        )

        val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
            NetworkResult.Loading
        )

        val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
            NetworkResult.Loading
        )

        every { userRepository.getUserInfoFlow() } returns userFlow
        every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
        every { roleRepository.getRoleInfoFlow() } returns roleFlow
        every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

        // Act & Assert
        useCase().test {
            // 1. Initial emission: onStart outputs Loading
            val initialItem = awaitItem()
            assertTrue(initialItem is Resource.Loading)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke combines user stream and message stream and role stream and permission stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val user1 = LoginUserDomainModel("test_user@test.com", "test user", 1234)
            val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Success(user1)
            )

            val message1 = MessageDomainModel(
                id = 101,
                subject = "Hey there",
                body = "this is test message one",
                read = false,
                userId = 123,
                createdAt = "2026-08-01T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-01T10:00:00Z"),
                isImportant = false
            )
            val message2 = MessageDomainModel(
                id = 102,
                subject = "Good bye",
                body = "this is test message two",
                read = false,
                userId = 987,
                createdAt = "2026-08-02T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-02T10:00:00Z"),
                isImportant = false
            )
            val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
                NetworkResult.Success(listOf(message1, message2))
            )

            val roleInfo = UserRoleDomainModel(
                listOf("Developer", "Tester", "Manager")
            )
            val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
                NetworkResult.Success(roleInfo)
            )

            val permissionInfo = UserPermissionDomainModel(
                listOf("Coding", "Peer Review", "Deployment")
            )
            val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
                NetworkResult.Success(permissionInfo)
            )

            every { userRepository.getUserInfoFlow() } returns userFlow
            every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
            every { roleRepository.getRoleInfoFlow() } returns roleFlow
            every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertEquals("test user", actualDashboard.userName)
                assertEquals("test_user@test.com", actualDashboard.userEmail)
                assertEquals("test_user@test.com", actualDashboard.userEmail)

                // 4. the message info
                assertEquals(2, actualDashboard.unreadMessages)
                assertEquals(0, actualDashboard.readMessages)

                // 5. role info
                assertFalse(actualDashboard.roles?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.roles?.size ?: 0)
                assertEquals("Developer", actualDashboard.roles?.get(0))
                assertEquals("Tester", actualDashboard.roles?.get(1))
                assertEquals("Manager", actualDashboard.roles?.get(2))

                // 6. permission info
                assertFalse(actualDashboard.permissions?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.permissions?.size ?: 0)
                assertEquals("Coding", actualDashboard.permissions?.get(0))
                assertEquals("Peer Review", actualDashboard.permissions?.get(1))
                assertEquals("Deployment", actualDashboard.permissions?.get(2))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines slow user stream and message stream and role stream and permission stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Loading
            )

            val message1 = MessageDomainModel(
                id = 101,
                subject = "Hey there",
                body = "this is test message one",
                read = false,
                userId = 123,
                createdAt = "2026-08-01T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-01T10:00:00Z"),
                isImportant = false
            )
            val message2 = MessageDomainModel(
                id = 102,
                subject = "Good bye",
                body = "this is test message two",
                read = false,
                userId = 987,
                createdAt = "2026-08-02T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-02T10:00:00Z"),
                isImportant = false
            )
            val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
                NetworkResult.Success(listOf(message1, message2))
            )

            val roleInfo = UserRoleDomainModel(
                listOf("Developer", "Tester", "Manager")
            )
            val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
                NetworkResult.Success(roleInfo)
            )

            val permissionInfo = UserPermissionDomainModel(
                listOf("Coding", "Peer Review", "Deployment")
            )
            val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
                NetworkResult.Success(permissionInfo)
            )

            every { userRepository.getUserInfoFlow() } returns userFlow
            every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
            every { roleRepository.getRoleInfoFlow() } returns roleFlow
            every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertNull(actualDashboard.userName)
                assertNull(actualDashboard.userEmail)

                // 4. the message info
                assertEquals(2, actualDashboard.unreadMessages)
                assertEquals(0, actualDashboard.readMessages)

                // 5. role info
                assertFalse(actualDashboard.roles?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.roles?.size ?: 0)
                assertEquals("Developer", actualDashboard.roles?.get(0))
                assertEquals("Tester", actualDashboard.roles?.get(1))
                assertEquals("Manager", actualDashboard.roles?.get(2))

                // 6. permission info
                assertFalse(actualDashboard.permissions?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.permissions?.size ?: 0)
                assertEquals("Coding", actualDashboard.permissions?.get(0))
                assertEquals("Peer Review", actualDashboard.permissions?.get(1))
                assertEquals("Deployment", actualDashboard.permissions?.get(2))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines user stream and slow message stream and role stream and permission stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val user1 = LoginUserDomainModel("test_user@test.com", "test user", 1234)
            val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Success(user1)
            )

            val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
                NetworkResult.Loading
            )

            val roleInfo = UserRoleDomainModel(
                listOf("Developer", "Tester", "Manager")
            )
            val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
                NetworkResult.Success(roleInfo)
            )

            val permissionInfo = UserPermissionDomainModel(
                listOf("Coding", "Peer Review", "Deployment")
            )
            val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
                NetworkResult.Success(permissionInfo)
            )

            every { userRepository.getUserInfoFlow() } returns userFlow
            every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
            every { roleRepository.getRoleInfoFlow() } returns roleFlow
            every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertEquals("test user", actualDashboard.userName)
                assertEquals("test_user@test.com", actualDashboard.userEmail)

                // 4. the message info
                assertNull(actualDashboard.unreadMessages)
                assertNull(actualDashboard.readMessages)

                // 5. role info
                assertFalse(actualDashboard.roles?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.roles?.size ?: 0)
                assertEquals("Developer", actualDashboard.roles?.get(0))
                assertEquals("Tester", actualDashboard.roles?.get(1))
                assertEquals("Manager", actualDashboard.roles?.get(2))

                // 6. permission info
                assertFalse(actualDashboard.permissions?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.permissions?.size ?: 0)
                assertEquals("Coding", actualDashboard.permissions?.get(0))
                assertEquals("Peer Review", actualDashboard.permissions?.get(1))
                assertEquals("Deployment", actualDashboard.permissions?.get(2))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines user stream and message stream and slow role stream and permission stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val user1 = LoginUserDomainModel("test_user@test.com", "test user", 1234)
            val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Success(user1)
            )

            val message1 = MessageDomainModel(
                id = 101,
                subject = "Hey there",
                body = "this is test message one",
                read = false,
                userId = 123,
                createdAt = "2026-08-01T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-01T10:00:00Z"),
                isImportant = false
            )
            val message2 = MessageDomainModel(
                id = 102,
                subject = "Good bye",
                body = "this is test message two",
                read = false,
                userId = 987,
                createdAt = "2026-08-02T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-02T10:00:00Z"),
                isImportant = false
            )
            val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
                NetworkResult.Success(listOf(message1, message2))
            )

            val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
                NetworkResult.Loading
            )

            val permissionInfo = UserPermissionDomainModel(
                listOf("Coding", "Peer Review", "Deployment")
            )
            val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
                NetworkResult.Success(permissionInfo)
            )

            every { userRepository.getUserInfoFlow() } returns userFlow
            every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
            every { roleRepository.getRoleInfoFlow() } returns roleFlow
            every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertEquals("test user", actualDashboard.userName)
                assertEquals("test_user@test.com", actualDashboard.userEmail)

                // 4. the message info
                assertEquals(2, actualDashboard.unreadMessages)
                assertEquals(0, actualDashboard.readMessages)

                // 5. role info
                assertNull(actualDashboard.roles)

                // 6. permission info
                assertFalse(actualDashboard.permissions?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.permissions?.size ?: 0)
                assertEquals("Coding", actualDashboard.permissions?.get(0))
                assertEquals("Peer Review", actualDashboard.permissions?.get(1))
                assertEquals("Deployment", actualDashboard.permissions?.get(2))

                cancelAndIgnoreRemainingEvents()
            }
        }


    @Test
    fun `invoke combines user stream and message stream and role stream and slow permission stream into enriched Resource Success`() =
        runTest {

            // Arrange
            val user1 = LoginUserDomainModel("test_user@test.com", "test user", 1234)
            val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Success(user1)
            )

            val message1 = MessageDomainModel(
                id = 101,
                subject = "Hey there",
                body = "this is test message one",
                read = false,
                userId = 123,
                createdAt = "2026-08-01T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-01T10:00:00Z"),
                isImportant = false
            )
            val message2 = MessageDomainModel(
                id = 102,
                subject = "Good bye",
                body = "this is test message two",
                read = false,
                userId = 987,
                createdAt = "2026-08-02T10:00:00Z",
                createdAtInstant = Instant.parse("2026-08-02T10:00:00Z"),
                isImportant = false
            )
            val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
                NetworkResult.Success(listOf(message1, message2))
            )

            val roleInfo = UserRoleDomainModel(
                listOf("Developer", "Tester", "Manager")
            )
            val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
                NetworkResult.Success(roleInfo)
            )

            val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
                NetworkResult.Loading
            )

            every { userRepository.getUserInfoFlow() } returns userFlow
            every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
            every { roleRepository.getRoleInfoFlow() } returns roleFlow
            every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertEquals("test user", actualDashboard.userName)
                assertEquals("test_user@test.com", actualDashboard.userEmail)

                // 4. the message info
                assertEquals(2, actualDashboard.unreadMessages)
                assertEquals(0, actualDashboard.readMessages)

                // 5. role info
                assertFalse(actualDashboard.roles?.isEmpty() ?: false)
                assertEquals(3, actualDashboard.roles?.size ?: 0)
                assertEquals("Developer", actualDashboard.roles?.get(0))
                assertEquals("Tester", actualDashboard.roles?.get(1))
                assertEquals("Manager", actualDashboard.roles?.get(2))

                // 6. permission info
                assertNull(actualDashboard.permissions)

                cancelAndIgnoreRemainingEvents()
            }
        }


    @Test
    fun `invoke combines NetworkResult 3 Errors and 1 Exception to Resource Success correctly`() =
        runTest {

            // Arrange
            val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Error(503, "Service Unavailable")
            )
            val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
                NetworkResult.Error(503, "Service Unavailable")
            )
            val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
                NetworkResult.Exception(IOException("Network down"))
            )
            val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
                NetworkResult.Error(400, "Bad Request of Permissions")
            )

            every { userRepository.getUserInfoFlow() } returns userFlow
            every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
            every { roleRepository.getRoleInfoFlow() } returns roleFlow
            every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 2. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Success)

                val actualDashboard = (nextItem as Resource.Success).data

                // 3. the user info
                assertNull(actualDashboard.userName)
                assertNull(actualDashboard.userEmail)

                // 4. the message info
                assertNull(actualDashboard.unreadMessages)
                assertNull(actualDashboard.readMessages)

                // 5. role info
                assertNull(actualDashboard.roles)

                // 6. permission info
                assertNull(actualDashboard.permissions)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invoke combines NetworkResult 4 Errors to Resource Error correctly`() =
        runTest {

            // Arrange
            val userFlow = MutableStateFlow<NetworkResult<LoginUserDomainModel>>(
                NetworkResult.Error(503, "Service Unavailable")
            )
            val messagesFlow = MutableStateFlow<NetworkResult<List<MessageDomainModel>>>(
                NetworkResult.Error(503, "Service Unavailable")
            )
            val roleFlow = MutableStateFlow<NetworkResult<UserRoleDomainModel>>(
                NetworkResult.Error(403, "Roles forbidden")
            )
            val permissionFlow = MutableStateFlow<NetworkResult<UserPermissionDomainModel>>(
                NetworkResult.Error(400, "Bad Request of Permissions")
            )

            every { userRepository.getUserInfoFlow() } returns userFlow
            every { messageRepository.getMessagesByUserFlow(any()) } returns messagesFlow
            every { roleRepository.getRoleInfoFlow() } returns roleFlow
            every { permissionRepository.getPermissionInfoFlow(any()) } returns permissionFlow

            // Act & Assert
            useCase().test {
                // 1. Initial emission: onStart outputs Loading
                val initialItem = awaitItem()
                assertTrue(initialItem is Resource.Loading)

                // 3. Next emission: Resource.Success(UserDashboardDomainModel)
                val nextItem = awaitItem()
                assertTrue(nextItem is Resource.Error)

                // 4. Assert there is only error
                val errorMessage = (nextItem as Resource.Error).message
                assertEquals("Unable to load dashboard data.", errorMessage)

                cancelAndIgnoreRemainingEvents()
            }
        }


}