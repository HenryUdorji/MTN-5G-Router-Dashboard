package com.routerdash.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.ui.theme.AppTheme

/** First run (or after the router rejects the saved password): router address + admin password. */
@Composable
fun SetupScreen(initialHost: String, error: String?, connecting: Boolean, onConnect: (String, String) -> Unit) {
    val c = AppTheme.colors
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("MC888 Control", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Connect your phone to the router's Wi-Fi, then enter the admin password from the sticker under the router.",
            color = c.textSecondary, fontSize = 15.sp,
        )
        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("Router address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Admin password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                error, color = c.text, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().background(c.criticalBg, RoundedCornerShape(10.dp)).padding(12.dp),
            )
        }
        Button(
            onClick = { onConnect(host, password) },
            enabled = !connecting && host.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "The router locks the admin login after about 5 wrong tries, so check the password before tapping Connect. " +
                "It's stored encrypted on this phone only.",
            color = c.textMuted, fontSize = 13.sp,
        )
    }
}
