package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RefreshMessagesUseCase @Inject constructor(
    private val repo: MessageRepository
) {
    open suspend operator fun invoke() {
        repo.syncMessages()
    }
}