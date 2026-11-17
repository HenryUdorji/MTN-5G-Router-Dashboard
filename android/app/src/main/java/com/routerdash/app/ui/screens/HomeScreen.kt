package com.routerdash.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.data.DashState
import com.routerdash.app.ui.components.AppCard
import com.routerdash.app.ui.components.BandNames
import com.routerdash.app.ui.components.CardTitle
import com.routerdash.app.ui.components.LegendDot
import com.routerdash.app.ui.components.MetricMeter
import com.routerdash.app.ui.components.Overline
import com.routerdash.app.ui.components.QualityBadge
import com.routerdash.app.ui.components.SinrHomeScale
import com.routerdash.app.ui.components.TwoLineChart
import com.routerdash.app.ui.components.StatusPill
import com.routerdash.app.ui.components.fmt
import com.routerdash.app.ui.theme.AppTheme
import com.routerdash.router.LteSignal
import com.routerdash.router.NrSignal
import com.routerdash.router.Quality
import com.routerdash.router.SignalQuality
import com.routerdash.router.SignalSnapshot
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(state: DashState.Live, onTryBandLock: () -> Unit) {
    val c = AppTheme.colors
    val snap = state.snapshot
    if (snap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(state.problem ?: "Connecting to ${state.host}…", color = c.textSecondary)
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusLine(snap, state.problem)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LteCard(snap.lte, Modifier.weight(1f))
            NrCard(snap.nr, Modifier.weight(1f))
        }
        NoisyAnchorTip(snap, onTryBandLock)
        ChartCard(state)
        DataCard(snap)
        Spacer16()
    }
}

@Composable
private fun StatusLine(snap: SignalSnapshot, problem: String?) {
    val c = AppTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val ago = ((now - snap.timestampMs) / 1000).coerceAtLeast(0)
    val type = when {
        snap.isNsa5g -> "5G NSA"
        snap.networkType.equals("SA", true) -> "5G SA"
        else -> snap.networkType ?: "—"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (problem != null) StatusPill(false, "Router not responding")
        else StatusPill(snap.connected, if (snap.connected) "Connected" else "No internet")
        Text("$type · ${snap.provider ?: ""} · ${ago} s ago", color = c.textMuted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun LteCard(lte: LteSignal?, modifier: Modifier) {
    val overall = SignalQuality.overall(lte?.rsrp, lte?.rsrq, lte?.sinr)
    AppCard(modifier) {
        BandHeader("4G LTE", BandNames.lteLabel(lte?.band), overall)
        Meters(lte?.rsrp, lte?.rsrq, lte?.sinr)
    }
}

@Composable
private fun NrCard(nr: NrSignal?, modifier: Modifier) {
    val overall = SignalQuality.overall(nr?.rsrp, nr?.rsrq, nr?.sinr)
    AppCard(modifier) {
        BandHeader("5G NR", if (nr == null) "Off" else BandNames.nrLabel(nr.band), overall)
        Meters(nr?.rsrp, nr?.rsrq, nr?.sinr)
    }
}

@Composable
private fun BandHeader(title: String, band: String, overall: Quality?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Overline(title)
        Text(band, color = AppTheme.colors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        QualityBadge(overall, if (overall == Quality.POOR) "Poor quality" else com.routerdash.app.ui.components.qualityLabel(overall))
    }
}

@Composable
private fun Meters(rsrp: Double?, rsrq: Double?, sinr: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricMeter("RSRP", fmt(rsrp), SignalQuality.rsrp(rsrp), rsrp?.let(SignalQuality::rsrpFraction))
        MetricMeter("RSRQ", fmt(rsrq), SignalQuality.rsrq(rsrq), rsrq?.let(SignalQuality::rsrqFraction))
        MetricMeter("SINR", fmt(sinr), SignalQuality.sinr(sinr), sinr?.let(SignalQuality::sinrFraction))
    }
}

/** Shown when the 4G anchor is noisy and the router can already see other 4G bands. */
@Composable
private fun NoisyAnchorTip(snap: SignalSnapshot, onTryBandLock: () -> Unit) {
    val c = AppTheme.colors
    val lte = snap.lte ?: return
    if (SignalQuality.sinr(lte.sinr) != Quality.POOR) return
    val others = snap.secondaryCarriers.map { it.band }.filter { it != lte.band }.distinct()
    if (others.isEmpty()) return
    val names = others.joinToString(" or ") { "Band $it" }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tipBg)
            .border(1.dp, c.tipLine, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Your 4G anchor is noisy", color = c.text, fontWeight = FontWeight.SemiBold)
        Text("$names may give a cleaner link than Band ${lte.band}.", color = c.textSecondary, fontSize = 13.sp)
        Button(onClick = onTryBandLock, modifier = Modifier.height(44.dp)) { Text("Try a band lock", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ChartCard(state: DashState.Live) {
    val c = AppTheme.colors
    val lte = state.history.map { it.lteSinr }
    val nr = state.history.map { it.nrSinr }
    val minutes = state.history.let { h -> if (h.size < 2) 0 else ((h.last().timeMs - h.first().timeMs) / 60_000).toInt() }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Signal quality (SINR)")
            Text(if (minutes < 1) "since app opened" else "last $minutes min", color = c.textMuted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(c.series4g, "4G ${fmt(lte.lastOrNull())}")
            LegendDot(c.series5g, "5G ${fmt(nr.lastOrNull())}")
        }
        TwoLineChart(
            lte, nr, SinrHomeScale,
            description = "SINR chart. 4G now ${fmt(lte.lastOrNull())} dB, 5G now ${fmt(nr.lastOrNull())} dB. Shaded area is poor quality, below 0 dB.",
        )
        Text("Shaded: poor (below 0 dB) · higher is better", color = c.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun DataCard(snap: SignalSnapshot) {
    val c = AppTheme.colors
    val down = snap.monthDownBytes / 1e9
    val up = snap.monthUpBytes / 1e9
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Data this month")
            Text("%.1f GB".format(down + up), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        val total = (down + up).takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (down > 0) Box(Modifier.weight((down / total).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.series4g))
            if (up > 0) Box(Modifier.weight((up / total).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.series5g))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Down %.1f GB".format(down), color = c.textSecondary, fontSize = 12.sp)
            Text("Up %.1f GB".format(up), color = c.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Spacer16() = Box(Modifier.height(16.dp))
