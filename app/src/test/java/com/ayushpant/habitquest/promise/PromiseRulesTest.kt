package com.ayushpant.habitquest.promise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PromiseRulesTest {
    private val today = LocalDate.of(2026, 11, 9) // a Monday

    @Test
    fun `one miss needs a tap, more misses need the oath`() {
        assertFalse(oathRequired(priorMisses = 0))
        assertTrue(oathRequired(priorMisses = 1))
        assertTrue(oathRequired(priorMisses = 5))
    }

    @Test
    fun `oath sentence names the day and time`() {
        assertEquals("I promise I will Meditate tomorrow at 07:00", oathSentence("Meditate", today.plusDays(1), LocalTime.of(7, 0), today))
        assertEquals("I promise I will Gym on Thursday 12 November at 18:30", oathSentence("Gym ", today.plusDays(3), LocalTime.of(18, 30), today))
    }

    @Test
    fun `typed oath ignores capitals and punctuation, not missing words`() {
        val s = "I promise I will Meditate tomorrow at 07:00"
        assertTrue(typedOathMatches("i promise i will meditate tomorrow at 07 00", s))
        assertTrue(typedOathMatches("  I promise, I will MEDITATE tomorrow at 07:00! ", s))
        assertFalse(typedOathMatches("I promise I will meditate", s))
        assertFalse(typedOathMatches("ok", s))
    }

    @Test
    fun `spoken oath needs promise and the habit name`() {
        assertTrue(spokenOathMatches("I promise I will meditate tomorrow at seven", "Meditate"))
        assertTrue(spokenOathMatches("i promise i'll go to the gym tomorrow at 6:30", "Gym"))
        assertTrue(spokenOathMatches("I promise to read 20 pages tomorrow", "Read 20 pages"))
        assertFalse(spokenOathMatches("I will meditate tomorrow", "Meditate")) // no "promise"
        assertFalse(spokenOathMatches("I promise to do it tomorrow", "Meditate")) // no habit
    }
}
