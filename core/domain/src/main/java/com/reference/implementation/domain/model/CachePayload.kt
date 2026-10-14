package com.reference.implementation.domain.model

sealed interface CachePayload<out T> {
    data class ActiveStream<T>(
        val data: T,
        val isSyncing: Boolean,
        val syncError: Throwable? = null // Pass the network 5xx/4xx error code here safely!
    ) : CachePayload<T>
}