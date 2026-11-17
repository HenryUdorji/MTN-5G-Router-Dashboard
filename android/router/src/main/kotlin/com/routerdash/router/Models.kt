package com.routerdash.router

/** 4G LTE readings. Any value can be null when the router doesn't report it. */
data class LteSignal(
    val band: String?,
    val earfcn: Int?,
    val pci: Int?,
    val cellId: String?,
    val rsrp: Double?,
    val rsrq: Double?,
    val sinr: Double?,
    val rssi: Double?,
)

/** 5G NR readings. */
data class NrSignal(
    val band: String?,
    val arfcn: Int?,
    val pci: Int?,
    val rsrp: Double?,
    val rsrq: Double?,
    val sinr: Double?,
    val rssi: Double?,
)

/** An extra carrier combined with the main one (carrier aggregation). */
data class Carrier(
    val band: String,
    val earfcn: Int?,
    val pci: Int?,
    val bandwidthMhz: Double?,
)

data class SignalSnapshot(
    val timestampMs: Long,
    /** Raw network type, e.g. "ENDC" (5G NSA), "LTE", "SA". */
    val networkType: String?,
    val provider: String?,
    val connected: Boolean,
    val lte: LteSignal?,
    val nr: NrSignal?,
    val secondaryCarriers: List<Carrier>,
    /** LTE band numbers currently allowed by the band lock. */
    val lteBandsAllowed: List<Int>,
    /** 5G band numbers currently allowed, e.g. "78". */
    val nrBandsAllowed: List<String>,
    val downBytesPerSec: Long,
    val upBytesPerSec: Long,
    val monthDownBytes: Long,
    val monthUpBytes: Long,
) {
    val isNsa5g: Boolean get() = networkType.equals("ENDC", ignoreCase = true)
}

data class RouterInfo(
    val model: String?,
    val firmware: String?,
    val webVersion: String?,
    val hardwareVersion: String?,
)
