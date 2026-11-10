package com.ayushpant.habitquest.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ayushpant.habitquest.data.AppDatabase
import com.ayushpant.habitquest.data.CreatureLine
import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.ReminderKind
import com.ayushpant.habitquest.data.SessionSource
import com.ayushpant.habitquest.data.Settings
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.EVERY_DAY_MASK
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone() = zone
    override fun withZone(zone: ZoneId) = MutableClock(now, zone)
    override fun instant() = now
}

@RunWith(RobolectricTestRunner::class)
class ReminderRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 11, 2)
    private val clock = MutableClock(monday.atTime(6, 0).atZone(zone).toInstant(), zone)
    private lateinit var db: AppDatabase
    private lateinit var habits: HabitRepository
    private lateinit var reminders: ReminderRepository

    private fun setTime(day: LocalDate, h: Int, m: Int) { clock.now = day.atTime(h, m).atZone(zone).toInstant() }
    private fun localOf(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    private fun newHabit(name: String = "Meditate") = runBlocking {
        habits.saveHabit(
            Habit(
                name = name, windowStart = LocalTime.of(6, 30), windowEnd = LocalTime.of(7, 30), basePoints = 10,
                creatureLine = CreatureLine.CALM, daysMask = EVERY_DAY_MASK, createdDate = monday,
            )
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        habits = HabitRepository(db, clock)
        reminders = ReminderRepository(db, habits, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `window start reminder shows once, at its time`() = runBlocking {
        newHabit()
        setTime(monday, 6, 0)
        assertEquals(monday.atTime(6, 30), localOf(reminders.nextAlarmMillis()))
        assertTrue(reminders.takeDue().isEmpty())

        setTime(monday, 6, 31)
        val ids = reminders.takeDue()
        assertEquals(1, ids.size)
        val popup = reminders.popupFor(ids.single()) as PopupModel.Reminder
        assertEquals(ReminderKind.START, popup.kind)
        assertTrue(reminders.takeDue().isEmpty()) // not shown twice
    }

    @Test
    fun `snooze brings the reminder back 15 minutes later`() = runBlocking {
        val id = newHabit()
        setTime(monday, 6, 31)
        val log = reminders.takeDue().single()
        reminders.snooze(log, id)
        assertEquals(monday.atTime(6, 46), localOf(reminders.nextAlarmMillis()))
        setTime(monday, 6, 46)
        val again = reminders.popupFor(reminders.takeDue().single()) as PopupModel.Reminder
        assertEquals(ReminderKind.SNOOZE, again.kind)
    }

    @Test
    fun `I promise skips the rest of today, and tomorrow's reminder remembers it`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 16)
        val lastChance = reminders.popupFor(reminders.takeDue().single()) as PopupModel.Reminder
        assertEquals(ReminderKind.LAST_CHANCE, lastChance.kind)
        reminders.skip(lastChance.logId, id, promised = true)

        setTime(monday, 23, 0)
        assertTrue(reminders.takeDue().isEmpty()) // skipped habit is not on the checklist; nothing else open

        setTime(monday.plusDays(1), 6, 31)
        val next = reminders.popupFor(reminders.takeDue().single()) as PopupModel.Reminder
        assertTrue(next.promisedYesterday)
    }

    @Test
    fun `only the first reminder popup of the day lists open tasks`() = runBlocking {
        newHabit("Meditate")
        habits.saveHabit(
            Habit(
                name = "Read", windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(10, 0), basePoints = 10,
                creatureLine = CreatureLine.WISE, daysMask = EVERY_DAY_MASK, createdDate = monday,
            )
        )
        habits.saveTask(Task(name = "Renew passport", dueDate = monday, basePoints = 10, createdDate = monday))

        setTime(monday, 6, 31)
        val first = reminders.popupFor(reminders.takeDue().single()) as PopupModel.Reminder
        assertEquals(listOf("Renew passport"), first.tasks.map { it.name })

        setTime(monday, 9, 1)
        val second = reminders.popupFor(reminders.takeDue().single()) as PopupModel.Reminder
        assertTrue(second.tasks.isEmpty())
    }

    @Test
    fun `checklist lists open habits and due tasks, and a tick confirms with the window start time`() = runBlocking {
        val id = newHabit()
        val taskId = habits.saveTask(Task(name = "Call bank", dueDate = monday, basePoints = 10, createdDate = monday))
        habits.saveTask(Task(name = "Later", dueDate = monday.plusDays(5), basePoints = 10, createdDate = monday))
        reminders.saveSettings(Settings(quietEnabled = false))

        setTime(monday, 23, 0)
        val ids = reminders.takeDue()
        val checklist = ids.map { reminders.popupFor(it) }.filterIsInstance<PopupModel.Checklist>().single()
        assertEquals(listOf("Meditate"), checklist.habits.map { it.name })
        assertEquals(listOf("Call bank"), checklist.tasks.map { it.name }) // not the one due later

        reminders.saveChecklist(checklist.logId, setOf(id), setOf(taskId))
        val session = db.sessions().getConfirmed(id, monday).single()
        assertEquals(SessionSource.CHECKLIST, session.source)
        assertEquals(LocalTime.of(6, 30), habits.localTimeOf(session.startTime))
        assertEquals(10, session.points) // in the window: full points
        assertEquals(monday, db.tasks().get(taskId)!!.doneDay)
    }

    @Test
    fun `no checklist popup when nothing is open`() = runBlocking {
        val id = newHabit()
        setTime(monday, 7, 0); habits.tickHabit(id)
        setTime(monday, 23, 0)
        assertTrue(reminders.takeDue().isEmpty())
    }

    @Test
    fun `after the last popup of the day the alarm is set for just after midnight`() = runBlocking {
        reminders.saveSettings(Settings(checklistEnabled = false))
        setTime(monday, 12, 0)
        assertEquals(monday.plusDays(1).atTime(0, 1), localOf(reminders.nextAlarmMillis()))
    }

    @Test
    fun `reminders from a call pop up after it, unless answered from the notification`() = runBlocking {
        val a = newHabit("Meditate")
        newHabit("Read")
        setTime(monday, 6, 31)
        val ids = reminders.takeDue()
        assertEquals(2, ids.size)
        reminders.addAfterCall(ids)
        assertTrue(reminders.hasAfterCall())
        // The user answers one from the notification during the call.
        reminders.done(ids.first { (reminders.popupFor(it) as PopupModel.Reminder).habitId == a }, a)

        setTime(monday, 6, 40)
        val after = reminders.takeAfterCall()
        assertEquals(1, after.size)
        assertEquals("Read", (reminders.popupFor(after.single()) as PopupModel.Reminder).habitName)
        assertTrue(!reminders.hasAfterCall())
        assertTrue(reminders.takeAfterCall().isEmpty())
    }

    // ---- Early and task reminders ------------------------------------------------------------------

    @Test
    fun `a task with a due time pops up then, and Done on the popup completes it on time`() = runBlocking {
        reminders.saveSettings(Settings(checklistEnabled = false))
        val id = habits.saveTask(Task(name = "Call bank", dueDate = monday, basePoints = 10, createdDate = monday, dueTime = LocalTime.of(18, 0), remindBefore = 60))
        setTime(monday, 16, 0)
        assertEquals(monday.atTime(17, 0), localOf(reminders.nextAlarmMillis()))

        setTime(monday, 17, 1)
        val early = reminders.popupFor(reminders.takeDue().single()) as PopupModel.TaskReminder
        assertTrue(early.early)
        assertEquals("Call bank", early.taskName)
        reminders.close(early.logId)

        setTime(monday, 18, 0)
        val due = reminders.popupFor(reminders.takeDue().single()) as PopupModel.TaskReminder
        assertTrue(!due.early)
        reminders.taskDone(due.logId, due.taskId)
        assertEquals(monday, db.tasks().get(id)!!.doneDay)
        assertEquals(12, db.tasks().get(id)!!.points) // on time: by the date
        assertTrue(reminders.takeDue().isEmpty())
    }

    @Test
    fun `a task done before its time gets no reminder, and a task without a time gets none`() = runBlocking {
        reminders.saveSettings(Settings(checklistEnabled = false))
        val timed = habits.saveTask(Task(name = "Call bank", dueDate = monday, basePoints = 10, createdDate = monday, dueTime = LocalTime.of(18, 0)))
        habits.saveTask(Task(name = "Renew passport", dueDate = monday, basePoints = 10, createdDate = monday))
        setTime(monday, 10, 0)
        habits.completeTask(timed)
        setTime(monday, 18, 5)
        assertTrue(reminders.takeDue().isEmpty())
    }

    @Test
    fun `a habit's early reminder shows before the window with the habit popup`() = runBlocking {
        reminders.saveSettings(Settings(checklistEnabled = false))
        val id = habits.saveHabit(
            Habit(name = "Meditate", windowStart = LocalTime.of(6, 30), windowEnd = LocalTime.of(7, 30), basePoints = 10,
                creatureLine = CreatureLine.CALM, daysMask = EVERY_DAY_MASK, createdDate = monday, remindBefore = 30)
        )
        setTime(monday, 6, 1)
        val popup = reminders.popupFor(reminders.takeDue().single()) as PopupModel.Reminder
        assertEquals(ReminderKind.EARLY, popup.kind)
        assertEquals(id, popup.habitId)
        assertEquals("Coming up at 06:30: Meditate", popupTitle(popup))
    }
}
