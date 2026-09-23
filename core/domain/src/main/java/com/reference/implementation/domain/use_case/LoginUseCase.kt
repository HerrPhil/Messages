package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.repository.LoginRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginUseCase @Inject constructor(
    private val repo: LoginRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        onRetry: suspend (Int) -> Unit
    ): Resource<LoginUserDomainModel> {
        return repo.login(email, password, onRetry)
            .toResource("Login") { loginUser ->
                loginUser
            }
    }
}