package com.ayushpant.habitquest.data

import android.util.Log
import androidx.room.withTransaction
import com.ayushpant.habitquest.balance.BalanceItem
import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.DimensionScore
import com.ayushpant.habitquest.balance.balance
import com.ayushpant.habitquest.balance.dimensionsOf
import com.ayushpant.habitquest.balance.maskOf
import com.ayushpant.habitquest.balance.suggestDimensions
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
import com.ayushpant.habitquest.scoring.weekStart
import com.ayushpant.habitquest.stats.Level
import com.ayushpant.habitquest.stats.NudgeHabit
import com.ayushpant.habitquest.stats.dailyNudge
import com.ayushpant.habitquest.stats.levelFor
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
    /** This week's freeze was used: the next miss resets the streak. */
    val freezeUsed: Boolean = false,
    /** Set when the user chose "Skip today" or "Can't today". */
    val skip: HabitSkip? = null,
    /** A promise for today (open or kept). */
    val promise: Promise? = null,
    /** A detected session waiting for the user's confirmation (app habits, spec 5.1). */
    val suggested: Session? = null,
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
    /** Every point ever earned: habits, daily bonuses and tasks. Gives the level. */
    val lifetimePoints: Int = 0,
) {
    val level: Level get() = levelFor(lifetimePoints)

    val nudge: String?
        get() = dailyNudge(
            habits.map {
                NudgeHabit(
                    it.habit.name, it.scheduledToday, it.isDone, it.habit.mbsMask,
                    promisedAt = it.promise?.takeIf { p -> p.status == PromiseStatus.OPEN }?.let { p ->
                        Instant.ofEpochMilli(p.dueAt).atZone(ZoneId.systemDefault()).toLocalTime().toString()
                    },
                )
            },
            balance,
            ScoringConfig.DEFAULT.dailyBonus,
        )

    val totalPoints get() = habits.sumOf { it.points } + dailyBonus + tasks.filter { it.isDone }.sumOf { it.points }

    /**
     * Today's Mind / Body / Soul meters. Planned: habits scheduled today (or done anyway) and tasks
     * due today or earlier (or done today). Tasks due later are not part of today.
     */
    val balance: List<DimensionScore>
        get() = balance(
            habits.filter { it.scheduledToday || it.isDone }
                .map { BalanceItem(it.habit.mbsMask, planned = true, done = it.isDone, points = it.points) } +
                tasks.filter { it.isDone || it.task.dueDate <= day }
                    .map { BalanceItem(it.task.mbsMask, planned = true, done = it.isDone, points = if (it.isDone) it.points else 0) }
        )
}

/** The creature line follows the habit's main dimension: Body → strong, Soul → calm, Mind → wise. */
fun creatureLineFor(mask: Int): CreatureLine {
    val dims = dimensionsOf(mask)
    return when {
        Dimension.BODY in dims -> CreatureLine.STRONG
        Dimension.SOUL in dims -> CreatureLine.CALM
        else -> CreatureLine.WISE
    }
}

