package com.sajilalduyun.app.security

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Validates license codes using RSA public-key signature verification.
 *
 * The app embeds only the PUBLIC key — valid codes cannot be guessed from
 * source code. Generate new codes offline with the private key via the
 * accompanying Python scripts.
 *
 * Renewal codes embed the duration in the signed purpose string:
 *   "SAJIL-LICENSE-RENEW:<duration_ms>"
 * This lets the same app accept both 3-day and 6-month codes.
 */
object LicenseVerifier {

    // RSA 2048-bit public key (X.509 SubjectPublicKeyInfo, DER-encoded)
    // Generated with: openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:2048
    // openssl rsa -pubout -in private.pem -outform DER -out public.der
    private val PUBLIC_KEY_BYTES = listOf(
        0x30, 0x82, 0x01, 0x22, 0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86,
        0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00, 0x03, 0x82, 0x01, 0x0F, 0x00,
        0x30, 0x82, 0x01, 0x0A, 0x02, 0x82, 0x01, 0x01, 0x00, 0xB4, 0x8C, 0xA8,
        0xD1, 0x11, 0x37, 0x5C, 0x14, 0x39, 0x97, 0xE3, 0x98, 0x27, 0x9A, 0xCE,
        0x6D, 0x8F, 0x69, 0xE4, 0xDE, 0xBB, 0x7D, 0xA3, 0x16, 0x03, 0x11, 0x14,
        0xF6, 0xF2, 0x05, 0xAF, 0x96, 0x57, 0xD8, 0x0D, 0x4C, 0x44, 0x91, 0x48,
        0xE8, 0x96, 0xFB, 0x76, 0x99, 0xA8, 0xF8, 0xF6, 0x32, 0xAB, 0xBB, 0x0B,
        0x13, 0x5C, 0x5B, 0xC4, 0x24, 0x5A, 0xFD, 0x24, 0xD2, 0x0F, 0x02, 0x39,
        0xEA, 0x96, 0xFD, 0xF0, 0x66, 0x98, 0x9D, 0x6F, 0x5C, 0x37, 0x75, 0xAF,
        0x0A, 0x66, 0xDB, 0x85, 0xF3, 0x92, 0x10, 0xB0, 0xC6, 0xDC, 0x31, 0xFB,
        0xC1, 0xC4, 0x11, 0x61, 0x1D, 0x20, 0x33, 0x5D, 0x0A, 0x7B, 0x8E, 0xA5,
        0xD7, 0x49, 0xB7, 0x49, 0x84, 0x99, 0x87, 0x59, 0xB9, 0x6D, 0x09, 0xBA,
        0x33, 0x6B, 0xF3, 0xB5, 0x08, 0x07, 0x53, 0xCE, 0x48, 0xF4, 0x63, 0xCE,
        0xAF, 0xB5, 0x98, 0x5D, 0x33, 0xC4, 0x47, 0xF3, 0xDC, 0xE1, 0x2C, 0x3C,
        0x42, 0x75, 0x2A, 0x0B, 0x40, 0xFD, 0x04, 0xCD, 0xA9, 0x88, 0xDE, 0x96,
        0x16, 0x71, 0xF2, 0x78, 0xA9, 0xFC, 0x34, 0x61, 0x19, 0x92, 0xD3, 0x79,
        0x40, 0x32, 0xEB, 0x67, 0xE0, 0xB7, 0x46, 0x63, 0xB9, 0xAB, 0x53, 0x1C,
        0x4E, 0x49, 0xC7, 0xFD, 0x47, 0x75, 0x5C, 0x14, 0xA7, 0x76, 0xD6, 0xFB,
        0xF3, 0x03, 0x22, 0x99, 0x33, 0xD9, 0x90, 0xBE, 0xD9, 0x4E, 0x10, 0x6D,
        0x4C, 0x7D, 0x30, 0x46, 0xB0, 0x0F, 0x0B, 0x54, 0x45, 0x68, 0x4F, 0xB9,
        0x7C, 0xFA, 0xA8, 0x89, 0xEA, 0xF7, 0xA6, 0x52, 0xCD, 0x57, 0x40, 0x3D,
        0x00, 0x79, 0xCB, 0x85, 0x97, 0xC0, 0xC3, 0x37, 0xE4, 0xAF, 0xBE, 0x3B,
        0xAC, 0xFF, 0x09, 0x77, 0xA2, 0xD2, 0xCC, 0xE9, 0xD1, 0x22, 0x39, 0x27,
        0x50, 0xC5, 0x4E, 0x79, 0x3E, 0xF5, 0x76, 0x2B, 0x84, 0x2A, 0x36, 0x97,
        0x57, 0x02, 0x03, 0x01, 0x00, 0x01,
    ).map { it.toByte() }.toByteArray()

    private val publicKey: java.security.PublicKey by lazy {
        val factory = KeyFactory.getInstance("RSA")
        factory.generatePublic(X509EncodedKeySpec(PUBLIC_KEY_BYTES))
    }

    /**
     * Verifies that [code] is a valid Base64-encoded RSA-SHA256 signature
     * over [purpose] (the data that was signed).
     *
     * @param purpose The exact byte-array that was signed during code generation,
     *                e.g. "SAJIL-OWNER-SETUP" or "SAJIL-LICENSE-RENEW:259200000".
     * @param code    The Base64-encoded signature to verify.
     * @return true if the signature is valid (proving the code was generated
     *         with the corresponding private key).
     */
    fun verify(code: String, purpose: String): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(purpose.toByteArray(Charsets.UTF_8))

