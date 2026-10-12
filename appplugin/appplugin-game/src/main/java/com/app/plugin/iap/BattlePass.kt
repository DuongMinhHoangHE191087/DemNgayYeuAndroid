package com.app.plugin.iap

import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.PreferenceHelper

/**
 * Battle pass / season pass engine — progression, hai track phần thưởng, catch-up
 * (doc 23 mục C.3 "season/battle-pass như subscription có thời hạn", doc 19 góc nhìn gaming).
 *
 * ═══ VÌ SAO LỚP NÀY TỒN TẠI ═══
 *
 * Battle pass xuất hiện trong ~60% game top-grossing và chiếm 1–40% doanh thu của chúng
 * (doc 19), nhưng các SDK subscription chuẩn (RevenueCat/Adapty) chỉ hiểu
 * "entitlement = access" — không hiểu "entitlement = mở track phần thưởng cao cấp của MÙA
 * NÀY". Mỗi studio vì thế phải tự ghép: cấu hình mùa, ngưỡng XP, phát phần thưởng, chống
 * claim trùng, cơ chế catch-up cho người vào muộn. Lớp này làm phần cơ chế đó một lần.
 *
 * ═══ BA QUYẾT ĐỊNH ═══
 *
 * 1. **Premium là non-renewing IAP theo mùa** (doc 19: battle pass KHÔNG phải auto-renewable
 *    subscription). `premium_product_id` nằm trong cấu hình mùa — mua sản phẩm đó cấp
 *    entitlement qua [Entitlements], và mỗi mùa dùng một product id khác nên entitlement mùa
 *    cũ tự động không mở track premium mùa mới. Không cần code xoá quyền theo mùa.
 * 2. **Game sở hữu XP, SDK sở hữu luật.** Game gọi `addXp(n)` mỗi khi người chơi đáng nhận
 *    XP (thắng trận, làm mission); SDK giữ tổng, đổi tổng sang tier theo ngưỡng cấu hình,
 *    phát event khi vượt tier, và thi hành catch-up (nhân bội số XP khi mùa đã trôi qua một
 *    phần cấu hình — cơ chế chuẩn cho người mua pass muộn).
 * 3. **Claim là dữ liệu, không phải vật phẩm.** `claim(tier, track)` kiểm tra luật (mùa đang
 *    chạy, tier đã đạt, chưa claim, premium cần entitlement) rồi trả danh sách reward + ghi
 *    nhận đã claim. **Trao vật phẩm là việc của game** — cùng ranh giới "SDK không vẽ hộ"
 *    của paywall và dunning.
 *
 * ═══ GIỚI HẠN PHẢI NÓI RÕ ═══
 *
 * - Toàn bộ tiến độ sống trên máy (prefs). Xoá app là mất XP và lịch sử claim; nguồn sự thật
 *   đúng là phía server theo user_id — chưa có backend.
 * - Vì claim chống trùng bằng trạng thái cục bộ, một máy đã root có thể sửa prefs để claim
 *   lại. Phần thưởng battle pass là tài nguyên game (không quy đổi tiền thật) nên rủi ro
 *   cùng hạng với `removeAds` trong [Entitlements] — chấp nhận được cho tới khi có backend.
 * - SDK không quyết ĐỊNH LƯỢNG XP của game (bao nhiêu XP mỗi trận) — đó là thiết kế game;
 *   cấu hình chỉ đặt NGƯỠNG tier, bội số catch-up và trần XP.
 */
public object BattlePass {

    private const val KEY_STATE = "battle_pass_state"
    private const val SEP = "|"

    /** Đồng hồ — tách ra để test điều khiển được thời gian mùa. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    // ═══ Kiểu dữ liệu ═══

    /** Một phần thưởng cấu hình: định danh + loại do game quy ước (gem, coin, skin...). */
    public data class Reward(val id: String, val type: String, val amount: Long)

    /** Một bậc của pass: ngưỡng XP tích luỹ và phần thưởng hai track. */
    public data class Tier(
        val tier: Int,
        val xpRequired: Long,
        val free: List<Reward>,
        val premium: List<Reward>
    )

    public data class SeasonConfig(
        val version: String,
        val seasonId: String,
        val startMs: Long,
        val endMs: Long,
        val premiumProductId: String?,
        val tiers: List<Tier>,
        val xpCap: Long,
        val catchUpEnabled: Boolean,
        val catchUpStartFraction: Double,
        val catchUpMultiplier: Double
    )

    public data class ApplyResult(val applied: Boolean, val report: String)
    public data class ClaimResult(val claimed: Boolean, val rewards: List<Reward>, val report: String)

