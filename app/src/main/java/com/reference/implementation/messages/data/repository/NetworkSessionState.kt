package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.remote.UserDto
import com.reference.implementation.messages.domain.model.UserDomainModel

data class NetworkSessionState(
    val userDto: UserDto
)
