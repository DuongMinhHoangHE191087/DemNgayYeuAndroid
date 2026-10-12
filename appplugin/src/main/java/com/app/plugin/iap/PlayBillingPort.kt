package com.app.plugin.iap

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.app.plugin.until.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Port thật: Play Billing 9. Thân chép nguyên văn từ `IapHelper` 1.7.0 (`buildAndConnect`, `connect`, `usable`,
 * `queryFor`, `toIapProduct`, phần store của `purchase`/`queryOwned`/`acknowledgeIfNeeded`), kể cả chữ log
 * "IapHelper …". Không tự chuyển luồng: mỗi lệnh chạy trên luồng gọi, callback về luồng của thư viện.
 */
internal class PlayBillingPort private constructor(private val app: Context?, client: BillingClient?) : BillingPort {

    /** Đường `IapHelper.init`: `connect` dựng `BillingClient` (trên luồng "appplugin-billing"). */
    constructor(app: Context) : this(app, null)

    /** Bọc một `BillingClient` có sẵn (test: mock); `connect` chỉ `startConnection`. */
    constructor(client: BillingClient) : this(null, client)

    /** Ghi trên luồng "appplugin-billing", đọc trên main (`isReady`, `purchase`, `restorePurchases`). */
    @Volatile private var client: BillingClient? = client

    /** Đặt SAU khi `startConnection` đã gọi: `client` được gán trước đó một nhịp trên luồng billing (F2B). */
    private val connectionStarted = AtomicBoolean(false)

    /**
     * F7/D11 (1.8.0 Task 5): kết nối trả `BILLING_UNAVAILABLE` (máy không có Play, API 26/28 `google_apis`) → một dòng log,
     * `usable()` false và không lệnh store nào nữa trong process (mỗi lệnh từng làm thư viện thử kết nối lại và log trên main).
     */
    private val unavailable = AtomicBoolean(false)

    /** Client còn gọi được store; `null` khi chưa dựng hoặc đã `BILLING_UNAVAILABLE` ([missing] = mã báo lại). */
    private fun live(): BillingClient? = if (unavailable.get()) null else client
    private fun missing(): Int = if (unavailable.get()) BillingPort.BILLING_UNAVAILABLE else NO_CLIENT

