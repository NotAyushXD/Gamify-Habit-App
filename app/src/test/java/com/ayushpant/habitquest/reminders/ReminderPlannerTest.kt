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
    fun `spec 6_2 - at most 3 reminders a day, and snoozes never take the last chance`() {
        val logs = listOf(
            log(1, ReminderKind.START, ReminderResult.SNOOZED),
            log(2, ReminderKind.SNOOZE, ReminderResult.SNOOZED),
            log(3, ReminderKind.SNOOZE, ReminderResult.PENDING, dueAt = at(7, 10)),
        )
        // Start + 1 snooze used 2 of 3; the third slot is kept for the last chance.
        val planned = planDay(day, listOf(meditate), logs, rules.copy(checklistEnabled = false))
        assertEquals(listOf(ReminderKind.LAST_CHANCE), planned.map { it.kind })
        assertEquals(0, startAndSnoozeLeft(meditate.windowStart, meditate.windowEnd, logs, rules))
    }

    @Test
    fun `nothing more after the last chance was shown`() {
        val logs = listOf(
            log(1, ReminderKind.START, ReminderResult.SNOOZED),
            log(2, ReminderKind.SNOOZE, ReminderResult.CLOSED),
            log(3, ReminderKind.LAST_CHANCE, ReminderResult.CLOSED),
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

    @Test
    fun `a promise reminder comes at the promised time, also on an unplanned day and over the limit`() {
        val promised = meditate.copy(scheduled = false, promiseAt = t(19, 0))
        val full = listOf(
            log(1, ReminderKind.START, ReminderResult.SNOOZED),
            log(2, ReminderKind.SNOOZE, ReminderResult.SNOOZED),
            log(3, ReminderKind.LAST_CHANCE, ReminderResult.CLOSED),
        )
        val planned = planDay(day, listOf(promised), full, rules.copy(checklistEnabled = false))
        assertEquals(listOf(ReminderKind.PROMISE to at(19, 0)), planned.map { it.kind to it.at })
    }

    @Test
    fun `the promise reminder wins over a window reminder at the same time`() {
        val h = meditate.copy(promiseAt = t(6, 30))
        val d = decide(at(6, 31), planDay(day, listOf(h), emptyList(), rules.copy(checklistEnabled = false)), rules)
        assertEquals(listOf(ReminderKind.PROMISE), d.show.map { it.kind })
    }

    @Test
    fun `editor warns when reminder times fall in quiet hours`() {
        // Quiet hours 23:30-06:00 in these rules.
        assertEquals(QuietClash.NONE, quietClash(t(7, 0), t(9, 0), rules))
        assertEquals(QuietClash.START_ONLY, quietClash(t(0, 40), t(9, 0), rules)) // last chance 08:45 still pops up
        assertEquals(QuietClash.ALL, quietClash(t(0, 40), t(5, 0), rules))
        assertEquals(QuietClash.ALL, quietClash(t(23, 40), t(23, 59), rules))
        assertEquals(QuietClash.NONE, quietClash(t(0, 40), t(5, 0), rules.copy(quietEnabled = false)))
    }

    // ---- Early and task reminders (developer request, Nov 10, 2026) -------------------------------

    private val noChecklist get() = rules.copy(checklistEnabled = false)

    @Test
    fun `an early reminder comes before the window, ends when it starts, and is outside the daily limit`() {
        val early = meditate.copy(remindBefore = 60)
        val planned = planDay(day, listOf(early), emptyList(), noChecklist.copy(maxPerHabit = 1))
        val heads = planned.single { it.kind == ReminderKind.EARLY }
        assertEquals(at(5, 30), heads.at)
        assertEquals(at(6, 30), heads.expiresAt)
        assertTrue(planned.any { it.kind == ReminderKind.START }) // the limit of 1 still leaves the start reminder
    }

    @Test
    fun `no early reminder before midnight, when done, skipped, unscheduled, or already logged`() {
        val early = meditate.copy(remindBefore = 180) // 06:30 − 3 h = 03:30, fine
        assertTrue(planDay(day, listOf(early), emptyList(), noChecklist).any { it.kind == ReminderKind.EARLY })
        val tooEarly = meditate.copy(remindBefore = 420) // 06:30 − 7 h: the day before
        assertTrue(planDay(day, listOf(tooEarly), emptyList(), noChecklist).none { it.kind == ReminderKind.EARLY })
        for (h in listOf(early.copy(done = true), early.copy(skipped = true), early.copy(scheduled = false))) {
            assertTrue(planDay(day, listOf(h), emptyList(), noChecklist).none { it.kind == ReminderKind.EARLY })
        }
        val logged = listOf(log(1, ReminderKind.EARLY, ReminderResult.CLOSED, at(3, 30)))
        assertTrue(planDay(day, listOf(early), logged, noChecklist).none { it.kind == ReminderKind.EARLY })
    }

    @Test
    fun `when the early and the start reminder are due together, only the start one shows`() {
        val planned = planDay(day, listOf(meditate.copy(remindBefore = 15)), emptyList(), noChecklist)
        val d = decide(at(6, 30), planned, rules) // the early one is still live until 06:30

        assertEquals(listOf(ReminderKind.START), d.show.map { it.kind })
        assertTrue(d.suppress.any { it.kind == ReminderKind.EARLY })
    }

    @Test
    fun `a task with a due time gets a reminder then, and an early one on the right day`() {
        val task = PlanTask(7, at(18, 0), remindBefore = 60)
        val planned = planDay(day, emptyList(), emptyList(), noChecklist, listOf(task))
        assertEquals(
            setOf(ReminderKind.TASK_EARLY to at(17, 0), ReminderKind.TASK_DUE to at(18, 0)),
            planned.map { it.kind to it.at }.toSet(),
        )
        assertTrue(planned.all { it.taskId == 7L && it.habitId == null })

        // 1 day before: planned on the day before only, and it ends at the due time.
        val dayBefore = PlanTask(8, day.plusDays(1).atTime(9, 0), remindBefore = 1440)
        val today = planDay(day, emptyList(), emptyList(), noChecklist, listOf(dayBefore)).single()
        assertEquals(ReminderKind.TASK_EARLY to at(9, 0), today.kind to today.at)
        assertEquals(day.plusDays(1).atTime(9, 0), today.expiresAt)
        assertTrue(planDay(day.plusDays(1), emptyList(), emptyList(), noChecklist, listOf(dayBefore)).none { it.kind == ReminderKind.TASK_EARLY })
    }

    @Test
    fun `a task reminder already logged is not planned again`() {
        val task = PlanTask(7, at(18, 0))
        val logged = listOf(PlanLog(1, null, ReminderKind.TASK_DUE, at(18, 0), ReminderResult.CLOSED, taskId = 7))
        assertTrue(planDay(day, emptyList(), logged, noChecklist, listOf(task)).isEmpty())
    }

    @Test
    fun `one popup per task, and tasks never hide a habit reminder`() {
        val planned = planDay(day, listOf(meditate), emptyList(), noChecklist, listOf(PlanTask(7, at(6, 30), remindBefore = 0)))
        val d = decide(at(6, 31), planned, rules)
        assertEquals(setOf(ReminderKind.START, ReminderKind.TASK_DUE), d.show.map { it.kind }.toSet())
        assertEquals(listOf(ReminderKind.TASK_EARLY), d.suppress.map { it.kind })
    }
}
