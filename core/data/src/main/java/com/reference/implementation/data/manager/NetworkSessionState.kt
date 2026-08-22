package com.reference.implementation.data.manager

import com.reference.implementation.data.dtos.RoleDto
import com.reference.implementation.data.dtos.UserDto

sealed class NetworkSessionState {
    object NoSession : NetworkSessionState()
    data class ActiveSession(
        val user: UserDto,
        val roles: List<RoleDto>,
    ) : NetworkSessionState()
}