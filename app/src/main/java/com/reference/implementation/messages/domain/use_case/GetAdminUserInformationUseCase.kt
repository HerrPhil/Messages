package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserOptionDomainModel
import com.reference.implementation.messages.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart

class GetAdminUserInformationUseCase(
    private val userRepository: UserRepository
) {

    /**
     * Here is the Flow-based solution to get user dropdown information
     */
    operator fun invoke(): Flow<Resource<List<UserOptionDomainModel>?>> {
        return combine(
            userRepository.getUsers(),
            userRepository.getUserInfoFlow() // the admin session user
        ) { listRes, sessionRes ->

            // Check of catastrophic hard error (e.g. streams failed)
            val allFailed = listOf(
                listRes, sessionRes
            ).all { it is NetworkResult.Error }

            if (allFailed) {
                Resource.Error("Unable to load user list data.")
            } else {

                // Extracts the data safely if Success: otherwise keep null
                val listInfo = (listRes as? NetworkResult.Success)?.data
                val sessionUserInfo = (sessionRes as? NetworkResult.Success)?.data

                val userOptions: List<UserOptionDomainModel>? = listInfo?.map { userItem ->
                    UserOptionDomainModel(
                        id = userItem.id,
                        name = userItem.name,
                        isAdmin = userItem.id == sessionUserInfo?.id
                    )
                }
                Resource.Success(userOptions)
            }
        }
            .onStart { emit(Resource.Loading) }
            .flowOn(Dispatchers.Default)
    }
}