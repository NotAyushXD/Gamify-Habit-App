package com.ayushpant.habitquest.detect

import com.ayushpant.habitquest.scoring.ScoringConfig

/*
 * Catch-up (spec 5.2): turns Android's app-usage events (an app came to the screen / left it /
 * the screen went off) into sessions of the linked apps. Pure Kotlin, unit tested.
 */

enum class UsageKind { FOREGROUND, BACKGROUND, SCREEN_OFF }

data class UsageEvent(val packageName: String?, val time: Long, val kind: UsageKind)

/** A time the app was on screen. Times in epoch milliseconds. */
data class UsageSession(val packageName: String, val start: Long, val end: Long)

/**
 * Sessions of [packages] in [events] (oldest first) that are over by [now]. Gaps under
 * [ScoringConfig.usageGapMinutes] are joined (a quick look at a message is not the end); sessions
 * shorter than [ScoringConfig.catchUpMinMinutes] are dropped. A session still going on is not returned.
 */
fun usageSessions(
    events: List<UsageEvent>,
    packages: Set<String>,
    now: Long,
    cfg: ScoringConfig = ScoringConfig.DEFAULT,
): List<UsageSession> {
    val gap = cfg.usageGapMinutes * 60_000
    val spans = mutableMapOf<String, MutableList<LongArray>>() // per app: [start, end]
    val openSince = mutableMapOf<String, Long>()

    fun close(pkg: String, time: Long) {
        val start = openSince.remove(pkg) ?: return
        if (time > start) spans.getOrPut(pkg) { mutableListOf() } += longArrayOf(start, time)
    }

    for (e in events) {
        when (e.kind) {
            UsageKind.FOREGROUND -> if (e.packageName in packages) openSince.putIfAbsent(e.packageName!!, e.time)
            UsageKind.BACKGROUND -> if (e.packageName in packages) close(e.packageName!!, e.time)
            UsageKind.SCREEN_OFF -> openSince.keys.toList().forEach { close(it, e.time) }
        }
    }

    val result = mutableListOf<UsageSession>()
    for ((pkg, list) in spans) {
        var start = list.first()[0]
        var end = list.first()[1]
        fun emit() {
            val stillGoing = pkg in openSince || now - end < gap
            if (!stillGoing && end - start >= cfg.catchUpMinMinutes * 60_000) result += UsageSession(pkg, start, end)
        }
        for (span in list.drop(1)) {
            if (span[0] - end < gap) {
                end = maxOf(end, span[1])
            } else {
                emit()
                start = span[0]
                end = span[1]
            }
        }
        emit()
    }
    return result.sortedBy { it.start }
}
