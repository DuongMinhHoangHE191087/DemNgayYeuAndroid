package com.app.plugin.brain

import com.app.plugin.license.LicenseGate
import com.app.plugin.until.LogUtil

/**
 * Facade tích hợp — biến `DecisionEngine` từ "một engine tồn tại" thành "thứ studio dùng được".
 *
 * ═══ MẶC ĐỊNH TẮT ═══
 *
 * `enabled = false` khi khởi tạo. Ở trạng thái tắt, mọi hàm `should*` trả về `true` — tức
 * **giữ nguyên hành vi cũ hoàn toàn**, luật tần suất tĩnh trong `AdsPlacementConfig` quyết
 * định như trước. Bật brain là quyết định kinh doanh, không phải hệ quả phụ của việc cập
 * nhật SDK: một studio nâng phiên bản không được tự nhiên thấy mật độ quảng cáo thay đổi.
 *
 * ═══ CÁCH DÙNG ═══
 *
 * ```kotlin
 * // 1. Bật (chỉ khi đã quyết định)
 * AdsBrain.enabled = true
 *
 * // 2. Báo vòng đời. Id truyền vào cũng là `user_id` telemetry (PlayerIdSource): id nội bộ mờ, không email/số điện thoại.
 * AdsBrain.onSessionStart("player_123")
 *
 * // 3. Hỏi trước khi hiện quảng cáo
 * if (AdsBrain.shouldShowInterstitial()) {
 *     AdsHelper.instance.showInterstitial(...)
 *     AdsBrain.onInterstitialShown()
 * }
 *
 * // 4. Nạp tín hiệu hành vi để segment dịch chuyển
 * AdsBrain.onLevelFail()
 * AdsBrain.onRewardedOffered(accepted = true)
 * AdsBrain.onPurchase(4.99)
 * ```
 *
 * ═══ RANH GIỚI TRÁCH NHIỆM ═══
 *
 * Facade này **không** tự hiện quảng cáo và **không** đụng vào `AdsHelper`. Nó chỉ trả lời
 * "có nên hay không". Giữ nguyên tắc ở doc 06 mục 12: business rule và execution thuộc về
 * caller, brain chỉ xếp hạng trong tập ứng viên hợp lệ mà caller đưa ra.
 */
internal object AdsBrain {

    /** Công tắc chính. Tắt = hành vi y hệt khi chưa có brain. */
    @Volatile
    public var enabled: Boolean = false

    /**
     * Tỉ lệ dân số brain thực sự điều khiển, khi `enabled = true`. Phân nhóm tất định theo
     * `playerId` nên một người luôn ở cùng một phía.
     *
     * Mặc định 1.0 — công tắc an toàn là `enabled`, không phải trường này. Đặt mặc định 0
     * sẽ tạo một no-op im lặng: bật brain lên mà không có gì xảy ra và không có dấu hiệu
     * nào để chẩn đoán.
     *
     * Cách dùng đúng khi mở rộng (doc 38 mục F.2): giữ `enabled = true` cố định rồi nâng
     * dần trường này qua policy artifact (`experiment.rollout_fraction`) — 0.05 → 0.2 → 1.0,
     * mỗi bước so sánh với holdout. Hạ về 0 là đường lùi khẩn cấp không cần release app.
     */
    @Volatile
    public var rolloutFraction: Double = 1.0

    private val engine get() = DecisionEngine.instance

    /**
     * Brain có đang thực sự điều khiển player hiện tại không.
     *
     * Gộp cả hai cổng vào một chỗ: sai sót hay gặp là kiểm tra cổng ở hàm `should*` nhưng
     * quên ở `observe()`, khiến player ngoài thí nghiệm vẫn âm thầm cập nhật belief — dữ
     * liệu của họ lẫn vào nhóm treatment và làm hỏng phép so sánh.
     *
     * 1.9.0 T5s (D7): artifact `enabled:false` cũng tắt (interstitial về luật tĩnh, 0 `brain_decision`); rollout của
     * artifact thắng rollout app đặt. Chạm `engine` trước: lần đầu khôi phục artifact đã lưu (`BrainPolicyStore`).
     */
    private fun isActive(): Boolean =
        enabled && licensed() && engine.let { BrainPolicy.isEnabled() } && HoldoutManager.isInRollout(state.playerId, rollout())

    /** 2.2.0 LIC (D3/D16-220): chỉ VALID mới quyết — PENDING cũng về luật tĩnh (trước: chỉ INVALID chặn). */
    private fun licensed(): Boolean = LicenseGate.verdict is LicenseGate.Verdict.Valid

