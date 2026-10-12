package com.app.plugin.segment

import android.content.Context
import com.app.plugin.until.PreUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Segment người dùng (2.2.0 SEG, port từ fork `:adsplugin` của Invoice Maker — tập lớn hơn của IM ∪ SC; FQN giữ nguyên
 * nên app bỏ fork mà không đổi import). SDK tự gọi [init] trong `AppPluginManager.initPlugin` và tự ghi
 * `SESSION_START`, `REWARDED_AD_COMPLETED`, `AD_IMPRESSION` qua `SegmentHooks`; app chỉ ghi event của mình (paywall,
 * tính năng, mua). RC đọc qua `SegmentHooks` (khoá `seg_*` y hệt fork), trên luồng nền; user property `seg_*` chỉ rời
 * máy khi tầng consent không phải DENIED/UNKNOWN (R-P1).
 */
public class UserSegmentEngine private constructor() {

    public companion object {
        public val instance: UserSegmentEngine by lazy { UserSegmentEngine() }

        private const val KEY_GEO_TIER = "seg_geo_tier"
        private const val KEY_FIRST_SEEN = "seg_first_seen_millis"
        private const val KEY_EVER_TAPPED_PLAN = "seg_ever_tapped_plan"
        private const val KEY_DISMISS_NO_TAP_COUNT = "seg_dismiss_no_tap_count"
        private const val KEY_BOUNCE_COUNT = "seg_bounce_count"
        private const val KEY_FEATURES_USED = "seg_features_used"
        private const val KEY_SESSION_TIMESTAMPS = "seg_session_timestamps"
        private const val KEY_REWARDED_AD_COMPLETIONS = "seg_rewarded_ad_completions"
        private const val KEY_AD_IMPRESSION_COUNT = "seg_ad_impression_count"
        private const val KEY_ECPM_BUCKET = "seg_ecpm_bucket"
        private const val KEY_PURCHASE_COMPLETED = "seg_purchase_completed"
        private const val KEY_PENDING_PAYWALL_SHOWN_AT = "seg_pending_paywall_shown_at"
        private const val KEY_PENDING_PAYWALL_PLAN_TAPPED = "seg_pending_paywall_plan_tapped"
        private const val KEY_LAST_NON_SESSION_EVENT_MILLIS = "seg_last_non_session_event_millis"
    }

    private var context: Context? = null
    private var config: SegmentScoringConfig = SegmentScoringConfig.DEFAULT
    private var cadenceConfigValue: SegmentCadenceConfig = SegmentCadenceConfig.DEFAULT
    private var revenueTierConfigValue: RevenueTierConfig = RevenueTierConfig.DEFAULT
    private var scorer: SegmentScorer = SegmentScorer(config)
    private var firstSeenMillis: Long = 0L
    private var forcedSegment: UserSegment? = null
    private var geoTierValue: Int = 4
    @Volatile private var lastRevenueTier: String? = null

    // Synchronizes recordEvent so SESSION_START / PURCHASE / REWARDED / AD_IMPRESSION
    // arriving from different threads (Play Billing, ad SDK callbacks, lifecycle observer)
    // can't interleave the read/write to lastNonSessionEventMillis or the
    // scorer.recordEvent + saveState pair.
    internal val recordLock = Any()   // internal: `LocalDataReset` nạp lại dưới khoá này (2.4.2 CLEAR I3)

    // Timestamp of the most recent revenue-signal recordEvent (anything that suggests the
    // user might convert). RevenueTierEvaluator uses this to classify PRODUCTIVE vs DORMANT
    // inside the rolling productive window. SESSION_START is intentionally excluded - mere
    // opens don't indicate monetary intent.
    private var lastNonSessionEventMillis: Long = 0L

    private val _segment = MutableStateFlow(UserSegment.COLD_START)
    public val segment: StateFlow<UserSegment> = _segment

    private val _score = MutableStateFlow(0)
    public val score: StateFlow<Int> = _score

    public val geoTier: Int
        get() = geoTierValue

