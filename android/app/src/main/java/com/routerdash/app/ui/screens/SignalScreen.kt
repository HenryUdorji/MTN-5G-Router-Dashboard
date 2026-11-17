package com.routerdash.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.data.DashState
import com.routerdash.app.data.SignalPoint
import com.routerdash.app.ui.components.AppCard
import com.routerdash.app.ui.components.BandNames
import com.routerdash.app.ui.components.CardTitle
import com.routerdash.app.ui.components.ChartScale
import com.routerdash.app.ui.components.LegendDot
import com.routerdash.app.ui.components.Segmented
import com.routerdash.app.ui.components.TwoLineChart
import com.routerdash.app.ui.components.fmt
import com.routerdash.app.ui.components.qualityLabel
import com.routerdash.app.ui.theme.AppTheme
import com.routerdash.router.Quality
import com.routerdash.router.SignalQuality
import com.routerdash.router.SignalSnapshot

private enum class Measure(
    val label: String,
    val unit: String,
    val scale: ChartScale,
    val lte: (SignalPoint) -> Double?,
    val nr: (SignalPoint) -> Double?,
) {
    RSRP("RSRP", "dBm", ChartScale(-120.0, -60.0, listOf(-60.0, -80.0, -100.0, -120.0), poorBelow = -100.0), { it.lteRsrp }, { it.nrRsrp }),
    RSRQ("RSRQ", "dB", ChartScale(-20.0, -3.0, listOf(-5.0, -10.0, -15.0, -20.0), poorBelow = -15.0), { it.lteRsrq }, { it.nrRsrq }),
    SINR("SINR", "dB", ChartScale(-5.0, 25.0, listOf(20.0, 10.0, 0.0), poorBelow = 0.0), { it.lteSinr }, { it.nrSinr }),
}

private val RANGES = listOf("15m" to 15 * 60_000L, "1h" to 60 * 60_000L)

@Composable
fun SignalScreen(state: DashState.Live) {
    val snap = state.snapshot ?: return HomeScreen(state, onTryBandLock = {}) // shows the connecting spinner
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveReadings(snap)
        HistoryCard(state.history)
        CarriersCard(snap)
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun LiveReadings(s: SignalSnapshot) {
    val c = AppTheme.colors
    AppCard {
        CardTitle("Live readings")
        TableRow("", "4G · ${BandNames.lteLabel(s.lte?.band)}", "5G · ${if (s.nr == null) "off" else BandNames.nrLabel(s.nr?.band)}", header = true)
        TableRow("RSRP", rated(s.lte?.rsrp, "dBm", SignalQuality.rsrp(s.lte?.rsrp)), rated(s.nr?.rsrp, "dBm", SignalQuality.rsrp(s.nr?.rsrp)))
        TableRow("RSRQ", rated(s.lte?.rsrq, "dB", SignalQuality.rsrq(s.lte?.rsrq)), rated(s.nr?.rsrq, "dB", SignalQuality.rsrq(s.nr?.rsrq)))
        TableRow("SINR", rated(s.lte?.sinr, "dB", SignalQuality.sinr(s.lte?.sinr)), rated(s.nr?.sinr, "dB", SignalQuality.sinr(s.nr?.sinr)))
        TableRow("RSSI", fmt(s.lte?.rssi, " dBm"), fmt(s.nr?.rssi, " dBm"))
        TableRow("PCI", s.lte?.pci?.toString() ?: "—", s.nr?.pci?.toString() ?: "—")
        TableRow("Channel", s.lte?.earfcn?.toString() ?: "—", s.nr?.arfcn?.toString() ?: "—")
        if (s.lte?.cellId != null) {
            Text("4G cell ID ${s.lte?.cellId}", color = c.textMuted, fontSize = 12.sp)
        }
    }
}

private fun rated(v: Double?, unit: String, q: Quality?): String =
    if (v == null) "—" else "${fmt(v)} $unit · ${qualityLabel(q)}"

@Composable
private fun TableRow(label: String, a: String, b: String, header: Boolean = false) {
    val c = AppTheme.colors
    val color = if (header) c.textMuted else c.text
    val size = if (header) 12.sp else 13.sp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), color = c.textSecondary, fontSize = 13.sp)
        Text(a, Modifier.weight(1f), color = color, fontSize = size, fontWeight = if (header) FontWeight.Normal else FontWeight.Medium)
        Text(b, Modifier.weight(1f), color = color, fontSize = size, fontWeight = if (header) FontWeight.Normal else FontWeight.Medium)
    }
}

