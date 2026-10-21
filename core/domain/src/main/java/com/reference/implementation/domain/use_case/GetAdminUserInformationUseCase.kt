package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.util.NetworkResult
import com.reference.implementation.domain.model.UserOptionDomainModel
import com.reference.implementation.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAdminUserInformationUseCase @Inject constructor(
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

            // Extract the specific ActiveStream type natively using Kotlin smart-casting
            val listStream = listRes as CachePayload.ActiveStream

            // Check of catastrophic hard error (e.g. streams failed)
            val listFailed = listStream.syncError != null
            val sessionFailed = sessionRes is NetworkResult.Error
            val allFailed = listFailed && sessionFailed

            if (allFailed) {
                Resource.Error("Unable to load user list data.")
            } else {

                // Extracts the data safely if Success: otherwise keep null
                val listInfo = listStream.data
                val sessionUserInfo = (sessionRes as? NetworkResult.Success)?.data

                // map from LoginUserDomainModel to UserOptionDomainModel
                val userOptions: List<UserOptionDomainModel> = listInfo.map { userItem ->
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