package com.app.plugin.iap

import com.app.plugin.firbase.GameHost
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.PreferenceHelper

/**
 * Cầu nối VIP subscription ↔ virtual currency — phát định kỳ cho người sở hữu
 * (doc 19: "entitlement = 500 gems/ngày" là thứ subscription SDK chuẩn không hiểu;
 * doc 23 mục C.3 "game-aware layer").
 *
 * ═══ VÌ SAO LỚP NÀY TỒN TẠI ═══
 *
 * RevenueCat/Adapty chỉ hiểu "entitlement = access" (mở tính năng, bỏ quảng cáo). Game thì
 * entitlement còn là **dòng vật phẩm định kỳ**: sở hữu VIP được 500 gem mỗi ngày, vé quay
 * mỗi tuần. Không SDK subscription nào làm phần này — mỗi game tự chế, và chế lệch: quên
 * phát khi người chơi không mở app đúng ngày, phát trùng khi mở hai lần, mất lịch sử khi
 * xoá app. Lớp này làm đúng ba việc đó một lần: biết AI sở hữu, BAO GIỜ đến lượt, và
 * chống phát trùng.
 *
 * ═══ BA QUYẾT ĐỊNH ═══
 *
 * 1. **Không truy lĩnh (no stacking).** Lỡ ba ngày không mở app thì chỉ nhận MỘT chu kỳ khi
 *    quay lại, không phải ba. Đây là lựa chọn thiết kế chuẩn của đa số game top-grossing
 *    (truy lĩnh tạo lạm phát tài nguyên và phá cân bằng kinh tế) — ghi rõ để studio biết
 *    mà cân nhắc, không phải sơ suất.
 * 2. **Chu kỳ tính từ lần claim trước**, không theo ngày dương lịch. Người nhận lúc 23h59
 *    rồi nhận lại lúc 00h01 sẽ không bị thiệt; chu kỳ 24h là 24h thật. Mốc cấu hình được
 *    (`period_hours`) cho vé tuần (168h) hay sự kiện 4h.
 * 3. **Sở hữu = Entitlements HOẶC SubscriptionLifecycle ACTIVE.** Hai nguồn vì hai loại sản
 *    phẩm: IAP thường (gói VIP vĩnh viễn) sống trong [Entitlements]; subscription được xác
 *    nhận qua trạng thái ACTIVE của [SubscriptionLifecycle] — kể cả khi Entitlements chưa
 *    kịp đồng bộ. Cả hai đều tắt là hết quyền nhận.
 *
 * ═══ RANH GIỚI ═══
 *
 * SDK trả lời "đã đến lượt nhận chưa, nhận gì" — **trao vật phẩm là việc của game**
 * (cùng ranh giới battle pass và dunning). SDK không quyết định số lượng vật phẩm — đó là
 * cân bằng kinh tế của studio, cấu hình chỉ mang nó đi.
 *
 * ═══ GIỚI HẠN PHẢI NÓI RÕ ═══
 *
 * Lịch sử claim sống trên máy (prefs): xoá app là mất và có thể nhận lại chu kỳ hiện tại
 * sau khi cài lại — cùng hạng rủi ro với battle pass claim, chấp nhận được cho tới khi có
 * backend xác thực theo user_id. Không đồng bộ đa thiết bị.
 */
public object RecurringGrants {

    private const val KEY_LAST = "recurring_grants_last"
    private const val SEP = "|"

    /** Đồng hồ tách riêng để test. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /** Một dòng phát định kỳ cấu hình cho một sản phẩm sở hữu. */
    public data class Grant(
        val productId: String,
        val periodHours: Long,
        val rewardType: String,
        val rewardAmount: Long
    )

    public data class ApplyResult(val applied: Boolean, val report: String)
    public data class ClaimResult(val claimed: Boolean, val rewardType: String, val rewardAmount: Long, val report: String)

    /** Dòng phát đến lượt nhận — để app vẽ nút "nhận quà VIP". */
    public data class Due(val grant: Grant)

    // ═══ Trạng thái ═══

    @Volatile private var version: String = "chưa nạp"
    @Volatile private var enabled: Boolean = true
    @Volatile private var grants: List<Grant> = emptyList()
    @Volatile private var lastClaim: MutableMap<String, Long> = loadLast()

    // ═══ Cấu hình từ xa ═══

