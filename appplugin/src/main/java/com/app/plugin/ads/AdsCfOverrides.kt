package com.app.plugin.ads

import android.util.Log
import com.app.plugin.until.LogUtil
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.util.Collections

/**
 * Khoá [KEY] = `ads_cf_overrides` (object `{"<tên>": <số nguyên>}`) trong JSON Remote Config `ads_policy_android`
 * ([AdsPolicy.REMOTE_KEY]) đè `@integer/<tên>` của app khi đọc qua `AppUtil.getResourceInt` (2.5.0 RCSW). Như
 * [GmaLoadContext]: không thêm trường vào [AdsPolicy] public; áp ở cùng hai chỗ (`applyRemotePolicy` khi RC về,
 * `restorePolicy` lúc mở app với bản đã lưu), giữ trong RAM, lúc đọc không I/O.
 *
 * **Luật allowlist:** chỉ nhận tên mà **mọi giá trị hợp lệ** hoặc là lùi về hành vi bản cũ (công tắc 0/1: 0 = mặc định
 * của bản này, 1 = bản trước), hoặc là enum bị chặn khoảng; RC không bật được hành vi chưa có trong SDK đã cài, và
 * không đặt được số tự do (thời gian chờ, ngưỡng). Hiện 4 tên ([ALLOWED]): `ads_cf_unit_gate_off` 0–1 (1 = không cổng
 * unit, 2.4.3), `ads_cf_native_media_ratio` 0–3 (enum tỉ lệ media), `ads_cf_unit_gate_prio_off` 0–1 (1 = FIFO 2.4.4),
 * `ads_cf_open_gap_own_only` 0–1 (1 = khoảng cách open ad chỉ tính open ad, 2.4.4). Thêm tên ⇒ kiểm theo luật này.
 *
 * Khoá RC **có mặt** thắng resource của app (kể cả 0); bỏ khoá khỏi RC để về resource. Tên ngoài allowlist, kiểu sai
 * (không phải số nguyên), ngoài khoảng ⇒ bỏ tên đó + logE; `ads_cf_overrides` không phải object ⇒ rỗng + logE; JSON hỏng,
 * không phải object, `schema_version` lạ ⇒ rỗng ([AdsPolicy.parse] đã logE). Khoá chỉ đọc ở top-level: nằm trong một
 * tier của `by_tier` thì `AdsPolicyTiers` bỏ cả khối tier kèm logE.
 */
internal object AdsCfOverrides {

    const val KEY = "ads_cf_overrides"

    private val ALLOWED: Map<String, IntRange> = mapOf(
        "ads_cf_unit_gate_off" to 0..1,
        "ads_cf_native_media_ratio" to 0..3,
        "ads_cf_unit_gate_prio_off" to 0..1,
        "ads_cf_open_gap_own_only" to 0..1,
    )

    /** Map bất biến; [apply] thay cả map (RC callback chạy ngoài main, đọc từ mọi luồng). */
    @Volatile
    private var values: Map<String, Int> = emptyMap()

    /** Áp chuỗi thô của `ads_policy_android` (cùng chuỗi vừa đưa cho [AdsPolicy.parse]); một dòng `Log.i` khi khác rỗng. */
    fun apply(raw: String?) {
        val parsed = parse(raw).also { values = it }
        if (parsed.isNotEmpty()) {
            Log.i(LogUtil.TAG, "ads cf overrides: " + parsed.entries.joinToString(", ") { "${it.key}=${it.value}" })
        }
    }

    /** Giá trị RC của [name] nếu khoá có mặt và hợp lệ, không thì `null` (người gọi đọc resource). */
    fun valueOf(name: String): Int? = values[name]

    fun resetForTest() { values = emptyMap() }

    private fun parse(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val o = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyMap()
        val schema = o.get("schema_version")
        if (schema != null && !AdsPolicy.isSupportedSchema(schema)) return emptyMap()
        val e = o.get(KEY) ?: return emptyMap()
        if (!e.isJsonObject) {
            LogUtil.logE("ads cf overrides: '$KEY' = $e không phải object, bỏ cả khoá")
            return emptyMap()
        }
        val out = LinkedHashMap<String, Int>()
        for ((name, v) in e.asJsonObject.entrySet()) {
            accept(name, v)?.let { out[name] = it }
        }
        return Collections.unmodifiableMap(out)
    }

    private fun accept(name: String, v: JsonElement): Int? {
        val range = ALLOWED[name]
        if (range == null) {
            LogUtil.logE("ads cf overrides: '$name' ngoài allowlist (4 tên), bỏ")
            return null
        }
        val n = wholeNumber(v)
        if (n == null || n !in range.first.toLong()..range.last.toLong()) {
            LogUtil.logE("ads cf overrides: '$name' = $v không phải số nguyên trong [${range.first}, ${range.last}], bỏ")
            return null
        }
        return n.toInt()
    }

    /** Số nguyên (JSON number hay chuỗi số, `1.0` được, `1.5`/boolean/object thì không), như `AdsPolicy`. */
    private fun wholeNumber(e: JsonElement): Long? =
        if (!e.isJsonPrimitive || e.asJsonPrimitive.isBoolean) null
        else runCatching { BigDecimal(e.asString.trim()).longValueExact() }.getOrNull()
}
