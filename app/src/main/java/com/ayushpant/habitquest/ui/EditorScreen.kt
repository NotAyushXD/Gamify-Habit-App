package com.ayushpant.habitquest.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.dimensionsOf
import com.ayushpant.habitquest.balance.maskOf
import com.ayushpant.habitquest.balance.suggestDimensions
import com.ayushpant.habitquest.data.CreatureLine
import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitType
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.EVERY_DAY_MASK
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DUE_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

enum class ItemKind { HABIT, TASK }

/**
 * Editor for habits (spec 9) and tasks. [id] = 0 creates a new item, and then a Habit | Task
 * switch is shown. Step 1 makes manual habits only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(repo: HabitRepository, initialKind: ItemKind, id: Long, onDone: () -> Unit, startType: HabitType? = null) {
    val cfg = ScoringConfig.DEFAULT
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isNew = id == 0L

    // `remember { mutableStateOf(...) }` is a variable that redraws the screen when it changes.
    var kind by remember { mutableStateOf(initialKind) }
    var name by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    // Habit fields
    var loadedHabit by remember { mutableStateOf<Habit?>(null) }
    var windowStart by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var windowEnd by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var daysMask by remember { mutableStateOf(EVERY_DAY_MASK) }
    // App habit (Step 3): opens and watches an app, e.g. a meditation app.
    var habitType by remember { mutableStateOf(startType ?: HabitType.MANUAL) }
    var sourcePackage by remember { mutableStateOf<String?>(null) }
    // Zone habit (Step 4): done at a place, e.g. the gym.
    var zoneId by remember { mutableStateOf<Long?>(null) }
    var addingZone by remember { mutableStateOf(false) }
    val zones by repo.observeZones().collectAsStateWithLifecycle(initialValue = emptyList())
    var pickingApp by remember { mutableStateOf(false) }

    // Mind / Body / Soul: suggested from the name until the user taps a chip.
    var dims by remember { mutableStateOf(emptySet<Dimension>()) }
    var dimsTouched by remember { mutableStateOf(false) }

    // Task fields
    var loadedTask by remember { mutableStateOf<Task?>(null) }
    var dueDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(id) {
        if (isNew) return@LaunchedEffect
        when (initialKind) {
            ItemKind.HABIT -> repo.getHabit(id)?.let { h ->
                loadedHabit = h
                name = h.name
                windowStart = h.windowStart
                windowEnd = h.windowEnd
                daysMask = h.daysMask
                habitType = startType ?: h.type
                sourcePackage = h.sourcePackage
                zoneId = h.zoneId
                dims = dimensionsOf(h.mbsMask)
                dimsTouched = h.mbsMask != 0
            }
            ItemKind.TASK -> repo.getTask(id)?.let { t ->
                loadedTask = t
                name = t.name
                dueDate = t.dueDate
                dims = dimensionsOf(t.mbsMask)
                dimsTouched = t.mbsMask != 0
            }
        }
    }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context, { _, h, m -> onPicked(LocalTime.of(h, m)) },
            initial.hour, initial.minute, DateFormat.is24HourFormat(context),
        ).show()
    }

    fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        // Android's date picker counts months from 0, java.time from 1.
        val dialog = DatePickerDialog(
            context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        )
        if (isNew) {
            dialog.datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        dialog.show()
    }

    val windowValid = windowStart < windowEnd
    val canSave = name.isNotBlank() && when (kind) {
        ItemKind.HABIT -> windowValid && daysMask != 0 && when (habitType) {
            HabitType.MANUAL -> true
            HabitType.APP -> sourcePackage != null
            HabitType.ZONE -> zoneId != null && zones.any { it.id == zoneId }
        }
        ItemKind.TASK -> true
    }
    val noun = if (kind == ItemKind.HABIT) "habit" else "task"

    fun save() {
        scope.launch {
            when (kind) {
                ItemKind.HABIT -> {
                    val base = loadedHabit ?: Habit(
                        name = "", windowStart = windowStart, windowEnd = windowEnd, basePoints = cfg.basePoints,
                        creatureLine = CreatureLine.CALM, daysMask = daysMask, createdDate = LocalDate.now(), // the repository sets the creature line
                    )
                    repo.saveHabit(
                        base.copy(
                            name = name.trim(), windowStart = windowStart, windowEnd = windowEnd,
                            daysMask = daysMask, mbsMask = maskOf(dims),
                            type = habitType, sourcePackage = if (habitType == HabitType.APP) sourcePackage else null,
                            zoneId = if (habitType == HabitType.ZONE) zoneId else null,
                        )
                    )
                }
                ItemKind.TASK -> {
                    val base = loadedTask ?: Task(
                        name = "", dueDate = dueDate, basePoints = cfg.basePoints, createdDate = LocalDate.now(),
                    )
                    repo.saveTask(base.copy(name = name.trim(), dueDate = dueDate, mbsMask = maskOf(dims)))
                }
            }
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New $noun" else "Edit $noun") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
            )
        },
        // The save button is always visible at the bottom.
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    enabled = canSave,
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).height(52.dp),
                ) { Text(if (isNew) "Add $noun" else "Save", style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isNew) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ItemKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k,
                            onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ItemKind.entries.size),
                        ) { Text(if (k == ItemKind.HABIT) "🔁 Habit" else "📋 Task") }
                    }
                }
                Text(
                    if (kind == ItemKind.HABIT) "Something you repeat. Builds a streak." else "Something you do once, by a date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- What -----------------------------------------------------------------------------
            FormCard("What") {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!dimsTouched) dims = suggestDimensions(it)
                    },
                    placeholder = { Text(if (kind == ItemKind.HABIT) "e.g. Meditate" else "e.g. Renew passport") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (isNew && kind == ItemKind.HABIT && name.isBlank()) {
                    Text("Or pick one:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipFlow {
                        for (q in QUICK_HABITS) {
                            AssistChip(onClick = {
                                name = q.name
                                windowStart = q.from
                                windowEnd = q.to
                                if (!dimsTouched) dims = suggestDimensions(q.name)
                            }, label = { Text("${q.emoji} ${q.name}") })
                        }
                    }
                }
            }

            when (kind) {
                // ---- When (habit) -------------------------------------------------------------------
                ItemKind.HABIT -> FormCard("When", "Doing it in this time gives full points.") {
                    ChipFlow {
                        for (preset in TIME_PRESETS) {
                            FilterChip(
                                selected = windowStart == preset.from && windowEnd == preset.to,
                                onClick = { windowStart = preset.from; windowEnd = preset.to },
                                label = { Text("${preset.emoji} ${preset.label}") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickTime(windowStart) { windowStart = it } }) { Text(windowStart.format(HHMM)) }
                        Text("to")
                        OutlinedButton(onClick = { pickTime(windowEnd) { windowEnd = it } }) { Text(windowEnd.format(HHMM)) }
                    }
                    if (!windowValid) Text("The end must be after the start.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    ChipFlow {
                        for ((label, mask) in DAY_PRESETS) {
                            FilterChip(selected = daysMask == mask, onClick = { daysMask = mask }, label = { Text(label) })
                        }
                    }
                    // Seven equal round buttons: always fit the width.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAY_LETTERS.forEachIndexed { i, letter ->
                            val bit = 1 shl i
                            val on = daysMask and bit != 0
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { daysMask = daysMask xor bit },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(letter, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // ---- When (task) --------------------------------------------------------------------
                ItemKind.TASK -> FormCard("Due", "On time: ×${cfg.taskOnTime} points. Late still counts, a bit less each day.") {
                    val today = LocalDate.now()
                    ChipFlow {
                        for ((label, date) in duePresets(today)) {
                            FilterChip(selected = dueDate == date, onClick = { dueDate = date }, label = { Text(label) })
                        }
                        AssistChip(onClick = { pickDate(dueDate) { dueDate = it } }, label = { Text("📅 Pick a date") })
                    }
                    Text("Due ${dueDate.format(DUE_DATE)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ---- How (habits) -------------------------------------------------------------------------
            if (kind == ItemKind.HABIT) {
                FormCard("How") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = habitType == HabitType.MANUAL, onClick = { habitType = HabitType.MANUAL }, shape = SegmentedButtonDefaults.itemShape(0, 3)) {
                            Text("✋ I tick it")
                        }
                        SegmentedButton(selected = habitType == HabitType.APP, onClick = { habitType = HabitType.APP }, shape = SegmentedButtonDefaults.itemShape(1, 3)) {
                            Text("🎧 App")
                        }
                        SegmentedButton(selected = habitType == HabitType.ZONE, onClick = { habitType = HabitType.ZONE }, shape = SegmentedButtonDefaults.itemShape(2, 3)) {
                            Text("📍 Place")
                        }
                    }
                    when (habitType) {
                        HabitType.APP -> {
                            OutlinedButton(onClick = { pickingApp = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(sourcePackage?.let { "App: ${appLabel(context, it) ?: it}" } ?: "Choose the app")
                            }
                            Hint("When you use the app, you're asked to confirm. You can always tick it yourself too.")
                        }
                        HabitType.ZONE -> {
                            ChipFlow {
                                for (z in zones) FilterChip(selected = zoneId == z.id, onClick = { zoneId = z.id }, label = { Text("📍 ${z.name}") })
                                AssistChip(onClick = { addingZone = true }, label = { Text("+ New place") })
                            }
                            Hint("Stay there 5 minutes and you're asked if you're doing it. Leave and you're asked to confirm.")
                        }
                        HabitType.MANUAL -> Hint("Tick it when you've done it.")
                    }
                }
            }

            // ---- Good for -----------------------------------------------------------------------------
            FormCard("Good for", if (dims.isNotEmpty() && !dimsTouched) "Suggested from the name. Tap to change." else "Its points fill these meters.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dimension.entries.forEach { d ->
                        FilterChip(
                            selected = d in dims,
                            onClick = {
                                dimsTouched = true
                                dims = if (d in dims) dims - d else dims + d
                            },
                            label = { Text("${d.emoji} ${d.label}") },
                        )
                    }
                }
            }

            Hint("Every ${noun} is worth ${cfg.basePoints} points. Being on time, streaks and bonuses add more.")
        }
    }

    if (addingZone) {
        ZoneEditorDialog(
            initial = null,
            onSave = { z -> scope.launch { zoneId = repo.saveZone(z) }; addingZone = false },
            onDismiss = { addingZone = false },
        )
    }

    if (pickingApp) {
        AppPickerDialog(
            onPick = { app ->
                sourcePackage = app.packageName
                if (name.isBlank() && isMeditationApp(app)) {
                    name = "Meditate"
                    if (!dimsTouched) dims = suggestDimensions(name)
                }
                pickingApp = false
            },
            onDismiss = { pickingApp = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"$name\"?") },
            text = { Text("The $noun leaves the Today screen. Points you already got stay.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (kind) {
                            ItemKind.HABIT -> repo.deleteHabit(id)
                            ItemKind.TASK -> repo.deleteTask(id)
                        }
                        onDone()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}


/** A titled white card for one part of the form. */
@Composable
private fun FormCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Chips that wrap onto the next line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private data class QuickHabit(val emoji: String, val name: String, val from: LocalTime, val to: LocalTime)

