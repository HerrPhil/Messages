package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.repository.RoleRepository

class FetchNewUserProfileUseCase(
    private val roleRepo: RoleRepository
) {
    suspend operator fun invoke(
        loginUser: LoginUserDomainModel,
        onRetry: suspend (Int) -> Unit
    ): Resource<Unit> = roleRepo.configureUserProfile(loginUser, onRetry)
        .toResource(
            "user profile"
        ) { /* no data to transform - all stored in role manager or session state */ }

}