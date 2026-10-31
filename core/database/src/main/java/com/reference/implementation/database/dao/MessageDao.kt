package com.reference.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reference.implementation.database.model.AdminMessageUnreadTotalQueryResult
import com.reference.implementation.database.model.MessageEntity
import com.reference.implementation.database.model.MessageReadUnreadQueryResult
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("unused")
interface MessageDao {

    @Query("SELECT * FROM messages")
    fun getMessagesStream(): Flow<List<MessageEntity>>

    // Called from Repository Implementation - mapped to Repository interface to access in use case.
    @Query("SELECT * FROM messages WHERE userId = :userId")
    fun getMessagesForUserStream(userId: Int): Flow<List<MessageEntity>>

    // THE SNAPSHOT BACKUP  HOOK: A fast, one-shot suspend lookup tool
    // that fetches the exact entity record before execution of CRUD!
    // Called from Repository Implementation - not mapped to Repository interface.
    @Query("SELECT * from messages where id = :messageId LIMIT 1")
    fun getMessageById(messageId: Int): MessageEntity?

    // THE DESTRUCTION COMMAND
    // Called from Repository Implementation - not mapped to Repository interface.
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Int)

    // THE CONTRACT: Accepts a list of entities and executes an atomic SQLite transaction loop
    // Called from Repository Implementation - not mapped to Repository interface.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(users: List<MessageEntity>)

    // THE TOGGLE READ STATUS CONTRACT
    // Support the markMessageAsRead/markMessageAsUnread functions in the MessageRepositoryImpl.
    @Query("UPDATE messages SET read = :isRead WHERE id = :messageId")
    suspend fun updateReadStatus(messageId: Int, isRead: Boolean)

    // THE UNREAD/TOTAL CONDITIONAL AGGREGATION ENGINE:
    // Computes both unread and total message counts for all users simultaneously,
    // down in the native C++ SQLite framework, returning a single, real-time snapshot row!
    @Query(value = """
        SELECT
            SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount,
            COUNT(*) as totalCount
        FROM messages
    """
    )
    fun getAdminMessageUnreadTotalStream(): Flow<AdminMessageUnreadTotalQueryResult>

    // THE READ/UNREAD CONDITIONAL AGGREGATION ENGINE:
    // Computes both read and unread message counts for a selected user ID simultaneously,
    // down in the native C++ SQLite framework, returning a single, real-time snapshot row!
    @Query(value = """
        SELECT
            SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount,
            SUM(CASE WHEN read = 1 THEN 1 ELSE 0 END) as readCount
        FROM messages
        WHERE userId = :userId 
    """
    )
    fun getUserMessageReadUnreadStream(userId: Int): Flow<MessageReadUnreadQueryResult>

}