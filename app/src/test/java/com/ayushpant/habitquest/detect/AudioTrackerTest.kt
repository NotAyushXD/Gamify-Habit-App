package com.ayushpant.habitquest.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackerTest {
    private val min = 60_000L
    private val medito = "meditofoundation.medito"

    @Test
    fun `a 12 minute session is suggested a minute after the audio stops, with evidence`() {
        val t = AudioTracker()
        t.onState(medito, playing = true, time = 0)
        t.onState(medito, playing = false, time = 12 * min)
        assertTrue(t.finished(12 * min + 30_000).isEmpty()) // still in the grace period
        assertEquals(13 * min, t.nextCheck())
        val s = t.finished(13 * min).single()
        assertEquals(0L, s.start)
        assertEquals(12 * min, s.end)
        assertTrue(s.evidence)
        assertNull(t.nextCheck())
    }

    @Test
    fun `a short pause does not end the session, and pauses are not counted`() {
        val t = AudioTracker()
        t.onState(medito, true, 0)
        t.onState(medito, false, 4 * min)
        t.onState(medito, true, 4 * min + 30_000) // resumed within the grace period
        t.onState(medito, false, 8 * min)
        val s = t.finished(10 * min).single()
        assertEquals(8 * min, s.end)
        assertEquals(7 * min + 30_000, s.playedMillis)
    }

    @Test
    fun `under 2 minutes is not suggested, under 5 has no evidence`() {
        val t = AudioTracker()
        t.onState(medito, true, 0)
        t.onState(medito, false, 90_000)
        assertTrue(t.finished(10 * min).isEmpty())

        t.onState(medito, true, 20 * min)
        t.onState(medito, false, 23 * min)
        val s = t.finished(30 * min).single()
        assertFalse(s.evidence)
    }

    @Test
    fun `still playing is never finished`() {
        val t = AudioTracker()
        t.onState(medito, true, 0)
        assertTrue(t.finished(60 * min).isEmpty())
        assertTrue(t.isActive(medito))
    }
}
