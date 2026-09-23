package com.reference.implementation.data.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoleManager @Inject constructor() {
    private val _roleState = MutableStateFlow<UserRoleState>(UserRoleState.Idle)
    val roleState: StateFlow<UserRoleState> = _roleState.asStateFlow()

    fun updateRole(newRole: UserRoleState) {
        _roleState.value = newRole
    }

    fun clear() {
        _roleState.value = UserRoleState.Unknown
    }
}