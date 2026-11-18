package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import com.example.shared.monetization.SacredZone

/**
 * Contract trừu tượng hóa toàn bộ nghiệp vụ quảng cáo Google AdMob.
 *
 * Tuân thủ Clean Architecture: UI (Activity/Composable) không phụ thuộc trực tiếp
 * vào Google Mobile Ads SDK — chỉ tương tác qua interface này.
 *
 * Nguyên tắc bắt buộc:
 *  - Mọi lệnh gọi phải kiểm tra [setVipStatus] trước — VIP không bao giờ thấy quảng cáo.
 *  - Interstitial tuân theo [com.example.shared.monetization.AdPlacementPolicy]
 *    (warmup 60s, cooldown 120s, tối đa 3 lần/phiên, tắt hoàn toàn trong SacredZone).
 *  - App Open Ad không hiện khi Paywall/Splash/Permission dialog hoặc SacredZone đang mở.
 */
interface AdsManager {
    /**
     * Trạng thái đồng thuận hiển thị quảng cáo từ UMP (User Messaging Platform).
     * Chỉ nạp hoặc hiển thị quảng cáo khi [canRequestAds] là true.
     */
    val canRequestAds: kotlinx.coroutines.flow.StateFlow<Boolean>

    /** Khởi tạo Google Mobile Ads SDK và cấu hình Test Device IDs */
    fun initialize(context: Context)

    /**
     * Yêu cầu cập nhật thông tin đồng thuận UMP (User Messaging Platform / GDPR CMP)
     * và tự động hiển thị form consent nếu cần trước khi khởi tạo quảng cáo.
     */
    fun requestConsentAndInitialize(activity: Activity, onConsentCompleted: (canRequestAds: Boolean) -> Unit = {})

    /**
     * Đồng bộ trạng thái VIP. Khi [isVip] = true, toàn bộ cache quảng cáo
     * sẽ bị giải phóng và không bao giờ được nạp lại cho đến khi gói hết hạn.
     */
    fun setVipStatus(isVip: Boolean)

    /** Nạp ngầm Interstitial Ad để sẵn sàng phục vụ ngay khi cần */
    fun preloadInterstitial(context: Context, adUnitId: String)

    /**
     * Hiển thị Interstitial Ad với cooldown 120s, warmup 60s, max 3 lần/phiên & SacredZone check.
     *
     * @param onAdDismissed Callback luôn được gọi — dù ad được hiển thị rồi đóng,
     *   hoặc bị chặn bởi interval/VIP/SacredZone/load failure. Người dùng không bao giờ bị chờ.
     */
    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit)

    /** Đăng ký lắng nghe Application Lifecycle để phát App Open Ad khi resume */
    fun registerAppOpenAdLifecycle(application: Application)

    /** Nạp ngầm App Open Ad ở chế độ nền */
    fun preloadAppOpenAd(context: Context, adUnitId: String)

    /**
     * Bật/tắt chế độ chặn App Open Ad.
     * Phải gọi [setAppOpenAdSuppressed](true) khi mở Paywall, Splash, Permission dialog.
     * Gọi lại [setAppOpenAdSuppressed](false) khi đóng các màn hình đó.
     */
    fun setAppOpenAdSuppressed(isSuppressed: Boolean)

    /** Đánh dấu user đang ở vùng cảm xúc thiêng liêng (tắt toàn bộ quảng cáo). */
    fun enterSacredZone(zone: SacredZone) {}

    /** Rời khỏi vùng cảm xúc thiêng liêng. */
    fun exitSacredZone(zone: SacredZone) {}

    /** Đánh dấu hôm nay là ngày kỷ niệm/sinh nhật trọng đại (tắt quảng cáo cả ngày). */
    fun setCelebrationDay(isCelebrationDay: Boolean) {}

    /** Trả true nếu đang trong bất kỳ SacredZone hoặc ngày kỷ niệm nào. */
    fun isInSacredZone(): Boolean = false
}
