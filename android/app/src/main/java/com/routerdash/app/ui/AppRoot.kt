package com.routerdash.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.routerdash.app.data.DashState
import com.routerdash.app.data.DashboardViewModel
import com.routerdash.app.ui.nav.BottomBar
import com.routerdash.app.ui.nav.Tab
import com.routerdash.app.ui.screens.HomeScreen
import com.routerdash.app.ui.screens.PlaceholderScreen
import com.routerdash.app.ui.screens.SetupScreen
import com.routerdash.app.ui.theme.AppTheme

@Composable
fun AppRoot(vm: DashboardViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    when (val s = state) {
        DashState.Loading -> Box(Modifier.fillMaxSize().background(c.ground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DashState.NeedsSetup -> SetupScreen(s.host, s.error, connecting, vm::connect)
        is DashState.Live -> MainScaffold(s)
    }
}

@Composable
private fun MainScaffold(live: DashState.Live) {
    val c = AppTheme.colors
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { it.route == entry?.destination?.route } ?: Tab.HOME

    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = c.ground,
        bottomBar = { BottomBar(current, ::go) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding()).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    if (current == Tab.HOME) "MC888 Control" else current.label,
                    color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.fillMaxSize()) {
                composable(Tab.HOME.route) { HomeScreen(live, onTryBandLock = { go(Tab.BANDS) }) }
                composable(Tab.SIGNAL.route) {
                    PlaceholderScreen("Signal", listOf("Live readings table", "History chart (RSRP / RSRQ / SINR)", "Combined carriers", "Best-spot finder", "Export CSV"))
                }
                composable(Tab.BANDS.route) {
                    PlaceholderScreen("Band locking", listOf("4G and 5G band lock", "Network mode", "Lock to this tower", "Band tester", "Back to auto + auto-undo"))
                }
                composable(Tab.DATA.route) {
                    PlaceholderScreen("Data & SMS", listOf("Usage per day", "Bundle size and low-data alert", "Balance check", "SMS inbox"))
                }
                composable(Tab.MORE.route) {
                    PlaceholderScreen("More", listOf("Reliability: watchdog, outages, speed tests, scheduled reboot", "Devices & Wi-Fi", "Advanced", "Settings: theme, router address, password"))
                }
            }
        }
    }
}
