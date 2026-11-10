package com.ayushpant.habitquest.game

import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.rewards.CreatureDef
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * Pure rules for the sparring game (step 5h). A friendly, turn-based match between one of the
 * user's creatures and a creature the app controls. Creatures have ENERGY, not health: at 0 a
 * creature takes a breather and bows. Nobody is hurt, sad or defeated (R5). No Android code, so
 * every rule is unit tested; randomness comes in as a parameter.
 */

enum class MoveKind { PUSH, BIG, REST }

/**
 * One of the three move slots every creature has (the theme names them per dimension).
 * PUSH and BIG lower the other creature's energy by about [power]; REST gives back [power] energy.
 */
data class Move(val kind: MoveKind, val power: Int, val accuracy: Int)

/** Numbers of the game, in one place. */
data class SparConfig(
    /** Slot 0 push, slot 1 big (can miss), slot 2 rest. */
    val moves: List<Move> = listOf(Move(MoveKind.PUSH, 15, 100), Move(MoveKind.BIG, 24, 75), Move(MoveKind.REST, 18, 100)),
    val maxEnergy: Int = 100,
    /** Rests per creature per match, so a match always ends. */
    val maxRests: Int = 2,
    /** After this many turns the match is a draw. */
    val maxTurns: Int = 30,
    val advantage: Double = 1.5,
    val disadvantage: Double = 0.67,
    /** A move's effect = power × creature power ÷ this. */
    val powerScale: Double = 12.0,
    /** Creature power = base + per stage above 1 + 1 per [bondPerPower] bond (up to [bondCap]). */
    val basePower: Int = 10,
    val powerPerStage: Int = 3,
    val bondPerPower: Int = 15,
    val bondCap: Int = 150,
    /** The app's creature rests when its energy is at or below this share (percent). */
    val opponentRestBelow: Int = 30,
    /** Percent of turns the app's creature tries its big move. */
    val opponentBigChance: Int = 30,
) {
    companion object {
        val DEFAULT = SparConfig()
    }
}

/** Body > Mind > Soul > Body. */
fun beats(attacker: Dimension, defender: Dimension): Boolean =
    (attacker == Dimension.BODY && defender == Dimension.MIND) ||
        (attacker == Dimension.MIND && defender == Dimension.SOUL) ||
        (attacker == Dimension.SOUL && defender == Dimension.BODY)

/** 1 = strong against, -1 = weak against, 0 = even. */
fun matchup(attacker: Dimension, defender: Dimension): Int = when {
    beats(attacker, defender) -> 1
    beats(defender, attacker) -> -1
    else -> 0
}

/** Strength comes from the creature's stage and bond only (no separate training). */
fun sparPower(stage: Int, bond: Int, cfg: SparConfig = SparConfig.DEFAULT): Int =
    cfg.basePower + (stage.coerceAtLeast(1) - 1) * cfg.powerPerStage + bond.coerceIn(0, cfg.bondCap) / cfg.bondPerPower

/** How much energy a push or big move takes away. [variance] is 0.9–1.1. Never below 1. */
fun moveAmount(move: Move, attackerPower: Int, matchup: Int, variance: Double, cfg: SparConfig = SparConfig.DEFAULT): Int {
    val factor = when (matchup) { 1 -> cfg.advantage; -1 -> cfg.disadvantage; else -> 1.0 }
    return max(1, (move.power * attackerPower / cfg.powerScale * factor * variance).roundToInt())
}

/** [id] is the creature id of the theme, for its move names and picture. */
data class Fighter(val name: String, val dimension: Dimension, val power: Int, val energy: Int, val restsLeft: Int, val id: String = "")

fun fighter(name: String, dimension: Dimension, power: Int, cfg: SparConfig = SparConfig.DEFAULT, id: String = "") =
    Fighter(name, dimension, power, cfg.maxEnergy, cfg.maxRests, id)

enum class SparOutcome { PLAYING, WON, LOST, DRAW }

data class SparState(val me: Fighter, val them: Fighter, val turn: Int = 0, val outcome: SparOutcome = SparOutcome.PLAYING)

