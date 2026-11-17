package com.routerdash.router.zte

import java.security.MessageDigest
import java.util.Base64

/**
 * Hashing used by the ZTE web API.
 *
 * Confirmed on MTN Nigeria firmware BD_MTNNGMC888V1.0.0B05:
 *   password sent = SHA256( SHA256(password).uppercase() + LD ).uppercase()
 * where LD is a one-time value fetched just before logging in.
 *
 * Older firmware with no LD value uses base64(password) instead.
 */
object ZteAuth {
    fun sha256Hex(s: String): String = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()))
    fun md5Hex(s: String): String = hex(MessageDigest.getInstance("MD5").digest(s.toByteArray()))

    fun loginPassword(password: String, ld: String?): String =
        if (ld.isNullOrEmpty()) {
            Base64.getEncoder().encodeToString(password.toByteArray())
        } else {
            sha256Hex(sha256Hex(password).uppercase() + ld).uppercase()
        }

    /**
     * The "AD" token that settings changes need: hash(hash(wa_inner_version + cr_version) + RD).
     * SHA-256 confirmed on firmware B05 (a test write returned "success"); MD5 kept for older firmware.
     */
    fun adToken(waInnerVersion: String, crVersion: String, rd: String, algorithm: AdAlgorithm): String {
        val h: (String) -> String = if (algorithm == AdAlgorithm.SHA256) ::sha256Hex else ::md5Hex
        return h(h(waInnerVersion + crVersion) + rd)
    }

    enum class AdAlgorithm { SHA256, MD5 }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
