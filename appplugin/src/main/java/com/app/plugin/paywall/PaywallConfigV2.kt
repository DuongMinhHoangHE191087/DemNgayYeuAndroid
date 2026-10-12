package com.app.plugin.paywall

import com.app.plugin.brain.DecisionPoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Parse `paywall_config_android` hình cố định (1.8.0 Task 9, plan "Hợp đồng D"):
 *
 * `{"schema_version":2,"version":"…","holdout":{"fraction":0.0},"session_cap":1,"placements":{"<pl>":{"decision_point":"…",
 * "proactive":true,"variants":[{"id","weight","products":[…],"highlight"}]}}}`
 *
 * - `schema_version` vắng = v1 (hình trước 1.8.0): áp với holdout **0**, không `session_cap`, không placement `proactive`
 *   (khối `holdout`/`session_cap`/`proactive` của v1 bị bỏ qua — D6: không ai bị giữ lại mà không khai).
 * - `schema_version` = 2: `holdout.fraction` ∈ [0, 1] (vắng = 0), `session_cap` số nguyên ≥ 0 (vắng = không giới hạn),
 *   `proactive` boolean theo placement (vắng = `false`: placement do người dùng mở không bao giờ bị giới hạn).
 * - `schema_version` khác → [Reject]; `PaywallManager` giữ cấu hình đang chạy và `logE`.
 *
 * Thuần (không I/O, không trạng thái): `PaywallManager.apply` gọi rồi thay cấu hình một lần.
 */
internal object PaywallConfigV2 {
    const val SCHEMA_V1 = 1
    const val SCHEMA_V2 = 2

    /** `session_cap` vắng: không giới hạn. */
    const val NO_CAP = Int.MAX_VALUE

    internal sealed interface Result

    internal data class Parsed(
        val schemaVersion: Int,
        val version: String,
        val holdoutFraction: Double,
        val sessionCap: Int,
        val placements: Map<String, List<PaywallManager.Variant>>,
        val decisionPoints: Map<String, DecisionPoint>,
        val proactive: Set<String>,
        val notes: List<String>
    ) : Result

    internal data class Reject(val reason: String) : Result

    @Suppress("UNCHECKED_CAST")
    fun parse(json: String?): Result {
        if (json.isNullOrBlank()) return Reject("cấu hình rỗng")
        val root: Map<String, Any> = try {
            Gson().fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
        } catch (e: Exception) {
            return Reject("lỗi parse: ${e.message}")
        } ?: return Reject("JSON không parse được thành object")

        val schema = when (val sv = root["schema_version"]) {
            null -> SCHEMA_V1
            is Number -> if (sv.toDouble() == SCHEMA_V2.toDouble()) SCHEMA_V2 else return Reject("schema_version $sv không hỗ trợ")
            else -> return Reject("schema_version '$sv' không hỗ trợ")
        }
        val ver = root["version"]?.toString() ?: return Reject("thiếu trường 'version'")
        val raw = root["placements"] as? Map<String, Any> ?: return Reject("thiếu 'placements'")
        val v2 = schema == SCHEMA_V2
        val fraction = if (v2) holdoutFraction(root["holdout"]) ?: return Reject("holdout.fraction phải là số trong [0, 1]") else 0.0
        val cap = if (v2) sessionCap(root["session_cap"]) ?: return Reject("session_cap phải là số nguyên ≥ 0") else NO_CAP

        val notes = mutableListOf<String>()
        val parsed = mutableMapOf<String, List<PaywallManager.Variant>>()
        val dpMap = mutableMapOf<String, DecisionPoint>()
        val proactive = mutableSetOf<String>()
        for ((placement, value) in raw) {
            val block = value as? Map<String, Any> ?: continue
            // decision_point sai chỉ cảnh báo và giữ SHOP_OPEN: lỗi chính tả trường tuỳ chọn không đáng mất cả paywall.
            val dpRaw = block["decision_point"]?.toString()
            if (!dpRaw.isNullOrBlank()) {
                val dp = runCatching { DecisionPoint.valueOf(dpRaw) }.getOrNull()
                if (dp != null) dpMap[placement] = dp
                else notes.add("$placement: decision_point '$dpRaw' không hợp lệ → dùng SHOP_OPEN")
            }
            if (v2 && block["proactive"] == true) proactive.add(placement)
            val variants = variants(placement, block["variants"] as? List<Any> ?: continue, notes)
            if (variants.any { it.weight > 0 }) parsed[placement] = variants
            else notes.add("$placement không còn biến thể hợp lệ nào → bỏ qua")
        }
        return Parsed(schema, ver, fraction, cap, parsed, dpMap, proactive.intersect(parsed.keys), notes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun variants(placement: String, list: List<Any>, notes: MutableList<String>): List<PaywallManager.Variant> {
        val out = mutableListOf<PaywallManager.Variant>()
        for (v in list) {
            val m = v as? Map<String, Any> ?: continue
            val id = m["id"]?.toString() ?: continue
            val products = (m["products"] as? List<Any>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) } ?: emptyList()
            if (products.isEmpty()) {
                // Biến thể rỗng khiến app vẽ màn hình trống: bỏ và nói rõ.
                notes.add("$placement/$id không có sản phẩm nào → bỏ qua")
                continue
            }
            val weight = (m["weight"]?.toString()?.toDoubleOrNull() ?: 0.0).toInt()
            out.add(PaywallManager.Variant(id, weight.coerceAtLeast(0), products, m["highlight"]?.toString()))
        }
        return out
    }

    /** `holdout` vắng hoặc `fraction` vắng → 0; `fraction` ngoài [0, 1] hoặc không phải số → `null` (Reject). */
    private fun holdoutFraction(block: Any?): Double? {
        if (block == null) return 0.0
        val m = block as? Map<*, *> ?: return null
        val f = m["fraction"] ?: return 0.0
        val d = (f as? Number)?.toDouble() ?: return null
        return d.takeIf { it in 0.0..1.0 }
    }

    /** `session_cap` vắng → [NO_CAP]; số nguyên ≥ 0 → giá trị đó; khác → `null` (Reject). */
    private fun sessionCap(raw: Any?): Int? {
        if (raw == null) return NO_CAP
        val d = (raw as? Number)?.toDouble() ?: return null
        if (d < 0 || d != Math.floor(d) || d > Int.MAX_VALUE) return null
        return d.toInt()
    }
}
