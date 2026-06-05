package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.manager.AuthSessionManager
import com.reference.implementation.messages.data.manager.RoleManager
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.domain.repository.LogoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogoutRepositoryImpl(
    private val sessionManager: SessionManager,
    private val authSessionManager: AuthSessionManager, // Global state source (Application Layer)
    private val roleManager: RoleManager, // Global state source (Application Layer)
    private val externalScope: CoroutineScope // an application scope
) : LogoutRepository {

    override suspend fun logout() {
        externalScope.launch(Dispatchers.IO) {
            sessionManager.logout()
            authSessionManager.stopSession()
            roleManager.clear()
        }
    }

}
