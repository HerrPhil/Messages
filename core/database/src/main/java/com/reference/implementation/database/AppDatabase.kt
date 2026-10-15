package com.reference.implementation.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reference.implementation.database.dao.BulletinDao
import com.reference.implementation.database.dao.UserDao
import com.reference.implementation.database.model.BulletinEntity
import com.reference.implementation.database.model.UserEntity

@Database(
    entities =
        [
            BulletinEntity::class,
            UserEntity::class
        ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bulletinDao(): BulletinDao
    abstract fun userDao(): UserDao
}