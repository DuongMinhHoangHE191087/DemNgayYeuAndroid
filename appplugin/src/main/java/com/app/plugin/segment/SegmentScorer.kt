package com.app.plugin.segment

public data class ScorerState(
    val geoTier: Int = 4,
    val everTappedPlan: Boolean = false,
    val dismissWithoutTapCount: Int = 0,
    val bounceCount: Int = 0,
    val featuresUsed: Set<String> = emptySet(),
    val sessionTimestamps: List<Long> = emptyList(),
    val rewardedAdCompletions: Int = 0,
    val adImpressionCount: Int = 0,
    val ecpmBucket: EcpmBucket? = null,
    val purchaseCompleted: Boolean = false,
    val pendingPaywallShownAt: Long? = null,
    val pendingPaywallPlanTapped: Boolean = false
)

internal class SegmentScorer(
    private val config: SegmentScoringConfig = SegmentScoringConfig.DEFAULT,
    initialState: ScorerState = ScorerState()
) {
    private val lock = Any()
    private var state = initialState

    fun setGeoTier(tier: Int) {
        synchronized(lock) {
            state = state.copy(geoTier = tier)
        }
    }

    fun recordEvent(event: SegmentEvent, nowMillis: Long, payload: Map<String, Any>? = null) {
        synchronized(lock) {
            state = when (event) {
                SegmentEvent.SESSION_START -> {
                    val pruned = (state.sessionTimestamps + nowMillis)
                        .filter { nowMillis - it <= SEVEN_DAYS_MILLIS }
                    state.copy(sessionTimestamps = pruned)
                }

                SegmentEvent.FEATURE_USED -> {
                    val featureId = payload?.get("featureId") as? String
                    if (featureId != null) {
                        state.copy(featuresUsed = state.featuresUsed + featureId)
                    } else {
                        state
                    }
                }

                SegmentEvent.PAYWALL_SHOWN ->
                    state.copy(pendingPaywallShownAt = nowMillis, pendingPaywallPlanTapped = false)

                SegmentEvent.PAYWALL_PLAN_TAPPED ->
                    state.copy(everTappedPlan = true, pendingPaywallPlanTapped = true)

                SegmentEvent.PAYWALL_DISMISSED -> {
                    var next = state
                    if (!state.pendingPaywallPlanTapped) {
                        next = next.copy(dismissWithoutTapCount = next.dismissWithoutTapCount + 1)
                        val shownAt = state.pendingPaywallShownAt
                        if (shownAt != null && nowMillis - shownAt < config.bounceWindowMillis) {
                            next = next.copy(bounceCount = next.bounceCount + 1)
                        }
                    }
                    next.copy(pendingPaywallShownAt = null, pendingPaywallPlanTapped = false)
                }

                SegmentEvent.REWARDED_AD_COMPLETED ->
                    state.copy(rewardedAdCompletions = state.rewardedAdCompletions + 1)

                SegmentEvent.AD_IMPRESSION -> {
                    val bucket = payload?.get("ecpmBucket") as? EcpmBucket
                    val newCount = state.adImpressionCount + 1
                    val newBucket = if (newCount >= config.ecpmMinImpressions && bucket != null) {
                        bucket
                    } else {
                        state.ecpmBucket
                    }
                    state.copy(adImpressionCount = newCount, ecpmBucket = newBucket)
                }

                SegmentEvent.PURCHASE_COMPLETED -> state.copy(purchaseCompleted = true)

                SegmentEvent.NON_PAYER_DISMISS -> state  // payload-only event, no scorer state mutation
            }
        }
    }

    fun currentScore(nowMillis: Long): Int {
        val snapshot = synchronized(lock) { state }
        if (snapshot.purchaseCompleted) return 100

        var score = 0
        score += when (snapshot.geoTier) {
            1 -> config.geoTier1Weight
            2 -> config.geoTier2Weight
            else -> 0
        }
        if (snapshot.everTappedPlan) score += config.planTappedWeight
        score += snapshot.bounceCount * config.bounceWeight
        score += maxOf(snapshot.dismissWithoutTapCount * config.dismissNoTapWeight, config.dismissNoTapCap)
        score += minOf(snapshot.featuresUsed.size * config.featureUsedWeight, config.featureUsedCap)

        val recentSessions = snapshot.sessionTimestamps.count { nowMillis - it <= SEVEN_DAYS_MILLIS }
        score += when {
            recentSessions >= 7 -> config.sessions7PlusWeight
            recentSessions >= 3 -> config.sessions3to6Weight
            else -> 0
        }

        score += minOf(snapshot.rewardedAdCompletions * config.rewardedAdWeight, config.rewardedAdCap)
        score += when (snapshot.ecpmBucket) {
            EcpmBucket.HIGH -> config.ecpmHighWeight
            EcpmBucket.LOW -> config.ecpmLowWeight
            null -> 0
        }

        return score.coerceIn(0, 100)
    }

    fun currentSegment(nowMillis: Long, firstSeenMillis: Long): UserSegment {
        val snapshot = synchronized(lock) { state }
        // Paid users always classify as HIP — they're done converting and the
        // scorer's HIP threshold should already accept them via the
        // purchaseCompleted=>100 path, but be explicit so future re-orderings
        // of this method don't accidentally demote a paid user.
        if (snapshot.purchaseCompleted) return UserSegment.HIP

        // Cold-start window protects new installs from paywalls. Lifecycle
        // gating runs BEFORE churn so a brand-new user can't ever be tagged
        // CHURN_RISK on day one.
        if (nowMillis - firstSeenMillis < config.coldStartWindowMillis) {
            return UserSegment.COLD_START
        }

        // CHURN_RISK is a lifecycle signal — past engagement followed by a
        // long gap. Detected before scoring so a returning user lands on the
        // winback variant even if their score is still high from prior usage.
        val mostRecentSession = snapshot.sessionTimestamps.maxOrNull() ?: 0L
        val gapMillis = nowMillis - mostRecentSession
        val isChurnCandidate = snapshot.sessionTimestamps.size >= config.churnRiskMinHistoricalSessions &&
            mostRecentSession > 0L &&
            gapMillis >= config.churnRiskGapMillis
        if (isChurnCandidate) return UserSegment.CHURN_RISK

        val score = currentScore(nowMillis)
        return when {
            score >= config.powerUserScoreThreshold && score >= config.hipThreshold -> UserSegment.POWER_USER
            score >= config.hipThreshold -> UserSegment.HIP
            score >= config.pspThreshold -> UserSegment.PSP
            else -> UserSegment.LIA
        }
    }

    fun snapshot(): ScorerState = synchronized(lock) { state }

    companion object {
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
