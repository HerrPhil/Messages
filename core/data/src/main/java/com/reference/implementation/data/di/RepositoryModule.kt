package com.reference.implementation.data.di

import com.reference.implementation.data.repositoryimpl.BulletinCacheRepositoryImpl
import com.reference.implementation.data.repositoryimpl.BulletinRepositoryImpl
import com.reference.implementation.data.repositoryimpl.LoginRepositoryImpl
import com.reference.implementation.data.repositoryimpl.LogoutRepositoryImpl
import com.reference.implementation.data.repositoryimpl.MessageCacheRepositoryImpl
import com.reference.implementation.data.repositoryimpl.MessageRepositoryImpl
import com.reference.implementation.data.repositoryimpl.PermissionRepositoryImpl
import com.reference.implementation.data.repositoryimpl.RefreshTokenRepositoryImpl
import com.reference.implementation.data.repositoryimpl.RoleRepositoryImpl
import com.reference.implementation.data.repositoryimpl.UserPreferencesRepositoryImpl
import com.reference.implementation.data.repositoryimpl.UserRepositoryImpl
import com.reference.implementation.domain.repository.BulletinCacheRepository
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.LoginRepository
import com.reference.implementation.domain.repository.LogoutRepository
import com.reference.implementation.domain.repository.MessageCacheRepository
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.PermissionRepository
import com.reference.implementation.domain.repository.RefreshTokenRepository
import com.reference.implementation.domain.repository.RoleRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import com.reference.implementation.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")  // This tells Lint: "I know this looks unused, do not warn me."
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRefreshTokenRepository(
        impl: RefreshTokenRepositoryImpl
    ): RefreshTokenRepository

    @Binds
    @Singleton
    abstract fun bindLoginRepository(
        impl: LoginRepositoryImpl
    ): LoginRepository

    @Binds
    @Singleton
    abstract fun bindLogoutRepository(
        impl: LogoutRepositoryImpl
    ): LogoutRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindRoleRepository(
        impl: RoleRepositoryImpl
    ): RoleRepository

    @Binds
    @Singleton
    abstract fun bindPermissionRepository(
        impl: PermissionRepositoryImpl
    ): PermissionRepository

    @Binds
    @Singleton
    abstract fun bindMessageCacheRepository(
        impl: MessageCacheRepositoryImpl
    ): MessageCacheRepository

    @Binds
    @Singleton
    abstract fun bindBulletinCacheRepository(
        impl: BulletinCacheRepositoryImpl
    ): BulletinCacheRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBulletinRepository(
        impl: BulletinRepositoryImpl
    ): BulletinRepository

}