/**
 * What one move did, for the message box (the screen turns it into the theme's words).
 * [hit] false = a big move that went wide, or a rest with none left. [amount] = energy taken or given back.
 */
data class SparEvent(val byMe: Boolean, val slot: Int, val kind: MoveKind, val hit: Boolean, val amount: Int, val matchup: Int)

data class TurnResult(val state: SparState, val events: List<SparEvent>)

/** The app's creature: rests when tired (while it has rests), else mostly pushes, sometimes a big move. */
fun opponentMove(state: SparState, rng: Random, cfg: SparConfig = SparConfig.DEFAULT): Int {
    val t = state.them
    if (t.restsLeft > 0 && t.energy * 100 <= cfg.maxEnergy * cfg.opponentRestBelow) return 2
    return if (rng.nextInt(100) < cfg.opponentBigChance) 1 else 0
}

/**
 * One turn. The stronger creature moves first (the user's on a tie); the other then moves if it
 * still has energy. The match ends when one creature reaches 0 energy, or in a draw after
 * [SparConfig.maxTurns] turns.
 */
fun playTurn(state: SparState, mySlot: Int, rng: Random, cfg: SparConfig = SparConfig.DEFAULT): TurnResult {
    require(state.outcome == SparOutcome.PLAYING) { "The match is over" }
    val theirSlot = opponentMove(state, rng, cfg)
    var me = state.me
    var them = state.them
    val events = mutableListOf<SparEvent>()

    fun act(byMe: Boolean, slot: Int) {
        val attacker = if (byMe) me else them
        val defender = if (byMe) them else me
        val move = cfg.moves[slot]
        if (move.kind == MoveKind.REST) {
            if (attacker.restsLeft <= 0) {
                events += SparEvent(byMe, slot, MoveKind.REST, hit = false, amount = 0, matchup = 0)
                return
            }
            val gain = min(move.power, cfg.maxEnergy - attacker.energy)
            val rested = attacker.copy(energy = attacker.energy + gain, restsLeft = attacker.restsLeft - 1)
            if (byMe) me = rested else them = rested
            events += SparEvent(byMe, slot, MoveKind.REST, hit = true, amount = gain, matchup = 0)
            return
        }
        if (rng.nextInt(100) >= move.accuracy) {
            events += SparEvent(byMe, slot, move.kind, hit = false, amount = 0, matchup = 0)
            return
        }
        val m = matchup(attacker.dimension, defender.dimension)
        val amount = moveAmount(move, attacker.power, m, 0.9 + rng.nextDouble() * 0.2, cfg)
        val tired = defender.copy(energy = max(0, defender.energy - amount))
        if (byMe) them = tired else me = tired
        events += SparEvent(byMe, slot, move.kind, hit = true, amount = amount, matchup = m)
    }

    val meFirst = me.power >= them.power
    act(meFirst, if (meFirst) mySlot else theirSlot)
    if (me.energy > 0 && them.energy > 0) act(!meFirst, if (meFirst) theirSlot else mySlot)

    val turn = state.turn + 1
    val outcome = when {
        them.energy <= 0 -> SparOutcome.WON
        me.energy <= 0 -> SparOutcome.LOST
        turn >= cfg.maxTurns -> SparOutcome.DRAW
        else -> SparOutcome.PLAYING
    }
    return TurnResult(SparState(me, them, turn, outcome), events)
}

/**
 * An opponent for a spar: any creature of the theme, about as strong as the user's creature
 * (from 2 weaker to 1 stronger). [boost] makes it stronger, e.g. for the hatch battle later.
 */
fun pickOpponent(creatures: List<CreatureDef>, myPower: Int, rng: Random, boost: Int = 0, cfg: SparConfig = SparConfig.DEFAULT): Pair<CreatureDef, Int> {
    val def = creatures[rng.nextInt(creatures.size)]
    val power = (myPower + rng.nextInt(-2, 2) + boost).coerceAtLeast(cfg.basePower - 2)
    return def to power
}
