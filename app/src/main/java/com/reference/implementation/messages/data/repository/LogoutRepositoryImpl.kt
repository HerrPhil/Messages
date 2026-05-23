package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.data.remote.LoginRequestDto
import com.reference.implementation.messages.data.remote.toDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
import com.reference.implementation.messages.domain.repository.LogoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class LogoutRepositoryImpl(
    private val sessionRepository: SessionRepository
) : LogoutRepository {

    override suspend fun logout(): NetworkResult<UserDomainModel> {
        return withContext(Dispatchers.IO) {
            sessionRepository.logout()
        }
    }

}
