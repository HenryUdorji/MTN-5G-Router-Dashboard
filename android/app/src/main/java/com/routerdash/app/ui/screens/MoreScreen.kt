package com.routerdash.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.data.ThemeMode
import com.routerdash.app.ui.components.AppCard
import com.routerdash.app.ui.components.CardTitle
import com.routerdash.app.ui.components.Overline
import com.routerdash.app.ui.components.Segmented
import com.routerdash.app.ui.theme.AppTheme

@Composable
fun MoreScreen(host: String, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit, onSignOut: () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("Appearance")
            AppCard {
                Text("Theme", color = c.textSecondary, fontSize = 13.sp)
                val modes = ThemeMode.entries
                Segmented(
                    listOf("System", "Light", "Dark"), modes.indexOf(themeMode),
                    { onThemeMode(modes[it]) }, Modifier.fillMaxWidth(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("Router connection")
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Router address", color = c.text, fontSize = 14.sp)
                    Text(host, color = c.textMuted, fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Refresh every", color = c.text, fontSize = 14.sp)
                    Text("3 seconds", color = c.textMuted, fontSize = 14.sp)
                }
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out and forget password")
                }
            }
        }

        AppCard {
            CardTitle("Coming next here")
            listOf(
                "Reliability: watchdog, outages, speed tests, scheduled reboot",
                "Devices & Wi-Fi",
                "Advanced: bridge mode, TTL",
            ).forEach { Text("• $it", color = c.textSecondary, fontSize = 14.sp) }
        }
    }
}
