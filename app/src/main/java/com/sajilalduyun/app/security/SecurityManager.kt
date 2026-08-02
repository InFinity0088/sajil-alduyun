package com.sajilalduyun.app.security

import android.content.Context
import android.content.SharedPreferences
import org.mindrot.jbcrypt.BCrypt
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

    // bcrypt work factor — higher = slower (10 is the default, 12 is more secure)
    private const val BCRYPT_ROUNDS = 12

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
    // Uses bcrypt with work factor 12 (~250ms per hash).
    // A 4-digit PIN (10,000 combos) would take ~40 minutes to brute-force
    // instead of microseconds with SHA-256.
    fun hashPin(pin: String): String {
        return BCrypt.hashpw(pin, BCrypt.gensalt(BCRYPT_ROUNDS))
    }

    /**
     * Checks if the entered PIN matches the stored hashed PIN.
     *
     * Supports two hash formats for backward compatibility:
     * 1. bcrypt (preferred) — detected by "$2a$" or "$2b$" prefix
     * 2. SHA-256 (legacy) — auto-detected, re-hashes to bcrypt on success
     *
     * @return true if PIN matches, false otherwise
     */
    fun verifyPin(enteredPin: String, storedHashedPin: String): Boolean {
        // Detect bcrypt format
        if (storedHashedPin.startsWith("\$2a\$") || storedHashedPin.startsWith("\$2b\$")) {
            return BCrypt.checkpw(enteredPin, storedHashedPin)
        }

        // Legacy SHA-256 fallback — migrate to bcrypt on successful login
        val legacyHash = sha256Hex(enteredPin)
        if (legacyHash == storedHashedPin) {
            // PIN matches with SHA-256 — upgrade to bcrypt
            return true // caller should call rehashPin() to upgrade
        }

        return false
    }

    /**
     * Re-hashes a PIN to bcrypt (for migrating legacy SHA-256 hashes).
     * Call this after a successful SHA-256 verification to upgrade the stored hash.
     */
    fun rehashPin(enteredPin: String, currentHash: String): String {
        // Only re-hash if it's still SHA-256 format
        if (!currentHash.startsWith("\$2a\$") && !currentHash.startsWith("\$2b\$")) {
            return hashPin(enteredPin)
        }
        return currentHash // already bcrypt, no change needed
    }

    /** SHA-256 hex digest (legacy) — kept for migration. */
    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
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
