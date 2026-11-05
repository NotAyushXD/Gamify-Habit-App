package com.ayushpant.habitquest.scoring

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * Pure scoring and streak rules (spec section 7). No Android code here, so the
 * unit tests in app/src/test run on the Mac in seconds.
 */

/** Spec 7.1: timing factor. Window boundaries are inclusive. Windows do not cross midnight. */
fun timingFactor(
    start: LocalTime,
    windowStart: LocalTime,
    windowEnd: LocalTime,
    cfg: ScoringConfig = ScoringConfig.DEFAULT,
): Double {
    // Work in minutes since midnight so "1 hour before 00:30" does not wrap to 23:30.
    val t = start.toSecondOfDay() / 60
    val ws = windowStart.toSecondOfDay() / 60
    val we = windowEnd.toSecondOfDay() / 60
    val near = cfg.nearWindowMinutes.toInt()
    return when {
        t in ws..we -> cfg.timingInWindow
        t in (ws - near) until ws -> cfg.timingNearWindow
        t in (we + 1)..(we + near) -> cfg.timingNearWindow
        else -> cfg.timingOtherTime
    }
}

/** Spec 7.2: streak multiplier for a streak that already includes today's session. */
fun streakMultiplier(streakDays: Int, cfg: ScoringConfig = ScoringConfig.DEFAULT): Double =
    cfg.streakTiers.firstOrNull { (minDays, _) -> streakDays >= minDays }?.second ?: 1.0

/** Spec 7.1: evidence factor. */
fun evidenceFactor(evidenceFound: Boolean, cfg: ScoringConfig = ScoringConfig.DEFAULT): Double =
    if (evidenceFound) cfg.evidenceFound else cfg.evidenceNone

/**
 * Spec 7.1: Points = Base × Timing × Streak × Evidence, rounded to the nearest whole number.
 * BigDecimal avoids float surprises such as 13.5 being stored as 13.4999… and rounding down.
 */
fun sessionPoints(base: Int, timing: Double, streak: Double, evidence: Double): Int =
    roundedPoints(base, timing, streak, evidence)

private fun roundedPoints(base: Int, vararg factors: Double): Int =
    factors
        .fold(BigDecimal(base)) { acc, factor -> acc * BigDecimal.valueOf(factor) }
        .setScale(0, RoundingMode.HALF_UP)
        .toInt()

/** Task factor: the on-time bonus, or a cut that grows with the number of days late. */
fun taskFactor(daysLate: Long, cfg: ScoringConfig = ScoringConfig.DEFAULT): Double =
    if (daysLate <= 0) cfg.taskOnTime
    else cfg.taskLateTiers.firstOrNull { (minDays, _) -> daysLate >= minDays }?.second ?: cfg.taskLateTiers.last().second

/** Days after [dueDate] that [doneDate] is. 0 or less means on time. */
fun daysLate(dueDate: LocalDate, doneDate: LocalDate): Long = ChronoUnit.DAYS.between(dueDate, doneDate)

/** Points for a task done on [doneDate] (or worth now, if [doneDate] is today and it is still open). */
fun taskPoints(base: Int, dueDate: LocalDate, doneDate: LocalDate, cfg: ScoringConfig = ScoringConfig.DEFAULT): Int =
    roundedPoints(base, taskFactor(daysLate(dueDate, doneDate), cfg))

/** The parts of a habit that scoring needs. */
data class HabitRules(
    val basePoints: Int,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
)

/** One confirmed session of a habit on one day. */
data class SessionInput(
    val id: Long,
    val start: LocalTime,
    val evidenceFound: Boolean,
)

/** Points for one session. [points] already includes [bonusPoints]. */
data class SessionScore(val points: Int, val bonusPoints: Int)

/**
 * Scores all confirmed sessions of one habit on one day.
 *
 * Only the first session (by start time) scores; later ones get 0 (one score per habit per day).
 * [closedStreak] is the streak state after the previous day was closed, so today's session is
 * day `currentDays + 1` of the streak.
 */
