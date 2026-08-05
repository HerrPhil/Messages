package com.reference.implementation.messages.domain.use_case

import com.reference.implementation.messages.data.repository.NetworkResult
import com.reference.implementation.messages.domain.model.LoginUserDomainModel
import com.reference.implementation.messages.domain.repository.LoginRepository
import okio.IOException
import retrofit2.HttpException

class LoginUseCase(private val repo: LoginRepository) {
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