package com.ayushpant.habitquest.rewards

import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.dimensionsOf
import com.ayushpant.habitquest.scoring.ScoringConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Pure rules for the egg, the hatch, and the companion (step 5b). No Android code, no theme:
 * the creature list comes in as a parameter, so a theme can never change a rule.
 */

/** How much growth the egg with this (0-based) [index] needs, and how much it starts with. */
fun eggTarget(index: Int, cfg: ScoringConfig = ScoringConfig.DEFAULT): Pair<Int, Int> =
    if (index == 0) cfg.eggFirstGrowth to cfg.eggFirstStart else cfg.eggLaterGrowth to cfg.eggLaterStart

/**
 * Shares [amount] growth between the dimensions in [mask], equally. A source with no dimension
 * (the day bonus) feeds all three. The sum is always [amount]; a remainder goes to the first
 * dimensions in the order Mind, Body, Soul.
 */
fun splitGrowth(amount: Int, mask: Int): Map<Dimension, Int> {
    val dims = dimensionsOf(mask).ifEmpty { Dimension.entries.toSet() }.sortedBy { it.ordinal }
    val base = amount / dims.size
    val rest = amount % dims.size
    return dims.mapIndexed { i, d -> d to base + if (i < rest) 1 else 0 }.toMap()
}

/**
 * The dimension of a hatching egg: the one that gave the most growth. If two are equal, the one
 * with fewer creatures owned; still equal: Mind, then Body, then Soul.
 */
fun hatchDimension(growth: Map<Dimension, Int>, ownedCount: Map<Dimension, Int>): Dimension =
    Dimension.entries.sortedWith(
        compareByDescending<Dimension> { growth[it] ?: 0 }.thenBy { ownedCount[it] ?: 0 }.thenBy { it.ordinal }
    ).first()

/**
 * Which creature hatches: one the user does not have in [dimension]; if all of that dimension
 * are owned, one from the next dimension; null when all creatures are owned (then coins instead).
 */
fun pickHatchling(dimension: Dimension, ownedIds: Set<String>, creatures: List<CreatureDef>): CreatureDef? {
    val order = Dimension.entries.let { all -> all.drop(dimension.ordinal) + all.take(dimension.ordinal) }
    for (d in order) {
        creatures.firstOrNull { it.dimension == d && it.id !in ownedIds }?.let { return it }
    }
    return null
}

/** The companion's stage from its bond: 1, 2 (at [ScoringConfig.companionStage2Bond]) or 3. */
fun companionStage(bond: Int, cfg: ScoringConfig = ScoringConfig.DEFAULT): Int = when {
    bond >= cfg.companionStage3Bond -> 3
    bond >= cfg.companionStage2Bond -> 2
    else -> 1
}

/**
 * ACTIVE after a confirmed session today; RESTING when the last confirmed session was
 * [ScoringConfig.companionRestingDays] or more days ago; WAITING otherwise (also with no session yet).
 */
fun companionState(doneToday: Boolean, lastDoneBefore: LocalDate?, today: LocalDate, cfg: ScoringConfig = ScoringConfig.DEFAULT): CompanionState = when {
    doneToday -> CompanionState.ACTIVE
    lastDoneBefore != null && ChronoUnit.DAYS.between(lastDoneBefore, today) >= cfg.companionRestingDays -> CompanionState.RESTING
    else -> CompanionState.WAITING
}

/**
 * True when today's first session ends a resting period: the companion welcomes the user back.
 * Same gap as RESTING in [companionState], so a companion that rested this morning always welcomes.
 */
fun welcomesBack(doneToday: Boolean, lastDoneBefore: LocalDate?, today: LocalDate, cfg: ScoringConfig = ScoringConfig.DEFAULT): Boolean =
    doneToday && lastDoneBefore != null && ChronoUnit.DAYS.between(lastDoneBefore, today) >= cfg.companionRestingDays
