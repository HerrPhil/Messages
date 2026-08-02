package com.reference.implementation.messages.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    suspend fun markMessageAsImportant(messageId: Int)
    suspend fun markMessageAsNotImportant(messageId: Int)
    fun getImportantMessageIds(): Flow<Set<String>>
}