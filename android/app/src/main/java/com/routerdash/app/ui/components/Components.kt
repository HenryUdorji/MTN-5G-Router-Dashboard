package com.routerdash.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.ui.theme.AppTheme
import com.routerdash.router.Quality

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = AppTheme.colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Overline(text: String) {
    Text(
        text.uppercase(), color = AppTheme.colors.textMuted,
        fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp,
    )
}

fun qualityLabel(q: Quality?): String = when (q) {
    Quality.EXCELLENT -> "Excellent"
    Quality.GOOD -> "Good"
    Quality.FAIR -> "Fair"
    Quality.POOR -> "Poor"
    null -> "No data"
}

@Composable
fun qualityMeterColor(q: Quality?): Color {
    val c = AppTheme.colors
    return when (q) {
        Quality.EXCELLENT, Quality.GOOD -> c.good
        Quality.FAIR -> c.warning
        Quality.POOR -> c.critical
        null -> c.meterTrack
    }
}

/** Icon + word, so the rating never depends on colour alone. */
@Composable
fun QualityBadge(q: Quality?, text: String = qualityLabel(q)) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        when (q) {
            Quality.EXCELLENT, Quality.GOOD ->
                Icon(Icons.Filled.Check, null, Modifier.size(13.dp), tint = c.goodIcon)
            Quality.FAIR -> Box(
                Modifier.size(11.dp).border(2.dp, c.warningIcon, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(5.dp).height(2.dp).background(c.warningIcon)) }
            Quality.POOR -> Icon(Icons.Filled.Warning, null, Modifier.size(13.dp), tint = c.criticalIcon)
            null -> {}
        }
        Text(text, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusPill(ok: Boolean, text: String) {
    val c = AppTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (ok) c.goodBg else c.criticalBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Warning, null, Modifier.size(14.dp),
            tint = if (ok) c.goodIcon else c.criticalIcon,
        )
        Text(text, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** "RSRP  −85 Good" with a thin bar underneath. */
@Composable
fun MetricMeter(label: String, value: String, quality: Quality?, fraction: Double?) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.textSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (quality != null) Text(qualityLabel(quality), color = c.text, fontSize = 12.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.meterTrack)) {
            if (fraction != null) {
                Box(
                    Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0.02f, 1f)).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(qualityMeterColor(quality))
                )
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(12.dp).height(2.dp).background(color))
        Text(text, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
    }
}

/**
 * Two-line SINR chart: fixed −5..15 dB scale with the "poor" zone (below 0) shaded.
 * Each list holds values in time order; nulls leave gaps.
 */
@Composable
fun SinrChart(lte: List<Double?>, nr: List<Double?>, description: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val minV = -5f
    val maxV = 15f
    Canvas(modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = description }) {
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        fun y(v: Double) = bottom - ((v.toFloat().coerceIn(minV, maxV) - minV) / (maxV - minV)) * (bottom - top)

        drawRect(
            c.critical.copy(alpha = 0.08f),
            topLeft = Offset(left, y(0.0)),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - y(0.0)),
        )
        listOf(15.0, 5.0, 0.0, -5.0).forEach { v ->
            drawLine(c.line, Offset(left, y(v)), Offset(right, y(v)), strokeWidth = 1.dp.toPx())
        }

        fun series(values: List<Double?>, color: Color) {
            if (values.isEmpty()) return
            val n = values.size
            val step = if (n > 1) (right - left) / (n - 1) else 0f
            var prev: Offset? = null
            values.forEachIndexed { i, v ->
                val pt = v?.let { Offset(left + step * i, y(it)) }
                if (pt != null && prev != null) {
                    drawLine(color, prev!!, pt, strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                prev = pt
            }
            values.lastOrNull()?.let {
                val end = Offset(left + step * (n - 1), y(it))
                drawCircle(c.surface, radius = 5.5.dp.toPx(), center = end)
                drawCircle(color, radius = 3.5.dp.toPx(), center = end)
            }
        }
        series(nr, c.series5g)
        series(lte, c.series4g)
    }
}

object BandNames {
    private val lte = mapOf(
        "1" to "2100 MHz", "3" to "1800 MHz", "7" to "2600 MHz", "8" to "900 MHz",
        "20" to "800 MHz", "28" to "700 MHz", "38" to "2600 MHz", "40" to "2300 MHz",
        "41" to "2500 MHz", "42" to "3500 MHz",
    )
    private val nr = mapOf(
        "1" to "2100 MHz", "3" to "1800 MHz", "7" to "2600 MHz", "8" to "900 MHz",
        "20" to "800 MHz", "28" to "700 MHz", "38" to "2600 MHz", "41" to "2.5 GHz",
        "77" to "3.7 GHz", "78" to "3.5 GHz",
    )

    fun lteLabel(band: String?) = band?.let { "B$it" } ?: "—"
    fun lteFreq(band: String?) = band?.let { lte[it] }
    fun nrLabel(band: String?) = band?.let { if (it.startsWith("n")) it else "n$it" } ?: "—"
    fun nrFreq(band: String?) = band?.removePrefix("n")?.let { nr[it] }
}

fun fmt(v: Double?, unit: String = ""): String {
    if (v == null) return "—"
    val s = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
    return s.replace('-', '−') + unit
}
