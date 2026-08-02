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
        0x30, 0x82, 0x01, 0x22, 0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86,
        0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00, 0x03, 0x82, 0x01, 0x0f, 0x00,
        0x30, 0x82, 0x01, 0x0a, 0x02, 0x82, 0x01, 0x01, 0x00, 0xae, 0x29, 0xe6,
        0x46, 0x1c, 0x7f, 0x93, 0x4e, 0x8a, 0xdf, 0xd9, 0xc2, 0x7b, 0x6f, 0x80,
        0x22, 0xa4, 0xb7, 0x8f, 0x9b, 0x02, 0x6e, 0x0e, 0x3f, 0x42, 0xa6, 0x16,
        0x17, 0xca, 0x82, 0xb5, 0x6a, 0xb9, 0x07, 0xc5, 0xec, 0xd6, 0x8d, 0xdf,
        0xad, 0x0f, 0x46, 0x15, 0xf5, 0x2d, 0x67, 0x8c, 0xec, 0x10, 0x15, 0x51,
        0x5e, 0xeb, 0x99, 0x0e, 0xbb, 0xc6, 0xdb, 0xf1, 0x34, 0x86, 0x28, 0x41,
        0x51, 0x3a, 0x39, 0xec, 0x69, 0x4c, 0xd4, 0x74, 0x4b, 0x05, 0xa3, 0xe4,
        0x0c, 0xe7, 0xab, 0x28, 0x15, 0x3d, 0x79, 0x31, 0xac, 0x74, 0xf6, 0xd3,
        0xcc, 0x4f, 0x65, 0x02, 0x23, 0x71, 0x85, 0x01, 0xd8, 0x9e, 0x04, 0x58,
        0xb3, 0x8b, 0x4b, 0x94, 0x0e, 0x28, 0x0c, 0x53, 0x3d, 0xf2, 0x6e, 0x19,
        0xeb, 0x5c, 0x1d, 0xed, 0x3d, 0x9e, 0x33, 0x0f, 0x6b, 0xeb, 0xf1, 0x76,
        0xab, 0x38, 0x5f, 0x60, 0x07, 0x47, 0xa8, 0xe7, 0xa6, 0x67, 0xd6, 0x94,
        0x9e, 0x1e, 0x2a, 0x04, 0x93, 0xf5, 0x98, 0x37, 0xda, 0x82, 0x5b, 0xd8,
        0x94, 0x8f, 0x74, 0x57, 0x25, 0xfa, 0xd3, 0xb1, 0x43, 0x78, 0x06, 0x3a,
        0xc5, 0x05, 0x1e, 0x82, 0x2f, 0x45, 0x20, 0x80, 0x69, 0x07, 0x80, 0x29,
        0x53, 0x3f, 0xfa, 0xde, 0x8d, 0x30, 0x4c, 0x79, 0x5b, 0xe2, 0x7a, 0x33,
        0x0b, 0xcd, 0x51, 0x2f, 0xb8, 0x53, 0xb5, 0x95, 0x02, 0xee, 0x4a, 0x81,
        0x61, 0xd1, 0xdf, 0x03, 0xa7, 0xb9, 0x57, 0x20, 0xa3, 0xf5, 0x54, 0x64,
        0xa2, 0x2a, 0xd4, 0x09, 0x9a, 0xf5, 0x66, 0x7a, 0x97, 0xfc, 0x81, 0xfa,
        0x63, 0x6d, 0x4f, 0xb9, 0x7b, 0x92, 0x95, 0x9a, 0xae, 0x8b, 0x90, 0xbd,
        0x54, 0x34, 0x33, 0x53, 0x0b, 0x7b, 0x98, 0x31, 0xd9, 0x57, 0x96, 0x36,
        0xff, 0xb5, 0xec, 0x73, 0xd1, 0x1d, 0x1f, 0x3a, 0x7c, 0xc0, 0xe2, 0xb3,
        0x6b, 0x02, 0x03, 0x01, 0x00, 0x01,
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
