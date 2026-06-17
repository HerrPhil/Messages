package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.domain.use_case.ForceLogoutUseCase
import com.reference.implementation.messages.domain.use_case.RefreshTokenUseCase
import com.reference.implementation.messages.domain.use_case.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val refreshTokenUseCaseProvider: () -> RefreshTokenUseCase,
    private val forceLogoutUseCaseProvider: () -> ForceLogoutUseCase
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
                val refreshTokenUseCase = refreshTokenUseCaseProvider()

                // execute the refresh call ON DEMAND
                refreshTokenUseCase(tokenUsedByThisRequest)
            } // Resource<RefreshTokenDomainModel>

            val authenticatedRequest =
                when (resourceToken) {
                    is Resource.Success -> {
                        val newAccessToken = resourceToken.data.newAccessToken

                        response.request.newBuilder()
                            .addHeader("Authorization", "Bearer $newAccessToken")
                            // Stamp it as a retry so AuthInterceptor leaves it alone
                            .tag(RetryTag::class.java, RetryTag())
                            .build()
                    }

                    is Resource.Error -> {
                        // Force logout - we are here most likely due to a refresh 403
                        val logoutScope = CoroutineScope(Dispatchers.Default)
                        logoutScope.launch {

                            // Resolve the UseCase ON DEMAND only when a 403 error hits
                            val forceLogoutUseCase = forceLogoutUseCaseProvider()

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
