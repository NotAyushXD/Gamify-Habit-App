package com.ayushpant.habitquest.rewards

import com.ayushpant.habitquest.scoring.ScoringConfig

/*
 * Pure rules for personal rewards (step 5c): real-life treats the user defines and buys with coins.
 * No Android code, unit tested.
 */

/** An example shown while the list is empty. Tapping it fills in a new reward. */
data class RewardIdea(val name: String, val cost: Int)

/** The three examples from the build prompt (5c). */
val REWARD_IDEAS = listOf(
    RewardIdea("One episode of a series", 100),
    RewardIdea("A coffee outside", 150),
    RewardIdea("A free evening", 500),
)

/** Longest reward name kept; longer names are cut. */
const val REWARD_NAME_MAX = 60

fun rewardCostValid(cost: Int, cfg: ScoringConfig = ScoringConfig.DEFAULT): Boolean = cost in cfg.rewardMinCost..cfg.rewardMaxCost

/** Coins still needed before [cost] can be redeemed with [balance]; 0 when it can be redeemed now. */
fun coinsStillNeeded(balance: Int, cost: Int): Int = (cost - balance).coerceAtLeast(0)
