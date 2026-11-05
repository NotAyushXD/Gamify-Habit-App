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
)

/** A reminder_log row for today. */
data class PlanLog(
    val id: Long,
    val habitId: Long?,
    val kind: ReminderKind,
    val dueAt: LocalDateTime,
    val result: ReminderResult,
)

/** A popup that has not been handled yet. [logId] is set for a waiting snooze. */
data class Planned(
    val habitId: Long?,
    val kind: ReminderKind,
    val at: LocalDateTime,
    val expiresAt: LocalDateTime,
    val logId: Long? = null,
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
 * Every popup for [day] that still needs handling: the window-start reminder, the last-chance
 * reminder, waiting snoozes, and the 11 PM checklist.
 */
fun planDay(
    day: LocalDate,
    habits: List<PlanHabit>,
    logs: List<PlanLog>,
    rules: ReminderRules,
): List<Planned> {
    val cfg = rules.config
    val endOfDay = day.atTime(LocalTime.MAX)
    val planned = mutableListOf<Planned>()

    for (h in habits) {
        if (!h.scheduled || h.done || h.skipped) continue
        val mine = logs.filter { it.habitId == h.id }
        if (mine.count { it.result in SHOWN_RESULTS } >= rules.maxPerHabit) continue // spec 6.2: max 3 a day
        val windowEnd = day.atTime(h.windowEnd)

        if (mine.none { it.kind == ReminderKind.START }) {
            planned += Planned(h.id, ReminderKind.START, day.atTime(h.windowStart), windowEnd)
        }
        val lastChance = h.windowEnd.minusMinutes(cfg.lastChanceMinutes)
        if (lastChance > h.windowStart && mine.none { it.kind == ReminderKind.LAST_CHANCE }) {
            planned += Planned(h.id, ReminderKind.LAST_CHANCE, day.atTime(lastChance), windowEnd)
        }
        for (snooze in mine.filter { it.kind == ReminderKind.SNOOZE && it.result == ReminderResult.PENDING }) {
            planned += Planned(h.id, ReminderKind.SNOOZE, snooze.dueAt, endOfDay, snooze.id)
        }
    }

    if (rules.checklistEnabled && logs.none { it.kind == ReminderKind.CHECKLIST }) {
        planned += Planned(null, ReminderKind.CHECKLIST, day.atTime(cfg.checklistTime), endOfDay)
    }
    return planned
}

/**
 * Splits the popups that are due at [now] into the ones to show and the ones that must not show
 * (expired, or quiet hours). One popup per habit at a time: if several are due together
 * (the phone was off), only the most urgent one shows.
 */
fun decide(now: LocalDateTime, planned: List<Planned>, rules: ReminderRules): Decision {
    val due = planned.filter { it.at <= now }
    val suppress = mutableListOf<Planned>()
    val show = mutableListOf<Planned>()
    for (p in due) {
        if (now > p.expiresAt || inQuietHours(now.toLocalTime(), rules)) suppress += p else show += p
    }
    val urgency = listOf(ReminderKind.LAST_CHANCE, ReminderKind.SNOOZE, ReminderKind.START)
    val onePerHabit = show.filter { it.habitId != null }
        .groupBy { it.habitId }
        .flatMap { (_, list) ->
            val keep = list.minBy { urgency.indexOf(it.kind) }
            suppress += list - keep
            listOf(keep)
        }
    return Decision(show = onePerHabit + show.filter { it.habitId == null }, suppress = suppress)
}

/** The time of the next popup after [now], or null if nothing else is planned today. */
fun nextAlarm(now: LocalDateTime, planned: List<Planned>): LocalDateTime? =
    planned.filter { it.at > now }.minOfOrNull { it.at }
