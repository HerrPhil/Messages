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

    // THE CONTRACT: Accepts a list of entities and executes an atomic SQLite transaction loop
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(users: List<MessageEntity>)

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