package com.app.plugin.ads

import android.content.Context
import com.app.plugin.consent.GppSignals
import com.app.plugin.until.LogUtil

/**
 * Seam core → MAX (2.0.0 C1c, D1, plan §Hợp đồng C): core (`AdsHelper`, `AdSdkStartup`, `AdsNetworkRegistry`,
 * `PaidEvent`) gọi mạng MAX chỉ qua giao diện này, không tham chiếu lớp nào của gói MAX. Impl ở cùng module (D16), lấy
 * bằng tên cố định [IMPL_CLASS] (keep rule trong `consumer-rules.pro`), để bản sau dời MAX sang module khác mà không
 * chạm core.
 */
internal interface MaxBridge {
    /** Dựng đối tượng quảng cáo MAX (không khởi tạo AppLovin). */
    fun initAd(context: Context)
    /** Khởi tạo AppLovin; chỉ `AdSdkStartup` gọi, một lần, khi consent cho phép. */
    fun startNetwork(context: Context)
    /** GPP giữa phiên (`setDoNotSell`); chưa khởi tạo thì không làm gì. */
    fun followGpp(context: Context, gpp: GppSignals?)
    /** Trạng thái "đã khởi tạo" về như process mới. Chỉ cho test. */
    fun resetForTest()
    /** Tên mạng chuẩn hoá từ tên mạng MAX báo (`applovin`, `pangle`, …; `null` → `source_null`). */
    fun getAdnetName(adnet: String?): String
    /** Đối tượng quảng cáo MAX đã dựng ở [initAd], tra theo loại `AdsIDDef`. */
    fun networks(): Networks

    interface Networks {
        fun getAdsBanner(type: Int): AdsBaseBanner?
        fun getAdsNtCl(type: Int): AdsBaseBanner?
        fun getAdsRect(type: Int): AdsBaseBanner?
        fun getAdsNative(type: Int): AdsBaseNative?
        fun getAdsFull(type: Int): AdsBaseFull?
        fun getAdsGift(type: Int): AdsBaseGift?
    }

    companion object {
        const val IMPL_CLASS = "com.app.plugin.ads.adsmax.MaxBridgeImpl"
        private val defaultClassFor: (String) -> Class<*> = { Class.forName(it) }
        /** Cách nạp lớp impl; test thay để đếm lần dò. */
        @Volatile internal var classFor: (String) -> Class<*> = defaultClassFor
        private val lock = Any()
        @Volatile private var probed = false
        @Volatile private var bridge: MaxBridge? = null

        /**
         * Impl MAX, dò **một lần** mỗi process. Không có SDK AppLovin ([AdsHelper.isMaxSdkAvailable] `false`, app tiện
         * ích) thì không dò, không nạp lớp MAX nào → `null` (nhánh F2-MAX 1.6.4). Lớp vắng hay lỗi → `null`, log, không ném.
         */
        fun get(): MaxBridge? {
            if (probed) return bridge
            synchronized(lock) {
                if (!probed) {
                    bridge = probe()
                    probed = true
                }
            }
            return bridge
        }

        private fun probe(): MaxBridge? {
            if (!AdsHelper.isMaxSdkAvailable) return null
            return try {
                classFor(IMPL_CLASS).getDeclaredConstructor().newInstance() as MaxBridge
            } catch (e: Throwable) {
                LogUtil.logE("ads max: không dò được $IMPL_CLASS: $e")
                null
            }
        }

        /** Về như process mới: dò lại ở lần [get] sau, nạp lớp bằng `Class.forName`. Chỉ cho test. */
        @JvmSynthetic
        internal fun resetProbeForTest() {
            synchronized(lock) {
                probed = false
                bridge = null
                classFor = defaultClassFor
            }
        }
    }
}