/** One tap fills the name, a usual time and the Mind / Body / Soul suggestion. */
private val QUICK_HABITS = listOf(
    QuickHabit("🧘", "Meditate", LocalTime.of(7, 0), LocalTime.of(7, 30)),
    QuickHabit("🏋️", "Gym", LocalTime.of(18, 0), LocalTime.of(19, 30)),
    QuickHabit("📖", "Read", LocalTime.of(21, 0), LocalTime.of(21, 30)),
    QuickHabit("🚶", "Walk", LocalTime.of(17, 0), LocalTime.of(18, 0)),
    QuickHabit("💧", "Drink water", LocalTime.of(9, 0), LocalTime.of(20, 0)),
    QuickHabit("✍️", "Journal", LocalTime.of(21, 30), LocalTime.of(22, 0)),
    QuickHabit("😴", "Sleep by 11", LocalTime.of(22, 30), LocalTime.of(23, 0)),
)

private data class TimePreset(val emoji: String, val label: String, val from: LocalTime, val to: LocalTime)

private val TIME_PRESETS = listOf(
    TimePreset("🌅", "Morning", LocalTime.of(6, 0), LocalTime.of(9, 0)),
    TimePreset("☀️", "Midday", LocalTime.of(12, 0), LocalTime.of(14, 0)),
    TimePreset("🌤️", "Afternoon", LocalTime.of(14, 0), LocalTime.of(17, 0)),
    TimePreset("🌆", "Evening", LocalTime.of(17, 0), LocalTime.of(20, 0)),
    TimePreset("🌙", "Night", LocalTime.of(20, 0), LocalTime.of(23, 0)),
)

/** Monday = bit 0 … Sunday = bit 6. */
private val DAY_PRESETS = listOf("Every day" to EVERY_DAY_MASK, "Weekdays" to 0b001_1111, "Weekends" to 0b110_0000)

private fun duePresets(today: LocalDate): List<Pair<String, LocalDate>> {
    val saturday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY))
    val nextMonday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
    return listOf("Today" to today, "Tomorrow" to today.plusDays(1), "This weekend" to saturday, "Next week" to nextMonday)
}
