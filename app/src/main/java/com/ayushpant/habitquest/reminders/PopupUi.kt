package com.ayushpant.habitquest.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ayushpant.habitquest.data.ReminderKind
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DUE_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

fun popupTitle(model: PopupModel.Reminder): String = when (model.kind) {
    ReminderKind.PROMISE -> "You promised yourself: ${model.habitName}"
    ReminderKind.LAST_CHANCE -> "${ReminderConfig.DEFAULT.lastChanceMinutes} minutes left for ${model.habitName}"
    ReminderKind.SNOOZE -> "Reminder: ${model.habitName}"
    ReminderKind.EARLY -> "Coming up at ${model.windowStart.format(HHMM)}: ${model.habitName}"
    else -> "Time for ${model.habitName}"
}

fun taskPopupTitle(model: PopupModel.TaskReminder): String =
    if (model.early) "Coming up: ${model.taskName}" else "Due now: ${model.taskName}"

/** "Due today at 18:00", "Due tomorrow at 09:30", "Due Fri 13 Nov at 09:30". */
fun taskDueLine(model: PopupModel.TaskReminder, today: java.time.LocalDate = java.time.LocalDate.now()): String {
    val day = when (model.dueDate) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> model.dueDate.format(DUE_DAY)
    }
    return "Due $day at ${model.dueTime.format(HHMM)}"
}

/** Moving a popup over other apps: called with the finger's movement in pixels, and when it lets go. */
class DragHandler(val onDrag: (dx: Float, dy: Float) -> Unit, val onDragEnd: () -> Unit)

/**
 * The card shown in a popup. The same card is used over other apps (PopupService) and inside the
 * app (PopupActivity). [onFinished] runs after the user answers or closes it. [openPromise] opens the
 * promise screen ("Can't today"); the promise screen records the answer itself.
 */
@Composable
fun PopupCard(
    model: PopupModel,
    reminders: ReminderRepository,
    scope: CoroutineScope,
    openApp: () -> Unit,
    openPromise: (logId: Long, habitId: Long) -> Unit,
    onFinished: () -> Unit,
    drag: DragHandler? = null,
) {
    fun act(block: suspend () -> Unit) {
        scope.launch {
            block()
            onFinished()
        }
    }
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)) {
            if (drag != null) DragBar(drag)
            when (model) {
                is PopupModel.Reminder -> ReminderContent(
                    model,
                    onClose = { act { reminders.close(model.logId) } },
                    act = ::act,
                    reminders = reminders,
                    openApp = openApp,
                    onCantToday = {
                        openPromise(model.logId, model.habitId)
                        onFinished()
                    },
                )
                is PopupModel.Checklist -> ChecklistContent(model, onClose = { act { reminders.close(model.logId) } }, onSave = { h, t ->
                    act { reminders.saveChecklist(model.logId, h, t) }
                })
                is PopupModel.TaskReminder -> {
                    Header(taskPopupTitle(model), onClose = { act { reminders.close(model.logId) } })
                    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(taskDueLine(model), style = MaterialTheme.typography.bodyLarge)
                        Text("Done on the due date: on-time points. The time is only for this reminder.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { act { reminders.taskDone(model.logId, model.taskId) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Done ✓")
                        }
                        OutlinedButton(onClick = { act { reminders.close(model.logId) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("OK, later")
                        }
                    }
                }
                is PopupModel.ZoneCheck -> {
                    Header("At ${model.zoneName}", onClose = onFinished)
                    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you doing ${model.habitName} now?", style = MaterialTheme.typography.titleMedium)
                        Text("Say yes and the session starts from when you arrived. It ends when you leave.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { act { reminders.startZoneSession(model.habitId, model.dwellAt) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Yes 💪")
                        }
                        OutlinedButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("No") }
                    }
                }
                is PopupModel.Confirm -> {
                    val title = if (model.catchUp) "Did you do ${model.habitName} at ${model.start.format(HHMM)}?" else "Did you do ${model.habitName}?"
                    Header(title, onClose = onFinished) // closing keeps it on Today
                    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${model.minutes} min, ${model.start.format(HHMM)}–${model.end.format(HHMM)}",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            when {
                                model.catchUp -> "Found in your app use: the app was open then. Confirm if it was this habit."
                                model.evidence -> "The audio played the whole time: ×1.2 evidence bonus."
                                else -> "Short session: no evidence bonus, it still counts."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { act { reminders.confirmDetected(model.sessionId) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Confirm ✓")
                        }
                        OutlinedButton(onClick = { act { reminders.rejectDetected(model.sessionId) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Not done")
                        }
                        Text("Times wrong? Close this and change them on the Today screen.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                PopupModel.Test -> {
                    Header("Test popup", onClose = onFinished)
                    Text(
                        "If you can read this over another app, popups work. Drag the bar at the top to move it.",
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Button(onClick = onFinished, modifier = Modifier.padding(top = 12.dp)) { Text("It works") }
                }
            }
        }
    }
}

/** The grab bar at the top of a popup over other apps. Drag it to move the popup; the place is remembered. */
@Composable
private fun DragBar(drag: DragHandler) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
            .height(24.dp)
            .pointerInput(drag) {
                detectDragGestures(
                    onDragEnd = drag.onDragEnd,
                    onDragCancel = drag.onDragEnd,
                ) { change, amount ->
                    change.consume()
                    drag.onDrag(amount.x, amount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline))
    }
}

