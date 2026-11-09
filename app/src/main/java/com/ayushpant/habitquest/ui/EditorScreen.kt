package com.ayushpant.habitquest.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
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
fun EditorScreen(repo: HabitRepository, initialKind: ItemKind, id: Long, onDone: () -> Unit) {
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
    // App habit (Step 3): opens and watches an app, e.g. Medito.
    var habitType by remember { mutableStateOf(HabitType.MANUAL) }
    var sourcePackage by remember { mutableStateOf<String?>(null) }
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
                habitType = if (h.type == HabitType.APP) HabitType.APP else HabitType.MANUAL
                sourcePackage = h.sourcePackage
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
        ItemKind.HABIT -> windowValid && daysMask != 0 && (habitType == HabitType.MANUAL || sourcePackage != null)
        ItemKind.TASK -> true
    }
    val noun = if (kind == ItemKind.HABIT) "habit" else "task"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New $noun" else "Edit $noun") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isNew) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ItemKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k,
                            onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ItemKind.entries.size),
                        ) { Text(if (k == ItemKind.HABIT) "Habit" else "Task") }
                    }
                }
                Text(
                    if (kind == ItemKind.HABIT) "Repeats on the days you pick. Builds a streak."
                    else "Done once, by a due date. Finish on time for a bonus.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (!dimsTouched) dims = suggestDimensions(it)
                },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            when (kind) {
                ItemKind.HABIT -> {
                    Column {
                        Text("Window", style = MaterialTheme.typography.titleSmall)
                        Text("The time you plan to do it. In the window = full points.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { pickTime(windowStart) { windowStart = it } }) {
                                Text("From ${windowStart.format(HHMM)}")
                            }
                            OutlinedButton(onClick = { pickTime(windowEnd) { windowEnd = it } }) {
                                Text("To ${windowEnd.format(HHMM)}")
                            }
                        }
                        if (!windowValid) {
                            Text("The end must be after the start, on the same day.", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Column {
                        Text("Days", style = MaterialTheme.typography.titleSmall)
                        Text("A day that is not selected is never a miss.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                            DAY_LETTERS.forEachIndexed { i, letter ->
                                val bit = 1 shl i
                                FilterChip(
                                    selected = daysMask and bit != 0,
                                    onClick = { daysMask = daysMask xor bit },
                                    label = { Text(letter) },
                                )
                            }
                        }
                    }
                }
                ItemKind.TASK -> {
                    Column {
                        Text("Due date", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { pickDate(dueDate) { dueDate = it } }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Due ${dueDate.format(DUE_DATE)}")
                        }
                        Text(taskScoringHelp(cfg), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Text(
                "Every habit and task is worth the same ${cfg.basePoints} base points, so the score stays fair. " +
                    "Timing, streaks and bonuses decide the rest.",
                style = MaterialTheme.typography.bodySmall,
            )

            Column {
                Text("Feeds your", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        dims.isEmpty() -> "Pick what this is good for. Its points fill these meters."
                        dimsTouched -> "Its points are shared between these meters."
                        else -> "Suggested from the name. Tap to change."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
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

            if (kind == ItemKind.HABIT) {
                Column {
                    Text("How do you do it?", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SegmentedButton(selected = habitType == HabitType.MANUAL, onClick = { habitType = HabitType.MANUAL }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text("Manual")
                        }
                        SegmentedButton(selected = habitType == HabitType.APP, onClick = { habitType = HabitType.APP }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                            Text("With an app")
                        }
                    }
                    if (habitType == HabitType.APP) {
                        OutlinedButton(onClick = { pickingApp = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(sourcePackage?.let { "App: ${appLabel(context, it) ?: it}" } ?: "Choose the app")
                        }
                        Text(
                            "Tap the habit's tile to open the app. When its audio stops, Habit Quest asks you to confirm " +
                                "the session (needs Notification access in Settings). You can always tick it by hand too.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("You tick it yourself, or tap Start and Done. Zone habits come in a later step.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
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
                },
            ) { Text("Save") }

            if (!isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (pickingApp) {
        AppPickerDialog(
            onPick = { app ->
                sourcePackage = app.packageName
                if (name.isBlank() && app.label.contains("medito", ignoreCase = true)) {
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

/** e.g. "By the due date: ×1.2. Late: 1+ days ×0.8, 3+ days ×0.6, …" — built from the config. */
private fun taskScoringHelp(cfg: ScoringConfig): String {
    val late = cfg.taskLateTiers.sortedBy { it.first }.joinToString(", ") { (days, f) -> "$days+ days ×$f" }
    return "By the due date: ×${cfg.taskOnTime} points. Late: $late."
}

