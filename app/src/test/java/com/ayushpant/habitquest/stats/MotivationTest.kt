package com.ayushpant.habitquest.stats

import com.ayushpant.habitquest.data.CreatureLine
import com.ayushpant.habitquest.data.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MotivationTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val habit = Habit(
        id = 1, name = "Meditate", windowStart = LocalTime.of(7, 0), windowEnd = LocalTime.of(8, 0), basePoints = 10,
        creatureLine = CreatureLine.CALM, daysMask = 127, createdDate = monday.minusDays(30),
    )
    private val noTasks = TaskSummary(0, 0, 0, null, 0, 0, 0, 0, 0)

    private fun summary(
        done: Int, scheduled: Int, streak: Int = 0, points: Int = 0,
        days: List<DayCell> = emptyList(), tasks: TaskSummary = noTasks, comebacks: Int = 0,
        today: LocalDate = monday.plusDays(6),
    ) = Summary(
        from = monday, to = monday.plusDays(6), points = points,
        habits = if (scheduled == 0 && done == 0) emptyList() else listOf(HabitSummary(habit, scheduled, done, 0, streak, streak)),
        days = days, tasks = tasks, today = today, comebacks = comebacks,
    )

    @Test
    fun `praise fits the result and is never negative`() {
        assertEquals("Outstanding!", praise(summary(9, 10), "this week").title)
        assertEquals("Strong work", praise(summary(7, 10), "this week").title)
        assertEquals("You kept going", praise(summary(4, 10), "this week").title)
        assertEquals("Small steps add up", praise(summary(1, 10), "this week").title)
        assertEquals("Fresh start", praise(summary(0, 10), "this week").title)
        assertEquals("Ready when you are", praise(summary(0, 0), "this week").title)
    }

    @Test
    fun `comparison only lists improvements`() {
        val better = compare(summary(8, 10, points = 120), summary(6, 10, points = 90), "last week")
        assertEquals(listOf("+20% more consistent than last week", "+30 points more than last week"), better)
        assertTrue(compare(summary(5, 10, points = 50), summary(8, 10, points = 90), "last week").isEmpty())
    }

    @Test
    fun `perfect days - today counts only when every planned habit is done`() {
        val today = monday.plusDays(2)
        val days = listOf(
            DayCell(monday, scheduled = 2, done = 2),
            DayCell(monday.plusDays(1), scheduled = 2, done = 1),
            DayCell(today, scheduled = 1, done = 1, pending = 1), // one of two done so far
        )
        val s = summary(4, 5, days = days, today = today)
        assertEquals(1, s.perfectDays)
        assertEquals(2, s.plannedDays)
        assertEquals(0.5, s.perfectRate!!, 0.0)
    }

    @Test
    fun `badges - earned first, progress counted`() {
        val b = badges(summary(3, 4, streak = 8, points = 40, comebacks = 1))
        val onFire = b.single { it.title == "On fire" }
        assertTrue(onFire.earned)
        val unstoppable = b.single { it.title == "Unstoppable" }
        assertFalse(unstoppable.earned)
        assertEquals(8, unstoppable.progress)
        assertTrue(b.takeWhile { it.earned }.size == b.count { it.earned })
    }

    @Test
    fun `highlights show the longest streak and skip empty ones`() {
        val h = highlights(summary(3, 4, streak = 5))
        assertEquals("Meditate: 5 days", h.first { it.title == "Longest streak now" }.value)
        assertTrue(h.none { it.title == "Comebacks" })
    }
}
