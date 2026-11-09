package com.ayushpant.habitquest.reminders

import androidx.room.withTransaction
import com.ayushpant.habitquest.data.AppDatabase
import com.ayushpant.habitquest.data.AppState
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.OathMethod
import com.ayushpant.habitquest.data.PromiseStatus
import com.ayushpant.habitquest.promise.oathRequired
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** For the promise screen. [logId] < 0 when it was opened from the Today screen, not a reminder. */
data class PromiseContext(
    val logId: Long,
    val habitId: Long,
    val habitName: String,
    val suggestedTime: LocalTime,
    val priorMisses: Int,
    val oathRequired: Boolean,
)

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
        /** False when another snooze would go over the daily reminder limit. */
        val canSnooze: Boolean = true,
        /** The promised time, on a PROMISE reminder. */
        val promiseAt: LocalTime? = null,
        /** The current streak (after the last closed day), shown on the popup: "don't lose 🔥 8". */
        val streakDays: Int = 0,
    ) : PopupModel

    data class Checklist(
        override val logId: Long,
        val habits: List<HabitLine>,
        val tasks: List<TaskLine>,
    ) : PopupModel

    /** Spec 5.1: "the habit, the start time, the end time, and the points", for a detected session. */
    data class Confirm(
        val sessionId: Long,
        val habitName: String,
        val start: LocalTime,
        val end: LocalTime,
        val evidence: Boolean,
        /** Found later in the app-usage record (spec 5.2), not live. [appName] is the app that was open. */
        val catchUp: Boolean = false,
        val appName: String? = null,
    ) : PopupModel {
        override val logId = -2L
        val minutes: Long get() = java.time.Duration.between(start, end).toMinutes()
    }

    /** Spec 5.3 step 4: "At the gym. Are you working out now?" */
    data class ZoneCheck(
        val habitId: Long,
        val habitName: String,
        val zoneName: String,
        /** When the dwell was detected (epoch ms); the session starts 5 min before it. */
        val dwellAt: Long,
    ) : PopupModel {
        override val logId = -3L
    }

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

    /** The reminder rules from the user's settings (quiet hours, limits). */
    suspend fun rules(): ReminderRules = settings().toRules()

    private fun Settings.toRules() = ReminderRules(quietEnabled, quietStart, quietEnd, checklistEnabled, maxReminders, config)

    /** One line of the reminder check in Settings. */
    data class LogLine(val dueAt: LocalDateTime, val what: String, val kind: ReminderKind, val result: ReminderResult)

    /** The latest reminders, newest first, for the reminder check in Settings. */
    fun observeRecentLog(limit: Int = 20): Flow<List<LogLine>> =
        combine(db.reminders().observeRecent(limit), db.habits().observeAll()) { logs, all ->
            val names = all.associate { it.id to it.name }
            logs.map { LogLine(toLocal(it.dueAt), it.habitId?.let { id -> names[id] } ?: "Checklist", it.kind, it.result) }
        }

    /** All active habits, for "Link a habit" in the Settings guide. */
    fun observeActiveHabits(): Flow<List<com.ayushpant.habitquest.data.Habit>> = db.habits().observeActive()

    /** Active habits done with an app, for the set-up guide in Settings. */
    fun observeAppHabits(): Flow<List<com.ayushpant.habitquest.data.Habit>> =
        db.habits().observeActive().map { list -> list.filter { it.type == com.ayushpant.habitquest.data.HabitType.APP && it.sourcePackage != null } }

    /** Where the user last dragged the popup (pixels from the top), or null for the default place. */
    suspend fun popupY(): Int? = db.appState().get(KEY_POPUP_Y)?.toIntOrNull()

    suspend fun savePopupY(y: Int) = db.appState().put(AppState(KEY_POPUP_Y, y.toString()))

    // ---- Planning ------------------------------------------------------------------------------

    private suspend fun planToday(): Pair<List<Planned>, ReminderRules> {
        val day = now().toLocalDate()
        val rules = settings().toRules()
        val skipped = db.skips().getForDay(day).map { it.habitId }.toSet()
        val doneIds = db.sessions().getForDay(day).filter { it.status == SessionStatus.CONFIRMED }.map { it.habitId }.toSet()
        val promises = db.promises().getDueOn(day).filter { it.status == PromiseStatus.OPEN }.associateBy { it.habitId }
        val planHabits = db.habits().getActive().map {
            PlanHabit(
                it.id, it.windowStart, it.windowEnd, isScheduledOn(it.daysMask, day), it.id in doneIds, it.id in skipped,
                promiseAt = promises[it.id]?.let { p -> toLocal(p.dueAt).toLocalTime() },
            )
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
        val todayLogs = db.reminders().getForDay(log.day).filter { it.habitId == habit.id }
            .map { PlanLog(it.id, it.habitId, it.kind, toLocal(it.dueAt), it.result) }
        val canSnooze = log.kind != ReminderKind.LAST_CHANCE &&
            startAndSnoozeLeft(habit.windowStart, habit.windowEnd, todayLogs, settings().toRules()) > 0
        return PopupModel.Reminder(
            logId = logId,
            habitId = habit.id,
            habitName = habit.name,
            windowStart = habit.windowStart,
            windowEnd = habit.windowEnd,
            kind = log.kind,
            promisedYesterday = promised,
            tasks = if (withTasks) db.tasks().getOpen().take(config.tasksOnFirstPopup).map { it.toLine(log.day) } else emptyList(),
            canSnooze = canSnooze,
            promiseAt = if (log.kind == ReminderKind.PROMISE) toLocal(log.dueAt).toLocalTime() else null,
            streakDays = db.streaks().get(habit.id)?.currentDays ?: 0,
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

    /** The confirmation popup for a detected session, or null if it was already answered. */
    suspend fun confirmationFor(sessionId: Long): PopupModel.Confirm? {
        val s = habits.session(sessionId) ?: return null
        if (s.status != com.ayushpant.habitquest.data.SessionStatus.SUGGESTED || s.endTime == null) return null
        val habit = db.habits().get(s.habitId) ?: return null
        return PopupModel.Confirm(
            s.id, habit.name, toLocal(s.startTime).toLocalTime(), toLocal(s.endTime).toLocalTime(), s.evidenceFound,
            catchUp = s.source == com.ayushpant.habitquest.data.SessionSource.CATCH_UP, appName = habit.sourcePackage,
        )
    }

    /** True now if popups must wait (quiet hours). A waiting suggestion stays on the Today tile. */
    suspend fun inQuietHoursNow(): Boolean = inQuietHours(now().toLocalTime(), settings().toRules())

    suspend fun catchUpSince(): Long? = db.appState().get(KEY_CATCH_UP_SINCE)?.toLongOrNull()

    suspend fun setCatchUpSince(millis: Long) = db.appState().put(AppState(KEY_CATCH_UP_SINCE, millis.toString()))



    suspend fun zoneCheckFor(habitId: Long, dwellAt: Long): PopupModel.ZoneCheck? {
        val habit = db.habits().get(habitId) ?: return null
        val zone = habit.zoneId?.let { db.zones().get(it) } ?: return null
        return PopupModel.ZoneCheck(habit.id, habit.name, zone.name, dwellAt)
    }

    suspend fun startZoneSession(habitId: Long, dwellAt: Long) = habits.startZoneSession(habitId, dwellAt)

    suspend fun confirmDetected(sessionId: Long) = habits.confirmSuggestion(sessionId)
    suspend fun rejectDetected(sessionId: Long) = habits.rejectSuggestion(sessionId)

    /** What the promise screen needs: the habit, a suggested time, and whether the oath is needed. */
    suspend fun promiseContext(logId: Long, habitId: Long): PromiseContext? {
        val habit = db.habits().get(habitId) ?: return null
        val misses = habits.priorConsecutiveMisses(habitId)
        return PromiseContext(logId, habit.id, habit.name, habit.windowStart, misses, oathRequired(misses))
    }

    /** The promise is made: today is skipped and the habit is promised for [dueAt]. */
    suspend fun promise(logId: Long, habitId: Long, dueAt: LocalDateTime, method: OathMethod, oathText: String?) {
        habits.makePromise(habitId, dueAt, method, oathText)
        if (logId >= 0) resolve(logId, ReminderResult.PROMISED) else onChange()
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
        const val KEY_POPUP_Y = "popup_y"
        const val KEY_CATCH_UP_SINCE = "catch_up_since"
    }
}
