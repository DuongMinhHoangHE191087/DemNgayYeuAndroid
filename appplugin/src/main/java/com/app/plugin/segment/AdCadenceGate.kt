package com.app.plugin.segment

/**
 * Decides ad cadence based on the user's [UserSegment]. Cold-start users get
 * a per-geo bootstrap (Tier 1 → PSP cadence, others → LIA cadence) since
 * COLD_START itself yields no interstitials.
 */
public object AdCadenceGate {

    public fun effectiveCadenceSegment(segment: UserSegment, geoTier: Int): UserSegment {
        return if (segment == UserSegment.COLD_START) {
            if (geoTier == 1) UserSegment.PSP else UserSegment.LIA
        } else {
            segment
        }
    }

    public fun shouldShowInterstitial(
        segment: UserSegment,
        geoTier: Int,
        tabSwitchCount: Int,
        config: SegmentCadenceConfig = SegmentCadenceConfig.DEFAULT
    ): Boolean {
        return when (effectiveCadenceSegment(segment, geoTier)) {
            UserSegment.POWER_USER -> tabSwitchCount >= config.powerUserTabSwitchThreshold
            UserSegment.HIP        -> tabSwitchCount >= config.hipTabSwitchThreshold
            UserSegment.PSP        -> tabSwitchCount >= config.pspTabSwitchThreshold
            UserSegment.LIA        -> tabSwitchCount >= config.liaTabSwitchThreshold
            UserSegment.CHURN_RISK -> tabSwitchCount >= config.churnRiskTabSwitchThreshold
            UserSegment.COLD_START -> false
        }
    }
}
