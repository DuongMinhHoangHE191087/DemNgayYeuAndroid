package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.app.plugin.ads.AdsHelper
import com.app.plugin.consent.ConsentManager
import com.example.BuildConfig
import com.example.monetization.AdPlacementPolicyGate
import com.example.monetization.AdPolicyGate
import com.example.privacy.AppPrivacyCoordinator
import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.SacredZone
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Triển khai chuẩn production của [AdsManager] — Thread-safe, Lifecycle-aware & SacredZone-aware.
 *
 * Kết hợp [AppPrivacyCoordinator] + [AdPolicyGate] (từ `:shared` [AdPlacementPolicy]):
 *  - VIP -> giải phóng cache & chặn vĩnh viễn.
 *  - SacredZone / Celebration day -> không hiển thị bất kỳ định dạng quảng cáo nào.
 *  - Interstitial: warmup 60s, cooldown 120s, tối đa 3 lần/phiên, callback onDismissed luôn fire.
 *  - App Open Ad: tôn trọng suppression flag và [AdPolicyGate].
 */
class AdsManagerImpl(
    private val adPolicyGate: AdPolicyGate = AdPlacementPolicyGate(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AdsManager,
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    // ─── UMP Consent State ───────────────────────────────────────────────────
    override val canRequestAds: StateFlow<Boolean> get() = AppPrivacyCoordinator.canRequestAds

    /** Guards against initializing the SDK (and preloading) twice from the two consent paths. */
    private val sdkInitialized = AtomicBoolean(false)

    init {
        adPolicyGate.updateConsent(AppPrivacyCoordinator.canRequestAds.value)
        // Consent withdrawn (or never granted): drop everything already loaded so nothing is shown.
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            AppPrivacyCoordinator.canRequestAds.collect { allowed ->
                adPolicyGate.updateConsent(allowed)
                if (!allowed) releaseLoadedAds()
            }
        }
    }

    // ─── Trạng thái VIP ──────────────────────────────────────────────────────
    @Volatile private var isVipUser: Boolean = false

    // ─── Interstitial Ad ─────────────────────────────────────────────────────
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var isInterstitialLoading: Boolean = false
    @Volatile private var lastInterstitialShownTime: Long = 0L

    private val minIntervalMs = AdPlacementPolicy.INTERSTITIAL_COOLDOWN_MS

    // ─── App Open Ad (AOA) ───────────────────────────────────────────────────
    @Volatile private var appOpenAd: AppOpenAd? = null
    @Volatile private var isAppOpenLoading: Boolean = false
    @Volatile private var appOpenLoadTime: Long = 0L
    @Volatile private var isAoaSuppressed: Boolean = false

    private val aoaExpirationMs = 4L * 3600L * 1000L

    @Volatile private var currentActivity: Activity? = null

    private val defaultInterstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID
    private val defaultAppOpenAdUnitId = BuildConfig.ADMOB_AOA_ID

    private val testDeviceIds: List<String> = listOf(
        AdRequest.DEVICE_ID_EMULATOR,
    )

    // ─── Sacred Zone & Policy Controls ───────────────────────────────────────

    override fun enterSacredZone(zone: SacredZone) {
        adPolicyGate.enterSacredZone(zone)
    }

    override fun exitSacredZone(zone: SacredZone) {
        adPolicyGate.exitSacredZone(zone)
    }

    override fun setCelebrationDay(isCelebrationDay: Boolean) {
        adPolicyGate.setCelebrationDay(isCelebrationDay)
    }

    override fun isInSacredZone(): Boolean = adPolicyGate.isInSacredZone()

    // ─── Initialize & UMP Consent ─────────────────────────────────────────────

    override fun requestConsentAndInitialize(activity: Activity, onConsentCompleted: (canRequestAds: Boolean) -> Unit) {
        val appContext = activity.applicationContext
        AppPrivacyCoordinator.attach(appContext)
        val initialCanRequest = AppPrivacyCoordinator.canRequestAds.value
        adPolicyGate.updateConsent(initialCanRequest)
        if (initialCanRequest) initialize(appContext)

        ConsentManager.request(activity) { _ ->
            AppPrivacyCoordinator.refresh()
            val canRequest = AppPrivacyCoordinator.canRequestAds.value
            adPolicyGate.updateConsent(canRequest)
            if (canRequest) initialize(appContext)
            onConsentCompleted(canRequest)
        }
    }

    override fun initialize(context: Context) {
        val allowed = AppPrivacyCoordinator.canRequestAds.value
        adPolicyGate.updateConsent(allowed)
        if (!isEligible(isVipUser, allowed)) return
        if (!sdkInitialized.compareAndSet(false, true)) return

        applyTestDeviceIds(testDeviceIds)

        MobileAds.initialize(context) {
            if (isEligible(isVipUser, canRequestAds.value)) {
                preloadInterstitial(context.applicationContext, defaultInterstitialAdUnitId)
                preloadAppOpenAd(context.applicationContext, defaultAppOpenAdUnitId)
            }
        }
    }

    private fun releaseLoadedAds() {
        interstitialAd = null
        appOpenAd = null
        isInterstitialLoading = false
        isAppOpenLoading = false
    }

    // ─── VIP Status ──────────────────────────────────────────────────────────

    override fun setVipStatus(isVip: Boolean) {
        this.isVipUser = isVip
        adPolicyGate.updateVip(isVip)
        runCatching { AdsHelper.instance.setRemoveAds(if (isVip) 1 else 0) }
        if (isVip) releaseLoadedAds()
    }

    // ─── Interstitial Ad ─────────────────────────────────────────────────────

    override fun preloadInterstitial(context: Context, adUnitId: String) {
        if (!INTERSTITIAL_ENABLED || !isEligible(isVipUser, canRequestAds.value, adUnitId) || interstitialAd != null || isInterstitialLoading) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isInterstitialLoading = false
                    interstitialAd = if (isEligible(isVipUser, canRequestAds.value)) ad else null
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            },
        )
    }

    override fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onAdDismissed()
            return
        }

        val currentTime = clock()
        adPolicyGate.updateConsent(canRequestAds.value)
        val isIntervalOk = lastInterstitialShownTime == 0L || (currentTime - lastInterstitialShownTime) >= minIntervalMs
        val policyOk = adPolicyGate.canShowInterstitial(currentTime)
        val currentAd = interstitialAd

        if (!INTERSTITIAL_ENABLED || !isEligible(isVipUser, canRequestAds.value) || !policyOk || currentAd == null || !isIntervalOk) {
            onAdDismissed()
            if (currentAd == null) {
                preloadInterstitial(activity.applicationContext, defaultInterstitialAdUnitId)
            }
            return
        }

        isAoaSuppressed = true

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                val dismissedAt = clock()
                interstitialAd = null
                lastInterstitialShownTime = dismissedAt
                adPolicyGate.recordInterstitialShown(dismissedAt)
                isAoaSuppressed = false
                preloadInterstitial(activity.applicationContext, defaultInterstitialAdUnitId)
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                isAoaSuppressed = false
                preloadInterstitial(activity.applicationContext, defaultInterstitialAdUnitId)
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() = Unit
        }

        currentAd.show(activity)
    }

    // ─── App Open Ad (AOA) ───────────────────────────────────────────────────

    override fun registerAppOpenAdLifecycle(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun preloadAppOpenAd(context: Context, adUnitId: String) {
        if (!APP_OPEN_ENABLED || !isEligible(isVipUser, canRequestAds.value, adUnitId) || isAppOpenAdAvailable() || isAppOpenLoading) return

        isAppOpenLoading = true
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isAppOpenLoading = false
                    appOpenAd = if (isEligible(isVipUser, canRequestAds.value)) ad else null
                    appOpenLoadTime = clock()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isAppOpenLoading = false
                }
            },
        )
    }

    private fun isAppOpenAdAvailable(): Boolean {
        val withinExpiry = (clock() - appOpenLoadTime) < aoaExpirationMs
        return appOpenAd != null && withinExpiry
    }

    override fun onStart(owner: LifecycleOwner) {
        currentActivity?.let { activity ->
            showAppOpenAdIfAvailable(activity)
        }
    }

    private fun showAppOpenAdIfAvailable(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val now = clock()
        adPolicyGate.updateConsent(canRequestAds.value)
        if (
            !APP_OPEN_ENABLED ||
            !isEligible(isVipUser, canRequestAds.value) ||
            isAoaSuppressed ||
            !adPolicyGate.canShowAppOpen(now) ||
            !isAppOpenAdAvailable()
        ) {
            if (appOpenAd == null && !isAppOpenLoading) {
                preloadAppOpenAd(activity.applicationContext, defaultAppOpenAdUnitId)
            }
            return
        }

        val ad = appOpenAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                preloadAppOpenAd(activity.applicationContext, defaultAppOpenAdUnitId)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                preloadAppOpenAd(activity.applicationContext, defaultAppOpenAdUnitId)
            }
        }

        ad.show(activity)
    }

    override fun setAppOpenAdSuppressed(isSuppressed: Boolean) {
        this.isAoaSuppressed = isSuppressed
    }

    // ─── ActivityLifecycleCallbacks ──────────────────────────────────────────

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    companion object {
        const val INTERSTITIAL_ENABLED = false
        const val APP_OPEN_ENABLED = false

        internal fun isEligible(isVip: Boolean, canRequestAds: Boolean, adUnitId: String = "x"): Boolean =
            !isVip && canRequestAds && adUnitId.isNotBlank()

        internal fun applyTestDeviceIds(testDeviceIds: List<String>) {
            val merged = MobileAds.getRequestConfiguration().toBuilder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(merged)
        }
    }
}
