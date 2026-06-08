package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import com.reference.implementation.messages.domain.repository.UserRepository

class UserRepositoryImpl(
    private val sessionManager: SessionManager,
) : UserRepository {
    override suspend fun getUserInfo(onRetry: suspend (Int) -> Unit): NetworkResult<LoginUserDomainModel> {
        val userName = when(val userNameSessionResult = sessionManager.getSessionUserName()) {
            is SessionResult.Authenticated -> userNameSessionResult.data
            else -> "no name"
        }
        val userEmail = when(val userEmailSessionResult = sessionManager.getSessionUserEmail()) {
            is SessionResult.Authenticated -> userEmailSessionResult.data
            else -> "no email"
        }
        val userDomainModel = LoginUserDomainModel(name = userName, email = userEmail)
        return NetworkResult.Success(userDomainModel)
    }
}