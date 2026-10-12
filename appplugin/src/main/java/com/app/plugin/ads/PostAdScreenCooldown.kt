package com.app.plugin.ads

/**
 * Cooldown "N màn hình" sau một quảng cáo toàn màn hình: sau interstitial, app open hay
 * rewarded, người dùng phải chuyển qua [requiredScreens] màn hình nữa thì interstitial, app open
 * tự động và banner mới được hiện lại.
 *
 * Không chặn `showRewarded()` (người dùng tự bấm xem để nhận thưởng, không được trông như bị
 * đơ) và không chặn full2 (chuỗi nối có chủ đích ngay sau chính quảng cáo vừa bật cooldown).
 *
 * `requiredScreens = 0` (mặc định) là tắt, để app không bật thì nhịp quảng cáo giữ nguyên như
 * trước 1.2.0. Speaker Clean và Invoice Maker dùng 2. "Màn hình" là thứ app báo qua
 * `AdsHelper.recordScreenChange()`, thường là mỗi lần đổi tab hoặc đổi route.
 */
internal class PostAdScreenCooldown(requiredScreens: Int = 0) {

    @Volatile
    var requiredScreens: Int = requiredScreens.coerceAtLeast(0)
        set(value) {
            field = value.coerceAtLeast(0)
        }

    @Volatile
    private var screensSinceLastAd: Int = Int.MAX_VALUE

    val isActive: Boolean
        get() = screensSinceLastAd < requiredScreens

    /** @return `true` khi cooldown vừa được bật (tức đang bật tính năng). */
    fun onAutoAdShown(): Boolean {
        if (requiredScreens == 0) return false
        screensSinceLastAd = 0
        return true
    }

    /** @return `true` đúng ở lần chuyển màn hình làm cooldown hết hiệu lực. */
    fun onScreenChange(): Boolean {
        if (!isActive) return false
        screensSinceLastAd++
        return !isActive
    }

    /** Đếm hiện tại, chỉ để ghi log. */
    val progress: String
        get() = "${screensSinceLastAd.coerceAtMost(requiredScreens)}/$requiredScreens"
}
