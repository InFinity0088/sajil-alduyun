package com.sajilalduyun.app.security

import kotlin.random.Random

object PasswordResetManager {

    fun generateResetCode(): String {
        return Random.nextInt(1000, 9999).toString()
    }

    fun isCodeExpired(expiresAt: Long): Boolean {
        return System.currentTimeMillis() > expiresAt
    }

    fun isCodeValid(code: String): Boolean {
        return code.length == 4 && code.all { it.isDigit() }
    }
}