fun scoreHabitDay(
    habit: HabitRules,
    closedStreak: StreakState,
    confirmed: List<SessionInput>,
    cfg: ScoringConfig = ScoringConfig.DEFAULT,
): Map<Long, SessionScore> {
    val ordered = confirmed.sortedWith(compareBy({ it.start }, { it.id }))
    val first = ordered.firstOrNull() ?: return emptyMap()
    val core = sessionPoints(
        base = habit.basePoints,
        timing = timingFactor(first.start, habit.windowStart, habit.windowEnd, cfg),
        streak = streakMultiplier(closedStreak.currentDays + 1, cfg),
        evidence = evidenceFactor(first.evidenceFound, cfg),
    )
    val bonus = if (closedStreak.comebackPending) cfg.comebackBonus else 0
    return ordered.associate { s ->
        s.id to if (s.id == first.id) SessionScore(core + bonus, bonus) else SessionScore(0, 0)
    }
}

/** Spec 7.4: daily bonus when every habit scheduled that day is done. */
fun dailyBonus(
    scheduledHabitIds: Set<Long>,
    doneHabitIds: Set<Long>,
    cfg: ScoringConfig = ScoringConfig.DEFAULT,
): Int =
    if (scheduledHabitIds.isNotEmpty() && doneHabitIds.containsAll(scheduledHabitIds)) cfg.dailyBonus else 0

// ---------------------------------------------------------------------------------------------
// Streaks (spec 7.2, 7.3)
// ---------------------------------------------------------------------------------------------

/** Streak state after a day has been closed. */
data class StreakState(
    val currentDays: Int = 0,
    val bestDays: Int = 0,
    val lastDoneDate: LocalDate? = null,
    /** Monday of the week in which [freezesUsed] were counted. */
    val freezeWeek: LocalDate? = null,
    val freezesUsed: Int = 0,
    /** True after a miss, until the next session. Gives the comeback bonus. */
    val comebackPending: Boolean = false,
)

enum class DayOutcome { DONE, MISSED, NOT_SCHEDULED }

/** What a day counts as for one habit. The day the habit was created is never a miss. */
fun dayOutcome(done: Boolean, scheduled: Boolean, isCreationDay: Boolean): DayOutcome = when {
    done -> DayOutcome.DONE
    scheduled && !isCreationDay -> DayOutcome.MISSED
    else -> DayOutcome.NOT_SCHEDULED
}

fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Spec 7.3: applies one closed day to the streak (done, freeze, or decay). */
fun closeDay(
    state: StreakState,
    date: LocalDate,
    outcome: DayOutcome,
    cfg: ScoringConfig = ScoringConfig.DEFAULT,
): StreakState = when (outcome) {
    DayOutcome.NOT_SCHEDULED -> state
    DayOutcome.DONE -> {
        val days = state.currentDays + 1
        state.copy(
            currentDays = days,
            bestDays = maxOf(state.bestDays, days),
            lastDoneDate = date,
            comebackPending = false,
        )
    }
    DayOutcome.MISSED -> {
        val week = weekStart(date)
        val usedThisWeek = if (state.freezeWeek == week) state.freezesUsed else 0
        when {
            // No streak to protect: do not waste the freeze.
            state.currentDays == 0 -> state.copy(comebackPending = true)
            usedThisWeek < cfg.freezesPerWeek -> state.copy(
                freezeWeek = week,
                freezesUsed = usedThisWeek + 1,
                comebackPending = true,
            )
            else -> state.copy(
                currentDays = maxOf(cfg.streakFloorAfterDecay, state.currentDays / cfg.decayDivisor),
                comebackPending = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Days of the week, stored as a bit mask: Monday = bit 0 … Sunday = bit 6.
// ---------------------------------------------------------------------------------------------

const val EVERY_DAY_MASK = 0b111_1111

fun isScheduledOn(daysMask: Int, date: LocalDate): Boolean =
    daysMask and (1 shl (date.dayOfWeek.value - 1)) != 0
