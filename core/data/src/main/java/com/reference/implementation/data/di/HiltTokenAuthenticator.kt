package com.reference.implementation.data.di

import com.reference.implementation.domain.use_case.ForceLogoutUseCase
import com.reference.implementation.domain.use_case.RefreshTokenUseCase
import com.reference.implementation.domain.use_case.Resource
import dagger.Lazy // CRITICAL: Explicitly import Dagger's Lazy wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiltTokenAuthenticator @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    // Dagger's Lazy acts exactly like the old custom lambdas
    private val refreshTokenUseCaseProvider: Lazy<RefreshTokenUseCase>,
    private val forceLogoutUseCaseProvider: Lazy<ForceLogoutUseCase>
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {

        // 1. Prevent infinite loops: If the refresh attempt itself failed, stop!
        if (response.priorResponse != null) {
            return null
        }

        synchronized(this) {

            // 1. Grab the token that this specific request used when it failed
            val tokenUsedByThisRequest = response.request.header("Authorization")
                ?.replace("Bearer ", "") ?: ""

            val resourceToken = runBlocking(Dispatchers.Default) {

                // Resolve the UseCase ON DEMAND only when a 401 error hits
                val refreshTokenUseCase = refreshTokenUseCaseProvider.get()

                // execute the refresh call ON DEMAND
                refreshTokenUseCase(tokenUsedByThisRequest)
            } // Resource<RefreshTokenDomainModel>

            val authenticatedRequest =
                when (resourceToken) {
                    is Resource.Success -> {
                        val newAccessToken = resourceToken.data.newAccessToken

                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()
                    }

                    is Resource.Error -> {
                        // Force logout - we are here most likely due to a refresh 403
                        externalScope.launch {

                            // Resolve the UseCase ON DEMAND only when a 403 error hits
                            val forceLogoutUseCase = forceLogoutUseCaseProvider.get()

                            // execute the logout call
                            forceLogoutUseCase()
                        }

                        null
                    }

                    else -> null
                }

            return authenticatedRequest
        }
    }
}