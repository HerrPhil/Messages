package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.LoginRequestDto
import com.reference.implementation.messages.data.remote.toDomainModel
import com.reference.implementation.messages.domain.model.LoginDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class LoginRepositoryImpl(private val apiService: ApiService) : LoginRepository {

    override suspend fun login(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): NetworkResult<LoginDomainModel> {
        return withContext(Dispatchers.IO) {
            try {
                val loginRequestDto = LoginRequestDto(email, password)
                val response = retryIO(times = 3, onRetry = onRetry) {
                    apiService.login(loginRequestDto)
                }
                if (response.isSuccessful && response.body() != null) {
                    // DTO never leaves this layer!
                    NetworkResult.Success(response.body()!!.toDomainModel())
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
