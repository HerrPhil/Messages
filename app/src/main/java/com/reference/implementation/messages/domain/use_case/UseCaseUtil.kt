package com.reference.implementation.messages.domain.use_case

fun getResourceErrorByCode(screen: String, code: Int): Resource<Nothing> {
    return when (code) {
        400 -> Resource.Error("Something went wrong") // BAD REQUEST
        401 -> Resource.Error("$screen not authorized")
        403 -> Resource.Error("$screen forbidden")
        404 -> Resource.Error("$screen not found")
        405 -> Resource.Error("$screen method not allowed")
        408 -> Resource.Error("$screen request timeout")
        429 -> Resource.Error("Too many $screen requests")
        500 -> Resource.Error("Server is having a bad day") // INTERNAL SERVER ERROR
        501 -> Resource.Error("$screen not implemented")
        502 -> Resource.Error("Something went wrong") // BAD GATEWAY
        503 -> Resource.Error("$screen is unavailable")
        504 -> Resource.Error("Something went wrong") // GATEWAY TIMEOUT
        else -> Resource.Error("Something went wrong")
    }
}
