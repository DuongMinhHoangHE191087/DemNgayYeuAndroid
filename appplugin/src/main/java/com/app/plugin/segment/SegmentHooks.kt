package com.app.plugin.segment

import android.content.Context
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.firbase.FirHelperConfig
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics

/**
 * Seam duy nhất giữa SDK và [UserSegmentEngine] (2.2.0 SEG, hợp đồng B; fix 1 theo D25/D27-220): hook thay cho các dòng
 * fork đặt trong `AdsHelper`/`AppPluginBase`/`FirHelper` (0 dòng ở hai file đang ở trần), cộng ba đường engine cần ra
 * ngoài gói: RC ([rcLong]/[rcString], một `FirHelperConfig` dùng lại), user property có cổng consent ([setUserProperty],
 * R-P1; phát lại một lần khi tầng thành GRANTED) và đồng hồ fullscreen bền.
 *
 * Hook: [onSessionStart] (`SessionTracker.onForeground`, mỗi lần foreground như fork), [onAdImpression]
 * (`AdEventLog.revenue`: mọi mạng đi qua `PaidEvent.emit`), [onRewardedCompleted] (rewarded + rewarded interstitial),
 * [onFullscreenShown] (nhánh hiện được của interstitial, app open, Full2, rewarded interstitial) và [blocksFullscreen]
 * (đầu `showInterstitial`/`showOpenAd`, mọi placement như fork). Phần đọc RC + prefs chạy trên luồng nền
 * ([SegmentStartup]); trước khi xong hook xếp hàng và guardrail không chặn. Guardrail mặc định tắt
 * (`SegmentCadenceConfig.globalGuardrailMillis` = 0, D10-220); RC `seg_cadence_guardrail_seconds` bật.
 */
internal object SegmentHooks {
    /** `reason` của `ad_show_fail` khi guardrail chặn (BE3 xếp vào `blocked_policy`). */
    const val REASON_GUARDRAIL = "seg_guardrail"

    /** Khoá bền của đồng hồ, trùng tên fork (`AdsHelper.KEY_LAST_FULLSCREEN_AD_SHOWN`) để app bỏ fork giữ được mốc cũ. */
    const val KEY_LAST_FULLSCREEN = "ads_last_fullscreen_shown_millis"

    /** Format fullscreen có doanh thu tính vào segment, như fork (+ `rewarded_interstitial` 1.9.0); banner/native loãng tín hiệu. */
    private val IN_SCOPE_FORMATS = setOf("interstitial", "opened", "rewarded", "rewarded_interstitial")

    @Volatile private var context: Context? = null
    @Volatile private var lastFullscreenMillis = -1L
    private val rcConfig by lazy { FirHelperConfig() }

    /** RC Long của [key] khi RC có giá trị từ server, null khi không có hoặc đọc lỗi (test thay). */
    @Volatile
    internal var rcLong: (String) -> Long? = { key -> runCatching { rcConfig.run { if (hasKey(key)) getLong(key) else null } }.getOrNull() }

    /** RC chuỗi của [key]; rỗng, vắng hay lỗi = null (test thay). */
    @Volatile
    internal var rcString: (String) -> String? = { key -> runCatching { rcConfig.getString(key, "") }.getOrNull()?.ifEmpty { null } }

    /** 2.4.2 SPLASH: mặc định cục bộ của guardrail, integer resource app (giây); vắng, 0 hay chưa có context = null. RC thắng. */
    internal fun localGuardrailSeconds(): Long? =
        context?.let { AppUtil.getResourceInt(it, "ads_cf_seg_cadence_guardrail_seconds") }?.takeIf { it > 0 }?.toLong()

    /** Nơi ghi user property thật (Firebase Analytics); test thay để đếm. Chỉ gọi qua [setUserProperty]. */
    @Volatile
    internal var propertySink: (String, String) -> Unit = { name, value -> Firebase.analytics.setUserProperty(name, value) }

