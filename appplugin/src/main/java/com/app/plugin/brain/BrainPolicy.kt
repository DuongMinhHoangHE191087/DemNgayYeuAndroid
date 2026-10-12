package com.app.plugin.brain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Policy artifact — cho phép chỉnh toàn bộ tham số brain từ xa mà KHÔNG cần release app.
 *
 * Đi theo pattern "policy artifact + local evaluation + server fallback" (doc 16 mục D.1,
 * tiền lệ production Adobe Target on-device decisioning), dùng lại đúng kênh vận chuyển đã có
 * trong repo: `FirHelperConfig` fetch Remote Config → chuỗi JSON → parse → apply.
 *
 * ═══ NGUYÊN TẮC AN TOÀN TRUNG TÂM ═══
 *
 * Artifact **chỉ được làm hệ thống AN TOÀN HƠN hoặc bằng**, không bao giờ hung hăng hơn trần
 * cứng biên dịch trong code. Mọi trường đều đi qua `clamp` theo đúng chiều rủi ro của nó.
 *
 * Đây là bài học rút trực tiếp từ teardown app đối thủ (doc 27 mục B.1): ở app đó, khi config
 * lỗi cú pháp thì hệ thống tự động bán gói ĐẮT NHẤT và tăng mật độ quảng cáo gấp 12 lần — im
 * lặng, không ai nhận ra. Nguyên nhân gốc: lớp fallback hung hăng hơn lớp cấu hình chính, và
 * không có ai kẹp giá trị.
 *
 * Ở đây điều đó không thể xảy ra: kể cả khi ai đó đẩy nhầm `max_ads_24h: 9999` lên Firebase,
 * giá trị thực thi vẫn bị kẹp về `HARD_MAX_ADS_24H`. Artifact hỏng → giữ nguyên policy cũ.
 */
public object BrainPolicy {

    // ═══ Trần cứng: artifact KHÔNG BAO GIỜ vượt được các giá trị này ═══
    // Đây là biên an toàn tuyệt đối của sản phẩm, chỉ đổi được bằng cách release app mới.
    public const val HARD_MAX_INTERSTITIAL_PER_SESSION: Int = 10
    public const val HARD_MAX_ADS_24H: Int = 60
    public const val HARD_MIN_COOLDOWN_MINUTES: Int = 1
    public const val HARD_MAX_CLICKS_PER_WINDOW: Int = 12

    // Trần cứng cho hành động chào bán. Cùng nguyên tắc: artifact chỉ được làm hệ thống an
    // toàn hơn. Đặc biệt `HARD_MAX_PURCHASES_BEFORE_BREAK` — đẩy nó lên cao là vô hiệu hoá
    // circuit breaker bảo vệ người chi dồn dập (doc 29 mục 5.3), thứ tuyệt đối không được
    // phép làm từ xa.
    public const val HARD_MAX_IAP_OFFERS_PER_SESSION: Int = 6
    public const val HARD_MIN_IAP_OFFER_COOLDOWN_MINUTES: Int = 3
    public const val HARD_MAX_PURCHASES_BEFORE_BREAK: Int = 5
    public const val HARD_MAX_FRUSTRATION_STREAK_THRESHOLD: Int = 5
    // Trần hỗ trợ gameplay: quà tặng miễn phí nhưng dồn dập vẫn gây hại — artifact không được
    // phép đẩy trần này lên mức biến "thỉnh thoảng trợ giúp" thành "máy phát quà".
    public const val HARD_MAX_ASSISTS_PER_SESSION: Int = 8

    public data class Profile(
        val failRate: Double,
        val rewardedAcceptRate: Double,
        val payerPropensity: Double,
        val churnTendency: Double
    )

    public data class Artifact(
        val version: String,
        val enabled: Boolean,
        val scenarioCaps: Map<PersonalizationScenario, Int>,
        val profiles: Map<LatentPlayerType, Profile>,
        val minConfidence: Double?
    )

    @Volatile
    private var current: Artifact? = null

    /** `experiment.rollout_fraction` của artifact đang chạy: thắng giá trị app đặt (`configure` chạy sau khôi phục, T5s). */
    @Volatile private var artifactRollout: Double? = null

