package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserDashboardDomainModel
import com.reference.implementation.messages.domain.repository.MessageRepository
import com.reference.implementation.messages.domain.repository.PermissionRepository
import com.reference.implementation.messages.domain.repository.RoleRepository
import com.reference.implementation.messages.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart

class GetUserDashboardUseCase(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository
) {

    /**
     * Here is the Flow-based solution to get user dashboard information
     */
    operator fun invoke(
        onRetry: suspend (Int) -> Unit = {}
    ): Flow<Resource<UserDashboardDomainModel>> {
        return combine(
            userRepository.getUserInfoFlow(),
            messageRepository.getMessagesByUserFlow(onRetry),
            roleRepository.getRoleInfoFlow(),
            permissionRepository.getPermissionInfoFlow(onRetry)
        ) { userRes, messageRes, roleRes, permissionRes ->

            val user = (userRes as? NetworkResult.Success)?.data
            val messages = (messageRes as? NetworkResult.Success)?.data ?: emptyList()
            val roles = (roleRes as? NetworkResult.Success)?.data?.roles ?: emptyList()
            val permissions =
                (permissionRes as? NetworkResult.Success)?.data?.permissions ?: emptyList()

            if (user == null) {
                Resource.Error("Failed to load user profile")
            } else {
                Resource.Success(
                    UserDashboardDomainModel(
                        userName = user.name,
                        userEmail = user.email,
                        unreadMessages = messages.count { !it.read },
                        readMessages = messages.count { it.read },
                        roles = roles,
                        permissions = permissions
                    )
                )
            }
        }
            .onStart { emit(Resource.Loading) }
            .flowOn(Dispatchers.Default)
    }
}