    public val dismissWithoutTapCount: Int
        get() = scorer.snapshot().dismissWithoutTapCount

    /**
     * Snapshot of the underlying scorer state. Exposed for RevenueTierEvaluator, which needs
     * the raw signal counters (rewardedAdCompletions, ecpmBucket, purchaseCompleted) the
     * public StateFlows don't surface.
     */
    public fun scorerSnapshot(): ScorerState = scorer.snapshot()

    /**
     * Wall-clock millis of the most recent revenue-signal event (plan tap, rewarded ad,
     * high-eCPM impression, purchase). 0 when none have fired yet. Used by
     * RevenueTierEvaluator to classify PRODUCTIVE vs DORMANT within the rolling window.
     */
    public val lastSegmentEventMillis: Long
        get() = lastNonSessionEventMillis

    public fun cadenceConfig(): SegmentCadenceConfig = cadenceConfigValue

    public fun revenueTierConfig(): RevenueTierConfig = revenueTierConfigValue

    public fun init(context: Context) {
        config = loadConfig()
        cadenceConfigValue = loadCadenceConfig()
        revenueTierConfigValue = loadRevenueTierConfig()

        firstSeenMillis = PreUtil.getLong(context, KEY_FIRST_SEEN, -1L)
        if (firstSeenMillis <= 0L) {
            firstSeenMillis = System.currentTimeMillis()
            PreUtil.setLong(context, KEY_FIRST_SEEN, firstSeenMillis)
        }

        // Restore the most-recent revenue-signal timestamp so RevenueTierEvaluator's
        // PRODUCTIVE/DORMANT classification survives process death. Without this, a user
        // who tapped a plan yesterday and cold-started today would read 0L and fall out
        // of the productive window immediately, defeating the trailing-window logic and
        // potentially demoting them to STARVED (re-snackbarring a user we shouldn't).
        // Defaults to 0L for upgraders - matches today's first-install behavior.
        lastNonSessionEventMillis = PreUtil.getLong(context, KEY_LAST_NON_SESSION_EVENT_MILLIS, 0L)

        scorer = SegmentScorer(config, loadState(context))

        val tier = GeoTierResolver.resolveTier(
            Locale.getDefault().country,
            tier1CountriesFromConfig(),
            tier2CountriesFromConfig()
        )
        scorer.setGeoTier(tier)
        geoTierValue = tier
        PreUtil.setInt(context, KEY_GEO_TIER, tier)

        recomputeAndEmit()
        // Last: until config + persisted state are loaded, recordEventNow is a no-op (never saves defaults over history).
        this.context = context
    }

    /**
     * Queued while the SDK loads the engine (after `initPlugin`, typically tens of ms), with the call time kept; effects
     * (StateFlow, snapshot, prefs) are visible after the load. Dropped before `initPlugin`.
     */
    public fun recordEvent(event: SegmentEvent, payload: Map<String, Any>? = null) {
        val now = System.currentTimeMillis()
        if (SegmentStartup.queueIfStarting { recordEventNow(event, payload, now) }) return
        recordEventNow(event, payload, now)
    }

    /** Ghi ngay với [now] = lúc gọi (hook của SDK, lượt phát lại); trước khi `init` xong thì bỏ, không lưu mặc định đè lịch sử. */
    internal fun recordEventNow(event: SegmentEvent, payload: Map<String, Any>?, now: Long) {
        val ctx = context ?: return
        synchronized(recordLock) {
            // Stamp the revenue-signal timestamp BEFORE the scorer (RevenueTierEvaluator sees it on the same event).
            // SESSION_START is excluded (opens are not monetary intent); AD_IMPRESSION only when the payload tags it HIGH.
            val isRevenueSignal = when (event) {
                SegmentEvent.PAYWALL_PLAN_TAPPED,
                SegmentEvent.REWARDED_AD_COMPLETED,
                SegmentEvent.PURCHASE_COMPLETED -> true
                SegmentEvent.AD_IMPRESSION -> (payload?.get("ecpmBucket") as? EcpmBucket) == EcpmBucket.HIGH
                else -> false
            }
            if (isRevenueSignal) {
                lastNonSessionEventMillis = now
                // Persist immediately: a death before the next scorer save must not lose the freshest revenue signal.
                PreUtil.setLong(ctx, KEY_LAST_NON_SESSION_EVENT_MILLIS, lastNonSessionEventMillis)
            }
            scorer.recordEvent(event, now, payload)
            saveState(ctx, scorer.snapshot())
            recomputeAndEmit()
        }
    }

