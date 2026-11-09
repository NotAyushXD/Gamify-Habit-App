package com.ayushpant.habitquest.stats

import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/*
 * The motivating parts of the Summary page: a praise headline, a comparison with the last period,
 * highlights and badges. Rule for all of them: never negative, never shaming. A weak period gets
 * encouragement, and a comparison is only shown when this period is as good or better.
 */

data class Praise(val title: String, val message: String)

/** [when_] is how the period reads in a sentence: "today", "this week", "that month", … */
fun praise(s: Summary, when_: String): Praise {
    val rate = s.habitRate
    val wins = s.done + s.habits.sumOf { it.extra }
    val tasks = s.tasks.completed
    return when {
        rate == null && wins == 0 && tasks == 0 ->
            Praise("Ready when you are", "Add a habit or a task and get your first win.")
        rate == null && tasks > 0 ->
            Praise("Getting things done", "You finished $tasks task${plural(tasks)} $when_. That's real progress.")
        rate != null && rate >= 0.9 ->
            Praise("Outstanding!", "You showed up for ${s.done} of ${s.scheduled} planned habits $when_. This is who you are now.")
        rate != null && rate >= 0.7 ->
            Praise("Strong work", "${s.done} of ${s.scheduled} done $when_. Consistency is building, one day at a time.")
        rate != null && rate >= 0.4 ->
            Praise("You kept going", "$wins win${plural(wins)} $when_. Every single one counts.")
        wins > 0 ->
            Praise("Small steps add up", "$wins win${plural(wins)} on the board $when_. Showing up is the hardest part, and you did.")
        else ->
            Praise("Fresh start", "Every day is a new chance. One small win is enough to begin.")
    }
}

/**
 * Lines like "+12% more consistent than last week" ([than] = "last week"). Only things that are as good or better than
 * [previous] are listed; an empty list means nothing is shown.
 */
fun compare(current: Summary, previous: Summary?, than: String): List<String> {
    if (previous == null) return emptyList()
    val lines = mutableListOf<String>()
    val now = current.habitRate
    val before = previous.habitRate
    if (now != null && before != null) {
        val diff = ((now - before) * 100).roundToInt()
        when {
            diff > 0 -> lines += "+$diff% more consistent than $than"
            diff == 0 && now > 0 -> lines += "As consistent as $than. Steady!"
        }
    }
    if (current.perfectDays > previous.perfectDays) {
        lines += "${current.perfectDays - previous.perfectDays} more perfect day${plural(current.perfectDays - previous.perfectDays)} than $than"
    }
    if (current.points > previous.points && previous.points > 0) {
        lines += "+${current.points - previous.points} points more than $than"
    }
    if (current.tasks.completedOnTime > previous.tasks.completedOnTime) {
        val more = current.tasks.completedOnTime - previous.tasks.completedOnTime
        lines += "$more more task${plural(more)} on time than $than"
    }
    return lines
}

data class Highlight(val emoji: String, val title: String, val value: String)

private val BEST_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

fun highlights(s: Summary): List<Highlight> = buildList {
    s.habits.maxByOrNull { it.currentStreak }?.takeIf { it.currentStreak > 0 }?.let {
        add(Highlight("🔥", "Longest streak now", "${it.habit.name}: ${it.currentStreak} day${plural(it.currentStreak)}"))
    }
    if (s.plannedDays > 0) add(Highlight("⭐", "Perfect days", "${s.perfectDays} of ${s.plannedDays}"))
    s.bestDay?.takeIf { s.from != s.to }?.let { (day, points) ->
        add(Highlight("🏆", "Best day", "${day.format(BEST_DAY)}: +$points"))
    }
    val wins = s.done + s.habits.sumOf { it.extra }
    if (wins > 0) add(Highlight("✅", "Habit wins", "$wins"))
    if (s.tasks.completed > 0) add(Highlight("📋", "Tasks finished", "${s.tasks.completed} (${s.tasks.completedOnTime} on time)"))
    if (s.tasks.completedEarly > 0) add(Highlight("⚡", "Finished early", "${s.tasks.completedEarly} task${plural(s.tasks.completedEarly)}"))
    if (s.comebacks > 0) add(Highlight("💪", "Comebacks", "${s.comebacks} time${plural(s.comebacks)} you came back after a miss"))
    if (s.promisesKept > 0) add(Highlight("🤝", "Promises kept", "${s.promisesKept} of ${s.promisesDecided}"))
}

data class Badge(
    val emoji: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
) {
    val earned get() = progress >= target
    val ratio get() = (progress.toFloat() / target).coerceAtMost(1f)
}

/** Badges for the period. Earned ones first, then the closest to being earned. */
fun badges(s: Summary): List<Badge> {
    val wins = s.done + s.habits.sumOf { it.extra }
    val streak = s.habits.maxOfOrNull { it.currentStreak } ?: 0
    return listOf(
        Badge("🌱", "First step", "Do a habit", wins, 1),
        Badge("⭐", "Perfect day", "Do every planned habit in one day", s.perfectDays, 1),
        Badge("🌟", "Five perfect days", "5 perfect days", s.perfectDays, 5),
        Badge("🔥", "On fire", "A 7-day streak", streak, 7),
        Badge("🚀", "Unstoppable", "A 30-day streak", streak, 30),
        Badge("💪", "Comeback", "Come back after a miss", s.comebacks, 1),
        Badge("🤝", "True to my word", "Keep 3 promises", s.promisesKept, 3),
        Badge("🐦", "Early bird", "Finish 3 tasks before they are due", s.tasks.completedEarly, 3),
        Badge("🎯", "Task master", "Finish 5 tasks on time", s.tasks.completedOnTime, 5),
        Badge("💯", "Century", "Earn 100 points", s.points, 100),
        Badge("🏅", "500 club", "Earn 500 points", s.points, 500),
    ).sortedWith(compareByDescending<Badge> { it.earned }.thenByDescending { it.ratio })
}

private fun plural(n: Int) = if (n == 1) "" else "s"
