package com.ayushpant.habitquest.game

import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.rewards.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The pure sparring rules of step 5h. */
class SparRulesTest {
    private val cfg = SparConfig.DEFAULT
    private val push = cfg.moves[0]

    @Test
    fun `the advantage triangle - Body beats Mind, Mind beats Soul, Soul beats Body`() {
        assertTrue(beats(Dimension.BODY, Dimension.MIND))
        assertTrue(beats(Dimension.MIND, Dimension.SOUL))
        assertTrue(beats(Dimension.SOUL, Dimension.BODY))
        for (d in Dimension.entries) assertFalse(beats(d, d))
        assertFalse(beats(Dimension.MIND, Dimension.BODY))
        assertEquals(1, matchup(Dimension.BODY, Dimension.MIND))
        assertEquals(-1, matchup(Dimension.MIND, Dimension.BODY))
        assertEquals(0, matchup(Dimension.SOUL, Dimension.SOUL))
    }

    @Test
    fun `a move is stronger with the advantage and weaker against it`() {
        val even = moveAmount(push, 12, matchup = 0, variance = 1.0)
        assertEquals(15, even) // 15 × 12 ÷ 12
        assertTrue(moveAmount(push, 12, matchup = 1, variance = 1.0) > even)
        assertTrue(moveAmount(push, 12, matchup = -1, variance = 1.0) < even)
        assertEquals(1, moveAmount(push, 0, matchup = -1, variance = 0.9)) // never below 1
    }

    @Test
    fun `strength comes from stage and bond only, with a cap`() {
        assertEquals(10, sparPower(stage = 1, bond = 0))
        assertEquals(13, sparPower(stage = 2, bond = 0))
        assertEquals(12, sparPower(stage = 1, bond = 30))
        assertEquals(sparPower(3, 150), sparPower(3, 900)) // bond counts up to the cap
        assertTrue(sparPower(3, 150) > sparPower(2, 30))
    }

    @Test
    fun `a rest gives energy back up to the maximum, and only twice a match`() {
        val tired = SparState(fighter("Me", Dimension.MIND, 10).copy(energy = 50), fighter("Them", Dimension.MIND, 1).copy(energy = 100))
        var s = playTurn(tired, 2, Random(1)).state
        assertEquals(1, s.me.restsLeft)
        assertTrue(s.me.energy <= 50 + cfg.moves[2].power) // rested, maybe pushed after
        s = playTurn(s, 2, Random(2)).state
        assertEquals(0, s.me.restsLeft)
        val third = playTurn(s, 2, Random(3))
        assertFalse(third.events.first { it.byMe }.hit) // no rests left: nothing happens
        val full = SparState(fighter("Me", Dimension.MIND, 10), fighter("Them", Dimension.MIND, 1))
        assertEquals(0, playTurn(full, 2, Random(4)).events.first { it.byMe }.amount) // already full
    }

    @Test
    fun `a big move can miss, and a miss changes no energy`() {
        val neverHits = cfg.copy(moves = listOf(push, cfg.moves[1].copy(accuracy = 0), cfg.moves[2]))
        val start = SparState(fighter("Me", Dimension.BODY, 20, neverHits), fighter("Them", Dimension.MIND, 1, neverHits))
        val turn = playTurn(start, 1, Random(5), neverHits)
        val mine = turn.events.first { it.byMe }
        assertFalse(mine.hit)
        assertEquals(cfg.maxEnergy, turn.state.them.energy)
    }

    @Test
    fun `every match ends - one creature at 0, or a draw at the turn limit`() {
        for (seed in 1..200) {
            val rng = Random(seed)
            var s = SparState(fighter("Me", Dimension.entries[seed % 3], 8 + seed % 10), fighter("Them", Dimension.entries[(seed / 3) % 3], 8 + (seed / 7) % 10))
            while (s.outcome == SparOutcome.PLAYING) s = playTurn(s, rng.nextInt(3), rng).state
            when (s.outcome) {
                SparOutcome.WON -> assertEquals(0, s.them.energy)
                SparOutcome.LOST -> assertEquals(0, s.me.energy)
                else -> assertEquals(cfg.maxTurns, s.turn)
            }
            assertTrue(s.turn <= cfg.maxTurns)
        }
    }

    @Test
    fun `a stronger creature with the advantage nearly always wins`() {
        val wins = (1..100).count { seed ->
            val rng = Random(seed)
            var s = SparState(fighter("Me", Dimension.BODY, 16), fighter("Them", Dimension.MIND, 10))
            while (s.outcome == SparOutcome.PLAYING) s = playTurn(s, 0, rng).state
            s.outcome == SparOutcome.WON
        }
        assertTrue("won $wins of 100", wins >= 95)
    }

    @Test
    fun `the app's creature rests when tired and it still can`() {
        val tiredThem = SparState(fighter("Me", Dimension.MIND, 10), fighter("Them", Dimension.MIND, 10).copy(energy = 20))
        assertEquals(2, opponentMove(tiredThem, Random(1)))
        val noRests = tiredThem.copy(them = tiredThem.them.copy(restsLeft = 0))
        assertTrue(opponentMove(noRests, Random(1)) != 2)
    }

    @Test
    fun `an opponent is about as strong as yours, and a boost makes it stronger`() {
        val creatures = Themes.CREATURES.creatures
        repeat(100) { seed ->
            val (def, power) = pickOpponent(creatures, 14, Random(seed))
            assertTrue(def in creatures)
            assertTrue(power in 12..15)
            val (_, boosted) = pickOpponent(creatures, 14, Random(seed), boost = 3)
            assertEquals(power + 3, boosted)
        }
    }
}
