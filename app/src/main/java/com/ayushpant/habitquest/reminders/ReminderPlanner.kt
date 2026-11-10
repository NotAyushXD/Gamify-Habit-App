package com.ayushpant.habitquest.reminders

import com.ayushpant.habitquest.data.ReminderKind
import com.ayushpant.habitquest.data.ReminderResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Pure reminder rules (spec 6.1, 6.2): which popups are due, which must not show, and when the
 * next alarm is needed. No Android code, so it is unit tested on the Mac.
 */

/** The user's settings that the rules need. */
data class ReminderRules(
    val quietEnabled: Boolean,
    val quietStart: LocalTime,
    val quietEnd: LocalTime,
    val checklistEnabled: Boolean,
    val maxPerHabit: Int,
    val config: ReminderConfig = ReminderConfig.DEFAULT,
)

/** One habit, as the planner sees it today. */
data class PlanHabit(
    val id: Long,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val scheduled: Boolean,
    val done: Boolean,
    val skipped: Boolean,
    /** The time the user promised to do it today, if there is an open promise. */
    val promiseAt: LocalTime? = null,
    /** Minutes before the window start for an early reminder, if the user chose one. */
    val remindBefore: Int? = null,
)

/** An open task with a due time, as the planner sees it. */
data class PlanTask(
    val id: Long,
    val due: LocalDateTime,
    /** Minutes before [due] for an early reminder, if the user chose one. */
    val remindBefore: Int? = null,
)

/** A reminder_log row for today. */
data class PlanLog(
    val id: Long,
    val habitId: Long?,
    val kind: ReminderKind,
    val dueAt: LocalDateTime,
    val result: ReminderResult,
    val taskId: Long? = null,
)

/** A popup that has not been handled yet. [logId] is set for a waiting snooze. */
data class Planned(
    val habitId: Long?,
    val kind: ReminderKind,
    val at: LocalDateTime,
    val expiresAt: LocalDateTime,
    val logId: Long? = null,
    val taskId: Long? = null,
)

data class Decision(val show: List<Planned>, val suppress: List<Planned>)

/** Quiet hours can cross midnight (23:30–06:00). Start = end means no quiet hours. */
fun inQuietHours(t: LocalTime, rules: ReminderRules): Boolean {
    if (!rules.quietEnabled || rules.quietStart == rules.quietEnd) return false
    return if (rules.quietStart < rules.quietEnd) t >= rules.quietStart && t < rules.quietEnd
    else t >= rules.quietStart || t < rules.quietEnd
}

private val SHOWN_RESULTS = ReminderResult.entries - setOf(ReminderResult.PENDING, ReminderResult.SUPPRESSED)

/**
 * How many window-start and snooze reminders a habit may still get today. One slot of the daily
 * limit is kept for the last-chance reminder, so snoozing can never take away the last chance.
 */
fun startAndSnoozeLeft(windowStart: LocalTime, windowEnd: LocalTime, logs: List<PlanLog>, rules: ReminderRules): Int {
    val hasLastChance = windowEnd.minusMinutes(rules.config.lastChanceMinutes) > windowStart && rules.maxPerHabit >= 2
    val cap = rules.maxPerHabit - if (hasLastChance) 1 else 0
    val used = logs.count { it.kind in setOf(ReminderKind.START, ReminderKind.SNOOZE) && it.result in SHOWN_RESULTS }
    return (cap - used).coerceAtLeast(0)
}

/**
 * Every popup for [day] that still needs handling: the early, window-start and last-chance
 * reminders, waiting snoozes, task reminders, and the 11 PM checklist. Early and task reminders
 * are the user's own choice, so (like a promise) they are outside the daily limit.
 */
