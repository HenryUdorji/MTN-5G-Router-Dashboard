package com.routerdash.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerdash.app.data.BandAction
import com.routerdash.app.data.DashState
import com.routerdash.app.ui.components.AppCard
import com.routerdash.app.ui.components.CardTitle
import com.routerdash.app.ui.components.Overline
import com.routerdash.app.ui.components.fmt
import com.routerdash.app.ui.theme.AppTheme
import com.routerdash.router.SignalSnapshot
import com.routerdash.router.zte.ZteMc888Driver

private val ALL_LTE = ZteMc888Driver.SUPPORTED_LTE_BANDS
private val ALL_NR = ZteMc888Driver.SUPPORTED_NR_BANDS

@Composable
fun BandsScreen(
    state: DashState.Live,
    action: BandAction,
    onApply: (lte: Set<Int>, nr: Set<String>, autoUndo: Boolean) -> Unit,
    onBackToAuto: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val snap = state.snapshot ?: return HomeScreen(state, onTryBandLock = {}) // connecting spinner
    val c = AppTheme.colors
    val busy = action is BandAction.Applying || action is BandAction.WaitingForInternet

    val currentLte = snap.lteBandsAllowed.ifEmpty { ALL_LTE }.toSet()
    val currentNr = snap.nrBandsAllowed.ifEmpty { ALL_NR }.toSet()
    // Re-start the edit whenever the router's actual settings change.
    var lte by remember(currentLte) { mutableStateOf(currentLte) }
    var nr by remember(currentNr) { mutableStateOf(currentNr) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val changed = lte != currentLte || nr != currentNr
    val isAuto = currentLte == ALL_LTE.toSet() && currentNr == ALL_NR.toSet()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (action != BandAction.Idle) ActionCard(action, snap, onDismissResult)

        CurrentCard(snap, isAuto, busy, onBackToAuto)

        BandCard(
            title = "4G bands",
            options = ALL_LTE.map { it.toString() },
            label = { "B$it" },
            selected = lte.map { it.toString() }.toSet(),
            changed = lte != currentLte,
            seen = listOfNotNull(snap.lte?.band) + snap.secondaryCarriers.map { it.band },
            current = snap.lte?.band,
            enabled = !busy,
            onToggle = { b -> lte = lte.toggle(b.toInt()) },
        )
        BandCard(
            title = "5G bands",
            options = ALL_NR,
            label = { "n$it" },
            selected = nr,
            changed = nr != currentNr,
            seen = listOfNotNull(snap.nr?.band?.removePrefix("n")),
            current = snap.nr?.band?.removePrefix("n"),
            enabled = !busy,
            onToggle = { b -> nr = nr.toggle(b) },
        )

        if (changed) {
            val empty = lte.isEmpty() || nr.isEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { lte = currentLte; nr = currentNr },
                    enabled = !busy, modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Cancel") }
                Button(
                    onClick = { confirming = true },
                    enabled = !busy && !empty, modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Apply lock", fontWeight = FontWeight.SemiBold) }
            }
            if (empty) Text("Pick at least one 4G and one 5G band.", color = c.criticalIcon, fontSize = 13.sp)
        }

        AppCard {
            CardTitle("Coming next on this screen")
            listOf("Network mode (5G + 4G / 4G only)", "Lock to this tower", "Band tester")
                .forEach { Text("• $it", color = c.textSecondary, fontSize = 14.sp) }
        }
        Text(
            "Locks can drop the connection for a few seconds. Back to auto is always one tap away.",
            color = c.textMuted, fontSize = 12.sp,
        )
        Box(Modifier.height(16.dp))
    }

    if (confirming) {
        ConfirmDialog(
            snap = snap,
            fromLte = currentLte, toLte = lte,
            fromNr = currentNr, toNr = nr,
            onConfirm = { autoUndo -> confirming = false; onApply(lte, nr, autoUndo) },
            onDismiss = { confirming = false },
        )
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@Composable
private fun CurrentCard(snap: SignalSnapshot, isAuto: Boolean, busy: Boolean, onBackToAuto: () -> Unit) {
    val c = AppTheme.colors
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline("Right now")
                Text(
                    if (isAuto) "Auto · all bands allowed"
                    else "Locked: 4G ${bandList(snap.lteBandsAllowed.map { "B$it" })} · 5G ${bandList(snap.nrBandsAllowed.map { "n$it" })}",
                    color = c.text, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "On B${snap.lte?.band ?: "—"}${snap.nr?.band?.let { " + n${it.removePrefix("n")}" } ?: ""}",
                    color = c.textMuted, fontSize = 12.sp,
                )
            }
            OutlinedButton(onClick = onBackToAuto, enabled = !isAuto && !busy) { Text("Back to auto") }
        }
    }
}

