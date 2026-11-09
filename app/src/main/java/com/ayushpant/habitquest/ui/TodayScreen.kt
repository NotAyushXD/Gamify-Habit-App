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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ayushpant.habitquest.data.TaskToday
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

    fun completeTask(taskId: Long) = viewModelScope.launch { repo.completeTask(taskId) }
    fun reopenTask(taskId: Long) = viewModelScope.launch { repo.reopenTask(taskId) }
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val DUE_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onAdd: (ItemKind) -> Unit,
    onEditHabit: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
) {
    val data by vm.state.collectAsStateWithLifecycle()
    // rememberSaveable: the chosen tab survives rotation and coming back from the editor.
    var tab by rememberSaveable { mutableIntStateOf(ItemKind.HABIT.ordinal) }
    val kind = ItemKind.entries[tab]
    var editing by remember { mutableStateOf<Session?>(null) }
    var reopening by remember { mutableStateOf<TaskToday?>(null) }

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
            FloatingActionButton(onClick = { onAdd(kind) }) {
                Icon(Icons.Default.Add, contentDescription = if (kind == ItemKind.HABIT) "Add habit" else "Add task")
            }
        },
    ) { padding ->
        val today = data ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { SummaryCard(today) }
            PrimaryTabRow(selectedTabIndex = tab) {
                val scheduled = today.habits.count { it.scheduledToday }
                val doneScheduled = today.habits.count { it.scheduledToday && it.isDone }
                Tab(
                    selected = kind == ItemKind.HABIT,
                    onClick = { tab = ItemKind.HABIT.ordinal },
                    text = { Text("Habits  $doneScheduled/$scheduled") },
                )
                Tab(
                    selected = kind == ItemKind.TASK,
                    onClick = { tab = ItemKind.TASK.ordinal },
                    text = { Text("Tasks  ${today.tasks.count { !it.isDone }} open") },
                )
            }
            when (kind) {
                ItemKind.HABIT -> HabitList(today, vm, onOpenDone = { editing = it }, onEditHabit = onEditHabit)
                ItemKind.TASK -> TaskList(
                    today,
                    onTick = { t -> if (t.isDone) reopening = t else vm.completeTask(t.task.id) },
                    onEditTask = onEditTask,
                )
            }
        }
    }

    reopening?.let { t ->
        AlertDialog(
            onDismissRequest = { reopening = null },
            title = { Text("Mark \"${t.task.name}\" as not done?") },
            text = { Text("Its +${t.points} points are removed. When you finish it again, it is scored for that day.") },
            confirmButton = { TextButton(onClick = { vm.reopenTask(t.task.id); reopening = null }) { Text("Not done") } },
            dismissButton = { TextButton(onClick = { reopening = null }) { Text("Cancel") } },
        )
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

/** List padding: room at the bottom so the + button never covers the last tile. */
private val LIST_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp)

@Composable
private fun EmptyText(text: String) {
    Text(text, modifier = Modifier.padding(16.dp).padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun HabitList(
    today: TodayData,
    vm: TodayViewModel,
    onOpenDone: (Session) -> Unit,
    onEditHabit: (Long) -> Unit,
) {
    if (today.habits.isEmpty()) return EmptyText("No habits yet. Tap + to add your first habit.")
    LazyColumn(contentPadding = LIST_PADDING, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(today.habits, key = { it.habit.id }) { h ->
            HabitTile(
                h = h,
                timeOf = vm::timeOf,
                onStart = { vm.start(h.habit.id) },
                onFinish = { s -> vm.finish(s.id) },
                onCancel = { s -> vm.remove(s.id) },
                onTick = { vm.tick(h.habit.id) },
                onOpenDone = onOpenDone,
                onEdit = { onEditHabit(h.habit.id) },
            )
        }
    }
}

@Composable
private fun TaskList(today: TodayData, onTick: (TaskToday) -> Unit, onEditTask: (Long) -> Unit) {
    if (today.tasks.isEmpty()) return EmptyText("No open tasks. Tap + to add one.")
    LazyColumn(contentPadding = LIST_PADDING, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(today.tasks, key = { it.task.id }) { t ->
            TaskTile(t = t, onTick = { onTick(t) }, onEdit = { onEditTask(t.task.id) })
        }
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
            if (today.balance.any { it.planned > 0 }) BalanceRow(today.balance, Modifier.padding(top = 12.dp))
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
                val freeze = if (h.freezeUsed && h.streakDays > 0) " · ❄️ freeze used" else ""
                val streak = if (h.streakDays > 0) "  ·  🔥 ${h.streakDays}$freeze" else ""
                Text(window + streak, style = MaterialTheme.typography.bodyMedium)
                val status = when {
                    firstDone != null -> {
                        val bonus = if (firstDone.bonusPoints > 0) " (incl. +${firstDone.bonusPoints} comeback)" else ""
                        "Done at ${timeOf(firstDone).format(HHMM)}  ·  +${h.points}$bonus"
                    }
                    h.inProgress != null -> "In progress since ${timeOf(h.inProgress).format(HHMM)}"
                    h.skip?.promised == true -> "Skipped today · you promised tomorrow"
                    h.skip != null -> "Skipped today"
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

@Composable
private fun TaskTile(t: TaskToday, onTick: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = t.isDone, onClick = onTick),
        colors = if (t.isDone) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.task.name, style = MaterialTheme.typography.titleMedium)
                val due = when {
                    t.isDone && t.daysLate > 0 -> "Done ${dayCount(t.daysLate)} late  ·  +${t.points}"
                    t.isDone -> "Done on time  ·  +${t.points}"
                    t.daysLate > 0 -> "Overdue by ${dayCount(t.daysLate)}  ·  now worth ${t.points}"
                    t.daysLate == 0L -> "Due today  ·  worth ${t.points}"
                    else -> "Due ${t.task.dueDate.format(DUE_DAY)}  ·  worth ${t.points}"
                }
                Text(
                    due,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!t.isDone && t.daysLate > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Checkbox(checked = t.isDone, onCheckedChange = { onTick() })
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit task") }
        }
    }
}

private fun dayCount(days: Long) = if (days == 1L) "1 day" else "$days days"

/** Opens for a done habit: fix the time, or remove a mistaken tick. Also used by History. */
@Composable
internal fun SessionDialog(
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
