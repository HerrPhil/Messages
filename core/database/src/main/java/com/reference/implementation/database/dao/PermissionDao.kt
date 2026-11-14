package com.reference.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reference.implementation.database.model.PermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("unused")
interface PermissionDao {

    @Query("SELECT * FROM permissions")
    fun getPermissionsStream(): Flow<List<PermissionEntity>>

    // THE CONTRACT: Accepts a list of entities and executes an atomic SQLite transaction loop.
    // Called from Repository Implementation - not mapped to Repository interface.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(messages: List<PermissionEntity>)
}