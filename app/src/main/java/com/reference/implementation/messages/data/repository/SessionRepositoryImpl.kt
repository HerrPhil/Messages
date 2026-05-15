package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.remote.UserDto
import com.reference.implementation.messages.data.remote.toDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class SessionRepositoryImpl(private val tokenManager: TokenManager) : SessionRepository {

    // 1. Define a private MutableStateFlow to hold the current state of "session user"
    //    returned by login.
    private val _sessionUserFlow = MutableStateFlow<NetworkSessionState?>(null)

    // 2. Expose the immutable Flow to consumer (eg. Home page ViewModel/UI)
    val sessionUserFlow: StateFlow<NetworkSessionState?> = _sessionUserFlow

    override suspend fun isLoggedIn(): Boolean {
        return !(tokenManager.getToken() == null)
    }

    override fun getSessionUser(): NetworkResult<UserDomainModel> =
        if (_sessionUserFlow.value == null) {
            NetworkResult.Error(0, "no session user")
        } else {
            // DTO never leaves this layer!
            NetworkResult.Success(_sessionUserFlow.value?.userDto!!.toDomainModel())
        }

    override fun updateSessionUser(newSessionUserDto: UserDto) {
        _sessionUserFlow.update { currentState ->
            currentState?.copy(
                userDto = newSessionUserDto
            )
        }
    }
}