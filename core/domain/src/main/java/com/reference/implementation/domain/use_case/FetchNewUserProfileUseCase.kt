package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.repository.RoleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetchNewUserProfileUseCase @Inject constructor(
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