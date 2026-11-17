package com.routerdash.router.zte

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses a real reading from the MC888D (firmware B05) captured by tools/probe.py. */
class ZteParserTest {
    private val raw = Json.parseToJsonElement(
        javaClass.getResource("/mc888_signal_b05.json")!!.readText()
    ).jsonObject
    private val s = ZteParser.parseSignal(raw, nowMs = 1000L)

    @Test fun `general fields`() {
        assertEquals("ENDC", s.networkType)
        assertTrue(s.isNsa5g)
        assertTrue(s.connected)
        assertEquals("MTN Nigeria", s.provider)
        assertEquals(10729L, s.downBytesPerSec)
        assertEquals(128_309_900_044L, s.monthDownBytes)
    }

    @Test fun `lte fields with hex pci`() {
        val lte = s.lte!!
        assertEquals("20", lte.band)
        assertEquals(6250, lte.earfcn)
        assertEquals(211, lte.pci) // "d3"
        assertEquals("62BD501", lte.cellId)
        assertEquals(-85.0, lte.rsrp!!, 0.0)
        assertEquals(-16.0, lte.rsrq!!, 0.0)
        assertEquals(-1.4, lte.sinr!!, 0.0)
    }

    @Test fun `5g fields`() {
        val nr = s.nr!!
        assertEquals("n78", nr.band)
        assertEquals(634080, nr.arfcn)
        assertEquals(350, nr.pci) // "15e"
        assertEquals(-95.0, nr.rsrp!!, 0.0)
        assertEquals(7.5, nr.sinr!!, 0.0)
    }

    @Test fun `carriers and band locks`() {
        assertEquals(listOf("3", "7", "7"), s.secondaryCarriers.map { it.band })
        assertEquals(20.0, s.secondaryCarriers[0].bandwidthMhz!!, 0.0)
        assertEquals(listOf(1, 3, 7, 8, 20, 28, 38, 40, 41, 42), s.lteBandsAllowed)
        assertEquals(listOf("1", "3", "7", "8", "20", "28", "38", "41", "77", "78"), s.nrBandsAllowed)
    }

    @Test fun `missing values become null not crashes`() {
        val empty = ZteParser.parseSignal(Json.parseToJsonElement("{}").jsonObject, 0)
        assertEquals(null, empty.lte)
        assertEquals(null, empty.nr)
        assertEquals(emptyList<Int>(), empty.lteBandsAllowed)
    }

    @Test fun `band masks round-trip in the router's own format`() {
        assertEquals("0x3a0080800c5", ZteParser.encodeLteMask(listOf(1, 3, 7, 8, 20, 28, 38, 40, 41, 42)))
        assertEquals(listOf(3, 7), ZteParser.decodeLteMask(ZteParser.encodeLteMask(setOf(7, 3))))
        assertEquals("0x44", ZteParser.encodeLteMask(listOf(3, 7)))
        assertEquals("3,78", ZteParser.encodeNrBands(listOf("n78", "3", "78")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty band lock is refused before reaching the router`() {
        ZteParser.encodeLteMask(emptyList())
    }
}
