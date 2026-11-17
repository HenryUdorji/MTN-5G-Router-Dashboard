package com.routerdash.router.zte

import org.junit.Assert.assertEquals
import org.junit.Test

class ZteAuthTest {
    private val ld = "0B918943DF0962BC7A1824C0555A389347B4FEBDC7CF9D1254406D80CE44E3F9"

    @Test fun `login hash matches the scheme that worked on the real router`() {
        // Expected value computed independently with Python's hashlib (same formula as tools/probe.py).
        assertEquals(
            "12A3B7141CE7960F989B0F06C4BDCC8877F1419F4C2D58E6F1E4BCBCBEB1A456",
            ZteAuth.loginPassword("admin", ld),
        )
    }

    @Test fun `old firmware without LD falls back to base64`() {
        assertEquals("YWRtaW4=", ZteAuth.loginPassword("admin", ""))
    }

    @Test fun `AD token in both candidate hashes`() {
        val wa = "BD_MTNNGMC888V1.0.0B05"
        assertEquals(
            "63327759f3a691b69c1e93a54537b9fc94798820837f106f8fa5057af923ab6b",
            ZteAuth.adToken(wa, "", ld, ZteAuth.AdAlgorithm.SHA256),
        )
        assertEquals("cd3131808be84143387c155c1108bc20", ZteAuth.adToken(wa, "", ld, ZteAuth.AdAlgorithm.MD5))
    }
}
