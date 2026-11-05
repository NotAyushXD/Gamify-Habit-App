package com.ayushpant.habitquest.stats

import com.ayushpant.habitquest.data.CreatureLine
import com.ayushpant.habitquest.data.DayBonus
import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitSkip
import com.ayushpant.habitquest.data.RangeData
import com.ayushpant.habitquest.data.Session
import com.ayushpant.habitquest.data.SessionSource
import com.ayushpant.habitquest.data.SessionStatus
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.EVERY_DAY_MASK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StatsTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val friday = monday.plusDays(4)

    private fun habit(id: Long, days: Int = EVERY_DAY_MASK, created: LocalDate = monday.minusDays(30)) = Habit(
        id = id, name = "H$id", windowStart = LocalTime.of(7, 0), windowEnd = LocalTime.of(8, 0), basePoints = 10,
        creatureLine = CreatureLine.CALM, daysMask = days, createdDate = created,
    )

    private fun done(habitId: Long, day: LocalDate, points: Int = 10) = Session(
        habitId = habitId, day = day, startTime = 0, endTime = 0, status = SessionStatus.CONFIRMED,
        source = SessionSource.MANUAL, evidenceFound = false, points = points,
    )

    private fun task(id: Long, due: LocalDate, doneDay: LocalDate?, points: Int = 0) = Task(
        id = id, name = "T$id", dueDate = due, basePoints = 10, createdDate = monday.minusDays(10),
        doneDay = doneDay, points = points,
    )

    private fun week(
        habits: List<Habit>,
        sessions: List<Session> = emptyList(),
        tasks: List<Task> = emptyList(),
        open: List<Task> = emptyList(),
        bonuses: List<DayBonus> = emptyList(),
        skips: List<HabitSkip> = emptyList(),
    ) = RangeData(monday, monday.plusDays(6), habits, sessions, bonuses, tasks, skips, open)

    @Test
    fun `periods - week starts Monday, month covers the whole month`() {
        assertEquals(monday to monday.plusDays(6), periodRange(Period.WEEK, friday))
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), periodRange(Period.MONTH, friday))
        assertEquals(friday to friday, periodRange(Period.DAY, friday))
        assertEquals(LocalDate.of(2026, 10, 5), shiftPeriod(Period.MONTH, LocalDate.of(2026, 11, 5), -1))
    }

    @Test
    fun `habit consistency counts past scheduled days, and today only once done`() {
        // Friday is today and not done yet: Mon–Thu count. Done Mon, Tue, Thu = 3 of 4.
        val s = buildSummary(
            week(listOf(habit(1)), sessions = listOf(done(1, monday), done(1, monday.plusDays(1)), done(1, monday.plusDays(3)))),
            today = friday,
        )
        val h = s.habits.single()
        assertEquals(4, h.scheduled)
        assertEquals(3, h.done)
        assertEquals(0.75, s.habitRate!!, 0.0)
    }

    @Test
    fun `done today counts, unscheduled done days are extra, not part of the rate`() {
        val monWedFri = 0b001_0101
        val s = buildSummary(
            week(listOf(habit(1, monWedFri)), sessions = listOf(done(1, monday), done(1, monday.plusDays(1)), done(1, friday))),
            today = friday,
        )
        val h = s.habits.single()
        assertEquals(3, h.scheduled) // Mon, Wed (missed), Fri (done today)
        assertEquals(2, h.done)
        assertEquals(1, h.extra) // Tuesday
    }

    @Test
    fun `the creation day is not a miss in the summary`() {
        val s = buildSummary(week(listOf(habit(1, created = monday.plusDays(2)))), today = friday)
        assertEquals(1, s.habits.single().scheduled) // Thursday only
    }

    @Test
    fun `day cells give the share of habits done per day`() {
        val s = buildSummary(week(listOf(habit(1), habit(2)), sessions = listOf(done(1, monday))), today = friday)
        assertEquals(0.5, s.days[0].rate!!, 0.0)
        assertNull(s.days[5].rate) // Saturday: in the future
    }

    @Test
    fun `task summary - on time, late, due in range, overdue now`() {
        val tasks = listOf(
            task(1, due = monday, doneDay = monday, points = 12),             // on time
            task(2, due = monday, doneDay = monday.plusDays(3), points = 6),  // 3 days late
            task(3, due = monday.plusDays(2), doneDay = null),                // due in range, open
        )
        val s = buildSummary(week(emptyList(), tasks = tasks, open = listOf(tasks[2])), today = friday)
        with(s.tasks) {
            assertEquals(2, completed)
            assertEquals(1, completedOnTime)
            assertEquals(3.0, averageDaysLate!!, 0.0)
            assertEquals(18, points)
            assertEquals(3, dueInRange)
            assertEquals(1, dueInRangeOnTime)
            assertEquals(1, openNow)
            assertEquals(1, overdueNow)
        }
    }

    @Test
    fun `points add sessions, daily bonuses and tasks`() {
        val s = buildSummary(
            week(
                listOf(habit(1)),
                sessions = listOf(done(1, monday, 12)),
                bonuses = listOf(DayBonus(monday, 10)),
                tasks = listOf(task(1, monday, monday, 12)),
            ),
            today = friday,
        )
        assertEquals(34, s.points)
    }

    @Test
    fun `history lists done, missed with skip, and tasks, newest day first`() {
        val skip = HabitSkip(2, monday.plusDays(1), promised = true, createdAt = 0)
        val days = buildHistory(
            week(
                listOf(habit(1), habit(2)),
                sessions = listOf(done(1, monday.plusDays(1))),
                tasks = listOf(task(1, monday, monday.plusDays(1), 12)),
                skips = listOf(skip),
            ),
            today = monday.plusDays(2),
        )
        assertEquals(listOf(monday.plusDays(2), monday.plusDays(1), monday), days.map { it.day })
        assertTrue(days[0].entries.isEmpty()) // today: misses are not listed
        val tue = days[1].entries
        assertTrue(tue[0] is HistoryEntry.HabitDone)
        assertEquals(skip, (tue[1] as HistoryEntry.HabitMissed).skip)
        assertTrue(tue[2] is HistoryEntry.TaskDone)
        assertEquals(22, days[1].points)
    }
}
