package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.remote.UserDto

sealed class NetworkSessionState {
    object NoSession : NetworkSessionState()
    data class ActiveSession(val data: UserDto) : NetworkSessionState()
}
