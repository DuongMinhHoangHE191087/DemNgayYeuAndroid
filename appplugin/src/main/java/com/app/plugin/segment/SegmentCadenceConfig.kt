package com.app.plugin.segment

/**
 * Per-segment ad-cadence thresholds. Higher number = gentler cadence (more
 * tab switches required before an interstitial is allowed). All values are
 * Firebase-Remote-Config overridable.
 *
 * Recommended ordering: POWER_USER ≥ HIP > PSP > LIA. POWER_USER threshold
 * is the gentlest because the user's LTV is already proven — annoying them
 * with ads is pure downside. CHURN_RISK is treated like LIA — they were
 * about to bounce anyway, an interstitial does less damage there than to a
 * power user who might churn from frustration.
 *
 * [globalGuardrailMillis]: minimum gap between two full-screen ads (interstitial / app open). **0 = off** in the SDK
 * (2.2.0, D10-220; the forks shipped IM 180 s / SC 60 s); RC `seg_cadence_guardrail_seconds` turns it on per app.
 */
public data class SegmentCadenceConfig(
    val hipTabSwitchThreshold:        Int  = 10,
    val pspTabSwitchThreshold:        Int  = 6,
    val liaTabSwitchThreshold:        Int  = 4,
    val powerUserTabSwitchThreshold:  Int  = 14,
    val churnRiskTabSwitchThreshold:  Int  = 4,
    val globalGuardrailMillis:        Long = 0L,
) {
    public companion object {
        public val DEFAULT: SegmentCadenceConfig = SegmentCadenceConfig()
    }
}
