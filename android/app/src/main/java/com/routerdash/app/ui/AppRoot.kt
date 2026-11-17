package com.routerdash.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.routerdash.app.data.ThemeMode
import com.routerdash.app.ui.components.AppIcons
import com.routerdash.app.ui.nav.BottomBar
import com.routerdash.app.ui.nav.Tab
import com.routerdash.app.ui.screens.HomeScreen
import com.routerdash.app.ui.screens.MoreScreen
import com.routerdash.app.ui.screens.PlaceholderScreen
import com.routerdash.app.ui.screens.LoginScreen
import com.routerdash.app.ui.screens.SignalScreen
import com.routerdash.app.ui.theme.AppTheme

@Composable
fun AppRoot(vm: DashboardViewModel, isDark: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val probe by vm.probe.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val toggleTheme = { vm.setThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK) }

    when (val s = state) {
        DashState.Loading -> Box(Modifier.fillMaxSize().background(c.ground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DashState.NeedsSetup -> {
            LaunchedEffect(s.host) { vm.probe(s.host) }
            LoginScreen(
                initialHost = s.host,
                error = s.error,
                connecting = connecting,
                probe = probe,
                isDark = isDark,
                onProbe = vm::probe,
                onConnect = vm::connect,
                onToggleTheme = toggleTheme,
            )
        }
        is DashState.Live -> MainScaffold(
            live = s,
            isDark = isDark,
            themeMode = themeMode,
            onThemeMode = vm::setThemeMode,
            onSignOut = vm::signOut,
        )
    }
}

@Composable
private fun MainScaffold(
    live: DashState.Live,
    isDark: Boolean,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
) {
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
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (current == Tab.HOME) "MC888 Control" else current.label,
                    Modifier.weight(1f),
                    color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                )
                if (current == Tab.HOME) {
                    // Quick switch: flips to the opposite of what's showing now (saved as Light or Dark).
                    IconButton(onClick = { onThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK) }) {
                        Icon(
                            if (isDark) AppIcons.Sun else AppIcons.Moon,
                            contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                            Modifier.size(22.dp), tint = c.text,
                        )
                    }
                }
            }
            NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.fillMaxSize()) {
                composable(Tab.HOME.route) { HomeScreen(live, onTryBandLock = { go(Tab.BANDS) }) }
                composable(Tab.SIGNAL.route) { SignalScreen(live) }
                composable(Tab.BANDS.route) {
                    PlaceholderScreen("Band locking", listOf("4G and 5G band lock", "Network mode", "Lock to this tower", "Band tester", "Back to auto + auto-undo"))
                }
                composable(Tab.DATA.route) {
                    PlaceholderScreen("Data & SMS", listOf("Usage per day", "Bundle size and low-data alert", "Balance check", "SMS inbox"))
                }
                composable(Tab.MORE.route) { MoreScreen(live.host, themeMode, onThemeMode, onSignOut) }
            }
        }
    }
}
