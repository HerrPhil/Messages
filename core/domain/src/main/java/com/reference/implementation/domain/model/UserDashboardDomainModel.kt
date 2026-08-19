package com.reference.implementation.domain.model

data class UserDashboardDomainModel(
    val userName: String?,
    val userEmail: String?,
    val unreadMessages: Int?,
    val readMessages: Int?,
    val roles: List<String>?,
    val permissions: List<String>?
)