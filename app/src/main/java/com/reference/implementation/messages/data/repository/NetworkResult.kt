package com.reference.implementation.messages.data.repository

sealed interface NetworkResult<out T : Any> {
    data object Loading: NetworkResult<Nothing>
    data class Success<out T : Any>(val data: T) : NetworkResult<T>
    data class Error(val code: Int, val message: String?) : NetworkResult<Nothing>
    data class Exception(val e: Throwable) : NetworkResult<Nothing>
}
