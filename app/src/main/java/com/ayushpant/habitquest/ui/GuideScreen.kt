package com.ayushpant.habitquest.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayushpant.habitquest.reminders.ReminderConfig
import com.ayushpant.habitquest.scoring.ScoringConfig

/** Where a guide topic's "Try it" button goes. */
enum class GuideAction { ADD_HABIT, ADD_TASK, PLACES, SUMMARY, HISTORY, SETTINGS }

private data class Topic(
    val emoji: String,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val action: Pair<String, GuideAction>? = null,
)

/** The guide's content. Numbers come from the config, so the guide always matches the app. */
private fun topics(cfg: ScoringConfig = ScoringConfig.DEFAULT, rc: ReminderConfig = ReminderConfig.DEFAULT): List<Topic> = listOf(
    Topic(
        "🌱", "Getting started", "Add a habit, then tick it when you've done it.",
        listOf(
            "On Today, tap + Habit. Type a name or tap a quick pick like 🧘 Meditate.",
            "Choose when you plan to do it (Morning, Evening, …) and on which days.",
            "Tap Add habit. It appears on Today.",
            "When you've done it, tap the big round button. That's it: it's done and you get points.",
            "Tap anywhere else on a habit for more: start now, change the time, can't today, edit.",
        ),
        "Add a habit" to GuideAction.ADD_HABIT,
    ),
    Topic(
        "⭐", "Points", "Every habit and task is worth the same ${cfg.basePoints} points. Extras on top.",
        listOf(
            "Do a habit in its planned time: full points. Up to ${cfg.nearWindowMinutes} min before or after: ×${cfg.timingNearWindow}. Another time that day: ×${cfg.timingOtherTime}.",
            "Your streak multiplies points: 7 days ×1.2, 14 days ×1.5, 30 days ×2.",
            "Do every habit planned for today: +${cfg.dailyBonus} (a perfect day 🌟).",
            "Come back after a missed day: +${cfg.comebackBonus}. Keep a promise: +${cfg.promiseKeptBonus}.",
            "When the app notices you did it (audio played, or you stayed at the gym long enough): ×${cfg.evidenceFound}.",
            "Points add up to levels and titles (Lv 4 · Starter). Your level never goes down for a miss.",
        ),
    ),
    Topic(
        "🔥", "Streaks and the freeze", "Days in a row. One free miss a week.",
        listOf(
            "Each planned day you do a habit, its streak grows by 1.",
            "Days you didn't plan the habit (rest days) never break a streak.",
            "Miss a planned day? The first miss of the week uses a free freeze ❄️: the streak stays.",
            "Another miss that week starts the streak again from 0. Your best streak is still remembered.",
            "Forgot to tick? Fix it in History: the streak is repaired too.",
        ),
    ),
    Topic(
        "📋", "Tasks", "One-off things with a due date.",
        listOf(
            "On Today, switch to Tasks and tap + Task. Pick when it's due: Today, Tomorrow, This weekend, …",
            "Tick it when it's done. On time: ×${cfg.taskOnTime} points.",
            "Late still counts, a little less each day (never 0). An open task is never a penalty.",
        ),
        "Add a task" to GuideAction.ADD_TASK,
    ),
    Topic(
        "🧠", "Mind · Body · Soul", "See which part of you your habits feed.",
        listOf(
            "Each habit or task feeds Mind 🧠, Body 💪 and/or Soul ✨. It's suggested from the name (meditate → Mind + Soul, gym → Body + Mind); you can change it.",
            "The meters on Today show how much of today's plan you've done for each.",
            "Summary shows them for the week or month, and which one could use more.",
        ),
    ),
    Topic(
        "🔔", "Reminders", "Gentle popups at your time.",
        listOf(
            "When a habit's time starts, a popup asks: Start now, Already done, Snooze ${rc.snoozeMinutes} min, or Can't today.",
            "${rc.lastChanceMinutes} minutes before the time ends, a last-chance reminder comes.",
            "At most a few reminders per habit a day (you choose how many in Settings).",
            "At ${rc.checklistTime} an evening checklist lets you tick anything you did but didn't record.",
            "No popups in quiet hours (Settings). During a call or while navigating you get a quiet notification with a short buzz, and the popup comes after.",
            "Each popup gives a short buzz, unless your phone is on silent.",
            "Drag the bar at the top of a popup to move it; it remembers the place.",
            "For popups over other apps, allow \"Display over other apps\" in Settings.",
        ),
        "Open Settings" to GuideAction.SETTINGS,
    ),
    Topic(
        "🤝", "Can't today? Make a promise", "No guilt: choose when you'll do it instead.",
        listOf(
            "On a reminder (or a habit's panel), tap Can't today.",
            "Pick tomorrow or another day, and a time.",
            "Missed it before today too? Then type or say the promise out loud to confirm it.",
            "A reminder comes at exactly that time. Do it that day: +${cfg.promiseKeptBonus} and a celebration.",
            "\"Not possible tomorrow either\" isn't an option… try pressing it 😉",
        ),
    ),
    Topic(
        "🎧", "Meditation (or any) app", "The app notices when you use it and asks you to confirm.",
        listOf(
            "Edit the habit → How → 🎧 App → choose the app (e.g. Medito).",
            "In Settings → Track a habit with an app, tap each Turn it on button.",
            "Use the app: from the habit, or open it directly. About a minute after the audio stops, you're asked \"Did you do it?\".",
            "Tap Confirm. Nothing is ever checked off without your tap.",
            "With Usage access, sessions without audio are noticed within the hour too.",
        ),
        "Set it up in Settings" to GuideAction.SETTINGS,
    ),
    Topic(
        "📍", "Places (like the gym)", "Arrive, and the app asks if you're doing it.",
        listOf(
            "Settings → Track a habit at a place: allow location, then \"Allow all the time\".",
            "Add the place (best while you're there): Use where I am now, or search an address.",
            "Edit the habit → How → 📍 Place → choose the place.",
            "Stay ${cfg.zoneDwellMinutes} minutes: \"Are you doing it now?\" Tap Yes. Leave: confirm the session.",
            "${cfg.zoneEvidenceMinutes} minutes or more there: ×${cfg.evidenceFound}. Driving past does nothing.",
        ),
        "Open Places" to GuideAction.PLACES,
    ),
    Topic(
        "📊", "Your progress (Summary)", "How you're doing this day, week or month.",
        listOf(
            "Summary has three parts: Mind · Body · Soul, Highlights (swipe sideways) and Overview.",
            "The big rings: green = habits done, blue = tasks on time, orange = perfect days. Points in the middle.",
            "The calendar shows rings for each day. Switch to Habit rings to see each habit's day.",
            "Tap a day to see it. Use the arrows to go back in time.",
            "Tap the Badges card in Highlights to see all badges and the next one to earn.",
        ),
        "Open Summary" to GuideAction.SUMMARY,
    ),
    Topic(
        "🗓️", "History: fix anything", "Every day, with what you did and missed.",
        listOf(
            "Tap a done habit to change its time or mark it not done.",
            "Forgot to tick something? Tap Add on a missed habit and choose the time.",
            "Points and streaks are fixed from that day on, automatically.",
        ),
        "Open History" to GuideAction.HISTORY,
    ),
    Topic(
        "🔒", "Your privacy", "Everything stays on your phone.",
        listOf(
            "Your habits, points and places are stored only on this phone.",
            "Notification access is used only to see when an app plays audio. Notifications are not read.",
            "Location is watched by your phone's own location service, only for your saved places.",
        ),
    ),
    Topic(
        "🛠️", "Something isn't working?", "Quick fixes.",
        listOf(
            "A reminder didn't come: Settings → Reminder check shows what happened to each one.",
            "A switch is greyed out in Notification access: App info → ⋮ → Allow restricted settings.",
            "On some phones, battery saving stops reminders: set Habit Quest's battery use to Unrestricted.",
            "A detected session was wrong: tap Not me. It's never counted.",
        ),
        "Open Settings" to GuideAction.SETTINGS,
    ),
)

/** "How to use Habit Quest": every feature in plain steps, searchable, with buttons to try it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit, onAction: (GuideAction) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val all = topics()
    val shown = all.filter {
        query.isBlank() || listOf(it.title, it.summary).plus(it.steps).any { t -> t.contains(query.trim(), ignoreCase = true) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to use Habit Quest") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search, e.g. streak, gym, promise") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (shown.isEmpty()) item { Text("Nothing found. Try another word.", style = MaterialTheme.typography.bodyLarge) }
            items(shown, key = { it.title }) { t ->
                val expanded = open == t.title || query.isNotBlank()
                Card(Modifier.fillMaxWidth().animateContentSize().clickable { open = if (open == t.title) null else t.title }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.emoji, fontSize = 26.sp, modifier = Modifier.width(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium)
                                Text(t.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (expanded) {
                            t.steps.forEachIndexed { i, step ->
                                Row {
                                    Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                    Text(step, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            t.action?.let { (label, action) ->
                                OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.padding(top = 4.dp)) { Text("$label →") }
                            }
                        }
                    }
                }
            }
        }
    }
}
