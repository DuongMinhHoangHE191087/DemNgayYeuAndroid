package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import com.app.plugin.until.LogUtil
import com.google.gson.JsonParser

/**
 * Khoá [KEY] = `gma_load_app_context` (boolean, mặc định `false`) trong JSON Remote Config `ads_policy_android`
 * ([AdsPolicy.REMOTE_KEY]): `true` thì sáu chỗ nạp GMA toàn màn hình/native (`AdsMobPlInters`, `AdsMobPlOpen`,
 * `AdsMobPlrewarded`, `AdsMobPlNt`, `AdsMobPlNtCl`, `AdsMobPlNtFull`) truyền `applicationContext` thay cho Activity.
 * Hiện quảng cáo vẫn bằng Activity; banner/rect không đổi (view gắn vào Activity).
 *
 * Lý do: G9 (DOC/65), GMA giữ Activity đã truyền lúc nạp (WebView, registry OMID), nên Activity cũ tích lại qua mỗi
 * lần xoay (`.superpowers/sdd/g9-heap-analysis.md`). Rủi ro: vài adapter mediation cần Activity lúc nạp (Meta mở quảng
 * cáo từ context lúc nạp; Teads, Equativ không nạp được với app context), nên mặc định tắt: DOC/65 §7 #18 phương án
 * (b), bật thử theo nhóm và so fill/eCPM.
 *
 * Không thêm trường vào [AdsPolicy] (data class public): giá trị đọc riêng từ cùng chuỗi JSON, ở cùng hai chỗ áp
 * policy (`AdsHelper.applyRemotePolicy` khi Remote Config về, `AdsHelper.restorePolicy` lúc mở app với bản đã lưu
 * [AdsPolicy.PREF_KEY]), và giữ trong bộ nhớ ([enabled]); lúc nạp chỉ đọc biến đó, không đọc prefs.
 */
internal object GmaLoadContext {

    const val KEY = "gma_load_app_context"

    /** Giá trị đang áp; đọc tại mỗi lần nạp, nên Remote Config đổi giữa phiên thì lần nạp sau theo giá trị mới. */
    @Volatile
    var enabled: Boolean = false
        private set

    /**
     * Chỉ JSON boolean `true` là bật. Thiếu khoá, `false`, sai kiểu (kể cả chuỗi `"true"`), JSON hỏng, không phải
     * object, hay `schema_version` khác [AdsPolicy.SCHEMA_VERSION] (cả policy về mặc định) → `false` = hành vi cũ.
     * Sai kiểu thì logE; JSON hỏng và schema lạ đã được [AdsPolicy.parse] logE trên cùng chuỗi.
     */
    fun parse(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        val o = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return false
        }
        val schema = o.get("schema_version")
        if (schema != null && !AdsPolicy.isSupportedSchema(schema)) return false
        val e = o.get(KEY) ?: return false
        if (e.isJsonPrimitive && e.asJsonPrimitive.isBoolean) return e.asBoolean
        LogUtil.logE("ads policy: '$KEY' = $e không phải true/false, dùng false (nạp GMA bằng Activity)")
        return false
    }

    /** Áp chuỗi thô của `ads_policy_android` (cùng chuỗi vừa đưa cho [AdsPolicy.parse]). */
    fun apply(raw: String?) {
        enabled = parse(raw)
        LogUtil.logD("ads policy $KEY=$enabled")
    }

    /**
     * Context truyền cho GMA khi nạp: [activity] khi cờ tắt; `applicationContext` của [context] khi cờ bật (một dòng
     * logD mỗi lần). Người gọi vẫn giữ cổng `mActivity == null → "activity null"` trước lời gọi này.
     */
    fun forLoad(activity: Activity, context: Context): Context {
        if (!enabled) return activity
        // applicationContext null chỉ có ở context giả/chưa gắn: giữ hành vi cũ thay vì truyền null cho GMA.
        val app: Context = context.applicationContext ?: return activity
        LogUtil.logD("ads gma load context=application")
        return app
    }
}
