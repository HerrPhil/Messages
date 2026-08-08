package com.reference.implementation.messages.presentation.screens.adminhome

interface AdminHomeUiEvent {
    object NavigateToAdminMessages : AdminHomeUiEvent
    object NavigateToAdminBulletins : AdminHomeUiEvent
    object RefreshDashboard : AdminHomeUiEvent
}