package com.ayushpant.habitquest.rewards

import com.ayushpant.habitquest.balance.Dimension

/*
 * A theme is DATA ONLY (step 5 rule): the names, the creatures, and the message texts of the
 * reward layer. No rule reads a theme; rules get the creature list as a plain parameter.
 * Every creature is drawn in our own simple shapes and never looks hungry, sick, sad, or dead (R5).
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
        // Names chosen by the developer, who reports approval from The Pokémon Company (Nov 10, 2026).
        // 6 per dimension; each evolution line stays in one dimension. Blurbs stay calm and kind (R4, R5).
        creatures = listOf(
            // Mind: the water line and the star-bug line.
            CreatureDef("squirtgorgon", "Squirtgorgon", Dimension.MIND, "A little shelled swimmer whose shell bears old, curious markings."),
            CreatureDef("wartorion", "Wartorion", Dimension.MIND, "A wave-riding turtle that reads the tides like a book."),
            CreatureDef("blastochimera", "Blastochimera", Dimension.MIND, "A great calm shell-fortress that thinks before it splashes."),
            CreatureDef("caterstellar", "Caterstellar", Dimension.MIND, "A tiny starry-eyed caterpillar that moves with the night sky."),
            CreatureDef("metapodas", "Metapodas", Dimension.MIND, "A golden chrysalis that glints in the sun while it dreams."),
            CreatureDef("butterfreeas", "Butterfreeas", Dimension.MIND, "An elegant butterfly whose wings send cool, fresh breezes."),
            // Body: the fire line, the spark pair and the stone.
            CreatureDef("charignis", "Charignis", Dimension.BODY, "A small, lively lizard with a warm ember glow."),
            CreatureDef("charmelion", "Charmelion", Dimension.BODY, "A proud flame beast that grows stronger in the sunshine."),
            CreatureDef("charititan", "Charititan", Dimension.BODY, "A big, kind flying titan with warm wings."),
            CreatureDef("pikastellar", "Pikastellar", Dimension.BODY, "A starry little spark creature, always ready to run."),
            CreatureDef("raichura", "Raichura", Dimension.BODY, "A spark beast whose sparks shimmer like northern lights."),
            CreatureDef("geodudonis", "Geodudonis", Dimension.BODY, "A rough little boulder with a flawless, shining heart."),
            // Soul: the leaf line, the spirit, the sleeper and the rainbow.
            CreatureDef("bulbavulpis", "Bulbavulpis", Dimension.SOUL, "A gentle plant-fox with a bud that opens when you rest."),
            CreatureDef("iviscar", "Iviscar", Dimension.SOUL, "A sturdy beetle that keeps a flower safe on its shell."),
            CreatureDef("venusauris", "Venusauris", Dimension.SOUL, "A great jungle friend whose bloom breathes fresh air."),
            CreatureDef("gengachimera", "Gengachimera", Dimension.SOUL, "A playful spirit made of many friendly lights."),
            CreatureDef("snorlaura", "Snorlaura", Dimension.SOUL, "A big sleepy friend that glows softly while it rests."),
            CreatureDef("dragonairis", "Dragonairis", Dimension.SOUL, "A graceful serpent that leaves a trail of rainbow light."),
        ),
    )

    val all: List<Theme> = listOf(CREATURES)

    /**
     * Creature ids of the first draft of this theme, and the creature each one became. A phone
     * that already hatched one keeps it under its new name (see `HabitRepository.renameOldCreatures`).
     */
    val RENAMED_IDS: Map<String, String> = mapOf(
        "lumi" to "squirtgorgon", "pondra" to "wartorion", "quillo" to "blastochimera", "orbit" to "caterstellar",
        "pebbit" to "charignis", "trundle" to "charmelion", "zippa" to "charititan", "bramb" to "pikastellar",
        "mossle" to "bulbavulpis", "dewin" to "iviscar", "wisper" to "venusauris", "solen" to "gengachimera",
    )

    fun byId(id: String?): Theme = all.firstOrNull { it.id == id } ?: CREATURES
}
