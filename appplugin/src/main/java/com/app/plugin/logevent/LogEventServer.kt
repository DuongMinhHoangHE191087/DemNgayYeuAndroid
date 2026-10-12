package com.app.plugin.logevent

import com.app.plugin.until.AppUtil
import com.app.plugin.until.Constants
import com.app.plugin.until.PreferenceHelper

private const val BASE_TAG = "LOG_EVENT_SERVER"

/**
 * Gửi event lên server của SDK qua một hàng đợi trong bộ nhớ (gate G6, xem `LogEventQueue`): trần 500,
 * 4 request đang bay, backoff 1→60 s, 4xx không thử lại.
 *
 * - Hàng đợi chỉ ở bộ nhớ: process chết thì event chưa gửi mất. Decision log của brain vẫn còn bản trên
 *   đĩa (`DecisionLogQueue`) và được replay ở lần flush sau.
 * - `onResult` của các hàm `log…` nhận tham số này được gọi đúng một lần, trên luồng bất kỳ (luồng gọi khi
 *   bị bỏ ngay, luồng IO của SDK, hoặc main thread khi Retrofit trả lời), và khi mạng lỗi có thể trễ vài
 *   phút, tới vài giờ khi mất mạng lâu — tới khi event gửi được hoặc bị bỏ (backoff tới 60 s, tối đa 5 lần
 *   thử lại, event xếp sau event đang chờ backoff). Đừng làm việc nặng hay chạm UI trong đó.
 */
public object LogEventServer {
    private var lastTimestamp = 0L

    /** CHỈ DÙNG TRONG TEST: xem [LogEventTransport.resetSenderForTest]. */
    @JvmSynthetic
    internal fun resetSenderForTest() = LogEventTransport.resetSenderForTest()

    /** CHỈ DÙNG TRONG TEST. */
    @JvmSynthetic
    internal fun pendingEventsForTest(): Int = LogEventTransport.pendingEventsForTest()

    /** Số event còn đưa vào hàng đợi mà không đẩy event cũ nhất ra ([LogEventTransport.eventQueueHeadroom]). */
    internal fun eventQueueHeadroom(): Int = LogEventTransport.eventQueueHeadroom()

    /** CHỈ DÙNG TRONG TEST. */
    @JvmSynthetic
    internal fun droppedEventsForTest(): Long = LogEventTransport.droppedEventsForTest()

    /** Dựng service gửi event (phần gửi ở [LogEventTransport]); `baseEndpoint` rỗng = telemetry tắt. */
    public fun init(baseEndpoint: String, key: String, isDebugMode: Boolean = false): Unit =
        LogEventTransport.init(baseEndpoint, key, isDebugMode)

    /** `init` đã dựng service gửi event. Replay decision log chờ điều này (không gửi vào hàng chưa init). */
    internal fun isInitialized(): Boolean = LogEventTransport.isInitialized()

    private fun isPassed30Minutes(): Boolean {
        val currentTimestamp = System.currentTimeMillis()
        val thirtyMinutesInMillis = 30 * 60 * 1000
        return (currentTimestamp - lastTimestamp) >= thirtyMinutesInMillis || lastTimestamp == 0L
    }

    /**
     * Dựng JSON của event rồi đưa vào hàng đợi (gate G6: trần 500, 4 request đang bay, backoff 1→60 s,
     * 4xx không thử lại). Event lỗi mạng/5xx được gửi lại thay vì mất, nên có thể tới server trễ.
     *
     * @param onResult tuỳ chọn — gọi đúng một lần: `true` khi server nhận, `false` khi event bị bỏ
     *   (chưa init, kill switch, không dựng được body, tràn hàng, 4xx, hết lượt thử lại). Chạy trên luồng
     *   bất kỳ và có thể trễ vài phút, tới vài giờ khi mất mạng lâu (xem KDoc của object). Cần cho
     *   decision log của Monetization Brain: hàng đợi bền vững chỉ được xoá bản ghi khi server đã nhận.
     */
    private fun logEventToServer(
        eventName: String,
        eventData: Map<String, Any>,
        onResult: ((Boolean) -> Unit)? = null
    ) = LogEventTransport.sendEvent(eventName, eventData, onResult?.let { cb -> { o: EventOutcome -> cb(o == EventOutcome.ACCEPTED) } })

