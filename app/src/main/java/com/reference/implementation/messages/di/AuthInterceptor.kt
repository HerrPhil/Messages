package com.reference.implementation.messages.di

import com.reference.implementation.data.manager.AccessTokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val accessTokenManager: AccessTokenManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // If it is the login or refresh endpoint, do not add the access token.
        if (
            originalRequest.url.encodedPath.contains("/auth/login") ||
            originalRequest.url.encodedPath.contains("/auth/refresh")
        ) {
            return chain.proceed(originalRequest)
        }

        // Retrieve the token dynamically at request time, every time
        // Expensive? No, runs in milliseconds!
        // The math done by the TEE (AES-GCM decryption) is incredibly fast — measured in milliseconds.
        val token = accessTokenManager.getToken()

        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)

    }
}