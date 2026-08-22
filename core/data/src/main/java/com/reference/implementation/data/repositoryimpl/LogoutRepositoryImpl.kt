package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.manager.AuthSessionManager
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.domain.repository.LogoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The benefit of passing the Dispatchers.IO as a class parameter is that
 * this allows you to easily pass
 * StandardTestDispatcher or
 * UnconfinedTestDispatcher
 * during unit testing
 */
class LogoutRepositoryImpl(
    private val externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO, // Global state source (Application Layer)
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