private fun bandList(names: List<String>) = if (names.size > 4) "${names.take(4).joinToString(", ")} +${names.size - 4}" else names.joinToString(", ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandCard(
    title: String,
    options: List<String>,
    label: (String) -> String,
    selected: Set<String>,
    changed: Boolean,
    seen: List<String>,
    current: String?,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.dp, if (changed) c.series4g else c.line, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle(title)
            Text(
                if (changed) "${selected.size} of ${options.size} · not applied"
                else if (selected.size == options.size) "All ${options.size} allowed" else "${selected.size} of ${options.size} allowed",
                color = if (changed) c.link else c.textMuted, fontSize = 12.sp,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { b ->
                val on = b in selected
                Box(
                    Modifier
                        .widthIn(min = 58.dp).heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) (if (c.isDark) Color(0xFF1D2A3D) else c.tipBg) else Color.Transparent)
                        .border(1.dp, if (on) c.series4g else c.line, RoundedCornerShape(10.dp))
                        .toggleable(value = on, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(b) })
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (on) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = c.link)
                        Text(
                            " " + label(b) + if (b == current) " ·" else "",
                            color = if (on) c.text else c.textSecondary,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        val seenText = seen.distinct().joinToString(", ") { label(it) + if (it == current) " (current)" else "" }
        if (seenText.isNotEmpty()) Text("Seen at your location: $seenText", color = c.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ActionCard(action: BandAction, now: SignalSnapshot, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val (bg, icon) = when (action) {
        is BandAction.Done -> c.goodBg to "ok"
        is BandAction.Undone, is BandAction.Failed -> c.criticalBg to "bad"
        else -> c.tipBg to "wait"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (icon) {
                "ok" -> Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = c.goodIcon)
                "bad" -> Icon(Icons.Filled.Warning, null, Modifier.size(20.dp), tint = c.criticalIcon)
                else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(
                when (action) {
                    BandAction.Applying -> "Sending the new bands to the router…"
                    is BandAction.WaitingForInternet ->
                        "Waiting for the internet to come back… ${action.seconds} s" +
                            (action.undoAt?.let { " · auto-undo at $it s" } ?: "")
                    is BandAction.Done -> action.message
                    is BandAction.Undone -> action.message
                    is BandAction.Failed -> action.message
                    BandAction.Idle -> ""
                },
                Modifier.weight(1f), color = c.text, fontSize = 14.sp,
            )
        }
        if (action is BandAction.Done && action.before != null) {
            val b = action.before
            Text(
                "Before: 4G B${b.lte?.band ?: "—"} SINR ${fmt(b.lte?.sinr)} dB · 5G SINR ${fmt(b.nr?.sinr)} dB\n" +
                    "Now:    4G B${now.lte?.band ?: "—"} SINR ${fmt(now.lte?.sinr)} dB · 5G SINR ${fmt(now.nr?.sinr)} dB",
                color = c.textSecondary, fontSize = 13.sp,
            )
            Text("Give it a minute: the numbers settle after the switch.", color = c.textMuted, fontSize = 12.sp)
        }
        if (action is BandAction.Done || action is BandAction.Undone || action is BandAction.Failed) {
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("OK", color = c.link) }
        }
    }
}

@Composable
private fun ConfirmDialog(
    snap: SignalSnapshot,
    fromLte: Set<Int>, toLte: Set<Int>,
    fromNr: Set<String>, toNr: Set<String>,
    onConfirm: (autoUndo: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    var autoUndo by rememberSaveable { mutableStateOf(true) }
    val lteChanged = fromLte != toLte
    val nrChanged = fromNr != toNr
    val leavingLte = snap.lte?.band?.toIntOrNull()?.let { it !in toLte } == true
    val leavingNr = snap.nr?.band?.removePrefix("n")?.let { it !in toNr } == true
    val title = buildString {
        append(if (toLte == ALL_LTE.toSet() && toNr == ALL_NR.toSet()) "Allow all bands?" else "Lock ")
        if (!(toLte == ALL_LTE.toSet() && toNr == ALL_NR.toSet())) {
            val parts = listOfNotNull(
                if (lteChanged) "4G to ${toLte.sorted().joinToString(" + ") { "B$it" }}" else null,
                if (nrChanged) "5G to ${toNr.sortedBy { it.toIntOrNull() ?: 0 }.joinToString(" + ") { "n$it" }}" else null,
            )
            append(parts.joinToString(", ")).append("?")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        icon = { Icon(Icons.Filled.Lock, null, tint = c.link) },
        title = { Text(title, color = c.text, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val leaving = listOfNotNull(
                    if (leavingLte) "Band ${snap.lte?.band}, your current 4G band" else null,
                    if (leavingNr) "n${snap.nr?.band?.removePrefix("n")}, your current 5G band" else null,
                )
                Text(
                    (if (leaving.isNotEmpty()) "You'll leave ${leaving.joinToString(" and ")}. " else "") +
                        "The internet drops for about 10–30 seconds while the router switches.",
                    color = c.textSecondary,
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SummaryRow("4G", if (lteChanged) "${describe(fromLte.map { "B$it" }, ALL_LTE.size)} → ${toLte.sorted().joinToString(", ") { "B$it" }}" else "Unchanged")
                    SummaryRow("5G", if (nrChanged) "${describe(fromNr.map { "n$it" }, ALL_NR.size)} → ${toNr.sortedBy { it.toIntOrNull() ?: 0 }.joinToString(", ") { "n$it" }}" else "Unchanged")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Undo automatically", color = c.text, fontWeight = FontWeight.SemiBold)
                        Text("If the internet isn't back within 2 minutes, put the previous bands back", color = c.textMuted, fontSize = 12.sp)
                    }
                    Switch(checked = autoUndo, onCheckedChange = { autoUndo = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(autoUndo) }) { Text("Lock bands", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.link) } },
    )
}

private fun describe(names: List<String>, total: Int) = if (names.size == total) "Auto, $total bands" else names.joinToString(", ")

@Composable
private fun SummaryRow(label: String, text: String) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.widthIn(min = 32.dp), color = c.textMuted, fontSize = 13.sp)
        Text(text, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
