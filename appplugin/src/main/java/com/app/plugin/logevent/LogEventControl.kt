package com.app.plugin.logevent

import android.content.Context
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Điều khiển từ xa việc gửi event (1.7.0 Task 7), khoá Remote Config [REMOTE_KEY]:
 * `{"version":1,"enabled":true,"default_sample":1.0,"sample":{"<event>":0.1}}`.
 *
 * - `enabled=false` ⇔ mọi event DROPPED, như kill switch `logevent` (decision log giữ bản ghi trên đĩa).
 * - Lấy mẫu tất định theo USER (không theo event): một user hoặc được giữ hết hoặc bỏ hết event có thể lấy mẫu, nên phễu
 *   `ad_request` → `ad_fill` → `ad_show` không bị cắt giữa chừng; giữ ở tỉ lệ r thì cũng giữ ở mọi r' > r. Event cùng chuỗi
 *   nên cùng tỉ lệ (hoặc event cuối ≥ event đầu), nếu không view fill/show lệch.
 *   Băm là FNV-1a của [HoldoutManager.bucketWith] (không có hàm băm mới), độ mịn 1/10 000.
 * - [NEVER_SAMPLE] (tập bền của T6, brain, iap) luôn giữ, kể cả khi cấu hình ghi tỉ lệ cho chúng.
 * - Chỉ bớt: không bao giờ làm event nào được gửi khi cổng consent chưa cho (cổng vẫn chạy sau bước này).
 * - Event được giữ với tỉ lệ < 1 mang `sample_rate` trong `eventData` để backend nhân ngược.
 * - Cấu hình hỏng hoặc `version` ≠ 1 → mặc định (giữ hết) + `logE`. Lưu ở [PREF_KEY] cho lần mở kế ([restore]).
 */
internal object LogEventControl {
    const val REMOTE_KEY = "logevent_control"
    const val PREF_KEY = "mem_logevent_control"
    const val SAMPLE_RATE_KEY = "sample_rate"
    private const val NAMESPACE = "logevent_sample"
    private const val BUCKETS = 10_000
    private const val SUPPORTED_VERSION = 1

    val NEVER_SAMPLE: Set<String> = DurableEvents.DURABLE +
        setOf(EventNames.BRAIN_DECISION, EventNames.BRAIN_FEEDBACK, EventNames.IAP_PURCHASE)

    internal data class Config(
        val enabled: Boolean = true,
        val defaultSample: Double = 1.0,
        val sample: Map<String, Double> = emptyMap()
    )

    @Volatile
    internal var current: Config = Config()
    private val disabledLogged = AtomicBoolean(false)

    /** Lúc mở app: áp bản đã lưu (đọc bộ nhớ, prefs đã nạp). Lỗi thì giữ mặc định và log. */
    fun restore(context: Context) {
        val raw = try {
            PreUtil.getString(context, PREF_KEY, "")
        } catch (e: Exception) {
            LogUtil.logE("logevent_control: không đọc được bản đã lưu, giữ mặc định: ${e.message}")
            return
        }
        current = parse(raw)
    }

    /** Giá trị [raw] của [REMOTE_KEY] vừa fetch: áp ngay và lưu cho lần mở kế. Chuỗi rỗng = gỡ (về mặc định). */
    fun applyRemote(context: Context?, raw: String) {
        val wasEnabled = current.enabled
        current = parse(raw)
        disabledLogged.set(false)
        // P-1: bật lại giữa phiên → bản bền bị chặn lúc tắt replay ngay (không chờ lần mở kế).
        if (!wasEnabled && current.enabled) DurableEvents.onTransportReady()
        if (context == null) return
        try {
            PreUtil.setString(context, PREF_KEY, raw)
        } catch (e: Exception) {
            LogUtil.logE("logevent_control: không lưu được cho lần mở kế: ${e.message}")
        }
    }

    internal fun parse(raw: String?): Config {
        if (raw.isNullOrBlank()) return Config()
        return try {
            val o = JsonParser.parseString(raw).asJsonObject
            if (o.get("version")?.asInt != SUPPORTED_VERSION) return bad("version không hỗ trợ")
            Config(
                enabled = o.get("enabled")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) it.asBoolean else return bad("enabled không phải boolean") } ?: true,
                defaultSample = o.get("default_sample")?.let { clamp(it.asDouble) } ?: 1.0,
                sample = samples(o.getAsJsonObject("sample"))
            )
        } catch (e: Exception) {
            bad("JSON hỏng: ${e.message}")
        }
    }

    private fun samples(o: JsonObject?): Map<String, Double> =
        o?.entrySet()?.associate { it.key to clamp(it.value.asDouble) } ?: emptyMap()

    private fun bad(why: String): Config {
        LogUtil.logE("logevent_control: $why, dùng mặc định (giữ mọi event)")
        return Config()
    }

    private fun clamp(v: Double): Double = if (v.isNaN()) 1.0 else v.coerceIn(0.0, 1.0)

    /** Tỉ lệ giữ của [eventName]: [NEVER_SAMPLE] luôn 1.0. */
    internal fun rateFor(cfg: Config, eventName: String): Double =
        if (eventName in NEVER_SAMPLE) 1.0 else cfg.sample[eventName] ?: cfg.defaultSample

    /** Tất định theo [userId]; [eventName] không vào hàm băm (giữ chữ ký). */
    @Suppress("UNUSED_PARAMETER")
    internal fun keeps(rate: Double, userId: String, eventName: String): Boolean =
        rate >= 1.0 || HoldoutManager.bucketWith(NAMESPACE, userId) < rate * BUCKETS

    /** Số event bị bỏ vì lấy mẫu (mỗi process, như bộ đếm của `LogEventTransport`); in ở dòng `logevent stats` (`sampled=`). */
    internal val sampledOutCount = AtomicLong(0)

    /**
     * Cổng của [LogEventTransport.sendEventAt]: `null` = không gửi (DROPPED); ngược lại trả `eventData`, thêm
     * `sample_rate` khi tỉ lệ < 1. Chạy trước cổng consent, chỉ có thể bớt event.
     */
    internal fun gate(eventName: String, eventData: Map<String, Any>, userId: String?): Map<String, Any>? {
        val cfg = current
        if (!cfg.enabled) {
            if (disabledLogged.compareAndSet(false, true)) LogUtil.logE("logevent_control: enabled=false — không gửi event (từ $eventName)")
            return null
        }
        val rate = rateFor(cfg, eventName)
        if (rate >= 1.0) return eventData
        if (!keeps(rate, userId ?: LogEventServerHelper.currentUserId(), eventName)) {
            sampledOutCount.incrementAndGet()
            return null
        }
        return eventData + (SAMPLE_RATE_KEY to rate)
    }

    /**
     * Được gửi lên server ngay lúc này không (P-1): kill switch `logevent` không bật, `enabled` không tắt, và tầng consent
     * không phải DENIED (N1: sau DENIED không byte nào rời máy, kể cả event đã xếp hàng trước DENIED). Đọc field volatile.
     */
    internal fun sendAllowed(): Boolean = !com.app.plugin.SdkKillSwitch.current.killsLogevent(com.app.plugin.BuildConfig.SDK_VERSION) &&
        current.enabled && com.app.plugin.consent.ConsentState.tier() != com.app.plugin.consent.ConsentTier.DENIED

    /** CHỈ DÙNG TRONG TEST. */
    internal fun resetForTest() {
        current = Config()
        disabledLogged.set(false)
        sampledOutCount.set(0)
    }
}
