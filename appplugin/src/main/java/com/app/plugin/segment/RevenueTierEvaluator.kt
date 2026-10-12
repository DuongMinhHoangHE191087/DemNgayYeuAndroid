package com.app.plugin.segment

public enum class RevenueTier {
    PRODUCTIVE,
    DORMANT,
    STARVED,
    STARVED_QUIESCED
}

public object RevenueTierEvaluator {

    public fun evaluate(
        snapshot: ScorerState,
        lifetimeSessionCount: Int,
        lastSegmentEventMillis: Long,
        nonPayerQuiesceUntilMillis: Long,
        now: Long,
        config: RevenueTierConfig
    ): RevenueTier {
        // Purchase is sticky forever; checked first so a purchased user is never quiesced.
        if (snapshot.purchaseCompleted) return RevenueTier.PRODUCTIVE

        if (now < nonPayerQuiesceUntilMillis) return RevenueTier.STARVED_QUIESCED

        val hasRevenueSignalEver = snapshot.pendingPaywallPlanTapped ||
            snapshot.rewardedAdCompletions > 0 ||
            snapshot.ecpmBucket == EcpmBucket.HIGH

        if (hasRevenueSignalEver) {
            return if (now - lastSegmentEventMillis < config.productiveWindowMillis) {
                RevenueTier.PRODUCTIVE
            } else {
                RevenueTier.DORMANT
            }
        }

        return if (lifetimeSessionCount >= config.starvedMinSessions) {
            RevenueTier.STARVED
        } else {
            RevenueTier.DORMANT
        }
    }
}
