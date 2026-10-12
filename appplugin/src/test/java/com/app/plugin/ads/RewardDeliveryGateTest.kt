package com.app.plugin.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardDeliveryGateTest {
    @Test
    fun `no reward when the user closes the ad before earning it`() {
        val gate = RewardDeliveryGate()

        assertFalse(gate.takeReward())
    }

    @Test
    fun `an earned reward is delivered exactly once`() {
        val gate = RewardDeliveryGate()
        gate.markEarned()

        assertTrue(gate.takeReward())
        assertFalse(gate.takeReward())
    }

    @Test
    fun `a new show starts without the previous show's reward`() {
        val previous = RewardDeliveryGate()
        previous.markEarned()
        previous.takeReward()

        val next = RewardDeliveryGate()

        assertFalse(next.takeReward())
    }
}
