package com.routerdash.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.ui.theme.AppTheme

enum class Tab(val route: String, val label: String) {
    HOME("home", "Home"),
    SIGNAL("signal", "Signal"),
    BANDS("bands", "Bands"),
    DATA("data", "Data"),
    MORE("more", "More"),
}

/** Bottom bar "B" from the mockup: labels on every tab, a short line above the active one. */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.background(c.navBar).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(color = c.line, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp)) {
            Tab.entries.forEach { tab ->
                val on = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .height(72.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(32.dp).height(3.dp)
                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(if (on) c.series4g else Color.Transparent)
                    )
                    Spacer(Modifier.height(13.dp))
                    Icon(
                        tab.icon(), contentDescription = null, Modifier.size(22.dp),
                        tint = if (on) c.link else c.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label, fontSize = 12.sp,
                        color = if (on) c.text else c.textSecondary,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.HOME -> Icons.Filled.Home
    Tab.SIGNAL -> SignalBars
    Tab.BANDS -> Icons.Filled.Lock
    Tab.DATA -> DownloadArrow
    Tab.MORE -> Icons.Filled.MoreVert
}

private val SignalBars: ImageVector by lazy {
    ImageVector.Builder("SignalBars", 24.dp, 24.dp, 24f, 24f).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(5f, 20f); verticalLineTo(17f)
        moveTo(10f, 20f); verticalLineTo(13f)
        moveTo(15f, 20f); verticalLineTo(9f)
        moveTo(20f, 20f); verticalLineTo(4f)
    }.build()
}

private val DownloadArrow: ImageVector by lazy {
    ImageVector.Builder("DownloadArrow", 24.dp, 24.dp, 24f, 24f).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(12f, 4f); verticalLineTo(16f)
        moveTo(7f, 11f); lineTo(12f, 16f); lineTo(17f, 11f)
        moveTo(5f, 20f); horizontalLineTo(19f)
    }.build()
}
