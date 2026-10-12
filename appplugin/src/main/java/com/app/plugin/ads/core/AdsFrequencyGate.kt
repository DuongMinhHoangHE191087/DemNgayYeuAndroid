package com.app.plugin.ads.core

import android.content.Context
import com.app.plugin.ads.AdsCfOverrides
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.GmaLoadContext
import com.app.plugin.app.AppPluginManager
import com.app.plugin.until.AppUtil
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Luật tần suất và cổng thời gian của [AdsHelper] (1.5.0 Task 2, G8): `ads_policy_android` ([AdsPolicy]), cooldown
 * N màn hình, và 7 cổng "được phép hiện/load theo thời gian". Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên văn; state
 * ở lại façade (R1: `postAdCooldown`, `fullScreenAdPolicy`, `mContext`; R3: mốc `*TimeShow`, `currConfig`, `bnIsShow`)
 * đọc qua [h] ở mỗi lần gọi, không giữ bản sao. `postAdCooldown`/`fullScreenAdPolicy` là `private val` (không test nào gán
 * lại); cái phải tránh là đọc `h` lúc dựng: lớp này dựng trước `fullScreenAdPolicy` (khai sau `gate` trong thứ tự
 * initializer của AdsHelper; `postAdCooldown` khai trước), nên một bản sao eager của nó sẽ là `null` (R-2;
 * `tools/core_internal_check.sh` chặn field-level `h.`, `init {`, `by lazy`).
 */
internal class AdsFrequencyGate(private val h: AdsHelper) {
    val adsPolicy: AdsPolicy get() = AdsPolicy.current

    /** Cooldown N màn hình app đặt bằng [setPostAdScreenCooldown]; `screens_before_interstitial` thắng khi có. */
    @Volatile private var appPostAdScreens = 0
    /** App đã gọi [recordScreenChange] ít nhất một lần trong process này (xem [isPostAdCooldownApplicable]). */
    @Volatile private var hasScreenChangeReport = false
    @Volatile private var warnedPolicyScreensWithoutReport = false

    fun applyPolicy(policy: AdsPolicy) {
        AdsPolicy.current = policy
        h.fullscreenPolicy().clickReturnCooldownMillis = policy.postClickCooldownMillis()
        h.screenCooldown().requiredScreens = policy.screensBeforeInterstitial ?: appPostAdScreens
        LogUtil.logD(
            "ads policy schema=${policy.schemaVersion} firstSession=${policy.noInterstitialFirstSession} " +
                "appOpenFrom=${policy.appOpenFromSession} gap=${policy.interstitialMinGapSeconds ?: "fullDeltatime"} " +
                "screens=${h.screenCooldown().requiredScreens} clickCooldownMs=${h.fullscreenPolicy().clickReturnCooldownMillis} " +
                "appOpenMaxAge=${policy.appOpenMaxAgeMinutes}min fullscreenMaxAge=${policy.fullscreenMaxAgeMillis() / 60_000L}min"
        )
    }

    fun applyRemotePolicy(raw: String) {
        applyPolicy(AdsPolicy.parse(raw))
        GmaLoadContext.apply(raw)
        AdsCfOverrides.apply(raw)
        AdsPolicy.save(h.appContext(), raw)
    }

    fun restorePolicy(context: Context) {
        val raw = AdsPolicy.restoreRaw(context)
        applyPolicy(AdsPolicy.parse(raw))
        GmaLoadContext.apply(raw)
        AdsCfOverrides.apply(raw)
    }

    fun setPostAdScreenCooldown(screens: Int) {
        appPostAdScreens = screens.coerceAtLeast(0)
        val fromPolicy = adsPolicy.screensBeforeInterstitial
        if (fromPolicy != null) {
            LogUtil.logD("ads cooldown: app đặt $screens màn, ads_policy_android đặt $fromPolicy, giữ $fromPolicy")
            return
        }
        h.screenCooldown().requiredScreens = screens
    }

    fun isPostAdScreenCooldownActive(): Boolean = h.screenCooldown().isActive

    fun recordScreenChange() {
        hasScreenChangeReport = true
        val cleared = h.screenCooldown().onScreenChange()
        if (h.screenCooldown().requiredScreens > 0) {
            LogUtil.logD("ads cooldown navigation ${h.screenCooldown().progress}")
        }
        // showBanner() chỉ kiểm tra cooldown ở lần được gọi kế tiếp, mà với màn hình đã gắn
        // banner thì thường không có lần kế tiếp nào: không chủ động hiện lại ở đây thì banner
        // bị ẩn lúc quảng cáo hiện sẽ ẩn mãi.
        if (cleared) {
            LogUtil.logD("ads bn helper resume after post-ad navigation cooldown")
            h.resumeParkedBanner()
        }
    }

