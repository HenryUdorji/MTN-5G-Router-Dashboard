package com.routerdash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routerdash.app.data.DashboardViewModel
import com.routerdash.app.data.ThemeMode
import com.routerdash.app.ui.AppRoot
import com.routerdash.app.ui.theme.RouterDashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: DashboardViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { DashboardViewModel((this[APPLICATION_KEY] as RouterDashApp).settings) }
                }
            )
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            RouterDashTheme(dark = dark) {
                AppRoot(vm, isDark = dark)
            }
        }
    }
}