    public fun forceSegment(segment: UserSegment?) {
        forcedSegment = segment
        recomputeAndEmit()
    }

    /**
     * Mirrors the latest revenue-tier classification to the Firebase `seg_revenue_tier` user property. The engine can't
     * compute it (no `lifetimeSessionCount`, an `:app` concern), so `:app` evaluates the tier and pushes the name here.
     * No-op before init. Consent-gated (`SegmentHooks.setUserProperty`: nothing leaves when DENIED/UNKNOWN; re-sent once
     * on GRANTED); failures are swallowed there.
     */
    public fun setRevenueTierUserProperty(tierName: String) {
        lastRevenueTier = tierName
        if (SegmentStartup.queueIfStarting { sendRevenueTier(tierName) }) return // gửi sau khi nạp, cùng hàng FIFO
        sendRevenueTier(tierName)
    }

    private fun sendRevenueTier(tierName: String) {
        if (context != null) SegmentHooks.setUserProperty("seg_revenue_tier", tierName)
    }

    /** Consent vừa thành GRANTED: gửi lại một lần segment, điểm và tầng doanh thu cuối (bị giữ lúc UNKNOWN). */
    internal fun republishUserProperties() {
        if (context == null) return
        SegmentHooks.setUserProperty("seg_segment", _segment.value.name)
        SegmentHooks.setUserProperty("seg_score", _score.value.toString())
        lastRevenueTier?.let { SegmentHooks.setUserProperty("seg_revenue_tier", it) }
    }

    private fun recomputeAndEmit() {
        val now = System.currentTimeMillis()
        val computedScore = scorer.currentScore(now)
        val computedSegment = forcedSegment ?: scorer.currentSegment(now, firstSeenMillis)

        val scoreChanged = computedScore != _score.value
        val segmentChanged = computedSegment != _segment.value
        _score.value = computedScore
        _segment.value = computedSegment

        if (scoreChanged || segmentChanged) {
            // Consent-gated, best-effort (SegmentHooks); never lets a logging failure break scoring.
            SegmentHooks.setUserProperty("seg_segment", computedSegment.name)
            SegmentHooks.setUserProperty("seg_score", computedScore.toString())
        }
    }

