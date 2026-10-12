package com.app.plugin.ads

import android.content.Context
import android.util.Log
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 2.4.4 ASPECT (Hợp đồng B, quyết định (b)): tỉ lệ media mà request native xin. Mặc định **ANY** (creative 1.91:1 và 1:1
 * của Meta không bị loại); resource app `@integer/ads_cf_native_media_ratio` lùi được: 1 = theo hướng màn hình (2.4.3),
 * 2 = LANDSCAPE, 3 = PORTRAIT; vắng/0/ngoài 1–3 = ANY. Trả mã trùng `MediaAspectRatio` Legacy (1 ANY, 2 LANDSCAPE,
 * 3 PORTRAIT); Next-Gen ánh xạ sang `NativeAd.NativeMediaAspectRatio`. Dùng chung hai module: không chạm lớp GMA.
 */
internal object NativeMediaRatio {
    internal const val RES_KEY: String = "ads_cf_native_media_ratio"

    private const val ANY = 1
    private const val LANDSCAPE = 2
    private const val PORTRAIT = 3
    private const val RAW_BY_ORIENTATION = 1

    private val logged = AtomicBoolean(false)
    private val mediaViewWarned = AtomicBoolean(false)

    /** Mã tỉ lệ cho một request native; đọc resource mỗi lần, log một dòng `Log.i` mỗi process (hợp đồng #65). */
    internal fun forRequest(context: Context, portrait: Boolean): Int {
        val raw = AppUtil.getResourceInt(context, RES_KEY)
        if (logged.compareAndSet(false, true)) {
            Log.i(LogUtil.TAG, "ads native media ratio: ${mode(raw)} ($RES_KEY=$raw)")
        }
        return when (raw) {
            RAW_BY_ORIENTATION -> if (portrait) PORTRAIT else LANDSCAPE
            LANDSCAPE -> LANDSCAPE
            PORTRAIT -> PORTRAIT
            else -> ANY
        }
    }

    /** Mã [forRequest] → giá trị tương ứng (enum Next-Gen): 2 [landscape], 3 [portrait], 1 và khác [any] (2.5.0 NGMAP). */
    internal fun <T> pick(code: Int, any: T, landscape: T, portrait: T): T = when (code) { LANDSCAPE -> landscape; PORTRAIT -> portrait; else -> any }

    private fun mode(raw: Int): String = when (raw) {
        RAW_BY_ORIENTATION -> "BY_ORIENTATION"
        LANDSCAPE -> "LANDSCAPE"
        PORTRAIT -> "PORTRAIT"
        else -> "ANY"
    }

    /** 2.5.0 PLMM (Fable C4): layout native của app thiếu/khác kiểu `ad_media` ⇒ không gắn media, một logE mỗi process. */
    internal fun warnNoMediaView(context: Context, layout: Int) {
        if (!mediaViewWarned.compareAndSet(false, true)) return
        LogUtil.logE("ads native: layout ${runCatching { context.resources.getResourceEntryName(layout) }.getOrNull()} (0x${Integer.toHexString(layout)}) " +
            "thiếu ad_media hoặc ad_media không phải MediaView, GMA yêu cầu MediaView; không gắn media")
    }

    internal fun resetForTest() { logged.set(false); mediaViewWarned.set(false) }
}
