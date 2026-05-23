package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.UserDomainModel
import com.reference.implementation.messages.domain.repository.LogoutRepository
import okio.IOException
import retrofit2.HttpException

class LogoutUseCase(private val repo: LogoutRepository) {
    // No parameters to log out - just do it!
    // I think logout will re-iterate what user logged out.
    // That is, return user domain model of user in session.
    suspend operator fun invoke(): Resource<UserDomainModel> {
        return when (val logoutNetworkResult = repo.logout()) {
            is NetworkResult.Success -> {
                Resource.Success(logoutNetworkResult.data)
            }

            is NetworkResult.Error -> {
                getResourceErrorByCode(logoutNetworkResult.code)
            }

            is NetworkResult.Exception -> {
                when (logoutNetworkResult.e) {
                    is IOException -> Resource.Error("No internet connection")
                    is HttpException -> {
                        getResourceErrorByCode(logoutNetworkResult.e.code())
                    }

                    else -> Resource.Error("Unknown error occurred")
                }
            }
        }
    }

    private fun getResourceErrorByCode(code: Int): Resource<Nothing> {
        return when (code) {
            400 -> Resource.Error("Something went wrong") // BAD REQUEST
            401 -> Resource.Error("Logout not authorized")
            403 -> Resource.Error("Logout forbidden")
            404 -> Resource.Error("Logout not found")
            405 -> Resource.Error("Logout method not allowed")
            408 -> Resource.Error("Logout request timeout")
            429 -> Resource.Error("Too many logout requests")
            500 -> Resource.Error("Server is having a bad day") // INTERNAL SERVER ERROR
            501 -> Resource.Error("Logout not implemented")
            502 -> Resource.Error("Something went wrong") // BAD GATEWAY
            503 -> Resource.Error("Logout is unavailable")
            504 -> Resource.Error("Something went wrong") // GATEWAY TIMEOUT
            else -> Resource.Error("Something went wrong")
        }
    }

}