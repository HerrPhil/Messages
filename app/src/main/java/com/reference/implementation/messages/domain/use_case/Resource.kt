package com.reference.implementation.messages.domain.use_case

sealed interface Resource<out T> {
    data object Loading: Resource<Nothing>
    data class Success<out T>(val data: T) : Resource<T>
    data class Error(val message: String) : Resource<Nothing>
}