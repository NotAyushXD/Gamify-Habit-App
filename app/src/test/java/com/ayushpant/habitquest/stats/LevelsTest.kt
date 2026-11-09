package com.ayushpant.habitquest.stats

import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.DimensionScore
import com.ayushpant.habitquest.balance.maskOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {
    @Test
    fun `levels start at 0, 50, 150, 300, 500`() {
        assertEquals(listOf(0, 50, 150, 300, 500), (1..5).map { levelStart(it) })
        assertEquals(1, levelFor(0).number)
        assertEquals(1, levelFor(49).number)
        assertEquals(2, levelFor(50).number)
        assertEquals(4, levelFor(320).number)
    }

    @Test
    fun `level progress and titles`() {
        val l = levelFor(320) // level 4: 300..500
        assertEquals(20, l.into)
        assertEquals(200, l.span)
        assertEquals(180, l.toNext)
        assertEquals("Starter", l.title)
        assertEquals("Beginner", levelFor(0).title)
        assertEquals("Steady", levelFor(levelStart(5)).title)
    }

    @Test
    fun `milestones`() {
        assertEquals(7, streakMilestone(7))
        assertNull(streakMilestone(8))
    }

    private val meditate = maskOf(setOf(Dimension.MIND, Dimension.SOUL))
    private val gym = maskOf(setOf(Dimension.BODY, Dimension.MIND))

    @Test
    fun `nudge - promise first, then the weakest dimension, then perfect day`() {
        val habits = listOf(
            NudgeHabit("Meditate", scheduled = true, done = true, mbsMask = meditate),
            NudgeHabit("Gym", scheduled = true, done = false, mbsMask = gym),
        )
        val body0 = listOf(DimensionScore(Dimension.BODY, planned = 0.5, done = 0.0, points = 0))
        assertEquals("💪 Your body is at 0% today. Gym would lift it.", dailyNudge(habits, body0, 10))
        assertEquals("⭐ One more for a perfect day: Gym (+10 bonus).", dailyNudge(habits, emptyList(), 10))
        val promised = habits + NudgeHabit("Read", scheduled = false, done = false, mbsMask = 1, promisedAt = "19:00")
        assertTrue(dailyNudge(promised, body0, 10)!!.startsWith("🤝 Today you keep your promise: Read"))
        assertEquals("🌟 Perfect day! Everything you planned is done.", dailyNudge(habits.map { it.copy(done = true) }, emptyList(), 10))
        assertNull(dailyNudge(emptyList(), emptyList(), 10))
    }

    @Test
    fun `quote of the day rotates`() {
        assertEquals(DAILY_QUOTES[0], quoteOfTheDay(1))
        assertEquals(DAILY_QUOTES[0], quoteOfTheDay(DAILY_QUOTES.size + 1))
    }
}