    public enum class SeasonPhase { NOT_STARTED, ACTIVE, ENDED, NO_SEASON }
    public enum class Track { FREE, PREMIUM }

    // ═══ Trạng thái cấu hình ═══

    @Volatile private var config: SeasonConfig? = null

    // ═══ Trạng thái tiến độ (đệm trong process + persist) ═══

    @Volatile private var seasonId: String = ""
    @Volatile private var xp: Long = 0
    @Volatile private var claimed: MutableSet<String> = mutableSetOf() // "tier:track"
    @Volatile private var lastTier: Int = 0

    init {
        loadState()
    }

    // ═══ Cấu hình từ xa ═══

    /**
     * Nạp cấu hình khoá `battle_pass_android`. JSON hỏng → GIỮ NGUYÊN bản đang chạy
     * (nguyên tắc nhà). `season_id` mới khác mùa đang chạy thì tiến độ đặt lại từ đầu —
     * mùa mới là cuộc đua mới.
     *
     * ```json
     * {
     *   "version": "bp_v1",
     *   "season_id": "season_3",
     *   "start_ts_ms": 1725148800000,
     *   "end_ts_ms": 1727740800000,
     *   "premium_product_id": "bp_season_3_premium",
     *   "xp_cap": 100000,
     *   "tiers": [
     *     {"tier": 1, "xp_required": 0,
     *      "rewards_free":    [{"id": "r1", "type": "gem", "amount": 10}],
     *      "rewards_premium": [{"id": "r2", "type": "skin", "amount": 1}]},
     *     {"tier": 2, "xp_required": 100, "rewards_free": [], "rewards_premium": []}
     *   ],
     *   "catch_up": {"enabled": true, "start_fraction": 0.5, "multiplier": 1.5}
     * }
     * ```
     */
    public fun apply(json: String?): ApplyResult {
        if (json.isNullOrBlank()) return reject("cấu hình rỗng")
        return try {
            val root = com.google.gson.Gson().fromJson(json, Map::class.java) ?: return reject("JSON null")
            val ver = root["version"] as? String
            if (ver.isNullOrBlank()) return reject("thiếu trường version")
            val sid = root["season_id"] as? String
            if (sid.isNullOrBlank()) return reject("thiếu trường season_id")
            val start = (root["start_ts_ms"] as? Number)?.toLong() ?: return reject("thiếu start_ts_ms")
            val end = (root["end_ts_ms"] as? Number)?.toLong() ?: return reject("thiếu end_ts_ms")
            if (end <= start) return reject("end_ts_ms phải sau start_ts_ms")

            val rawTiers = root["tiers"] as? List<*> ?: return reject("thiếu tiers")
            if (rawTiers.isEmpty()) return reject("tiers rỗng")
            val tiers = mutableListOf<Tier>()
            for (raw in rawTiers) {
                val t = raw as? Map<*, *> ?: return reject("tier không phải object")
                val n = (t["tier"] as? Number)?.toInt() ?: return reject("tier thiếu số bậc")
                val req = (t["xp_required"] as? Number)?.toLong() ?: return reject("tier $n thiếu xp_required")
                if (req < 0) return reject("tier $n có xp_required âm")
                tiers.add(Tier(n, req, parseRewards(t["rewards_free"], n, "free"), parseRewards(t["rewards_premium"], n, "premium")))
            }
            // Không sort lại: battle pass có thứ tự bậc Ý NGHĨA (claim, hiển thị theo bậc).
            // Cấu hình phải tăng dần cả số bậc lẫn ngưỡng XP — xếp sai là lỗi người cấu
            // hình, im lặng sửa hộ sẽ giấu mất lỗi đó.
            for (i in 1 until tiers.size) {
                if (tiers[i].tier <= tiers[i - 1].tier) return reject("số bậc phải tăng dần")
                if (tiers[i].xpRequired <= tiers[i - 1].xpRequired)
                    return reject("xp_required phải tăng dần")
            }

            val premiumPid = (root["premium_product_id"] as? String)?.takeIf { it.isNotBlank() }
            val xpCap = (root["xp_cap"] as? Number)?.toLong() ?: Long.MAX_VALUE
            if (xpCap <= 0) return reject("xp_cap phải dương")

            var cuEnabled = false
            var cuFraction = 0.5
            var cuMult = 1.0
            (root["catch_up"] as? Map<*, *>)?.let { cu ->
                cuEnabled = cu["enabled"] == true
                (cu["start_fraction"] as? Number)?.toDouble()?.let { cuFraction = it }
                (cu["multiplier"] as? Number)?.toDouble()?.let { cuMult = it }
            }
            if (cuFraction <= 0.0 || cuFraction >= 1.0) return reject("catch_up.start_fraction phải trong (0,1)")
            if (cuMult < 1.0) return reject("catch_up.multiplier phải >= 1")

            val newConfig = SeasonConfig(ver, sid, start, end, premiumPid, tiers, xpCap, cuEnabled, cuFraction, cuMult)
            config = newConfig
            if (seasonId != sid) resetProgress(sid)
            ApplyResult(
                true,
                "áp $ver: mùa $sid, ${tiers.size} bậc, premium=${premiumPid ?: "không có"}, " +
                    "catch_up=${if (cuEnabled) "x$cuMult sau ${cuFraction}" else "tắt"}"
            )
        } catch (e: Exception) {
            reject("JSON hỏng: ${e.message}")
        }
    }