    /**
     * Sàn khám phá do artifact yêu cầu. `BanditPolicy` nằm trong `DecisionEngine` nên
     * `BrainPolicy` không tự đặt được; engine đọc giá trị này khi áp policy.
     */
    @Volatile
    public var pendingExplorationFloor: Double? = null
        internal set

    /** Ghi lại vì sao artifact bị từ chối/bị kẹp — hiển thị trong debug view và log. */
    @Volatile
    public var lastApplyReport: String = "chưa nạp artifact nào — đang dùng giá trị mặc định trong code"
        private set

    public fun activeVersion(): String = current?.version ?: "built-in-default"

    /** Kill switch: artifact có thể tắt toàn bộ personalization, quay về policy trung tính. */
    public fun isEnabled(): Boolean = current?.enabled ?: true

    public fun scenarioCap(scenario: PersonalizationScenario): Int? = current?.scenarioCaps?.get(scenario)

    public fun profileFor(type: LatentPlayerType): Profile? = current?.profiles?.get(type)

    public fun minConfidence(): Double = current?.minConfidence ?: PersonalizationScenario.MIN_CONFIDENCE

    internal fun rolloutOverride(): Double? = artifactRollout

    // ═══ Nạp artifact ═══

    public data class ApplyResult(val applied: Boolean, val report: String)