    /** Schema v2 (1.7.0): `isLoad` 1/3 → `ad_fill`, 0/2 → `ad_no_fill`; v1 không có `request_id`/`error_code` (rỗng). */
    public fun logAdLoad(
        adFormat: String,
        adPlatform: String,
        adNetwork: String,
        placement: String,
        isLoad: Int,
        loadTime: Long,
        screenName: String,
        adID: String,
        isSDKInit: Int
    ) {
        val r = AdEventLog.LoadRequest(adFormat, AdEventLog.mediationOf(adNetwork), placement, adID, "", screenName, isSDKInit)
        if (isLoad == 1 || isLoad == 3) AdEventLog.fill(r, loadTime, adPlatform) else AdEventLog.noFill(r, loadTime, "", "")
    }

    /** Schema v2 (1.7.0): `ad_show_attempt`, `ready` = 1 khi [isAction] (trạng thái `AdTracker`) là đã load. */
    public fun logAdStartAction(
        adFormat: String,
        placement: String,
        isAction: Int,
        screenName: String,
        isSDKInit: Int
    ): Unit = AdEventLog.showAttempt(adFormat, placement, if (isAction == AdStatus.Loaded.code) 1 else 0, screenName)

    public fun logEventConfigChange(status: String) {
        val eventData: Map<String, Any> = mapOf(
            "config_change_status" to status,
            "screen_name" to ScreenName.name
        )

        logEventToServer(EventNames.CF_CHANGE, eventData)
    }

    /** Schema v2 (1.7.0): `ad_request` với `request_id` mới ([adNetwork] = mạng trung gian → `mediation`). */
    public fun logAdStartLoad(
        adFormat: String,
        adPlatform: String,
        adNetwork: String,
        placement: String,
        screenName: String,
        adID: String,
        isSDKInit: Int
    ): Unit = AdEventLog.request(
        AdEventLog.LoadRequest(adFormat, AdEventLog.mediationOf(adNetwork), placement, adID, AdEventLog.newRequestId(), screenName, isSDKInit)
    )

    /** Schema v2 (1.7.0): `ad_show` ([adPlatform] = mạng thắng → `ad_source`); `imp_id` rỗng tới khi có paid event (T3). */
    public fun logAdShow(
        adFormat: String,
        adPlatform: String,
        adNetwork: String,
        placement: String,
        screenName: String,
        isShow: Int,
        adID: String
    ): Unit = AdEventLog.show(adFormat, AdEventLog.mediationOf(adNetwork), AppUtil.extractAdNetwork(adPlatform), placement, adID, "")

    /** Schema v2 (1.7.0): `ad_show_fail`, `ad_format` suy từ tiền tố placement, `is_action` = trạng thái `AdTracker`. */
    public fun logAdShowFailWithReason(
        placement: String,
        reason: String
    ) {
        val plm = placement.ifEmpty { "unknown" }
        AdEventLog.showFail(AdEventLog.formatOfPlacement(plm), plm, reason, AdTracker.getStatus(plm), ScreenName.name)
    }

    /** Schema v2 (1.7.0): `ad_revenue`, `value_micros` = round([adRevenue]·1e6), `precision_type` -1, `imp_id` rỗng. */
    public fun logAdRevenue(
        adFormat: String,
        adPlatform: String,
        adNetwork: String,
        placement: String,
        screenName: String,
        adRevenue: Double,
        currencyCode: String,
        adID: String
    ): Unit = AdEventLog.revenue(
        adFormat, AdEventLog.mediationOf(adNetwork), adPlatform, adID, placement, Math.round(adRevenue * 1_000_000),
        currencyCode, -1, "", screenName
    )

    public fun loadSDKInitTime() {
        logEventToServer(EventNames.SDK_INIT_TIME, mapOf())
    }

    public fun loadFetchConfigTime(isSuccess: Int, screenName: String) {
        val eventData: Map<String, Any> = mapOf(
            "is_fetch_success" to isSuccess,
            "screen_name" to screenName
        )
        logEventToServer(EventNames.FETCH_CF_TIME, eventData)
    }

