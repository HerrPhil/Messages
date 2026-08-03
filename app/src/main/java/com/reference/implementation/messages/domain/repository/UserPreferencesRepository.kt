package com.reference.implementation.messages.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    suspend fun markMessageAsImportant(messageId: Int)
    suspend fun markMessageAsNotImportant(messageId: Int)
    suspend fun markBulletinAsBookmark(bulletinId: Int)
    suspend fun markBulletinAsNotBookmark(bulletinId: Int)
    fun getImportantMessageIds(): Flow<Set<String>>
    fun getBookmarkBulletinIds(): Flow<Set<String>>
}