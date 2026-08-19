package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.UserRepository

class LoadAllUsersUseCase(private val repo: UserRepository) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.loadAllUsers(onRetry)
    }
}