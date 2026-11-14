package com.reference.implementation.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permissions")
data class PermissionEntity(
    @PrimaryKey val id: Int,
    val task: String,
    val userId: Int // data owner = admin user
)