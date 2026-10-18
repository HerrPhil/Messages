package com.reference.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reference.implementation.database.model.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("unused")
interface MessageDao {

    @Query("SELECT * FROM messages")
    fun getMessagesStream(): Flow<List<MessageEntity>>

    // THE CONTRACT: Accepts a list of entities and executes an atomic SQLite transaction loop
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(users: List<MessageEntity>)

}