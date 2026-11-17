package com.routerdash.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.ui.components.AppCard
import com.routerdash.app.ui.components.CardTitle
import com.routerdash.app.ui.theme.AppTheme

/** Stand-in for screens that come in later milestones. */
@Composable
fun PlaceholderScreen(title: String, planned: List<String>) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            CardTitle("$title is coming next")
            planned.forEach { Text("• $it", color = c.textSecondary, fontSize = 14.sp) }
        }
    }
}
