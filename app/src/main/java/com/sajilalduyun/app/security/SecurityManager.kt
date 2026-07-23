package com.sajilalduyun.app.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object SecurityManager {

    // How many wrong attempts before lockout
    private const val MAX_ATTEMPTS = 5

    // Lockout duration in milliseconds (15 minutes)
    private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L

    // Session timeout in milliseconds (10 minutes)
    private const val SESSION_TIMEOUT_MS = 10 * 60 * 1000L

    private const val PREFS_NAME = "security_prefs"
    private const val KEY_LAST_ACTIVITY = "last_activity_time"

    private var prefs: SharedPreferences? = null

    /**
     * Must be called once early in the app lifecycle (e.g. from BaseActivity.onCreate)
     * before any other SecurityManager methods.
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(): SharedPreferences =
        prefs ?: throw IllegalStateException("SecurityManager not initialized — call init(context)")

    private fun failedAttemptsKey(userId: String) = "failed_attempts_$userId"
    private fun lockoutStartKey(userId: String) = "lockout_start_$userId"

    // --- PIN HASHING ---
    // Scrambles the PIN using SHA-256. One-way — cannot be reversed.
    // Example: "1234" becomes "03ac674..." forever
    fun hashPin(pin: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Checks if the entered PIN matches the stored hashed PIN
    fun verifyPin(enteredPin: String, storedHashedPin: String): Boolean {
        return hashPin(enteredPin) == storedHashedPin
    }

    // --- RATE LIMITING ---
    // Call this when a login attempt fails
    fun recordFailedAttempt(userId: String) {
        val p = getPrefs()
        val attempts = p.getInt(failedAttemptsKey(userId), 0) + 1
        p.edit().putInt(failedAttemptsKey(userId), attempts).apply()

        if (attempts >= MAX_ATTEMPTS) {
            p.edit().putLong(lockoutStartKey(userId), System.currentTimeMillis()).apply()
        }
    }

    // Returns true if the user is currently locked out
    fun isLockedOut(userId: String): Boolean {
        val p = getPrefs()
        val lockStart = p.getLong(lockoutStartKey(userId), 0L)
        if (lockStart == 0L) return false

        val elapsed = System.currentTimeMillis() - lockStart
        return if (elapsed >= LOCKOUT_DURATION_MS) {
            // Lockout expired, reset everything
            p.edit()
                .remove(failedAttemptsKey(userId))
                .remove(lockoutStartKey(userId))
                .apply()
            false
        } else {
            true
        }
    }

    // How many minutes remaining in lockout
    fun lockoutMinutesRemaining(userId: String): Long {
        val p = getPrefs()
        val lockStart = p.getLong(lockoutStartKey(userId), 0L)
        if (lockStart == 0L) return 0
        val elapsed = System.currentTimeMillis() - lockStart
        val remaining = LOCKOUT_DURATION_MS - elapsed
        return (remaining / 60000).coerceAtLeast(0)
    }

    // Reset failed attempts after successful login
    fun resetAttempts(userId: String) {
        val p = getPrefs()
        p.edit()
            .remove(failedAttemptsKey(userId))
            .remove(lockoutStartKey(userId))
            .apply()
    }

    // --- SESSION TIMEOUT ---
    // Call this every time the user does anything in the app
    fun recordActivity() {
        getPrefs().edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
    }

    // Returns true if session has expired (10 min of no activity)
    fun isSessionExpired(): Boolean {
        val p = getPrefs()
        val lastActivity = p.getLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
        val elapsed = System.currentTimeMillis() - lastActivity
        return elapsed >= SESSION_TIMEOUT_MS
    }
}