@Composable
private fun HistoryCard(history: List<SignalPoint>) {
    val c = AppTheme.colors
    var measureIdx by rememberSaveable { mutableIntStateOf(Measure.SINR.ordinal) }
    var rangeIdx by rememberSaveable { mutableIntStateOf(1) }
    val m = Measure.entries[measureIdx]
    val cutoff = (history.lastOrNull()?.timeMs ?: 0L) - RANGES[rangeIdx].second
    val pts = history.filter { it.timeMs >= cutoff }
    val lte = pts.map(m.lte)
    val nr = pts.map(m.nr)

    AppCard {
        CardTitle("History")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Segmented(Measure.entries.map { it.label }, measureIdx, { measureIdx = it })
            Segmented(RANGES.map { it.first }, rangeIdx, { rangeIdx = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(c.series4g, "4G ${fmt(lte.lastOrNull())}")
            LegendDot(c.series5g, "5G ${fmt(nr.lastOrNull())}")
        }
        TwoLineChart(
            lte, nr, m.scale,
            description = "${m.label} history. 4G now ${fmt(lte.lastOrNull())} ${m.unit}, 5G now ${fmt(nr.lastOrNull())} ${m.unit}.",
        )
        val shown = if (pts.size < 2) 0 else (pts.last().timeMs - pts.first().timeMs) / 60_000
        Text(
            "Shaded: poor · ${if (shown < 1) "collecting since the app opened" else "showing $shown min"} · longer history comes with saved logs",
            color = c.textMuted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun CarriersCard(s: SignalSnapshot) {
    val c = AppTheme.colors
    val count4g = 1 + s.secondaryCarriers.size
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Combined carriers")
            Text("$count4g × 4G${if (s.nr != null) " + 1 × 5G" else ""}", color = c.textMuted, fontSize = 12.sp)
        }
        Column {
            CarrierRow(BandNames.lteLabel(s.lte?.band), "primary", c.tipBg, c.link,
                listOfNotNull(s.lte?.earfcn?.let { "EARFCN $it" }, s.lte?.pci?.let { "PCI $it" }).joinToString(" · "))
            s.secondaryCarriers.forEach { cc ->
                HorizontalDivider(color = c.divider)
                CarrierRow(BandNames.lteLabel(cc.band), null, Color.Transparent, c.text,
                    listOfNotNull(cc.bandwidthMhz?.let { "${fmt(it)} MHz" }, cc.earfcn?.let { "EARFCN $it" }).joinToString(" · "))
            }
            if (s.nr != null) {
                HorizontalDivider(color = c.divider)
                CarrierRow(BandNames.nrLabel(s.nr?.band), "5G", c.criticalBg, c.series5g,
                    listOfNotNull(s.nr?.arfcn?.let { "ARFCN $it" }, s.nr?.pci?.let { "PCI $it" }).joinToString(" · "))
            }
        }
    }
}

@Composable
private fun CarrierRow(band: String, tag: String?, tagBg: Color, tagColor: Color, detail: String) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(band, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (tag != null) {
            Text(
                tag, Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(tagBg).padding(horizontal = 6.dp, vertical = 1.dp),
                color = tagColor, fontSize = 11.sp,
            )
        }
        Box(Modifier.weight(1f))
        Text(detail, color = c.textMuted, fontSize = 13.sp)
    }
}
