package com.reference.implementation.domain.repository

interface LogoutRepository {
    suspend fun logout()
    suspend fun forceLogout()
}
