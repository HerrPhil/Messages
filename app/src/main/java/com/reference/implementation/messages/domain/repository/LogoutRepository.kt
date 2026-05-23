package com.reference.implementation.messages.domain.repository

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginDomainModel
import com.reference.implementation.messages.domain.model.UserDomainModel

interface LogoutRepository {
    suspend fun logout(): NetworkResult<UserDomainModel>
}
