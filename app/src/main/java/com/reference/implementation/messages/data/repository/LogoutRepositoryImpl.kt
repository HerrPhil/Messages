package com.reference.implementation.messages.data.repository

import android.util.Log
import com.reference.implementation.messages.data.manager.AuthSessionManager
import com.reference.implementation.messages.data.manager.RoleManager
import com.reference.implementation.messages.domain.repository.LogoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogoutRepositoryImpl(
    private val sessionRepository: SessionRepository,
    private val externalScope: CoroutineScope, // an application scope
    private val authSessionManager: AuthSessionManager, // Global state source (Application Layer)
    private val roleManager: RoleManager // Global state source (Application Layer)
) : LogoutRepository {

    override suspend fun logout() {
        externalScope.launch(Dispatchers.IO) {
            Log.d("LogoutRepositoryImpl", "call sessionRepository.logout() as a Job.")
            sessionRepository.logout()
            authSessionManager.stopSession()
            roleManager.clear()
        }
    }

}
