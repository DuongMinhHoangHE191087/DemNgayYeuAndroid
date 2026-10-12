package com.app.plugin.ads

import android.os.Bundle
import com.app.plugin.BuildInfo
import com.app.plugin.consent.ConsentTier
import com.app.plugin.logevent.EventNames

/**
 * Event `sdk_health` (doc 60 G, gate G7): gửi một lần mỗi process, khi `AdSdkStartup` khởi tạo mạng quảng
 * cáo (hoặc bỏ qua vì kill switch). Hợp đồng tên (khoá ở `EventNameContractTest` và `SdkHealthTest`): tên
 * event và param snake_case, tên ≤ 40 ký tự, giá trị chuỗi ≤ 100 ký tự. Không mang dữ liệu cá nhân.
 */
internal object SdkHealth {
    const val EVENT = EventNames.SDK_HEALTH

    /** Giá trị `networks` khi kill switch `ads` chặn khởi tạo mạng. */
    const val NETWORKS_KILLED = "killed"

    /**
     * Các mạng được **yêu cầu** khởi tạo (có SDK trong bản dựng), không phải các mạng đã khởi tạo xong.
     * [meta] `false`: Meta Audience Network bị bỏ qua vì consent (`DENIED` trong vùng GDPR, P1).
     */
    fun networks(maxAvailable: Boolean, meta: Boolean): String =
        listOfNotNull("admob", "meta".takeIf { meta }, "max".takeIf { maxAvailable }).joinToString(",")

    /**
     * 1.9.0 T1: [networks] + `,partners=<csv>` — bidding partner bật và có adapter (`BiddingPartners`, thứ tự tập đóng;
     * rỗng: `partners=`). Dashboard tách ở `,partners=`. Tối đa 68 ký tự (≤ 100).
     */
    internal fun withPartners(networks: String, present: List<String>): String = "$networks,partners=${present.joinToString(",")}"

    /**
     * @param gdprApplies `TcfConsent.gdprApplies`; `null` (khoá IABTCF vắng) thành `"unknown"`.
     * @param networks [networks] hoặc [NETWORKS_KILLED].
     * @param sinceRequestMs từ lần đầu app request mạng quảng cáo (trước cổng consent) tới lúc **bắt đầu**
     *   khởi tạo mạng, không phải lúc khởi tạo xong: tức phần lớn là thời gian chờ consent.
     * @param kill `SdkKillSwitch.label` của phiên bản đang chạy: `none`, `version` (cả phiên bản bị tắt), hoặc
     *   các module bị tắt theo thứ tự `ads`, `iap`, `logevent`, nối bằng dấu phẩy, không khoảng trắng (ví dụ
     *   `ads,iap`). Dạng này là hợp đồng với dashboard (tách theo dấu phẩy), khoá ở `SdkHealthTest`.
     */
    fun params(
        sdkVersion: String,
        consentTier: ConsentTier,
        gdprApplies: Boolean?,
        networks: String,
        sinceRequestMs: Long,
        kill: String,
    ): Map<String, Any> = linkedMapOf(
        "sdk_version" to sdkVersion,
        "consent_tier" to consentTier.name,
        "consent_gdpr" to (gdprApplies?.toString() ?: "unknown"),
        "networks" to networks,
        "since_request_ms" to sinceRequestMs,
        "kill" to kill,
    )

    /** `AppPluginBase.onCreate` main-thread ms; -1 khi chưa đo (app không kế thừa `AppPluginBase`); process phụ đo onCreate không có `initPlugin()`. */
    @Volatile
    internal var initMs: Long = -1L

    /** Thêm `init_ms` vào [params] (giữ nguyên chữ ký [params], hợp đồng 6 param của `SdkHealthTest`). */
    internal fun withInitMs(params: Map<String, Any>): Map<String, Any> = params + ("init_ms" to initMs)

    /** Thêm `license` (`LicenseGate.label`) vào [params], giữ chữ ký [params] (2.1.0 SA1, R-SA1 I2). */
    internal fun withLicense(params: Map<String, Any>, label: String): Map<String, Any> = params + ("license" to label)

    /** Thêm `build` (= `BuildInfo.ID`, `appplugin/<phiên bản>/<sha>`, 2.1.0 SC1) vào [params], giữ chữ ký [params]. */
    internal fun withBuild(params: Map<String, Any>): Map<String, Any> = params + ("build" to BuildInfo.ID)

    fun toBundle(params: Map<String, Any>): Bundle = Bundle().apply {
        for ((key, value) in params) {
            if (value is Long) putLong(key, value) else putString(key, value.toString())
        }
    }
}