    public fun loadFireStoreTime(isSuccess: Int, screenName: String) {
        val eventData: Map<String, Any> = mapOf(
            "is_fetch_success" to isSuccess,
            "screen_name" to screenName
        )
        logEventToServer(EventNames.FIRESTORE_TIME, eventData)
    }

    public fun loadStartFireStore(screenName: String) {
        val eventData: Map<String, Any> = mapOf(
            "screen_name" to screenName
        )
        logEventToServer(EventNames.START_FIRESTORE, eventData)
    }

    public fun logFetchConfigPassTimeOut(isPass: Int) {
        val eventData: Map<String, Any> = mapOf(
            "is_pass" to isPass
        )
        logEventToServer(EventNames.FETCH_CF_TIMEOUT, eventData)
    }

    public fun logFetchConfigStart() {
        logEventToServer(EventNames.FETCH_CF_START, mapOf())
    }

    public fun logAdAdapterInit(
        adPlatform: String,
        adapterClass: String,
        isReady: Int,
        latency: Long,
        screenName: String
    ) {
        val eventData: Map<String, Any> = mapOf(
            "ad_platform" to adPlatform,
            "adapter_class" to adapterClass,
            "is_ready" to isReady,
            "init_latency_ms" to latency,
            "screen_name" to screenName
        )

        logEventToServer(EventNames.ADMOB_ADAPTER_INIT, eventData)
    }


    public fun logScreenPlay(
        screenName: String,
        idItem: Int,
    ) {
        val eventData: Map<String, Any> = mapOf(
            "screen_name" to screenName, "id_item" to idItem
        )

        logEventToServer(EventNames.SCREEN_PLAY, eventData)
    }

    public fun logScreenEnd(
        totalDuration: Int,
        screenName: String,
        idItem: Int,
        screenSession: Int,
    ) {
        val eventData: Map<String, Any> = mapOf(
            "total_duration" to totalDuration,
            "screen_name" to screenName,
            "id_item" to idItem,
            "screen_session" to screenSession
        )

        logEventToServer(EventNames.SCREEN_END, eventData)
    }

    private fun logSessionStart() {
        if (isPassed30Minutes()) {
            val lost = TelemetryConsentGate.consumePendingLostForSessionStart()   // O3 (a): event process trước giữ mà mất
            logEventToServer(EventNames.SESSION_START, if (lost > 0) mapOf("pre_consent_lost" to lost) else mapOf())
        }
        lastTimestamp = System.currentTimeMillis()
    }

    private fun logFirstOpen() {
        logEventToServer(EventNames.FIRST_OPEN, mapOf())
    }

    public fun logEvent(behavior: String, screenName: String) {
        val eventData: Map<String, Any> = mapOf(
            "id_item" to behavior,
            "screen_name" to screenName,
        )

        logEventToServer(EventNames.BEHAVIOR, eventData)
    }

    public fun logButtonClick(behavior: String, screenName: String) {
        val eventData: Map<String, Any> = mapOf(
            "id_item" to behavior,
            "screen_name" to screenName,
        )

        logEventToServer(EventNames.BUTTON_CLICK, eventData)
    }


    /**
     * Decision log của Monetization Brain — bản ghi bắt buộc cho off-policy evaluation.
     *
     * `chosenActionProbability` (propensity) và `eligibleActions` là hai trường KHÔNG THỂ
     * tái tạo sau này: propensity phụ thuộc trạng thái posterior tại đúng thời điểm quyết
     * định, còn tập ứng viên phụ thuộc guardrail đã chạy lúc đó. Thiếu chúng thì mọi
     * estimator OPE (IPS, DR...) đều vô hiệu — xem doc 28 mục H.
     */
    public fun logBrainDecision(
        decisionId: String,
        decisionPoint: String,
        chosenActionId: String,
        chosenActionMode: String,
        chosenActionProbability: Double,
        eligibleActions: String,
        guardrailsApplied: String,
        segment: String,
        segmentConfidence: Double,
        scenario: String,
        isHoldout: Int,
        policyVersion: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData = brainDecisionEventData(
            decisionId, decisionPoint, chosenActionId, chosenActionMode, chosenActionProbability,
            eligibleActions, guardrailsApplied, segment, segmentConfidence, scenario, isHoldout, policyVersion
        )
        logEventToServer(EventNames.BRAIN_DECISION, eventData, onResult)
    }

