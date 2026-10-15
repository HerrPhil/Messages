package com.reference.implementation.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ConcurrencyModule {

    // SCOPE 1: For CPU-bound background processing (JSON, Math, Filtering)
    @Provides
    @Singleton
    @DefaultScope
    fun provideDefaultScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // SCOPE 2: For I/O-bound background processing (Network Syncs, Room Cache Writes)
    @Provides
    @Singleton
    @ApplicationScope // Or @InputOutputScope based on your team's nomenclature tags
    fun provideApplicationIoScope(): CoroutineScope {
        // Pairing a SupervisorJob with Dispatchers.IO guarantees that background
        // network latency blocks can scale up to 64 elastic threads cleanly,
        // leaving your core CPU Default threads completely empty and ready for UI rendering!
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    // New providers for dispatchers

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

}