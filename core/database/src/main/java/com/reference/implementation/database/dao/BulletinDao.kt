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

    // THE CONTRACT: Accepts a list of entities and executes an atomic SQLite transaction loop
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBulletins(bulletins: List<BulletinEntity>)

    @Query("SELECT id FROM bulletins WHERE isBookmark = 1")
    suspend fun getBookmarkedIds(): List<Int>
}

