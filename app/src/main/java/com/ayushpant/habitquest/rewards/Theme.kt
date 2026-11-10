package com.ayushpant.habitquest.rewards

import com.ayushpant.habitquest.balance.Dimension

/*
 * A theme is DATA ONLY (step 5 rule): the names, the creatures, and the message texts of the
 * reward layer. No rule reads a theme; rules get the creature list as a plain parameter.
 * Every creature is an original design (rule R14) and never looks hungry, sick, sad, or dead (R5).
 */

/** How the companion feels today. All three states look calm or happy. */
enum class CompanionState { ACTIVE, WAITING, RESTING }

/** One creature of a theme. [id] is stored in the database, so never change it once shipped. */
data class CreatureDef(
    val id: String,
    val name: String,
    val dimension: Dimension,
    /** One line about it, shown in the Collection. */
    val blurb: String,
)

/** The words a theme uses. Functions get the companion's name. */
data class ThemeText(
    val eggName: String,
    val coinName: String,
    val companionName: String,
    val questName: String,
    val collectionName: String,
    val firstEgg: String,
    val eggGrowing: String,
    val hatched: (name: String, dimension: Dimension) -> String,
    val fullCollection: String,
    val active: (name: String) -> String,
    val waiting: (name: String) -> String,
    val resting: (name: String) -> String,
    val welcomeBack: (name: String) -> String,
    val chooseCompanion: String,
    val notYet: String,
)

data class Theme(val id: String, val name: String, val text: ThemeText, val creatures: List<CreatureDef>) {
    fun creature(id: String): CreatureDef? = creatures.firstOrNull { it.id == id }
}

object Themes {
    const val DEFAULT_ID = "creatures"

    /** The first theme: a soft nature world. */
    val CREATURES = Theme(
        id = DEFAULT_ID,
        name = "Creature world",
        text = ThemeText(
            eggName = "egg",
            coinName = "coins",
            companionName = "companion",
            questName = "quest",
            collectionName = "Collection",
            firstEgg = "Your first egg. Every coin you earn helps it grow.",
            eggGrowing = "Keep going: every coin grows the egg.",
            hatched = { name, dim -> "$name hatched! A ${dim.label} creature joined your collection." },
            fullCollection = "Your collection is complete! +100 coins instead of a new creature.",
            active = { name -> "$name is cheering you on today." },
            waiting = { name -> "$name is waiting for today's first habit." },
            resting = { name -> "$name is resting, ready whenever you are." },
            welcomeBack = { name -> "Welcome back! $name missed you." },
            chooseCompanion = "Choose",
            notYet = "Not hatched yet",
        ),
        creatures = listOf(
            CreatureDef("lumi", "Lumi", Dimension.MIND, "A glow-moth that lights up when you learn something."),
            CreatureDef("pondra", "Pondra", Dimension.MIND, "A slow, thoughtful turtle who loves a quiet plan."),
            CreatureDef("quillo", "Quillo", Dimension.MIND, "A feathered fox that collects clever ideas."),
            CreatureDef("orbit", "Orbit", Dimension.MIND, "A round little owl that watches the stars."),
            CreatureDef("pebbit", "Pebbit", Dimension.BODY, "A pebble rabbit that hops every morning."),
            CreatureDef("trundle", "Trundle", Dimension.BODY, "A leaf-bear that loves long walks."),
            CreatureDef("zippa", "Zippa", Dimension.BODY, "A spark-lizard, quick and always warm."),
            CreatureDef("bramb", "Bramb", Dimension.BODY, "A bramble boar, sturdy and kind."),
            CreatureDef("mossle", "Mossle", Dimension.SOUL, "A moss sprite that hums when you breathe slowly."),
            CreatureDef("dewin", "Dewin", Dimension.SOUL, "A dew-drop deer that shines after rain."),
            CreatureDef("wisper", "Wisper", Dimension.SOUL, "A wind-cat that curls up next to good thoughts."),
            CreatureDef("solen", "Solen", Dimension.SOUL, "A sun-flower seal that turns toward warmth."),
        ),
    )

    val all: List<Theme> = listOf(CREATURES)

    fun byId(id: String?): Theme = all.firstOrNull { it.id == id } ?: CREATURES
}
