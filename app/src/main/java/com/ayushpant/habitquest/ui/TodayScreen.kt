package com.ayushpant.habitquest.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.ayushpant.habitquest.stats.quoteOfTheDay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ayushpant.habitquest.data.HabitType
import com.ayushpant.habitquest.data.PromiseStatus
import com.ayushpant.habitquest.data.Session
import com.ayushpant.habitquest.data.TaskToday
import com.ayushpant.habitquest.data.TodayData
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
    fun chooseStarter(creatureId: String) = viewModelScope.launch { repo.chooseStarter(creatureId) }
    fun confirmDetected(sessionId: Long) = viewModelScope.launch { repo.confirmSuggestion(sessionId) }
    fun rejectDetected(sessionId: Long) = viewModelScope.launch { repo.rejectSuggestion(sessionId) }

    /** App habit tile (spec 5.1): record the launch, then open the linked app. */
    fun openApp(context: android.content.Context, habit: com.ayushpant.habitquest.data.Habit) = viewModelScope.launch {
        repo.recordLaunch(habit.id)
        val launch = habit.sourcePackage?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) context.startActivity(launch) else repo.startSession(habit.id) // app gone: a manual session
    }
    fun remove(sessionId: Long) = viewModelScope.launch { repo.removeSession(sessionId) }
    fun editStart(sessionId: Long, time: LocalTime) = viewModelScope.launch { repo.editSessionStart(sessionId, time) }
    fun timeOf(session: Session): LocalTime = repo.localTimeOf(session.startTime)

    fun completeTask(taskId: Long) = viewModelScope.launch { repo.completeTask(taskId) }
    fun reopenTask(taskId: Long) = viewModelScope.launch { repo.reopenTask(taskId) }
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val DUE_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

private fun timeOfMillis(ms: Long): LocalTime = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime()

/** What a habit or task panel (bottom sheet) is open for. */
private sealed interface Sheet {
    data class ForHabit(val habitId: Long) : Sheet
    data class ForTask(val taskId: Long) : Sheet
}

