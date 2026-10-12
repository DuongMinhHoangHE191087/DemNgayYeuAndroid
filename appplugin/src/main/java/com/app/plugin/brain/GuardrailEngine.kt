package com.app.plugin.brain

/**
 * Guardrail engine — chạy TRƯỚC khi bandit nhìn thấy action space (doc 06 mục 12-13, doc 14).
 *
 * PHÂN LOẠI HAI TẦNG (sửa theo doc 28 mục E.4 — bản đầu gộp hai loại làm một, đó là lỗi):
 *
 *  - `HARD`     : ràng buộc TUYỆT ĐỐI (đạo đức, chính sách store, chống gian lận, bảo vệ trẻ em).
 *                 Loại action khỏi candidate set, không bao giờ được biểu diễn thành penalty
 *                 trong reward — vì penalty luôn có thể bị "mua" bằng đủ doanh thu.
 *  - `RESOURCE` : ràng buộc TÀI NGUYÊN có ngân sách (ad-load/session, fatigue/ngày, rủi ro churn).
 *                 Theo lý thuyết Bandits-with-Knapsacks, lời giải tối ưu là một PHÂN PHỐI TRỘN
 *                 trên các arm, không phải "chọn arm tốt nhất rồi chặn". Lọc cứng loại này gây
 *                 bias hệ thống. Đúng ra phải dùng Lagrangian shadow price:
 *                     r_tilde(a) = r(a) - Σ_j lambda_j * c_j(a)
 *                     lambda_j ← lambda_j * exp(eta * (c_observed - B_j/T))
 *
 * TRẠNG THÁI: nợ kỹ thuật này ĐÃ ĐƯỢC TRẢ. `LagrangianBudget` cung cấp giá bóng cho nhóm
 * RESOURCE và được `DecisionEngine` trừ thẳng vào reward của từng arm trước khi bandit chọn.
 * Các ngưỡng RESOURCE trong class này vẫn giữ nguyên nhưng đổi vai trò: từ "cơ chế điều tiết
 * duy nhất" thành **trần an toàn tuyệt đối ở biên**. Hai lớp bổ sung nhau — Lagrangian điều
 * tiết nhịp bên trong ngân sách (mượt), trần cứng chặn ở biên (không thương lượng, vì phạt
 * mềm luôn có thể bị "mua" bằng đủ doanh thu).
 *
 * HAI NGUYÊN TẮC THIẾT KẾ (rút từ teardown app đối thủ — doc 27):
 *
 * 1. "Fail-safe, không phải fail-aggressive" — mọi giá trị default trong class này PHẢI là biên
 *    an toàn nhất hệ thống. App đối thủ mắc lỗi ngược: khi config lỗi cú pháp, hệ thống tự động
 *    bán gói ĐẮT NHẤT và tăng mật độ ad gấp 12 lần, im lặng.
 * 2. "Cô lập state theo loại action" — mỗi ActionMode phải có state cap RIÊNG, không share giữa
 *    hai loại không liên quan (app đối thủ share ô nhớ khoá tầng giữa pip↔banner, squeeze↔native).
 */
public enum class GuardrailKind {
    /** Ràng buộc tuyệt đối — luôn là hard filter, đúng về mặt lý thuyết. */
    HARD,

    /** Ràng buộc tài nguyên — điều tiết mượt bằng `LagrangianBudget`; ngưỡng ở đây là trần biên. */
    RESOURCE
}

public data class GuardrailViolation(
    val code: String,
    val reason: String,
    val kind: GuardrailKind
)

public class GuardrailEngine {
    // 2.4.0 CARRY-POL (#24): mọi trường ghi được là @Volatile — policy server áp trên `appplugin-policy`, quyết định đọc trên main.

    // Ngân sách tài nguyên. Production nạp từ policy artifact (kênh FirHelper remote config,
    // theo pattern AdsPlacementConfigManager.parserConfig()). Giá trị ở đây là biên an toàn
    // nhất — nếu fetch artifact fail, hệ thống rơi về đây và phải bảo thủ HƠN, không lỏng hơn.
    @Volatile public var maxInterstitialPerSession: Int = 6
    @Volatile public var minMinutesBetweenInterstitial: Int = 3
    @Volatile public var maxAds24h: Int = 40
    @Volatile public var churnRiskProtectionThreshold: Double = 0.75

