package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.manager.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

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
        val token = runBlocking {
            tokenManager.getToken()
        }

        val requestBuilder = originalRequest.newBuilder()
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
