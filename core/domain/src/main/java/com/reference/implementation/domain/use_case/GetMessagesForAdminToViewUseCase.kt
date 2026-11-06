package com.reference.implementation.domain.use_case

import com.reference.implementation.domain.model.MessageDomainModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

open class GetMessagesForAdminToViewUseCase @Inject constructor(
    private val getMessagesForActiveUserUseCase: GetMessagesForActiveUserUseCase,
    private val getMessagesForSelectedUserUseCase: GetMessagesForSelectedUserUseCase,
) {
    open operator fun invoke(selectedUserId: Int?): Flow<Resource<List<MessageDomainModel>>> {
        return if (selectedUserId == null) {
            getMessagesForActiveUserUseCase()
        } else {
            getMessagesForSelectedUserUseCase(selectedUserId)
        }
    }
}