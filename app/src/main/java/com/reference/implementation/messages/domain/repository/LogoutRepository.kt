package com.reference.implementation.messages.domain.repository

interface LogoutRepository {
    suspend fun logout()
    suspend fun forceLogout()
}
