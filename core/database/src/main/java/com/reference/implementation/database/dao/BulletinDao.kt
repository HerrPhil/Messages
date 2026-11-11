package com.reference.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reference.implementation.database.model.BulletinEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("unused")
interface BulletinDao {

    @Query("SELECT * FROM bulletins")
    fun getBulletinsStream(): Flow<List<BulletinEntity>>

    // THE SINGLE-ROW STREAM: Listens infinitely to exactly one message record!
    @Query("SELECT * FROM bulletins WHERE id = :bulletinId LIMIT 1")
    fun getBulletinByIdStream(bulletinId: Int): Flow<BulletinEntity?>

    // THE CONTRACT: Accepts a list of entities and executes an atomic SQLite transaction loop.
    // Called from Repository Implementation - not mapped to Repository interface.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBulletins(bulletins: List<BulletinEntity>)
}

