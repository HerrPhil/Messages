package com.reference.implementation.data.manager

sealed interface SessionResult<out T : Any> {
    data class Authenticated<out T : Any>(val data: T) : SessionResult<T>
    object NoValue : SessionResult<Nothing>
}