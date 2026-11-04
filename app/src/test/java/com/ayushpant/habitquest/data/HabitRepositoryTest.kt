package com.ayushpant.habitquest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
    fun `day close catches up after days with no run - freeze, decay, comeback`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 0); repo.tickHabit(id)   // Mon: done, streak 1
        setTime(monday.plusDays(1), 7, 0); repo.tickHabit(id) // Tue: done, streak 2
        // Wed, Thu, Fri missed. The app does not run until Saturday.
        setTime(monday.plusDays(5), 9, 0)
        // Tuesday's tick already closed Monday. Now Tue–Fri close in one go.
        assertEquals(4, repo.closePendingDays())

        val streak = db.streaks().get(id)!!
        assertEquals(1, streak.currentDays)      // Wed freeze (2), Thu decay (1), Fri stays 1
        assertEquals(2, streak.bestDays)
        assertTrue(streak.comebackPending)

        setTime(monday.plusDays(5), 7, 0); repo.tickHabit(id)
        // streak 2 → 1.0; comeback +5
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
}
