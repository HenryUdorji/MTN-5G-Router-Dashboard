package com.routerdash.router

enum class Quality { EXCELLENT, GOOD, FAIR, POOR }

/**
 * Plain-language ratings for signal values. Thresholds are common rules of
 * thumb for LTE/NR, the same ones shown in the mockup.
 */
object SignalQuality {
    fun rsrp(dbm: Double?): Quality? = dbm?.let {
        when {
            it >= -80 -> Quality.EXCELLENT
            it >= -90 -> Quality.GOOD
            it >= -100 -> Quality.FAIR
            else -> Quality.POOR
        }
    }

    fun rsrq(db: Double?): Quality? = db?.let {
        when {
            it >= -10 -> Quality.GOOD
            it >= -15 -> Quality.FAIR
            else -> Quality.POOR
        }
    }

    fun sinr(db: Double?): Quality? = db?.let {
        when {
            it >= 20 -> Quality.EXCELLENT
            it >= 13 -> Quality.GOOD
            it >= 0 -> Quality.FAIR
            else -> Quality.POOR
        }
    }

    /** Overall rating = the worst of the three, since one bad value limits the link. */
    fun overall(rsrp: Double?, rsrq: Double?, sinr: Double?): Quality? =
        listOfNotNull(rsrp(rsrp), rsrq(rsrq), sinr(sinr)).maxByOrNull { it.ordinal }

    /** 0..1 position on a meter, for drawing bars. */
    fun rsrpFraction(dbm: Double) = ((dbm + 120) / 60).coerceIn(0.0, 1.0)
    fun rsrqFraction(db: Double) = ((db + 20) / 17).coerceIn(0.0, 1.0)
    fun sinrFraction(db: Double) = ((db + 5) / 35).coerceIn(0.0, 1.0)
}
