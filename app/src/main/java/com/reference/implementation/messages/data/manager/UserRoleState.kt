package com.reference.implementation.messages.data.manager

sealed interface UserRoleState {
    object Loading : UserRoleState
    data class RegularUser(val name: String) : UserRoleState
    object Administrator : UserRoleState
    object Unknown : UserRoleState
}