    private fun loadConfig(): SegmentScoringConfig {
        val defaults = SegmentScoringConfig.DEFAULT
        return defaults.copy(
            geoTier1Weight = overrideInt("seg_weight_geo_tier1", defaults.geoTier1Weight),
            geoTier2Weight = overrideInt("seg_weight_geo_tier2", defaults.geoTier2Weight),
            planTappedWeight = overrideInt("seg_weight_plan_tapped", defaults.planTappedWeight),
            bounceWeight = overrideInt("seg_weight_bounce", defaults.bounceWeight),
            bounceWindowMillis = SegmentHooks.rcLong("seg_bounce_window_millis") ?: defaults.bounceWindowMillis,
            dismissNoTapWeight = overrideInt("seg_weight_dismiss_no_tap", defaults.dismissNoTapWeight),
            dismissNoTapCap = overrideInt("seg_weight_dismiss_no_tap_cap", defaults.dismissNoTapCap),
            featureUsedWeight = overrideInt("seg_weight_feature_used", defaults.featureUsedWeight),
            featureUsedCap = overrideInt("seg_weight_feature_used_cap", defaults.featureUsedCap),
            sessions3to6Weight = overrideInt("seg_weight_sessions_3to6", defaults.sessions3to6Weight),
            sessions7PlusWeight = overrideInt("seg_weight_sessions_7plus", defaults.sessions7PlusWeight),
            rewardedAdWeight = overrideInt("seg_weight_rewarded_ad", defaults.rewardedAdWeight),
            rewardedAdCap = overrideInt("seg_weight_rewarded_ad_cap", defaults.rewardedAdCap),
            ecpmHighWeight = overrideInt("seg_weight_ecpm_high", defaults.ecpmHighWeight),
            ecpmLowWeight = overrideInt("seg_weight_ecpm_low", defaults.ecpmLowWeight),
            ecpmMinImpressions = overrideInt("seg_ecpm_min_impressions", defaults.ecpmMinImpressions),
            hipThreshold = overrideInt("seg_threshold_hip", defaults.hipThreshold),
            pspThreshold = overrideInt("seg_threshold_psp", defaults.pspThreshold),
            coldStartWindowMillis = SegmentHooks.rcLong("seg_cold_start_window_hours")?.let { it * 60 * 60 * 1000 }
                ?: defaults.coldStartWindowMillis,
            powerUserScoreThreshold = overrideInt(
                "seg_threshold_power_user", defaults.powerUserScoreThreshold,
            ),
            churnRiskMinHistoricalSessions = overrideInt(
                "seg_churn_min_historical_sessions", defaults.churnRiskMinHistoricalSessions,
            ),
            churnRiskGapMillis = SegmentHooks.rcLong("seg_churn_gap_days")?.let { it * 24 * 60 * 60 * 1000 }
                ?: defaults.churnRiskGapMillis,
        )
    }

    private fun overrideInt(key: String, default: Int): Int = SegmentHooks.rcLong(key)?.toInt() ?: default

    private fun loadCadenceConfig(): SegmentCadenceConfig {
        val defaults = SegmentCadenceConfig.DEFAULT
        return defaults.copy(
            hipTabSwitchThreshold = overrideInt("seg_cadence_hip_tab_switch", defaults.hipTabSwitchThreshold),
            pspTabSwitchThreshold = overrideInt("seg_cadence_psp_tab_switch", defaults.pspTabSwitchThreshold),
            liaTabSwitchThreshold = overrideInt("seg_cadence_lia_tab_switch", defaults.liaTabSwitchThreshold),
            powerUserTabSwitchThreshold = overrideInt(
                "seg_cadence_power_user_tab_switch", defaults.powerUserTabSwitchThreshold,
            ),
            churnRiskTabSwitchThreshold = overrideInt(
                "seg_cadence_churn_risk_tab_switch", defaults.churnRiskTabSwitchThreshold,
            ),
            // 2.2.0 (D10-220): default 0 = guardrail off; RC key, else the app's integer resource (2.4.2), turns it on (IM 180, SC 60 seconds).
            globalGuardrailMillis = (SegmentHooks.rcLong("seg_cadence_guardrail_seconds") ?: SegmentHooks.localGuardrailSeconds())?.let { it * 1000 }
                ?: defaults.globalGuardrailMillis
        )
    }

    private fun loadRevenueTierConfig(): RevenueTierConfig {
        val defaults = RevenueTierConfig.DEFAULT
        return defaults.copy(
            starvedMinSessions = overrideInt("seg_revenue_starved_min_sessions", defaults.starvedMinSessions),
            productiveWindowMillis = SegmentHooks.rcLong("seg_revenue_productive_window_hours")?.let { it * 60 * 60 * 1000 }
                ?: defaults.productiveWindowMillis,
            starvedDismissKillSwitch = overrideInt("seg_revenue_starved_dismiss_kill_switch", defaults.starvedDismissKillSwitch),
            killSwitchCooldownDays = SegmentHooks.rcLong("seg_revenue_starved_kill_switch_cooldown_days")
                ?: defaults.killSwitchCooldownDays
        )
    }

