package com.ayushpant.habitquest.balance

/*
 * Mind / Body / Soul. Every habit and task has one or more dimensions (stored as a bit mask).
 * When it is done, its points are shared equally between them. The meters show, per dimension,
 * how much of what was planned got done. Pure Kotlin, unit tested.
 */

enum class Dimension(val bit: Int, val label: String, val emoji: String) {
    MIND(1, "Mind", "🧠"),
    BODY(2, "Body", "💪"),
    SOUL(4, "Soul", "✨"),
}

fun dimensionsOf(mask: Int): Set<Dimension> = Dimension.entries.filter { mask and it.bit != 0 }.toSet()

fun maskOf(dimensions: Set<Dimension>): Int = dimensions.sumOf { it.bit }

private val M = Dimension.MIND
private val B = Dimension.BODY
private val S = Dimension.SOUL

/**
 * Word stems and what they feed. The first stems that appear in the name win; longer, more
 * specific stems come first (e.g. "cold shower" before "shower").
 */
private val KEYWORDS: List<Pair<List<String>, Set<Dimension>>> = listOf(
    listOf("meditat", "mindful", "breathwork", "breathing", "headspace", "medito", "calm app") to setOf(M, S),
    listOf("yoga", "tai chi", "qigong") to setOf(B, M, S),
    listOf("cold shower", "ice bath") to setOf(B, M),
    listOf("gym", "workout", "work out", "exercise", "lift", "weights", "strength", "run", "jog", "sprint", "hiit",
        "crossfit", "push-up", "pushup", "squat", "plank", "sport", "football", "soccer", "cricket", "tennis",
        "badminton", "basketball", "boxing", "martial", "climb", "row") to setOf(B, M),
    listOf("walk", "steps", "hike", "hiking", "trek", "dance", "dancing") to setOf(B, S),
    listOf("swim", "cycle", "cycling", "bike", "stretch", "pilates", "mobility") to setOf(B),
    listOf("sleep", "bed by", "nap") to setOf(B, M),
    listOf("water", "hydrat", "eat", "diet", "meal", "cook", "vegetable", "fruit", "protein", "vitamin", "fast",
        "no sugar", "skincare", "skin care", "teeth", "floss", "shower") to setOf(B),
    listOf("journal", "reflect", "diary") to setOf(M, S),
    listOf("gratitude", "grateful", "thankful", "pray", "prayer", "worship", "church", "temple", "mosque",
        "gurdwara", "spiritual", "mantra", "chant", "affirmation", "bible", "quran", "gita", "scripture") to setOf(S),
    listOf("family", "friend", "call mom", "call dad", "parents", "kids", "partner", "date night", "volunteer",
        "charity", "kindness", "help someone", "nature", "garden", "plant") to setOf(S),
    listOf("music", "piano", "guitar", "violin", "sing", "instrument", "art", "paint", "draw", "sketch", "craft",
        "poem", "poetry") to setOf(M, S),
    listOf("therapy", "counsel") to setOf(M, S),
    listOf("read", "book", "study", "learn", "course", "class", "lecture", "homework", "exam", "revise", "code",
        "coding", "program", "write", "writing", "blog", "language", "duolingo", "spanish", "french", "german",
        "japanese", "hindi", "podcast", "research", "chess", "puzzle", "plan", "focus", "deep work", "no phone",
        "screen time", "digital detox", "news", "budget", "finance", "invest") to setOf(M),
    listOf("clean", "tidy", "declutter", "laundry", "dishes", "organis", "organiz") to setOf(M, B),
)

/** Dimensions suggested from a habit or task name. Empty if nothing matches. */
fun suggestDimensions(name: String): Set<Dimension> {
    val text = " " + name.lowercase().replace(Regex("[^a-z0-9 -]"), " ") + " "
    for ((stems, dims) in KEYWORDS) {
        // A stem must start a word ("run" matches "running", not "brunch").
        if (stems.any { text.contains(" $it") }) return dims
    }
    return emptySet()
}

/** One habit-day or task in the calculation. */
data class BalanceItem(val mask: Int, val planned: Boolean, val done: Boolean, val points: Int)

data class DimensionScore(val dimension: Dimension, val planned: Double, val done: Double, val points: Int) {
    /** Share of the planned work done, 0..1. Null when nothing was planned for this dimension. */
    val rate: Double? get() = if (planned <= 0.0) null else (done / planned).coerceAtMost(1.0)
}

/**
 * Each item counts as 1, shared equally between its dimensions; its points are shared the same way.
 * A done item that was not planned (an extra day) counts as planned too, so it can only help.
 */
fun balance(items: List<BalanceItem>): List<DimensionScore> {
    val planned = DoubleArray(3)
    val done = DoubleArray(3)
    val points = DoubleArray(3)
    for (item in items) {
        val dims = dimensionsOf(item.mask)
        if (dims.isEmpty() || (!item.planned && !item.done)) continue
        val share = 1.0 / dims.size
        for (d in dims) {
            val i = d.ordinal
            planned[i] += share
            if (item.done) {
                done[i] += share
                points[i] += item.points * share
            }
        }
    }
    return Dimension.entries.map { DimensionScore(it, planned[it.ordinal], done[it.ordinal], Math.round(points[it.ordinal]).toInt()) }
}
