package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.manager.SessionResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import com.reference.implementation.messages.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class UserRepositoryImpl(
    private val sessionManager: SessionManager,
) : UserRepository {

    /**
     * Emits:
     * - user info stored in the session manager at login
     */
    override fun getUserInfoFlow(): Flow<NetworkResult<LoginUserDomainModel>> = flow {
        // Emit Loading state initially
        emit(NetworkResult.Loading)

        // Step 1: Fetch the user info results from session manager
        val userName = when (val userNameSessionResult = sessionManager.getSessionUserName()) {
            is SessionResult.Authenticated -> userNameSessionResult.data
            else -> "no name"
        }
        val userEmail = when (val userEmailSessionResult = sessionManager.getSessionUserEmail()) {
            is SessionResult.Authenticated -> userEmailSessionResult.data
            else -> "no email"
        }

        // Step 2: Contain the user info in a Domain Model
        val userDomainModel = LoginUserDomainModel(
            name = userName,
            email = userEmail
        )

        // Step 3: Emit the Domain Model
        emit(NetworkResult.Success(userDomainModel))
    }.catch { e ->
        if (e is CancellationException) throw e
        Audit.createInstance().writeLog(e.message ?: "no user info")
        emit(NetworkResult.Exception(e))
    }.onCompletion {
        withContext(NonCancellable) {
            Audit.createInstance().writeLog("${auditLogTimestamp()} get user info ended")
        }
    }.flowOn(Dispatchers.Default)
}