package com.routerdash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routerdash.app.data.DashboardViewModel
import com.routerdash.app.ui.AppRoot
import com.routerdash.app.ui.theme.RouterDashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RouterDashTheme {
                val vm: DashboardViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { DashboardViewModel((this[APPLICATION_KEY] as RouterDashApp).settings) }
                    }
                )
                AppRoot(vm)
            }
        }
    }
}
