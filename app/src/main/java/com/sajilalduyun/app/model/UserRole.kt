package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole {
    OWNER,
    WORKER
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,
    val name: String,
    val role: UserRole,
    val pin: String,
    val phoneNumber: String = "",
    val isActive: Boolean
)