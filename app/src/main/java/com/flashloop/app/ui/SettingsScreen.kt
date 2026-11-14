package com.flashloop.app.ui

import android.Manifest
import android.app.Application
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.data.AppSettings
import com.flashloop.app.data.Deck
import com.flashloop.app.data.FreqMode
import com.flashloop.app.data.Keys
import com.flashloop.app.data.fmtTime
import com.flashloop.app.scheduling.Scheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as FlashLoopApp

    val settings: StateFlow<AppSettings> =
        a.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val decks: StateFlow<List<Deck>> =
        a.repository.decks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Save a change, then recompute the alarm so it always matches the new settings. */
    fun update(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch {
            a.settings.edit(block)
            Scheduler.reschedule(a)
        }
    }
}

private fun pickTime(context: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        initial / 60,
        initial % 60,
        DateFormat.is24HourFormat(context)
    ).show()
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val canOverlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var wantOverlay by remember { mutableStateOf(false) }

    // When the user comes back from Android's overlay-permission screen having granted it, switch it on.
    LaunchedEffect(tick) {
        if (wantOverlay && Settings.canDrawOverlays(ctx)) {
            vm.update { it[Keys.OVERLAY] = true }
            wantOverlay = false
        }
    }

    val paused = s.pausedUntil > System.currentTimeMillis()
    var custom by remember(s.freqCount) { mutableStateOf(s.freqCount.toString()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Automatic interruptions") {
            SwitchRow("Enable automatic flashcards", s.enabled) { v -> vm.update { it[Keys.ENABLED] = v } }
            SwitchRow("Pause until I resume", paused && s.pausedUntil == Long.MAX_VALUE) { v ->
                vm.update { it[Keys.PAUSED_UNTIL] = if (v) Long.MAX_VALUE else 0L }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    vm.update { it[Keys.PAUSED_UNTIL] = System.currentTimeMillis() + 60L * 60_000L }
                }) { Text("Pause 1 hour") }
                OutlinedButton(onClick = {
                    vm.update { it[Keys.PAUSED_UNTIL] = System.currentTimeMillis() + 24L * 60L * 60_000L }
                }) { Text("Pause 1 day") }
            }
            if (paused && s.pausedUntil != Long.MAX_VALUE) {
                Text(
                    "Paused until ${DateFormat.getTimeFormat(ctx).format(Date(s.pausedUntil))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Section("How often") {
            ChipRow {
                FreqMode.values().forEach { m ->
                    FilterChip(
                        selected = s.freqMode == m,
                        onClick = { vm.update { it[Keys.FREQ_MODE] = m.name } },
                        label = { Text(m.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                    )
                }
            }
            val unit = when (s.freqMode) {
                FreqMode.DAILY -> "per day"
                FreqMode.WEEKLY -> "per week"
                FreqMode.MONTHLY -> "per month"
            }
            ChipRow {
                listOf(1, 2, 3, 5, 10).forEach { n ->
                    FilterChip(
                        selected = s.freqCount == n,
                        onClick = { vm.update { it[Keys.FREQ_COUNT] = n } },
                        label = { Text("$n") }
                    )
                }
            }
            OutlinedTextField(
                value = custom,
                onValueChange = { raw ->
                    val digits = raw.filter { ch -> ch.isDigit() }.take(3)
                    custom = digits
                    val n = digits.toIntOrNull()
                    if (n != null && n in 1..500) vm.update { it[Keys.FREQ_COUNT] = n }
                },
                label = { Text("Custom number of interruptions $unit") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "This is the number of interruptions, not the number of different cards. " +
                    "If nothing is due, nothing is shown.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Section("Active hours") {
            Text("Interruptions only happen between these times.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pickTime(ctx, s.activeStartMin) { m -> vm.update { it[Keys.ACTIVE_START] = m } }
                }) { Text("From ${fmtTime(s.activeStartMin)}") }
                OutlinedButton(onClick = {
                    pickTime(ctx, s.activeEndMin) { m -> vm.update { it[Keys.ACTIVE_END] = m } }
                }) { Text("To ${fmtTime(s.activeEndMin)}") }
            }
        }

        Section("Quiet hours") {
            SwitchRow("Enable quiet hours", s.quietEnabled) { v -> vm.update { it[Keys.QUIET_ENABLED] = v } }
            if (s.quietEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickTime(ctx, s.quietStartMin) { m -> vm.update { it[Keys.QUIET_START] = m } }
                    }) { Text("From ${fmtTime(s.quietStartMin)}") }
                    OutlinedButton(onClick = {
                        pickTime(ctx, s.quietEndMin) { m -> vm.update { it[Keys.QUIET_END] = m } }
                    }) { Text("To ${fmtTime(s.quietEndMin)}") }
                }
            }
        }

        Section("Maximum per hour") {
            ChipRow {
                listOf(0, 1, 2, 3, 5).forEach { n ->
                    FilterChip(
                        selected = s.maxPerHour == n,
                        onClick = { vm.update { it[Keys.MAX_PER_HOUR] = n } },
                        label = { Text(if (n == 0) "No limit" else "$n") }
                    )
                }
            }
        }

        Section("Floating flashcard (overlay)") {
            SwitchRow("Show flashcards over other apps", s.overlayEnabled && canOverlay) { want ->
                if (want && !canOverlay) showOverlayDialog = true
                else vm.update { it[Keys.OVERLAY] = want }
            }
            Text(
                if (canOverlay) "Permission: granted" else "Permission: not granted (notifications are used instead)",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Android may still block the overlay on some phones or while the screen is locked. " +
                    "When that happens FlashLoop falls back to a notification.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Section("Notifications") {
            Text(
                if (notifOk) "Permission: granted" else "Permission: NOT granted. Without it, automatic interruptions may not work when the overlay is unavailable.",
                style = MaterialTheme.typography.bodySmall
            )
            SwitchRow("Use notifications as fallback", s.notificationsEnabled) { v ->
                vm.update { it[Keys.NOTIFICATIONS] = v }
            }
            if (!notifOk) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Allow")
                        }
                    }
                    OutlinedButton(onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            )
                        }
                    }) { Text("Notification settings") }
                }
            }
        }

        Section("Snooze (notification button)") {
            ChipRow {
                listOf(5, 15).forEach { n ->
                    FilterChip(
                        selected = s.snoozeMinutes == n,
                        onClick = { vm.update { it[Keys.SNOOZE_MIN] = n } },
                        label = { Text("$n min") }
                    )
                }
            }
            Text("The floating card always offers both 5 and 15 minutes.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Only use this deck") {
            ChipRow {
                FilterChip(
                    selected = s.deckFilterId == 0L,
                    onClick = { vm.update { it[Keys.DECK_FILTER] = 0L } },
                    label = { Text("All decks") }
                )
                decks.forEach { d ->
                    FilterChip(
                        selected = s.deckFilterId == d.id,
                        onClick = { vm.update { it[Keys.DECK_FILTER] = d.id } },
                        label = { Text(d.name) }
                    )
                }
            }
        }

        Section("Reliability") {
            Text(
                "Android can delay background alarms, and some phone brands (Xiaomi, Oppo, Vivo, Huawei, " +
                    "Samsung…) stop apps aggressively. If interruptions are late or missing, exclude " +
                    "FlashLoop from battery optimisation.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }) { Text("Open battery settings") }
        }
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Allow floating flashcards") },
            text = {
                Text(
                    "FlashLoop needs the \"Display over other apps\" permission to show a flashcard on top " +
                        "of whatever you're doing. It is only used to show your own flashcards at the times " +
                        "you choose, and you can take the permission back any time in Android settings. " +
                        "Without it, FlashLoop uses notifications instead."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    wantOverlay = true
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showOverlayDialog = false }) { Text("Not now") } }
        )
    }
}
