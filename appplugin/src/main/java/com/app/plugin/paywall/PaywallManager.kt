package com.app.plugin.paywall

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.DecisionPoint
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.iap.PaywallSession
import com.app.plugin.logevent.IapEventLog
import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.until.LogUtil
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Paywall cấu hình từ xa, có A/B và **holdout native**.
 *
 * ═══ SDK KHÔNG VẼ GIAO DIỆN ═══
 *
 * `paywallFor()` trả về *nên bán gì, theo thứ tự nào, với biến thể nào* — app tự vẽ. Cùng
 * ranh giới `AdsBrain` đã theo: brain trả lời "có nên", caller thực thi.
 *
 * Vì sao không vẽ hộ: giao diện là thứ đặc thù nhất của từng game. Một thư viện vẽ paywall sẽ
 * hoặc trông lạc lõng trong game, hoặc phải mang theo một hệ thống theming đủ lớn để trở
 * thành gánh nặng lớn hơn chính tính năng nó cung cấp. Đó là lý do game top-grossing tự build
 * paywall thay vì dùng vendor (doc 19).
 *
 * ═══ HOLDOUT NATIVE — KHOẢNG TRỐNG ĐÃ XÁC MINH CỦA THỊ TRƯỜNG ═══
 *
 * doc 19 mục 5: **không vendor paywall lớn nào** (RevenueCat, Adapty, Superwall, Apphud,
 * Qonversion) có holdout native. Họ đo được biến thể A so với B, nhưng không đo được *"có
 * paywall so với KHÔNG có paywall"* — tức không trả lời được câu hỏi về giá trị tuyệt đối.
 *
 * Ở đây nhóm holdout **không nhận paywall nào cả**, và số lần bỏ qua được đếm để phân tích
 * sau này biết mẫu số. Bỏ qua im lặng sẽ khiến nhóm đối chứng trông như không tồn tại.
 *
 * ═══ SCHEMA V2 (1.8.0 Task 9, plan "Hợp đồng D", D6/D14) ═══
 *
 * Cấu hình parse ở [PaywallConfigV2]. Holdout paywall là khối RIÊNG của cấu hình v2
 * (`holdout.fraction`, mặc định **0**; cấu hình v1 không có holdout): một lần rút trên
 * `user_id` của SDK (cột `user_id` của mọi event) trong không gian băm `paywall_holdout_v2`,
 * **độc lập** với holdout của brain (`brain_holdout_v1`) và không kế thừa tỉ lệ của brain —
 * hai thí nghiệm không được tương quan theo cấu trúc (D14). `session_cap` giới hạn số paywall
 * `proactive` (SDK chủ động mời) hiện ra trong một process; placement do người dùng mở không
 * bao giờ bị giới hạn. Mỗi nhánh `null` khác holdout phát `paywall_skip` kèm lý do.
 *
 * Thứ tự kiểm tra trong `paywallFor()` là CỐ Ý:
 *   1. cấu hình (chưa nạp / placement không khai báo → `null`, `paywall_skip no_config`)
 *   2. **nhóm đối chứng paywall kiểm tra TRƯỚC** — không tốn một quyết định bandit nào cho
 *      người không bao giờ thấy paywall (doc 49 mục E.2).
 *   3. `session_cap` của placement `proactive` (`paywall_skip session_cap`) — cũng trước brain,
 *      cùng lý do: không tốn quyết định cho lượt chắc chắn không hiện.
 *   4. brain gate (brain từ chối → `null`, `paywall_skip brain`)
 *   5. chọn biến thể
 */
public object PaywallManager {

    public data class Variant(
        val id: String,
        val weight: Int,
        val products: List<String>,
        val highlight: String?
    )

    /** Kết quả trả cho app. `products` đã theo đúng thứ tự của biến thể. */
    public data class Paywall(
        val placement: String,
        val variantId: String,
        val products: List<String>,
        /** Sản phẩm cần làm nổi bật, nếu biến thể có khai báo. */
        val highlight: String?,
        val version: String
    )

    public data class ApplyResult(val applied: Boolean, val report: String)

    /** Không gian băm RIÊNG cho A/B paywall — độc lập thống kê với holdout và với rollout brain. */
    private const val HASH_NAMESPACE = "paywall_variant"

    /** Không gian băm của holdout paywall (D14) — khác `brain_holdout_v1` của `HoldoutManager`. */
    private const val HOLDOUT_NAMESPACE = "paywall_holdout_v2"
    private const val HOLDOUT_BUCKETS = 10_000

    private val EMPTY = PaywallConfigV2.Parsed(
        PaywallConfigV2.SCHEMA_V1, "chưa nạp", 0.0, PaywallConfigV2.NO_CAP, emptyMap(), emptyMap(), emptySet(), emptyList()
    )

