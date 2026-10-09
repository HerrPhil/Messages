package com.reference.implementation.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.reference.implementation.database.model.BulletinEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("unused")
interface BulletinDao {
    @Query("SELECT * FROM bulletins")
    fun getBulletinsStream(): Flow<List<BulletinEntity>>
}