    /** Tầng thành GRANTED: phát lại user property bị giữ lúc UNKNOWN (một lần mỗi lần đổi); DENIED/PARTIAL không làm gì thêm. */
    private val onTier: (ConsentTier) -> Unit = { tier ->
        if (tier == ConsentTier.GRANTED) SegmentStartup.runOrQueue { UserSegmentEngine.instance.republishUserProperties() }
    }

    /** `AppPluginManager.initPlugin` (main): chỉ ghi nhận và hẹn; RC + prefs của engine, bộ eCPM, đồng hồ nạp trên luồng nền. */
    fun init(context: Context) {
        val app = context.applicationContext ?: context
        this.context = app
        if (!ConsentState.hasOnChange(onTier)) ConsentState.onChange(onTier)
        SegmentStartup.start {
            lastFullscreenMillis = PreUtil.getLong(app, KEY_LAST_FULLSCREEN, -1L)
            UserSegmentEngine.instance.init(app)
            EcpmBucketResolver.init(app)
            val engine = UserSegmentEngine.instance
            LogUtil.logD("segment: init geoTier=${engine.geoTier} segment=${engine.segment.value}")
        }
    }

    /** `SESSION_START` mỗi lần app vào foreground (fork: `ProcessLifecycleOwner.onStart`). */
    fun onSessionStart() = System.currentTimeMillis().let { t -> SegmentStartup.runOrQueue { UserSegmentEngine.instance.recordEventNow(SegmentEvent.SESSION_START, null, t) } }

    /** Mọi impression fullscreen trong phạm vi: có giá trị → kèm nhóm eCPM; ≤ 0 (Meta direct, AdMob 0) → đếm, không nhóm. */
    fun onAdImpression(format: String, revenueMicros: Long) {
        if (format !in IN_SCOPE_FORMATS) return
        val t = System.currentTimeMillis()
        SegmentStartup.runOrQueue {
            val payload = if (revenueMicros > 0L) mapOf("ecpmBucket" to EcpmBucketResolver.instance.recordImpression(revenueMicros)) else null
            UserSegmentEngine.instance.recordEventNow(SegmentEvent.AD_IMPRESSION, payload, t)
        }
    }

    fun onRewardedCompleted() = System.currentTimeMillis().let { t -> SegmentStartup.runOrQueue { UserSegmentEngine.instance.recordEventNow(SegmentEvent.REWARDED_AD_COMPLETED, null, t) } }

    /** Fullscreen vừa hiện: đóng dấu đồng hồ, ghi bền để kill/khởi động lại không vượt guardrail. */
    fun onFullscreenShown(nowMs: Long) = SegmentStartup.runOrQueue {
        lastFullscreenMillis = nowMs
        context?.let { PreUtil.setLong(it, KEY_LAST_FULLSCREEN, nowMs) }
    }

    /** Guardrail chéo loại của fork: fullscreen gần nhất cách [nowMs] chưa đủ `globalGuardrailMillis`; chưa nạp xong = không chặn. */
    fun blocksFullscreen(nowMs: Long): Boolean {
        if (!SegmentStartup.isLoaded) return false
        val last = lastFullscreenMillis
        return last > 0 && nowMs - last < UserSegmentEngine.instance.cadenceConfig().globalGuardrailMillis
    }

    /** User property của engine: DENIED/UNKNOWN → không gì rời máy (UNKNOWN không bao giờ là đồng ý); lỗi thì nuốt. */
    fun setUserProperty(name: String, value: String) {
        val tier = ConsentState.tier()
        if (tier == ConsentTier.DENIED || tier == ConsentTier.UNKNOWN) return
        try {
            propertySink(name, value)
        } catch (e: Exception) {
            LogUtil.logE("segment: setUserProperty $name failed: ${e.message}")
        }
    }

    /** Test: về trạng thái trước `init` (đồng hồ, hàng đợi); seam RC/sink do test tự đặt lại. */
    internal fun resetForTest() {
        context = null
        lastFullscreenMillis = -1L
        SegmentStartup.resetForTest()
    }
}
