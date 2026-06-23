package com.reference.implementation.messages.data.manager

sealed interface UserRoleState {
    object Idle: UserRoleState
    object Loading : UserRoleState
    object RegularUser : UserRoleState
    object Administrator : UserRoleState
    object Unknown : UserRoleState
}