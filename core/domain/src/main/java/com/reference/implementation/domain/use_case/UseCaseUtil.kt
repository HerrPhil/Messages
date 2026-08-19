package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.util.NetworkResult
import okio.IOException
import retrofit2.HttpException

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

inline fun <T : Any, R> NetworkResult<T>.toResource(
    domainDetailsContext: String,
    transform: (T) -> R
): Resource<R> {
    return when(this) {
        is NetworkResult.Loading -> Resource.Loading
        is NetworkResult.Success -> Resource.Success(transform(data))
        is NetworkResult.Error -> getResourceErrorByCode(domainDetailsContext, code)
        is NetworkResult.Exception -> when(e) {
            is IOException -> Resource.Error("No internet connection")
            is HttpException -> getResourceErrorByCode(domainDetailsContext, e.code())
            else -> Resource.Error("Unknown error occurred")
        }
    }
}