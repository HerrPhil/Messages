package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.remote.RoleDto
import com.reference.implementation.messages.data.remote.UserDto

sealed class NetworkSessionState {
    object NoSession : NetworkSessionState()
    data class ActiveSession(
        val user: UserDto,
        val role: RoleDto,
    ) : NetworkSessionState()
}
