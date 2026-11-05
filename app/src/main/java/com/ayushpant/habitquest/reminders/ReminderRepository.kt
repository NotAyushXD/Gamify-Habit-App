package com.ayushpant.habitquest.reminders

import androidx.room.withTransaction
import com.ayushpant.habitquest.data.AppDatabase
import com.ayushpant.habitquest.data.AppState
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.ReminderKind
import com.ayushpant.habitquest.data.ReminderLog
import com.ayushpant.habitquest.data.ReminderResult
import com.ayushpant.habitquest.data.SessionSource
import com.ayushpant.habitquest.data.SessionStatus
import com.ayushpant.habitquest.data.Settings
import com.ayushpant.habitquest.data.SignalKind
import com.ayushpant.habitquest.data.SignalLog
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.daysLate
import com.ayushpant.habitquest.scoring.isScheduledOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A line for an open task on a popup. */
data class TaskLine(val id: Long, val name: String, val dueDate: LocalDate, val daysLate: Long)

/** A line for an open habit on the checklist. */
data class HabitLine(val id: Long, val name: String, val windowStart: LocalTime, val windowEnd: LocalTime)

/** What a popup shows. The UI draws it; the buttons call [ReminderRepository]. */
sealed interface PopupModel {
    val logId: Long

    data class Reminder(
        override val logId: Long,
        val habitId: Long,
        val habitName: String,
        val windowStart: LocalTime,
        val windowEnd: LocalTime,
        val kind: ReminderKind,
        /** The user tapped "I promise" for this habit yesterday. */
        val promisedYesterday: Boolean,
        /** Open tasks, only on the first reminder popup of the day. */
        val tasks: List<TaskLine>,
    ) : PopupModel

    data class Checklist(
        override val logId: Long,
        val habits: List<HabitLine>,
        val tasks: List<TaskLine>,
    ) : PopupModel

    /** From the "Test popup" button in Settings. */
    data object Test : PopupModel {
        override val logId = -1L
    }
}

/**
 * Reminders and popups (spec 6). Decides with the pure rules in ReminderPlanner.kt, records every
 * popup in reminder_log, and turns popup buttons into sessions, snoozes and skips.
 */
