package com.app.plugin.iap

import com.app.plugin.ads.RemoveAdsMask

/**
 * Bảng kịch bản của harness G12 (1.8.0 Task 8, §Hợp đồng B): **12** script, tên cố định (T16 `iap_gate.sh` gọi đúng tên
 * này qua `--es appplugin_iap_script <tên>`). Chỉ [IapHarness] đọc bảng này, và chỉ khi đủ 3 khoá: app không bật harness
 * không bao giờ nạp lớp này.
 *
 * Mỗi script là dữ liệu: port kịch bản ([ScriptedBillingPort]) trả lời theo bảng, còn mọi quyết định (cấp quyền lợi,
 * ack, sổ token, `iap_result`, vòng đời) là của state machine thật trong `IapHelper`. Catalog riêng của harness
 * ([SUB_ID] mask ALL, [INAPP_ID] mask INTERRUPTIVE, không consumable) thay danh sách sản phẩm của app khi đã arm.
 *
 * Script #5 có hai pha: `kill_mid_purchase` (mở màn mua, không bao giờ có callback; T16 `am force-stop`) rồi mở lại
 * với `restore_unacked` (pha 2 của cùng script — cùng token, `queryOwned` = PURCHASED chưa ack).
 * Script #10/#12 dùng token của fixture RTDN [RTDN_FIXTURE_TOKEN] (T13 `backend/tests/fixtures/rtdn`, tiền điều kiện T16).
 */
internal object IapScripts {
    const val BUY_SUB = "buy_sub"
    const val BUY_CHEAP = "buy_cheap"
    const val CANCEL = "cancel"
    const val PENDING_THEN_PAID = "pending_then_paid"
    const val KILL_MID_PURCHASE = "kill_mid_purchase"
    const val RESTORE_OWNED = "restore_owned"
    const val REOPEN = "reopen"
    const val RESTORE_STORE_ERROR = "restore_store_error"
    const val REVOKE = "revoke"
    const val GRACE = "grace"
    const val DENIED = "denied"
    const val MESSAGES_OFF = "messages_off"

    const val SUB_ID = "g12_sub"
    const val INAPP_ID = "g12_lite"
    const val RTDN_FIXTURE_TOKEN = "rtdn-fixture-sub-token"
    const val PAYWALL_PLACEMENT = "g12_paywall"
    const val PAYWALL_VARIANT = "g12_v1"
    const val PAYWALL_VERSION = "g12_demo"

    /** Cấu hình paywall demo cố định (script 1, 10, 11, 12): schema v2, holdout 0, placement người dùng mở. */
    const val PAYWALL_CONFIG = """{"schema_version":2,"version":"$PAYWALL_VERSION","holdout":{"fraction":0.0},""" +
        """"placements":{"$PAYWALL_PLACEMENT":{"decision_point":"SHOP_OPEN","proactive":false,"variants":""" +
        """[{"id":"$PAYWALL_VARIANT","weight":1,"products":["$SUB_ID","$INAPP_ID"],"highlight":"$SUB_ID"}]}}}"""

    /** Cấu hình vòng đời cố định của script 12 (D2 tắt bằng cấu hình); `version` là trường bắt buộc của `apply`. */
    const val LIFECYCLE_MESSAGES_OFF = """{"version":"g12_messages_off","in_app_messages":{"enabled":false}}"""

    /** Trễ tất định của purchases-updated sau `launch`; PENDING → PURCHASED sau 5 s (script 4). */
    const val CALLBACK_DELAY_MS = 300L
    const val PENDING_TO_PAID_MS = 5_000L

    val MASKS: Map<String, Int> = mapOf(SUB_ID to RemoveAdsMask.ALL, INAPP_ID to RemoveAdsMask.INTERRUPTIVE)

    enum class Action { BUY, RESTORE, OBSERVE }

    /** Một lần purchases-updated sau `launch`: mã store, trạng thái giao dịch (`null` = không có giao dịch), độ trễ. */
    data class Update(val code: Int, val state: IapPurchaseState?, val afterMs: Long)

