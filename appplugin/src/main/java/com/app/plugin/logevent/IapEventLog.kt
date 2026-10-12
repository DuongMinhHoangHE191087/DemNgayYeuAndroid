package com.app.plugin.logevent

import com.app.plugin.iap.PaywallSession

/**
 * Event schema v2 của IAP và paywall (1.8.0 Task 7, plan "Hợp đồng E"): một chỗ dựng `eventData` của `iap_purchase`,
 * `iap_result`, `paywall_impression`, `paywall_holdout_skip`, `paywall_skip`, `paywall_choice`, `subscription_state`.
 * Như [AdEventLog]: builder map thuần `…Data` (test hợp đồng đọc) và hàm gửi qua [LogEventTransport.sendEvent]; key
 * snake_case ≤ 40 ký tự; không I/O. Cả bảy event thuộc `DurableEvents.DURABLE` và không lấy mẫu (D7).
 *
 * Không có `usd` (D3): `price_micros` (số thực thu) + `currency_code`, server quy đổi. `LogEventServer.logIapPurchase`
 * (public) giữ chữ ký v1, không đổi; chỗ gọi v2 (`IapHelper`, Task 5) gọi [iapPurchase] ở đây.
 */
internal object IapEventLog {
    /** `phase` của `iap_purchase`: giai đoạn tính tiền thật. */
    const val PHASE_TRIAL = "trial"
    const val PHASE_INTRO = "intro"
    const val PHASE_BASE = "base"
    const val PHASE_INAPP = "inapp"

    /** `outcome` của `iap_result`. */
    const val OUTCOME_SUCCESS = "success"
    const val OUTCOME_CANCEL = "cancel"
    const val OUTCOME_PENDING = "pending"
    const val OUTCOME_FAILED = "failed"
    const val OUTCOME_ALREADY_OWNED = "already_owned"
    const val OUTCOME_ACK_FAILED = "ack_failed"

    /** `reason` của `paywall_skip` (nhánh `null` khác holdout). */
    const val SKIP_SESSION_CAP = "session_cap"
    const val SKIP_BRAIN = "brain"
    const val SKIP_NO_CONFIG = "no_config"

    /** `source` của `subscription_state`. */
    const val SOURCE_CLIENT = "client"
    const val SOURCE_SERVER = "server"

    // ── Builder thuần ────────────────────────────────────────────────────────────────────────────

    /**
     * [priceMicros] = số thực thu (0 với trial). [paywall] = phiên paywall đang mở lúc mua; ngoài paywall thì
     * `paywall_session_id`, `placement`, `variant_id` rỗng. [offerTag] rỗng nếu không có ưu đãi.
     */
    fun iapPurchaseData(
        productId: String, orderId: String, priceMicros: Long, currencyCode: String, quantity: Int, channel: String,
        isSubscription: Boolean, purchaseToken: String, phase: String, offerTag: String, paywall: PaywallSession.Snapshot?,
        screenName: String
    ): Map<String, Any> = mapOf(
        "product_id" to productId,
        "order_id" to orderId,
        "price_micros" to priceMicros,
        "currency_code" to currencyCode,
        "quantity" to quantity,
        "channel" to channel,
        "is_subscription" to if (isSubscription) 1 else 0,
        "purchase_token" to purchaseToken,
        "screen_name" to screenName,
        "phase" to phase,
        "offer_tag" to offerTag,
        "paywall_session_id" to (paywall?.sessionId ?: ""),
        "placement" to (paywall?.placement ?: ""),
        "variant_id" to (paywall?.variantId ?: "")
    )

    /** [outcome] một trong `OUTCOME_*`; [responseCode] = `BillingResponseCode` (0 = OK). */
    fun iapResultData(outcome: String, responseCode: Int, productId: String, paywallSessionId: String): Map<String, Any> = mapOf(
        "outcome" to outcome,
        "response_code" to responseCode,
        "product_id" to productId,
        "paywall_session_id" to paywallSessionId
    )