    /** Event sống của `DecisionLogger` (O1 (b)): [eventData] dựng sẵn, body mang [userId] = trường 13 (`null` = id hiện hành). */
    internal fun logBrainDecisionForUser(eventData: Map<String, Any>, userId: String?, onResult: (Boolean) -> Unit) =
        LogEventTransport.sendEvent(EventNames.BRAIN_DECISION, eventData, { o -> onResult(o == EventOutcome.ACCEPTED) }, userId)

    /**
     * Replay của `DecisionLogger`: như [logBrainDecision] nhưng báo [EventOutcome], để bản ghi bị server từ
     * chối (gửi lại vô ích) được xử lý khác bản bị bỏ vì lý do tạm thời. [userId]: `user_id` lúc ghi bản ghi (review
     * D I3), để replay trước `configure` không mang id khác với id đã tính `is_holdout`; `null` = id hiện hành.
     */
    internal fun replayBrainDecision(
        eventData: Map<String, Any>,
        userId: String? = null,
        onOutcome: (EventOutcome) -> Unit
    ) = LogEventTransport.sendEvent(EventNames.BRAIN_DECISION, eventData, onOutcome, userId)

    internal fun brainDecisionEventData(
        decisionId: String,
        decisionPoint: String,
        chosenActionId: String,
        chosenActionMode: String,
        chosenActionProbability: Double,
        eligibleActions: String,
        guardrailsApplied: String,
        segment: String,
        segmentConfidence: Double,
        scenario: String,
        isHoldout: Int,
        policyVersion: String
    ): Map<String, Any> = mapOf(
        "decision_id" to decisionId,
        "decision_point" to decisionPoint,
        "chosen_action_id" to chosenActionId,
        "chosen_action_mode" to chosenActionMode,
        "chosen_action_probability" to chosenActionProbability,
        "eligible_actions" to eligibleActions,
        "guardrails_applied" to guardrailsApplied,
        "segment" to segment,
        "segment_confidence" to segmentConfidence,
        "scenario" to scenario,
        "is_holdout" to isHoldout,
        "policy_version" to policyVersion,
        "screen_name" to ScreenName.name
    )

    /**
     * Doanh thu IAP, **tách theo kênh** (doc 26 mục C.2).
     *
     * Gửi `price_micros` + `currency_code` NGUYÊN BẢN của store, không quy đổi sẵn sang USD:
     * client không có tỉ giá đáng tin, server thì có. Quy đổi tại client rồi gửi đi là cách
     * chắc chắn nhất để nhét tỉ giá cũ vào báo cáo tài chính mà không ai truy ngược được.
     *
     * `channel` để sẵn cho web-shop/D2P (doc 18) — biên lợi nhuận hai kênh chênh nhau rất
     * lớn (~70% so với ~92% net), gộp một số thì không đánh giá được payment routing.
     */
    public fun logIapPurchase(
        productId: String,
        orderId: String,
        priceMicros: Long,
        currencyCode: String,
        quantity: Int,
        channel: String,
        isSubscription: Boolean,
        purchaseToken: String = "",
        onResult: ((Boolean) -> Unit)? = null
    ) {
        logEventToServer(
            EventNames.IAP_PURCHASE,
            iapPurchaseEventData(
                productId, orderId, priceMicros, currencyCode, quantity, channel,
                isSubscription, purchaseToken
            ),
            onResult
        )
    }

