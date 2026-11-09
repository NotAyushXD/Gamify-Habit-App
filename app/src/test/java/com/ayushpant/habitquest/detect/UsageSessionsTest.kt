package com.ayushpant.habitquest.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSessionsTest {
    private val min = 60_000L
    private val medito = "meditofoundation.medito"
    private val watched = setOf(medito)
    private fun fg(t: Long, pkg: String = medito) = UsageEvent(pkg, t, UsageKind.FOREGROUND)
    private fun bg(t: Long, pkg: String = medito) = UsageEvent(pkg, t, UsageKind.BACKGROUND)

    @Test
    fun `an app open 14 minutes is one session`() {
        val s = usageSessions(listOf(fg(0), bg(14 * min)), watched, now = 60 * min).single()
        assertEquals(UsageSession(medito, 0, 14 * min), s)
    }

    @Test
    fun `a 1 minute look at another app does not split the session`() {
        val events = listOf(fg(0), bg(5 * min), fg(6 * min), bg(12 * min))
        assertEquals(UsageSession(medito, 0, 12 * min), usageSessions(events, watched, 60 * min).single())
    }

    @Test
    fun `a long break makes two sessions, short ones are dropped`() {
        val events = listOf(fg(0), bg(10 * min), fg(30 * min), bg(31 * min), fg(50 * min), bg(55 * min))
        val s = usageSessions(events, watched, 120 * min)
        assertEquals(listOf(0L to 10 * min, 50 * min to 55 * min), s.map { it.start to it.end })
    }

    @Test
    fun `screen off ends the time on screen`() {
        val events = listOf(fg(0), UsageEvent(null, 8 * min, UsageKind.SCREEN_OFF))
        assertEquals(8 * min, usageSessions(events, watched, 60 * min).single().end)
    }

    @Test
    fun `a session still going on, or just ended, is not returned yet`() {
        assertTrue(usageSessions(listOf(fg(0)), watched, 30 * min).isEmpty())
        assertTrue(usageSessions(listOf(fg(0), bg(10 * min)), watched, 11 * min).isEmpty()) // could resume
    }

    @Test
    fun `other apps are ignored`() {
        assertTrue(usageSessions(listOf(fg(0, "com.whatsapp"), bg(30 * min, "com.whatsapp")), watched, 60 * min).isEmpty())
    }
}