    // 2.2.0 LIC (D30-220): tín hiệu không phải quyết định chờ verdict đầu — PENDING giữ ≤ 8 (bỏ cũ nhất), VALID phát theo thứ tự
    // (trước tín hiệu mới), INVALID bỏ. Móc là một thể hiện: `LicenseGate.onVerdict` gộp trùng, không rò khi PENDING mãi.
    private val heldSignals = ArrayDeque<() -> Unit>()
    private val licenseHook: (LicenseGate.Verdict) -> Unit = { flushHeld(it) }

    private fun emitWhenLicensed(signal: () -> Unit): Unit = when (val v = LicenseGate.verdict) {
        is LicenseGate.Verdict.Valid -> { flushHeld(v); signal() }
        is LicenseGate.Verdict.Invalid -> heldSignals.clear()
        LicenseGate.Verdict.Pending -> { if (heldSignals.size >= 8) heldSignals.removeFirst(); heldSignals.addLast(signal); LicenseGate.onVerdict(licenseHook) }
    }

    @Synchronized private fun flushHeld(v: LicenseGate.Verdict) {
        val due = heldSignals.toList().also { heldSignals.clear() }
        if (v is LicenseGate.Verdict.Valid) due.forEach { try { it() } catch (e: Exception) { LogUtil.logE("AdsBrain: tín hiệu giữ lỗi ${e.message}") } }
    }

    private fun rollout(): Double = BrainPolicy.rolloutOverride() ?: rolloutFraction

    @Volatile
    private var state: PlayerState = PlayerState("anonymous")

    // Trạng thái phiên do facade tự quản, để caller không phải đếm tay.
    private var adsThisSession = 0
    private var lastInterstitialAtMs = 0L
    private var levelAttempts = 0
    private var levelFails = 0
    private var rewardedOffered = 0
    private var rewardedAccepted = 0
    private var lastIapOfferAtMs = 0L
    private var lastRewardedAtMs = 0L

    /**
     * Quyết định đang CHỜ tín hiệu downstream.
     *
     * Không chốt reward ngay lúc hành động: reward phải đến từ điều xảy ra sau đó
     * (`RewardModel`). Chốt ngay sẽ tạo reward tautological — hành động được thưởng vì
     * đã xảy ra, khiến bandit hội tụ về "luôn hiện quảng cáo".
     */
    private var pendingRequest: DecisionRequest? = null
    private var pendingResponse: DecisionResponse? = null
    private var pendingHadValue: Boolean = false

    // ═══ Vòng đời ═══

    /**
     * Bắt đầu phiên với player id đang có hiệu lực ([PlayerIdSource.current]): id app đã truyền qua
     * `MonetizationSdk.configure { playerId }` nếu có (trong process này, hoặc đã lưu từ lần mở trước — review cuối
     * I2), không thì id ổn định do SDK tự quản (`BrainStorage.playerId()`, UUID mỗi lần cài).
     *
     * `AppPluginManager.initPlugin` (mở app) và `AppPluginBase.onStart` (quay lại sau ≥ 30 phút ở nền) gọi bản này. Nhờ
     * đọc [PlayerIdSource], lần resume giữ đúng id app đã configure: bucket holdout/rollout, `experiment_exposure` và
     * `user_id` telemetry là một id (review D I2; trước đây resume về `BrainStorage.playerId()`, đổi bucket giữa phiên
     * trong khi `user_id` vẫn là id app). App không có id riêng thì như trước.
     */
    @Synchronized
    public fun onSessionStart(): Unit = startSession(PlayerIdSource.current())

    /**
     * Bắt đầu phiên với id người dùng riêng của app (tốt hơn id của SDK vì sống qua cả lần cài lại). Id này cũng thành
     * nguồn `user_id` telemetry và URL web shop ([PlayerIdSource.setFromApp], lưu cho cả các lần mở sau), ghi TRƯỚC khi
     * phát `experiment_exposure`: app gọi thẳng hàm này (DOC/38 §B bước 2) hay qua
     * `MonetizationSdk.configure { playerId }` thì bucket, exposure và `user_id` đều là một id (review D I1). Các lần
     * [onSessionStart] không tham số sau đó (resume) giữ id này.
     *
     * Truyền id nội bộ mờ (opaque), không email hay số điện thoại: nó tới server telemetry của SDK trên mọi event.
     * Id rỗng, hay chứa U+001F/xuống dòng, không phải id ([PlayerIdSource.isUsable], cùng điều kiện với `configure`):
     * log lỗi và dùng id đang có hiệu lực ([PlayerIdSource.current]).
     */
    @Synchronized
    public fun onSessionStart(playerId: String) {
        if (!PlayerIdSource.isUsable(playerId)) {
            LogUtil.logE(
                "AdsBrain.onSessionStart: playerId rỗng hoặc chứa U+001F/xuống dòng — dùng id đang có hiệu lực " +
                    "(id app đã truyền, hoặc id của SDK)"
            )
            startSession(PlayerIdSource.current())
            return
        }
        PlayerIdSource.setFromApp(playerId)   // bộ nhớ + prefs (review cuối I2)
        startSession(playerId)
    }