    /**
     * Parse + validate + apply. KHÔNG BAO GIỜ ném exception ra ngoài: artifact hỏng chỉ dẫn
     * tới việc giữ nguyên policy đang chạy, không được làm gián đoạn phiên chơi của user.
     *
     * @param guardrails engine để áp ngưỡng guardrail; null nếu chỉ muốn parse.
     */
    public fun apply(json: String?, guardrails: GuardrailEngine? = null): ApplyResult {
        if (json.isNullOrBlank()) {
            return reject("artifact rỗng")
        }
        return try {
            val gson = Gson()
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val root: Map<String, Any> = gson.fromJson(json, mapType)
                ?: return reject("JSON không parse được thành object")

            val version = root["version"]?.toString() ?: return reject("thiếu trường 'version'")
            val enabled = root["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true

            val notes = mutableListOf<String>()
            var rollout: Double? = null

            // --- Guardrail thresholds ---
            val g = asMap(root["guardrails"])
            if (guardrails != null && g != null) {
                num(g["max_interstitial_per_session"])?.let {
                    guardrails.maxInterstitialPerSession =
                        clampUpper(it.toInt(), HARD_MAX_INTERSTITIAL_PER_SESSION, "max_interstitial_per_session", notes)
                }
                num(g["max_ads_24h"])?.let {
                    guardrails.maxAds24h =
                        clampUpper(it.toInt(), HARD_MAX_ADS_24H, "max_ads_24h", notes)
                }
                num(g["min_minutes_between_interstitial"])?.let {
                    // Cooldown NGẮN hơn = hung hăng hơn -> kẹp CẬN DƯỚI.
                    guardrails.minMinutesBetweenInterstitial =
                        clampLower(it.toInt(), HARD_MIN_COOLDOWN_MINUTES, "min_minutes_between_interstitial", notes)
                }
                num(g["churn_risk_threshold"])?.let {
                    guardrails.churnRiskProtectionThreshold = it.coerceIn(0.0, 1.0)
                }
            }

            // --- Ngưỡng chào bán (IAP_OFFER) ---
            val iap = asMap(root["iap"])
            if (guardrails != null && iap != null) {
                num(iap["max_offers_per_session"])?.let {
                    guardrails.maxIapOffersPerSession =
                        clampUpper(it.toInt(), HARD_MAX_IAP_OFFERS_PER_SESSION, "iap.max_offers_per_session", notes)
                }
                num(iap["min_minutes_between_offers"])?.let {
                    // Cooldown NGẮN hơn = hung hăng hơn -> kẹp cận DƯỚI.
                    guardrails.minMinutesBetweenIapOffers =
                        clampLower(it.toInt(), HARD_MIN_IAP_OFFER_COOLDOWN_MINUTES, "iap.min_minutes_between_offers", notes)
                }
                num(iap["max_purchases_per_session_before_break"])?.let {
                    // Ngưỡng CAO hơn = ngắt muộn hơn = bảo vệ yếu hơn -> kẹp cận trên.
                    guardrails.maxPurchasesPerSessionBeforeBreak =
                        clampUpper(it.toInt(), HARD_MAX_PURCHASES_BEFORE_BREAK, "iap.max_purchases_per_session_before_break", notes)
                }
                num(iap["frustration_streak_threshold"])?.let {
                    // Ngưỡng cao hơn = cho phép chào bán khi bế tắc sâu hơn -> kẹp cận trên.
                    // Kẹp theo HẰNG SỐ, không theo giá trị hiện tại: lấy giá trị hiện tại làm
                    // trần sẽ khiến ngưỡng tụt dần sau mỗi lần nạp artifact và không bao giờ
                    // phục hồi được — một cái bẫy ratchet chỉ lộ ra sau nhiều lần fetch.
                    guardrails.frustrationStreakThreshold =
                        clampUpper(it.toInt(), HARD_MAX_FRUSTRATION_STREAK_THRESHOLD, "iap.frustration_streak_threshold", notes)
                }
            }

            // --- Hỗ trợ gameplay (FREE_ACTION / GAMEPLAY_CONFIG) ---
            val gp = asMap(root["gameplay"])
            if (guardrails != null && gp != null) {
                num(gp["max_assists_per_session"])?.let {
                    // Trần CAO hơn = quà dồn dập hơn -> kẹp cận trên như mọi trần tần suất khác.
                    guardrails.maxAssistsPerSession =
                        clampUpper(it.toInt(), HARD_MAX_ASSISTS_PER_SESSION, "gameplay.max_assists_per_session", notes)
                }
            }

            // --- Guardrail theo tiến độ (từ teardown game production) ---
            // Đây là nhóm DUY NHẤT mà artifact được phép nới lỏng tự do: chúng mặc định TẮT
            // (0 = không áp), nên mọi giá trị dương đều làm hệ thống bảo thủ hơn hiện trạng.
            asMap(root["progression"])?.let { pg ->
                if (guardrails != null) {
                    num(pg["min_level_for_interstitial"])?.let {
                        guardrails.minLevelForInterstitial = it.toInt().coerceAtLeast(0)
                    }
                    num(pg["min_level_for_interstitial_after_loss"])?.let {
                        guardrails.minLevelForInterstitialAfterLoss = it.toInt().coerceAtLeast(0)
                    }
                    num(pg["min_minutes_after_rewarded"])?.let {
                        guardrails.minMinutesAfterRewarded = it.toInt().coerceAtLeast(0)
                    }
                    num(pg["skip_first_interstitials_on_reopen"])?.let {
                        guardrails.skipFirstInterstitialsOnReopen = it.toInt().coerceAtLeast(0)
                    }
                    num(pg["max_iap_offers_24h"])?.let {
                        guardrails.maxIapOffers24h = it.toInt().coerceAtLeast(0)
                    }
                    if (guardrails.minLevelForInterstitialAfterLoss in 1 until guardrails.minLevelForInterstitial) {
                        // Nhánh thua mở SỚM hơn nhánh thắng gần như chắc chắn là lỗi cấu hình:
                        // nó có nghĩa là dội quảng cáo vào người vừa thua trước cả người vừa thắng.
                        notes.add(
                            "progression: ngưỡng nhánh thua (${guardrails.minLevelForInterstitialAfterLoss}) " +
                            "THẤP hơn nhánh thắng (${guardrails.minLevelForInterstitial}) — kiểm tra lại"
                        )
                    }
                }
            }

            // --- Chống click-fraud ---
            val cf = asMap(root["click_fraud"])
            if (cf != null) {
                num(cf["max_clicks_per_window"])?.let {
                    // Cho phép NHIỀU click hơn = bảo vệ yếu hơn -> kẹp cận trên.
                    ClickFraudGuard.maxClicksPerWindow =
                        clampUpper(it.toInt(), HARD_MAX_CLICKS_PER_WINDOW, "max_clicks_per_window", notes)
                }
                num(cf["window_seconds"])?.let { ClickFraudGuard.windowMs = (it * 1000).toLong().coerceAtLeast(1000L) }
                num(cf["lockout_seconds"])?.let { ClickFraudGuard.lockoutMs = (it * 1000).toLong().coerceAtLeast(0L) }
            }

            // --- Thí nghiệm: holdout & sàn khám phá ---
            val exp = asMap(root["experiment"])
            if (exp != null) {
                num(exp["holdout_fraction"])?.let {
                    // Kẹp [0, 0.5]: holdout quá nửa dân số gần như chắc chắn là lỗi cấu hình.
                    HoldoutManager.holdoutFraction = it.coerceIn(0.0, 0.5)
                }
                exp["holdout_salt"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                    HoldoutManager.salt = it
                }
                num(exp["rollout_fraction"])?.let {
                    // Không kẹp trần như holdout: mở rộng tới 100% là đích đến hợp lệ của
                    // staged rollout. Chỉ kẹp về khoảng có nghĩa.
                    rollout = it.coerceIn(0.0, 1.0)   // 2.4.0 D17-240: chỉ rolloutOverride (AdsBrain.rollout() ưu tiên nó); không ghi rollout phía client
                    if (it <= 0.0) notes.add("rollout_fraction=0 → brain không điều khiển player nào")
                }
                num(exp["exploration_floor"])?.let { raw ->
                    val v = raw.coerceIn(0.0, 0.5)
                    if (v <= 0.0) notes.add("exploration_floor=0 → OPE sẽ không dùng được")
                    pendingExplorationFloor = v
                }
            }

            // --- Tỉ giá quy đổi doanh thu IAP ---
            // Nằm trong artifact vì tỉ giá đổi hằng ngày còn app hàng tháng mới release. Chỉ
            // ảnh hưởng tới tổng USD tích luỹ trên máy; số tiền nguyên bản luôn được gửi
            // nguyên vẹn trong event `iap_purchase` để backend tự quy đổi.
            asMap(root["fx_rates"])?.let { fx ->
                val parsed = fx.mapNotNull { (code, raw) -> num(raw)?.let { code to it } }.toMap()
                if (parsed.isNotEmpty()) {
                    com.app.plugin.iap.FxRates.setRates(parsed)
                    notes.add("đã nạp ${com.app.plugin.iap.FxRates.knownCurrencyCount()} tỉ giá")
                }
            }

            // --- Sản phẩm nào tắt quảng cáo ---
            // Khai báo từ xa để sửa được nhầm lẫn cấu hình mà không cần release: một
            // subscriber bị bỏ sót khỏi danh sách này sẽ tiếp tục thấy quảng cáo dù đã trả
            // tiền — đúng anti-pattern doc 27 mục B.3.
            (root["remove_ads_products"] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                ?.let { com.app.plugin.iap.Entitlements.setRemoveAdsProducts(it.toSet()) }

            // --- Trọng số reward: tham số QUẢN TRỊ, không phải tinh chỉnh kỹ thuật ---
            // Đẩy đủ mạnh về phía doanh thu sẽ đảo ngược thứ tự ưu tiên và đưa hệ thống về
            // đúng hành vi tham lam mà nó được thiết kế để tránh (doc 39 mục E). Đặt trong
            // artifact để mọi thay đổi đi qua quy trình duyệt policy, có version và audit.
            asMap(root["reward"])?.let { rw ->
                num(rw["weight_immediate_value"])?.let { RewardModel.weightImmediateValue = it.coerceIn(0.0, 1.0) }
                num(rw["weight_continuation"])?.let { RewardModel.weightContinuation = it.coerceIn(0.0, 1.0) }
                // Cảnh báo khi trọng số làm mất tính chất chống suy biến: "hiện ad rồi mất
                // người chơi" phải luôn bị chấm thấp hơn "im lặng mà giữ được người".
                val adThenQuit = RewardModel.estimate(true, false)
                val silentThenStay = RewardModel.estimate(false, true)
                if (adThenQuit >= silentThenStay) {
                    notes.add(
                        "trọng số reward làm MẤT tính chất chống suy biến " +
                            "(ad+quit=%.2f >= im lặng+ở lại=%.2f) → bandit sẽ học cách hung hăng"
                                .format(adThenQuit, silentThenStay)
                    )
                }
            }

            // --- Trần riêng cho từng kịch bản ---
            val scenarioCaps = mutableMapOf<PersonalizationScenario, Int>()
            asMap(root["scenarios"])?.forEach { (name, raw) ->
                val scenario = PersonalizationScenario.values().firstOrNull { it.name == name }
                if (scenario == null) {
                    notes.add("bỏ qua kịch bản lạ '$name'")
                    return@forEach
                }
                num(asMap(raw)?.get("interstitial_cap"))?.let {
                    scenarioCaps[scenario] =
                        clampUpper(it.toInt(), HARD_MAX_INTERSTITIAL_PER_SESSION, "scenarios.$name.interstitial_cap", notes)
                }
            }

            // --- Hồ sơ likelihood (cho phép calibrate segment từ xa) ---
            val profiles = mutableMapOf<LatentPlayerType, Profile>()
            asMap(root["likelihood_profiles"])?.forEach { (name, raw) ->
                val type = LatentPlayerType.values().firstOrNull { it.name == name }
                if (type == null) {
                    notes.add("bỏ qua latent type lạ '$name'")
                    return@forEach
                }
                val m = asMap(raw) ?: return@forEach
                val fail = num(m["fail_rate"]) ?: return@forEach
                val rew = num(m["rewarded_accept"]) ?: return@forEach
                val payer = num(m["payer_propensity"]) ?: return@forEach
                val churn = num(m["churn_tendency"]) ?: return@forEach
                profiles[type] = Profile(
                    fail.coerceIn(0.0, 1.0), rew.coerceIn(0.0, 1.0),
                    payer.coerceIn(0.0, 1.0), churn.coerceIn(0.0, 1.0)
                )
            }
            // Hồ sơ phải đủ CẢ 4 type, nếu không thì bỏ toàn bộ: trộn hồ sơ mới với hồ sơ cũ
            // tạo ra so sánh khập khiễng giữa các segment — sai còn tệ hơn không đổi.
            if (profiles.isNotEmpty() && profiles.size != LatentPlayerType.values().size) {
                notes.add("likelihood_profiles thiếu type (${profiles.size}/${LatentPlayerType.values().size}) → bỏ qua toàn bộ")
                profiles.clear()
            }

            val minConf = num(root["min_confidence"])?.coerceIn(0.0, 1.0)

            current = Artifact(version, enabled, scenarioCaps, profiles, minConf)
            artifactRollout = rollout
            val report = buildString {
                append("đã nạp '$version'")
                if (!enabled) append(" [KILL SWITCH: personalization TẮT]")
                if (scenarioCaps.isNotEmpty()) append(", ${scenarioCaps.size} scenario cap")
                if (profiles.isNotEmpty()) append(", ${profiles.size} profile")
                if (notes.isNotEmpty()) append("\n  ⚠ ${notes.joinToString("\n  ⚠ ")}")
            }
            lastApplyReport = report
            android.util.Log.i(com.app.plugin.until.LogUtil.TAG, "BrainPolicy: ${report.substringBefore('\n')}")   // cổng thiết bị đọc dòng này (T5s)
            ApplyResult(true, report)
        } catch (e: Exception) {
            reject("lỗi parse: ${e.message}")
        }
    }

    /** Bỏ artifact, quay về giá trị mặc định biên dịch trong code. */
    public fun clear() {
        current = null
        artifactRollout = null
        lastApplyReport = "đã xoá artifact — quay về mặc định trong code"
    }

    // ═══ Helper ═══

    private fun reject(reason: String): ApplyResult {
        // Cố ý KHÔNG xoá `current`: artifact mới hỏng thì giữ nguyên cái đang chạy tốt.
        lastApplyReport = "TỪ CHỐI artifact ($reason) — giữ nguyên '${activeVersion()}'"
        return ApplyResult(false, lastApplyReport)
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMap(any: Any?): Map<String, Any>? = any as? Map<String, Any>

    private fun num(any: Any?): Double? = when (any) {
        is Number -> any.toDouble()
        is String -> any.toDoubleOrNull()
        else -> null
    }

    private fun clampUpper(value: Int, ceiling: Int, field: String, notes: MutableList<String>): Int =
        if (value > ceiling) {
            notes.add("$field=$value vượt trần cứng $ceiling → kẹp về $ceiling")
            ceiling
        } else value.coerceAtLeast(0)

    private fun clampLower(value: Int, floor: Int, field: String, notes: MutableList<String>): Int =
        if (value < floor) {
            notes.add("$field=$value dưới sàn cứng $floor → kẹp về $floor")
            floor
        } else value
}
