package com.ayushpant.habitquest.balance

import com.ayushpant.habitquest.balance.Dimension.BODY
import com.ayushpant.habitquest.balance.Dimension.MIND
import com.ayushpant.habitquest.balance.Dimension.SOUL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceTest {
    @Test
    fun `suggestions from the name - the examples from the request`() {
        assertEquals(setOf(MIND, SOUL), suggestDimensions("Meditate with Medito"))
        assertEquals(setOf(BODY, MIND), suggestDimensions("Gym"))
        assertEquals(setOf(BODY, MIND), suggestDimensions("Morning run"))
        assertEquals(setOf(MIND), suggestDimensions("Read 20 pages"))
        assertEquals(setOf(MIND, SOUL), suggestDimensions("Journal before bed"))
        assertEquals(setOf(SOUL), suggestDimensions("Call family"))
        assertEquals(setOf(BODY, MIND, SOUL), suggestDimensions("Yoga"))
    }

    @Test
    fun `a stem must start a word`() {
        assertTrue(suggestDimensions("Brunch with Tom").isEmpty()) // "run" inside "brunch"
        assertEquals(setOf(BODY, MIND), suggestDimensions("Running"))
        assertTrue(suggestDimensions("Renew passport").isEmpty())
    }

    @Test
    fun `mask round trip`() {
        assertEquals(setOf(MIND, SOUL), dimensionsOf(maskOf(setOf(MIND, SOUL))))
        assertEquals(0, maskOf(emptySet()))
    }

    @Test
    fun `meters share each item between its dimensions`() {
        val meditate = maskOf(setOf(MIND, SOUL))
        val gym = maskOf(setOf(BODY, MIND))
        val scores = balance(
            listOf(
                BalanceItem(meditate, planned = true, done = true, points = 12),
                BalanceItem(gym, planned = true, done = false, points = 0),
            )
        ).associateBy { it.dimension }
        // Mind: planned 0.5 + 0.5, done 0.5 → 50 %. Soul: 0.5 of 0.5 → 100 %. Body: 0 of 0.5 → 0 %.
        assertEquals(0.5, scores[MIND]!!.rate!!, 1e-9)
        assertEquals(1.0, scores[SOUL]!!.rate!!, 1e-9)
        assertEquals(0.0, scores[BODY]!!.rate!!, 1e-9)
        assertEquals(6, scores[MIND]!!.points)
        assertEquals(6, scores[SOUL]!!.points)
    }

    @Test
    fun `nothing planned gives no rate, and an extra done item only helps`() {
        val read = maskOf(setOf(MIND))
        assertNull(balance(emptyList()).first().rate)
        val scores = balance(listOf(BalanceItem(read, planned = false, done = true, points = 10))).associateBy { it.dimension }
        assertEquals(1.0, scores[MIND]!!.rate!!, 1e-9)
        assertNull(scores[BODY]!!.rate)
    }
}
