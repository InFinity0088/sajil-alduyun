package com.sajilalduyun.app.service

import android.content.Context
import android.provider.Settings
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.ConsumedLicense
import com.sajilalduyun.app.model.RevokedLicense
import com.sajilalduyun.app.security.LicenseVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.TimeUnit

object LicenseManager {

    // SharedPreferences = simple key-value storage for small data
    private const val PREF_NAME = "license_prefs"
    private const val KEY_START_DATE = "license_start_date"
    private const val KEY_DURATION_MS = "license_duration_ms"
    private const val KEY_DEVICE_ID = "license_device_id"

    // ──────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────

    /** SHA-256 hex digest of a license code string. */
    private fun hashCode(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(code.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Persistent device identifier (survives app reinstalls). */
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
    }

    // ──────────────────────────────────────────────
    //  Code consumption tracking
    // ──────────────────────────────────────────────

    /** Returns true if this license code has already been used on any device. */
    private fun isCodeConsumed(context: Context, code: String): Boolean {
        val hash = hashCode(code)
        return runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.consumedLicenseDao().getByCodeHash(hash) != null
        }
    }

    /** Marks a license code as consumed so it cannot be reused. */
    private fun consumeCode(context: Context, code: String) {
        val hash = hashCode(code)
        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.consumedLicenseDao().insert(
                ConsumedLicense(
                    codeHash = hash,
                    deviceId = getDeviceId(context),
                    consumedAt = Date().time
                )
            )
        }
    }

    /** Records a device ID as revoked (license transferred away from it). */
    private fun revokeDevice(context: Context, oldDeviceId: String, transferredTo: String = "") {
        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.revokedLicenseDao().insert(
                RevokedLicense(
                    deviceId = oldDeviceId,
                    revokedAt = Date().time,
                    transferredTo = transferredTo
                )
            )
        }
    }

    /** Checks if a device ID has been revoked (license transferred away). */
    private fun isDeviceRevoked(context: Context, deviceId: String): Boolean {
        return runBlocking(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.revokedLicenseDao().getByDeviceId(deviceId) != null
        }
    }

    // ──────────────────────────────────────────────
    //  Activation
    // ──────────────────────────────────────────────

    /** Save the license start date and duration (low-level, skips consumption check). */
    fun activateLicense(context: Context, durationMs: Long) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_START_DATE, Date().time)
            .putLong(KEY_DURATION_MS, durationMs)
            .putString(KEY_DEVICE_ID, getDeviceId(context))
            .apply()
    }

    /**
     * Result of [activateLicenseFromCode].
     * @property success true if activation succeeded
     * @property errorMessage non-null user-facing message when success is false
     */
    data class SetupActivationResult(
        val success: Boolean,
        val errorMessage: String?
    )

    /**
     * Full setup/transfer flow: verify code → check consumption → consume → activate.
     *
     * Accepts two kinds of codes:
     * 1. Setup codes (SAJIL-OWNER-SETUP) — first-time owner registration
     * 2. Transfer codes (SAJIL-LICENSE-TRANSFER) — move license from old phone
     */
    fun activateLicenseFromCode(context: Context, code: String): SetupActivationResult {
        val cleaned = code.trim()
        if (cleaned.isEmpty()) return SetupActivationResult(false, "رمز الترخيص فارغ")

        val deviceId = getDeviceId(context)
        var durationMs = 0L
        var found = false

        // 1. Try setup code verification
        val setupResult = LicenseVerifier.verifySetup(cleaned, deviceId)
        if (setupResult.valid) {
            durationMs = setupResult.durationMs
            found = true
        }

        // 2. If setup fails, try transfer code verification
        if (!found) {
            val transferResult = LicenseVerifier.verifyTransfer(cleaned, deviceId)
            if (transferResult.valid) {
                durationMs = transferResult.durationMs
                found = true
            }
        }

        if (!found) return SetupActivationResult(false, "رمز الترخيص غير صحيح")

        // 4. Reject already-consumed codes
        if (isCodeConsumed(context, cleaned)) {
            return SetupActivationResult(false, "هذا الرمز مستخدم بالفعل")
        }

        // 5. Burn the code so it can never be used again
        consumeCode(context, cleaned)

        // 6. Activate with the correct duration
        activateLicense(context, durationMs)

        // 7. Save to Firestore for cloud recovery
        saveToCloud(context, "OWNER_001")

        return SetupActivationResult(true, null)
    }

    /** Save the active license to Firestore so it can be recovered on another device. */
    fun saveToCloud(context: Context, ownerId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val startDate = prefs.getLong(KEY_START_DATE, 0L)
        val durationMs = prefs.getLong(KEY_DURATION_MS, 0L)
        val deviceId = getDeviceId(context)
        if (startDate > 0L && durationMs > 0L) {
            SyncService.syncLicense(ownerId, startDate, durationMs, deviceId)
        }
    }

    /**
     * Restore license from cloud data (used when setting up on a new device
     * where the owner already has an active license in Firestore).
     */
    fun restoreFromCloud(context: Context, startDate: Long, durationMs: Long) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_START_DATE, startDate)
            .putLong(KEY_DURATION_MS, durationMs)
            .putString(KEY_DEVICE_ID, getDeviceId(context))
            .apply()
    }
    // ──────────────────────────────────────────────

    /** Check if the currently active license is still valid. */
    fun isLicenseValid(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val startDate = prefs.getLong(KEY_START_DATE, 0L)
        if (startDate == 0L) return false

        val currentDeviceId = getDeviceId(context)

        // Verify the license is bound to THIS device — prevents copying
        // app data to another phone to bypass the license.
        val storedDeviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (storedDeviceId != null && storedDeviceId != currentDeviceId) {
            return false
        }

        // Check if this device was revoked (license transferred away)
        if (isDeviceRevoked(context, currentDeviceId)) {
            // Clear the stored license so the user sees the expired screen
            prefs.edit().clear().apply()
            return false
        }

        val duration = prefs.getLong(KEY_DURATION_MS, 0L)

        // Unlimited license never expires
        if (duration == Long.MAX_VALUE) return true

        val elapsed = Date().time - startDate
        return elapsed < duration
    }

    /** Days remaining on the active license. [Long.MAX_VALUE] = unlimited. */
    fun daysRemaining(context: Context): Long {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val startDate = prefs.getLong(KEY_START_DATE, 0L)
        val duration = prefs.getLong(KEY_DURATION_MS, 0L)

        if (duration == Long.MAX_VALUE) return Long.MAX_VALUE  // unlimited

        val elapsed = Date().time - startDate
        val remaining = duration - elapsed
        return TimeUnit.MILLISECONDS.toDays(remaining)
    }

    // ──────────────────────────────────────────────
    //  Renewal
    // ──────────────────────────────────────────────

    /** Renew the license with a renewal code (duration is embedded in the code). */
    fun renewLicense(context: Context, renewalCode: String): Boolean {
        val cleaned = renewalCode.trim()
        if (cleaned.isEmpty()) return false

        // Reject already-consumed codes
        if (isCodeConsumed(context, cleaned)) return false

        val deviceId = getDeviceId(context)
        val result = LicenseVerifier.verifyRenewal(cleaned, deviceId)
        if (!result.valid) return false

        // Burn the code
        consumeCode(context, cleaned)

        // Reset start date and store the duration from the renewal code
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_START_DATE, Date().time)
            .putLong(KEY_DURATION_MS, result.durationMs)
            .apply()
        return true
    }

    // ──────────────────────────────────────────────
    //  Revocation
    // ──────────────────────────────────────────────

    /**
     * Revokes the license on a specific device using a revoke code.
     *
     * The admin generates this code offline with the private key:
     *   python3 generate_code.py revoke --device <device_id>
     *
     * The customer enters it on their old phone, and the license is
     * immediately invalidated. On next launch, [isLicenseValid] will
     * return false and the app will show the license expired screen.
     */
    fun revokeLicense(context: Context, revokeCode: String): Boolean {
        val cleaned = revokeCode.trim()
        if (cleaned.isEmpty()) return false

        val deviceId = getDeviceId(context)
        if (!LicenseVerifier.verifyRevoke(cleaned, deviceId)) return false

        // Reject already-consumed codes
        if (isCodeConsumed(context, cleaned)) return false

        // Consume + store as revoked
        consumeCode(context, cleaned)
        revokeDevice(context, deviceId)

        // Clear the stored license
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        return true
    }

    // ──────────────────────────────────────────────
    //  Display
    // ──────────────────────────────────────────────

    /** Human-readable label for the active license duration. */
    fun durationLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val ms = prefs.getLong(KEY_DURATION_MS, 6L * 30 * 24 * 60 * 60 * 1000L)
        return when (ms) {
            3L * 24 * 60 * 60 * 1000L -> "3 أيام"
            6L * 30 * 24 * 60 * 60 * 1000L -> "6 أشهر"
            Long.MAX_VALUE -> "غير محدود"
            else -> "${ms}ms"
        }
    }
}
