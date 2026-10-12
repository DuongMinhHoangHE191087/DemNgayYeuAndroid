package com.app.plugin.ads

/**
 * Khi nào được khởi tạo SDK quảng cáo: MobileAds (kéo theo mọi adapter mediation), Meta
 * Audience Network và AppLovin MAX. Chỉ khi hội đủ hai điều kiện:
 *
 * 1. app đã yêu cầu (`AdsMobMy.startSdkInit`), để app quyết thời điểm;
 * 2. UMP cho phép yêu cầu quảng cáo (`ConsentInformation.canRequestAds()`).
 *
 * Trang Get Started của Google Mobile Ads Android ghi: "If you need to obtain consent from users
 * in the European Economic Area (EEA), the United Kingdom (UK), or Switzerland, set any
 * request-specific flags … or otherwise take action before loading ads, ensure you do so before
 * initializing Google Mobile Ads SDK (Legacy)." và "Ads may be preloaded by Google Mobile Ads SDK
 * (Legacy) or mediation partner SDKs upon initialization." ("Legacy" là tên Google đặt cho
 * `play-services-ads`, bản SDK này dùng; trang đọc ngày 2026-09-23.) Tức là chỉ riêng việc khởi
 * tạo đã có thể tải quảng cáo, dù app chưa gọi lệnh load nào.
 *
 * Không có ngoại lệ "ngoài EEA thì khởi tạo sớm": trước khi UMP trả lời, app chưa biết người dùng
 * ở vùng nào. Người dùng quay lại không phải chờ, vì UMP lưu câu trả lời của phiên trước, nên
 * điều kiện 2 đã đúng ngay lúc mở app.
 */
internal class AdSdkStartGate {
    private var requested = false

    @Volatile
    var isStarted: Boolean = false
        private set

    /** App đã yêu cầu nhưng đang chờ consent. */
    val isWaitingForConsent: Boolean
        get() = requested && !isStarted

    /** App yêu cầu khởi tạo. @return `true` khi người gọi phải khởi tạo ngay (đúng một lần). */
    @Synchronized
    fun request(canRequestAds: Boolean): Boolean {
        requested = true
        return tryStart(canRequestAds)
    }

    /** UMP vừa có câu trả lời mới. @return `true` khi người gọi phải khởi tạo ngay. */
    @Synchronized
    fun onConsentChanged(canRequestAds: Boolean): Boolean = tryStart(canRequestAds)

    private fun tryStart(canRequestAds: Boolean): Boolean {
        if (isStarted || !requested || !canRequestAds) return false
        isStarted = true
        return true
    }
}