    /** Thân chung của hai bản [onSessionStart]; gọi trong khoá của `AdsBrain` (thứ tự khoá AdsBrain → BrainStorage). */
    private fun startSession(playerId: String) {
        // Phiên trước kết thúc mà chưa chốt -> coi như người chơi đã rời đi.
        resolvePending(playerContinued = false)
        if (state.playerId != playerId) {
            // Đổi player: dựng state mới thay vì mang số liệu của người trước sang.
            state = PlayerState(playerId)
            levelAttempts = 0; levelFails = 0
            rewardedOffered = 0; rewardedAccepted = 0
        }
        state.sessionsLifetime += 1
        state.daysSinceLastSession = 0
        adsThisSession = 0
        lastInterstitialAtMs = 0L
        lastIapOfferAtMs = 0L
        state.purchasesThisSession = 0
        state.iapOffersThisSession = 0
        state.minutesSinceLastIapOffer = -1
        state.assistsThisSession = 0
        // Bộ đếm cơ hội cấp lại mỗi phiên — đó chính là điều làm "ân hạn khi mở lại app"
        // có nghĩa. `lastRewardedAtMs` thì KHÔNG reset: người vừa xem rewarded rồi thoát ra
        // vào lại ngay vẫn là người vừa xem rewarded.
        state.interstitialOpportunitiesThisSession = 0
        state.iapOffers24h = BrainStorage.loadOfferCount24h()
        engine.startNewSession(playerId)
        DecisionLogger.flushQueue()
        // Bản ghi phân nhóm thí nghiệm (doc 23 Phase 4 mục 3) — mỗi phiên một hàng cho MỌI
        // player khi thí nghiệm đang chạy, KỂ CẢ người ngoài rollout và nhóm đối chứng: cờ
        // membership của chính họ là dữ liệu. Chỉ phát khi `enabled` vì brain tắt nghĩa là
        // không có thí nghiệm nào đang chạy — ghi membership lúc đó là dữ liệu gây nhiễu.
        // 2.2.0 LIC (D28/D30-220): chỉ phát khi VALID; cold start tới đây TRƯỚC khi `LicenseGate` bắt đầu → giữ tới verdict đầu.
        // `user_id` của hàng đọc PlayerIdSource lúc phát: người chơi đã đổi trong lúc chờ thì hàng này lệch bucket → bỏ.
        if (enabled && BrainPolicy.isEnabled()) {
            val rec = HoldoutManager.exposureRecord(playerId, rollout())
            emitWhenLicensed {
                if (PlayerIdSource.current() == playerId) com.app.plugin.logevent.LogEventServer.logExperimentExposure(
                    experimentId = rec.experimentId, holdoutBucket = rec.bucket, isHoldout = rec.isHoldout,
                    inRollout = rec.inRollout, saltVersion = rec.saltVersion
                )
            }
        }
    }

    /** Ảnh chụp trạng thái hiện tại — phục vụ debug view. */
    public fun currentState(): PlayerState = state

    /** Số interstitial đã hiện trong phiên hiện tại. Chỉ đọc; facade tự quản bộ đếm. */
    public fun adsShownThisSession(): Int = adsThisSession

    // ═══ Câu hỏi quyết định ═══