    /**
     * Nạp cấu hình khoá `recurring_grants_android`. JSON hỏng → giữ nguyên bản đang chạy.
     *
     * ```json
     * {
     *   "version": "rg_v1",
     *   "enabled": true,
     *   "grants": [
     *     {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500},
     *     {"product_id": "vip_pass", "period_hours": 168, "reward_type": "ticket", "reward_amount": 1}
     *   ]
     * }
     * ```
     *
     * Một sản phẩm có thể mang nhiều dòng phát (gem ngày + vé tuần). Dòng trùng hệt
     * (cùng sản phẩm + chu kỳ + loại) bị loại bỏ để không phát đúp.
     */
    public fun apply(json: String?): ApplyResult {
        if (json.isNullOrBlank()) return reject("cấu hình rỗng")
        return try {
            val root = com.google.gson.Gson().fromJson(json, Map::class.java) ?: return reject("JSON null")
            val ver = root["version"] as? String
            if (ver.isNullOrBlank()) return reject("thiếu trường version")

            var newEnabled = true
            (root["enabled"] as? Boolean)?.let { newEnabled = it }

            val list = mutableListOf<Grant>()
            (root["grants"] as? List<*>)?.forEach { raw ->
                val m = raw as? Map<*, *> ?: return reject("grant không phải object")
                val pid = m["product_id"] as? String
                if (pid.isNullOrBlank()) return reject("grant thiếu product_id")
                val hours = (m["period_hours"] as? Number)?.toLong() ?: return reject("grant $pid thiếu period_hours")
                if (hours <= 0) return reject("grant $pid có period_hours <= 0")
                val type = m["reward_type"] as? String
                if (type.isNullOrBlank()) return reject("grant $pid thiếu reward_type")
                val amount = (m["reward_amount"] as? Number)?.toLong() ?: return reject("grant $pid thiếu reward_amount")
                if (amount <= 0) return reject("grant $pid có reward_amount <= 0")
                list.add(Grant(pid, hours, type, amount))
            }
            val deduped = list.distinctBy { Triple(it.productId, it.periodHours, it.rewardType) }

            version = ver
            enabled = newEnabled
            grants = deduped
            val dropped = list.size - deduped.size
            ApplyResult(
                true,
                "áp $ver: ${deduped.size} dòng phát, enabled=$newEnabled" +
                    if (dropped > 0) "; bỏ $dropped dòng trùng" else ""
            )
        } catch (e: Exception) {
            reject("JSON hỏng: ${e.message}")
        }
    }

    private fun reject(reason: String): ApplyResult =
        ApplyResult(false, "từ chối ($reason) — giữ nguyên bản $version")

    public fun activeVersion(): String = version

    // ═══ Đọc ═══

    private fun owned(productId: String): Boolean =
        Entitlements.isActive(productId) || GameHost.subscriptionState(productId) == SubState.ACTIVE // D28: không nhãn server

    /**
     * Các dòng phát ĐÃ ĐẾN LƯỢT cho người này lúc này (sở hữu + đủ chu kỳ). App gọi khi
     * vào màn hình chính để vẽ nút nhận.
     */
    public fun dueGrants(ts: Long = clock()): List<Due> {
        if (!enabled) return emptyList()
        val out = mutableListOf<Due>()
        for (g in grants) {
            if (!owned(g.productId)) continue
            val last = lastClaim[keyOf(g)] ?: 0L
            if (ts >= last + g.periodHours * 3_600_000L) out.add(Due(g))
        }
        return out
    }

    /**
     * Nhận một dòng phát. Trả vật phẩm kèm report; game tự trao. Chống trùng: cùng dòng
     * chỉ nhận một lần mỗi chu kỳ, kể cả gọi hai lần liên tiếp.
     */
    @Synchronized
    public fun claim(productId: String, rewardType: String, ts: Long = clock()): ClaimResult {
        if (!enabled) return ClaimResult(false, "", 0, "tính năng đang tắt")
        val g = grants.firstOrNull { it.productId == productId && it.rewardType == rewardType }
            ?: return ClaimResult(false, "", 0, "không có dòng phát $productId/$rewardType")
        if (!owned(productId)) return ClaimResult(false, "", 0, "không sở hữu $productId")
        val key = keyOf(g)
        val last = lastClaim[key] ?: 0L
        val periodMs = g.periodHours * 3_600_000L
        if (ts < last + periodMs) {
            val remainHours = (last + periodMs - ts + 3_599_999) / 3_600_000
            return ClaimResult(false, "", 0, "chưa đến lượt — còn ${remainHours}h")
        }
        lastClaim[key] = ts
        persist()
        try {
            LogEventServer.logRecurringGrantClaim(
                productId = productId, rewardType = g.rewardType, rewardAmount = g.rewardAmount,
                periodHours = g.periodHours, version = version
            )
        } catch (e: Exception) {
            // LogEventServer chưa init — bỏ qua.
        }
        return ClaimResult(true, g.rewardType, g.rewardAmount, "đã nhận ${g.rewardAmount} ${g.rewardType}")
    }

    private fun keyOf(g: Grant): String = "${g.productId}|${g.rewardType}|${g.periodHours}"

    // ═══ Persist ═══

    private fun persist() {
        PreferenceHelper.getInstance().setValue(
            KEY_LAST, lastClaim.entries.joinToString(SEP) { "${it.key}:${it.value}" }
        )
    }

    private fun loadLast(): MutableMap<String, Long> {
        val out = mutableMapOf<String, Long>()
        for (part in PreferenceHelper.getInstance().get(KEY_LAST, "").split(SEP)) {
            val i = part.lastIndexOf(':')
            if (i <= 0) continue
            val ts = part.substring(i + 1).toLongOrNull() ?: continue
            if (ts >= 0) out[part.substring(0, i)] = ts
        }
        return out
    }

    // ═══ Reset (test) ═══

    @Synchronized
    public fun clear() {
        version = "chưa nạp"
        enabled = true
        grants = emptyList()
        lastClaim = mutableMapOf()
        clock = { System.currentTimeMillis() }
        PreferenceHelper.getInstance().remove(KEY_LAST)
    }
}
