package com.ayushpant.habitquest.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.Session
import com.ayushpant.habitquest.data.SessionSource
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.daysLate
import com.ayushpant.habitquest.stats.HistoryDay
import com.ayushpant.habitquest.stats.HistoryEntry
import com.ayushpant.habitquest.stats.buildHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** History (spec 9): sessions and done tasks by day. Fix a time, undo, or add a missed session. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repo: HabitRepository) : ViewModel() {
    /** How many days back are shown. "Show older" adds 30. */
    private val daysShown = MutableStateFlow(30L)

    val days: StateFlow<List<HistoryDay>?> = daysShown
        .flatMapLatest { n ->
            val today = repo.today()
            repo.observeRange(today.minusDays(n - 1), today).map { buildHistory(it, today) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showOlder() { daysShown.value += 30 }
    fun timeOf(session: Session): LocalTime = repo.localTimeOf(session.startTime)
    fun editStart(sessionId: Long, time: LocalTime) = viewModelScope.launch { repo.editSessionStart(sessionId, time) }
    fun remove(sessionId: Long) = viewModelScope.launch { repo.removeSession(sessionId) }
    fun addMissed(habitId: Long, day: LocalDate, time: LocalTime) =
        viewModelScope.launch { repo.addSession(habitId, day, time, SessionSource.MANUAL) }
    fun reopenTask(taskId: Long) = viewModelScope.launch { repo.reopenTask(taskId) }
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_HEADER = DateTimeFormatter.ofPattern("EEEE d MMMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val days by vm.days.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Session?>(null) }
    var reopening by remember { mutableStateOf<Task?>(null) }

    fun addMissed(habit: Habit, day: LocalDate) {
        TimePickerDialog(
            context, { _, h, m -> vm.addMissed(habit.id, day, LocalTime.of(h, m)) },
            habit.windowStart.hour, habit.windowStart.minute, DateFormat.is24HourFormat(context),
        ).show()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        val list = days ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Forgot to tick something? Tap \"Add\" on a missed habit. Streaks and points are fixed from that day on.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(list, key = { it.day.toString() }) { day ->
                DayCard(
                    day = day,
                    timeOf = vm::timeOf,
                    onSession = { editing = it },
                    onAddMissed = { addMissed(it, day.day) },
                    onTask = { reopening = it },
                )
            }
            item {
                OutlinedButton(onClick = vm::showOlder, modifier = Modifier.fillMaxWidth()) { Text("Show older days") }
            }
        }
    }

    editing?.let { s ->
        SessionDialog(
            session = s,
            start = vm.timeOf(s),
            onChangeStart = { vm.editStart(s.id, it); editing = null },
            onRemove = { vm.remove(s.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    reopening?.let { t ->
        AlertDialog(
            onDismissRequest = { reopening = null },
            title = { Text("Mark \"${t.name}\" as not done?") },
            text = { Text("Its +${t.points} points are removed and it goes back to your open tasks.") },
            confirmButton = { TextButton(onClick = { vm.reopenTask(t.id); reopening = null }) { Text("Not done") } },
            dismissButton = { TextButton(onClick = { reopening = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayCard(
    day: HistoryDay,
    timeOf: (Session) -> LocalTime,
    onSession: (Session) -> Unit,
    onAddMissed: (Habit) -> Unit,
    onTask: (Task) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.day.format(DAY_HEADER), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("+${day.points}", style = MaterialTheme.typography.titleMedium)
            }
            if (day.dailyBonus > 0) Text("All habits done: +${day.dailyBonus} bonus", style = MaterialTheme.typography.bodySmall)
            if (day.entries.isEmpty()) Text("Nothing recorded.", style = MaterialTheme.typography.bodyMedium)
            for (entry in day.entries) {
                when (entry) {
                    is HistoryEntry.HabitDone -> {
                        val s = entry.session
                        val via = if (s.source == SessionSource.CHECKLIST) " · checklist" else ""
                        val bonus = if (s.bonusPoints > 0) " (incl. +${s.bonusPoints} comeback)" else ""
                        EntryRow("✓ ${entry.habitName}", "${timeOf(s).format(HHMM)}$via · +${s.points}$bonus") { onSession(s) }
                    }
                    is HistoryEntry.HabitMissed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        val why = when {
                            entry.skip?.promised == true -> "skipped · promised for the next day"
                            entry.skip != null -> "skipped"
                            else -> "missed"
                        }
                        Column(Modifier.weight(1f)) {
                            Text("✗ ${entry.habit.name}", style = MaterialTheme.typography.bodyLarge)
                            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { onAddMissed(entry.habit) }) { Text("Add") }
                    }
                    is HistoryEntry.TaskDone -> {
                        val t = entry.task
                        val late = daysLate(t.dueDate, t.doneDay!!)
                        val timing = if (late > 0) "$late day${if (late == 1L) "" else "s"} late" else "on time"
                        EntryRow("☑ ${t.name}", "task · $timing · +${t.points}") { onTask(t) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}
