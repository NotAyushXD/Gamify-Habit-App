package com.ayushpant.habitquest.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScoringTest {

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    private val meditate = HabitRules(basePoints = 10, windowStart = t(6, 30), windowEnd = t(7, 30))

    // ---- Spec 7.5 example -------------------------------------------------------------------

    @Test
    fun `spec 7_5 example - meditate at 0710, streak 8, audio found gives 14 points`() {
        val points = sessionPoints(
            base = 10,
            timing = timingFactor(t(7, 10), t(6, 30), t(7, 30)),
            streak = streakMultiplier(8),
            evidence = evidenceFactor(evidenceFound = true),
        )
        assertEquals(14, points)
    }

    @Test
    fun `spec 7_5 example through scoreHabitDay - streak 8 including today`() {
        // Today's session makes the streak 8, so 7 days were closed before today.
        val closed = StreakState(currentDays = 7)
        val scores = scoreHabitDay(meditate, closed, listOf(SessionInput(1, t(7, 10), evidenceFound = true)))
        assertEquals(SessionScore(points = 14, bonusPoints = 0), scores[1])
    }

    // ---- Timing factor -------------------------------------------------------------------------

    @Test
    fun `timing factor - window boundaries are inside the window`() {
        assertEquals(1.0, timingFactor(t(6, 30), t(6, 30), t(7, 30)), 0.0)
        assertEquals(1.0, timingFactor(t(7, 30), t(6, 30), t(7, 30)), 0.0)
    }

    @Test
    fun `timing factor - up to 1 hour before or after gives 0_6`() {
        assertEquals(0.6, timingFactor(t(6, 29), t(6, 30), t(7, 30)), 0.0)
        assertEquals(0.6, timingFactor(t(5, 30), t(6, 30), t(7, 30)), 0.0)
        assertEquals(0.6, timingFactor(t(7, 31), t(6, 30), t(7, 30)), 0.0)
        assertEquals(0.6, timingFactor(t(8, 30), t(6, 30), t(7, 30)), 0.0)
    }

    @Test
    fun `timing factor - other time on the same day gives 0_3`() {
        assertEquals(0.3, timingFactor(t(5, 29), t(6, 30), t(7, 30)), 0.0)
        assertEquals(0.3, timingFactor(t(8, 31), t(6, 30), t(7, 30)), 0.0)
        assertEquals(0.3, timingFactor(t(22, 0), t(6, 30), t(7, 30)), 0.0)
    }

    @Test
    fun `timing factor - window near midnight does not wrap to the previous evening`() {
        assertEquals(0.3, timingFactor(t(23, 50), t(0, 30), t(1, 0)), 0.0)
        assertEquals(0.6, timingFactor(t(0, 0), t(0, 30), t(1, 0)), 0.0)
    }

    // ---- Streak multiplier ---------------------------------------------------------------------

    @Test
    fun `streak multiplier tiers`() {
        assertEquals(1.0, streakMultiplier(1), 0.0)
        assertEquals(1.0, streakMultiplier(6), 0.0)
        assertEquals(1.2, streakMultiplier(7), 0.0)
        assertEquals(1.2, streakMultiplier(13), 0.0)
        assertEquals(1.5, streakMultiplier(14), 0.0)
        assertEquals(1.5, streakMultiplier(29), 0.0)
        assertEquals(2.0, streakMultiplier(30), 0.0)
        assertEquals(2.0, streakMultiplier(365), 0.0)
    }

    // ---- Rounding --------------------------------------------------------------------------------

    @Test
    fun `rounding is half up without float errors`() {
        // 15 × 0.6 × 1.5 = 13.5 exactly → 14
        assertEquals(14, sessionPoints(15, 0.6, 1.5, 1.0))
        // 5 × 0.3 × 1.0 = 1.5 → 2
        assertEquals(2, sessionPoints(5, 0.3, 1.0, 1.0))
        // 10 × 0.3 × 1.2 × 1.2 = 4.32 → 4
        assertEquals(4, sessionPoints(10, 0.3, 1.2, 1.2))
    }

    @Test
    fun `manual entry has evidence factor 1`() {
        assertEquals(10, sessionPoints(10, 1.0, 1.0, evidenceFactor(false)))
    }

    // ---- One score per habit per day, comeback bonus -------------------------------------------

    @Test
    fun `only the first session of the day scores`() {
        val sessions = listOf(
            SessionInput(2, t(19, 0), false),
            SessionInput(1, t(7, 0), false),
        )
        val scores = scoreHabitDay(meditate, StreakState(), sessions)
        assertEquals(10, scores[1]!!.points)
        assertEquals(0, scores[2]!!.points)
    }

    @Test
    fun `comeback bonus adds 5 after rounding`() {
        val closed = StreakState(currentDays = 3, comebackPending = true)
        val scores = scoreHabitDay(meditate, closed, listOf(SessionInput(1, t(7, 0), false)))
        assertEquals(SessionScore(points = 15, bonusPoints = 5), scores[1])
    }

    @Test
    fun `no sessions gives no scores`() {
        assertTrue(scoreHabitDay(meditate, StreakState(), emptyList()).isEmpty())
    }

    // ---- Daily bonus -----------------------------------------------------------------------------

    @Test
    fun `daily bonus only when all scheduled habits are done`() {
        assertEquals(10, dailyBonus(setOf(1, 2, 3), setOf(1, 2, 3)))
        assertEquals(10, dailyBonus(setOf(1, 2), setOf(1, 2, 9))) // extra unscheduled habit is fine
        assertEquals(0, dailyBonus(setOf(1, 2, 3), setOf(1, 2)))
        assertEquals(0, dailyBonus(emptySet(), emptySet()))
    }

    // ---- Freeze, decay, streak (spec 7.3) --------------------------------------------------------

    private val mon = LocalDate.of(2026, 11, 2) // a Monday

    @Test
    fun `done day increases streak and best`() {
        val s = closeDay(StreakState(currentDays = 4, bestDays = 4), mon, DayOutcome.DONE)
        assertEquals(5, s.currentDays)
        assertEquals(5, s.bestDays)
        assertEquals(mon, s.lastDoneDate)
        assertFalse(s.comebackPending)
    }

    @Test
    fun `first miss of the week uses the freeze and keeps the streak`() {
        val s = closeDay(StreakState(currentDays = 10, bestDays = 10), mon, DayOutcome.MISSED)
        assertEquals(10, s.currentDays)
        assertEquals(1, s.freezesUsed)
        assertEquals(mon, s.freezeWeek)
        assertTrue(s.comebackPending)
    }

    @Test
    fun `spec 12_3 - miss 2 days - freeze then decay`() {
        var s = StreakState(currentDays = 10, bestDays = 10)
        s = closeDay(s, mon, DayOutcome.MISSED)               // freeze
        s = closeDay(s, mon.plusDays(1), DayOutcome.MISSED)   // decay 10 → 5
        assertEquals(5, s.currentDays)
        assertEquals(10, s.bestDays)
        assertTrue(s.comebackPending)
    }

    @Test
    fun `decay never takes the streak to 0`() {
        var s = StreakState(currentDays = 3, freezeWeek = mon, freezesUsed = 1)
        s = closeDay(s, mon.plusDays(1), DayOutcome.MISSED) // 3 → 1
        assertEquals(1, s.currentDays)
        s = closeDay(s, mon.plusDays(2), DayOutcome.MISSED) // 1 → stays 1
        assertEquals(1, s.currentDays)
    }

    @Test
    fun `freeze resets on Monday`() {
        val sunday = mon.plusDays(6)
        var s = StreakState(currentDays = 8)
        s = closeDay(s, sunday, DayOutcome.MISSED)          // freeze of week 1
        assertEquals(8, s.currentDays)
        s = closeDay(s, sunday.plusDays(1), DayOutcome.MISSED) // Monday: new week, new freeze
        assertEquals(8, s.currentDays)
        assertEquals(sunday.plusDays(1), s.freezeWeek)
    }

    @Test
    fun `miss with no streak does not use the freeze`() {
        val s = closeDay(StreakState(currentDays = 0), mon, DayOutcome.MISSED)
        assertEquals(0, s.currentDays)
        assertEquals(0, s.freezesUsed)
        assertTrue(s.comebackPending)
    }

    @Test
    fun `unscheduled day changes nothing`() {
        val before = StreakState(currentDays = 5, comebackPending = true)
        assertEquals(before, closeDay(before, mon, DayOutcome.NOT_SCHEDULED))
    }

    @Test
    fun `creation day is never a miss`() {
        assertEquals(DayOutcome.NOT_SCHEDULED, dayOutcome(done = false, scheduled = true, isCreationDay = true))
        assertEquals(DayOutcome.MISSED, dayOutcome(done = false, scheduled = true, isCreationDay = false))
        assertEquals(DayOutcome.DONE, dayOutcome(done = true, scheduled = false, isCreationDay = false))
    }

    @Test
    fun `days of week mask`() {
        val monWedFri = 0b001_0101
        assertTrue(isScheduledOn(monWedFri, mon))
        assertFalse(isScheduledOn(monWedFri, mon.plusDays(1)))
        assertTrue(isScheduledOn(monWedFri, mon.plusDays(4)))
        assertTrue(isScheduledOn(EVERY_DAY_MASK, mon.plusDays(6)))
    }

    // ---- Tasks ------------------------------------------------------------------------------------

    @Test
    fun `task done on or before the due date gets the on-time bonus`() {
        assertEquals(12, taskPoints(10, dueDate = mon, doneDate = mon))
        assertEquals(12, taskPoints(10, dueDate = mon, doneDate = mon.minusDays(3)))
    }

    @Test
    fun `task done late loses more the longer it waits`() {
        val late = listOf(1, 2, 3, 6, 7, 13, 14, 60).map { taskPoints(10, mon, mon.plusDays(it.toLong())) }
        assertEquals(listOf(8, 8, 6, 6, 4, 4, 2, 2), late)
    }

    @Test
    fun `a late task is never worth 0`() {
        assertEquals(1, taskPoints(5, mon, mon.plusDays(365))) // 5 × 0.2 = 1
    }

    @Test
    fun `config values can be changed for tuning`() {
        val cfg = ScoringConfig(dailyBonus = 25)
        assertEquals(25, dailyBonus(setOf(1), setOf(1), cfg))
    }
}
