package com.app.plugin.ads.core

import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.RemoveAdsMask.FLAG_FULLSCREEN
import com.app.plugin.ads.RemoveAdsMask.FLAG_REWARDED
import com.google.gson.JsonParser
import java.math.BigDecimal

/** Định dạng SDK nạp sẵn (2.3.0 PRELOAD); [rc] = tên trong `formats` của RC [AdPreloadPlan.REMOTE_KEY]. */
internal enum class PreloadFormat(val rc: String) {
    INTERSTITIAL("interstitial"), APP_OPEN("app_open"), REWARDED("rewarded"), REWARDED_INTERSTITIAL("rewarded_interstitial")
}

internal data class PreloadSlot(val format: PreloadFormat, val placement: String)

internal data class PreloadConfig(val enabled: Boolean, val formats: Set<PreloadFormat>, val delayMs: Long)

/** Một slot: nạp ([skip] `null`) hoặc bỏ, kèm lý do cho dòng log `preload: skip`. */
internal data class PreloadDecision(val slot: PreloadSlot, val skip: String?)

/**
 * Kế hoạch nạp sẵn, thuần: RC → [PreloadConfig]; cấu hình mạng + mask → [PreloadDecision]; chờ cửa sổ `fullStartTime` của
 * interstitial (D17-230 c). Chỉ toàn màn hình: banner, NtCl, native inline, rect và native full đứng riêng gắn view (nạp
 * khi app gắn view); native full của chuỗi Full2 đi theo interstitial/rewarded.
 */
internal object AdPreloadPlan {
    const val REMOTE_KEY = "ads_preload_android"
    const val MAX_DELAY_MS = 60_000L
    const val RWI_DEFAULT = "rwi_default"
    val DEFAULT = PreloadConfig(enabled = true, formats = PreloadFormat.values().toSet(), delayMs = 0L)

    /** RC thô → cấu hình và lỗi (`null` = hợp lệ). Rỗng = [DEFAULT]; hỏng, `schema_version` ≠ 1, trường sai = [DEFAULT] cả khối. */
    fun parse(raw: String?): Pair<PreloadConfig, String?> {
        if (raw.isNullOrBlank()) return DEFAULT to null
        return try {
            val o = JsonParser.parseString(raw).asJsonObject
            o.get("schema_version")?.let { require(whole(it.asString) == 1L) { "schema_version $it" } }
            val enabled = o.get("enabled")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "enabled $it" }; it.asBoolean }
            val formats = o.get("formats")?.asJsonArray?.map { e ->
                PreloadFormat.values().firstOrNull { it.rc == e.asString } ?: throw IllegalArgumentException("format $e")
            }?.toSet()
            val delay = o.get("delay_ms")?.let { whole(it.asString) ?: throw IllegalArgumentException("delay_ms $it") }
            PreloadConfig(enabled ?: true, formats ?: DEFAULT.formats, (delay ?: 0L).coerceIn(0L, MAX_DELAY_MS)) to null
        } catch (e: Exception) {
            DEFAULT to (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun whole(s: String): Long? = try { BigDecimal(s.trim()).longValueExact() } catch (e: Exception) { null }

    /** Slot theo [c]: interstitial `full_default` (+Full2), mọi app open có cấu hình, rewarded `gift_default`, rwi nếu có cấu hình. */
    fun decide(
        c: PreloadConfig, openPlacements: List<String>, rwiConfigured: Boolean, removed: (Int) -> Boolean, disabled: (Int) -> Boolean,
    ): List<PreloadDecision> = buildList {
        fun add(f: PreloadFormat, pl: String, skip: String? = null) {
            if (f in c.formats) add(PreloadDecision(PreloadSlot(f, pl), skip ?: blockReason(f, removed, disabled)))
        }
        add(PreloadFormat.INTERSTITIAL, PlacementDefault.PLFullDefault)
        val opens = openPlacements.filter { it.startsWith("full_") }.distinct()
        if (opens.isEmpty()) add(PreloadFormat.APP_OPEN, "", "not configured") else opens.forEach { add(PreloadFormat.APP_OPEN, it) }
        add(PreloadFormat.REWARDED, PlacementDefault.PLGiftDefault)
        add(PreloadFormat.REWARDED_INTERSTITIAL, RWI_DEFAULT, if (rwiConfigured) null else "not configured")
    }

    /**
     * Cùng cờ với cổng load của controller (cờ gỡ, cờ tắt). rwi: gỡ = `FLAG_FULLSCREEN`, tắt = `FLAG_REWARDED`, đúng vị từ của
     * `RewardedInterstitialController` (`isRemoved`/`isKilledOrDisabled`; 2.4.0 CARRY-ADS #27 — trước: gỡ rewarded cũng bỏ rwi).
     */
    fun blockReason(f: PreloadFormat, removed: (Int) -> Boolean, disabled: (Int) -> Boolean): String? {
        val (remove, kill) = when (f) {
            PreloadFormat.INTERSTITIAL, PreloadFormat.APP_OPEN -> FLAG_FULLSCREEN to FLAG_FULLSCREEN
            PreloadFormat.REWARDED -> FLAG_REWARDED to FLAG_REWARDED
            PreloadFormat.REWARDED_INTERSTITIAL -> FLAG_FULLSCREEN to FLAG_REWARDED
        }
        return if (removed(remove)) "remove_ads" else if (disabled(kill)) "disabled" else null
    }

    /** ms còn lại tới khi `isIntersAllowLoadAds` cho load (giây, `≤ t/3` khi `t ≥ 120`, như `AdsFrequencyGate`); 0 = cho ngay. */
    fun startWindowWaitMs(nowSec: Long, startSec: Long, fullStartTime: Int, sessionStartSec: Long, fullSessionTime: Int): Long {
        fun wait(from: Long, t: Int) = if (t >= 120) (from + t / 3 + 1 - nowSec).coerceAtLeast(0L) else 0L
        return maxOf(wait(startSec, fullStartTime), wait(sessionStartSec, fullSessionTime)) * 1000L
    }
}
