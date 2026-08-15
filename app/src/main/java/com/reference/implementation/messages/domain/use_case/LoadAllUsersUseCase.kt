package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.domain.repository.UserRepository

class LoadAllUsersUseCase(private val repo: UserRepository) {
    suspend operator fun invoke(
        onRetry: suspend (Int) -> Unit
    ) {
        repo.loadAllUsers(onRetry)
    }
}