/** Raw records for a date range. History and Summary build their views from it. */
data class RangeData(
    val from: LocalDate,
    val to: LocalDate,
    /** All habits, deleted ones too (for names). */
    val habits: List<Habit>,
    val sessions: List<Session>,
    val bonuses: List<DayBonus>,
    /** Tasks done or due in the range. */
    val tasks: List<Task>,
    val skips: List<HabitSkip>,
    val openTasks: List<Task> = emptyList(),
    val streaks: List<Streak> = emptyList(),
    /** Promises due in the range. */
    val promises: List<Promise> = emptyList(),
)

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
    /** Called after every change, so reminders can be planned again. */
    private val onChange: () -> Unit = {},
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun now(): Long = clock.millis()

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
                val row = streakByHabit[habit.id]
                val stored = row?.currentDays ?: 0
                HabitToday(
                    habit = habit,
                    scheduledToday = isScheduledOn(habit.daysMask, day),
                    inProgress = mine.firstOrNull { it.status == SessionStatus.SUGGESTED && it.endTime == null },
                    suggested = mine.lastOrNull { it.status == SessionStatus.SUGGESTED && it.endTime != null },
                    done = done,
                    streakDays = if (done.isNotEmpty()) stored + 1 else stored,
                    freezeUsed = row != null && row.freezeWeek == weekStart(day) && row.freezesUsed >= cfg.freezesPerWeek,
                )
            },
            dailyBonus = bonus?.points ?: 0,
            tasks = tasks
                .map { t ->
                    val doneOn = t.doneDay ?: day
                    TaskToday(
                        task = t,
                        daysLate = daysLate(t.dueDate, doneOn),
                        points = if (t.doneDay != null) t.points else taskPoints(cfg.basePoints, t.dueDate, day, cfg),
                    )
                }
                .sortedBy { it.isDone },
        )
    }.combine(db.skips().observeBetween(day, day)) { data, skips ->
        val byHabit = skips.associateBy { it.habitId }
        data.copy(habits = data.habits.map { it.copy(skip = byHabit[it.habit.id]) })
    }.combine(db.promises().observeDueOn(day)) { data, promises ->
        val byHabit = promises.filter { it.status != PromiseStatus.BROKEN }.associateBy { it.habitId }
        data.copy(habits = data.habits.map { it.copy(promise = byHabit[it.habit.id]) })
    }.combine(
        combine(db.sessions().observeTotalPoints(), db.dayBonus().observeTotalPoints(), db.tasks().observeTotalPoints()) { a, b, c -> a + b + c }
    ) { data, total -> data.copy(lifetimePoints = total) }

    suspend fun getHabit(id: Long): Habit? = db.habits().get(id)

    // ---- Habits --------------------------------------------------------------------------------

    /** Inserts a new habit (id = 0) or updates an existing one. Returns the id. */
    suspend fun saveHabit(habit: Habit): Long {
        val clean = habit.copy(basePoints = cfg.basePoints, creatureLine = creatureLineFor(habit.mbsMask)) // points fixed for everyone
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
        }.also { onChange() }
    }

    /** Soft delete: the habit disappears, but its sessions and points stay. */
    suspend fun deleteHabit(id: Long) {
        closePendingDays()
        db.withTransaction {
            val habit = db.habits().get(id) ?: return@withTransaction
            db.habits().update(habit.copy(active = false))
            rescoreDay(today())
        }
        onChange()
    }

    // ---- Tasks (one-off, with a due date) -------------------------------------------------------

    suspend fun getTask(id: Long): Task? = db.tasks().get(id)

    /** Inserts a new task (id = 0) or updates one. A done task is scored again for its new due date. */
    suspend fun saveTask(task: Task): Long {
        val clean = task.copy(basePoints = cfg.basePoints) // fixed for everyone
        return if (clean.id == 0L) {
            db.tasks().insert(clean.copy(createdDate = today()))
        } else {
            val points = clean.doneDay?.let { taskPoints(cfg.basePoints, clean.dueDate, it, cfg) } ?: 0
            db.tasks().update(clean.copy(points = points))
            clean.id
        }.also { onChange() }
    }

    /** Tick: the task is done today. */
    suspend fun completeTask(id: Long) {
        val t = db.tasks().get(id) ?: return
        if (t.doneDay != null) return
        val day = today()
        db.tasks().update(
            t.copy(doneDay = day, doneAt = clock.millis(), points = taskPoints(cfg.basePoints, t.dueDate, day, cfg))
        )
        onChange()
    }

    /** "Not done": the task is open again and its points are removed. */
    suspend fun reopenTask(id: Long) {
        val t = db.tasks().get(id) ?: return
        db.tasks().update(t.copy(doneDay = null, doneAt = null, points = 0))
        onChange()
    }

    /** Soft delete: the task disappears, but points it already got stay. */
    suspend fun deleteTask(id: Long) {
        val t = db.tasks().get(id) ?: return
        db.tasks().update(t.copy(active = false))
        onChange()
    }

    // ---- Sessions (manual habits) --------------------------------------------------------------

    /** "Start": records the start time. The session counts only after "Done". */
    suspend fun startSession(habitId: Long) = changeDay(today()) {
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
    suspend fun finishSession(sessionId: Long) = changeDay(today()) {
        val s = db.sessions().get(sessionId) ?: return@changeDay
        db.sessions().update(s.copy(endTime = clock.millis(), status = SessionStatus.CONFIRMED))
    }

    /** Tick: the user says "I did it", now. They can change the time afterwards. */
    suspend fun tickHabit(habitId: Long, source: SessionSource = SessionSource.MANUAL) = changeDay(today()) {
        val now = clock.millis()
        db.sessions().insert(
            Session(
                habitId = habitId, day = today(), startTime = now, endTime = now,
                status = SessionStatus.CONFIRMED, source = source, evidenceFound = false,
            )
        )
    }

    /**
     * Adds a confirmed session at [time] on [day]: a missed session added in History, or a tick
     * in the 11 PM checklist. A past day replays the habit's streak from that day on.
     */
    suspend fun addSession(habitId: Long, day: LocalDate, time: LocalTime, source: SessionSource) {
        val habit = db.habits().get(habitId) ?: return
        if (day < habit.createdDate || day > today()) return
        changeDay(day, habitId) {
            val start = minOf(millisOf(day, time), clock.millis())
            db.sessions().insert(
                Session(
                    habitId = habitId, day = day, startTime = start, endTime = start,
                    status = SessionStatus.CONFIRMED, source = source, evidenceFound = false,
                )
            )
        }
    }

    /** Changes the start time of a session on its own day. The end time moves with it. */
    suspend fun editSessionStart(sessionId: Long, newStart: LocalTime) {
        val s = db.sessions().get(sessionId) ?: return
        changeDay(s.day, s.habitId) {
            val startMillis = minOf(millisOf(s.day, newStart), clock.millis()) // never in the future
            val duration = s.endTime?.let { it - s.startTime }
            db.sessions().update(s.copy(startTime = startMillis, endTime = duration?.let { startMillis + it }))
        }
    }

    /** Removes a session (a mistaken tick, or a started session the user cancels). */
    suspend fun removeSession(sessionId: Long) {
        val s = db.sessions().get(sessionId) ?: return
        changeDay(s.day, s.habitId) { db.sessions().delete(sessionId) }
    }

    // ---- App habits: detected sessions (spec 5.1, 5.2) ------------------------------------------

    /** Packages of the active app habits: the apps whose audio the listener watches. */
    suspend fun watchedPackages(): Set<String> =
        db.habits().getActive().filter { it.type == HabitType.APP }.mapNotNull { it.sourcePackage }.toSet()

    /** The user tapped an app habit's tile: the source app is opened (spec 5.1 steps 1–4). */
    suspend fun recordLaunch(habitId: Long) {
        val habit = db.habits().get(habitId) ?: return
        logSignal(SignalKind.LAUNCH, "${habit.sourcePackage} habit=$habitId")
    }

    /**
     * The listener found a finished listening session. It becomes a SUGGESTED session: it gives no
     * points until the user confirms it (spec 2.1, 10). Returns the session id, or null when nothing
     * new is suggested (no app habit for that app, already done that day, or already suggested).
     */
    suspend fun suggestDetected(packageName: String, start: Long, end: Long, evidence: Boolean, source: SessionSource): Long? {
        logSignal(SignalKind.MEDIA, "session $packageName ${(end - start) / 60_000} min evidence=$evidence")
        val habit = db.habits().getActive().firstOrNull { it.type == HabitType.APP && it.sourcePackage == packageName } ?: return null
        val day = Instant.ofEpochMilli(start).atZone(clock.zone).toLocalDate()
        val sessions = db.sessions().getForDay(day).filter { it.habitId == habit.id }
        if (sessions.any { it.status == SessionStatus.CONFIRMED }) return null
        if (sessions.any { it.status == SessionStatus.SUGGESTED && it.endTime != null && it.startTime < end && it.endTime > start }) return null
        val id = db.sessions().insert(
            Session(
                habitId = habit.id, day = day, startTime = start, endTime = end,
                status = SessionStatus.SUGGESTED, source = source, evidenceFound = evidence,
            )
        )
        onChange()
        return id
    }

    /** "Confirm": the user says the detected session is right. Only now does it give points. */
    suspend fun confirmSuggestion(sessionId: Long) {
        val s = db.sessions().get(sessionId) ?: return
        if (s.status != SessionStatus.SUGGESTED) return
        changeDay(s.day, s.habitId) { db.sessions().update(s.copy(status = SessionStatus.CONFIRMED)) }
    }

    /** "Not done": the detection was wrong. Kept as REJECTED for the debug log, never counted. */
    suspend fun rejectSuggestion(sessionId: Long) {
        val s = db.sessions().get(sessionId) ?: return
        if (s.status != SessionStatus.SUGGESTED) return
        db.sessions().update(s.copy(status = SessionStatus.REJECTED))
        onChange()
    }

    suspend fun session(id: Long): Session? = db.sessions().get(id)

    /** "Skip today" / "Can't today". Stops today's reminders for the habit. It is still a miss. */
    suspend fun skipToday(habitId: Long, promised: Boolean) {
        db.skips().put(HabitSkip(habitId, today(), promised, clock.millis()))
        onChange()
    }

    /**
     * Planned days this habit was missed in a row before today (unplanned days are passed over).
     * Decides whether a promise needs the oath.
     */
    suspend fun priorConsecutiveMisses(habitId: Long): Int {
        val habit = db.habits().get(habitId) ?: return 0
        var misses = 0
        var day = today().minusDays(1)
        while (day > habit.createdDate) {
            if (isScheduledOn(habit.daysMask, day)) {
                if (db.sessions().getConfirmed(habitId, day).isNotEmpty()) break
                misses++
            }
            day = day.minusDays(1)
        }
        return misses
    }

    /**
     * "Can't today, I promise": today is skipped (no more reminders today), and the habit is promised
     * for [dueAt]. An earlier promise that is still open (e.g. for today) is broken by this.
     */
    suspend fun makePromise(habitId: Long, dueAt: LocalDateTime, method: OathMethod, oathText: String?) {
        db.withTransaction {
            for (old in db.promises().getDueOn(today()) + listOfNotNull(db.promises().getOpen(habitId))) {
                if (old.habitId == habitId && old.status == PromiseStatus.OPEN) db.promises().update(old.copy(status = PromiseStatus.BROKEN))
            }
            db.promises().insert(
                Promise(
                    habitId = habitId, madeOn = today(), dueDay = dueAt.toLocalDate(),
                    dueAt = dueAt.atZone(clock.zone).toInstant().toEpochMilli(), oathMethod = method, oathText = oathText,
                )
            )
            db.skips().put(HabitSkip(habitId, today(), promised = true, createdAt = clock.millis()))
        }
        onChange()
    }

    suspend fun openPromise(habitId: Long): Promise? = db.promises().getOpen(habitId)

    suspend fun undoSkip(habitId: Long, day: LocalDate) {
        db.skips().delete(habitId, day)
        onChange()
    }

    suspend fun skipsOn(day: LocalDate): List<HabitSkip> = db.skips().getForDay(day)

    private fun millisOf(day: LocalDate, time: LocalTime) = day.atTime(time).atZone(clock.zone).toInstant().toEpochMilli()

    /**
     * Runs [block], then fixes the points. For today (an open day) that is a rescore. For a closed
     * day the habit's streak is replayed from [day], because every later day depends on it.
     */
    private suspend fun changeDay(day: LocalDate, habitId: Long? = null, block: suspend () -> Unit) {
        closePendingDays() // the streak must be up to date before today is scored
        db.withTransaction {
            block()
            if (day >= today() || habitId == null) rescoreDay(today()) else replayHabit(habitId, day)
        }
        onChange()
    }

    // ---- Scoring -------------------------------------------------------------------------------

    /**
     * Recalculates the points of every confirmed session on [day], and the daily bonus.
     * Valid for the first day that is not closed yet (normally today), because it uses the
     * streak state after the previous day.
     */
    private suspend fun rescoreDay(day: LocalDate) {
        for (habit in db.habits().getActive().filter { it.createdDate <= day }) {
            rescoreHabitDay(habit, day, (db.streaks().get(habit.id) ?: Streak(habit.id)).toState())
        }
        updateDayBonus(day)
    }

    /** Scores one habit's confirmed sessions on [day], given the streak after the day before. */
    private suspend fun rescoreHabitDay(habit: Habit, day: LocalDate, closedStreak: StreakState) {
        val confirmed = db.sessions().getConfirmed(habit.id, day)
        // A promise for this day is kept as soon as the habit is done, also when the session is added
        // later in History. Undone again: open while the day lasts, broken once it is over.
        val promise = db.promises().getDueOn(day).filter { it.habitId == habit.id }.maxByOrNull { it.id }
        if (promise != null) {
            val status = when {
                confirmed.isNotEmpty() -> PromiseStatus.KEPT
                promise.status != PromiseStatus.KEPT -> promise.status // open stays open, broken stays broken
                day < today() -> PromiseStatus.BROKEN
                else -> PromiseStatus.OPEN
            }
            if (status != promise.status) db.promises().update(promise.copy(status = status))
        }
        val scores = scoreHabitDay(
            habit = HabitRules(cfg.basePoints, habit.windowStart, habit.windowEnd),
            closedStreak = closedStreak,
            confirmed = confirmed.map { SessionInput(it.id, localTimeOf(it.startTime), it.evidenceFound) },
            cfg = cfg,
            extraBonus = if (promise != null && confirmed.isNotEmpty()) cfg.promiseKeptBonus else 0,
        )
        for ((id, score) in scores) db.sessions().setPoints(id, score.points, score.bonusPoints)
    }

    private suspend fun updateDayBonus(day: LocalDate) {
        val habits = db.habits().getActive().filter { it.createdDate <= day }
        val scheduledIds = habits.filter { isScheduledOn(it.daysMask, day) }.map { it.id }.toSet()
        val doneIds = habits.filter { db.sessions().getConfirmed(it.id, day).isNotEmpty() }.map { it.id }.toSet()
        val bonus = dailyBonus(scheduledIds, doneIds, cfg)
        if (bonus > 0) db.dayBonus().upsert(DayBonus(day, bonus)) else db.dayBonus().delete(day)
    }

    /**
     * After a change on a closed day: walks the habit's streak from its creation day. Days before
     * [fromDay] only update the streak (their points stay as they are); [fromDay] and later days
     * are scored again, then today. Daily bonuses from [fromDay] on are checked again.
     */
    private suspend fun replayHabit(habitId: Long, fromDay: LocalDate) {
        val habit = db.habits().get(habitId) ?: return
        val lastClosed = db.appState().get(KEY_LAST_CLOSED)?.let(LocalDate::parse) ?: today().minusDays(1)
        var state = StreakState()
        var day = habit.createdDate
        while (day <= lastClosed) {
            if (day >= fromDay) rescoreHabitDay(habit, day, state)
            val outcome = dayOutcome(
                done = db.sessions().getConfirmed(habit.id, day).isNotEmpty(),
                scheduled = isScheduledOn(habit.daysMask, day),
                isCreationDay = habit.createdDate == day,
            )
            state = closeDay(state, day, outcome, cfg)
            day = day.plusDays(1)
        }
        db.streaks().upsert(state.toEntity(habit.id))
        rescoreHabitDay(habit, today(), state)
        var d = fromDay
        while (d <= today()) {
            updateDayBonus(d)
            d = d.plusDays(1)
        }
    }

    // ---- History and Summary -------------------------------------------------------------------

    /** Everything recorded between [from] and [to], for the History and Summary screens. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<RangeData> = combine(
        db.habits().observeAll(),
        db.sessions().observeConfirmedBetween(from, to),
        db.dayBonus().observeBetween(from, to),
        db.tasks().observeDoneOrDueBetween(from, to),
        db.skips().observeBetween(from, to),
    ) { habits, sessions, bonuses, tasks, skips ->
        RangeData(from, to, habits, sessions, bonuses, tasks, skips)
    }.combine(db.tasks().observeOpen()) { data, open -> data.copy(openTasks = open) }
        .combine(db.streaks().observeAll()) { data, streaks -> data.copy(streaks = streaks) }
        .combine(db.promises().observeDueBetween(from, to)) { data, promises -> data.copy(promises = promises) }

    suspend fun lastClosedDay(): LocalDate? = db.appState().get(KEY_LAST_CLOSED)?.let(LocalDate::parse)

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
        applyRuleChanges()
        backfillDimensions()
        db.signals().deleteOlderThan(clock.millis() - Duration.ofDays(SIGNAL_LOG_DAYS).toMillis())
        return closed
    }

    private suspend fun closeOneDay(day: LocalDate) {
        logSignal(SignalKind.TASK, "day_close $day")
        rescoreDay(day) // backstop: makes sure the points and the daily bonus are final
        for (p in db.promises().getDueOn(day).filter { it.status == PromiseStatus.OPEN }) {
            db.promises().update(p.copy(status = PromiseStatus.BROKEN))
        }
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

    /**
     * When the streak rules change (v0.4: a miss after the freeze resets to 0), the stored
     * streaks were made with the old rules. This walks every habit once with the new rules.
     * Points of past days stay as they are; only today is scored again.
     */
    private suspend fun applyRuleChanges() {
        val done = db.appState().get(KEY_RULES_VERSION)?.toIntOrNull() ?: 1
        if (done >= RULES_VERSION) return
        db.withTransaction {
            for (habit in db.habits().getActive()) replayHabit(habit.id, today())
            db.appState().put(AppState(KEY_RULES_VERSION, RULES_VERSION.toString()))
        }
        Log.i(LOG_TAG, "Streaks recalculated with rules v$RULES_VERSION")
    }

    /** Habits and tasks made before Mind / Body / Soul existed get a suggestion from their name, once. */
    private suspend fun backfillDimensions() {
        if (db.appState().get(KEY_MBS_BACKFILL) != null) return
        db.withTransaction {
            for (h in db.habits().getActive().filter { it.mbsMask == 0 }) {
                val mask = maskOf(suggestDimensions(h.name))
                if (mask != 0) db.habits().update(h.copy(mbsMask = mask, creatureLine = creatureLineFor(mask)))
            }
            for (t in db.tasks().getOpen().filter { it.mbsMask == 0 }) {
                val mask = maskOf(suggestDimensions(t.name))
                if (mask != 0) db.tasks().update(t.copy(mbsMask = mask))
            }
            db.appState().put(AppState(KEY_MBS_BACKFILL, "1"))
        }
    }

    suspend fun recordTaskRun(task: String) {
        db.appState().put(AppState("task_last_run.$task", Instant.now(clock).toString()))
    }

    suspend fun logSignal(kind: SignalKind, payload: String) {
        db.signals().insert(SignalLog(time = clock.millis(), kind = kind, payload = payload))
    }

    private companion object {
        const val KEY_LAST_CLOSED = "last_closed_day"
        const val KEY_RULES_VERSION = "streak_rules_version"
        const val KEY_MBS_BACKFILL = "mbs_backfill"
        /** Raise this when the streak rules change, so stored streaks are walked again. */
        const val RULES_VERSION = 2
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
