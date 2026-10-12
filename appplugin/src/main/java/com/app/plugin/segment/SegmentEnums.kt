package com.app.plugin.segment

public enum class SegmentEvent {
    SESSION_START,
    FEATURE_USED,
    PAYWALL_SHOWN,
    PAYWALL_DISMISSED,
    PAYWALL_PLAN_TAPPED,
    REWARDED_AD_COMPLETED,
    AD_IMPRESSION,
    PURCHASE_COMPLETED,
    NON_PAYER_DISMISS  // payload: {"rung": Int}
}

/**
 * Behavioural segments. Scorer-driven (HIP / PSP / LIA / POWER_USER) or
 * lifecycle-driven (COLD_START / CHURN_RISK):
 *
 *   - COLD_START   — first 48h after install, paywall suppressed
 *   - HIP          — high-intent paying segment, score ≥ hipThreshold
 *   - POWER_USER   — sustained super-engaged user (score ≥ powerUserThreshold,
 *                    above hipThreshold) — paywall + ad cadence treats them
 *                    even gentler than HIP because the LTV is already there
 *   - PSP          — price-sensitive paying segment, score ≥ pspThreshold
 *   - LIA          — low-intent ad-watcher segment, score below pspThreshold
 *   - CHURN_RISK   — user had ≥ N historical sessions but hasn't returned in
 *                    M days; surfaces winback paywall variant
 *
 * Adding a value here is a public API change for every host app — sync
 * `AdCadenceGate.shouldShowInterstitial` exhaustive `when {}` and any
 * host-app bridge (e.g. SegmentBridgeImpl mapping to PaywallSegment).
 */
public enum class UserSegment {
    HIP,
    PSP,
    LIA,
    COLD_START,
    POWER_USER,
    CHURN_RISK,
}

public enum class EcpmBucket {
    HIGH,
    LOW
}