class ReminderRepository(
    private val db: AppDatabase,
    private val habits: HabitRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val config: ReminderConfig = ReminderConfig.DEFAULT,
    /** Called after every change, so the next alarm can be set again. */
    private val onChange: () -> Unit = {},
) {
    private fun now(): LocalDateTime = LocalDateTime.now(clock)
    private fun toMillis(t: LocalDateTime) = t.atZone(clock.zone).toInstant().toEpochMilli()
    private fun toLocal(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), clock.zone)

    // ---- Settings ------------------------------------------------------------------------------

    suspend fun settings(): Settings = db.settings().get() ?: Settings()

    fun observeSettings(): Flow<Settings> = db.settings().observe().map { it ?: Settings() }

    suspend fun saveSettings(settings: Settings) {
        db.settings().put(settings.copy(id = 1, maxReminders = settings.maxReminders.coerceIn(1, 5)))
        onChange()
    }

    private fun Settings.toRules() = ReminderRules(quietEnabled, quietStart, quietEnd, checklistEnabled, maxReminders, config)

    // ---- Planning ------------------------------------------------------------------------------

    private suspend fun planToday(): Pair<List<Planned>, ReminderRules> {
        val day = now().toLocalDate()
        val rules = settings().toRules()
        val skipped = db.skips().getForDay(day).map { it.habitId }.toSet()
        val doneIds = db.sessions().getForDay(day).filter { it.status == SessionStatus.CONFIRMED }.map { it.habitId }.toSet()
        val planHabits = db.habits().getActive().map {
            PlanHabit(it.id, it.windowStart, it.windowEnd, isScheduledOn(it.daysMask, day), it.id in doneIds, it.id in skipped)
        }
        val logs = db.reminders().getForDay(day).map { PlanLog(it.id, it.habitId, it.kind, toLocal(it.dueAt), it.result) }
        return planDay(day, planHabits, logs, rules) to rules
    }

    /** When the next alarm must ring. After the last popup of the day: just after midnight, to plan the new day. */
    suspend fun nextAlarmMillis(): Long {
        val now = now()
        val next = nextAlarm(now, planToday().first) ?: now.toLocalDate().plusDays(1).atTime(0, 1)
        return toMillis(next)
    }

    /** Retry time during a phone call (spec 6.2: show the popup when the call ends). */
    fun retryMillis(): Long = clock.millis() + config.callRetrySeconds * 1000

    /**
     * Takes the popups that are due now: records them as shown (or suppressed) and returns the
     * reminder_log ids to show. Call it when the alarm rings.
     */
    suspend fun takeDue(): List<Long> {
        val now = now()
        val day = now.toLocalDate()
        val (planned, rules) = planToday()
        val decision = decide(now, planned, rules)
        val shown = db.withTransaction {
            db.signals().insert(SignalLog(time = clock.millis(), kind = SignalKind.TASK, payload = "reminders ${decision.show.size} shown ${decision.suppress.size} suppressed"))
            for (p in decision.suppress) record(p, day, ReminderResult.SUPPRESSED, shownAt = null)
            val ids = mutableListOf<Long>()
            for (p in decision.show) {
                if (p.kind == ReminderKind.CHECKLIST && checklistModel(0).let { it.habits.isEmpty() && it.tasks.isEmpty() }) {
                    record(p, day, ReminderResult.SUPPRESSED, shownAt = null) // nothing left open: no popup
                    continue
                }
                ids += record(p, day, ReminderResult.SHOWN, shownAt = clock.millis())
            }
            // The first habit reminder of the day also lists the open tasks.
            val firstKey = KEY_TASKS_POPUP + day
            if (db.appState().get(firstKey) == null) {
                val first = ids.firstOrNull { id -> db.reminders().get(id)?.habitId != null }
                if (first != null) db.appState().put(AppState(firstKey, first.toString()))
            }
            ids
        }
        return shown
    }

    private suspend fun record(p: Planned, day: LocalDate, result: ReminderResult, shownAt: Long?): Long {
        val existing = p.logId?.let { db.reminders().get(it) }
        return if (existing != null) {
            db.reminders().update(existing.copy(result = result, shownAt = shownAt))
            existing.id
        } else {
            db.reminders().insert(ReminderLog(habitId = p.habitId, day = day, kind = p.kind, dueAt = toMillis(p.at), shownAt = shownAt, result = result))
        }
    }

    // ---- Popup contents ------------------------------------------------------------------------

    suspend fun popupFor(logId: Long): PopupModel? {
        if (logId == PopupModel.Test.logId) return PopupModel.Test
        val log = db.reminders().get(logId) ?: return null
        if (log.kind == ReminderKind.CHECKLIST) return checklistModel(logId)
        val habit = db.habits().get(log.habitId ?: return null) ?: return null
        val promised = db.skips().getForDay(log.day.minusDays(1)).any { it.habitId == habit.id && it.promised }
        val withTasks = db.appState().get(KEY_TASKS_POPUP + log.day) == logId.toString()
        return PopupModel.Reminder(
            logId = logId,
            habitId = habit.id,
            habitName = habit.name,
            windowStart = habit.windowStart,
            windowEnd = habit.windowEnd,
            kind = log.kind,
            promisedYesterday = promised,
            tasks = if (withTasks) db.tasks().getOpen().take(config.tasksOnFirstPopup).map { it.toLine(log.day) } else emptyList(),
        )
    }

    /** Habits scheduled today that are not done or skipped, and open tasks due today or earlier. */
    private suspend fun checklistModel(logId: Long): PopupModel.Checklist {
        val day = now().toLocalDate()
        val skipped = db.skips().getForDay(day).map { it.habitId }.toSet()
        val doneIds = db.sessions().getForDay(day).filter { it.status == SessionStatus.CONFIRMED }.map { it.habitId }.toSet()
        val open = db.habits().getActive().filter {
            isScheduledOn(it.daysMask, day) && it.id !in doneIds && it.id !in skipped
        }
        return PopupModel.Checklist(
            logId = logId,
            habits = open.map { HabitLine(it.id, it.name, it.windowStart, it.windowEnd) },
            tasks = db.tasks().getOpen().filter { it.dueDate <= day }.map { it.toLine(day) },
        )
    }

    private fun Task.toLine(day: LocalDate) = TaskLine(id, name, dueDate, daysLate(dueDate, day))

    // ---- Popup buttons -------------------------------------------------------------------------

    private suspend fun resolve(logId: Long, result: ReminderResult) {
        val log = db.reminders().get(logId) ?: return
        db.reminders().update(log.copy(result = result))
        onChange()
    }

    /** "Start now". */
    suspend fun start(logId: Long, habitId: Long) {
        habits.startSession(habitId)
        resolve(logId, ReminderResult.STARTED)
    }

    /** "Already done": the user confirms it now. */
    suspend fun done(logId: Long, habitId: Long) {
        habits.tickHabit(habitId)
        resolve(logId, ReminderResult.DONE)
    }

    /** "Snooze 15 min": a new reminder waits; it counts toward the daily limit when it shows. */
    suspend fun snooze(logId: Long, habitId: Long) {
        val day = now().toLocalDate()
        db.reminders().insert(
            ReminderLog(
                habitId = habitId, day = day, kind = ReminderKind.SNOOZE,
                dueAt = clock.millis() + config.snoozeMinutes * 60_000, result = ReminderResult.PENDING,
            )
        )
        resolve(logId, ReminderResult.SNOOZED)
    }

    /** "Skip today", or "I promise: tomorrow" ([promised] = true). No more reminders today. */
    suspend fun skip(logId: Long, habitId: Long, promised: Boolean) {
        habits.skipToday(habitId, promised)
        resolve(logId, if (promised) ReminderResult.PROMISED else ReminderResult.SKIPPED)
    }

    /** The close button. */
    suspend fun close(logId: Long) {
        if (logId != PopupModel.Test.logId) resolve(logId, ReminderResult.CLOSED)
    }

    /**
     * Checklist "Save". A tick is the user's confirmation (spec 5). The session gets the start of
     * the window as its time: the user did it, and the checklist must not cost timing points.
     */
    suspend fun saveChecklist(logId: Long, habitIds: Set<Long>, taskIds: Set<Long>) {
        val day = now().toLocalDate()
        for (id in habitIds) {
            val habit = db.habits().get(id) ?: continue
            habits.addSession(id, day, habit.windowStart, SessionSource.CHECKLIST)
        }
        for (id in taskIds) habits.completeTask(id)
        resolve(logId, if (habitIds.isEmpty() && taskIds.isEmpty()) ReminderResult.CLOSED else ReminderResult.DONE)
    }

    private companion object {
        const val KEY_TASKS_POPUP = "tasks_popup."
    }
}
