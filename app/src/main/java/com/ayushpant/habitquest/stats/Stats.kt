package com.ayushpant.habitquest.stats

import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitSkip
import com.ayushpant.habitquest.data.RangeData
import com.ayushpant.habitquest.data.Session
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.daysLate
import com.ayushpant.habitquest.scoring.isScheduledOn
import com.ayushpant.habitquest.scoring.weekStart
import java.time.LocalDate

/*
 * Pure functions that turn raw records (RangeData) into the History and Summary views.
 * No Android code, so they are unit tested on the Mac.
 */

// ---- Periods ------------------------------------------------------------------------------------

enum class Period { DAY, WEEK, MONTH }

/** The first and last day of the [period] that contains [anchor]. Weeks start on Monday. */
fun periodRange(period: Period, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
    Period.DAY -> anchor to anchor
    Period.WEEK -> weekStart(anchor).let { it to it.plusDays(6) }
    Period.MONTH -> anchor.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
}

/** Moves [anchor] by [steps] periods (negative = back in time). */
fun shiftPeriod(period: Period, anchor: LocalDate, steps: Long): LocalDate = when (period) {
    Period.DAY -> anchor.plusDays(steps)
    Period.WEEK -> anchor.plusWeeks(steps)
    Period.MONTH -> anchor.plusMonths(steps)
}

fun LocalDate.daysUntil(last: LocalDate): List<LocalDate> =
    generateSequence(this) { it.plusDays(1) }.takeWhile { it <= last }.toList()

// ---- Summary ------------------------------------------------------------------------------------

data class HabitSummary(
    val habit: Habit,
    /** Scheduled days in the range so far (today counts only once it is done). */
    val scheduled: Int,
    /** Scheduled days on which it was done. */
    val done: Int,
    /** Days it was done although not scheduled. */
    val extra: Int,
    val currentStreak: Int,
    val bestStreak: Int,
) {
    val rate: Double? get() = if (scheduled == 0) null else done.toDouble() / scheduled
}

/** One day in the Summary chart. [scheduled] = 0 means nothing to do, or a future day. */
data class DayCell(val day: LocalDate, val scheduled: Int, val done: Int) {
    val rate: Double? get() = if (scheduled == 0) null else done.toDouble() / scheduled
}

data class TaskSummary(
    /** Tasks done in the range. */
    val completed: Int,
    val completedOnTime: Int,
    /** Average days late, of the tasks done late. Null if none were late. */
    val averageDaysLate: Double?,
    val points: Int,
    /** Tasks whose due date is in the range. */
    val dueInRange: Int,
    /** Of those, the ones done by their due date. */
    val dueInRangeOnTime: Int,
    val openNow: Int,
    val overdueNow: Int,
)

data class Summary(
    val from: LocalDate,
    val to: LocalDate,
    val points: Int,
    val habits: List<HabitSummary>,
    val days: List<DayCell>,
    val tasks: TaskSummary,
) {
    val scheduled get() = habits.sumOf { it.scheduled }
    val done get() = habits.sumOf { it.done }
    val habitRate: Double? get() = if (scheduled == 0) null else done.toDouble() / scheduled
}

/**
 * Does [day] count for [habit]? A done day always counts. A not-done day counts once it is over,
 * except the day the habit was created (same rule as streaks: that day is never a miss).
 */
private fun counts(habit: Habit, day: LocalDate, today: LocalDate, done: Boolean) = when {
    day < habit.createdDate || day > today -> false
    done -> true
    else -> day < today && day != habit.createdDate
}