    /**
     * Payload của `iap_purchase` — tách thuần để unit test khoá được từng trường mà không
     * cần khởi tạo LogEventServer.
     *
     * `purchase_token` (doc 45 mục E.1): biên nhận Play Billing gửi kèm để backend có ĐẦU
     * VÀO mà xác thực giao dịch phía server — không có nó thì không bao giờ đóng được
     * khoảng trống "quyền lợi tin vào những gì Play trả về trên máy". Đây là biên nhận mua
     * hàng (receipt), KHÔNG phải thông tin phiên như URL checkout — gửi về server của chính
     * studio là luồng chuẩn của mọi hệ thống xác thực IAP; và nó chỉ đi trong pipeline log
     * của studio, không bao giờ sang bên thứ ba (AppsFlyer chỉ nhận doanh thu). Kênh web-shop
     * không có token Play — để chuỗi rỗng, server phân biệt qua `channel`.
     */
    internal fun iapPurchaseEventData(
        productId: String,
        orderId: String,
        priceMicros: Long,
        currencyCode: String,
        quantity: Int,
        channel: String,
        isSubscription: Boolean,
        purchaseToken: String
    ): Map<String, Any> = mapOf(
        "product_id" to productId,
        "order_id" to orderId,
        "price_micros" to priceMicros,
        "currency_code" to currencyCode,
        "quantity" to quantity,
        "channel" to channel,
        "is_subscription" to if (isSubscription) 1 else 0,
        "purchase_token" to purchaseToken,
        "screen_name" to ScreenName.name
    )

