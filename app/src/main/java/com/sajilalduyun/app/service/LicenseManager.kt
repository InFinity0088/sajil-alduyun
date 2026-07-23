package com.sajilalduyun.app.service

import android.content.Context
import com.sajilalduyun.app.security.LicenseVerifier
import java.util.Date
import java.util.concurrent.TimeUnit

object LicenseManager {

    // SharedPreferences = simple key-value storage for small data
    private const val PREF_NAME = "license_prefs"
    private const val KEY_START_DATE = "license_start_date"
    private const val KEY_RENEWAL_CODE = "renewal_code"

    // License is valid for 6 months (in milliseconds)
    private const val LICENSE_DURATION_MS = 6L * 30 * 24 * 60 * 60 * 1000L

    // Save the license start date when owner first sets up
    fun activateLicense(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_START_DATE, Date().time).apply()
    }

    // Check if license is still valid
    fun isLicenseValid(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val startDate = prefs.getLong(KEY_START_DATE, 0L)

        if (startDate == 0L) return false

        val elapsed = Date().time - startDate
        return elapsed < LICENSE_DURATION_MS
    }

    // How many days remaining
    fun daysRemaining(context: Context): Long {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val startDate = prefs.getLong(KEY_START_DATE, 0L)
        val elapsed = Date().time - startDate
        val remaining = LICENSE_DURATION_MS - elapsed
        return TimeUnit.MILLISECONDS.toDays(remaining)
    }

    // Renew license with a new code
    fun renewLicense(context: Context, renewalCode: String): Boolean {
        val cleaned = renewalCode.trim()
        if (cleaned.isEmpty()) return false
        if (!LicenseVerifier.verify(cleaned, "SAJIL-LICENSE-RENEW")) return false

        // Reset the start date to now
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_START_DATE, Date().time).apply()
        return true
    }
}