fun buildSummary(data: RangeData, today: LocalDate): Summary {
    val active = data.habits.filter { it.active }
    val doneDays: Map<Long, Set<LocalDate>> =
        data.sessions.groupBy { it.habitId }.mapValues { (_, list) -> list.map { it.day }.toSet() }
    val streaks = data.streaks.associateBy { it.habitId }
    val lastDay = minOf(data.to, today)
    val days = data.from.daysUntil(lastDay)

    val habitSummaries = active.map { habit ->
        val done = doneDays[habit.id].orEmpty()
        var scheduled = 0
        var doneScheduled = 0
        var extra = 0
        for (day in days) {
            val isDone = day in done
            if (!counts(habit, day, today, isDone)) continue
            if (isScheduledOn(habit.daysMask, day)) {
                scheduled++
                if (isDone) doneScheduled++
            } else if (isDone) {
                extra++
            }
        }
        val stored = streaks[habit.id]
        val doneToday = today in done
        HabitSummary(
            habit = habit,
            scheduled = scheduled,
            done = doneScheduled,
            extra = extra,
            currentStreak = (stored?.currentDays ?: 0) + if (doneToday) 1 else 0,
            bestStreak = maxOf(stored?.bestDays ?: 0, (stored?.currentDays ?: 0) + if (doneToday) 1 else 0),
        )
    }

    val cells = data.from.daysUntil(data.to).map { day ->
        var scheduled = 0
        var done = 0
        for (habit in active) {
            val isDone = day in doneDays[habit.id].orEmpty()
            if (!counts(habit, day, today, isDone) || !isScheduledOn(habit.daysMask, day)) continue
            scheduled++
            if (isDone) done++
        }
        DayCell(day, scheduled, done)
    }

    val inRange = { d: LocalDate? -> d != null && d >= data.from && d <= data.to }
    val doneTasks = data.tasks.filter { inRange(it.doneDay) }
    val late = doneTasks.map { daysLate(it.dueDate, it.doneDay!!) }.filter { it > 0 }
    val dueTasks = data.tasks.filter { inRange(it.dueDate) }
    val taskSummary = TaskSummary(
        completed = doneTasks.size,
        completedOnTime = doneTasks.size - late.size,
        averageDaysLate = if (late.isEmpty()) null else late.average(),
        points = doneTasks.sumOf { it.points },
        dueInRange = dueTasks.size,
        dueInRangeOnTime = dueTasks.count { it.doneDay != null && daysLate(it.dueDate, it.doneDay) <= 0 },
        openNow = data.openTasks.size,
        overdueNow = data.openTasks.count { it.dueDate < today },
    )

    return Summary(
        from = data.from,
        to = data.to,
        points = data.sessions.sumOf { it.points } + data.bonuses.sumOf { it.points } + taskSummary.points,
        habits = habitSummaries,
        days = cells,
        tasks = taskSummary,
    )
}

// ---- History ------------------------------------------------------------------------------------

sealed interface HistoryEntry {
    data class HabitDone(val session: Session, val habitName: String) : HistoryEntry
    /** A scheduled habit with no session on a past day. [skip] is set if the user skipped it. */
    data class HabitMissed(val habit: Habit, val skip: HabitSkip?) : HistoryEntry
    data class TaskDone(val task: Task) : HistoryEntry
}

data class HistoryDay(
    val day: LocalDate,
    val entries: List<HistoryEntry>,
    val dailyBonus: Int,
) {
    val points: Int
        get() = dailyBonus + entries.sumOf {
            when (it) {
                is HistoryEntry.HabitDone -> it.session.points
                is HistoryEntry.TaskDone -> it.task.points
                is HistoryEntry.HabitMissed -> 0
            }
        }
}

/** Days from newest to oldest. Misses are listed for past days only; today is on the Today screen. */
fun buildHistory(data: RangeData, today: LocalDate): List<HistoryDay> {
    val names = data.habits.associate { it.id to it.name }
    val sessionsByDay = data.sessions.groupBy { it.day }
    val tasksByDay = data.tasks.filter { it.doneDay != null }.groupBy { it.doneDay!! }
    val bonusByDay = data.bonuses.associate { it.day to it.points }
    val skips = data.skips.associateBy { it.habitId to it.day }

    return data.from.daysUntil(minOf(data.to, today)).reversed().map { day ->
        val sessions = sessionsByDay[day].orEmpty()
        val doneHabitIds = sessions.map { it.habitId }.toSet()
        val missed = if (day >= today) emptyList() else data.habits
            .filter {
                it.active && day > it.createdDate && isScheduledOn(it.daysMask, day) && it.id !in doneHabitIds
            }
            .map { HistoryEntry.HabitMissed(it, skips[it.id to day]) }
        HistoryDay(
            day = day,
            entries = sessions.map { HistoryEntry.HabitDone(it, names[it.habitId] ?: "Deleted habit") } +
                missed +
                tasksByDay[day].orEmpty().map { HistoryEntry.TaskDone(it) },
            dailyBonus = bonusByDay[day] ?: 0,
        )
    }
}
