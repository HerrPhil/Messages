package com.reference.implementation.messages.data.audit

import okhttp3.Interceptor
import okhttp3.Response

class SecurityAuditInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestStart = System.currentTimeMillis()

        // Proceed with the network call
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            // LOGGING FAILED ATTEMPT: Network level (Timeout, No Route, etc.)
            auditLog(
                "CRITICAL",
                request.url.toString(),
                "Network failure: ${e.message}",
                requestStart
            )
            throw e
        }

        // LOGGING FAILED ATTEMPT: Server level (5xx errors)
        if (!response.isSuccessful && response.code >= 500) {
            auditLog(
                "WARN",
                request.url.toString(),
                "Server Error Code: ${response.code}",
                requestStart
            )
        }

        // Otherwise, return the response back into the chain
        return response
    }

    private fun auditLog(level: String, url: String, message: String, requestStart: Long) {
        // In a real (production) app,
        // this might send data to a secure remote logger like Timber or Firebase.
        // TODO store audit logs for techs in a remote logger (free)
        println("[$level] [AUDIT] | URL: $url | MESSAGE: $message| START TIMESTAMP: $requestStart | END TIMESTAMP: ${System.currentTimeMillis()}")
    }
}

