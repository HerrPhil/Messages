package com.reference.implementation.messages.presentation.screens.bulletin

import com.reference.implementation.messages.domain.model.MessageDomainModel

interface BulletinUiEvent {
    data class showToast(val message: String) : BulletinUiEvent
}