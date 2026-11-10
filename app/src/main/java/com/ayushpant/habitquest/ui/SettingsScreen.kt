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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CardDefaults
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
import com.ayushpant.habitquest.data.HabitType
import com.ayushpant.habitquest.detect.CatchUp
import com.ayushpant.habitquest.detect.Geofences
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
fun SettingsScreen(
    reminders: ReminderRepository,
    onLinkHabit: (habitId: Long, type: HabitType) -> Unit = { _, _ -> },
    onZones: () -> Unit = {},
    /** A permission may have changed: register zones and alarms again. */
    onPermissionsChanged: () -> Unit = {},
    onShowWelcome: () -> Unit = {},
    onGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by reminders.observeSettings().collectAsStateWithLifecycle(initialValue = null)
    val log by reminders.observeRecentLog().collectAsStateWithLifecycle(initialValue = emptyList())
    var nextAlarm by remember { mutableStateOf<String?>(null) }

    // Permission status can change in the phone's settings, so read it again each time we come back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPermissionsChanged() // the user may come back from a phone setting
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
    val usage = remember(refresh) { CatchUp.hasUsageAccess(context) }
    val locationNow = remember(refresh) { Geofences.hasForegroundLocation(context) }
    val locationAlways = remember(refresh) { Geofences.hasBackgroundLocation(context) }
    // After a location answer: register the zones again (they need "all the time").
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++; onPermissionsChanged() }
    // Android 11+ opens the location page of the app's settings, where "Allow all the time" is.
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; onPermissionsChanged() }
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
            // The guide first: what every feature does and how to use it.
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onGuide),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📘", fontSize = 30.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("How to use Habit Quest", style = MaterialTheme.typography.titleMedium)
                        Text("Every feature in a few simple steps", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", fontSize = 28.sp)
                }
            }

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
                    why = "Lets Habit Quest see when an app habit's app (e.g. your meditation app) plays audio, to suggest the session. " +
                        "It does not read your notifications. If the switch is greyed out: App info → ⋮ → Allow restricted settings.",
                    granted = listener,
                    action = "Open setting",
                ) {
                    openNotificationAccess(context)
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

            Section("Track a habit with an app") {
                AppHabitGuide(reminders, notificationAccess = listener, popups = overlay, usageAccess = usage, onLinkHabit = { onLinkHabit(it, HabitType.APP) })
            }

            Section("Track a habit at a place") {
                ZoneHabitGuide(reminders, foreground = locationNow, background = locationAlways, onZones = onZones,
                    onLinkHabit = { onLinkHabit(it, HabitType.ZONE) }, askForeground = {
                        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }, askBackground = { askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) })
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

            OutlinedButton(onClick = onShowWelcome, modifier = Modifier.fillMaxWidth()) { Text("Show the welcome tour again") }
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Step-by-step set-up for app habits, with a live ✓ for each step. Works with any app that plays
 * audio: Medito, Headspace, Calm, Insight Timer, a podcast or music app, …
 */
@Composable
private fun AppHabitGuide(
    reminders: ReminderRepository,
    notificationAccess: Boolean,
    popups: Boolean,
    usageAccess: Boolean,
    onLinkHabit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val appHabits by reminders.observeAppHabits().collectAsStateWithLifecycle(initialValue = emptyList())
    val allHabits by reminders.observeActiveHabits().collectAsStateWithLifecycle(initialValue = emptyList())
    var choosing by remember { mutableStateOf(false) }
    Text(
        "Habit Quest can notice when you use an app for a habit, for example a meditation app, " +
            "and ask you to confirm the session. Nothing is checked off without your tap.",
        style = MaterialTheme.typography.bodyMedium,
    )
    GuideStep(1, notificationAccess, "Allow Notification access",
        "It lets Habit Quest see when an app starts and stops playing audio. It does not read your notifications.") {
        if (!notificationAccess) {
            GuideButton("Turn it on") { openNotificationAccess(context) }
            GuideButton("Switch greyed out?") { openAppInfo(context) }
        }
    }
    GuideStep(2, appHabits.isNotEmpty(), "Link the habit to its app",
        if (appHabits.isEmpty()) "Choose the habit, then pick the app it uses."
        else appHabits.joinToString("\n") { "${it.name} → ${appLabel(context, it.sourcePackage!!) ?: it.sourcePackage}" }) {
        GuideButton(if (appHabits.isEmpty()) "Link a habit" else "Link another") { choosing = true }
    }
    GuideStep(3, popups, "Allow Display over other apps",
        "So the \"Did you do it?\" question can appear over the app. Without it, it comes as a notification.") {
        if (!popups) GuideButton("Turn it on") { openOverlaySetting(context) }
    }
    GuideStep(4, usageAccess, "Allow Usage access (for apps opened without audio)",
        "Every hour Habit Quest looks at when your linked apps were on screen, to catch sessions the audio did not show " +
            "(a silent timer, or the phone had just restarted). It asks you to confirm each one.") {
        if (!usageAccess) GuideButton("Turn it on") { openUsageAccess(context) }
    }
    GuideStep(5, null, "Use the app as usual",
        "Start it from the habit's tile or open it directly. About a minute after the audio stops, you are asked: " +
            "Confirm ✓ or Not done. 5 min or more of audio gives the ×1.2 evidence bonus; under 2 min is ignored.") {
        for (h in appHabits) {
            val launch = context.packageManager.getLaunchIntentForPackage(h.sourcePackage!!) ?: continue
            GuideButton("Open ${appLabel(context, h.sourcePackage) ?: h.name}") { context.startActivity(launch) }
        }
    }

    if (choosing) {
        HabitChooser("Which habit uses an app?", allHabits, label = { h -> h.sourcePackage?.let { " · ${appLabel(context, it) ?: it}" } ?: "" },
            onPick = { choosing = false; onLinkHabit(it) }, onDismiss = { choosing = false })
    }
    Text(
        "Audio apps are noticed about a minute after the audio stops. Apps without audio are noticed within the hour " +
            "with Usage access (3 min or more on screen). You can always tick a habit by hand.",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Pick one of the habits (or a new one, id 0). */
@Composable
private fun HabitChooser(
    title: String,
    habits: List<com.ayushpant.habitquest.data.Habit>,
    label: (com.ayushpant.habitquest.data.Habit) -> String,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                for (h in habits) {
                    Text(
                        h.name + label(h),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(h.id) }.padding(vertical = 12.dp),
                    )
                }
                Text(
                    "+ New habit",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(0L) }.padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Set-up for zone habits (spec 5.3, 11), each step with its check and a button. */
@Composable
private fun ZoneHabitGuide(
    reminders: ReminderRepository,
    foreground: Boolean,
    background: Boolean,
    onZones: () -> Unit,
    onLinkHabit: (Long) -> Unit,
    askForeground: () -> Unit,
    askBackground: () -> Unit,
) {
    val habits by reminders.observeActiveHabits().collectAsStateWithLifecycle(initialValue = emptyList())
    val zoneHabits = habits.filter { it.type == HabitType.ZONE && it.zoneId != null }
    var choosing by remember { mutableStateOf(false) }
    Text(
        "For habits you do somewhere, like the gym: stay 5 minutes and Habit Quest asks if you're doing it, " +
            "leave and it asks you to confirm. Driving past does nothing. Your location stays on the phone.",
        style = MaterialTheme.typography.bodyMedium,
    )
    GuideStep(1, foreground, "Allow location", "Needed to save a place and to know when you are there.") {
        if (!foreground) GuideButton("Allow") { askForeground() }
    }
    GuideStep(2, if (foreground) background else false, "Choose \"Allow all the time\"",
        "So places work while Habit Quest is closed. Android shows this as a separate choice.") {
        if (foreground && !background) GuideButton("Allow all the time") { askBackground() }
    }
    GuideStep(3, zoneHabits.isNotEmpty(), "Save the place and link a habit",
        if (zoneHabits.isEmpty()) "Add the place (best while you are there), then choose the habit." else zoneHabits.joinToString("\n") { "📍 ${it.name}" }) {
        GuideButton("Places") { onZones() }
        GuideButton(if (zoneHabits.isEmpty()) "Link a habit" else "Link another") { choosing = true }
    }
    if (choosing) {
        HabitChooser("Which habit is done at a place?", habits, label = { h -> if (h.type == HabitType.ZONE) " · 📍" else "" },
            onPick = { choosing = false; onLinkHabit(it) }, onDismiss = { choosing = false })
    }
}

/** Opens Habit Quest's own switch in "Notification access" (Android 11+), or the list of apps before that. */
private fun openNotificationAccess(context: android.content.Context) {
    val list = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val component = android.content.ComponentName(context, HabitListenerService::class.java)
        val direct = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        if (direct.resolveActivity(context.packageManager) != null) {
            context.startActivity(direct)
            return
        }
    }
    context.startActivity(list)
}

/** App info, where "⋮ → Allow restricted settings" is (for apps installed from a file). */
private fun openAppInfo(context: android.content.Context) {
    context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
}

/** Opens "Usage access" (Habit Quest's own page where the phone supports it). */
private fun openUsageAccess(context: android.content.Context) {
    val direct = Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri())
    val list = Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)
    context.startActivity(if (direct.resolveActivity(context.packageManager) != null) direct else list)
}

private fun openOverlaySetting(context: android.content.Context) {
    context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
}

@Composable
private fun GuideButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 6.dp, end = 8.dp)) { Text(text) }
}

/** One numbered step. [done]: true ✓, false ✗, null = nothing to check. [actions]: buttons below it. */
@Composable
private fun GuideStep(number: Int, done: Boolean?, title: String, detail: String, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            when (done) {
                true -> "✓"
                false -> "$number"
                null -> "$number"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (done == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(28.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (done == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow { actions() }
        }
    }
}

private fun kindText(kind: ReminderKind) = when (kind) {
    ReminderKind.START -> "start"
    ReminderKind.LAST_CHANCE -> "last chance"
    ReminderKind.SNOOZE -> "snooze"
    ReminderKind.CHECKLIST -> "11 PM"
    ReminderKind.PROMISE -> "promise"
    ReminderKind.EARLY -> "early"
    ReminderKind.TASK_DUE -> "task due"
    ReminderKind.TASK_EARLY -> "task early"
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
