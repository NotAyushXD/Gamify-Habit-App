package com.ayushpant.habitquest.data

import android.util.Log
import androidx.room.withTransaction
import com.ayushpant.habitquest.scoring.DayOutcome
import com.ayushpant.habitquest.scoring.HabitRules
import com.ayushpant.habitquest.scoring.ScoringConfig
import com.ayushpant.habitquest.scoring.SessionInput
import com.ayushpant.habitquest.scoring.StreakState
import com.ayushpant.habitquest.scoring.closeDay
import com.ayushpant.habitquest.scoring.dailyBonus
import com.ayushpant.habitquest.scoring.dayOutcome
import com.ayushpant.habitquest.scoring.isScheduledOn
import com.ayushpant.habitquest.scoring.daysLate
import com.ayushpant.habitquest.scoring.scoreHabitDay
import com.ayushpant.habitquest.scoring.taskPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

const val LOG_TAG = "HabitQuest"

/** One habit as the Today screen shows it. */
data class HabitToday(
    val habit: Habit,
    val scheduledToday: Boolean,
    val inProgress: Session?,
    /** Confirmed sessions today, earliest first. */
    val done: List<Session>,
    /** Streak including today, once today is done. */
    val streakDays: Int,
) {
    val isDone get() = done.isNotEmpty()
    val points get() = done.sumOf { it.points }
}

/** One task as the Today screen shows it. */
data class TaskToday(
    val task: Task,
    /** Days past the due date: when it was done, or today if it is still open. 0 or less = on time. */
    val daysLate: Long,
    /** Points it got (done), or points it is worth if done today (open). */
    val points: Int,
) {
    val isDone get() = task.doneDay != null
}

data class TodayData(
    val day: LocalDate,
    val habits: List<HabitToday>,
    val dailyBonus: Int,
    /** Open tasks (soonest due first), then tasks done today. */
    val tasks: List<TaskToday> = emptyList(),
) {
    val totalPoints get() = habits.sumOf { it.points } + dailyBonus + tasks.filter { it.isDone }.sumOf { it.points }
}

/**
 * The only class that changes the database. Screens and background tasks call it.
 *
 * Points for a day are always recalculated from that day's confirmed sessions ([rescoreDay]),
 * so editing or removing a session can never leave wrong points behind.
 */
class HabitRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val cfg: ScoringConfig = ScoringConfig.DEFAULT,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun localTimeOf(epochMillis: Long): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(clock.zone).toLocalTime()

    // ---- Reading -------------------------------------------------------------------------------

    fun observeToday(day: LocalDate): Flow<TodayData> = combine(
        db.habits().observeActive(),
        db.sessions().observeForDay(day),
        db.streaks().observeAll(),
        db.dayBonus().observe(day),
        db.tasks().observeForDay(day),
    ) { habits, sessions, streaks, bonus, tasks ->
        val streakByHabit = streaks.associateBy { it.habitId }
        TodayData(
            day = day,
            habits = habits.map { habit ->
                val mine = sessions.filter { it.habitId == habit.id }
                val done = mine.filter { it.status == SessionStatus.CONFIRMED }
                val stored = streakByHabit[habit.id]?.currentDays ?: 0
                HabitToday(
                    habit = habit,
                    scheduledToday = isScheduledOn(habit.daysMask, day),
                    inProgress = mine.firstOrNull { it.status == SessionStatus.SUGGESTED && it.endTime == null },
                    done = done,
                    streakDays = if (done.isNotEmpty()) stored + 1 else stored,
                )
            },
            dailyBonus = bonus?.points ?: 0,
            tasks = tasks
                .map { t ->
                    val doneOn = t.doneDay ?: day
                    TaskToday(
                        task = t,
                        daysLate = daysLate(t.dueDate, doneOn),
                        points = if (t.doneDay != null) t.points else taskPoints(t.basePoints, t.dueDate, day, cfg),
                    )
                }
                .sortedBy { it.isDone },
        )
    }

    suspend fun getHabit(id: Long): Habit? = db.habits().get(id)

    // ---- Habits --------------------------------------------------------------------------------

    /** Inserts a new habit (id = 0) or updates an existing one. Returns the id. */
    suspend fun saveHabit(habit: Habit): Long {
        val clean = habit.copy(basePoints = habit.basePoints.coerceIn(cfg.minBasePoints, cfg.maxBasePoints))
        closePendingDays()
        return db.withTransaction {
            val id = if (clean.id == 0L) {
                db.habits().insert(clean.copy(createdDate = today())).also { db.streaks().upsert(Streak(habitId = it)) }
            } else {
                db.habits().update(clean)
                clean.id
            }
            rescoreDay(today()) // the window or base points can change today's points
            id
        }
    }

    /** Soft delete: the habit disappears, but its sessions and points stay. */
    suspend fun deleteHabit(id: Long) {
        closePendingDays()
        db.withTransaction {
            val habit = db.habits().get(id) ?: return@withTransaction
            db.habits().update(habit.copy(active = false))
            rescoreDay(today())
        }
    }

    // ---- Tasks (one-off, with a due date) -------------------------------------------------------

    suspend fun getTask(id: Long): Task? = db.tasks().get(id)

    /** Inserts a new task (id = 0) or updates one. A done task is scored again for its new due date. */
    suspend fun saveTask(task: Task): Long {
        val clean = task.copy(basePoints = task.basePoints.coerceIn(cfg.minBasePoints, cfg.maxBasePoints))
        return if (clean.id == 0L) {
            db.tasks().insert(clean.copy(createdDate = today()))
        } else {
            val points = clean.doneDay?.let { taskPoints(clean.basePoints, clean.dueDate, it, cfg) } ?: 0
            db.tasks().update(clean.copy(points = points))
            clean.id
        }
    }

    /** Tick: the task is done today. */
    suspend fun completeTask(id: Long) {
        val t = db.tasks().get(id) ?: return
        if (t.doneDay != null) return
        val day = today()
        db.tasks().update(
            t.copy(doneDay = day, doneAt = clock.millis(), points = taskPoints(t.basePoints, t.dueDate, day, cfg))
        )
    }

    /** "Not done": the task is open again and its points are removed. */
    suspend fun reopenTask(id: Long) {
        val t = db.tasks().get(id) ?: return
        db.tasks().update(t.copy(doneDay = null, doneAt = null, points = 0))
    }

    /** Soft delete: the task disappears, but points it already got stay. */
    suspend fun deleteTask(id: Long) {
        val t = db.tasks().get(id) ?: return
        db.tasks().update(t.copy(active = false))
    }

    // ---- Sessions (manual habits) --------------------------------------------------------------

    /** "Start": records the start time. The session counts only after "Done". */
    suspend fun startSession(habitId: Long) = changeToday {
        val now = clock.millis()
        val open = db.sessions().getForDay(today())
            .any { it.habitId == habitId && it.status == SessionStatus.SUGGESTED && it.endTime == null }
        if (!open) {
            db.sessions().insert(
                Session(
                    habitId = habitId, day = today(), startTime = now, endTime = null,
                    status = SessionStatus.SUGGESTED, source = SessionSource.MANUAL, evidenceFound = false,
                )
            )
        }
    }

    /** "Done" on a started session. This tap is the user's confirmation. */
    suspend fun finishSession(sessionId: Long) = changeToday {
        val s = db.sessions().get(sessionId) ?: return@changeToday
        db.sessions().update(s.copy(endTime = clock.millis(), status = SessionStatus.CONFIRMED))
    }

    /** Tick: the user says "I did it", now. They can change the time afterwards. */
    suspend fun tickHabit(habitId: Long) = changeToday {
        val now = clock.millis()
        db.sessions().insert(
            Session(
                habitId = habitId, day = today(), startTime = now, endTime = now,
                status = SessionStatus.CONFIRMED, source = SessionSource.MANUAL, evidenceFound = false,
            )
        )
    }

    /** Changes the start time of a session on its own day. The end time moves with it. */
    suspend fun editSessionStart(sessionId: Long, newStart: LocalTime) = changeToday {
        val s = db.sessions().get(sessionId) ?: return@changeToday
        val startMillis = minOf(
            s.day.atTime(newStart).atZone(clock.zone).toInstant().toEpochMilli(),
            clock.millis(), // a session cannot start in the future
        )
        val duration = s.endTime?.let { it - s.startTime }
        db.sessions().update(s.copy(startTime = startMillis, endTime = duration?.let { startMillis + it }))
    }

    /** Removes a session (a mistaken tick, or a started session the user cancels). */
    suspend fun removeSession(sessionId: Long) = changeToday {
        db.sessions().delete(sessionId)
    }

    private suspend fun changeToday(block: suspend () -> Unit) {
        closePendingDays() // the streak must be up to date before today is scored
        db.withTransaction {
            block()
            rescoreDay(today())
        }
    }

    // ---- Scoring -------------------------------------------------------------------------------

    /**
     * Recalculates the points of every confirmed session on [day], and the daily bonus.
     * Valid for the first day that is not closed yet (normally today), because it uses the
     * streak state after the previous day.
     */
    private suspend fun rescoreDay(day: LocalDate) {
        val habits = db.habits().getActive().filter { it.createdDate <= day }
        val doneIds = mutableSetOf<Long>()
        for (habit in habits) {
            val confirmed = db.sessions().getConfirmed(habit.id, day)
            if (confirmed.isNotEmpty()) doneIds += habit.id
            val scores = scoreHabitDay(
                habit = HabitRules(habit.basePoints, habit.windowStart, habit.windowEnd),
                closedStreak = (db.streaks().get(habit.id) ?: Streak(habit.id)).toState(),
                confirmed = confirmed.map { SessionInput(it.id, localTimeOf(it.startTime), it.evidenceFound) },
                cfg = cfg,
            )
            for ((id, score) in scores) db.sessions().setPoints(id, score.points, score.bonusPoints)
        }
        val scheduledIds = habits.filter { isScheduledOn(it.daysMask, day) }.map { it.id }.toSet()
        val bonus = dailyBonus(scheduledIds, doneIds, cfg)
        if (bonus > 0) db.dayBonus().upsert(DayBonus(day, bonus)) else db.dayBonus().delete(day)
    }

    // ---- Day close (spec 6.3) ------------------------------------------------------------------

    /**
     * Closes every day before today that is not closed yet. Safe to call at any time and
     * from anywhere: the worker at 00:05, app start, and before each change. This is how the
     * day-close task "runs again if it did not run at 00:05" (spec 12.1).
     *
     * Returns the number of days closed.
     */
    suspend fun closePendingDays(): Int {
        var closed = 0
        while (true) {
            // One transaction per day, so two callers at the same time cannot close a day twice.
            val didClose = db.withTransaction {
                val today = today()
                val last = db.appState().get(KEY_LAST_CLOSED)?.let(LocalDate::parse)
                if (last == null) {
                    // First run after install: there is nothing before today to close.
                    db.appState().put(AppState(KEY_LAST_CLOSED, today.minusDays(1).toString()))
                    return@withTransaction false
                }
                val day = last.plusDays(1)
                if (day >= today) return@withTransaction false
                closeOneDay(day)
                db.appState().put(AppState(KEY_LAST_CLOSED, day.toString()))
                true
            }
            if (!didClose) break
            closed++
        }
        // The streak changed, so today's sessions (if any) get the new multiplier.
        if (closed > 0) db.withTransaction { rescoreDay(today()) }
        db.signals().deleteOlderThan(clock.millis() - Duration.ofDays(SIGNAL_LOG_DAYS).toMillis())
        return closed
    }

    private suspend fun closeOneDay(day: LocalDate) {
        logSignal(SignalKind.TASK, "day_close $day")
        rescoreDay(day) // backstop: makes sure the points and the daily bonus are final
        for (habit in db.habits().getActive()) {
            if (habit.createdDate > day) continue
            val outcome = dayOutcome(
                done = db.sessions().getConfirmed(habit.id, day).isNotEmpty(),
                scheduled = isScheduledOn(habit.daysMask, day),
                isCreationDay = habit.createdDate == day,
            )
            val before = db.streaks().get(habit.id) ?: Streak(habit.id)
            val after = closeDay(before.toState(), day, outcome, cfg)
            db.streaks().upsert(after.toEntity(habit.id))
            if (outcome == DayOutcome.MISSED) {
                Log.i(LOG_TAG, "Miss: ${habit.name} on $day, streak ${before.currentDays} -> ${after.currentDays}")
            }
        }
    }

    suspend fun recordTaskRun(task: String) {
        db.appState().put(AppState("task_last_run.$task", Instant.now(clock).toString()))
    }

    private suspend fun logSignal(kind: SignalKind, payload: String) {
        db.signals().insert(SignalLog(time = clock.millis(), kind = kind, payload = payload))
    }

    private companion object {
        const val KEY_LAST_CLOSED = "last_closed_day"
        const val SIGNAL_LOG_DAYS = 14L
    }
}

private fun Streak.toState() = StreakState(
    currentDays = currentDays, bestDays = bestDays, lastDoneDate = lastDoneDate,
    freezeWeek = freezeWeek, freezesUsed = freezesUsed, comebackPending = comebackPending,
)

private fun StreakState.toEntity(habitId: Long) = Streak(
    habitId = habitId, currentDays = currentDays, bestDays = bestDays, lastDoneDate = lastDoneDate,
    freezeWeek = freezeWeek, freezesUsed = freezesUsed, comebackPending = comebackPending,
)