    /**
     * Cấu hình đang chạy — một tham chiếu, thay nguyên khối khi apply (không ai đọc được nửa cũ nửa mới).
     * `decisionPoints`: DecisionPoint brain dùng khi placement hỏi ý kiến, mặc định SHOP_OPEN — `CURRENCY_LOW`
     * cho paywall kiểu "hết gem, mua thêm?", vì hai bối cảnh khác nhau về đạo đức (xem `AdsBrain.shouldOfferIap`).
     */
    @Volatile private var config: PaywallConfigV2.Parsed = EMPTY
    @Volatile private var holdoutSkips: Int = 0

    /** Số paywall `proactive` đã hiện trong process này (đếm ở [onPaywallShown]). */
    private val proactiveShown = AtomicInteger(0)
    private val idMismatchLogged = AtomicBoolean(false)

    public fun activeVersion(): String = config.version

    /** Số lần một player thuộc nhóm đối chứng bị bỏ qua — mẫu số của phép so sánh sau này. */
    public fun holdoutSkipCount(): Int = holdoutSkips

    public fun clear() {
        config = EMPTY
        holdoutSkips = 0
        proactiveShown.set(0)
        idMismatchLogged.set(false)
        PaywallSession.close()
    }

    // ═══ Nạp cấu hình ═══

    /**
     * Parse + apply (v1 hoặc v2, [PaywallConfigV2]). Không bao giờ ném ra ngoài: cấu hình hỏng
     * hoặc `schema_version` lạ chỉ dẫn tới việc giữ nguyên cấu hình đang chạy — cùng nguyên tắc
     * với `BrainPolicy`.
     */
    public fun apply(json: String?): ApplyResult {
        val parsed = try {
            PaywallConfigV2.parse(json)
        } catch (e: Exception) {
            PaywallConfigV2.Reject("lỗi parse: ${e.message}")
        }
        if (parsed is PaywallConfigV2.Reject) return reject(parsed.reason)
        val cfg = parsed as PaywallConfigV2.Parsed
        config = cfg
        val report = buildString {
            append("đã nạp '${cfg.version}' (schema v${cfg.schemaVersion}), ${cfg.placements.size} placement")
            if (cfg.notes.isNotEmpty()) append("\n  ⚠ ${cfg.notes.joinToString("\n  ⚠ ")}")
        }
        LogUtil.logD("Paywall: $report")
        return ApplyResult(true, report)
    }

    private fun reject(reason: String): ApplyResult {
        // Cố ý KHÔNG xoá cấu hình đang chạy: bản mới hỏng thì giữ bản cũ đang chạy tốt.
        val msg = "TỪ CHỐI cấu hình paywall ($reason) — giữ nguyên '${config.version}'"
        LogUtil.logE(msg)
        return ApplyResult(false, msg)
    }

    // ═══ Quyết định ═══

    /**
     * @return paywall cho player này, hoặc `null` khi: chưa nạp cấu hình, placement không được
     *   khai báo, player **thuộc nhóm đối chứng paywall**, placement `proactive` đã chạm
     *   `session_cap`, hoặc **brain từ chối chào bán** lúc này (bế tắc, vận tốc mua cao, chạm
     *   trần phiên/ngày...).
     *
     *   `null` nghĩa là "SDK không có ý kiến, app dùng luồng cũ" — KHÔNG phải "hiện paywall
     *   rỗng". Trả về một paywall không sản phẩm sẽ khiến app vẽ một màn hình trống, tệ hơn
     *   hẳn việc không vẽ gì.
     *
     *   Holdout rút trên `user_id` của SDK, không trên [playerId] (id app truyền vào chỉ dùng
     *   chọn biến thể); hai id khác nhau → log một lần.
     *
     *   HỢP ĐỒNG: mỗi lời gọi là một quyết định bandit khi brain bật (có decision log, có
     *   guardrail). App gọi MỘT lần cho mỗi cơ hội chào bán — gọi hai lần cho cùng một màn
     *   hình là hai quyết định, làm lệch propensity trong decision log. Cũng vì vậy KHÔNG gọi
     *   cả `AdsBrain.shouldOfferIap` lẫn hàm này cho cùng một placement: đó là quyết định kép.
     */
    public fun paywallFor(placement: String, playerId: String): Paywall? {
        val cfg = config
        val variants = cfg.placements[placement] ?: return skip(placement, IapEventLog.SKIP_NO_CONFIG)

        if (inPaywallHoldout(cfg.holdoutFraction, playerId)) {
            // Đếm để phân tích sau này biết mẫu số của nhóm đối chứng. Gửi kèm biến thể LẼ RA đã
            // hiện: không có nó thì chỉ so được số gộp, không so được từng biến thể.
            holdoutSkips += 1
            try {
                IapEventLog.paywallHoldoutSkip(
                    placement = placement,
                    counterfactualVariantId = pick(variants, playerId)?.id ?: "unknown",
                    version = cfg.version,
                    paywallSessionId = UUID.randomUUID().toString()
                )
            } catch (e: Exception) {
                LogUtil.logE("Paywall holdout skip log failed: ${e.message}")
            }
            return null
        }

        if (placement in cfg.proactive && proactiveShown.get() >= cfg.sessionCap) {
            return skip(placement, IapEventLog.SKIP_SESSION_CAP)
        }

        // Hỏi brain TRƯỚC KHI chọn biến thể (thứ tự cố ý — xem KDoc lớp). Brain tắt hoặc
        // player ngoài rollout thì hàm này trả true không hiệu ứng phụ: hành vi cũ nguyên vẹn.
        if (!AdsBrain.shouldOfferIap(cfg.decisionPoints[placement] ?: DecisionPoint.SHOP_OPEN)) {
            return skip(placement, IapEventLog.SKIP_BRAIN)
        }

        val chosen = pick(variants, playerId) ?: return null
        return Paywall(placement, chosen.id, chosen.products, chosen.highlight, cfg.version)
    }

