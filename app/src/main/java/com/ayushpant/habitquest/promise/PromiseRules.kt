package com.ayushpant.habitquest.promise

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * Rules for "Can't today, I promise" (decided with the developer after the spec):
 * - Missing a habit once: a tap is enough to promise.
 * - Missed before today too: the promise only counts once the user types or says the oath.
 * - A kept promise (habit done on the promised day) gives a bonus; a broken one is only recorded.
 * Pure Kotlin, unit tested.
 */

/** [priorMisses] = planned days missed in a row before today. Today's miss alone needs only a tap. */
fun oathRequired(priorMisses: Int): Boolean = priorMisses >= 1

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM")

/** "I promise I will Meditate tomorrow at 07:00" */
fun oathSentence(habitName: String, dueDay: LocalDate, dueTime: LocalTime, today: LocalDate): String {
    val day = if (dueDay == today.plusDays(1)) "tomorrow" else "on ${dueDay.format(DAY)}"
    return "I promise I will ${habitName.trim()} $day at ${dueTime.format(TIME)}"
}

/** Lower case, only letters, digits and single spaces: "I promise, I'll…" and "i promise i'll" compare equal. */
fun normalise(text: String): String =
    text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

/** Typing must match the sentence word for word (punctuation and capitals do not matter). */
fun typedOathMatches(typed: String, sentence: String): Boolean = normalise(typed) == normalise(sentence)

/**
 * Speech recognisers write times and dates in many ways ("7", "seven", "7:00"), so a spoken oath
 * counts when it says "promise" and every word of the habit name.
 */
fun spokenOathMatches(spoken: String, habitName: String): Boolean {
    val words = normalise(spoken).split(" ").toSet()
    val nameWords = normalise(habitName).split(" ").filter { it.length >= 2 }
    return "promise" in words && nameWords.all { name -> words.any { it.startsWith(name) || name.startsWith(it) && it.length >= 4 } }
}
