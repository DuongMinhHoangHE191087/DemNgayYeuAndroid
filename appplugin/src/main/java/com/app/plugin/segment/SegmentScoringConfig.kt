package com.app.plugin.segment

/**
 * Scoring + segment-threshold configuration for the scorer. All values are
 * Firebase-Remote-Config overridable via [com.app.plugin.segment.UserSegmentEngine.loadConfig].
 *
 * Defaults are tuned for a freemium consumer app with ~30s avg session.
 * Per-app overrides should land in Remote Config rather than this file so
 * sibling projects depending on adsplugin pick up the new defaults without
 * recompiling.
 */
internal data class SegmentScoringConfig(
    val geoTier1Weight: Int = 40,
    val geoTier2Weight: Int = 20,
    val planTappedWeight: Int = 20,
    val bounceWeight: Int = -10,
    val bounceWindowMillis: Long = 2000L,
    val dismissNoTapWeight: Int = -10,
    val dismissNoTapCap: Int = -30,
    val featureUsedWeight: Int = 5,
    val featureUsedCap: Int = 25,
    val sessions3to6Weight: Int = 10,
    val sessions7PlusWeight: Int = 15,
    val rewardedAdWeight: Int = 5,
    val rewardedAdCap: Int = 15,
    val ecpmHighWeight: Int = 10,
    val ecpmLowWeight: Int = -10,
    val ecpmMinImpressions: Int = 5,
    val hipThreshold: Int = 70,
    val pspThreshold: Int = 40,
    val coldStartWindowMillis: Long = 48L * 60 * 60 * 1000,
    /** Score ≥ this AND segment would already be HIP → promote to POWER_USER.
     *  Default 90 means a near-perfect score plus geo/sessions/featureUse all
     *  firing. Set above [hipThreshold] otherwise the bucket collapses. */
    val powerUserScoreThreshold: Int = 90,
    /** A user with ≥ this many historical sessions in the persisted log who
     *  hasn't opened the app in [churnRiskGapMillis] is classified CHURN_RISK
     *  instead of LIA/PSP/HIP. Floor of 5 prevents brand-new installs flagged
     *  as churn after a single 14-day gap. */
    val churnRiskMinHistoricalSessions: Int = 5,
    /** How long since the most recent session before the user is treated as
     *  at risk of churn. Default 14 days — matches typical app's weekly-
     *  active definition; long enough that one bad week doesn't winback. */
    val churnRiskGapMillis: Long = 14L * 24 * 60 * 60 * 1000,
) {
    companion object {
        val DEFAULT = SegmentScoringConfig()
    }
}
