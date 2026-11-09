package com.ayushpant.habitquest.stats

import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.DimensionScore
import com.ayushpant.habitquest.balance.dimensionsOf
import com.ayushpant.habitquest.scoring.ScoringConfig

/*
 * Levels, the daily nudge and the quote of the day. Pure Kotlin, unit tested.
 * Rule as everywhere: never negative, never shaming.
 */

data class Level(
    val number: Int,
    val title: String,
    /** Points earned since this level started. */
    val into: Int,
    /** Points this level needs in total, until the next one. */
    val span: Int,
) {
    val progress: Float get() = if (span == 0) 1f else into.toFloat() / span
    val toNext: Int get() = span - into
}

/** First point total of [level]. */
fun levelStart(level: Int, cfg: ScoringConfig = ScoringConfig.DEFAULT): Int = cfg.levelStep * level * (level - 1) / 2

fun levelFor(totalPoints: Int, cfg: ScoringConfig = ScoringConfig.DEFAULT): Level {
    val points = totalPoints.coerceAtLeast(0)
    var n = 1
    while (levelStart(n + 1, cfg) <= points) n++
    val start = levelStart(n, cfg)
    val title = cfg.levelTitles.firstOrNull { (from, _) -> n >= from }?.second ?: cfg.levelTitles.last().second
    return Level(n, title, points - start, levelStart(n + 1, cfg) - start)
}

/** The next streak milestone reached exactly at [streak], or null. */
fun streakMilestone(streak: Int, cfg: ScoringConfig = ScoringConfig.DEFAULT): Int? = streak.takeIf { it in cfg.streakMilestones }

/** A habit as the nudge sees it today. */
data class NudgeHabit(val name: String, val scheduled: Boolean, val done: Boolean, val mbsMask: Int, val promisedAt: String? = null)

/**
 * One short message for the top of Today, most useful first: a promise to keep, how far to a
 * perfect day, or which dimension a habit would lift.
 */
fun dailyNudge(habits: List<NudgeHabit>, balance: List<DimensionScore>, dailyBonus: Int): String? {
    habits.firstOrNull { it.promisedAt != null && !it.done }?.let {
        return "🤝 Today you keep your promise: ${it.name} at ${it.promisedAt}."
    }
    val planned = habits.filter { it.scheduled }
    if (planned.isEmpty()) return null
    val open = planned.filter { !it.done }
    if (open.isEmpty()) return "🌟 Perfect day! Everything you planned is done."
    val weakest = balance.filter { it.rate != null && it.rate!! < 0.5 }.minByOrNull { it.rate!! }
    if (weakest != null) {
        val lifter = open.firstOrNull { weakest.dimension in dimensionsOf(it.mbsMask) }
        if (lifter != null) {
            return "${weakest.dimension.emoji} Your ${weakest.dimension.label.lowercase()} is at ${(weakest.rate!! * 100).toInt()}% today. ${lifter.name} would lift it."
        }
    }
    return if (open.size == 1) "⭐ One more for a perfect day: ${open.single().name} (+$dailyBonus bonus)."
    else "⭐ ${open.size} more for a perfect day (+$dailyBonus bonus). Start with ${open.first().name}."
}

/** Short lines for the quote of the day, written for this app. */
val DAILY_QUOTES = listOf(
    "Small steps, every day. That is the whole secret.",
    "You don't have to be perfect. You have to show up.",
    "The best time was yesterday. The next best time is now.",
    "Discipline is remembering what you want most.",
    "One good day is a start. A good week is a habit.",
    "You are becoming the person who does this.",
    "Progress, not perfection.",
    "Missing once is an accident. Coming back is the habit.",
    "Your future self is watching. Make them proud.",
    "Motivation gets you started. Habits keep you going.",
    "Do it for the calm you feel afterwards.",
    "Ten minutes counts. Ten minutes always counts.",
    "Strong body, clear mind, full soul.",
    "Every tick is a vote for who you want to be.",
    "Be kind to yourself, and keep going.",
    "Hard days count double.",
    "Consistency beats intensity.",
    "Start where you are. Use what you have. Do what you can.",
    "The chain grows one link at a time.",
    "Today is a good day for a good day.",
)

fun quoteOfTheDay(dayOfYear: Int): String = DAILY_QUOTES[(dayOfYear - 1).mod(DAILY_QUOTES.size)]
