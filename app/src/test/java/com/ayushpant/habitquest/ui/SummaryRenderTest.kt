package com.ayushpant.habitquest.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
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
import com.ayushpant.habitquest.balance.maskOf
import com.ayushpant.habitquest.balance.suggestDimensions
import com.ayushpant.habitquest.data.AppDatabase
import com.ayushpant.habitquest.promise.PromiseScreen
import com.ayushpant.habitquest.reminders.PromiseContext
import com.ayushpant.habitquest.data.CreatureLine
import com.ayushpant.habitquest.data.Habit
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.OwnedCreature
import com.ayushpant.habitquest.data.Task
import com.ayushpant.habitquest.scoring.EVERY_DAY_MASK
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
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
                repo.saveHabit(Habit(name = it, windowStart = LocalTime.of(7, 0), windowEnd = LocalTime.of(8, 0), basePoints = 10, creatureLine = CreatureLine.CALM, daysMask = EVERY_DAY_MASK, createdDate = start, mbsMask = maskOf(suggestDimensions(it))))
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
        val today = mutableStateOf(false)
        val summaryVm = SummaryViewModel(repo)
        val todayVm = TodayViewModel(repo)
        compose.setContent {
            HabitQuestTheme {
                if (today.value) TodayScreen(todayVm, onAdd = {}, onEditHabit = {}, onEditTask = {}) else SummaryScreen(summaryVm)
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "summary-week.png")
        compose.onNodeWithText("Habit rings").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "summary-week-habit-rings.png")
        compose.onNodeWithText("Month").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "summary-month.png")
        today.value = true
        compose.mainClock.advanceTimeBy(2_000)
        save(out, "today.png")
        // The Today view model runs the day-close task in the background: stop it before closing the database.
        todayVm.viewModelScope.cancel()
        runBlocking { delay(300) }
        db.close()
    }

    @Test
    fun renderSettings() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = HabitRepository(db)
        val reminders = com.ayushpant.habitquest.reminders.ReminderRepository(db, repo)
        compose.setContent { HabitQuestTheme { SettingsScreen(reminders) } }
        compose.mainClock.advanceTimeBy(1_000)
        save(File("build/render").apply { mkdirs() }, "settings.png")
        db.close()
    }

    @Test
    fun renderCollection() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = HabitRepository(db)
        runBlocking {
            repo.closePendingDays() // makes the first egg
            val day = LocalDate.of(2026, 11, 10)
            val creatures = com.ayushpant.habitquest.rewards.Themes.CREATURES.creatures
            listOf(0, 4, 8).forEachIndexed { i, c ->
                db.creatures().insert(OwnedCreature(themeId = com.ayushpant.habitquest.rewards.Themes.DEFAULT_ID, creatureId = creatures[c].id, hatchDay = day, companion = i == 0))
            }
        }
        compose.setContent { HabitQuestTheme { CollectionScreen(repo, onBack = {}) } }
        compose.mainClock.advanceTimeBy(1_000)
        save(File("build/render").apply { mkdirs() }, "collection.png")
        db.close()
    }

    @Test
    fun renderEditor() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        compose.setContent { HabitQuestTheme { EditorScreen(HabitRepository(db), ItemKind.HABIT, 0L, onDone = {}) } }
        compose.mainClock.advanceTimeBy(1_000)
        save(File("build/render").apply { mkdirs() }, "editor-new.png")
        db.close()
    }

    @Test
    fun renderWelcome() {
        compose.setContent { HabitQuestTheme { WelcomeScreen {} } }
        compose.mainClock.advanceTimeBy(500)
        save(File("build/render").apply { mkdirs() }, "welcome-1.png")
        compose.onNodeWithText("Next").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        save(File("build/render"), "welcome-2.png")
    }

    @Test
    fun renderGuide() {
        compose.setContent { HabitQuestTheme { GuideScreen(onBack = {}, onAction = {}) } }
        compose.onNodeWithText("Points").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        save(File("build/render").apply { mkdirs() }, "guide.png")
    }

    @Test
    fun renderPromise() {
        val out = File("build/render").apply { mkdirs() }
        val ctx = PromiseContext(logId = 1, habitId = 1, habitName = "Meditate", suggestedTime = LocalTime.of(7, 0), priorMisses = 2, oathRequired = true)
        compose.setContent {
            HabitQuestTheme { PromiseScreen(ctx, LocalDate.of(2026, 11, 9), onPromise = { _, _, _ -> }, onBack = {}) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        save(out, "promise.png")
    }

    private fun save(dir: File, name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
