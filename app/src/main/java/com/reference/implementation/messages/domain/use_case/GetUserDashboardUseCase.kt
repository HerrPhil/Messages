package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserDashboardDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import com.reference.implementation.messages.domain.repository.PermissionRepository
import com.reference.implementation.messages.domain.repository.RoleRepository
import com.reference.implementation.messages.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okio.IOException
import retrofit2.HttpException

class GetUserDashboardUseCase(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository
) {
    // Converts the calls into a Flow state machine for the UI
    operator fun invoke(
        onRetryString: suspend (String) -> Unit
    ): Flow<Resource<UserDashboardDomainModel>> = flow {

        emit(getResource(onRetryString))

    }.onStart { emit(Resource.Loading) }

    private suspend fun getResource(onRetryString: suspend (String) -> Unit): Resource<UserDashboardDomainModel> {
        // Warning: kotlinlang.org flow documentation says this violates context preservation.
        // If we see IllegalStateException here, then use .flowOn(Dispatchers.Default).
        // Move execution to the Default thread pool

        return withContext(Dispatchers.Default) {

            var resource: Resource<UserDashboardDomainModel> = Resource.Loading

            // 1. Kick off all Retrofit network calls completely in parallel.
            val userDeferred = async {
                val onRetry: suspend (Int) -> Unit = { attempt ->
                    onRetryString("user attempt $attempt")
                }
                userRepository.getUserInfo(onRetry)
            }
            val messageDeferred = async {
                val onRetry: suspend (Int) -> Unit = { attempt ->
                    onRetryString("message attempt $attempt")
                }
                messageRepository.getMessagesByUser(onRetry)
            }
            val roleDeferred = async {
                val onRetry: suspend (Int) -> Unit = { attempt ->
                    onRetryString("role attempt $attempt")
                }
                roleRepository.getRoleInfo(onRetry)
            }
            val permissionDeferred = async {
                val onRetry: suspend (Int) -> Unit = { attempt ->
                    onRetryString("permission attempt $attempt")
                }
                permissionRepository.getPermissionInfo(onRetry)
            }

            // 2. Await all responses simultaneously.
            val userResult = userDeferred.await() // NetworkResult<UserDomainModel>
            val messageResult = messageDeferred.await() // NetworkResult<UserMessageDomainModel>
            val roleResult = roleDeferred.await() // NetworkResult<UserRoleDomainModel>
            val permissionResult =
                permissionDeferred.await() // NetworkResult<UserPermissionDomainModel>
            val results = listOf(
                userResult, messageResult, roleResult, permissionResult
            )
            val allSuccess = results.all { it is NetworkResult.Success }

            // 3. Handle your NetworkResult wrapper logic
            if (allSuccess) {
                val userName = when (userResult is NetworkResult.Success) {
                    true -> userResult.data.name
                    false -> "no name"
                }
                val userEmail = when (userResult is NetworkResult.Success) {
                    true -> userResult.data.email
                    false -> "no email"
                }
                val messages = when (messageResult is NetworkResult.Success) {
                    true -> messageResult.data
                    false -> emptyList()
                }
                val unreadMessages = messages.count { messageDomainModel ->
                    !messageDomainModel.read
                }
                val readMessages = messages.count { messageDomainModel ->
                    messageDomainModel.read
                }
                val roles = when (roleResult is NetworkResult.Success) {
                    true -> roleResult.data.roles
                    false -> emptyList()
                }
                val permissions = when (permissionResult is NetworkResult.Success) {
                    true -> permissionResult.data.permissions
                    false -> emptyList()
                }

                val userDashboardDomainModel = UserDashboardDomainModel(
                    userName = userName,
                    userEmail = userEmail,
                    unreadMessages = unreadMessages,
                    readMessages = readMessages,
                    roles = roles,
                    permissions = permissions
                )

                resource = Resource.Success(userDashboardDomainModel) // value put in flow

            } else {
                val anyError = results.any { it is NetworkResult.Error }
                val anyException = results.any { it is NetworkResult.Exception }
                if (anyError) {
                    val error = results.first { it is NetworkResult.Error }
                    if (error is NetworkResult.Error) {

                        resource = getResourceErrorByCode("", error.code) // value put in flow

                    }
                } else if (anyException) {
                    val exception = results.first { it is NetworkResult.Exception }
                    if (exception is NetworkResult.Exception) {
                        val resourceException = when (exception.e) {
                            is IOException -> Resource.Error("No internet connection")
                            is HttpException -> {
                                exception.e.code()
                                getResourceErrorByCode("user", exception.e.code())
                            }

                            else -> Resource.Error("Unknown error occurred")
                        }

                        resource = resourceException // value put in flow

                    }
                } else {

                    resource = Resource.Error("Something went wrong") // value put in flow

                }
            }

            return@withContext resource
        }

    }


}