    // --- Ngưỡng cho hành động chào bán (IAP_OFFER) ---
    // Cơ sở: doc 29 mục 5.3 số 4 (circuit breaker theo spend velocity) và mục 5.2
    // (deposit-frequency spike là marker at-risk mạnh nhất, dễ đo nhất trong IAP).
    // Các con số là LỰA CHỌN THIẾT KẾ bảo thủ, chưa được calibrate từ dữ liệu thật —
    // đúng như mọi ngưỡng khác trong class này.
    @Volatile public var maxIapOffersPerSession: Int = 3
    @Volatile public var minMinutesBetweenIapOffers: Int = 10

    /** Số lượt mua trong một phiên đủ để NGẮT chào bán. Circuit breaker của doc 29 mục 5.3. */
    @Volatile public var maxPurchasesPerSessionBeforeBreak: Int = 3

    /**
     * Chuỗi thua đủ sâu để coi là "đang bế tắc" — không chào bán ở trạng thái này.
     *
     * Ranh giới không nằm ở việc có thua hay không (chào bán sau vài lần thua là mẫu thiết kế
     * game bình thường), mà ở chỗ **mức sẵn lòng trả tăng theo độ dài chuỗi thua**. Chào bán
     * càng lúc bế tắc thì càng "hiệu quả" — và đó chính xác là lý do không được làm, theo
     * câu hỏi Q4 của rubric doc 29 mục 10.2.
     */
    @Volatile public var frustrationStreakThreshold: Int = 5

    // --- Guardrail theo tiến độ (chắt lọc từ teardown một game puzzle production) ---
    //
    // Điểm chung: bảo vệ KHOẢNH KHẮC MONG MANH. Người mới chưa gắn bó, người vừa thua đang
    // bực, người vừa mở lại app chưa vào nhịp — ba thời điểm mà một quảng cáo có giá trị tức
    // thì gần như nhau nhưng chi phí retention chênh nhau rất xa.
    //
    // Mặc định 0/-1 = TẮT, giữ nguyên hành vi cũ. Bật là quyết định của studio.

    /** Không hiện interstitial trước level này. Game tham chiếu dùng 10. */
    @Volatile public var minLevelForInterstitial: Int = 0

    /**
     * Ngưỡng riêng cho nhánh THUA, thường cao hơn nhánh thắng (game tham chiếu: 22 so với 10).
     * Lý do là tâm lý, không phải kỹ thuật: người vừa thua đã bực, chồng thêm quảng cáo là
     * đẩy họ rời game — cùng một logic mà `RewardModel` dựa vào.
     *
     * 0 = dùng chung ngưỡng với nhánh thắng.
     */
    @Volatile public var minLevelForInterstitialAfterLoss: Int = 0

    /**
     * Hoãn interstitial sau khi người chơi vừa xem rewarded. Game tham chiếu tách hẳn thông
     * số này khỏi cooldown thường. Người vừa TỰ NGUYỆN xem một quảng cáo dài không nên bị
     * chồng ngay một quảng cáo ép buộc.
     */
    @Volatile public var minMinutesAfterRewarded: Int = 0

    /** Bỏ qua N cơ hội interstitial đầu sau khi mở lại app — lúc người chơi đang cân nhắc ở lại. */
    @Volatile public var skipFirstInterstitialsOnReopen: Int = 0

    /** Trần chào bán theo 24h. Trần theo phiên không chặn được người mở app 10 lần/ngày. */
    @Volatile public var maxIapOffers24h: Int = 0

    /**
     * Trần hỗ trợ gameplay (quà tặng FREE_ACTION / chỉnh độ khó GAMEPLAY_CONFIG) mỗi phiên.
     *
     * Hỗ trợ MIỄN PHÍ cho studio nhưng không miễn phí cho trải nghiệm: dồn dập quà tặng tạo
     * "helplessness học được" (người chơi ngừng cố vì chờ được cho), phá tín hiệu tiến bộ mà
     * game dựa vào, và làm lệch kinh tế game. Trần này là chốt chặn biên cho cả hai loại action
     * hỗ trợ — nhịp bên trong do người chơi tự điều tiết (quà chỉ có giá trị khi họ đang kẹt).
     */
    @Volatile public var maxAssistsPerSession: Int = 3

