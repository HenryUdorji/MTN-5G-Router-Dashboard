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

    fun loginPassword(password: String, ld: String?): String =
        if (ld.isNullOrEmpty()) {
            Base64.getEncoder().encodeToString(password.toByteArray())
        } else {
            sha256Hex(sha256Hex(password).uppercase() + ld).uppercase()
        }

    /**
     * The "AD" token every settings change needs (except LOGIN and SET_WEB_LANGUAGE):
     *   AD = SHA256( SHA256(wa_inner_version + cr_version) + RD ), uppercase hex.
     * Taken from the router's own page (js/util.js: cookWithRequest = SHA256; the inputs
     * come from js/language.js). RD is a one-time value fetched just before each change.
     */
    fun adToken(waInnerVersion: String, crVersion: String, rd: String): String {
        val h = { x: String -> sha256Hex(x).uppercase() }
        return h(h(waInnerVersion + crVersion) + rd)
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
