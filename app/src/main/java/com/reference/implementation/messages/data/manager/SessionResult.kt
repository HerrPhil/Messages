package com.reference.implementation.messages.data.manager

import com.reference.implementation.messages.data.repository.NetworkResult

sealed interface SessionResult<out T : Any> {
    data class Authenticated<out T : Any>(val data: T) : SessionResult<T>
    object NoValue : SessionResult<Nothing>
}