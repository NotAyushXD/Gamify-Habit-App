package com.ayushpant.habitquest.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.HabitToday
import com.ayushpant.habitquest.data.Session
import com.ayushpant.habitquest.data.TodayData
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Holds the Today screen's data. It lives while the screen exists, also across rotation. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: HabitRepository) : ViewModel() {
    private val day = MutableStateFlow(repo.today())

    val state: StateFlow<TodayData?> = day
        .flatMapLatest { repo.observeToday(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // If the app stays open past midnight, move to the new day.
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                if (repo.today() != day.value) refresh()
            }
        }
    }

    /** Called each time the screen comes to the front. */
    fun refresh() = viewModelScope.launch {
        repo.closePendingDays()
        day.value = repo.today()
    }

    fun start(habitId: Long) = viewModelScope.launch { repo.startSession(habitId) }
    fun finish(sessionId: Long) = viewModelScope.launch { repo.finishSession(sessionId) }
    fun tick(habitId: Long) = viewModelScope.launch { repo.tickHabit(habitId) }
    fun remove(sessionId: Long) = viewModelScope.launch { repo.removeSession(sessionId) }
    fun editStart(sessionId: Long, time: LocalTime) = viewModelScope.launch { repo.editSessionStart(sessionId, time) }
    fun timeOf(session: Session): LocalTime = repo.localTimeOf(session.startTime)
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE d MMMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
) {
    val data by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Session?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Today")
                    data?.let { Text(it.day.format(DAY_TITLE), style = MaterialTheme.typography.bodySmall) }
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) { Icon(Icons.Default.Add, contentDescription = "Add habit") }
        },
    ) { padding ->
        val today = data ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(today) }
            if (today.habits.isEmpty()) {
                item {
                    Text(
                        "No habits yet. Tap + to add your first habit.",
                        modifier = Modifier.padding(top = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(today.habits, key = { it.habit.id }) { h ->
                HabitTile(
                    h = h,
                    timeOf = vm::timeOf,
                    onStart = { vm.start(h.habit.id) },
                    onFinish = { s -> vm.finish(s.id) },
                    onCancel = { s -> vm.remove(s.id) },
                    onTick = { vm.tick(h.habit.id) },
                    onOpenDone = { s -> editing = s },
                    onEdit = { onEditHabit(h.habit.id) },
                )
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
}

@Composable
private fun SummaryCard(today: TodayData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${today.totalPoints} points today", style = MaterialTheme.typography.headlineSmall)
            val bonusText = if (today.dailyBonus > 0) {
                "All habits done: +${today.dailyBonus} daily bonus included"
            } else {
                "Do every habit scheduled today for +${ScoringConfig.DEFAULT.dailyBonus}"
            }
            Text(bonusText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HabitTile(
    h: HabitToday,
    timeOf: (Session) -> LocalTime,
    onStart: () -> Unit,
    onFinish: (Session) -> Unit,
    onCancel: (Session) -> Unit,
    onTick: () -> Unit,
    onOpenDone: (Session) -> Unit,
    onEdit: () -> Unit,
) {
    val firstDone = h.done.firstOrNull()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (h.scheduledToday || h.isDone) 1f else 0.6f)
            // Spec 9: tap the tile to start a session.
            .clickable {
                when {
                    firstDone != null -> onOpenDone(firstDone)
                    h.inProgress == null -> onStart()
                }
            },
        colors = if (h.isDone) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.habit.name, style = MaterialTheme.typography.titleMedium)
                val window = "${h.habit.windowStart.format(HHMM)}–${h.habit.windowEnd.format(HHMM)}"
                val streak = if (h.streakDays > 0) "  ·  🔥 ${h.streakDays}" else ""
                Text(window + streak, style = MaterialTheme.typography.bodyMedium)
                val status = when {
                    firstDone != null -> {
                        val bonus = if (firstDone.bonusPoints > 0) " (incl. +${firstDone.bonusPoints} comeback)" else ""
                        "Done at ${timeOf(firstDone).format(HHMM)}  ·  +${h.points}$bonus"
                    }
                    h.inProgress != null -> "In progress since ${timeOf(h.inProgress).format(HHMM)}"
                    !h.scheduledToday -> "Not scheduled today"
                    else -> "Not done — tap to start"
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                if (h.inProgress != null && firstDone == null) {
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = { onFinish(h.inProgress) }) { Text("Done") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { onCancel(h.inProgress) }) { Text("Cancel") }
                    }
                }
            }
            // Spec 9: tick a manual habit.
            Checkbox(
                checked = h.isDone,
                onCheckedChange = { if (firstDone != null) onOpenDone(firstDone) else onTick() },
            )
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit habit") }
        }
    }
}

/** Opens for a done habit: fix the time, or remove a mistaken tick. */
@Composable
private fun SessionDialog(
    session: Session,
    start: LocalTime,
    onChangeStart: (LocalTime) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmRemove by remember(session.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirmRemove) "Remove this session?" else "Done at ${start.format(HHMM)}") },
        text = {
            Text(
                if (confirmRemove) "Its points are removed from today."
                else "+${session.points} points. If you did it at another time, change the time: points follow the time."
            )
        },
        confirmButton = {
            if (confirmRemove) {
                TextButton(onClick = onRemove) { Text("Remove") }
            } else {
                TextButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> onChangeStart(LocalTime.of(h, m)) },
                        start.hour, start.minute, DateFormat.is24HourFormat(context),
                    ).show()
                }) { Text("Change time") }
            }
        },
        dismissButton = {
            Row {
                if (!confirmRemove) TextButton(onClick = { confirmRemove = true }) { Text("Not done") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
