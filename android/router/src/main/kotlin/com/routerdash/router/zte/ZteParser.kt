package com.routerdash.router.zte

import com.routerdash.router.Carrier
import com.routerdash.router.LteSignal
import com.routerdash.router.NrSignal
import com.routerdash.router.RouterInfo
import com.routerdash.router.SignalSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Turns the router's raw JSON (all values are strings) into models. */
object ZteParser {

    /** Fields requested for [parseSignal], plus "loginfo" to detect an expired session. */
    val SIGNAL_FIELDS = listOf(
        "loginfo", "network_type", "network_provider", "ppp_status",
        "realtime_tx_thrpt", "realtime_rx_thrpt", "wan_active_channel",
        "lte_rsrp", "lte_rsrq", "lte_snr", "lte_rssi", "lte_band", "lte_pci", "cell_id",
        "lte_ca_pcell_band", "lte_ca_pcell_freq", "lte_multi_ca_scell_info", "lte_band_lock",
        "Z5g_rsrp", "Z5g_rsrq", "Z5g_SINR", "Z5g_rssi",
        "nr5g_action_band", "nr5g_action_channel", "nr5g_pci", "nr5g_band_lock",
        "monthly_rx_bytes", "monthly_tx_bytes",
    )

    val INFO_FIELDS = listOf("model_name", "wa_inner_version", "web_version", "hardware_version")

    fun parseSignal(j: JsonObject, nowMs: Long): SignalSnapshot {
        val lteBand = j.str("lte_ca_pcell_band") ?: j.str("lte_band")
        val lte = LteSignal(
            band = lteBand,
            earfcn = (j.str("lte_ca_pcell_freq") ?: j.str("wan_active_channel"))?.toIntOrNull(),
            pci = j.str("lte_pci")?.toIntOrNull(16),
            cellId = j.str("cell_id")?.uppercase(),
            rsrp = j.num("lte_rsrp"),
            rsrq = j.num("lte_rsrq"),
            sinr = j.num("lte_snr"),
            rssi = j.num("lte_rssi"),
        )
        val nr = NrSignal(
            band = j.str("nr5g_action_band"),
            arfcn = j.str("nr5g_action_channel")?.toIntOrNull(),
            pci = j.str("nr5g_pci")?.toIntOrNull(16),
            rsrp = j.num("Z5g_rsrp"),
            rsrq = j.num("Z5g_rsrq"),
            sinr = j.num("Z5g_SINR"),
            rssi = j.num("Z5g_rssi"),
        )
        return SignalSnapshot(
            timestampMs = nowMs,
            networkType = j.str("network_type"),
            provider = j.str("network_provider"),
            connected = j.str("ppp_status")?.contains("connected") == true &&
                j.str("ppp_status")?.contains("disconnected") != true,
            lte = lte.takeIf { it.band != null || it.rsrp != null },
            nr = nr.takeIf { it.band != null || it.rsrp != null },
            secondaryCarriers = parseCarriers(j.str("lte_multi_ca_scell_info")),
            lteBandsAllowed = decodeLteMask(j.str("lte_band_lock")),
            nrBandsAllowed = j.str("nr5g_band_lock")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            downBytesPerSec = j.num("realtime_rx_thrpt")?.toLong() ?: 0,
            upBytesPerSec = j.num("realtime_tx_thrpt")?.toLong() ?: 0,
            monthDownBytes = j.num("monthly_rx_bytes")?.toLong() ?: 0,
            monthUpBytes = j.num("monthly_tx_bytes")?.toLong() ?: 0,
        )
    }

    fun parseInfo(j: JsonObject) = RouterInfo(
        model = j.str("model_name"),
        firmware = j.str("wa_inner_version"),
        webVersion = j.str("web_version"),
        hardwareVersion = j.str("hardware_version"),
    )

    /** "0x3a0080800c5" -> [1, 3, 7, 8, 20, 28, 38, 40, 41, 42]: bit n-1 set = band n allowed. */
    fun decodeLteMask(mask: String?): List<Int> {
        val m = mask?.removePrefix("0x")?.removePrefix("0X")?.toULongOrNull(16) ?: return emptyList()
        return (0 until 64).filter { (m shr it) and 1uL == 1uL }.map { it + 1 }
    }

    /** "1,211,1,3,1650,20.0;2,211,1,7,2852,20.0" -> carriers (index, pci, state, band, earfcn, MHz). */
    fun parseCarriers(info: String?): List<Carrier> =
        info.orEmpty().split(";").mapNotNull { part ->
            val f = part.split(",")
            if (f.size < 6) null else Carrier(
                band = f[3].trim(),
                earfcn = f[4].trim().toIntOrNull(),
                pci = f[1].trim().toIntOrNull(),
                bandwidthMhz = f[5].trim().toDoubleOrNull(),
            )
        }

    internal fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    internal fun JsonObject.num(key: String): Double? = str(key)?.toDoubleOrNull()
}
