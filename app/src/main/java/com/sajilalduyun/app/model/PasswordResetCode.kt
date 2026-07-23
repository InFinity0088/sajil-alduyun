package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "password_reset_codes")
data class PasswordResetCode(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val phoneNumber: String,
    val code: String,
    val createdAt: Date = Date(),
    val expiresAt: Date = Date(System.currentTimeMillis() + 15 * 60 * 1000), // 15 minutes
    val isUsed: Boolean = false
)
