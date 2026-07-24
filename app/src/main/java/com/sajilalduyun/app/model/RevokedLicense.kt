package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks device IDs that have had their license transferred away (revoked).
 *
 * When a license is transferred from an old phone to a new phone via a
 * transfer code that includes `--old-device`, the old phone's device ID
 * is stored here. On every [isLicenseValid][com.sajilalduyun.app.service.LicenseManager.isLicenseValid]
 * check, if the current device appears in this table, the license is
 * considered invalid — the old phone can no longer use the app.
 */
@Entity(tableName = "revoked_licenses")
data class RevokedLicense(
    @PrimaryKey
    val deviceId: String,
    val revokedAt: Long,
    val transferredTo: String = ""
)
