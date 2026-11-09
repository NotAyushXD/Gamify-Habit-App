package com.ayushpant.habitquest.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushpant.habitquest.BuildConfig
import com.ayushpant.habitquest.data.ReminderKind
import com.ayushpant.habitquest.detect.HabitListenerService
import com.ayushpant.habitquest.data.ReminderResult
import com.ayushpant.habitquest.data.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.ayushpant.habitquest.reminders.PopupPresenter
import com.ayushpant.habitquest.reminders.ReminderRepository
import com.ayushpant.habitquest.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/** Settings (spec 9, 11): permission status, quiet hours, checklist, reminder limit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(reminders: ReminderRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by reminders.observeSettings().collectAsStateWithLifecycle(initialValue = null)
    val log by reminders.observeRecentLog().collectAsStateWithLifecycle(initialValue = emptyList())
    var nextAlarm by remember { mutableStateOf<String?>(null) }

    // Permission status can change in the phone's settings, so read it again each time we come back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    LaunchedEffect(refresh, settings) {
        val at = java.time.Instant.ofEpochMilli(reminders.nextAlarmMillis()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        nextAlarm = at.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))
    }
    val overlay = remember(refresh) { ReminderScheduler.canDrawOverlays(context) }
    val notify = remember(refresh) { ReminderScheduler.canNotify(context) }
    val exact = remember(refresh) { ReminderScheduler.canUseExactAlarms(context) }
    val listener = remember(refresh) { HabitListenerService.isAllowed(context) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun save(change: (Settings) -> Settings) {
        val current = settings ?: return
        scope.launch { reminders.saveSettings(change(current)) }
    }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context, { _, h, m -> onPicked(LocalTime.of(h, m)) },
            initial.hour, initial.minute, DateFormat.is24HourFormat(context),
        ).show()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Permissions") {
                PermissionRow(
                    title = "Display over other apps",
                    why = "Shows reminder popups over the app you are using. Without it, reminders come as notifications.",
                    granted = overlay,
                    action = "Open setting",
                ) {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                    )
                }
                PermissionRow(
                    title = "Notifications",
                    why = "Needed for reminders when popups are off, and for the small \"Reminder on screen\" notice.",
                    granted = notify,
                    action = "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                PermissionRow(
                    title = "Notification access",
                    why = "Lets Habit Quest see when an app habit's app (e.g. Medito) plays audio, to suggest the session. " +
                        "It does not read your notifications. If the switch is greyed out: App info → ⋮ → Allow restricted settings.",
                    granted = listener,
                    action = "Open setting",
                ) {
                    context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                PermissionRow(
                    title = "Exact alarms",
                    why = "Reminders arrive on time instead of some minutes late.",
                    granted = exact,
                    action = "Open setting",
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                        )
                    }
                }
                OutlinedButton(onClick = { PopupPresenter.showTest(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show a test popup")
                }
            }

            val s = settings ?: return@Column
            Section("Quiet hours") {
                SwitchRow("No popups during quiet hours", s.quietEnabled) { on -> save { it.copy(quietEnabled = on) } }
                if (s.quietEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickTime(s.quietStart) { t -> save { it.copy(quietStart = t) } } }) {
                            Text("From ${s.quietStart.format(HHMM)}")
                        }
                        OutlinedButton(onClick = { pickTime(s.quietEnd) { t -> save { it.copy(quietEnd = t) } } }) {
                            Text("To ${s.quietEnd.format(HHMM)}")
                        }
                    }
                }
            }

            Section("Reminders") {
                SwitchRow("11 PM checklist", s.checklistEnabled) { on -> save { it.copy(checklistEnabled = on) } }
                Text("Ticks off anything you did but did not record. Shows only if something is still open.", style = MaterialTheme.typography.bodySmall)
                Text("Reminders per habit per day: ${s.maxReminders}", modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = s.maxReminders.toFloat(),
                    onValueChange = { v -> save { it.copy(maxReminders = v.roundToInt()) } },
                    valueRange = 1f..5f,
                    steps = 3,
                )
                Text("Window start, a snooze, and \"last chance\" 15 min before the end each count as one.", style = MaterialTheme.typography.bodySmall)
            }

            Section("Reminder check") {
                Text("Next reminder: ${nextAlarm ?: "…"}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "If a reminder did not come, this list shows what happened to it. " +
                        "\"Not shown\" means quiet hours, the daily limit, or the phone was off past its window.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (log.isEmpty()) Text("No reminders yet.", style = MaterialTheme.typography.bodyMedium)
                for (line in log) {
                    Row {
                        Text(line.dueAt.format(DateTimeFormatter.ofPattern("EEE HH:mm")), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.3f))
                        Text("${line.what} · ${kindText(line.kind)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.45f))
                        Text(resultText(line.result), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.25f))
                    }
                }
            }

            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun kindText(kind: ReminderKind) = when (kind) {
    ReminderKind.START -> "start"
    ReminderKind.LAST_CHANCE -> "last chance"
    ReminderKind.SNOOZE -> "snooze"
    ReminderKind.CHECKLIST -> "11 PM"
    ReminderKind.PROMISE -> "promise"
}

private fun resultText(result: ReminderResult) = when (result) {
    ReminderResult.PENDING -> "waiting"
    ReminderResult.SHOWN -> "shown"
    ReminderResult.STARTED -> "started ✓"
    ReminderResult.DONE -> "done ✓"
    ReminderResult.SNOOZED -> "snoozed"
    ReminderResult.SKIPPED -> "skipped"
    ReminderResult.PROMISED -> "promised 🤝"
    ReminderResult.CLOSED -> "closed"
    ReminderResult.SUPPRESSED -> "not shown"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((if (granted) "✓ " else "✗ ") + title, style = MaterialTheme.typography.bodyLarge)
            Text(why, style = MaterialTheme.typography.bodySmall)
        }
        if (!granted) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
