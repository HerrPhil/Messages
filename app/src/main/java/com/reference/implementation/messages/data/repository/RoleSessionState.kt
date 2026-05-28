package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.remote.RoleDto

sealed class RoleSessionState {
    object NoRole: RoleSessionState()
    data class UserRole(val data: RoleDto): RoleSessionState()
}