    /** Biến thể của player, kể cả khi họ thuộc nhóm đối chứng — dùng để phân tích, không để phục vụ. */
    public fun variantIdFor(placement: String, playerId: String): String? =
        config.placements[placement]?.let { pick(it, playerId)?.id }

    /**
     * Holdout paywall (D6/D14): `bucketWith("paywall_holdout_v2", user_id) < fraction·10 000`.
     * `fraction` 0 (mặc định, mọi cấu hình v1) → không rút, không đọc user_id.
     */
    private fun inPaywallHoldout(fraction: Double, playerId: String): Boolean {
        if (fraction <= 0.0) return false
        return HoldoutManager.bucketWith(HOLDOUT_NAMESPACE, sdkUserId(playerId)) < (fraction * HOLDOUT_BUCKETS).toInt()
    }

    /** `user_id` của SDK; chưa có (trống/lỗi) thì dùng [playerId] để không dồn mọi người vào một bucket. */
    private fun sdkUserId(playerId: String): String {
        val uid = runCatching { LogEventServerHelper.currentUserId() }.getOrNull()
        if (uid.isNullOrBlank()) return playerId
        if (uid != playerId && idMismatchLogged.compareAndSet(false, true)) {
            LogUtil.logD("Paywall: playerId khác user_id của SDK → holdout rút trên user_id")
        }
        return uid
    }

    private fun skip(placement: String, reason: String): Paywall? {
        try {
            IapEventLog.paywallSkip(placement, reason)
        } catch (e: Exception) {
            LogUtil.logE("Paywall skip log failed: ${e.message}")
        }
        return null
    }

    /**
     * Chọn biến thể theo trọng số, **tất định** theo `playerId`.
     *
     * Dùng không gian băm riêng (`HASH_NAMESPACE`) nên độc lập thống kê với holdout và với
     * rollout của brain. Dùng chung không gian sẽ khiến mọi người trong holdout rơi vào cùng
     * một biến thể — hai thí nghiệm tương quan hoàn toàn và không chạy song song được.
     */
    private fun pick(variants: List<Variant>, playerId: String): Variant? {
        val usable = variants.filter { it.weight > 0 }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { it.weight }
        if (total <= 0) return null
        val bucket = HoldoutManager.bucketWith(HASH_NAMESPACE, playerId) % total
        var acc = 0
        for (v in usable) {
            acc += v.weight
            if (bucket < acc) return v
        }
        return usable.last()
    }

    // ═══ Đo lường ═══

    /**
     * Báo paywall đã hiện ra màn hình. Cần cho tỉ lệ chuyển đổi — không có mẫu số thì con số
     * "bao nhiêu lượt mua" không nói lên điều gì.
     *
     * Mở phiên paywall (`PaywallSession`, `paywall_session_id` mới): `paywall_choice` và
     * `iap_purchase` của lượt mua kế tiếp mang cùng id đó. Placement `proactive` được đếm vào
     * `session_cap`.
     *
     * Đồng thời báo cho brain: một lượt chào bán IAP vừa hiện, bất kể ai quyết định hiện nó.
     * Nếu không, bộ đếm guardrail (trần phiên, cooldown, trần 24h) sẽ coi như lượt này chưa
     * xảy ra và cho phép chào bán tiếp ngay — paywall trở thành cửa sau lách qua chính bộ
     * bảo vệ vừa chặn nó ở `paywallFor`. Khi brain tắt lời gọi này là no-op.
     */
    public fun onPaywallShown(paywall: Paywall) {
        val cfg = config
        if (paywall.placement in cfg.proactive) proactiveShown.incrementAndGet()
        val sessionId = UUID.randomUUID().toString()
        PaywallSession.open(paywall.placement, paywall.variantId, sessionId)
        try {
            AdsBrain.onIapOfferShown()
        } catch (e: Exception) {
            LogUtil.logE("Paywall brain-notify failed: ${e.message}")
        }
        try {
            IapEventLog.paywallImpression(
                placement = paywall.placement,
                variantId = paywall.variantId,
                productCount = paywall.products.size,
                version = paywall.version,
                paywallSessionId = sessionId,
                schemaVersionCfg = cfg.schemaVersion
            )
        } catch (e: Exception) {
            LogUtil.logE("Paywall impression log failed: ${e.message}")
        }
    }
}