    private fun tier1CountriesFromConfig(): Set<String> = countries("seg_geo_tier1_countries", GeoTierResolver.DEFAULT_TIER1)

    private fun tier2CountriesFromConfig(): Set<String> = countries("seg_geo_tier2_countries", GeoTierResolver.DEFAULT_TIER2)

    private fun countries(key: String, defaults: Set<String>): Set<String> {
        val str = SegmentHooks.rcString(key) ?: defaults.joinToString(",")
        return str.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun loadState(context: Context): ScorerState {
        val featuresStr = PreUtil.getString(context, KEY_FEATURES_USED, "")
        val features = if (featuresStr.isEmpty()) emptySet() else featuresStr.split(",").toSet()

        val timestampsStr = PreUtil.getString(context, KEY_SESSION_TIMESTAMPS, "")
        val timestamps = if (timestampsStr.isEmpty()) {
            emptyList()
        } else {
            timestampsStr.split(",").mapNotNull { it.toLongOrNull() }
        }

        val bucketStr = PreUtil.getString(context, KEY_ECPM_BUCKET, "")
        val bucket = EcpmBucket.values().firstOrNull { it.name == bucketStr }

        val pendingShownAt = PreUtil.getLong(context, KEY_PENDING_PAYWALL_SHOWN_AT, -1L)
            .let { if (it <= 0L) null else it }

        return ScorerState(
            geoTier = PreUtil.getInt(context, KEY_GEO_TIER, 4),
            everTappedPlan = PreUtil.getInt(context, KEY_EVER_TAPPED_PLAN, 0) == 1,
            dismissWithoutTapCount = PreUtil.getInt(context, KEY_DISMISS_NO_TAP_COUNT, 0),
            bounceCount = PreUtil.getInt(context, KEY_BOUNCE_COUNT, 0),
            featuresUsed = features,
            sessionTimestamps = timestamps,
            rewardedAdCompletions = PreUtil.getInt(context, KEY_REWARDED_AD_COMPLETIONS, 0),
            adImpressionCount = PreUtil.getInt(context, KEY_AD_IMPRESSION_COUNT, 0),
            ecpmBucket = bucket,
            purchaseCompleted = PreUtil.getInt(context, KEY_PURCHASE_COMPLETED, 0) == 1,
            pendingPaywallShownAt = pendingShownAt,
            pendingPaywallPlanTapped = PreUtil.getInt(context, KEY_PENDING_PAYWALL_PLAN_TAPPED, 0) == 1
        )
    }

    private fun saveState(context: Context, state: ScorerState) {
        PreUtil.setInt(context, KEY_GEO_TIER, state.geoTier)
        PreUtil.setInt(context, KEY_EVER_TAPPED_PLAN, if (state.everTappedPlan) 1 else 0)
        PreUtil.setInt(context, KEY_DISMISS_NO_TAP_COUNT, state.dismissWithoutTapCount)
        PreUtil.setInt(context, KEY_BOUNCE_COUNT, state.bounceCount)
        PreUtil.setString(context, KEY_FEATURES_USED, state.featuresUsed.joinToString(","))
        PreUtil.setString(context, KEY_SESSION_TIMESTAMPS, state.sessionTimestamps.joinToString(","))
        PreUtil.setInt(context, KEY_REWARDED_AD_COMPLETIONS, state.rewardedAdCompletions)
        PreUtil.setInt(context, KEY_AD_IMPRESSION_COUNT, state.adImpressionCount)
        PreUtil.setString(context, KEY_ECPM_BUCKET, state.ecpmBucket?.name ?: "")
        PreUtil.setInt(context, KEY_PURCHASE_COMPLETED, if (state.purchaseCompleted) 1 else 0)
        PreUtil.setLong(context, KEY_PENDING_PAYWALL_SHOWN_AT, state.pendingPaywallShownAt ?: -1L)
        PreUtil.setInt(context, KEY_PENDING_PAYWALL_PLAN_TAPPED, if (state.pendingPaywallPlanTapped) 1 else 0)
    }
}