fun planDay(
    day: LocalDate,
    habits: List<PlanHabit>,
    logs: List<PlanLog>,
    rules: ReminderRules,
    tasks: List<PlanTask> = emptyList(),
): List<Planned> {
    val cfg = rules.config
    val endOfDay = day.atTime(LocalTime.MAX)
    val planned = mutableListOf<Planned>()

    for (h in habits) {
        if (h.done) continue
        val mine = logs.filter { it.habitId == h.id }
        // A promise reminder comes at the promised time, also on an unplanned day. It is not part of
        // the daily limit: the user asked for it.
        if (h.promiseAt != null && !h.skipped && mine.none { it.kind == ReminderKind.PROMISE }) {
            planned += Planned(h.id, ReminderKind.PROMISE, day.atTime(h.promiseAt), endOfDay)
        }
        if (!h.scheduled || h.skipped) continue
        // The early heads-up ends when the window starts (then the start reminder comes). An early
        // time before midnight would belong to the day before: none then.
        val early = h.remindBefore?.let { day.atTime(h.windowStart).minusMinutes(it.toLong()) }
        if (early != null && early.toLocalDate() == day && mine.none { it.kind == ReminderKind.EARLY }) {
            planned += Planned(h.id, ReminderKind.EARLY, early, day.atTime(h.windowStart))
        }
        val windowEnd = day.atTime(h.windowEnd)
        // Spec 6.2: max 3 reminders a day (start + snoozes + last chance together).
        val left = startAndSnoozeLeft(h.windowStart, h.windowEnd, mine, rules)

        if (left > 0 && mine.none { it.kind == ReminderKind.START }) {
            planned += Planned(h.id, ReminderKind.START, day.atTime(h.windowStart), windowEnd)
        }
        val lastChance = h.windowEnd.minusMinutes(cfg.lastChanceMinutes)
        if (lastChance > h.windowStart && rules.maxPerHabit >= 2 && mine.none { it.kind == ReminderKind.LAST_CHANCE }) {
            planned += Planned(h.id, ReminderKind.LAST_CHANCE, day.atTime(lastChance), windowEnd)
        }
        if (left > 0) {
            for (snooze in mine.filter { it.kind == ReminderKind.SNOOZE && it.result == ReminderResult.PENDING }) {
                planned += Planned(h.id, ReminderKind.SNOOZE, snooze.dueAt, endOfDay, snooze.id)
            }
        }
    }

    for (t in tasks) {
        val mine = logs.filter { it.taskId == t.id }
        if (t.due.toLocalDate() == day && mine.none { it.kind == ReminderKind.TASK_DUE }) {
            planned += Planned(null, ReminderKind.TASK_DUE, t.due, endOfDay, taskId = t.id)
        }
        // The early one can fall on an earlier day (e.g. 1 day before): it is planned on that day.
        val early = t.remindBefore?.let { t.due.minusMinutes(it.toLong()) }
        if (early != null && early.toLocalDate() == day && mine.none { it.kind == ReminderKind.TASK_EARLY }) {
            planned += Planned(null, ReminderKind.TASK_EARLY, early, t.due, taskId = t.id)
        }
    }

    if (rules.checklistEnabled && logs.none { it.kind == ReminderKind.CHECKLIST }) {
        planned += Planned(null, ReminderKind.CHECKLIST, day.atTime(cfg.checklistTime), endOfDay)
    }
    return planned
}

/**
 * Splits the popups that are due at [now] into the ones to show and the ones that must not show
 * (expired, or quiet hours). One popup per habit or task at a time: if several are due together
 * (the phone was off), only the most urgent one shows.
 */
fun decide(now: LocalDateTime, planned: List<Planned>, rules: ReminderRules): Decision {
    val due = planned.filter { it.at <= now }
    val suppress = mutableListOf<Planned>()
    val show = mutableListOf<Planned>()
    for (p in due) {
        if (now > p.expiresAt || inQuietHours(now.toLocalTime(), rules)) suppress += p else show += p
    }
    val urgency = listOf(
        ReminderKind.PROMISE, ReminderKind.LAST_CHANCE, ReminderKind.SNOOZE, ReminderKind.START, ReminderKind.EARLY,
        ReminderKind.TASK_DUE, ReminderKind.TASK_EARLY,
    )
    fun mostUrgent(list: List<Planned>, key: (Planned) -> Long?): List<Planned> =
        list.filter { key(it) != null }.groupBy(key).flatMap { (_, same) ->
            val keep = same.minBy { urgency.indexOf(it.kind) }
            suppress += same - keep
            listOf(keep)
        }
    val habits = mostUrgent(show) { it.habitId }
    val tasks = mostUrgent(show) { it.taskId }
    return Decision(show = habits + tasks + show.filter { it.habitId == null && it.taskId == null }, suppress = suppress)
}

/** The time of the next popup after [now], or null if nothing else is planned today. */
fun nextAlarm(now: LocalDateTime, planned: List<Planned>): LocalDateTime? =
    planned.filter { it.at > now }.minOfOrNull { it.at }

/** Which of a habit's reminders fall in quiet hours (and so will not pop up). */
enum class QuietClash { NONE, START_ONLY, ALL }

/**
 * Warns in the editor when a habit's reminder times are in quiet hours: quiet hours suppress
 * popups, they do not delay them, so such a habit would get fewer (or no) reminders.
 */
fun quietClash(windowStart: LocalTime, windowEnd: LocalTime, rules: ReminderRules): QuietClash {
    if (!inQuietHours(windowStart, rules)) return QuietClash.NONE
    val lastChance = windowEnd.minusMinutes(rules.config.lastChanceMinutes)
    val hasLastChance = lastChance > windowStart && rules.maxPerHabit >= 2
    return if (hasLastChance && !inQuietHours(lastChance, rules)) QuietClash.START_ONLY else QuietClash.ALL
}
