package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.LogoutRepository

class LogoutUseCase(private val repo: LogoutRepository) {
    // No parameters to log out - just do it!
    // I think logout will re-iterate what user logged out.
    // That is, return user domain model of user in session.
    operator fun invoke() {
        repo.logout()
    }
}