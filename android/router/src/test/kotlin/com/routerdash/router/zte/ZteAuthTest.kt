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

    @Test fun `AD token is uppercase SHA-256 like the router's own page`() {
        // Same formula as js/util.js + js/service.js on firmware B05; computed independently in Python.
        assertEquals(
            "68F700451250E95642AF3035BA34E20EBB8FE58C10A88C2C68ABB76BB18E9317",
            ZteAuth.adToken("BD_MTNNGMC888V1.0.0B05", "", ld),
        )
    }
}