    /**
     * Điều 7.4: cooldown N màn hình do `ads_policy_android` bật chỉ chạy khi app đã báo màn ([recordScreenChange])
     * ít nhất một lần trong process. Không thì app không báo màn bị chặn interstitial, app open tự động và banner
     * vĩnh viễn sau quảng cáo đầu tiên: cooldown không bật cho quảng cáo này và SDK logE một lần. Giá trị app tự
     * đặt bằng [setPostAdScreenCooldown] giữ hành vi cũ.
     */
    private fun isPostAdCooldownApplicable(): Boolean {
        val fromPolicy = adsPolicy.screensBeforeInterstitial
        if (fromPolicy == null || fromPolicy == 0 || hasScreenChangeReport) return true
        if (!warnedPolicyScreensWithoutReport) {
            warnedPolicyScreensWithoutReport = true
            LogUtil.logE(
                "ads policy: screens_before_interstitial=$fromPolicy nhưng app chưa gọi AdsHelper.recordScreenChange() " +
                    "lần nào; cooldown màn hình không bật (không chặn quảng cáo) tới lần gọi đầu"
            )
        }
        return false
    }

    fun markAutoAdShown() {
        if (!isPostAdCooldownApplicable()) return
        if (!h.screenCooldown().onAutoAdShown()) return
        // Chặn trong showBanner() chỉ áp cho lần gọi sau; banner đang hiện từ trước quảng cáo
        // phải tự ẩn ở đây.
        if (h.bnIsShow) {
            h.hideBanner()
        }
    }

    /**
     * 2.5.0 LAM2: open ad (`full_splash` miễn ở AppOpenController) không hiện trong `openAdDeltaTime` sau mọi toàn màn hình
     * (open, interstitial/rwi, Full2, rewarded). `@integer/ads_cf_open_gap_own_only` = 1 (app hoặc RC, đọc mỗi lần) ⇒ chỉ open ad.
     */
    fun isOpenAllowShowAds(): Boolean {
        val tCurr = System.currentTimeMillis() / 1000
        val ownOnly = h.appContext()?.let { runCatching { AppUtil.getResourceInt(it, "ads_cf_open_gap_own_only") == 1 }.getOrNull() } == true
        val last = if (ownOnly) h.openTimeShow else maxOf(h.openTimeShow, h.fullTimeShow, h.full2TimeShow, h.giftTimeShow)
        return (tCurr - last) > h.currConfig.openAdDeltaTime
    }

    fun isIntersAllowLoadAds(): Boolean {
        val tCurr = System.currentTimeMillis() / 1000
        if ((tCurr - AppPluginManager.instance.getTimeStart()) <= h.currConfig.fullStartTime / 3 && h.currConfig.fullStartTime >= 120) {
            return false
        }
        if ((tCurr - AppPluginManager.instance.getTimeSesstionStart()) <= h.currConfig.fullSesstionTime / 3 && h.currConfig.fullSesstionTime >= 120) {
            return false
        }
        return true
    }

    /**
     * Lý do chặn interstitial theo thời gian và phiên (hằng `AdShowFailReason`), hoặc `null`. Một chỗ cho
     * [AdsHelper.showInterstitial] (khi `isDeltaTime`) và [AdsHelper.isInterstitialReadyToShow], nên hai hàm luôn cùng kết luận:
     * `fullTimeStart` cũ (`not_allow_show`), rồi [AdsPolicy.interstitialVerdict] với luật phiên đầu và luật
     * khoảng cách duy nhất (của policy, không thì `fullDeltatime`).
     */
    fun interstitialRefusal(): String? {
        val tCurr = System.currentTimeMillis() / 1000
        if ((tCurr - AppPluginManager.instance.getTimeStart()) <= h.currConfig.fullStartTime) {
            return Constants.AdShowFailReason.NOT_ALLOW_SHOW
        }
        return SharedFullscreenGap.verdict(h, tCurr)
    }

    fun isNtClAllowShowAds(): Boolean {
        return (System.currentTimeMillis() / 1000 - h.clTimeShow) > h.currConfig.clDeltaTime
    }

    fun isBnAllowShowAds(): Boolean {
        return (System.currentTimeMillis() / 1000 - h.bnTimeShow) > h.currConfig.bannerDeltaTime
    }

    fun isFull2AllowShowAds(): Boolean {
        val tCurr = System.currentTimeMillis() / 1000
        if ((tCurr - h.full2TimeShow) <= h.currConfig.full2DeltaTime) {
            return false
        }
        return true
    }

    fun isRewardedAllowShowAds(): Boolean {
        val tCurr = System.currentTimeMillis() / 1000
        if ((tCurr - h.giftTimeShow) <= h.currConfig.giftDeltaTime) {
            return false
        }
        return true
    }
}

/**
 * Luật phiên đầu + khoảng cách DÙNG CHUNG của interstitial và rewarded interstitial (1.9.0 Task 2, hợp đồng §C):
 * [AdsPolicy.interstitialVerdict] trên cùng mốc `fullTimeShow`. Object riêng vì façade giữ [AdsFrequencyGate] `private`
 * (AdsHelper không thêm dòng) và khối dump của lớp đó có từ 1.5.0; đọc [AdsHelper] mỗi lần gọi.
 */
internal object SharedFullscreenGap {
    fun verdict(h: AdsHelper, nowSeconds: Long = System.currentTimeMillis() / 1000): String? =
        AdsPolicy.current.interstitialVerdict(
            sessionCount = AppPluginManager.instance.getSessionApp(),
            secondsSinceLast = nowSeconds - h.fullTimeShow,
            legacyMinGapSeconds = h.currConfig.fullDeltaTime
        )
}
