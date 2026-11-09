package com.ayushpant.habitquest.scoring

import java.time.LocalTime

/**
 * Every scoring value from spec section 7, in one place.
 *
 * These are the v1 defaults. To tune the game after tests, change the numbers here
 * and nowhere else. Tests can pass a modified copy, e.g. `ScoringConfig(dailyBonus = 20)`.
 */
data class ScoringConfig(
    // 7.1 Base points: the same for every habit and task. Not user-adjustable, so the points
    // stay fair and cannot be inflated (decision after the spec, which allowed 5–20).
    val basePoints: Int = 10,

    // 7.1 Timing factor
    val timingInWindow: Double = 1.0,
    val timingNearWindow: Double = 0.6,
    val nearWindowMinutes: Long = 60,
    val timingOtherTime: Double = 0.3,

    // 7.1 Evidence factor
    val evidenceFound: Double = 1.2,
    val evidenceNone: Double = 1.0,

    // 7.2 Streak multiplier: (minimum streak days, multiplier), checked from the top.
    val streakTiers: List<Pair<Int, Double>> = listOf(
        30 to 2.0,
        14 to 1.5,
        7 to 1.2,
        1 to 1.0,
    ),

    // 7.3 Missed days: the first misses of a week use a freeze (streak kept); any other miss
    // resets the streak to 0 (decision after the spec, which halved it and never went below 1).
    val freezesPerWeek: Int = 1,
    val comebackBonus: Int = 5,

    // Promise (decision after the spec): doing the habit on the day you promised gives this extra.
    val promiseKeptBonus: Int = 5,

    // 7.4 Daily bonus (habits only; tasks never count toward it)
    val dailyBonus: Int = 10,

    // Tasks (one-off, with a due date; not in the spec, see CLAUDE.md).
    // Points = Base × task factor. Done on or before the due date gets the on-time bonus.
    val taskOnTime: Double = 1.2,
    // Done late: (minimum days late, factor), checked from the top. Never 0, so finishing always pays.
    val taskLateTiers: List<Pair<Int, Double>> = listOf(
        14 to 0.2,
        7 to 0.4,
        3 to 0.6,
        1 to 0.8,
    ),

    // App habits (spec 5.1, 5.5): audio from the linked app is the main signal.
    /** Audio must stop for this long before the session counts as ended (a pause is not the end). */
    val audioEndGraceSeconds: Long = 60,
    /** Less audio than this is not suggested at all (a preview, a mistake). */
    val minDetectedMinutes: Long = 2,
    /** This much audio is "evidence" for the ×1.2 evidence factor. */
    val evidenceAudioMinutes: Long = 5,

    // Catch-up (spec 5.2, 6.3): sessions found later in Android's app-usage record.
    /** Leaving the app for less than this does not end the session (spec 5.1 step 7: 2 minutes). */
    val usageGapMinutes: Long = 2,
    /** The app must be on screen at least this long to count. */
    val catchUpMinMinutes: Long = 3,
    /** How far back the catch-up looks the first time (Android keeps a few days). */
    val catchUpLookbackHours: Long = 48,

    // Zone habits (spec 5.3).
    /** Staying in the zone this long counts as "arrived" (dwell); driving past does not. */
    val zoneDwellMinutes: Long = 5,
    /** This much time in the zone is "evidence" for the ×1.2 evidence factor. */
    val zoneEvidenceMinutes: Long = 30,
    /** Smallest zone radius (spec 1.1: 150 m or more; geofences are not precise below that). */
    val minZoneRadiusMeters: Int = 150,

    // Levels (decision after the spec, brought forward from Step 5): lifetime points give a level.
    // Level n starts at levelStep × n × (n − 1) / 2 points: 0, 50, 150, 300, 500, 750, …
    val levelStep: Int = 50,
    // (first level, title), checked from the top.
    val levelTitles: List<Pair<Int, String>> = listOf(
        20 to "Legend",
        16 to "Unstoppable",
        12 to "Disciplined",
        8 to "Committed",
        5 to "Steady",
        3 to "Starter",
        1 to "Beginner",
    ),
    // Streak lengths that get a big celebration.
    val streakMilestones: List<Int> = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 365),

    // 6.3 Day close runs at this time and closes the previous day.
    val dayCloseTime: LocalTime = LocalTime.of(0, 5),
) {
    companion object {
        val DEFAULT = ScoringConfig()
    }
}
