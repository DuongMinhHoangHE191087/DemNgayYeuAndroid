package com.app.plugin.ads

/** Guardrails for automatic fullscreen ads and the remotely configured Full2 chain. */
internal class FullScreenAdPolicy(
    clickReturnCooldownMillis: Long = DEFAULT_CLICK_RETURN_COOLDOWN_MILLIS,
) {
    /**
     * Cooldown sau click, đặt lại tại chỗ bởi `AdsHelper.applyPolicy` (`post_click_cooldown_seconds`). Không dựng
     * instance mới: làm vậy mất [isHostActivityResumed] tới lần resume kế. Áp cho lần click sau, không kéo dài
     * hay rút ngắn cửa sổ đang chạy.
     */
    @Volatile
    internal var clickReturnCooldownMillis: Long = clickReturnCooldownMillis.coerceAtLeast(0L)
        set(value) {
            field = value.coerceAtLeast(0L)
        }

    @Volatile
    private var suppressAutomaticFullscreenUntilMillis: Long = -1L
    @Volatile
    private var isHostActivityResumed: Boolean = false

    fun recordAdClick(nowMillis: Long = System.currentTimeMillis()) {
        val candidate = nowMillis + clickReturnCooldownMillis
        if (candidate > suppressAutomaticFullscreenUntilMillis) {
            suppressAutomaticFullscreenUntilMillis = candidate
        }
    }

    fun shouldSuppressAutomaticFullscreen(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis < suppressAutomaticFullscreenUntilMillis

    fun onHostActivityResumed() {
        isHostActivityResumed = true
    }

    fun onHostActivityPaused() {
        isHostActivityResumed = false
    }

    fun shouldDeferFull2UntilHostResumed(): Boolean = !isHostActivityResumed

    fun isHostReadyForFullscreen(): Boolean = isHostActivityResumed

    fun shouldChainFull2AfterInterstitial(
        full2FlagShow: Int,
        originPlacement: String?,
    ): Boolean = shouldChainFull2AfterInterstitial(full2FlagShow, originPlacement, fixedMoment = false)

    /**
     * 2.4.2 FIXED: the fixed-moment turn ([fixedMoment]) of the end-of-onboarding interstitial (PLFullIntro3) chains
     * like any other placement, as in the fork; the launch ad never does, and intro3 without it keeps the old exclusion.
     */
    fun shouldChainFull2AfterInterstitial(
        full2FlagShow: Int,
        originPlacement: String?,
        fixedMoment: Boolean,
    ): Boolean {
        if (full2FlagShow != 1 && full2FlagShow != 3) return false
        if (originPlacement == PlacementDefault.PLFullIntro3) return fixedMoment

        return originPlacement != PlacementDefault.PLFullSplash &&
            originPlacement != PlacementDefault.PLFull2Splash
    }

    fun shouldChainFull2AfterRewarded(full2FlagShow: Int): Boolean =
        full2FlagShow == 2 || full2FlagShow == 3

    companion object {
        const val DEFAULT_CLICK_RETURN_COOLDOWN_MILLIS = 5_000L
    }
}
