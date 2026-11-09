package com.ayushpant.habitquest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.maskOf
import com.ayushpant.habitquest.scoring.EVERY_DAY_MASK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A clock the test can move forward, to simulate days passing. */
private class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone() = zone
    override fun withZone(zone: ZoneId) = MutableClock(now, zone)
    override fun instant() = now
}

/**
 * Drives the real repository and an in-memory Room database through several days.
 * Robolectric runs the Android parts on the Mac, so no phone is needed.
 */
@RunWith(RobolectricTestRunner::class)
class HabitRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 11, 2)
    private val clock = MutableClock(at(monday, 6, 0), zone)
    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository

    private fun at(day: LocalDate, h: Int, m: Int): Instant = day.atTime(h, m).atZone(zone).toInstant()
    private fun setTime(day: LocalDate, h: Int, m: Int) { clock.now = at(day, h, m) }
    private fun today() = runBlocking { repo.observeToday(repo.today()).first() }
    private fun habitToday(id: Long) = today().habits.single { it.habit.id == id }

    private fun newHabit(name: String = "Meditate", daysMask: Int = EVERY_DAY_MASK) = runBlocking {
        repo.saveHabit(
            Habit(
                name = name, windowStart = LocalTime.of(6, 30), windowEnd = LocalTime.of(7, 30),
                basePoints = 10, creatureLine = CreatureLine.CALM, daysMask = daysMask, createdDate = monday,
            )
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = HabitRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `eight days in the window - day 7 and 8 get the 1_2 streak multiplier`() = runBlocking {
        val id = newHabit()
        val points = (0L until 8).map { d ->
            setTime(monday.plusDays(d), 7, 10)
            repo.tickHabit(id)
            habitToday(id).points
        }
        // Manual entry, so evidence is 1.0: 10 × 1.0 × streak × 1.0
        assertEquals(listOf(10, 10, 10, 10, 10, 10, 12, 12), points)
        assertEquals(8, habitToday(id).streakDays)
    }

    @Test
    fun `day close catches up after days with no run - freeze, reset, comeback`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 0); repo.tickHabit(id)   // Mon: done, streak 1
        setTime(monday.plusDays(1), 7, 0); repo.tickHabit(id) // Tue: done, streak 2
        // Wed, Thu, Fri missed. The app does not run until Saturday.
        setTime(monday.plusDays(5), 9, 0)
        // Tuesday's tick already closed Monday. Now Tue–Fri close in one go.
        assertEquals(4, repo.closePendingDays())

        val streak = db.streaks().get(id)!!
        assertEquals(0, streak.currentDays)      // Wed freeze (2), Thu reset (0), Fri stays 0
        assertEquals(2, streak.bestDays)
        assertTrue(streak.comebackPending)

        setTime(monday.plusDays(5), 7, 0); repo.tickHabit(id)
        assertEquals(1, habitToday(id).streakDays) // starts again
        // streak 1 → 1.0; comeback +5
        assertEquals(15, habitToday(id).points)
        assertEquals(5, habitToday(id).done.single().bonusPoints)
    }

    @Test
    fun `closing twice does nothing the second time`() = runBlocking {
        newHabit()
        setTime(monday.plusDays(3), 9, 0)
        assertEquals(3, repo.closePendingDays())
        assertEquals(0, repo.closePendingDays())
    }

    @Test
    fun `daily bonus comes when the last habit is done and goes if one is removed`() = runBlocking {
        val a = newHabit("Meditate")
        val b = newHabit("Read")
        setTime(monday, 7, 0)
        repo.tickHabit(a)
        assertEquals(0, today().dailyBonus)
        repo.tickHabit(b)
        assertEquals(10, today().dailyBonus)
        assertEquals(30, today().totalPoints)

        repo.removeSession(habitToday(b).done.single().id)
        assertEquals(0, today().dailyBonus)
        assertEquals(10, today().totalPoints)
    }

    @Test
    fun `unscheduled days are not misses`() = runBlocking {
        val monWedFri = 0b001_0101
        val id = newHabit(daysMask = monWedFri)
        setTime(monday, 7, 0); repo.tickHabit(id)
        setTime(monday.plusDays(2), 7, 0); repo.tickHabit(id) // Wed; Tue was not scheduled
        setTime(monday.plusDays(3), 9, 0); repo.closePendingDays()
        val streak = db.streaks().get(id)!!
        assertEquals(2, streak.currentDays)
        assertFalse(streak.comebackPending)
        assertEquals(0, streak.freezesUsed)
    }

    @Test
    fun `a habit not done on its creation day is not a miss`() = runBlocking {
        val id = newHabit()
        setTime(monday.plusDays(1), 9, 0)
        repo.closePendingDays()
        assertFalse(db.streaks().get(id)!!.comebackPending)
    }

    @Test
    fun `changing the time of a tick changes the points`() = runBlocking {
        val id = newHabit()
        setTime(monday, 21, 0)
        repo.tickHabit(id)
        assertEquals(3, habitToday(id).points) // 0.3: not near the window
        repo.editSessionStart(habitToday(id).done.single().id, LocalTime.of(7, 0))
        assertEquals(10, habitToday(id).points)
    }

    @Test
    fun `only the first session of the day scores`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 0); repo.tickHabit(id)
        setTime(monday, 20, 0); repo.tickHabit(id)
        val done = habitToday(id).done
        assertEquals(listOf(10, 0), done.map { it.points })
        assertEquals(10, habitToday(id).points)
    }

    @Test
    fun `a started session is not confirmed until Done, and does not count at day close`() = runBlocking {
        val id = newHabit()
        setTime(monday.plusDays(1), 7, 0)
        repo.startSession(id)
        val h = habitToday(id)
        assertFalse(h.isDone)
        assertEquals(SessionStatus.SUGGESTED, h.inProgress!!.status)

        setTime(monday.plusDays(2), 9, 0)
        repo.closePendingDays()
        assertTrue(db.streaks().get(id)!!.comebackPending) // Tuesday was a miss
    }

    @Test
    fun `start then Done confirms with the start time`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 20); repo.startSession(id)
        setTime(monday, 7, 45); repo.finishSession(habitToday(id).inProgress!!.id)
        val h = habitToday(id)
        assertNull(h.inProgress)
        assertEquals(10, h.points) // started in the window
        assertEquals(LocalTime.of(7, 20), repo.localTimeOf(h.done.single().startTime))
    }

    @Test
    fun `deleted habit keeps its points and leaves today`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 0); repo.tickHabit(id)
        repo.deleteHabit(id)
        assertTrue(today().habits.isEmpty())
        assertEquals(10, db.sessions().getConfirmed(id, monday).single().points)
    }

    // ---- Tasks ------------------------------------------------------------------------------------

    private fun newTask(due: LocalDate, base: Int = 10) = runBlocking {
        repo.saveTask(Task(name = "Renew passport", dueDate = due, basePoints = base, createdDate = monday))
    }
    private fun taskToday(id: Long) = today().tasks.single { it.task.id == id }

    @Test
    fun `task done by the due date gets the on-time bonus`() = runBlocking {
        val id = newTask(due = monday.plusDays(2))
        setTime(monday, 10, 0)
        assertEquals(12, taskToday(id).points) // worth, while open
        repo.completeTask(id)
        assertTrue(taskToday(id).isDone)
        assertEquals(12, taskToday(id).points)
        assertEquals(12, today().totalPoints)
    }

    @Test
    fun `overdue task stays on Today and its worth drops, then it scores for the day it is done`() = runBlocking {
        val id = newTask(due = monday)
        setTime(monday.plusDays(4), 10, 0) // Friday, 4 days late
        val open = taskToday(id)
        assertFalse(open.isDone)
        assertEquals(4L, open.daysLate)
        assertEquals(6, open.points)
        repo.completeTask(id)
        assertEquals(6, taskToday(id).points)
    }

    @Test
    fun `done task leaves Today the next day`() = runBlocking {
        val id = newTask(due = monday)
        setTime(monday, 10, 0); repo.completeTask(id)
        setTime(monday.plusDays(1), 10, 0)
        assertTrue(today().tasks.none { it.task.id == id })
    }

    @Test
    fun `reopening a task removes its points`() = runBlocking {
        val id = newTask(due = monday)
        setTime(monday, 10, 0); repo.completeTask(id)
        repo.reopenTask(id)
        val t = taskToday(id)
        assertFalse(t.isDone)
        assertEquals(0, db.tasks().get(id)!!.points)
        assertEquals(0, today().totalPoints)
    }

    @Test
    fun `tasks do not count toward the daily bonus`() = runBlocking {
        val habit = newHabit()
        newTask(due = monday) // open task due today
        setTime(monday, 7, 0); repo.tickHabit(habit)
        assertEquals(10, today().dailyBonus) // the open task does not block the bonus
    }

    // ---- History edits on closed days (replay) ----------------------------------------------------

    @Test
    fun `adding a missed session in History restores the streak and the points`() = runBlocking {
        val id = newHabit()
        val tue = monday.plusDays(1)
        val wed = monday.plusDays(2)
        setTime(monday, 7, 0); repo.tickHabit(id)
        setTime(wed, 7, 0); repo.tickHabit(id) // Tuesday missed: freeze, comeback +5 on Wednesday
        assertEquals(15, habitToday(id).points)

        setTime(monday.plusDays(3), 9, 0) // Thursday
        repo.addSession(id, tue, LocalTime.of(7, 0), SessionSource.MANUAL)

        assertEquals(10, db.sessions().getConfirmed(id, tue).single().points)
        assertEquals(10, db.sessions().getConfirmed(id, wed).single().points) // no comeback any more
        val streak = db.streaks().get(id)!!
        assertEquals(3, streak.currentDays)
        assertEquals(0, streak.freezesUsed)
        assertFalse(streak.comebackPending)
    }

    @Test
    fun `replay leaves points before the edited day alone`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 0); repo.tickHabit(id)
        setTime(monday.plusDays(3), 9, 0)
        repo.closePendingDays() // Monday is closed and final now
        db.sessions().setPoints(db.sessions().getConfirmed(id, monday).single().id, 99, 0) // e.g. old config
        repo.addSession(id, monday.plusDays(1), LocalTime.of(7, 0), SessionSource.MANUAL)
        assertEquals(99, db.sessions().getConfirmed(id, monday).single().points)
    }

    @Test
    fun `changing the time of a past session scores it again`() = runBlocking {
        val id = newHabit()
        setTime(monday, 21, 0); repo.tickHabit(id)
        setTime(monday.plusDays(1), 9, 0)
        val session = db.sessions().getConfirmed(id, monday).single()
        assertEquals(3, session.points)
        repo.editSessionStart(session.id, LocalTime.of(7, 0))
        assertEquals(10, db.sessions().getConfirmed(id, monday).single().points)
    }

    @Test
    fun `adding a past session can earn that day's daily bonus`() = runBlocking {
        val a = newHabit("Meditate")
        val b = newHabit("Read")
        setTime(monday, 7, 0); repo.tickHabit(a)
        setTime(monday.plusDays(1), 9, 0)
        assertNull(db.dayBonus().observe(monday).first())
        repo.addSession(b, monday, LocalTime.of(20, 0), SessionSource.MANUAL)
        assertEquals(10, db.dayBonus().observe(monday).first()!!.points)
    }

    @Test
    fun `a session cannot be added before the habit existed or in the future`() = runBlocking {
        val id = newHabit()
        setTime(monday, 9, 0)
        repo.addSession(id, monday.minusDays(1), LocalTime.of(7, 0), SessionSource.MANUAL)
        repo.addSession(id, monday.plusDays(1), LocalTime.of(7, 0), SessionSource.MANUAL)
        assertTrue(db.sessions().getConfirmed(id, monday.minusDays(1)).isEmpty())
        assertTrue(db.sessions().getConfirmed(id, monday.plusDays(1)).isEmpty())
    }

    @Test
    fun `skip shows on Today and is still a miss`() = runBlocking {
        val id = newHabit()
        setTime(monday.plusDays(1), 10, 0)
        repo.skipToday(id, promised = true)
        assertTrue(habitToday(id).skip!!.promised)
        setTime(monday.plusDays(2), 9, 0)
        repo.closePendingDays()
        assertTrue(db.streaks().get(id)!!.comebackPending)
    }

    @Test
    fun `old streaks are walked once with the new reset rule`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 0); repo.tickHabit(id)
        setTime(monday.plusDays(1), 7, 0); repo.tickHabit(id)
        setTime(monday.plusDays(5), 9, 0); repo.closePendingDays() // streak now 0 under the new rule
        // Pretend the phone still has an old-rule streak and the rules flag is old.
        db.streaks().upsert(db.streaks().get(id)!!.copy(currentDays = 1))
        db.appState().put(AppState("streak_rules_version", "1"))
        repo.closePendingDays()
        assertEquals(0, db.streaks().get(id)!!.currentDays)
    }

    // ---- Mind / Body / Soul ------------------------------------------------------------------------

    @Test
    fun `old habits get dimensions from their name once, and the creature follows`() = runBlocking {
        val id = newHabit("Gym")
        db.habits().update(db.habits().get(id)!!.copy(mbsMask = 0)) // as on a phone before v4
        db.appState().delete("mbs_backfill")
        repo.closePendingDays()
        val h = db.habits().get(id)!!
        assertEquals(maskOf(setOf(Dimension.BODY, Dimension.MIND)), h.mbsMask)
        assertEquals(CreatureLine.STRONG, h.creatureLine)
    }

    @Test
    fun `points are fixed at 10 whatever the habit says`() = runBlocking {
        val id = repo.saveHabit(
            Habit(name = "Read", windowStart = LocalTime.of(6, 30), windowEnd = LocalTime.of(7, 30), basePoints = 20,
                creatureLine = CreatureLine.WISE, daysMask = EVERY_DAY_MASK, createdDate = monday)
        )
        setTime(monday, 7, 0); repo.tickHabit(id)
        assertEquals(10, habitToday(id).points)
    }

    @Test
    fun `today's meters - meditation feeds mind and soul, gym not done yet`() = runBlocking {
        val meditate = repo.saveHabit(
            Habit(name = "Meditate", windowStart = LocalTime.of(6, 30), windowEnd = LocalTime.of(7, 30), basePoints = 10,
                creatureLine = CreatureLine.CALM, daysMask = EVERY_DAY_MASK, createdDate = monday,
                mbsMask = maskOf(setOf(Dimension.MIND, Dimension.SOUL)))
        )
        repo.saveHabit(
            Habit(name = "Gym", windowStart = LocalTime.of(18, 0), windowEnd = LocalTime.of(19, 0), basePoints = 10,
                creatureLine = CreatureLine.STRONG, daysMask = EVERY_DAY_MASK, createdDate = monday,
                mbsMask = maskOf(setOf(Dimension.BODY, Dimension.MIND)))
        )
        setTime(monday, 7, 0); repo.tickHabit(meditate)
        val meters = today().balance.associateBy { it.dimension }
        assertEquals(0.5, meters[Dimension.MIND]!!.rate!!, 1e-9)
        assertEquals(1.0, meters[Dimension.SOUL]!!.rate!!, 1e-9)
        assertEquals(0.0, meters[Dimension.BODY]!!.rate!!, 1e-9)
    }
}
