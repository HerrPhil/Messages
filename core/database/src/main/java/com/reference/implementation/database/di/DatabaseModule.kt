package com.reference.implementation.database.di

import android.content.Context
import androidx.room.Room
import com.reference.implementation.database.AppDatabase
import com.reference.implementation.database.dao.BulletinDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// object - provide
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "messages_cache.db"

    // PROVIDER 1: Instructs Hilt on how to construct your abstract Room database factory.
    // Marked as @Singleton to guarantee that exactly one instance across the app,
    // protecting your SQLite database file from dangerous multi-thread file-access crashes.
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            // Establishes a safe automated fallback strategy.
            // If you increment your database version number inside AppDatabase without providing
            // an explicit migration file path routine, Room will safely wipe the local cache tables
            // cleanly on startup rather than crashing.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    // PROVIDER 2: Extracting individual DAO interfaces.
    // By providing BulletinDao directly, your repository constructors can demand it by name!
    // Hilt automatically fetches your Singleton AppDatabase provider from above to satisfy
    // the argument.
    @Provides
    fun provideBulletinDao(database: AppDatabase): BulletinDao = database.bulletinDao()


    // Future DAO provider hooks will simply be appended right here step-by-step:
    // @Provides
    // fun provideMessageDao(database: AppDatabase): MessageDao = database.MessageDao()
}