    /**
     * ComfortIndex — North Star Metric thứ tư (doc 26 mục C.4), chốt mỗi cuối phiên.
     *
     * `coverage` là trường BẮT BUỘC, không phải metadata phụ: hiện chỉ 2/5 thành phần của công
     * thức có dữ liệu. Gửi `value` mà không gửi `coverage` sẽ khiến dashboard vẽ một đường
     * trông như đã đo được sự thoải mái của người chơi, trong khi ba tín hiệu quan trọng nhất
     * (organic purchase probability, refund rate, retention sau mua) còn chưa tồn tại.
     *
     * `policy_aggressiveness` đi cùng nhưng KHÔNG nằm trong công thức — guardrail nổ nhiều là
     * tín hiệu về sức khoẻ CHÍNH SÁCH, không phải về trải nghiệm người chơi (họ đã được bảo vệ).
     * Gộp hai thứ làm cả hai mất ý nghĩa; xem đính chính ở đầu ComfortIndex.kt.
     */
    public fun logBrainComfort(
        value: Double,
        coverage: Double,
        componentsMissing: String,
        policyAggressiveness: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "comfort_value" to value,
            "comfort_coverage" to coverage,
            "components_missing" to componentsMissing,
            "policy_aggressiveness" to policyAggressiveness,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.BRAIN_COMFORT, eventData, onResult)
    }

    /**
     * Lượt hiện paywall — MẪU SỐ của tỉ lệ chuyển đổi.
     *
     * Không có event này thì "bao nhiêu lượt mua" là một con số trần trụi không so sánh được
     * giữa các biến thể: biến thể được hiện nhiều hơn tất nhiên bán được nhiều hơn.
     */
    public fun logPaywallImpression(
        placement: String,
        variantId: String,
        productCount: Int,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "placement" to placement,
            "variant_id" to variantId,
            "product_count" to productCount,
            "paywall_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.PAYWALL_IMPRESSION, eventData, onResult)
    }

    /**
     * Lượt bỏ qua của nhóm đối chứng paywall — nửa kia của `logPaywallImpression`.
     *
     * `counterfactualVariantId` là biến thể người này LẼ RA đã thấy nếu không thuộc nhóm đối
     * chứng. Có nó thì phép so sánh không dừng ở "có paywall vs không" mà còn trả lời được
     * cho từng biến thể: biến thể A giữ lại 100 người thì mất bao nhiêu lượt mua so với khi
     * hiện nó.
     */
    public fun logPaywallHoldoutSkip(
        placement: String,
        counterfactualVariantId: String,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "placement" to placement,
            "counterfactual_variant_id" to counterfactualVariantId,
            "paywall_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.PAYWALL_HOLDOUT_SKIP, eventData, onResult)
    }

    /**
     * Bản ghi phân nhóm thí nghiệm — phát MỖI phiên một lần cho MỌI player khi thí nghiệm
     * đang chạy (doc 23 Phase 4 mục 3).
     *
     * Khác với mọi event khác chỉ phát khi có hành vi (hiện ads, mở paywall, mua...), event
     * này phát cho cả người KHÔNG LÀM GÌ: nhóm đối chứng và người ngoài rollout chỉ tồn tại
     * trong dữ liệu nếu hàng membership của họ được ghi lại. Không có nó, phép so sánh
     * treatment vs holdout chỉ phủ được những người đã chạm vào phễu mua — tức là đo trên
     * tập đã tự chọn lọc.
     *
     * `bucket` + `saltVersion` đi kèm để server tái tạo được membership mà không tin mù quáng
     * cờ client gửi lên: cùng (salt, user_id) phải ra cùng bucket, lệch là biết log hỏng.
     *
     * Không dedup_key: mỗi phiên là một lần phơi nhiễm thật, và đây là hàng duy nhất ghi
     * bucket + salt tại thời điểm đó — đổi muối giữa chừng mà gộp các phiên bằng khoá tự chế
     * thì mất dấu chu kỳ đo.
     */
    public fun logExperimentExposure(
        experimentId: String,
        holdoutBucket: Int,
        isHoldout: Boolean,
        inRollout: Boolean,
        saltVersion: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "experiment_id" to experimentId,
            "holdout_bucket" to holdoutBucket,
            "is_holdout" to if (isHoldout) 1 else 0,
            "in_rollout" to if (inRollout) 1 else 0,
            "salt_version" to saltVersion,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.EXPERIMENT_EXPOSURE, eventData, onResult)
    }

    /**
     * Người được router dẫn ra web shop — tử số của phễu routing đa kênh (doc 18 mục 6.7).
     * Ghép với `iap_purchase` channel WEB_SHOP (về sau, do server xác nhận) để đo tỉ lệ chuyển
     * đổi theo kênh: mở checkout bao nhiêu, hoàn tất bao nhiêu.
     *
     * CỐ Ý không gửi URL: template của studio có thể nhúng session token — gửi nó vào hàng đợi
     * log là phát tán một bí mật phiên ra ngoài hệ thống thanh toán.
     */
    public fun logWebCheckoutOpened(
        productId: String,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "product_id" to productId,
            "channel" to "WEB_SHOP",
            "routing_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.WEB_CHECKOUT_OPENED, eventData, onResult)
    }

    /**
     * Chuyển đổi trạng thái vòng đời subscription (doc 23 mục C.3). Một event, cặp `from_state`
     * → `to_state` phủ toàn bộ taxonomy của doc 19 mục B: ACTIVE→IN_GRACE_PERIOD là "vào grace
     * period", IN_GRACE_PERIOD→ACTIVE là "billing issue resolved" (dunning thành công),
     * ACTIVE→CANCELLED là "cancelled", *→LAPSED là "expired/churned", và LAPSED→ACTIVE là
     * win-back thành công. Chỉ phát khi trạng thái thật sự đổi.
     */
    public fun logSubscriptionState(
        productId: String,
        fromState: String,
        toState: String,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "product_id" to productId,
            "from_state" to fromState,
            "to_state" to toState,
            "lifecycle_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.SUBSCRIPTION_STATE, eventData, onResult)
    }

    /**
     * Vượt bậc battle pass (doc 23 mục C.3). Chỉ phát khi bậc THẬT SỰ tăng — đo funnel
     * tiến độ theo mùa và so sánh tốc độ lên bậc giữa các cấu hình catch-up.
     */
    public fun logBattlePassTier(
        seasonId: String,
        fromTier: Int,
        toTier: Int,
        isPremium: Boolean,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "season_id" to seasonId,
            "from_tier" to fromTier,
            "to_tier" to toTier,
            "is_premium" to if (isPremium) 1 else 0,
            "battle_pass_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.BATTLE_PASS_TIER, eventData, onResult)
    }

    /**
     * Claim phần thưởng battle pass. Đo chuyển đổi của track premium (claim premium của
     * người không mua pass = 0 về định nghĩa, nên `is_premium` ở đây là cờ sở hữu).
     */
    public fun logBattlePassClaim(
        seasonId: String,
        tier: Int,
        track: String,
        rewardCount: Int,
        isPremium: Boolean,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "season_id" to seasonId,
            "tier" to tier,
            "track" to track,
            "reward_count" to rewardCount,
            "is_premium" to if (isPremium) 1 else 0,
            "battle_pass_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.BATTLE_PASS_CLAIM, eventData, onResult)
    }

    /**
     * Một dòng phát định kỳ (VIP) được nhận (doc 19: "entitlement = 500 gems/ngày").
     * Đo tần suất dùng đặc quyền — tín hiệu giữ subscription mạnh hơn cả lý do mua.
     */
    public fun logRecurringGrantClaim(
        productId: String,
        rewardType: String,
        rewardAmount: Long,
        periodHours: Long,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "product_id" to productId,
            "reward_type" to rewardType,
            "reward_amount" to rewardAmount,
            "period_hours" to periodHours,
            "recurring_grants_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.RECURRING_GRANT_CLAIM, eventData, onResult)
    }

    /**
     * Lượt mở tường offerwall (doc 20) — mẫu số của tỉ lệ chuyển đổi sang completion.
     * Không có nó thì số completion không so được giữa các provider: provider được mở
     * nhiều hơn tất nhiên hoàn thành nhiều hơn.
     */
    public fun logOfferwallImpression(
        providerId: String,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "provider_id" to providerId,
            "offerwall_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.OFFERWALL_IMPRESSION, eventData, onResult)
    }

    /**
     * Offer hoàn thành — tử số của phễu offerwall. Kèm `transaction_id` của vendor để
     * chống trùng phía backend (callback S2S hay retry). SDK đã chặn trùng ở nguồn,
     * dedup_key phía server là lớp phòng thủ thứ hai.
     */
    public fun logOfferwallCompleted(
        providerId: String,
        currencyEarned: Long,
        transactionId: String,
        version: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val eventData: Map<String, Any> = mapOf(
            "provider_id" to providerId,
            "currency_earned" to currencyEarned,
            "transaction_id" to transactionId,
            "offerwall_version" to version,
            "screen_name" to ScreenName.name
        )
        logEventToServer(EventNames.OFFERWALL_COMPLETED, eventData, onResult)
    }

    /** Outcome về sau, khớp lại với decision qua `decision_id`. */
    /**
     * @param rewardValue reward 0..1 của [RewardModel] tính tại thời điểm chốt quyết định —
     *   chính con số bandit dùng để học. Gửi kèm nguyên bản để backend làm off-policy
     *   evaluation (doc 46 mục B.1: propensity đã log mà thiếu reward thì mọi estimator
     *   IPS/DR đều vô hiệu). `null` = KHÔNG gửi trường `reward_value` — để backend phân
     *   biệt "thiếu" với 0.0; bản ghi cũ và feedback chưa chốt được reward giữ hành vi cũ.
     */
    public fun logBrainFeedback(
        decisionId: String,
        event: String,
        timestampMs: Long,
        rewardValue: Double? = null,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        logEventToServer(
            EventNames.BRAIN_FEEDBACK,
            brainFeedbackEventData(decisionId, event, timestampMs, rewardValue),
            onResult
        )
    }

    /**
     * Payload của event `brain_feedback`. Tách riêng để khoá bằng test mà không cần mạng:
     * hợp đồng `reward_value` — có mặt khi chốt được reward, VẮNG MẶT khi `null` (không phải
     * 0.0) — là thứ backend dựa vào để phân biệt "thiếu" với "reward bằng không" khi tính
     * off-policy evaluation.
     */
    internal fun brainFeedbackEventData(
        decisionId: String,
        event: String,
        timestampMs: Long,
        rewardValue: Double? = null
    ): Map<String, Any> {
        val eventData: MutableMap<String, Any> = mutableMapOf(
            "decision_id" to decisionId,
            "outcome_event" to event,
            "outcome_timestamp_ms" to timestampMs,
            "screen_name" to ScreenName.name
        )
        if (rewardValue != null) eventData["reward_value"] = rewardValue
        return eventData
    }

    public fun sessionStart() {
        val timeOpen = PreferenceHelper.getInstance().get(Constants.Common.TIME_OPEN, 0)
        if (timeOpen == 1) {
            logFirstOpen()
        }
        logSessionStart()
    }

}