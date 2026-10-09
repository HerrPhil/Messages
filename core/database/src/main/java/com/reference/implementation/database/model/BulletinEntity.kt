package com.reference.implementation.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bulletins")
data class BulletinEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val title: String,
    val post: String,
    val timestamp: String,
    val isBookmark: Boolean
)