    fun paywallImpressionData(
        placement: String, variantId: String, productCount: Int, version: String, paywallSessionId: String,
        schemaVersionCfg: Int, screenName: String
    ): Map<String, Any> = mapOf(
        "placement" to placement,
        "variant_id" to variantId,
        "product_count" to productCount,
        "paywall_version" to version,
        "screen_name" to screenName,
        "paywall_session_id" to paywallSessionId,
        "schema_version_cfg" to schemaVersionCfg
    )

    fun paywallHoldoutSkipData(
        placement: String, counterfactualVariantId: String, version: String, paywallSessionId: String, screenName: String
    ): Map<String, Any> = mapOf(
        "placement" to placement,
        "counterfactual_variant_id" to counterfactualVariantId,
        "paywall_version" to version,
        "screen_name" to screenName,
        "paywall_session_id" to paywallSessionId
    )

    /** [reason] một trong `SKIP_*`. */
    fun paywallSkipData(placement: String, reason: String): Map<String, Any> =
        mapOf("placement" to placement, "reason" to reason)

    fun paywallChoiceData(paywallSessionId: String, placement: String, variantId: String, productId: String): Map<String, Any> = mapOf(
        "paywall_session_id" to paywallSessionId,
        "placement" to placement,
        "variant_id" to variantId,
        "product_id" to productId
    )

    /** [source] `SOURCE_CLIENT` (lifecycle của máy) hoặc `SOURCE_SERVER` (tín hiệu server). */
    fun subscriptionStateData(
        productId: String, fromState: String, toState: String, version: String, source: String, screenName: String
    ): Map<String, Any> = mapOf(
        "product_id" to productId,
        "from_state" to fromState,
        "to_state" to toState,
        "lifecycle_version" to version,
        "screen_name" to screenName,
        "source" to source
    )

    // ── Gửi ──────────────────────────────────────────────────────────────────────────────────────

    private fun send(eventName: String, eventData: Map<String, Any>) = LogEventTransport.sendEvent(eventName, eventData, null)

    /** `iap_purchase` v2; phiên paywall lấy từ [PaywallSession.current]. Dựng body và envelope trên IO như mọi event. */
    fun iapPurchase(
        productId: String, orderId: String, priceMicros: Long, currencyCode: String, quantity: Int, channel: String,
        isSubscription: Boolean, purchaseToken: String, phase: String, offerTag: String
    ) = send(
        EventNames.IAP_PURCHASE,
        iapPurchaseData(
            productId, orderId, priceMicros, currencyCode, quantity, channel, isSubscription, purchaseToken, phase, offerTag,
            PaywallSession.current(), ScreenName.name
        )
    )

    fun iapResult(outcome: String, responseCode: Int, productId: String) =
        send(EventNames.IAP_RESULT, iapResultData(outcome, responseCode, productId, PaywallSession.current()?.sessionId ?: ""))

    fun paywallImpression(placement: String, variantId: String, productCount: Int, version: String, paywallSessionId: String, schemaVersionCfg: Int) =
        send(
            EventNames.PAYWALL_IMPRESSION,
            paywallImpressionData(placement, variantId, productCount, version, paywallSessionId, schemaVersionCfg, ScreenName.name)
        )

    fun paywallHoldoutSkip(placement: String, counterfactualVariantId: String, version: String, paywallSessionId: String) =
        send(
            EventNames.PAYWALL_HOLDOUT_SKIP,
            paywallHoldoutSkipData(placement, counterfactualVariantId, version, paywallSessionId, ScreenName.name)
        )

    fun paywallSkip(placement: String, reason: String) = send(EventNames.PAYWALL_SKIP, paywallSkipData(placement, reason))

    /**
     * `IapHelper.purchase()` vừa mở màn mua của [productId]: nếu phiên paywall còn mở thì phát `paywall_choice` và trả
     * `true`; ngoài paywall thì không phát gì (trả `false`).
     */
    fun onPurchaseLaunched(productId: String): Boolean {
        val s = PaywallSession.current() ?: return false
        send(EventNames.PAYWALL_CHOICE, paywallChoiceData(s.sessionId, s.placement, s.variantId, productId))
        return true
    }

    fun subscriptionState(productId: String, fromState: String, toState: String, version: String, source: String) =
        send(EventNames.SUBSCRIPTION_STATE, subscriptionStateData(productId, fromState, toState, version, source, ScreenName.name))
}
