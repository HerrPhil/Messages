package com.reference.implementation.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reference.implementation.database.dao.BulletinDao
import com.reference.implementation.database.model.BulletinEntity

@Database(
    entities =
        [
            BulletinEntity::class
        ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bulletinDao(): BulletinDao
}