    /** Đếm số lần mỗi guardrail đã NGĂN CHẶN thành công — đầu vào cho ComfortIndex (doc 26 mục C.4). */
    private val preventedCounts: MutableMap<String, Int> = mutableMapOf()

    public fun preventedCount(code: String): Int = preventedCounts[code] ?: 0

    public fun allPreventedCounts(): Map<String, Int> = preventedCounts.toMap()

    /**
     * Trần interstitial thực thi = min(trần tuyệt đối, trần của kịch bản).
     *
     * Chiều `min` là có chủ đích và là ràng buộc an toàn quan trọng: kịch bản personalization
     * chỉ được phép SIẾT chặt hơn, không bao giờ được nới lỏng vượt trần chung. Nếu cho phép
     * nới, một player bị phân loại nhầm vào AD_FORWARD sẽ nhận nhiều quảng cáo hơn cả mức mà
     * chính sách chung cho phép — đúng kiểu lỗi mà nguyên tắc fail-safe (doc 27) cấm.
     */
    public fun effectiveInterstitialCap(scenario: PersonalizationScenario?): Int {
        if (scenario == null) return maxInterstitialPerSession
        // Trần của kịch bản có thể được override từ policy artifact (đã kẹp an toàn ở đó).
        val scenarioCap = BrainPolicy.scenarioCap(scenario) ?: scenario.interstitialCapPerSession
        return minOf(maxInterstitialPerSession, scenarioCap)
    }

    private fun record(violation: GuardrailViolation): GuardrailViolation {
        preventedCounts[violation.code] = (preventedCounts[violation.code] ?: 0) + 1
        return violation
    }

