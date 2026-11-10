package com.ayushpant.habitquest.rewards

import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.maskOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The pure egg, hatch and companion rules of step 5b. */
class EggRulesTest {
    private val creatures = Themes.CREATURES.creatures
    private val today = LocalDate.of(2026, 11, 10)
    private fun idsOf(d: Dimension) = creatures.filter { it.dimension == d }.map { it.id }.toSet()

    @Test
    fun `the first egg needs 100 and starts at 20, later eggs need 300 and start at 30`() {
        assertEquals(100 to 20, eggTarget(0))
        assertEquals(300 to 30, eggTarget(1))
        assertEquals(300 to 30, eggTarget(7))
    }

    @Test
    fun `growth is shared equally between the dimensions and always adds up`() {
        assertEquals(mapOf(Dimension.MIND to 10), splitGrowth(10, maskOf(setOf(Dimension.MIND))))
        assertEquals(mapOf(Dimension.MIND to 6, Dimension.SOUL to 6), splitGrowth(12, maskOf(setOf(Dimension.MIND, Dimension.SOUL))))
        val odd = splitGrowth(11, maskOf(setOf(Dimension.BODY, Dimension.SOUL)))
        assertEquals(11, odd.values.sum())
        assertEquals(6, odd[Dimension.BODY]) // the remainder goes to the first dimension
        // No dimension (the day bonus): all three.
        assertEquals(mapOf(Dimension.MIND to 4, Dimension.BODY to 3, Dimension.SOUL to 3), splitGrowth(10, 0))
    }

    @Test
    fun `the dimension that gave the most growth decides the creature`() {
        val growth = mapOf(Dimension.MIND to 20, Dimension.BODY to 50, Dimension.SOUL to 10)
        assertEquals(Dimension.BODY, hatchDimension(growth, emptyMap()))
    }

    @Test
    fun `a tie goes to the dimension with fewer creatures, never against more growth`() {
        val tie = mapOf(Dimension.MIND to 40, Dimension.BODY to 40, Dimension.SOUL to 0)
        assertEquals(Dimension.BODY, hatchDimension(tie, mapOf(Dimension.MIND to 2, Dimension.BODY to 1)))
        assertEquals(Dimension.MIND, hatchDimension(tie, mapOf(Dimension.MIND to 1, Dimension.BODY to 1))) // still equal: Mind first
        val moreMind = mapOf(Dimension.MIND to 41, Dimension.BODY to 40, Dimension.SOUL to 0)
        assertEquals(Dimension.MIND, hatchDimension(moreMind, mapOf(Dimension.MIND to 4)))
    }

    @Test
    fun `the hatchling is one the user does not have, from another dimension when that one is complete`() {
        val first = pickHatchling(Dimension.SOUL, emptySet(), creatures)!!
        assertEquals(Dimension.SOUL, first.dimension)
        assertNotEquals(first.id, pickHatchling(Dimension.SOUL, setOf(first.id), creatures)!!.id)

        val allSoul = idsOf(Dimension.SOUL)
        val other = pickHatchling(Dimension.SOUL, allSoul, creatures)!!
        assertNotEquals(Dimension.SOUL, other.dimension)
        assertTrue(other.id !in allSoul)
    }

    @Test
    fun `with every creature owned there is no hatchling, so coins come instead`() {
        assertNull(pickHatchling(Dimension.MIND, creatures.map { it.id }.toSet(), creatures))
    }

    @Test
    fun `the theme has the same number of creatures in each dimension, with unique ids`() {
        val sizes = Dimension.entries.map { idsOf(it).size }.toSet()
        assertEquals(1, sizes.size)
        assertTrue(sizes.single() > 0)
        assertEquals(creatures.size, creatures.map { it.id }.toSet().size)
    }

    @Test
    fun `theme 1 has the developer's 18 creatures, 6 per dimension`() {
        assertEquals(18, creatures.size)
        for (d in Dimension.entries) assertEquals(6, idsOf(d).size)
    }

    @Test
    fun `every first-draft creature id becomes a different creature of the same dimension`() {
        val old = mapOf("lumi" to Dimension.MIND, "pebbit" to Dimension.BODY, "mossle" to Dimension.SOUL)
        for ((from, dim) in old) assertEquals(dim, Themes.CREATURES.creature(Themes.RENAMED_IDS.getValue(from))!!.dimension)
        assertEquals(12, Themes.RENAMED_IDS.size)
        assertEquals(12, Themes.RENAMED_IDS.values.toSet().size)
        assertTrue(Themes.RENAMED_IDS.values.all { Themes.CREATURES.creature(it) != null })
    }

    @Test
    fun `every creature has its own three spar moves`() {
        for (c in creatures) {
            val names = Themes.CREATURES.moveNames(c.id, c.dimension)
            assertEquals(c.id, 3, names.size)
            assertEquals(names, Themes.CREATURES.text.sparMovesByCreature[c.id]) // its own, not the fallback
        }
    }

    @Test
    fun `the three starters are one per dimension, and owned ones are not offered again`() {
        val starters = Themes.CREATURES.starters
        assertEquals(listOf("bulbavulpis", "charignis", "squirtgorgon"), starters)
        assertEquals(Dimension.entries.toSet(), starters.map { Themes.CREATURES.creature(it)!!.dimension }.toSet())
        assertEquals(starters, starterChoices(starters, emptySet()))
        assertEquals(listOf("bulbavulpis", "squirtgorgon"), starterChoices(starters, setOf("charignis", "geodudonis")))
        assertTrue(starterChoices(starters, starters.toSet()).isEmpty())
    }

    @Test
    fun `companion stages start at 30 and 100 bond`() {
        assertEquals(1, companionStage(0))
        assertEquals(1, companionStage(29))
        assertEquals(2, companionStage(30))
        assertEquals(2, companionStage(99))
        assertEquals(3, companionStage(100))
    }

    @Test
    fun `companion is active after a session today, resting after 2 days without one, waiting otherwise`() {
        assertEquals(CompanionState.ACTIVE, companionState(true, today.minusDays(5), today))
        assertEquals(CompanionState.WAITING, companionState(false, null, today))
        assertEquals(CompanionState.WAITING, companionState(false, today.minusDays(1), today))
        assertEquals(CompanionState.RESTING, companionState(false, today.minusDays(2), today))
    }

    @Test
    fun `the companion welcomes the user back after every resting period, and only then`() {
        for (gap in 1L..5L) {
            val restedThisMorning = companionState(false, today.minusDays(gap), today) == CompanionState.RESTING
            assertEquals("gap $gap", restedThisMorning, welcomesBack(true, today.minusDays(gap), today))
        }
        assertFalse(welcomesBack(false, today.minusDays(3), today)) // not back yet
        assertFalse(welcomesBack(true, null, today)) // a first session ever is not a comeback
    }
}