/**
 * Today: one glance at the day (ring, points, level, one line of encouragement), then the habits and
 * the tasks. Each row has one big round button (done) and opens a panel with every other action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onAdd: (ItemKind) -> Unit,
    onEditHabit: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onCollection: () -> Unit = {},
    onRewards: () -> Unit = {},
) {
    val data by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // rememberSaveable: the chosen tab survives rotation and coming back from the editor.
    var tab by rememberSaveable { mutableIntStateOf(ItemKind.HABIT.ordinal) }
    val kind = ItemKind.entries[tab]
    var editing by remember { mutableStateOf<Session?>(null) }
    var reopening by remember { mutableStateOf<TaskToday?>(null) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    // Celebrate what was just finished: compare with the data before the change.
    var previous by remember { mutableStateOf<TodayData?>(null) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    LaunchedEffect(data) {
        val now = data ?: return@LaunchedEffect
        previous?.let { before -> celebrationFor(before, now)?.let { celebration = it } }
        previous = now
    }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { onAdd(kind) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (kind == ItemKind.HABIT) "Habit" else "Task") },
                )
            },
        ) { padding ->
            val today = data ?: return@Scaffold
            // One list for the whole page: the header scrolls away, the switch sticks to the top.
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp), // room for the + button
            ) {
                item(key = "header") { Header(today, onCollection, onRewards, onChooseStarter = { vm.chooseStarter(it.id) }) }
                stickyHeader(key = "switch") {
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val scheduled = today.habits.count { it.scheduledToday }
                        val done = today.habits.count { it.scheduledToday && it.isDone }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(selected = kind == ItemKind.HABIT, onClick = { tab = ItemKind.HABIT.ordinal }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                                Text("Habits  $done/$scheduled")
                            }
                            SegmentedButton(selected = kind == ItemKind.TASK, onClick = { tab = ItemKind.TASK.ordinal }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                                Text("Tasks  ${today.tasks.count { !it.isDone }}")
                            }
                        }
                    }
                }
                when (kind) {
                    ItemKind.HABIT -> {
                        if (today.habits.isEmpty()) item(key = "empty-h") {
                            EmptyState("🌱", "Start with one small habit", "Something you want to do every day, like meditating or reading.", "Add your first habit") { onAdd(ItemKind.HABIT) }
                        }
                        items(today.habits, key = { "habit-${it.habit.id}" }) { h ->
                            HabitRow(
                                h = h,
                                onCheck = {
                                    val done = h.done.firstOrNull()
                                    if (done != null) editing = done else vm.tick(h.habit.id)
                                },
                                onOpen = { sheet = Sheet.ForHabit(h.habit.id) },
                                onConfirmDetected = { vm.confirmDetected(it.id) },
                                onRejectDetected = { vm.rejectDetected(it.id) },
                                onFinish = { vm.finish(it.id) },
                                onCancel = { vm.remove(it.id) },
                            )
                        }
                    }
                    ItemKind.TASK -> {
                        if (today.tasks.isEmpty()) item(key = "empty-t") {
                            EmptyState("📋", "No tasks right now", "A task is a one-off thing with a due date, like renewing a passport.", "Add a task") { onAdd(ItemKind.TASK) }
                        }
                        items(today.tasks, key = { "task-${it.task.id}" }) { t ->
                            TaskRow(
                                t = t,
                                onCheck = { if (t.isDone) reopening = t else vm.completeTask(t.task.id) },
                                onOpen = { sheet = Sheet.ForTask(t.task.id) },
                            )
                        }
                    }
                }
            }
        }
        celebration?.let { c -> CelebrationOverlay(c, onDone = { if (celebration?.id == c.id) celebration = null }) }
    }

    // ---- Panels and dialogs ----------------------------------------------------------------------
    val today = data
    when (val sh = sheet) {
        is Sheet.ForHabit -> today?.habits?.firstOrNull { it.habit.id == sh.habitId }?.let { h ->
            ModalBottomSheet(onDismissRequest = { sheet = null }) {
                HabitActions(
                    h = h,
                    timeOf = vm::timeOf,
                    onAction = { action ->
                        sheet = null
                        when (action) {
                            HabitAction.DONE_NOW -> vm.tick(h.habit.id)
                            HabitAction.START -> vm.start(h.habit.id)
                            HabitAction.OPEN_APP -> vm.openApp(context, h.habit)
                            HabitAction.CHANGE -> h.done.firstOrNull()?.let { editing = it }
                            HabitAction.CANT_TODAY -> context.startActivity(
                                com.ayushpant.habitquest.reminders.PopupActivity.promiseIntent(context, -1L, h.habit.id)
                            )
                            HabitAction.EDIT -> onEditHabit(h.habit.id)
                        }
                    },
                )
            }
        }
        is Sheet.ForTask -> today?.tasks?.firstOrNull { it.task.id == sh.taskId }?.let { t ->
            ModalBottomSheet(onDismissRequest = { sheet = null }) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.task.name, style = MaterialTheme.typography.titleLarge)
                    Text(taskStatus(t), style = MaterialTheme.typography.bodyMedium)
                    if (t.isDone) {
                        SheetButton("↩︎  Not done after all") { sheet = null; reopening = t }
                    } else {
                        SheetButton("✓  Mark done  (+${t.points})", primary = true) { sheet = null; vm.completeTask(t.task.id) }
                    }
                    SheetButton("✏️  Edit task") { sheet = null; onEditTask(t.task.id) }
                }
            }
        }
        null -> Unit
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

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

/** The top of Today: greeting, the day's ring with points, level, and one line of encouragement. */
@Composable
private fun Header(today: TodayData, onCollection: () -> Unit, onRewards: () -> Unit, onChooseStarter: (com.ayushpant.habitquest.rewards.CreatureDef) -> Unit) {
    val level = today.level
    val scheduled = today.habits.count { it.scheduledToday }
    val done = today.habits.count { it.scheduledToday && it.isDone }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(LocalTime.now().hour), style = MaterialTheme.typography.headlineSmall)
                Text(today.day.format(DAY_TITLE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill("🪙 ${today.coins}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.clickable(onClick = onRewards))
            Spacer(Modifier.width(8.dp))
            Pill("Lv ${level.number} · ${level.title}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DayRing(done, scheduled)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("${today.totalPoints} points", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            when {
                                scheduled == 0 -> "Nothing planned today"
                                done == scheduled -> "🌟 Perfect day! +${today.dailyBonus} bonus"
                                else -> "${scheduled - done} to go for a perfect day"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { level.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text("${level.toNext} to Lv ${level.number + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (today.balance.any { it.planned > 0 }) BalanceRow(today.balance, Modifier.padding(top = 14.dp))
            }
        }
        StarterCard(today.rewards, onChoose = onChooseStarter)
        CompanionCard(today.rewards, onClick = onCollection)
        val line = today.nudge
        Column {
            if (line != null && !line.startsWith("🌟")) Text(line, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("“${quoteOfTheDay(today.day.dayOfYear)}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The day's progress: a ring with "2/3" in the middle. */
@Composable
private fun DayRing(done: Int, total: Int) {
    val target = if (total == 0) 0f else done.toFloat() / total
    val progress by androidx.compose.animation.core.animateFloatAsState(target, label = "day")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val inset = 5.dp.toPx()
            val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(10.dp.toPx()))
            if (progress > 0f) drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arc, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Box(Modifier.clip(RoundedCornerShape(50)).then(modifier).background(background).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** A small label like "🕖 07:00–08:00" or "🔥 9". */
@Composable
private fun Chip(text: String, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/** The big round "done" button: empty ring when open, filled with ✓ when done. */
@Composable
private fun CheckCircle(done: Boolean, onClick: () -> Unit, contentDescription: String) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (done) color else Color.Transparent)
            .border(2.5.dp, if (done) color else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun EmptyState(emoji: String, title: String, text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 48.sp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(button) }
    }
}

/** One habit: the done button, the name with chips, and one short status line. Tap for actions. */
@Composable
private fun HabitRow(
    h: HabitToday,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
    onConfirmDetected: (Session) -> Unit,
    onRejectDetected: (Session) -> Unit,
    onFinish: (Session) -> Unit,
    onCancel: (Session) -> Unit,
) {
    val firstDone = h.done.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).alpha(if (h.scheduledToday || h.isDone) 1f else 0.55f).clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(h.isDone, onCheck, if (h.isDone) "${h.habit.name} done. Change or undo" else "Mark ${h.habit.name} done")
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.habit.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    when (h.habit.type) {
                        HabitType.APP -> Text("  🎧", style = MaterialTheme.typography.bodyMedium)
                        HabitType.ZONE -> Text("  📍", style = MaterialTheme.typography.bodyMedium)
                        HabitType.MANUAL -> Unit
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("🕖 ${h.habit.windowStart.format(HHMM)}–${h.habit.windowEnd.format(HHMM)}")
                    if (h.streakDays > 0) Chip("🔥 ${h.streakDays}" + if (h.freezeUsed) " ❄️" else "", Brand.Amber.copy(alpha = 0.18f), MaterialTheme.colorScheme.onSurface)
                }
                val (status, color) = habitStatus(h)
                if (status != null) Text(status, style = MaterialTheme.typography.bodySmall, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
                if (h.suggested != null && firstDone == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConfirmDetected(h.suggested) }) { Text("Confirm") }
                        OutlinedButton(onClick = { onRejectDetected(h.suggested) }) { Text("Not me") }
                    }
                }
                if (h.inProgress != null && firstDone == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onFinish(h.inProgress) }) { Text("Finish") }
                        OutlinedButton(onClick = { onCancel(h.inProgress) }) { Text("Cancel") }
                    }
                }
            }
            if (firstDone != null) Text("+${h.points}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

/** One short line under the habit name, or null when the chips say enough. Colour: null = default. */
@Composable
private fun habitStatus(h: HabitToday): Pair<String?, Color?> {
    val firstDone = h.done.firstOrNull()
    return when {
        firstDone != null -> {
            val kept = if (h.promise?.status == PromiseStatus.KEPT) " · 🤝 promise kept" else ""
            "✓ Done at ${timeOfMillis(firstDone.startTime).format(HHMM)}$kept" to MaterialTheme.colorScheme.primary
        }
        h.suggested != null -> {
            val s = h.suggested
            "🎧 ${(s.endTime!! - s.startTime) / 60_000} min detected (${timeOfMillis(s.startTime).format(HHMM)}). Was it you?" to null
        }
        h.inProgress != null && h.habit.type == HabitType.ZONE -> "📍 There since ${timeOfMillis(h.inProgress.startTime).format(HHMM)}" to MaterialTheme.colorScheme.primary
        h.inProgress != null -> "● Going since ${timeOfMillis(h.inProgress.startTime).format(HHMM)}" to MaterialTheme.colorScheme.primary
        h.promise != null -> "🤝 You promised: ${timeOfMillis(h.promise.dueAt).format(HHMM)} today" to MaterialTheme.colorScheme.tertiary
        h.skip != null -> (if (h.skip.promised) "Skipped today · promise made" else "Skipped today") to null
        !h.scheduledToday -> "Rest day" to null
        else -> null to null
    }
}

private enum class HabitAction { DONE_NOW, START, OPEN_APP, CHANGE, CANT_TODAY, EDIT }

/** The panel that opens when a habit is tapped: every action in plain words. */
@Composable
private fun HabitActions(h: HabitToday, timeOf: (Session) -> LocalTime, onAction: (HabitAction) -> Unit) {
    val done = h.done.firstOrNull()
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(h.habit.name, style = MaterialTheme.typography.titleLarge)
        Text(
            buildString {
                append("Planned ${h.habit.windowStart.format(HHMM)}–${h.habit.windowEnd.format(HHMM)}")
                if (h.streakDays > 0) append(" · 🔥 ${h.streakDays}-day streak")
                if (done != null) append(" · done at ${timeOf(done).format(HHMM)}, +${h.points}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done == null) {
            SheetButton("✓  I did it", primary = true) { onAction(HabitAction.DONE_NOW) }
            when (h.habit.type) {
                HabitType.APP -> SheetButton("🎧  Open the app") { onAction(HabitAction.OPEN_APP) }
                else -> if (h.inProgress == null) SheetButton("▶  Start now, finish later") { onAction(HabitAction.START) }
            }
            if (h.skip == null) SheetButton("🤝  Can't today") { onAction(HabitAction.CANT_TODAY) }
        } else {
            SheetButton("🕖  Change time or undo") { onAction(HabitAction.CHANGE) }
        }
        SheetButton("✏️  Edit habit") { onAction(HabitAction.EDIT) }
    }
}

@Composable
private fun SheetButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(text, style = MaterialTheme.typography.titleMedium) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(text, style = MaterialTheme.typography.bodyLarge) }
    }
}

