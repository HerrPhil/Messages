package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.LogoutRepository

class ForceLogoutUseCase(private val repo: LogoutRepository) {
    // No parameters to log out - just do it!
    // I think logout will re-iterate what user logged out.
    // That is, return user domain model of user in session.
    suspend operator fun invoke() {
        repo.forceLogout()
    }
}