    /**
     * @param scenario kịch bản personalization đang áp dụng. Trần của scenario chỉ có thể làm
     *   hệ thống BẢO THỦ HƠN, không bao giờ nới lỏng hơn trần tuyệt đối của guardrail — xem
     *   `effectiveInterstitialCap()`. Đây là ràng buộc an toàn: một segment bị phân loại sai
     *   không được phép mở khoá cho nhiều quảng cáo hơn mức trần chung.
     */
    public fun evaluate(
        candidate: ActionCandidate,
        state: PlayerState,
        adsShownThisSession: Int,
        minutesSinceLastInterstitial: Int,
        scenario: PersonalizationScenario? = null,
        /** Bối cảnh quyết định. Cần để phân biệt nhánh THẮNG với nhánh THUA — hai nhánh có
         *  ngưỡng khác nhau. `null` = không rõ bối cảnh, xử lý như nhánh thắng. */
        decisionPoint: DecisionPoint? = null
    ): GuardrailViolation? {
        val isAdLike = candidate.mode == ActionMode.INTERSTITIAL || candidate.mode == ActionMode.REWARDED_AD

        // ================= HARD — ràng buộc tuyệt đối =================

        // Chống click-fraud: bảo vệ tài khoản mediation (doc 27, pattern production đối thủ).
        // Miễn trừ NO_ACTION, GAMEPLAY_CONFIG và FREE_ACTION: khoá này bảo vệ kho quảng cáo
        // khỏi click gian lận, không phải để cấm game tặng quà hay chỉnh độ khó.
        if (candidate.mode != ActionMode.NO_ACTION &&
            candidate.mode != ActionMode.GAMEPLAY_CONFIG &&
            candidate.mode != ActionMode.FREE_ACTION &&
            ClickFraudGuard.isLocked(state.playerId)
        ) {
            return record(
                GuardrailViolation(
                    "CLICK_FRAUD_LOCKOUT",
                    "vượt ngưỡng click trong window, đang khoá ad",
                    GuardrailKind.HARD
                )
            )
        }

        // Đạo đức (doc 14): rewarded chỉ mời sau fail THẬT — không "manufactured fail".
        // Ràng buộc đạo đức không bao giờ được đổi thành penalty trong reward.
        if (candidate.mode == ActionMode.REWARDED_AD && state.failStreakCurrent <= 0) {
            return record(
                GuardrailViolation(
                    "ETH_MANUFACTURED_FAIL",
                    "rewarded chỉ nên mời sau fail thật (doc 14)",
                    GuardrailKind.HARD
                )
            )
        }

        // Đạo đức: không kiếm tiền trên sự bế tắc (doc 29 mục 10.2 câu Q4).
        if (candidate.mode == ActionMode.IAP_OFFER &&
            state.failStreakCurrent >= frustrationStreakThreshold
        ) {
            return record(
                GuardrailViolation(
                    "ETH_FRUSTRATION_EXPLOIT",
                    "đang bế tắc (chuỗi thua ${state.failStreakCurrent}) — không chào bán",
                    GuardrailKind.HARD
                )
            )
        }

        // Circuit breaker theo spend velocity (doc 29 mục 5.3 số 4).
        //
        // HARD chứ không RESOURCE, và đây là điểm then chốt: xếp vào RESOURCE sẽ biến nó
        // thành khoản phạt mềm mà bandit có thể "mua lại" bằng đủ doanh thu — mà doanh thu
        // từ đúng nhóm chi dồn dập thì luôn đủ lớn để mua. Bảo vệ người dễ tổn thương
        // không được phép có giá.
        if (candidate.mode == ActionMode.IAP_OFFER &&
            state.purchasesThisSession >= maxPurchasesPerSessionBeforeBreak
        ) {
            return record(
                GuardrailViolation(
                    "IAP_VELOCITY_BREAKER",
                    "đã mua ${state.purchasesThisSession} lần trong phiên — tạm dừng chào bán",
                    GuardrailKind.HARD
                )
            )
        }

        // ================= RESOURCE — trần an toàn ở biên =================
        // Nhịp tiêu thụ bên trong ngân sách do `LagrangianBudget` điều tiết (giá bóng trừ vào
        // reward trước khi bandit chọn). Các ngưỡng dưới đây là chốt chặn cuối: chạm tới nghĩa
        // là giá bóng đã không kìm đủ, và ta không thương lượng thêm.

        if (candidate.mode == ActionMode.INTERSTITIAL) {
            // Cửa sổ giữ chân người mới. Nhánh thua dùng ngưỡng riêng nếu có khai báo.
            val isLossPath = decisionPoint == DecisionPoint.LEVEL_FAIL ||
                decisionPoint == DecisionPoint.LEVEL_FAIL_STREAK
            val levelGate =
                if (isLossPath && minLevelForInterstitialAfterLoss > 0) minLevelForInterstitialAfterLoss
                else minLevelForInterstitial
            if (levelGate > 0 && state.currentLevel < levelGate) {
                return record(
                    GuardrailViolation(
                        "NEW_PLAYER_GRACE",
                        "level ${state.currentLevel} < ngưỡng $levelGate" +
                            (if (isLossPath) " (nhánh thua)" else ""),
                        GuardrailKind.RESOURCE
                    )
                )
            }

            // Vừa mở lại app — bỏ qua N cơ hội đầu.
            if (skipFirstInterstitialsOnReopen > 0 &&
                state.interstitialOpportunitiesThisSession < skipFirstInterstitialsOnReopen
            ) {
                return record(
                    GuardrailViolation(
                        "REOPEN_GRACE",
                        "cơ hội thứ ${state.interstitialOpportunitiesThisSession + 1} sau khi mở lại app",
                        GuardrailKind.RESOURCE
                    )
                )
            }

            // Vừa xem rewarded tự nguyện. -1 = chưa từng, hiểu là vô cùng lâu — nhầm chỗ này
            // sẽ chặn interstitial của mọi người chưa bao giờ xem rewarded, tức phần lớn dân số.
            if (minMinutesAfterRewarded > 0 &&
                state.minutesSinceLastRewarded in 0 until minMinutesAfterRewarded
            ) {
                return record(
                    GuardrailViolation(
                        "INTER_AFTER_REWARDED_COOLDOWN",
                        "vừa xem rewarded ${state.minutesSinceLastRewarded} phút trước",
                        GuardrailKind.RESOURCE
                    )
                )
            }

            val cap = effectiveInterstitialCap(scenario)
            if (adsShownThisSession >= cap) {
                return record(
                    GuardrailViolation(
                        "AD_PRESSURE_SESSION_CAP",
                        "vượt trần interstitial/phiên (hiệu lực=$cap" +
                            (scenario?.let { ", theo kịch bản ${it.displayName}" } ?: "") + ")",
                        GuardrailKind.RESOURCE
                    )
                )
            }
            if (minutesSinceLastInterstitial < minMinutesBetweenInterstitial) {
                return record(
                    GuardrailViolation(
                        "AD_PRESSURE_COOLDOWN",
                        "chưa đủ cooldown giữa 2 interstitial",
                        GuardrailKind.RESOURCE
                    )
                )
            }
            if (state.adImpressions24h >= maxAds24h) {
                return record(
                    GuardrailViolation(
                        "AD_PRESSURE_24H_CAP",
                        "vượt max_ads_24h",
                        GuardrailKind.RESOURCE
                    )
                )
            }
        }

        // Bảo vệ payer vừa mua: giảm ad pressure ngay sau giao dịch.
        //
        // Nhận diện payer bằng SỐ LƯỢT mua, không bằng tổng tiền. Từ khi có billing thật
        // (`com.app.plugin.iap`), giá về theo tiền tệ địa phương và tổng USD chỉ được cộng
        // khi biết chắc tỉ giá — người trả 9,99 EUR trên máy chưa nạp tỉ giá EUR có tổng USD
        // bằng 0. Dùng tổng tiền làm phép thử "đã từng trả tiền chưa" sẽ bỏ rơi đúng những
        // payer đó, im lặng và chỉ ở một số thị trường.
        if (isAdLike && state.purchaseCountLifetime > 0 && state.daysSinceLastPurchase in 0..2) {
            return record(
                GuardrailViolation(
                    "PAYER_PROTECTION_RECENT",
                    "recent payer cần giảm ad pressure",
                    GuardrailKind.RESOURCE
                )
            )
        }

        // Trần tần suất chào bán. Cô lập hoàn toàn khỏi state của quảng cáo (doc 27 mục B.2):
        // dùng chung bộ đếm giữa hai loại action không liên quan là lỗi đã thấy ở app đối thủ.
        if (candidate.mode == ActionMode.IAP_OFFER) {
            if (maxIapOffers24h > 0 && state.iapOffers24h >= maxIapOffers24h) {
                return record(
                    GuardrailViolation(
                        "IAP_OFFER_24H_CAP",
                        "vượt trần chào bán/24h ($maxIapOffers24h)",
                        GuardrailKind.RESOURCE
                    )
                )
            }
            if (state.iapOffersThisSession >= maxIapOffersPerSession) {
                return record(
                    GuardrailViolation(
                        "IAP_OFFER_SESSION_CAP",
                        "vượt trần chào bán/phiên ($maxIapOffersPerSession)",
                        GuardrailKind.RESOURCE
                    )
                )
            }
            // -1 = chưa từng chào bán trong phiên này, phải hiểu là "vô cùng lâu" chứ không
            // phải "0 phút trước" — hiểu nhầm chỗ này sẽ chặn đúng lần chào bán ĐẦU TIÊN.
            val since = state.minutesSinceLastIapOffer
            if (since in 0 until minMinutesBetweenIapOffers) {
                return record(
                    GuardrailViolation(
                        "IAP_OFFER_COOLDOWN",
                        "chưa đủ cooldown giữa 2 lần chào bán",
                        GuardrailKind.RESOURCE
                    )
                )
            }
        }

        // Trần tần suất hỗ trợ gameplay (FREE_ACTION / GAMEPLAY_CONFIG). Cô lập khỏi bộ đếm
        // của quảng cáo và chào bán — ba loại action không liên quan không share trạng thái
        // (nguyên tắc cô lập state, doc 27).
        if ((candidate.mode == ActionMode.FREE_ACTION || candidate.mode == ActionMode.GAMEPLAY_CONFIG) &&
            state.assistsThisSession >= maxAssistsPerSession
        ) {
            return record(
                GuardrailViolation(
                    "ASSIST_SESSION_CAP",
                    "vượt trần hỗ trợ/phiên ($maxAssistsPerSession)",
                    GuardrailKind.RESOURCE
                )
            )
        }

        // Ngân sách rủi ro churn.
        if (candidate.mode == ActionMode.INTERSTITIAL && state.churnRisk > churnRiskProtectionThreshold) {
            return record(
                GuardrailViolation(
                    "CHURN_PROTECTION",
                    "churn_risk cao, tránh thêm ad pressure",
                    GuardrailKind.RESOURCE
                )
            )
        }

        return null
    }
}