private fun taskStatus(t: TaskToday): String = when {
    t.isDone && t.daysLate > 0 -> "Done ${dayCount(t.daysLate)} late · +${t.points}"
    t.isDone -> "Done on time · +${t.points}"
    t.daysLate > 0 -> "Overdue by ${dayCount(t.daysLate)} · now worth ${t.points}"
    t.daysLate == 0L -> "Due today${dueAt(t)} · worth ${t.points}"
    else -> "Due ${t.task.dueDate.format(DUE_DAY)}${dueAt(t)} · worth ${t.points}"
}

/** " at 18:00" when the task has a due time, else nothing. */
private fun dueAt(t: TaskToday): String = t.task.dueTime?.let { " at ${it.format(HHMM)}" } ?: ""

/** One task: the done button, the name, and its due date as a coloured chip. */
@Composable
private fun TaskRow(t: TaskToday, onCheck: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(t.isDone, onCheck, if (t.isDone) "${t.task.name} done. Undo" else "Mark ${t.task.name} done")
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.task.name, style = MaterialTheme.typography.titleMedium)
                when {
                    t.isDone -> Chip(if (t.daysLate > 0) "Done late" else "✓ On time", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    t.daysLate > 0 -> Chip("Overdue ${dayCount(t.daysLate)}", Brand.Coral.copy(alpha = 0.16f), Brand.Coral)
                    t.daysLate == 0L -> Chip("Due today", Brand.Amber.copy(alpha = 0.2f), MaterialTheme.colorScheme.onSurface)
                    else -> Chip("Due ${t.task.dueDate.format(DUE_DAY)}")
                }
            }
            Text(
                "+${t.points}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (t.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
