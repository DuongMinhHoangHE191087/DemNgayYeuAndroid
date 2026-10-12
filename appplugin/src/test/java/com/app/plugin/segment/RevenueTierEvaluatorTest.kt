package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Test

class RevenueTierEvaluatorTest {

    private val productiveWindow = 14L * 24 * 60 * 60 * 1000
    private val now = 1_000_000_000_000L

    private fun stateNoRevenue() = ScorerState()

    private fun stateWithRewardedAd() = ScorerState(rewardedAdCompletions = 1)

    private fun stateWithPlanTap() = ScorerState(pendingPaywallPlanTapped = true)

    private fun stateWithHighEcpm() = ScorerState(ecpmBucket = EcpmBucket.HIGH)

    private fun stateWithLowEcpm() = ScorerState(ecpmBucket = EcpmBucket.LOW)

    private fun statePurchased() = ScorerState(purchaseCompleted = true)

    @Test
    fun `purchaseCompleted is sticky with 1000 sessions ago`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = statePurchased(),
            lifetimeSessionCount = 1000,
            lastSegmentEventMillis = now - 365L * 24 * 60 * 60 * 1000,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.PRODUCTIVE, tier)
    }

    @Test
    fun `purchaseCompleted is sticky for brand new user`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = statePurchased(),
            lifetimeSessionCount = 1,
            lastSegmentEventMillis = now,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.PRODUCTIVE, tier)
    }

    @Test
    fun `quiesce window returns STARVED_QUIESCED`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateNoRevenue(),
            lifetimeSessionCount = 50,
            lastSegmentEventMillis = now - productiveWindow - 1,
            nonPayerQuiesceUntilMillis = now + 1_000_000L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.STARVED_QUIESCED, tier)
    }

    @Test
    fun `quiesce window expired falls through to other rules`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateNoRevenue(),
            lifetimeSessionCount = 50,
            lastSegmentEventMillis = now - productiveWindow - 1,
            nonPayerQuiesceUntilMillis = now - 1L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.STARVED, tier)
    }

    @Test
    fun `recent rewarded ad keeps user PRODUCTIVE`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithRewardedAd(),
            lifetimeSessionCount = 20,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.PRODUCTIVE, tier)
    }

    @Test
    fun `recent plan tap keeps user PRODUCTIVE`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithPlanTap(),
            lifetimeSessionCount = 20,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.PRODUCTIVE, tier)
    }

    @Test
    fun `HIGH ecpmBucket keeps user PRODUCTIVE within window`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithHighEcpm(),
            lifetimeSessionCount = 20,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.PRODUCTIVE, tier)
    }

    @Test
    fun `LOW ecpmBucket does NOT make user PRODUCTIVE`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithLowEcpm(),
            lifetimeSessionCount = 20,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.STARVED, tier)
    }

    @Test
    fun `historical revenue with expired window returns DORMANT`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithRewardedAd(),
            lifetimeSessionCount = 50,
            lastSegmentEventMillis = now - productiveWindow - 1,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.DORMANT, tier)
    }

    @Test
    fun `no revenue ever with less than starvedMinSessions returns DORMANT`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateNoRevenue(),
            lifetimeSessionCount = 9,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.DORMANT, tier)
    }

    @Test
    fun `no revenue ever with exactly starvedMinSessions returns STARVED`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateNoRevenue(),
            lifetimeSessionCount = 10,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.STARVED, tier)
    }

    @Test
    fun `no revenue ever with more than starvedMinSessions returns STARVED`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateNoRevenue(),
            lifetimeSessionCount = 100,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.STARVED, tier)
    }

    @Test
    fun `productive window boundary exactly equals window returns DORMANT`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithRewardedAd(),
            lifetimeSessionCount = 50,
            lastSegmentEventMillis = now - productiveWindow,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.DORMANT, tier)
    }

    @Test
    fun `productive window boundary one ms less than window returns PRODUCTIVE`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithRewardedAd(),
            lifetimeSessionCount = 50,
            lastSegmentEventMillis = now - productiveWindow + 1,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.PRODUCTIVE, tier)
    }

    @Test
    fun `config override starvedMinSessions of 5 makes session 5 STARVED`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateNoRevenue(),
            lifetimeSessionCount = 5,
            lastSegmentEventMillis = now - 1000L,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT.copy(starvedMinSessions = 5)
        )
        assertEquals(RevenueTier.STARVED, tier)
    }

    @Test
    fun `config override productiveWindowMillis of 1 day makes 25-hour-old rewarded ad DORMANT`() {
        val oneDay = 24L * 60 * 60 * 1000
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = stateWithRewardedAd(),
            lifetimeSessionCount = 50,
            lastSegmentEventMillis = now - 25L * 60 * 60 * 1000,
            nonPayerQuiesceUntilMillis = 0L,
            now = now,
            config = RevenueTierConfig.DEFAULT.copy(productiveWindowMillis = oneDay)
        )
        assertEquals(RevenueTier.DORMANT, tier)
    }

    @Test
    fun `purchaseCompleted overrides quiesce window`() {
        val tier = RevenueTierEvaluator.evaluate(
            snapshot = statePurchased(),
            lifetimeSessionCount = 50,
            lastSegmentEventMillis = now - productiveWindow - 1,
            nonPayerQuiesceUntilMillis = now + 1_000_000L,
            now = now,
            config = RevenueTierConfig.DEFAULT
        )
        assertEquals(RevenueTier.PRODUCTIVE, tier)
    }
}