            val decoded = Base64.decode(code.trim(), Base64.DEFAULT)
            sig.verify(decoded)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Result of verifying a renewal code that encodes its own duration.
     */
    data class RenewalResult(
        val valid: Boolean,
        val durationMs: Long
    )

    /**
     * Known durations that renewal and setup codes can encode.
     * The signed purpose is "SAJIL-LICENSE-RENEW:<duration_ms>" or
     * "SAJIL-OWNER-SETUP:<duration_ms>".
     *
     * Long.MAX_VALUE represents an unlimited (non-expiring) license.
     */
    private val KNOWN_DURATIONS = listOf(
        3L * 24 * 60 * 60 * 1000L,         // 3 days
        6L * 30 * 24 * 60 * 60 * 1000L,    // ~6 months
        Long.MAX_VALUE                      // Unlimited
    )

    /**
     * Result of verifying a renewal code that encodes its own duration.
     */
    // Already defined above: data class RenewalResult(valid, durationMs)

    /**
     * Result of verifying a setup code that encodes its own duration.
     */
    data class SetupResult(
        val valid: Boolean,
        val durationMs: Long
    )

    /**
     * Result of verifying a transfer code.
     * @property oldDeviceId optionally embedded in the transfer code for revocation.
     */
    data class TransferResult(
        val valid: Boolean,
        val durationMs: Long,
        val oldDeviceId: String? = null
    )

    /**
     * Verifies a transfer code — used when moving a license to a new phone.
     *
     * Purpose formats:
     *   "SAJIL-LICENSE-TRANSFER:<duration>:<newDeviceId>"            (simple transfer)
     *   "SAJIL-LICENSE-TRANSFER:<duration>:<newDeviceId>:<oldId>"    (transfer + auto-revoke)
     */
    fun verifyTransfer(code: String, newDeviceId: String): TransferResult {
        for (duration in KNOWN_DURATIONS) {
            val purpose = "SAJIL-LICENSE-TRANSFER:$duration:$newDeviceId"
            if (verify(code, purpose)) {
                return TransferResult(true, duration)
            }
        }
        return TransferResult(false, 0L)
    }

    /**
     * Verifies a revoke code that invalidates a license on a specific device.
     * Purpose: "SAJIL-LICENSE-REVOKE:<deviceId>"
     */
    fun verifyRevoke(code: String, deviceId: String): Boolean {
        val purpose = "SAJIL-LICENSE-REVOKE:$deviceId"
        return verify(code, purpose)
    }

    /**
     * Verifies a renewal code by trying each known duration.
     *
     * When [deviceId] is provided (non-null), also tries device-locked purposes
     * first: "SAJIL-LICENSE-RENEW:<duration>:<deviceId>". This prevents a code
     * generated with --device from working on any other phone.
     *
     * Falls back to non-device-specific purposes for backward compatibility
     * with codes generated without --device.
     */
    fun verifyRenewal(code: String, deviceId: String? = null): RenewalResult {
        // 1. Device-locked codes (highest priority)
        if (deviceId != null) {
            for (duration in KNOWN_DURATIONS) {
                val purpose = "SAJIL-LICENSE-RENEW:$duration:$deviceId"
                if (verify(code, purpose)) {
                    return RenewalResult(true, duration)
                }
            }
        }
        // 2. Non-device-locked duration codes (backward compat)
        for (duration in KNOWN_DURATIONS) {
            val purpose = "SAJIL-LICENSE-RENEW:$duration"
            if (verify(code, purpose)) {
                return RenewalResult(true, duration)
            }
        }
        return RenewalResult(false, 0L)
    }

    /**
     * Verifies a setup (owner registration) code.
     *
     * When [deviceId] is provided, tries device-locked purposes first:
     * "SAJIL-OWNER-SETUP:<duration>:<deviceId>". This locks the code
     * to a specific phone — the same code is useless on any other device.
     *
     * Falls back to non-device-specific purposes, then to the bare
     * "SAJIL-OWNER-SETUP" for full backward compatibility.
     */
    fun verifySetup(code: String, deviceId: String? = null): SetupResult {
        // 1. Device-locked codes (highest priority — machine-locked license)
        if (deviceId != null) {
            for (duration in KNOWN_DURATIONS) {
                val purpose = "SAJIL-OWNER-SETUP:$duration:$deviceId"
                if (verify(code, purpose)) {
                    return SetupResult(true, duration)
                }
            }
        }
        // 2. Non-device-locked duration codes (backward compat)
        for (duration in KNOWN_DURATIONS) {
            val purpose = "SAJIL-OWNER-SETUP:$duration"
            if (verify(code, purpose)) {
                return SetupResult(true, duration)
            }
        }
        // 3. Bare SAJIL-OWNER-SETUP (oldest backward compat → 6 months)
        if (verify(code, "SAJIL-OWNER-SETUP")) {
            return SetupResult(true, 6L * 30 * 24 * 60 * 60 * 1000L)
        }
        return SetupResult(false, 0L)
    }

    /**
     * Returns true if the given duration represents an unlimited license.
     */
    fun isUnlimited(durationMs: Long): Boolean = durationMs == Long.MAX_VALUE

    /**
     * Supported durations and their display labels.
     */
    val supportedDurations: List<Pair<Long, String>>
        get() = KNOWN_DURATIONS.map { ms ->
            ms to when (ms) {
                3L * 24 * 60 * 60 * 1000L -> "3 أيام"
                6L * 30 * 24 * 60 * 60 * 1000L -> "6 أشهر"
                Long.MAX_VALUE -> "غير محدود"
                else -> "${ms}ms"
            }
        }
}
