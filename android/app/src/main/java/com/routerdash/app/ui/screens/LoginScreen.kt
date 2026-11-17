package com.routerdash.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.data.RouterProbe
import com.routerdash.app.ui.components.AppIcons
import com.routerdash.app.ui.theme.AppTheme

@Composable
fun LoginScreen(
    initialHost: String,
    error: String?,
    connecting: Boolean,
    probe: RouterProbe,
    isDark: Boolean,
    onProbe: (String) -> Unit,
    onConnect: (host: String, password: String) -> Unit,
    onToggleTheme: () -> Unit,
) {
    val c = AppTheme.colors
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var editingHost by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val canConnect = !connecting && password.isNotEmpty() && probe !is RouterProbe.Checking

    Column(
        Modifier.fillMaxSize().background(c.ground).safeDrawingPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    if (isDark) AppIcons.Sun else AppIcons.Moon,
                    contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                    Modifier.size(22.dp), tint = c.text,
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.tipBg)
                        .border(1.dp, c.tipLine, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.SignalBars, null, Modifier.size(34.dp), tint = c.series4g) }
                Text("Connect your router", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sign in with the router's admin password to see your signal and control bands.",
                    color = c.textSecondary, fontSize = 15.sp,
                )
            }

            // Router found / not found
            RouterStatus(probe, onChange = { editingHost = !editingHost }, onRetry = { onProbe(host) })
            AnimatedVisibility(editingHost) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host, onValueChange = { host = it },
                        label = { Text("Router address") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onProbe(host); editingHost = false }),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onProbe(host); editingHost = false }) { Text("Find") }
                }
            }

            // Password
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Admin password") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canConnect) onConnect(host, password) }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) AppIcons.EyeOff else AppIcons.Eye,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                Modifier.size(22.dp), tint = c.textSecondary,
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.series4g, unfocusedBorderColor = c.line,
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Where do I find it?",
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { showHelp = !showHelp }.padding(vertical = 6.dp),
                    color = c.link, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
                AnimatedVisibility(showHelp) {
                    Text(
                        "On the sticker under the router, next to \"Admin password\" or \"Web password\". It's different from the Wi-Fi password.",
                        color = c.textSecondary, fontSize = 13.sp,
                    )
                }
            }

            if (error != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.criticalBg).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = c.criticalIcon)
                    Text(error, color = c.text, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Bottom: tries left, Connect, privacy note
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val left = (probe as? RouterProbe.Found)?.info?.attemptsLeft
                if (left != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, null, Modifier.size(16.dp), tint = if (left <= 2) c.criticalIcon else c.warningIcon)
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = c.text, fontWeight = FontWeight.SemiBold)) {
                                    append(if (left == 1) "1 try left" else "$left tries left")
                                }
                                append(" before the router locks sign-in")
                            },
                            color = c.textSecondary, fontSize = 13.sp,
                        )
                    }
                }
                Button(
                    onClick = { onConnect(host, password) },
                    enabled = canConnect,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (connecting) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(13.dp), tint = c.textMuted)
                    Text("  Saved encrypted on this phone only", color = c.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RouterStatus(probe: RouterProbe, onChange: () -> Unit, onRetry: () -> Unit) {
    val c = AppTheme.colors
    val (bg, title, detail) = when (probe) {
        RouterProbe.Checking -> Triple(c.surface, "Looking for your router…", "Make sure this phone is on the router's Wi-Fi")
        is RouterProbe.Found -> Triple(
            c.goodBg, "Router found",
            listOfNotNull(
                "ZTE ${probe.info.model ?: "router"} at ${probe.host}",
                probe.info.firmware?.let { fw -> Regex("B\\d+$").find(fw)?.value?.let { "firmware $it" } },
            ).joinToString(" · "),
        )
        is RouterProbe.NotFound -> Triple(c.criticalBg, "No router at ${probe.host}", "Connect to the router's Wi-Fi, or change the address")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
            .border(1.dp, if (probe is RouterProbe.Checking) c.line else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(
                when (probe) {
                    is RouterProbe.Found -> c.good.copy(alpha = 0.18f)
                    is RouterProbe.NotFound -> c.critical.copy(alpha = 0.18f)
                    RouterProbe.Checking -> Color.Transparent
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (probe) {
                RouterProbe.Checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                is RouterProbe.Found -> Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = c.goodIcon)
                is RouterProbe.NotFound -> Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = c.criticalIcon)
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold)
            Text(detail, color = c.textSecondary, fontSize = 13.sp)
        }
        if (probe is RouterProbe.NotFound) TextButton(onClick = onRetry) { Text("Retry", color = c.link) }
        TextButton(onClick = onChange) { Text("Change", color = c.link, fontWeight = FontWeight.SemiBold) }
    }
}