    /**
     * @property updates BUY: chuỗi purchases-updated sau `launch`; rỗng = không bao giờ callback (kill giữa lượt mua).
     * @property owned RESTORE: `queryOwned` trả [productId]/[token] với cờ acknowledged này; `null` = không sở hữu gì.
     * @property scripted `false` = giữ port Play thật (script 7, cold start).
     * @property inAppMessages harness cho `showInAppMessages` chạy (D2: im dưới harness trừ khi script yêu cầu).
     * @property resume pha 2 của script (mở lại sau `am force-stop`).
     */
    data class Script(
        val name: String,
        val action: Action,
        val productId: String = SUB_ID,
        val token: String = "g12-$name",
        val viaPaywall: Boolean = false,
        val updates: List<Update> = emptyList(),
        val owned: Boolean? = null,
        val ownedOk: Boolean = true,
        val scripted: Boolean = true,
        val lifecycleConfig: String? = null,
        val waitConsent: Boolean = false,
        val inAppMessages: Boolean = false,
        val resume: Script? = null,
    )

    private val PAID = listOf(Update(BillingPort.OK, IapPurchaseState.PURCHASED, CALLBACK_DELAY_MS))

    val ALL: List<Script> = listOf(
        Script(BUY_SUB, Action.BUY, viaPaywall = true, updates = PAID),
        Script(BUY_CHEAP, Action.BUY, productId = INAPP_ID, updates = PAID),
        Script(CANCEL, Action.BUY, updates = listOf(Update(BillingPort.USER_CANCELED, null, CALLBACK_DELAY_MS))),
        Script(
            PENDING_THEN_PAID, Action.BUY,
            updates = listOf(
                Update(BillingPort.OK, IapPurchaseState.PENDING, CALLBACK_DELAY_MS),
                Update(BillingPort.OK, IapPurchaseState.PURCHASED, CALLBACK_DELAY_MS + PENDING_TO_PAID_MS)
            )
        ),
        Script(
            KILL_MID_PURCHASE, Action.BUY,
            resume = Script("restore_unacked", Action.RESTORE, token = "g12-$KILL_MID_PURCHASE", owned = false)
        ),
        // Cùng token với buy_sub: đây là sub của script 1 khôi phục sau `pm clear` (backend khử trùng theo token).
        Script(RESTORE_OWNED, Action.RESTORE, token = "g12-$BUY_SUB", owned = true),
        Script(REOPEN, Action.OBSERVE, scripted = false),
        Script(RESTORE_STORE_ERROR, Action.RESTORE, owned = true, ownedOk = false),
        Script(REVOKE, Action.RESTORE),
        Script(GRACE, Action.BUY, token = RTDN_FIXTURE_TOKEN, viaPaywall = true, updates = PAID, inAppMessages = true),
        Script(DENIED, Action.BUY, viaPaywall = true, updates = PAID, waitConsent = true),
        Script(
            MESSAGES_OFF, Action.BUY, token = RTDN_FIXTURE_TOKEN, viaPaywall = true, updates = PAID,
            lifecycleConfig = LIFECYCLE_MESSAGES_OFF, inAppMessages = true
        ),
    )

    /** Tên ∈ bảng (kể cả tên pha 2); tên lạ → `null` (không arm). */
    fun find(name: String): Script? =
        ALL.firstOrNull { it.name == name } ?: ALL.firstNotNullOfOrNull { s -> s.resume?.takeIf { it.name == name } }

    fun typeOf(productId: String): IapProductType = if (productId == INAPP_ID) IapProductType.INAPP else IapProductType.SUBS

    /** Giá store của catalog harness: SUB 4,99 USD một offer base (pha `base`), INAPP 0,99 USD. */
    fun offers(type: IapProductType): List<PortOffer> = when (type) {
        IapProductType.SUBS -> listOf(
            PortOffer(
                SUB_ID, type, 4_990_000L, "USD", "US$4.99", "G12 Pro", "harness G12",
                listOf(PortOfferPhase("g12-offer-base", "", listOf(PortPhase(4_990_000L, "USD", 0))))
            )
        )
        IapProductType.INAPP -> listOf(PortOffer(INAPP_ID, type, 990_000L, "USD", "US$0.99", "G12 Lite", "harness G12", emptyList()))
    }
}
