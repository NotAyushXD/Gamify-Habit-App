package com.ayushpant.habitquest.reminders

import com.ayushpant.habitquest.data.ReminderKind
import com.ayushpant.habitquest.data.ReminderResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReminderPlannerTest {
    private val day = LocalDate.of(2026, 11, 5)
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private fun at(h: Int, m: Int) = day.atTime(h, m)

    private val rules = ReminderRules(
        quietEnabled = true, quietStart = t(23, 30), quietEnd = t(6, 0), checklistEnabled = true, maxPerHabit = 3,
    )
    private val meditate = PlanHabit(1, t(6, 30), t(7, 30), scheduled = true, done = false, skipped = false)

    private fun log(id: Long, kind: ReminderKind, result: ReminderResult, dueAt: java.time.LocalDateTime = at(6, 30)) =
        PlanLog(id, 1, kind, dueAt, result)

    @Test
    fun `plans the window start, the last chance 15 min before the end, and the checklist`() {
        val planned = planDay(day, listOf(meditate), emptyList(), rules)
        assertEquals(
            listOf(ReminderKind.START to at(6, 30), ReminderKind.LAST_CHANCE to at(7, 15), ReminderKind.CHECKLIST to at(23, 0)),
            planned.map { it.kind to it.at },
        )
    }

    @Test
    fun `done, skipped or unscheduled habits get no reminders`() {
        for (h in listOf(meditate.copy(done = true), meditate.copy(skipped = true), meditate.copy(scheduled = false))) {
            assertTrue(planDay(day, listOf(h), emptyList(), rules.copy(checklistEnabled = false)).isEmpty())
        }
    }

    @Test
    fun `a window of 15 minutes or less has no last-chance reminder`() {
        val short = meditate.copy(windowStart = t(7, 0), windowEnd = t(7, 15))
        assertEquals(listOf(ReminderKind.START), planDay(day, listOf(short), emptyList(), rules.copy(checklistEnabled = false)).map { it.kind })
    }

    @Test
    fun `handled reminders are not planned again, a waiting snooze is`() {
        val logs = listOf(
            log(10, ReminderKind.START, ReminderResult.SNOOZED),
            log(11, ReminderKind.SNOOZE, ReminderResult.PENDING, dueAt = at(6, 45)),
        )
        val planned = planDay(day, listOf(meditate), logs, rules.copy(checklistEnabled = false))
        assertEquals(listOf(ReminderKind.LAST_CHANCE, ReminderKind.SNOOZE), planned.map { it.kind })
        assertEquals(11L, planned.last().logId)
    }

    @Test
    fun `spec 6_2 - at most 3 reminders for a habit a day`() {
        val logs = listOf(
            log(1, ReminderKind.START, ReminderResult.SNOOZED),
            log(2, ReminderKind.SNOOZE, ReminderResult.SNOOZED),
            log(3, ReminderKind.SNOOZE, ReminderResult.SNOOZED),
            log(4, ReminderKind.SNOOZE, ReminderResult.PENDING, dueAt = at(7, 10)),
        )
        assertTrue(planDay(day, listOf(meditate), logs, rules.copy(checklistEnabled = false)).isEmpty())
    }

    @Test
    fun `suppressed reminders do not count toward the limit`() {
        val logs = listOf(log(1, ReminderKind.START, ReminderResult.SUPPRESSED))
        assertEquals(listOf(ReminderKind.LAST_CHANCE), planDay(day, listOf(meditate), logs, rules.copy(checklistEnabled = false)).map { it.kind })
    }

    @Test
    fun `decide shows due popups and suppresses expired ones`() {
        val planned = planDay(day, listOf(meditate), emptyList(), rules)
        val d = decide(at(6, 31), planned, rules)
        assertEquals(listOf(ReminderKind.START), d.show.map { it.kind })
        assertTrue(d.suppress.isEmpty())

        val late = decide(at(8, 0), planned, rules) // the phone was off all morning
        assertTrue(late.show.isEmpty())
        assertEquals(setOf(ReminderKind.START, ReminderKind.LAST_CHANCE), late.suppress.map { it.kind }.toSet())
    }

    @Test
    fun `when start and last chance are due together, only last chance shows`() {
        val d = decide(at(7, 20), planDay(day, listOf(meditate), emptyList(), rules), rules)
        assertEquals(listOf(ReminderKind.LAST_CHANCE), d.show.map { it.kind })
        assertEquals(listOf(ReminderKind.START), d.suppress.map { it.kind })
    }

    @Test
    fun `quiet hours suppress popups, also across midnight`() {
        val early = meditate.copy(windowStart = t(5, 0), windowEnd = t(5, 30))
        val d = decide(at(5, 1), planDay(day, listOf(early), emptyList(), rules), rules)
        assertTrue(d.show.isEmpty())
        assertTrue(inQuietHours(t(23, 45), rules))
        assertTrue(inQuietHours(t(2, 0), rules))
        assertFalse(inQuietHours(t(6, 0), rules))
        assertFalse(inQuietHours(t(2, 0), rules.copy(quietEnabled = false)))
    }

    @Test
    fun `next alarm is the next planned popup`() {
        val planned = planDay(day, listOf(meditate), emptyList(), rules)
        assertEquals(at(6, 30), nextAlarm(at(6, 0), planned))
        assertEquals(at(7, 15), nextAlarm(at(6, 30), planned))
        assertNull(nextAlarm(at(23, 0), planned))
    }

    @Test
    fun `checklist off or already shown is not planned`() {
        assertTrue(planDay(day, emptyList(), emptyList(), rules.copy(checklistEnabled = false)).isEmpty())
        val shown = listOf(PlanLog(1, null, ReminderKind.CHECKLIST, at(23, 0), ReminderResult.DONE))
        assertTrue(planDay(day, emptyList(), shown, rules).isEmpty())
    }
}
