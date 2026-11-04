package com.ayushpant.habitquest.scoring

import java.time.LocalTime

/**
 * Every scoring value from spec section 7, in one place.
 *
 * These are the v1 defaults. To tune the game after tests, change the numbers here
 * and nowhere else. Tests can pass a modified copy, e.g. `ScoringConfig(dailyBonus = 20)`.
 */
data class ScoringConfig(
    // 7.1 Base points
    val defaultBasePoints: Int = 10,
    val minBasePoints: Int = 5,
    val maxBasePoints: Int = 20,

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

    // 7.3 Missed days
    val freezesPerWeek: Int = 1,
    val decayDivisor: Int = 2,
    val streakFloorAfterDecay: Int = 1,
    val comebackBonus: Int = 5,

    // 7.4 Daily bonus
    val dailyBonus: Int = 10,

    // 6.3 Day close runs at this time and closes the previous day.
    val dayCloseTime: LocalTime = LocalTime.of(0, 5),
) {
    companion object {
        val DEFAULT = ScoringConfig()
    }
}
