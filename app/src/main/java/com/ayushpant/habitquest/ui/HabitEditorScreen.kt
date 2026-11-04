package com.ayushpant.habitquest.ui

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
import androidx.compose.material3.Slider
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
import com.ayushpant.habitquest.data.CreatureLine
import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.scoring.EVERY_DAY_MASK
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** Habit editor (spec 9). [habitId] = 0 creates a new habit. Step 1 makes manual habits only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorScreen(repo: HabitRepository, habitId: Long, onDone: () -> Unit) {
    val cfg = ScoringConfig.DEFAULT
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // `remember { mutableStateOf(...) }` is a variable that redraws the screen when it changes.
    var loaded by remember { mutableStateOf<Habit?>(null) }
    var name by remember { mutableStateOf("") }
    var windowStart by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var windowEnd by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var basePoints by remember { mutableStateOf(cfg.defaultBasePoints) }
    var daysMask by remember { mutableStateOf(EVERY_DAY_MASK) }
    var creatureLine by remember { mutableStateOf(CreatureLine.CALM) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(habitId) {
        if (habitId != 0L) {
            repo.getHabit(habitId)?.let { h ->
                loaded = h
                name = h.name
                windowStart = h.windowStart
                windowEnd = h.windowEnd
                basePoints = h.basePoints
                daysMask = h.daysMask
                creatureLine = h.creatureLine
            }
        }
    }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context, { _, h, m -> onPicked(LocalTime.of(h, m)) },
            initial.hour, initial.minute, DateFormat.is24HourFormat(context),
        ).show()
    }

    val windowValid = windowStart < windowEnd
    val canSave = name.isNotBlank() && windowValid && daysMask != 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (habitId == 0L) "New habit" else "Edit habit") },
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
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

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

            Column {
                Text("Base points: $basePoints", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = basePoints.toFloat(),
                    onValueChange = { basePoints = it.roundToInt() },
                    valueRange = cfg.minBasePoints.toFloat()..cfg.maxBasePoints.toFloat(),
                    steps = cfg.maxBasePoints - cfg.minBasePoints - 1,
                )
            }

            Column {
                Text("Creature", style = MaterialTheme.typography.titleSmall)
                Text("Calm = mind, Strong = body, Wise = learning. Hatches in a later version.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    CreatureLine.entries.forEach { line ->
                        FilterChip(
                            selected = creatureLine == line,
                            onClick = { creatureLine = line },
                            label = { Text(line.label()) },
                        )
                    }
                }
            }

            Text(
                "Type: manual. App and zone habits come in later steps.",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val base = loaded ?: Habit(
                        name = "", windowStart = windowStart, windowEnd = windowEnd, basePoints = basePoints,
                        creatureLine = creatureLine, daysMask = daysMask, createdDate = LocalDate.now(),
                    )
                    val habit = base.copy(
                        name = name.trim(), windowStart = windowStart, windowEnd = windowEnd,
                        basePoints = basePoints, daysMask = daysMask, creatureLine = creatureLine,
                    )
                    scope.launch {
                        repo.saveHabit(habit)
                        onDone()
                    }
                },
            ) { Text("Save") }

            if (loaded != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"$name\"?") },
            text = { Text("The habit leaves the Today screen. Points you already got stay.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteHabit(habitId)
                        onDone()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun CreatureLine.label() = when (this) {
    CreatureLine.CALM -> "Calm"
    CreatureLine.STRONG -> "Strong"
    CreatureLine.WISE -> "Wise"
}
