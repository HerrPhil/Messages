package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.LoginRequestDto
import com.reference.implementation.messages.data.remote.toDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class LoginRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val sessionRepository: SessionRepository
) : LoginRepository {

    override suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<UserDomainModel> {
        return withContext(Dispatchers.IO) {
            try {
                val loginRequestDto = LoginRequestDto(email, password)
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.login(loginRequestDto)
                }
                if (response.isSuccessful && response.body() != null) {

                    // The token is a technical detail of the data layer.
                    // It never leaves this layer!
                    // Function is saveToken is a suspend function; inside withContext coroutine scope - OK
                    tokenManager.saveToken(response.body()!!.accessToken)

                    val userDto = response.body()!!.userDto

                    // The session user DTO gets stashed for other screens that want to look up
                    // data based on the session user ID.
                    sessionRepository.updateSessionUser(userDto)

                    // DTO never leaves this layer!
                    NetworkResult.Success(userDto.toDomainModel())
                } else {
                    // Transform unsuccessful Retrofit calls.
                    NetworkResult.Error(response.code(), response.message())
                }
            } catch (e: Throwable) {
                Audit.createInstance().writeLog(e.message ?: "no message")
                NetworkResult.Exception(e)
            } finally {
                withContext(NonCancellable) {
                    Audit.createInstance().writeLog("${auditLogTimestamp()} login call ended")
                }
            }
        }
    }

}