    /**
     * @return true nếu nên hiện interstitial. Khi `enabled = false` luôn trả `true`
     *   để caller giữ nguyên luồng cũ.
     */
    @Synchronized
    public fun shouldShowInterstitial(): Boolean {
        if (!isActive()) return true
        // Đếm CƠ HỘI, không đếm lượt hiện. Ân hạn khi mở lại app phải tính cả những lần bị
        // chặn — nếu chỉ đếm lượt hiện được thì ân hạn không bao giờ hết hạn.
        state.interstitialOpportunitiesThisSession += 1
        state.minutesSinceLastRewarded = minutesSince(lastRewardedAtMs)
        // Chạm tới một decision point mới CHÍNH LÀ bằng chứng người chơi còn ở lại —
        // nếu họ rời đi thì đã không có lần hỏi này. Chốt quyết định treo trước khi tạo
        // quyết định mới; ghi đè mà không chốt sẽ vừa mất dữ liệu vừa gán nhầm kết quả
        // của hành động trước sang hành động sau.
        resolvePending(playerContinued = true)
        val req = DecisionRequest(state.playerId, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val d = engine.decide(
            req, state,
            listOf(
                ActionCandidate(ActionMode.NO_ACTION, "no_action"),
                ActionCandidate(ActionMode.INTERSTITIAL, "interstitial", isBaseline = true)
            ),
            adsShownThisSession = adsThisSession,
            minutesSinceLastInterstitial = minutesSinceLastInterstitial()
        )
        openPending(req, d)
        return d.chosenAction.mode == ActionMode.INTERSTITIAL
    }

    /**
     * @return true nếu nên mời rewarded. Guardrail đạo đức (doc 14) tự chặn khi chưa có
     *   thất bại thật — không cần caller tự kiểm tra.
     */
    @Synchronized
    public fun shouldOfferRewarded(): Boolean {
        if (!isActive()) return true
        resolvePending(playerContinued = true)
        val req = DecisionRequest(state.playerId, DecisionPoint.REWARDED_OPPORTUNITY)
        val d = engine.decide(
            req, state,
            listOf(
                ActionCandidate(ActionMode.NO_ACTION, "no_action"),
                ActionCandidate(ActionMode.REWARDED_AD, "rewarded", isBaseline = true)
            ),
            adsShownThisSession = adsThisSession
        )
        openPending(req, d)
        return d.chosenAction.mode == ActionMode.REWARDED_AD
    }

    /**
     * Hỏi trước khi chào bán một gói IAP — một trong hai cổng nhị phân của vòng
     * cross-modal (doc 23 mục E.3).
     *
     * Tập ứng viên của cổng này là nhị phân (`IAP_OFFER` vs `NO_ACTION`). Quyết định
     * cross-modal thật sự — `REWARDED_AD` vs `IAP_OFFER` vs `NO_ACTION` tại CÙNG một điểm
     * quyết định theo propensity trả tiền — nằm ở [chooseCrossModal]. Dùng cổng này khi
     * game chỉ có chỗ cho một lời chào bán; dùng [chooseCrossModal] khi game có thể trao
     * cả reward lẫn mời mua và muốn brain tự chọn thứ đáng đưa nhất.
     *
     * Guardrail đạo đức tự chặn hai tình huống mà caller không cần tự kiểm tra: đang bế tắc
     * (`ETH_FRUSTRATION_EXPLOIT`) và đang chi dồn dập trong phiên (`IAP_VELOCITY_BREAKER`).
     *
     * @param decisionPoint bối cảnh chào bán. `SHOP_OPEN` khi người chơi tự mở cửa hàng,
     *   `CURRENCY_LOW` khi hết tài nguyên — hai bối cảnh này khác nhau về mặt đạo đức nên
     *   được học riêng, không gộp chung một arm.
     * @return true nếu nên chào bán. Khi brain chưa bật/ngoài rollout luôn trả `true` để
     *   caller giữ nguyên luồng cũ.
     */
    @Synchronized
    @JvmOverloads
    public fun shouldOfferIap(decisionPoint: DecisionPoint = DecisionPoint.SHOP_OPEN): Boolean {
        if (!isActive()) return true
        resolvePending(playerContinued = true)
        refreshIapTiming()
        val req = DecisionRequest(state.playerId, decisionPoint)
        val d = engine.decide(
            req, state,
            listOf(
                ActionCandidate(ActionMode.NO_ACTION, "no_action"),
                ActionCandidate(ActionMode.IAP_OFFER, "iap_offer", isBaseline = true)
            ),
            adsShownThisSession = adsThisSession
        )
        openPending(req, d)
        return d.chosenAction.mode == ActionMode.IAP_OFFER
    }

    /**
     * Đã thực sự hiện gói chào bán ra màn hình.
     *
     * KHÔNG đánh dấu là "đã giao được giá trị": hiện một gói mà không ai mua thì chưa tạo ra
     * gì cả. Giá trị chỉ được ghi nhận khi `onPurchase()` chốt lại quyết định đang treo —
     * nếu không, bandit sẽ học rằng cứ chào bán là thắng và hội tụ về việc chào bán liên tục,
     * đúng kiểu suy biến mà `RewardModel` sinh ra để tránh (doc 39).
     *
     * Brain tắt / ngoài rollout thì bỏ qua: các bộ đếm này chỉ được đọc trong `shouldOfferIap`,
     * mà hàm đó trả `true` ngay khi không active — nhích bộ đếm trong trạng thái tắt chỉ tạo
     * số liệu chết, và vi phạm nguyên tắc "tắt = hành vi y hệt khi chưa có brain".
     */
    @Synchronized
    public fun onIapOfferShown() {
        if (!isActive()) return
        state.iapOffersThisSession += 1
        state.iapOffers24h = BrainStorage.bumpOfferCount24h()
        lastIapOfferAtMs = System.currentTimeMillis()
        pendingHadValue = false
    }

    // ═══ Quyết định cross-modal — điểm khác mọi vendor (doc 23 mục E.3, Phase 4) ═══

    /** Kết quả của [chooseCrossModal]: action được chọn + cờ holdout. */
    public data class CrossModalDecision(val action: ActionMode, val isHoldout: Boolean)

    /**
     * Quyết định cross-modal thật sự: tại CÙNG một điểm quyết định, brain chọn giữa
     * `NO_ACTION`, `REWARDED_AD` và `IAP_OFFER` theo propensity trả tiền của người chơi —
     * thay vì hai cổng Boolean [shouldOfferRewarded]/[shouldOfferIap] hỏi hai câu hỏi tách
     * biệt và mù nhau. Người có xu hướng trả tiền được mời gói; người không, được mời xem
     * quảng cáo thưởng — thay vì cả hai cùng bị dội cả hai. Vì bandit thấy các arm CẠNH
     * TRANH trong cùng một lượt chọn, ad và purchase thay thế nhau chứ không cộng dồn áp lực.
     *
     * [baseline] là hành vi game sẽ làm nếu không có brain — holdout nhận đúng nó, và brain
     * tắt / ngoài rollout cũng trả đúng nó (tắt = hành vi cũ, cùng hợp đồng với các cổng
     * Boolean trả `true` khi tắt).
     *
     * [allowRewarded]/[allowIapOffer] cho phép game loại arm không tồn tại ở điểm đó
     * (ví dụ màn này không có reward để trao → `allowRewarded = false`). Cả hai bị cấm thì
     * không còn quyết định nào để ra — trả baseline luôn, không hỏi engine (không ghi quyết
     * định một arm vào log OPE).
     *
     * Feedback đi đúng đường ống treo của các cổng nhị phân: `REWARDED_AD` chốt qua
     * [onRewardedOffered], `IAP_OFFER` chốt qua [onPurchase] (onPurchase chỉ ghi công khi
     * quyết định treo mang mode `IAP_OFFER`).
     *
     * @param decisionPoint điểm ra quyết định — `LEVEL_FAIL`, `CURRENCY_LOW`,
     *   `LEVEL_COMPLETE`, `RETURN_AFTER_INACTIVITY`, `SHOP_OPEN`... Mỗi điểm được học riêng.
     */
    @Synchronized
    @JvmOverloads
    public fun chooseCrossModal(
        decisionPoint: DecisionPoint,
        allowRewarded: Boolean = true,
        allowIapOffer: Boolean = true,
        baseline: ActionMode = ActionMode.NO_ACTION
    ): CrossModalDecision {
        if (!allowRewarded && !allowIapOffer) return CrossModalDecision(baseline, isHoldout = false)
        if (!isActive()) return CrossModalDecision(baseline, isHoldout = false)

        // Chốt quyết định treo của lượt hỏi trước trước khi ra quyết định mới — cùng đường
        // ống feedback với các cổng Boolean.
        resolvePending(playerContinued = true)
        if (allowIapOffer) refreshIapTiming()

        val candidates = mutableListOf(ActionCandidate(ActionMode.NO_ACTION, "no_action"))
        if (allowRewarded) {
            candidates += ActionCandidate(
                ActionMode.REWARDED_AD, "rewarded",
                isBaseline = baseline == ActionMode.REWARDED_AD
            )
        }
        if (allowIapOffer) {
            candidates += ActionCandidate(
                ActionMode.IAP_OFFER, "iap_offer",
                isBaseline = baseline == ActionMode.IAP_OFFER
            )
        }

        val req = DecisionRequest(state.playerId, decisionPoint)
        val d = engine.decide(req, state, candidates, adsShownThisSession = adsThisSession)
        openPending(req, d)
        return CrossModalDecision(d.chosenAction.mode, d.isHoldout)
    }

    // ═══ Hỗ trợ gameplay — hai mode cuối của action space (doc 23 Phase 5, doc 24 mục C) ═══

    /** Kết quả của [chooseAssist]: loại hỗ trợ được chọn + cờ holdout. */
    public data class AssistDecision(val action: ActionMode, val isHoldout: Boolean)

    /**
     * Quyết định hỗ trợ gameplay: tại điểm người chơi đang kẹt, brain chọn giữa `NO_ACTION`,
     * `FREE_ACTION` (tặng hint/boost MIỄN PHÍ) và `GAMEPLAY_CONFIG` (giảm độ khó màn) — theo
     * belief về khả năng tự vượt qua của họ. Đây là hai mode cuối cùng của action space gốc
     * (doc 01) được nối dây thật; doc 24 mục C ghi nhận dynamic difficulty adjustment là một
     * decision hợp lệ của monetization brain, không phải chuyện riêng của game design.
     *
     * ═══ RÀNG BUỘC ĐẠO ĐỨC CỐT LÕI ═══
     *
     * Hàm này CỐ Ý không có arm `IAP_OFFER`. Cứu người bế tắc bằng thứ PHẢI TRẢ TIỀN là khai
     * thác frustration — đúng hành vi `ETH_FRUSTRATION_EXPLOIT` chặn. Hỗ trợ ở đây chỉ tồn tại
     * dưới dạng cho đi (FREE_ACTION) hoặc chỉnh trải nghiệm (GAMEPLAY_CONFIG); bán rescue là
     * quyết định IAP riêng của game, đi qua [shouldOfferIap]/[chooseCrossModal] nơi guardrail
     * từ chối nó khi chuỗi thua đã sâu.
     *
     * [baseline] là hành vi game sẽ làm nếu không có brain (thường `NO_ACTION` — không trợ
     * giúp); holdout và khi brain tắt nhận đúng nó. [allowFreeGift]/[allowDifficultyTuning] để
     * game loại loại hỗ trợ không tồn tại ở điểm đó (màn boss không cho chỉnh độ khó →
     * `allowDifficultyTuning = false`). Cả hai bị cấm thì trả baseline luôn, không hỏi engine.
     *
     * Feedback: [onAssistShown] khi đưa hỗ trợ ra, rồi [onAssistUsed] khi biết người chơi có
     * dùng hay không — bandit học "trợ giúp đúng lúc giữ chân người" thay vì "cứ tặng là thắng".
     *
     * @param decisionPoint điểm ra quyết định — thường `LEVEL_FAIL`, `LEVEL_FAIL_STREAK`,
     *   `CURRENCY_LOW`. Mỗi điểm được học riêng.
     */
    @Synchronized
    @JvmOverloads
    public fun chooseAssist(
        decisionPoint: DecisionPoint,
        allowFreeGift: Boolean = true,
        allowDifficultyTuning: Boolean = true,
        baseline: ActionMode = ActionMode.NO_ACTION
    ): AssistDecision {
        if (!allowFreeGift && !allowDifficultyTuning) return AssistDecision(baseline, isHoldout = false)
        if (!isActive()) return AssistDecision(baseline, isHoldout = false)

        // Chốt quyết định treo của lượt hỏi trước — cùng đường ống feedback với mọi cổng khác.
        resolvePending(playerContinued = true)

        val candidates = mutableListOf(ActionCandidate(ActionMode.NO_ACTION, "no_action"))
        if (allowFreeGift) {
            candidates += ActionCandidate(
                ActionMode.FREE_ACTION, "free_assist",
                isBaseline = baseline == ActionMode.FREE_ACTION
            )
        }
        if (allowDifficultyTuning) {
            candidates += ActionCandidate(
                ActionMode.GAMEPLAY_CONFIG, "ease_difficulty",
                isBaseline = baseline == ActionMode.GAMEPLAY_CONFIG
            )
        }

        val req = DecisionRequest(state.playerId, decisionPoint)
        val d = engine.decide(req, state, candidates, adsShownThisSession = adsThisSession)
        openPending(req, d)
        return AssistDecision(d.chosenAction.mode, d.isHoldout)
    }

    /**
     * Đã thực sự đưa hỗ trợ ra cho người chơi (hiện nút nhận quà / áp dụng độ khó mới).
     *
     * Cùng học thuyết với [onIapOfferShown]: đưa ra chưa phải giá trị — người chơi nhận quà
     * rồi vẫn bỏ game thì chưa tạo ra gì. Giá trị chỉ chốt ở [onAssistUsed]/tín hiệu ở lại.
     * Bộ đếm phiên ở đây là đầu vào của trần `ASSIST_SESSION_CAP`.
     */
    @Synchronized
    public fun onAssistShown() {
        if (!isActive()) return
        state.assistsThisSession += 1
        pendingHadValue = false
    }

    /**
     * Kết quả của lần hỗ trợ vừa đưa ra.
     *
     * `used = true`: người chơi ĐÃ dùng hỗ trợ — đó vừa là giá trị trao đi vừa là tín hiệu
     * họ tiếp tục chơi, nên chốt ngay như [onRewardedOffered] chốt lượt nhận quảng cáo thưởng.
     * `used = false`: đưa ra mà bị phớt lờ — KHÔNG chốt âm ngay; chờ tín hiệu ở lại thật
     * (`onLevelWin`/`onLevelFail`/`onSessionEnd`) như đường [onAdSkipped], vì người từ chối
     * quà vẫn có thể tự vượt qua và ở lại.
     */
    @Synchronized
    public fun onAssistUsed(used: Boolean) {
        if (!isActive()) return
        if (used) {
            pendingHadValue = true
            resolvePending(playerContinued = true)
        } else {
            pendingHadValue = false
        }
    }

    // ═══ Tín hiệu hành vi ═══

    @Synchronized
    public fun onInterstitialShown() {
        adsThisSession += 1
        state.adImpressions24h += 1
        lastInterstitialAtMs = System.currentTimeMillis()
        pendingHadValue = true   // chờ xem người chơi có ở lại không rồi mới chốt
    }

    /** Caller quyết định không hiện (hoặc ad không sẵn sàng). */
    @Synchronized
    public fun onAdSkipped() {
        pendingHadValue = false
    }

    /**
     * Người chơi tiếp tục chơi — tín hiệu downstream TÍCH CỰC.
     *
     * Được gọi tự động từ `onLevelFail/onLevelWin/onPurchase`. Gọi thủ công nếu game có
     * hoạt động khác đáng coi là "vẫn đang chơi".
     */
    @Synchronized
    public fun onPlayerContinued(): Unit = resolvePending(playerContinued = true)

    /**
     * Kết thúc phiên. Quyết định nào còn treo sẽ được chốt là **người chơi đã rời đi** —
     * nếu vừa hiện quảng cáo xong mà phiên chấm dứt, đó là tín hiệu tiêu cực thật sự.
     */
    @Synchronized
    public fun onSessionEnd() {
        resolvePending(playerContinued = false)
        reportComfort()
        DecisionLogger.flushQueue()
    }

    /**
     * ComfortIndex hiện tại của người chơi này — North Star Metric thứ tư (doc 26 mục C.4).
     *
     * Đọc `Result.coverage` trước khi đọc `Result.value`: hiện chỉ 2/5 thành phần có dữ liệu
     * nên `isTrustworthy()` luôn trả false. Con số này dùng để theo dõi xu hướng, chưa dùng
     * để ra quyết định kinh doanh.
     */
    public fun comfort(): ComfortIndex.Result =
        ComfortIndex.compute(state, engine.guardrails.maxAds24h)

    /**
     * Chốt ComfortIndex mỗi cuối phiên và gửi lên pipeline.
     *
     * Cuối phiên là thời điểm đúng: các bộ đếm của phiên đã đầy đủ, và đây là nhịp tự nhiên
     * nhất mà SDK chắc chắn có. Trước đây `ComfortIndex` tồn tại nhưng không ai gọi — một chỉ
     * số North Star không được tính ở đâu thì trên thực tế là chưa có.
     */
    private fun reportComfort() {
        if (!isActive()) return
        try {
            val r = comfort()
            com.app.plugin.logevent.LogEventServer.logBrainComfort(
                value = r.value,
                coverage = r.coverage,
                componentsMissing = r.componentsMissing.joinToString(";"),
                policyAggressiveness = ComfortIndex.policyAggressiveness(engine)
                    .entries.joinToString(";") { "${it.key}=${it.value}" }
            )
        } catch (e: Exception) {
            // Chỉ số quan sát không bao giờ được làm hỏng vòng đời phiên.
        }
    }

    @Synchronized
    public fun onRewardedOffered(accepted: Boolean) {
        rewardedOffered += 1
        if (accepted) {
            rewardedAccepted += 1
            state.adImpressions24h += 1
            lastRewardedAtMs = System.currentTimeMillis()
        }
        recompute()
        pendingHadValue = accepted
        // Xem rewarded xong là hành vi chủ động -> chính nó đã là tín hiệu tiếp tục chơi.
        resolvePending(playerContinued = accepted)
        observe()
    }

    @Synchronized
    public fun onLevelFail() {
        resolvePending(playerContinued = true)
        levelAttempts += 1; levelFails += 1
        state.failStreakCurrent += 1
        recompute(); observe()
    }

    @Synchronized
    public fun onLevelWin() {
        resolvePending(playerContinued = true)
        levelAttempts += 1
        state.failStreakCurrent = 0
        state.currentLevel += 1
        recompute(); observe()
    }

    @Synchronized
    public fun onPurchase(usd: Double) {
        // Nếu quyết định đang treo CHÍNH LÀ một lần chào bán, thì lượt mua này là kết quả của
        // nó và phải được ghi nhận là có giá trị. Kiểm tra đúng loại action thay vì đánh dấu
        // vô điều kiện: một lượt mua xảy ra khi đang treo quyết định interstitial sẽ ghi công
        // nhầm cho quảng cáo, và bandit học sai theo hướng có lợi cho việc hiện quảng cáo.
        if (pendingResponse?.chosenAction?.mode == ActionMode.IAP_OFFER) {
            pendingHadValue = true
        }
        resolvePending(playerContinued = true)
        val isFirstPurchase = state.purchaseCountLifetime == 0
        state.purchasesThisSession += 1
        state.purchaseCountLifetime += 1
        state.purchaseValueLifetimeUsd += usd
        state.daysSinceLastPurchase = 0
        recompute(); observe()
        // Lượt mua đầu đời là thời điểm chuyển đổi quan trọng nhất với CRM — phát một lần
        // duy nhất (điều kiện bắt trước khi tăng đếm). Gate consent/config nằm trong bridge.
        if (isFirstPurchase) {   // 2.2.0 LIC (D28/D30-220): chưa VALID thì giữ tới verdict đầu; đếm mua ở trên vẫn cập nhật
            val pid = state.playerId; val holdout = HoldoutManager.isHoldout(pid)
            emitWhenLicensed { BrainSignalListener.current()?.onFirstPurchase(pid, usd, holdout) }
        }
    }

    // ═══ Nội bộ ═══

    /**
     * Cập nhật "bao lâu rồi kể từ lần chào bán trước" ngay trước khi hỏi guardrail. Tính sẵn
     * ở đây thay vì để guardrail tự tính vì `PlayerState` là ảnh chụp — nó không biết đồng hồ.
     */
    private fun refreshIapTiming() {
        state.minutesSinceLastIapOffer =
            if (lastIapOfferAtMs == 0L) -1
            else ((System.currentTimeMillis() - lastIapOfferAtMs) / 60_000L)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun minutesSinceLastInterstitial(): Int = minutesSince(lastInterstitialAtMs, Int.MAX_VALUE)

    /**
     * @param never giá trị khi sự kiện chưa từng xảy ra. Interstitial dùng `MAX_VALUE` (đã
     *   rất lâu), rewarded dùng `-1` vì guardrail phân biệt "chưa từng" với "vừa xong" —
     *   trả 0 cho "chưa từng" sẽ chặn interstitial của mọi người chưa bao giờ xem rewarded.
     */
    private fun minutesSince(atMs: Long, never: Int = -1): Int =
        if (atMs == 0L) never
        else ((System.currentTimeMillis() - atMs) / 60_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * Tổng hợp tỉ lệ từ bộ đếm thô — vai trò của feature pipeline trong hệ thống đầy đủ.
     * Ở đây làm tại client vì SDK chưa có feature store (doc 31 mục 1).
     */
    private fun recompute() {
        state.failRateLast30m =
            if (levelAttempts > 0) levelFails.toDouble() / levelAttempts else 0.0
        state.rewardedOpportunitiesRecent = rewardedOffered
        state.rewardedAcceptRateRecent =
            if (rewardedOffered > 0) rewardedAccepted.toDouble() / rewardedOffered else 0.0
    }

    private fun observe() {
        if (!isActive()) return
        // Tín hiệu CRM chỉ phát khi belief THỰC SỰ dịch chuyển — lấy loại trước/sau
        // thay vì đoán, vì update() chạy nhưng posterior có thể không đổi argmax.
        val before = engine.beliefFor(state.playerId).mostLikelyType()
        val updated = engine.observe(state)
        if (updated) {
            val after = engine.beliefFor(state.playerId).mostLikelyType()
            if (after != before) {
                BrainSignalListener.current()?.onSegmentChange(
                    state.playerId, before.name, after.name,
                    HoldoutManager.isHoldout(state.playerId)
                )
            }
        }
    }

    /** Mở một quyết định mới: giá trị của nó chưa có — chỉ onInterstitialShown/onRewardedOffered/onPurchase/onAssistUsed mới đặt (O9, plan 1.6.0 Task 1). */
    private fun openPending(req: DecisionRequest, d: DecisionResponse) {
        pendingRequest = req; pendingResponse = d
        pendingHadValue = false
    }

    /**
     * Chốt quyết định đang treo bằng tín hiệu downstream.
     *
     * Gọi khi không có quyết định treo là vô hại (bỏ qua im lặng) — caller không phải theo
     * dõi xem đã có quyết định nào chưa.
     */
    private fun resolvePending(playerContinued: Boolean) {
        val req = pendingRequest ?: return
        val res = pendingResponse ?: return
        pendingRequest = null; pendingResponse = null
        val reward = RewardModel.estimate(pendingHadValue, playerContinued)
        pendingHadValue = false
        engine.feedback(req, res, reward)
    }
}