    /** `enableAutoServiceReconnection()` (billing v8+) thay vòng retry tự viết (retry tay dễ thành vòng lặp tiêu pin). */
    override fun connect(onSetup: (PortSetup) -> Unit) {
        val ctx = app
        if (client == null && ctx != null) {
            try {
                client = BillingClient.newBuilder(ctx)
                    .setListener { result, purchases ->
                        IapHelper.onPortPurchasesUpdated(result.responseCode, purchases?.map(::toPortPurchase), result.debugMessage)
                    }
                    .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                            .enableOneTimeProducts()
                            .enablePrepaidPlans()
                            .build()
                    )
                    .enableAutoServiceReconnection()
                    .build()
            } catch (e: Exception) {
                LogUtil.logE("IapHelper init failed: ${e.message}")
                return
            }
        }
        startConnection(onSetup)
    }

    private fun startConnection(onSetup: (PortSetup) -> Unit) {
        val client = client ?: return
        val started = connectionStarted // bắt vào biến cục bộ: listener ẩn danh không cần accessor tổng hợp (dump API)
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    when (BillingSetupOutcome.of(result.responseCode, client.connectionState)) {
                        BillingSetupOutcome.CONNECTED -> {
                            started.set(true) // callback có thể tới trước dòng đặt cờ sau startConnection
                            onSetup(PortSetup.CONNECTED)
                        }
                        BillingSetupOutcome.STILL_CONNECTING -> onSetup(PortSetup.STILL_CONNECTING)
                        BillingSetupOutcome.FAILED -> {
                            val code = result.responseCode
                            val message = result.debugMessage
                            if (code != BillingPort.BILLING_UNAVAILABLE) {
                                LogUtil.logE("IapHelper setup failed: $code $message")
                                onSetup(PortSetup.FAILED(code, message))
                            } else if (unavailable.compareAndSet(false, true)) {
                                LogUtil.logE("IapHelper unavailable: $code (không thử lại trong process) $message")
                                onSetup(PortSetup.UNAVAILABLE(code, message))
                            }
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    LogUtil.logD("IapHelper disconnected — auto reconnection sẽ tự xử lý")
                }
            })
        } catch (e: Exception) {
            LogUtil.logE("IapHelper connect failed: ${e.message}")
        }
        started.set(true)
    }

    /**
     * Đã `startConnection` và không đang kết nối. Không dùng `BillingClient.isReady`: có
     * `enableAutoServiceReconnection()` nó luôn true, kể cả lúc kết nối đầu còn chạy (F2B, bytecode `billing:9.0.0`).
     * DISCONNECTED vẫn là sẵn sàng: lời gọi kế tiếp tự kết nối lại.
     */
    override fun usable(): Boolean {
        val client = live() ?: return false
        return connectionStarted.get() &&
            client.connectionState.let { it != BillingClient.ConnectionState.CONNECTING && it != BillingClient.ConnectionState.CLOSED }
    }

    override fun queryProducts(type: IapProductType, ids: List<String>, onDone: (code: Int, List<PortOffer>, message: String) -> Unit) {
        val client = live() ?: return onDone(missing(), emptyList(), "")
        val params = productParams(ids, storeTypeOf(type))
        try {
            client.queryProductDetailsAsync(params) { result, queryResult ->
                val offers = mutableListOf<PortOffer>()
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (details in queryResult.productDetailsList) {
                        toPortOffer(details, type)?.let { offers += it }
                    }
                    LogUtil.logD("IapHelper products query code=${result.responseCode} n=${offers.size} type=$type") // Task R #12; R-T11 m5
                } else {
                    LogUtil.logE("IapHelper queryProducts($type) failed: ${result.responseCode}")
                }
                onDone(result.responseCode, offers, result.debugMessage)
            }
        } catch (e: Exception) {
            LogUtil.logE("IapHelper queryProducts($type) crashed: ${e.message}")
            onDone(NO_CLIENT, emptyList(), e.message ?: "")
        }
    }

    private fun toPortOffer(details: ProductDetails, type: IapProductType): PortOffer? {
        return try {
            if (type == IapProductType.SUBS) {
                // Lấy phase tính phí ĐẦU TIÊN có giá > 0. Phase đầu tiên nói chung có thể là
                // free trial giá 0 — báo cáo doanh thu theo giá đó sẽ ghi mọi subscription
                // thành 0 đồng.
                val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return null
                val phase = offer.pricingPhases.pricingPhaseList
                    .firstOrNull { it.priceAmountMicros > 0 }
                    ?: offer.pricingPhases.pricingPhaseList.firstOrNull()
                    ?: return null
                PortOffer(
                    productId = details.productId,
                    type = type,
                    basePriceMicros = phase.priceAmountMicros,
                    currency = phase.priceCurrencyCode,
                    formatted = phase.formattedPrice,
                    title = details.title,
                    description = details.description,
                    offers = details.subscriptionOfferDetails.orEmpty().map { o ->
                        PortOfferPhase(
                            token = o.offerToken,
                            tag = o.offerTags.firstOrNull() ?: "",
                            phases = o.pricingPhases.pricingPhaseList.map {
                                PortPhase(it.priceAmountMicros, it.priceCurrencyCode, it.billingCycleCount)
                            }
                        )
                    }
                )
            } else {
                val offer = details.oneTimePurchaseOfferDetails ?: return null
                PortOffer(
                    productId = details.productId,
                    type = type,
                    basePriceMicros = offer.priceAmountMicros,
                    currency = offer.priceCurrencyCode,
                    formatted = offer.formattedPrice,
                    title = details.title,
                    description = details.description,
                    offers = emptyList()
                )
            }
        } catch (e: Exception) {
            LogUtil.logE("IapHelper map product ${details.productId} failed: ${e.message}")
            null
        }
    }

    /**
     * Phải lấy lại `ProductDetails` tươi: `launchBillingFlow` yêu cầu đúng đối tượng do billing library cấp, không
     * nhận model tự dựng. Loại sản phẩm suy từ `offerToken`: chỉ SUBS có offer (`toPortOffer`: INAPP `offers` rỗng →
     * `IapProduct.offerToken` = "") — trùng `product.type` của mã cũ. `onLaunch` chỉ khi KHÔNG mở được luồng mua, với mã và
     * `debugMessage` của store (store OK mà không có sản phẩm → `ITEM_UNAVAILABLE`, T5/D20 C2); mở được thì không gọi.
     */
    override fun launch(activity: Activity, productId: String, offerToken: String, onLaunch: (code: Int, message: String) -> Unit) {
        val client = live() ?: return onLaunch(missing(), "")
        val storeType = if (offerToken.isNotEmpty()) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        client.queryProductDetailsAsync(productParams(listOf(productId), storeType)) { result, queryResult ->
            val details = queryResult.productDetailsList.firstOrNull()
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details == null) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                onLaunch(if (ok) BillingClient.BillingResponseCode.ITEM_UNAVAILABLE else result.responseCode, result.debugMessage)
                return@queryProductDetailsAsync
            }
            val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
            if (offerToken.isNotEmpty()) builder.setOfferToken(offerToken)
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(builder.build()))
                .build()
            val launch = client.launchBillingFlow(activity, flow)
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                onLaunch(launch.responseCode, launch.debugMessage)
            }
        }
    }

    override fun queryOwned(type: IapProductType, onDone: (ok: Boolean, List<PortPurchase>, message: String) -> Unit) {
        val client = live() ?: return onDone(false, emptyList(), "")
        val storeType = storeTypeOf(type)
        try {
            val params = QueryPurchasesParams.newBuilder().setProductType(storeType).build()
            client.queryPurchasesAsync(params) { result, purchases ->
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                onDone(ok, if (ok) purchases.map(::toPortPurchase) else emptyList(), result.debugMessage)
            }
        } catch (e: Exception) {
            LogUtil.logE("IapHelper queryOwned($storeType) crashed: ${e.message}")
            onDone(false, emptyList(), e.message ?: "")
        }
    }

    override fun acknowledge(token: String, onDone: (code: Int, message: String) -> Unit) {
        val client = live() ?: return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        ) { r -> onDone(r.responseCode, r.debugMessage) }
    }

    override fun consume(token: String, onDone: (code: Int, message: String) -> Unit) {
        val client = live() ?: return
        client.consumeAsync(
            ConsumeParams.newBuilder().setPurchaseToken(token).build()
        ) { r, _ -> onDone(r.responseCode, r.debugMessage) }
    }

    override fun showInAppMessages(activity: Activity, onResult: (subscriptionUpdated: Boolean) -> Unit) {
        val client = live() ?: return onResult(false)
        val params = InAppMessageParams.newBuilder()
            .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL)
            .build()
        client.showInAppMessages(activity, params) { r ->
            onResult(r.responseCode == InAppMessageResult.InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED)
        }
    }

    override fun end() {
        client?.endConnection()
    }

    private fun storeTypeOf(type: IapProductType): String =
        if (type == IapProductType.SUBS) BillingClient.ProductType.SUBS
        else BillingClient.ProductType.INAPP

    private fun productParams(ids: List<String>, storeType: String): QueryProductDetailsParams =
        QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(storeType)
                        .build()
                }
            )
            .build()

    private fun toPortPurchase(p: Purchase): PortPurchase = PortPurchase(
        products = p.products,
        orderId = p.orderId ?: "",
        token = p.purchaseToken,
        timeMs = p.purchaseTime,
        state = when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> IapPurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> IapPurchaseState.PENDING
            else -> IapPurchaseState.UNSPECIFIED
        },
        acknowledged = p.isAcknowledged,
        autoRenewing = p.isAutoRenewing,
        quantity = p.quantity,
        suspended = p.isSuspended
    )

    private companion object {
        /** Không có client / lời gọi store ném lỗi (đã log): mã SDK -1 như `IapResult.Failed(-1, …)`. */
        const val NO_CLIENT = -1
    }
}
