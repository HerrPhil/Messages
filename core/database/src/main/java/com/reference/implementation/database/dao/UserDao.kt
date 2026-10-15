package com.reference.implementation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reference.implementation.database.model.BulletinEntity
import com.reference.implementation.database.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("unused")
interface UserDao {

    @Query("SELECT * FROM users")
    fun getUsersStream(): Flow<List<UserEntity>>

    // THE CONTRACT: Accepts a list of entities and executes an atomic SQLite transaction loop
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

}

