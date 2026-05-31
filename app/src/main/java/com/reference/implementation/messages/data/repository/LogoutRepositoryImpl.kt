package com.reference.implementation.messages.data.repository

import android.util.Log
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.domain.model.UserDomainModel
import com.reference.implementation.messages.domain.repository.LogoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class LogoutRepositoryImpl(
    private val sessionRepository: SessionRepository,
    private val externalScope: CoroutineScope // an application scope
) : LogoutRepository {

    override suspend fun logout(): NetworkResult<UserDomainModel> {

        // 1. Create the job on the application scope
        val deferredResult = externalScope.async(context = Dispatchers.IO) {
            Log.d("LogoutRepositoryImpl", "call sessionRepository.logout() as a Job.")
            sessionRepository.logout()
        }

        try {
            Log.d("LogoutRepositoryImpl", "return sessionRepository.logout() Job result.")
            return deferredResult.await()
        } catch (e: CancellationException) {
            // If the ViewModel dies, then this catch block triggers!
            // Essentially, the deferred result of the async job is not longer needed.
            // The await() failed, but the underlying job is still running out in the app scope.
            // We log it and let the app scope finish the job peacefully.
            withContext(NonCancellable) {
                Log.d("LogoutRepositoryImpl", "The authenticated shell ViewModel has died.")
                Log.d(
                    "LogoutRepositoryImpl",
                    "The authenticated shell ViewModel threw this CancellationException."
                )
                Log.d("LogoutRepositoryImpl", "We report and rethrow.")
                Audit.createInstance().writeLog(e.message ?: "no logout message")
            }
            throw e
        }
    }

}