/** Title and the close button (spec 6.2: every popup has a button that closes it). */
@Composable
private fun Header(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
    }
}

@Composable
private fun ReminderContent(
    model: PopupModel.Reminder,
    onClose: () -> Unit,
    act: (suspend () -> Unit) -> Unit,
    reminders: ReminderRepository,
    openApp: () -> Unit,
    onCantToday: () -> Unit,
) {
    Header(popupTitle(model), onClose)
    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.promiseAt != null) {
            Text(
                "You said you would do it at ${model.promiseAt.format(HHMM)} today. Keep your word: " +
                    "+${ScoringConfig.DEFAULT.promiseKeptBonus} bonus 🤝",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text("Window ${model.windowStart.format(HHMM)}–${model.windowEnd.format(HHMM)}")
        }
        if (model.streakDays > 0) {
            Text("🔥 ${model.streakDays}-day streak. Keep it alive today.", style = MaterialTheme.typography.bodyMedium)
        }
        if (model.promisedYesterday && model.promiseAt == null) {
            Text("Yesterday you promised yourself today. You've got this 💪", style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = { act { reminders.start(model.logId, model.habitId); openApp() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Start now")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { act { reminders.done(model.logId, model.habitId) } }, modifier = Modifier.weight(1f)) {
                Text("Already done")
            }
            if (model.canSnooze) {
                OutlinedButton(onClick = { act { reminders.snooze(model.logId, model.habitId) } }, modifier = Modifier.weight(1f)) {
                    Text("Snooze ${ReminderConfig.DEFAULT.snoozeMinutes} min")
                }
            }
        }
        TextButton(onClick = onCantToday) { Text("Can't today") }
        if (model.tasks.isNotEmpty()) {
            HorizontalDivider()
            Text("Tasks to keep in mind", style = MaterialTheme.typography.titleSmall)
            for (task in model.tasks) Text("• ${task.name} — ${dueText(task)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChecklistContent(
    model: PopupModel.Checklist,
    onClose: () -> Unit,
    onSave: (Set<Long>, Set<Long>) -> Unit,
) {
    var habits by remember(model.logId) { mutableStateOf(emptySet<Long>()) }
    var tasks by remember(model.logId) { mutableStateOf(emptySet<Long>()) }
    Header("11 PM checklist", onClose)
    Column(
        Modifier.padding(end = 12.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Tick what you did today. A tick counts as done.", style = MaterialTheme.typography.bodyMedium)
        if (model.habits.isNotEmpty()) {
            Text("Habits", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            for (h in model.habits) {
                CheckRow(h.name, "${h.windowStart.format(HHMM)}–${h.windowEnd.format(HHMM)}", h.id in habits) {
                    habits = if (h.id in habits) habits - h.id else habits + h.id
                }
            }
        }
        if (model.tasks.isNotEmpty()) {
            Text("Tasks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            for (t in model.tasks) {
                CheckRow(t.name, dueText(t), t.id in tasks) {
                    tasks = if (t.id in tasks) tasks - t.id else tasks + t.id
                }
            }
        }
    }
    Button(onClick = { onSave(habits, tasks) }, modifier = Modifier.padding(top = 12.dp, end = 12.dp).fillMaxWidth()) {
        Text("Save")
    }
}

@Composable
private fun CheckRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun dueText(t: TaskLine) = when {
    t.daysLate > 1 -> "overdue by ${t.daysLate} days"
    t.daysLate == 1L -> "overdue by 1 day"
    t.daysLate == 0L -> "due today"
    else -> "due ${t.dueDate.format(DUE_DAY)}"
}