    private fun parseRewards(raw: Any?, tier: Int, trackName: String): List<Reward> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: throw IllegalArgumentException("rewards_$trackName bậc $tier không phải danh sách")
        return list.mapIndexed { i, item ->
            val m = item as? Map<*, *> ?: throw IllegalArgumentException("reward #$i bậc $tier không phải object")
            val id = m["id"] as? String
            if (id.isNullOrBlank()) throw IllegalArgumentException("reward #$i bậc $tier thiếu id")
            val type = m["type"] as? String ?: ""
            val amount = (m["amount"] as? Number)?.toLong() ?: 0L
            if (amount < 0) throw IllegalArgumentException("reward $id bậc $tier có amount âm")
            Reward(id, type, amount)
        }
    }

    private fun reject(reason: String): ApplyResult {
        val cur = config
        return ApplyResult(false, "từ chối ($reason) — giữ nguyên bản ${cur?.version ?: "chưa nạp"}")
    }

    public fun activeVersion(): String = config?.version ?: "chưa nạp"

    // ═══ Tiến độ ═══

    /**
     * Cộng XP. Trả số XP thực nhận (sau nhân catch-up và trần). Không có mùa đang chạy
     * (`NO_SEASON`/chưa nạp cấu hình) hoặc mùa chưa bắt đầu/đã kết thúc thì trả 0 — XP
     * ngoài mùa không có nghĩa.
     *
     * Catch-up: khi mùa đã trôi qua `start_fraction` thời lượng, mỗi điểm XP được nhân
     * `multiplier` — người mua pass giữa mùa đuổi kịp được. Đây là cơ chế chuẩn doc 23 C.3.
     */
    @Synchronized
    public fun addXp(amount: Long, ts: Long = clock()): Long {
        val cfg = config ?: return 0
        if (amount <= 0) return 0
        if (phaseAt(ts, cfg) != SeasonPhase.ACTIVE) return 0
        val mult = if (cfg.catchUpEnabled && catchUpActive(ts, cfg)) cfg.catchUpMultiplier else 1.0
        val gained = Math.min((amount * mult).toLong(), cfg.xpCap - xp).coerceAtLeast(0)
        if (gained == 0L) return 0
        val fromTier = currentTierInternal(cfg)
        xp += gained
        persist()
        val toTier = currentTierInternal(cfg)
        if (toTier > fromTier) {
            lastTier = toTier
            emitTierEvent(cfg, fromTier, toTier)
        }
        return gained
    }

    public fun xpTotal(): Long = xp

    /** Bậc cao nhất đã đạt theo tổng XP (bậc có xp_required ≤ xp). */
    public fun currentTier(): Int = config?.let { currentTierInternal(it) } ?: 0

    private fun currentTierInternal(cfg: SeasonConfig): Int {
        var out = 0
        for (t in cfg.tiers) if (xp >= t.xpRequired && t.tier > out) out = t.tier
        return out
    }

    // ═══ Premium ═══

    /**
     * Track premium mở khi entitlement của `premium_product_id` mùa này đang active —
     * mua non-renewing IAP qua IapHelper/PurchaseRouter là đủ, không cần nối thêm gì.
     */
    public fun isPremiumActive(): Boolean {
        val pid = config?.premiumProductId ?: return false
        return Entitlements.isActive(pid)
    }

    // ═══ Claim ═══

    /**
     * Claim phần thưởng một bậc. Trả danh sách reward khi thành công; game tự trao vật phẩm.
     * Luật: mùa đang ACTIVE, bậc đã đạt, chưa claim lần nào, track PREMIUM cần entitlement.
     */
    @Synchronized
    public fun claim(tier: Int, track: Track, ts: Long = clock()): ClaimResult {
        val cfg = config ?: return ClaimResult(false, emptyList(), "chưa nạp cấu hình")
        if (phaseAt(ts, cfg) != SeasonPhase.ACTIVE)
            return ClaimResult(false, emptyList(), "mùa ${cfg.seasonId} không đang chạy")
        val tierCfg = cfg.tiers.firstOrNull { it.tier == tier }
            ?: return ClaimResult(false, emptyList(), "không có bậc $tier")
        if (xp < tierCfg.xpRequired)
            return ClaimResult(false, emptyList(), "chưa đạt bậc $tier")
        if (track == Track.PREMIUM && !isPremiumActive())
            return ClaimResult(false, emptyList(), "chưa sở hữu premium mùa này")
        val key = "$tier:${track.name}"
        if (key in claimed)
            return ClaimResult(false, emptyList(), "bậc $tier track ${track.name} đã claim")
        val rewards = if (track == Track.FREE) tierCfg.free else tierCfg.premium
        claimed.add(key)
        persist()
        try {
            LogEventServer.logBattlePassClaim(
                seasonId = cfg.seasonId, tier = tier, track = track.name,
                rewardCount = rewards.size, isPremium = isPremiumActive(), version = cfg.version
            )
        } catch (e: Exception) {
            // LogEventServer chưa init — không để hỏng luồng chơi.
        }
        return ClaimResult(true, rewards, "đã claim bậc $tier track ${track.name}")
    }

    /** Các cặp (bậc, track) còn nhận được phần thưởng lúc này — để app vẽ chấm báo. */
    public fun claimable(ts: Long = clock()): List<Pair<Int, Track>> {
        val cfg = config ?: return emptyList()
        if (phaseAt(ts, cfg) != SeasonPhase.ACTIVE) return emptyList()
        val out = mutableListOf<Pair<Int, Track>>()
        val premium = isPremiumActive()
        for (t in cfg.tiers) {
            if (xp < t.xpRequired) continue
            if ("${t.tier}:FREE" !in claimed && t.free.isNotEmpty()) out.add(t.tier to Track.FREE)
            if (premium && "${t.tier}:PREMIUM" !in claimed && t.premium.isNotEmpty())
                out.add(t.tier to Track.PREMIUM)
        }
        return out
    }

    // ═══ Thời gian mùa ═══

    public fun phase(ts: Long = clock()): SeasonPhase = config?.let { phaseAt(ts, it) } ?: SeasonPhase.NO_SEASON

    private fun phaseAt(ts: Long, cfg: SeasonConfig): SeasonPhase = when {
        ts < cfg.startMs -> SeasonPhase.NOT_STARTED
        ts <= cfg.endMs -> SeasonPhase.ACTIVE
        else -> SeasonPhase.ENDED
    }

    private fun catchUpActive(ts: Long, cfg: SeasonConfig): Boolean {
        val span = cfg.endMs - cfg.startMs
        return ts >= cfg.startMs + (span * cfg.catchUpStartFraction).toLong()
    }

    // ═══ Event ═══

    private fun emitTierEvent(cfg: SeasonConfig, fromTier: Int, toTier: Int) {
        try {
            LogEventServer.logBattlePassTier(
                seasonId = cfg.seasonId, fromTier = fromTier, toTier = toTier,
                isPremium = isPremiumActive(), version = cfg.version
            )
        } catch (e: Exception) {
            // LogEventServer chưa init — bỏ qua.
        }
    }

    // ═══ Persist ═══

    private fun persist() {
        PreferenceHelper.getInstance().setValue(
            KEY_STATE,
            listOfNotNull(seasonId, xp.toString(), lastTier.toString(), claimed.joinToString(","))
                .joinToString(SEP)
        )
    }

    private fun loadState() {
        val parts = PreferenceHelper.getInstance().get(KEY_STATE, "").split(SEP)
        if (parts.size < 2) return
        seasonId = parts[0]
        xp = parts[1].toLongOrNull() ?: 0
        lastTier = parts.getOrNull(2)?.toIntOrNull() ?: 0
        claimed = parts.getOrNull(3)?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
    }

    /** Mùa đổi thì tiến độ về 0 — cuộc đua mới (và entitlement mùa cũ không còn giá trị). */
    @Synchronized
    private fun resetProgress(newSeasonId: String) {
        seasonId = newSeasonId
        xp = 0
        lastTier = 0
        claimed = mutableSetOf()
        persist()
    }

    // ═══ Reset (test) ═══

    @Synchronized
    public fun clear() {
        config = null
        seasonId = ""
        xp = 0
        lastTier = 0
        claimed = mutableSetOf()
        clock = { System.currentTimeMillis() }
        PreferenceHelper.getInstance().remove(KEY_STATE)
    }
}
