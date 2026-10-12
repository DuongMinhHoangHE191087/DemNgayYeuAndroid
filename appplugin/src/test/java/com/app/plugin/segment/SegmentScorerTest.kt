package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentScorerTest {

    private val config = SegmentScoringConfig.DEFAULT

    @Test
    fun freshScorerWithNoSignalsScoresZero() {
        val scorer = SegmentScorer(config)
        assertEquals(0, scorer.currentScore(nowMillis = 0L))
    }

    @Test
    fun geoTier1AddsFortyAndTier2AddsTwenty() {
        val tier1 = SegmentScorer(config)
        tier1.setGeoTier(1)
        assertEquals(40, tier1.currentScore(nowMillis = 0L))

        val tier2 = SegmentScorer(config)
        tier2.setGeoTier(2)
        assertEquals(20, tier2.currentScore(nowMillis = 0L))

        val tier4 = SegmentScorer(config)
        tier4.setGeoTier(4)
        assertEquals(0, tier4.currentScore(nowMillis = 0L))
    }

    @Test
    fun planTappedAddsTwentyOnce() {
        val scorer = SegmentScorer(config)
        scorer.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 1000L)
        scorer.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 2000L)

        assertEquals(20, scorer.currentScore(nowMillis = 3000L))
    }

    @Test
    fun fastDismissWithNoTapAppliesBounceAndDismissPenaltiesTogether() {
        val scorer = SegmentScorer(config)
        scorer.recordEvent(SegmentEvent.PAYWALL_SHOWN, nowMillis = 1000L)
        scorer.recordEvent(SegmentEvent.PAYWALL_DISMISSED, nowMillis = 1500L) // 500ms later, < 2000ms bounce window

        // -10 (dismiss without tap) + -10 (bounce) = -20, clamped to 0
        assertEquals(0, scorer.currentScore(nowMillis = 2000L))
    }

    @Test
    fun slowDismissWithNoTapAppliesOnlyDismissPenaltyNotBounce() {
        val scorer = SegmentScorer(config)
        scorer.recordEvent(SegmentEvent.PAYWALL_SHOWN, nowMillis = 1000L)
        scorer.recordEvent(SegmentEvent.PAYWALL_DISMISSED, nowMillis = 5000L) // 4000ms later, >= 2000ms bounce window
        scorer.setGeoTier(1) // add +40 so the -10 dismiss penalty is visible above zero-clamp

        assertEquals(30, scorer.currentScore(nowMillis = 6000L))
    }

    @Test
    fun dismissWithoutTapCountIsCappedAtNegativeThirty() {
        val scorer = SegmentScorer(config)
        scorer.setGeoTier(1) // +40, so the cap is visible above zero-clamp
        for (i in 0 until 5) {
            scorer.recordEvent(SegmentEvent.PAYWALL_SHOWN, nowMillis = 10_000L * i)
            scorer.recordEvent(SegmentEvent.PAYWALL_DISMISSED, nowMillis = 10_000L * i + 5000L) // well outside bounce window
        }

        // 40 + max(5 * -10, -30) = 40 - 30 = 10
        assertEquals(10, scorer.currentScore(nowMillis = 100_000L))
    }

    @Test
    fun dismissingAfterTappingAPlanInTheSameCycleAppliesNoPenalty() {
        val scorer = SegmentScorer(config)
        scorer.setGeoTier(1)
        scorer.recordEvent(SegmentEvent.PAYWALL_SHOWN, nowMillis = 1000L)
        scorer.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 1100L)
        scorer.recordEvent(SegmentEvent.PAYWALL_DISMISSED, nowMillis = 1200L)

        // +40 (tier1) + 20 (plan tapped) + 0 (no dismiss/bounce penalty, plan was tapped this cycle)
        assertEquals(60, scorer.currentScore(nowMillis = 2000L))
    }

    @Test
    fun featureUsedAddsFiveEachCappedAtTwentyFive() {
        val scorer = SegmentScorer(config)
        for (i in 1..7) {
            scorer.recordEvent(
                SegmentEvent.FEATURE_USED,
                nowMillis = 1000L,
                payload = mapOf("featureId" to "feature_$i")
            )
        }

        assertEquals(25, scorer.currentScore(nowMillis = 2000L))
    }

    @Test
    fun sessionsInTrailingSevenDaysAddTenForThreeToSixAndFifteenForSevenPlus() {
        val dayMillis = 24L * 60 * 60 * 1000

        val fourSessions = SegmentScorer(config)
        for (i in 0 until 4) {
            fourSessions.recordEvent(SegmentEvent.SESSION_START, nowMillis = i * dayMillis)
        }
        assertEquals(10, fourSessions.currentScore(nowMillis = 4 * dayMillis))

        val sevenSessions = SegmentScorer(config)
        for (i in 0 until 7) {
            sevenSessions.recordEvent(SegmentEvent.SESSION_START, nowMillis = i * dayMillis)
        }
        assertEquals(15, sevenSessions.currentScore(nowMillis = 7 * dayMillis))
    }

    @Test
    fun sessionsOlderThanSevenDaysDoNotCount() {
        val dayMillis = 24L * 60 * 60 * 1000
        val scorer = SegmentScorer(config)
        scorer.recordEvent(SegmentEvent.SESSION_START, nowMillis = 0L)
        scorer.recordEvent(SegmentEvent.SESSION_START, nowMillis = 1 * dayMillis)
        scorer.recordEvent(SegmentEvent.SESSION_START, nowMillis = 2 * dayMillis)

        // By day 10, all three sessions are older than the trailing 7-day window
        assertEquals(0, scorer.currentScore(nowMillis = 10 * dayMillis))
    }

    @Test
    fun rewardedAdCompletedAddsFiveEachCappedAtFifteen() {
        val scorer = SegmentScorer(config)
        for (i in 0 until 5) {
            scorer.recordEvent(SegmentEvent.REWARDED_AD_COMPLETED, nowMillis = 1000L)
        }

        assertEquals(15, scorer.currentScore(nowMillis = 2000L))
    }

    @Test
    fun ecpmBucketOnlyAppliesOnceFiveImpressionsRecorded() {
        val scorer = SegmentScorer(config)
        for (i in 0 until 4) {
            scorer.recordEvent(
                SegmentEvent.AD_IMPRESSION,
                nowMillis = 1000L,
                payload = mapOf("ecpmBucket" to EcpmBucket.HIGH)
            )
        }
        assertEquals(0, scorer.currentScore(nowMillis = 2000L))

        scorer.recordEvent(
            SegmentEvent.AD_IMPRESSION,
            nowMillis = 1000L,
            payload = mapOf("ecpmBucket" to EcpmBucket.HIGH)
        )
        assertEquals(10, scorer.currentScore(nowMillis = 2000L))
    }

    @Test
    fun lowEcpmBucketSubtractsTen() {
        val scorer = SegmentScorer(config)
        scorer.setGeoTier(1)
        for (i in 0 until 5) {
            scorer.recordEvent(
                SegmentEvent.AD_IMPRESSION,
                nowMillis = 1000L,
                payload = mapOf("ecpmBucket" to EcpmBucket.LOW)
            )
        }

        assertEquals(30, scorer.currentScore(nowMillis = 2000L))
    }

    @Test
    fun purchaseCompletedForcesScoreToOneHundred() {
        val scorer = SegmentScorer(config)
        scorer.recordEvent(SegmentEvent.PURCHASE_COMPLETED, nowMillis = 1000L)

        assertEquals(100, scorer.currentScore(nowMillis = 2000L))
        assertEquals(UserSegment.HIP, scorer.currentSegment(nowMillis = 2000L, firstSeenMillis = 0L))
    }

    @Test
    fun scoreNeverGoesBelowZeroOrAboveOneHundred() {
        val low = SegmentScorer(config)
        low.recordEvent(SegmentEvent.PAYWALL_SHOWN, nowMillis = 0L)
        low.recordEvent(SegmentEvent.PAYWALL_DISMISSED, nowMillis = 100L)
        assertEquals(0, low.currentScore(nowMillis = 1000L))

        val high = SegmentScorer(config)
        high.setGeoTier(1)
        high.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 0L)
        for (i in 1..10) {
            high.recordEvent(
                SegmentEvent.FEATURE_USED,
                nowMillis = 0L,
                payload = mapOf("featureId" to "feature_$i")
            )
        }
        for (i in 0 until 10) {
            high.recordEvent(SegmentEvent.REWARDED_AD_COMPLETED, nowMillis = 0L)
        }
        for (i in 0 until 5) {
            high.recordEvent(
                SegmentEvent.AD_IMPRESSION,
                nowMillis = 0L,
                payload = mapOf("ecpmBucket" to EcpmBucket.HIGH)
            )
        }
        assertEquals(100, high.currentScore(nowMillis = 1000L))
    }

    @Test
    fun segmentIsColdStartWithinTheColdStartWindowRegardlessOfScore() {
        val scorer = SegmentScorer(config)
        scorer.setGeoTier(1) // would otherwise be HIP

        assertEquals(
            UserSegment.COLD_START,
            scorer.currentSegment(nowMillis = 1000L, firstSeenMillis = 0L)
        )
    }

    @Test
    fun segmentFollowsScoreThresholdsAfterColdStartWindowElapses() {
        val afterWindow = config.coldStartWindowMillis + 1

        val hip = SegmentScorer(config)
        hip.setGeoTier(1)
        hip.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 0L)
        for (i in 1..3) {
            hip.recordEvent(SegmentEvent.FEATURE_USED, nowMillis = 0L, payload = mapOf("featureId" to "feature_$i"))
        }
        // score = geoTier1(40) + planTapped(20) + 3 features(15) = 75 >= hipThreshold(70)
        assertEquals(UserSegment.HIP, hip.currentSegment(nowMillis = afterWindow, firstSeenMillis = 0L))

        val psp = SegmentScorer(config)
        psp.setGeoTier(2)
        psp.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 0L)
        psp.recordEvent(SegmentEvent.FEATURE_USED, nowMillis = 0L, payload = mapOf("featureId" to "feature_1"))
        // score = geoTier2(20) + planTapped(20) + 1 feature(5) = 45, within [pspThreshold(40), hipThreshold(70))
        assertEquals(UserSegment.PSP, psp.currentSegment(nowMillis = afterWindow, firstSeenMillis = 0L))

        val lia = SegmentScorer(config)
        assertEquals(UserSegment.LIA, lia.currentSegment(nowMillis = afterWindow, firstSeenMillis = 0L))
    }

    @Test
    fun snapshotRoundTripsThroughANewScorerWithTheSameScore() {
        val original = SegmentScorer(config)
        original.setGeoTier(1)
        original.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 0L)
        original.recordEvent(SegmentEvent.REWARDED_AD_COMPLETED, nowMillis = 0L)

        val restored = SegmentScorer(config, original.snapshot())

        assertEquals(original.currentScore(nowMillis = 1000L), restored.currentScore(nowMillis = 1000L))
    }

    // --------------------------------------------------------------------
    // CHURN_RISK + POWER_USER segment tests
    // --------------------------------------------------------------------

    @Test
    fun churnRiskFiresWhenUserHasManySessionsAndLongGap() {
        // 6 historical sessions then a 15-day gap (default churn = 14d).
        val scorer = SegmentScorer(config)
        scorer.setGeoTier(1)
        val day = 24L * 60 * 60 * 1000
        repeat(6) { i -> scorer.recordEvent(SegmentEvent.SESSION_START, nowMillis = i * day) }
        val now = 5 * day + 15 * day  // last session at day 5, now at day 20 → 15d gap

        assertEquals(
            UserSegment.CHURN_RISK,
            scorer.currentSegment(nowMillis = now, firstSeenMillis = 0L),
        )
    }

    @Test
    fun churnRiskRequiresMinimumHistoricalSessions() {
        // Only 3 sessions, then 30-day gap → too thin a history to call churn.
        // Falls back to score-based bucketing (no signals → LIA).
        val scorer = SegmentScorer(config)
        val day = 24L * 60 * 60 * 1000
        repeat(3) { i -> scorer.recordEvent(SegmentEvent.SESSION_START, nowMillis = i * day) }
        val now = 2 * day + 30 * day  // 30d gap from last session

        assertEquals(
            UserSegment.LIA,
            scorer.currentSegment(nowMillis = now, firstSeenMillis = 0L),
        )
    }

    @Test
    fun shortGapKeepsNormalSegment() {
        // 6 historical sessions, recent activity (3d gap) → not churn.
        val scorer = SegmentScorer(config)
        scorer.setGeoTier(1)
        scorer.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 0L)
        repeat(6) { i ->
            // Pack sessions into the last week so they survive the 7-day window
            // and the user still scores in HIP range.
            scorer.recordEvent(SegmentEvent.SESSION_START, nowMillis = i * 24L * 60 * 60 * 1000)
        }
        val now = 5 * 24L * 60 * 60 * 1000 + 3 * 24L * 60 * 60 * 1000  // 3d gap

        val segment = scorer.currentSegment(nowMillis = now, firstSeenMillis = 0L)
        // Not CHURN_RISK; the exact bucket depends on score but must not be churn.
        org.junit.Assert.assertNotEquals(UserSegment.CHURN_RISK, segment)
    }

    @Test
    fun coldStartTakesPrecedenceOverChurnRisk() {
        // First-day user CANNOT be classified CHURN_RISK even if the gap math
        // looks like one (e.g. via clock skew).
        val scorer = SegmentScorer(config)
        // No sessions recorded, firstSeen=0, now within cold-start window.
        assertEquals(
            UserSegment.COLD_START,
            scorer.currentSegment(nowMillis = 1000L, firstSeenMillis = 0L),
        )
    }

    @Test
    fun powerUserFiresWhenScoreReachesPowerUserThreshold() {
        // Max out every positive signal → score 100 → POWER_USER (which sits
        // above hipThreshold default 70 and powerUserThreshold default 90).
        val scorer = SegmentScorer(config)
        scorer.setGeoTier(1)
        scorer.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 0L)
        for (i in 1..10) {
            scorer.recordEvent(
                SegmentEvent.FEATURE_USED,
                nowMillis = 0L,
                payload = mapOf("featureId" to "f_$i"),
            )
        }
        for (i in 1..10) {
            scorer.recordEvent(SegmentEvent.REWARDED_AD_COMPLETED, nowMillis = 0L)
        }
        for (i in 1..5) {
            scorer.recordEvent(
                SegmentEvent.AD_IMPRESSION,
                nowMillis = 0L,
                payload = mapOf("ecpmBucket" to EcpmBucket.HIGH),
            )
        }

        // Past cold-start window so the lifecycle gate doesn't short-circuit.
        val afterWindow = config.coldStartWindowMillis + 1
        assertEquals(
            UserSegment.POWER_USER,
            scorer.currentSegment(nowMillis = afterWindow, firstSeenMillis = 0L),
        )
    }

    @Test
    fun powerUserPreemptsHipAtScoreCeiling() {
        // Custom config: powerUser threshold lower than the user's score so we
        // unambiguously land in POWER_USER (instead of HIP).
        val highCutoff = config.copy(
            powerUserScoreThreshold = 60,
            hipThreshold = 50,
        )
        val scorer = SegmentScorer(highCutoff)
        scorer.setGeoTier(1)
        scorer.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED, nowMillis = 0L)
        // Score = 40 (geoTier1) + 20 (planTapped) = 60 → POWER_USER ≥ 60.
        val afterWindow = highCutoff.coldStartWindowMillis + 1
        assertEquals(
            UserSegment.POWER_USER,
            scorer.currentSegment(nowMillis = afterWindow, firstSeenMillis = 0L),
        )
    }

    @Test
    fun purchasedUserStaysHipNotPowerUser() {
        // Paid users hit the explicit isPro short-circuit → HIP, never
        // POWER_USER (we don't want to soft-bias paid users by accident).
        val scorer = SegmentScorer(config)
        scorer.recordEvent(SegmentEvent.PURCHASE_COMPLETED, nowMillis = 0L)
        val afterWindow = config.coldStartWindowMillis + 1
        assertEquals(
            UserSegment.HIP,
            scorer.currentSegment(nowMillis = afterWindow, firstSeenMillis = 0L),
        )
    }
}
