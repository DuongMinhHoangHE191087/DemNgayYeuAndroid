package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context

/**
 * Contract trừu tượng hóa toàn bộ nghiệp vụ quảng cáo Google AdMob.
 *
 * Tuân thủ Clean Architecture: UI (Activity/Composable) không phụ thuộc trực tiếp
 * vào Google Mobile Ads SDK — chỉ tương tác qua interface này.
 *
 * Nguyên tắc bắt buộc:
 *  - Mọi lệnh gọi phải kiểm tra [setVipStatus] trước — VIP không bao giờ thấy quảng cáo.
 *  - Interstitial có interval tối thiểu 30 giây (capped).
 *  - App Open Ad không hiện khi Paywall/Splash/Permission dialog đang mở.
 */
interface AdsManager {

    /** Khởi tạo Google Mobile Ads SDK và cấu hình Test Device IDs */
    fun initialize(context: Context)

    /**
     * Đồng bộ trạng thái VIP. Khi [isVip] = true, toàn bộ cache quảng cáo
     * sẽ bị giải phóng và không bao giờ được nạp lại cho đến khi gói hết hạn.
     */
    fun setVipStatus(isVip: Boolean)

    /** Nạp ngầm Interstitial Ad để sẵn sàng phục vụ ngay khi cần */
    fun preloadInterstitial(context: Context, adUnitId: String)

    /**
     * Hiển thị Interstitial Ad với Interval Capping 30 giây.
     *
     * @param onAdDismissed Callback luôn được gọi — dù ad được hiển thị rồi đóng,
     *   hoặc bị chặn bởi interval/VIP/load failure. Người dùng không bao giờ bị chờ.
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
}
