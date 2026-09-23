package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.di.ApplicationScope
import com.reference.implementation.data.di.IoDispatcher
import com.reference.implementation.data.manager.AuthSessionManager
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.domain.repository.LogoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The benefit of passing the Dispatchers.IO as a class parameter is that
 * this allows you to easily pass
 * StandardTestDispatcher or
 * UnconfinedTestDispatcher
 * during unit testing
 */
@Singleton
class LogoutRepositoryImpl @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher, // Hilt provides it
    private val sessionManager: SessionManager, // Global state source (Application Layer)
    private val authSessionManager: AuthSessionManager,
    private val roleManager: RoleManager
) : LogoutRepository {

    override fun logout() {
        externalScope.launch(ioDispatcher) {
            sessionManager.logout()
            authSessionManager.stopSession()
            roleManager.clear()
        }
    }

    override fun forceLogout() {
        externalScope.launch(ioDispatcher) {
            sessionManager.logout()
            authSessionManager.forceStopSession()
            roleManager.clear()
        }
    }

}