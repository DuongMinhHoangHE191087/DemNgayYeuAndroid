package com.app.plugin.iap

import android.app.Activity
import com.android.billingclient.api.BillingClient

/**
 * Cổng tới store (1.8.0 Task 4, §Hợp đồng A): `IapHelper` giữ state machine, port giữ lời gọi store.
 *
 * Chỉ dùng model của SDK, không kiểu của Google: `ProductDetails`/`Purchase` không dựng được ngoài thư viện (F8),
 * nên port giả (test, harness G12) phải nói bằng `PortOffer`/`PortPurchase`. Mã kết quả là số nguyên theo bảng
 * `BillingClient.BillingResponseCode` của Play (hằng dưới đây); port khác ánh xạ về bảng đó. Mọi callback trả mã store
 * cũng trả `message` = `debugMessage` của store ("" khi không có) — `IapResult.Failed.message` như trước T4 (D20).
 *
 * Luồng: port không tự chuyển luồng; callback về trên luồng mà nguồn của port trả (thư viện Play: luồng của nó).
 */
internal interface BillingPort {
    fun connect(onSetup: (PortSetup) -> Unit)

    /** Gọi store được chưa (thay `IapHelper.usable(client)` cũ). */
    fun usable(): Boolean

    fun queryProducts(type: IapProductType, ids: List<String>, onDone: (code: Int, List<PortOffer>, message: String) -> Unit)

    /**
     * Mở luồng mua. `onLaunch(code, message)` chỉ khi KHÔNG mở được (mã store, kể cả 0 khi store OK mà không có sản
     * phẩm); mở được thì không gọi — kết quả mua về qua `IapHelper.onPortPurchasesUpdated`.
     */
    fun launch(activity: Activity, productId: String, offerToken: String, onLaunch: (code: Int, message: String) -> Unit)

    fun queryOwned(type: IapProductType, onDone: (ok: Boolean, List<PortPurchase>, message: String) -> Unit)

    fun acknowledge(token: String, onDone: (code: Int, message: String) -> Unit)

    fun consume(token: String, onDone: (code: Int, message: String) -> Unit)

    /** Hộp thư Play (`TRANSACTIONAL`); chỉ T11 dùng. */
    fun showInAppMessages(activity: Activity, onResult: (subscriptionUpdated: Boolean) -> Unit)

    /** `endConnection`. */
    fun end()

    companion object {
        /** Hằng biên dịch của Play (chép giá trị lúc build, không cần lớp billing lúc chạy). */
        const val OK = BillingClient.BillingResponseCode.OK
        const val USER_CANCELED = BillingClient.BillingResponseCode.USER_CANCELED
        const val BILLING_UNAVAILABLE = BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
        const val ITEM_ALREADY_OWNED = BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
    }
}

/** Kết quả `connect` (ánh xạ `BillingSetupOutcome` + `BILLING_UNAVAILABLE` = 3). */
internal sealed class PortSetup {
    object CONNECTED : PortSetup()
    object STILL_CONNECTING : PortSetup()
    data class UNAVAILABLE(val code: Int, val message: String) : PortSetup()
    data class FAILED(val code: Int, val message: String) : PortSetup()
}

/**
 * Một sản phẩm store trả về. `basePriceMicros`/`currency`/`formatted` = giá SDK báo cáo: INAPP = giá một lần;
 * SUBS = phase đầu có giá > 0 của offer đầu (không có thì phase đầu) — đúng quy tắc `toIapProduct` cũ.
 * `offers` giữ thứ tự store; rỗng với INAPP.
 */
internal data class PortOffer(
    val productId: String,
    val type: IapProductType,
    val basePriceMicros: Long,
    val currency: String,
    val formatted: String,
    val title: String,
    val description: String,
    val offers: List<PortOfferPhase>,
)

/** Một offer của SUBS: `token` để mua, `tag` = tag đầu của offer ("" khi không có), `phases` theo thứ tự tính phí. */
internal data class PortOfferPhase(val token: String, val tag: String, val phases: List<PortPhase>)

internal data class PortPhase(val priceMicros: Long, val currency: String, val cycles: Int)

/**
 * Một giao dịch store trả về. `suspended` (`Purchase.isSuspended()`, PBL 9.0.0, D18: đã mua nhưng không còn hiệu
 * lực — tạm dừng hoặc thanh toán bị từ chối) = như PENDING nhưng không bao giờ cấp quyền lợi.
 */
internal data class PortPurchase(
    val products: List<String>,
    val orderId: String,
    val token: String,
    val timeMs: Long,
    val state: IapPurchaseState,
    val acknowledged: Boolean,
    val autoRenewing: Boolean,
    val quantity: Int,
    val suspended: Boolean,
)
