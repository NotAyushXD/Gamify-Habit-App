package com.ayushpant.habitquest.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ayushpant.habitquest.data.AppDatabase
import com.ayushpant.habitquest.data.CreatureLine
import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.EVERY_DAY_MASK
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private class StepClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone() = zone
    override fun withZone(zone: ZoneId) = StepClock(now, zone)
    override fun instant() = now
}

/**
 * Renders the Summary screen with sample data to PNG files in app/build/render, to check how it looks
 * without a phone. Robolectric draws real pixels in NATIVE graphics mode.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h2300dp-xhdpi")
class SummaryRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun render() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = LocalDate.of(2026, 10, 26)
        val clock = StepClock(start.atTime(6, 0).atZone(zone).toInstant(), zone)
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = HabitRepository(db, clock)
        fun at(d: LocalDate, h: Int) { clock.now = d.atTime(h, 0).atZone(zone).toInstant() }
        runBlocking {
            val names = listOf("Meditate", "Gym", "Read")
            val ids = names.map {
                repo.saveHabit(Habit(name = it, windowStart = LocalTime.of(7, 0), windowEnd = LocalTime.of(8, 0), basePoints = 10, creatureLine = CreatureLine.CALM, daysMask = EVERY_DAY_MASK, createdDate = start))
            }
            val t1 = repo.saveTask(Task(name = "Passport", dueDate = start.plusDays(9), basePoints = 10, createdDate = start))
            val t2 = repo.saveTask(Task(name = "Bank", dueDate = start.plusDays(8), basePoints = 10, createdDate = start))
            repo.saveTask(Task(name = "Taxes", dueDate = start.plusDays(9), basePoints = 10, createdDate = start))
            for (d in 0L..10L) {
                val day = start.plusDays(d)
                at(day, 7)
                if (d % 4 != 3L) repo.tickHabit(ids[0])
                if (d % 2 == 0L) repo.tickHabit(ids[1])
                if (d != 5L) repo.tickHabit(ids[2])
                if (d == 7L) repo.completeTask(t1)
                if (d == 10L) repo.completeTask(t2)
            }
        }
        val out = File(System.getProperty("renderDir") ?: "build/render").apply { mkdirs() }
        compose.setContent { HabitQuestTheme { SummaryScreen(SummaryViewModel(repo)) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "summary-week-overview.png")
        compose.onAllNodesWithText("Habits").onFirst().performClick()
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "summary-week-habits.png")
        compose.onNodeWithText("Month").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "summary-month-habits.png")
        compose.onNodeWithText("Overview").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "summary-month-overview.png")
        db.close()
    }

    private fun save(dir: File, name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
