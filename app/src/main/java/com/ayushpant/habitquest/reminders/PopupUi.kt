package com.ayushpant.habitquest.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayushpant.habitquest.data.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DUE_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

fun popupTitle(model: PopupModel.Reminder): String = when (model.kind) {
    ReminderKind.LAST_CHANCE -> "${ReminderConfig.DEFAULT.lastChanceMinutes} minutes left for ${model.habitName}"
    ReminderKind.SNOOZE -> "Reminder: ${model.habitName}"
    else -> "Time for ${model.habitName}"
}

/**
 * The card shown in a popup. The same card is used over other apps (PopupService) and inside the
 * app (PopupActivity). [onFinished] runs after the user answers or closes it.
 */
@Composable
fun PopupCard(
    model: PopupModel,
    reminders: ReminderRepository,
    scope: CoroutineScope,
    openApp: () -> Unit,
    onFinished: () -> Unit,
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
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            when (model) {
                is PopupModel.Reminder -> ReminderContent(model, onClose = { act { reminders.close(model.logId) } }, act = ::act, reminders = reminders, openApp = openApp)
                is PopupModel.Checklist -> ChecklistContent(model, onClose = { act { reminders.close(model.logId) } }, onSave = { h, t ->
                    act { reminders.saveChecklist(model.logId, h, t) }
                })
                PopupModel.Test -> {
                    Header("Test popup", onClose = onFinished)
                    Text(
                        "If you can read this over another app, popups work.",
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Button(onClick = onFinished, modifier = Modifier.padding(top = 12.dp)) { Text("It works") }
                }
            }
        }
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
) {
    var promising by remember(model.logId) { mutableStateOf(false) }
    if (promising) {
        PromiseContent(model, onBack = { promising = false }, onPromise = {
            act { reminders.skip(model.logId, model.habitId, promised = true) }
        })
        return
    }

    Header(popupTitle(model), onClose)
    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Window ${model.windowStart.format(HHMM)}–${model.windowEnd.format(HHMM)}")
        if (model.promisedYesterday) {
            Text("Yesterday you promised yourself today. You've got this 💪", style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = { act { reminders.start(model.logId, model.habitId); openApp() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Start now")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { act { reminders.done(model.logId, model.habitId) } }, modifier = Modifier.weight(1f)) {
                Text("Already done")
            }
            OutlinedButton(onClick = { act { reminders.snooze(model.logId, model.habitId) } }, modifier = Modifier.weight(1f)) {
                Text("Snooze ${ReminderConfig.DEFAULT.snoozeMinutes} min")
            }
        }
        if (model.kind == ReminderKind.LAST_CHANCE) {
            TextButton(onClick = { promising = true }) { Text("Can't today") }
        } else {
            TextButton(onClick = { act { reminders.skip(model.logId, model.habitId, promised = false) } }) { Text("Skip today") }
        }
        if (model.tasks.isNotEmpty()) {
            HorizontalDivider()
            Text("Tasks to keep in mind", style = MaterialTheme.typography.titleSmall)
            for (task in model.tasks) Text("• ${task.name} — ${dueText(task)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Can't today": no guilt, a realistic goal, a promise. The button unlocks after a few seconds. */
@Composable
private fun PromiseContent(model: PopupModel.Reminder, onBack: () -> Unit, onPromise: () -> Unit) {
    var secondsLeft by remember(model.logId) { mutableIntStateOf(ReminderConfig.DEFAULT.promiseWaitSeconds) }
    LaunchedEffect(model.logId) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    Header("That's okay", onClose = onBack)
    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Don't feel guilty if it's not possible today.")
        Text("Setting realistic goals is better than forcing it.")
        Text("Promise yourself that you will do ${model.habitName} tomorrow.", style = MaterialTheme.typography.titleSmall)
        Button(onClick = onPromise, enabled = secondsLeft == 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (secondsLeft > 0) "I promise: tomorrow ($secondsLeft)" else "I promise: tomorrow")
        }
        TextButton(onClick = onBack) { Text("Back") }
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
