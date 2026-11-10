package com.ayushpant.habitquest.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure personal-reward rules of step 5c. */
class RewardRulesTest {
    @Test
    fun `a reward costs 10 to 5000 coins`() {
        assertFalse(rewardCostValid(9))
        assertTrue(rewardCostValid(10))
        assertTrue(rewardCostValid(5000))
        assertFalse(rewardCostValid(5001))
        assertFalse(rewardCostValid(0))
    }

    @Test
    fun `coins still needed is the gap, and 0 when the balance is enough`() {
        assertEquals(30, coinsStillNeeded(balance = 120, cost = 150))
        assertEquals(0, coinsStillNeeded(balance = 150, cost = 150))
        assertEquals(0, coinsStillNeeded(balance = 900, cost = 150))
    }

    @Test
    fun `the three examples are the ones from the plan, and all have a valid cost`() {
        assertEquals(
            listOf("One episode of a series" to 100, "A coffee outside" to 150, "A free evening" to 500),
            REWARD_IDEAS.map { it.name to it.cost },
        )
        assertTrue(REWARD_IDEAS.all { rewardCostValid(it.cost) })
    }
}
