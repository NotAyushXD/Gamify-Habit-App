package com.ayushpant.habitquest.detect

import com.ayushpant.habitquest.scoring.ScoringConfig

/*
 * Turns "the app started / stopped playing audio" signals into one suggested session (spec 5.1).
 * Pure Kotlin: the notification listener feeds it, the tests feed it too.
 *
 * A short pause (e.g. the user pauses Medito for 30 s) does not end the session: only when the
 * audio has been stopped for [ScoringConfig.audioEndGraceSeconds] is the session finished.
 */

/** A finished listening session for one app. Times in epoch milliseconds. */
data class DetectedSession(
    val packageName: String,
    val start: Long,
    val end: Long,
    /** Milliseconds the audio actually played (pauses not counted). */
    val playedMillis: Long,
    val evidence: Boolean,
)

class AudioTracker(private val cfg: ScoringConfig = ScoringConfig.DEFAULT) {
    private class Track(var firstStart: Long, var playingSince: Long?, var played: Long, var lastStop: Long)

    private val tracks = mutableMapOf<String, Track>()

    /** The app [pkg] reports its audio state at [time]. */
    fun onState(pkg: String, playing: Boolean, time: Long) {
        val track = tracks[pkg]
        if (playing) {
            when {
                track == null -> tracks[pkg] = Track(time, time, 0, time)
                track.playingSince == null -> track.playingSince = time
            }
        } else if (track?.playingSince != null) {
            track.played += time - track.playingSince!!
            track.playingSince = null
            track.lastStop = time
        }
    }

    /** True while [pkg] is playing or within the grace period after it stopped. */
    fun isActive(pkg: String): Boolean = tracks.containsKey(pkg)

    /** When to look again: the end of the grace period of the app that stopped first, or null. */
    fun nextCheck(): Long? = tracks.values.filter { it.playingSince == null }.minOfOrNull { it.lastStop + cfg.audioEndGraceSeconds * 1000 }

    /**
     * Sessions whose audio has been stopped for the whole grace period at [now]. They are removed
     * from the tracker. Too short ones are dropped (not suggested).
     */
    fun finished(now: Long): List<DetectedSession> {
        val grace = cfg.audioEndGraceSeconds * 1000
        val done = tracks.filter { (_, t) -> t.playingSince == null && now - t.lastStop >= grace }
        done.keys.forEach { tracks.remove(it) }
        return done.mapNotNull { (pkg, t) ->
            if (t.played < cfg.minDetectedMinutes * 60_000) null
            else DetectedSession(pkg, t.firstStart, t.lastStop, t.played, evidence = t.played >= cfg.evidenceAudioMinutes * 60_000)
        }
    }
}
