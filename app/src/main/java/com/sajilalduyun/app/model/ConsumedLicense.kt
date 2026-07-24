package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks license codes that have already been used (consumed).
 *
 * Once a license code is redeemed during setup or renewal, its SHA-256 hash
 * is stored here. Future attempts to reuse the same code are rejected,
 * preventing the same code from being applied to multiple installations.
 *
 * The deviceId is the Android ID of the device where the code was consumed,
 * for informational purposes. The hash is the primary constraint.
 */
@Entity(tableName = "consumed_licenses")
data class ConsumedLicense(
    @PrimaryKey
    val codeHash: String,       // SHA-256 hex digest of the license code
    val deviceId: String,       // ANDROID_ID of consuming device
    val consumedAt: Long        // timestamp when consumed
)
