package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadAllUsersUseCase @Inject constructor(
    private val repo: UserRepository
) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.loadAllUsers(onRetry)
    }
}