package com.reference.implementation.messages.data.repository

import com.reference.implementation.messages.data.remote.UserDto
import com.reference.implementation.messages.domain.model.UserDomainModel

/**
 * This repository interface is a special case.
 * Note it is not in the Domain layer.
 * Session-based information is a technical detail for the data layer.
 * At most, another repository might take the session repository as an input parameter.
 * It might then reveal the username or email in its results.
 * The session repository has no reason to appear in the domain layer.
 */
interface SessionRepository {
    suspend fun isLoggedIn(): Boolean
    fun getSessionUser(): NetworkResult<UserDomainModel>
    fun updateSessionUser(newSessionUserDto: UserDto): Unit
}