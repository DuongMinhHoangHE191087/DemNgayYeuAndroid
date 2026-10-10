package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton quản lý toàn bộ vòng đời Google Play Billing Client v7.
 *
 * Trách nhiệm:
 *  - Kết nối [BillingClient] với cơ chế tự động retry khi mất kết nối.
 *  - Truy vấn danh sách sản phẩm từ Google Play Console (SUBS + INAPP).
 *  - Khôi phục giao dịch đã mua ([queryExistingPurchases]) khi mở app hoặc đổi máy.
 *  - Khởi chạy cửa sổ thanh toán Google Play ([launchPurchaseFlow]).
 *  - Lắng nghe kết quả thanh toán ([onPurchasesUpdated]) và xử lý Acknowledge bắt buộc.
 *
 * Nguyên tắc bất biến:
 *  - [isVipUser] là Single Source of Truth cho toàn bộ app về trạng thái VIP billing.
 *  - [acknowledgePurchase] PHẢI được gọi trong vòng 3 ngày — nếu không Google sẽ tự hoàn tiền.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Product IDs ─────────────────────────────────────────────────────────
    // Nguồn thật nằm ở VipProductIds — giữ alias ở đây để không phải sửa các nơi đã tham
    // chiếu BillingManager.PRODUCT_VIP_* (PaywallScreen, ViewModel).
    companion object {
        const val PRODUCT_VIP_MONTHLY = VipProductIds.MONTHLY
        const val PRODUCT_VIP_YEARLY = VipProductIds.YEARLY     // Có ưu đãi 3 ngày Free Trial
        const val PRODUCT_VIP_LIFETIME = VipProductIds.LIFETIME // In-App Purchase (mua đứt)
    }

    // ─── StateFlows (Single Source of Truth) ─────────────────────────────────

    /** Trạng thái VIP từ Google Play — toàn bộ app lắng nghe qua StateFlow này */
    private val _isVipUser = MutableStateFlow(false)
    val isVipUser: StateFlow<Boolean> = _isVipUser.asStateFlow()

    /** Product ID đang hoạt động của người dùng (null nếu không có gói VIP) */
    private val _activeProductId = MutableStateFlow<String?>(null)
    val activeProductId: StateFlow<String?> = _activeProductId.asStateFlow()

    /**
     * True sau khi [queryExistingPurchases] đã trả lời ÍT NHẤT MỘT LẦN kể từ khi app khởi
     * động (dù kết quả là có VIP hay không). [isVipUser] khởi tạo `false` — nếu collector nào
     * đó hạ cấp người dùng ngay khi thấy `false` mà chưa chờ cờ này, một VIP thật sẽ bị hạ
     * xuống FREE trong vài trăm mili-giây đầu app mở, trước khi Google Play kịp trả lời.
     */
    private val _hasSyncedOnce = MutableStateFlow(false)
    val hasSyncedOnce: StateFlow<Boolean> = _hasSyncedOnce.asStateFlow()

    /** Danh sách ProductDetails từ Google Play Store (giá, ưu đãi, offer tokens) */
    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList: StateFlow<List<ProductDetails>> = _productDetailsList.asStateFlow()

    /** Sự kiện thanh toán — UI lắng nghe để hiển thị thông báo */
    private val _purchaseEvent = MutableSharedFlow<PurchaseEvent>(replay = 0)
    val purchaseEvent: SharedFlow<PurchaseEvent> = _purchaseEvent.asSharedFlow()

    // ─── BillingClient ────────────────────────────────────────────────────────
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        // Tự động kết nối và query sản phẩm/giao dịch ngay khi khởi tạo
        startBillingConnection()
    }

    // ─── Connection Management ────────────────────────────────────────────────

    /**
     * Kết nối Google Play Billing Service.
     * Tự động thử kết nối lại sau 3 giây nếu bị ngắt kết nối (onBillingServiceDisconnected).
     */
    fun startBillingConnection() {
        if (billingClient.isReady) {
            queryAvailableProducts()
            queryExistingPurchases()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Kết nối thành công — truy vấn sản phẩm và giao dịch hiện có
                    queryAvailableProducts()
                    queryExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Tự động thử kết nối lại được xử lý tự động bởi enableAutoServiceReconnection()
            }
        })
    }

    // ─── Product Queries ──────────────────────────────────────────────────────

    /**
     * Truy vấn thông tin sản phẩm (giá, tiêu đề, ưu đãi) từ Google Play Store.
     * Kết quả cập nhật vào [productDetailsList] để PaywallScreen hiển thị.
     */
    fun queryAvailableProducts() {
        if (!billingClient.isReady) return

        val subList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val inAppList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subList)
            .build()

        billingClient.queryProductDetailsAsync(subParams) { billingResult, subQueryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val current = _productDetailsList.value.filter { it.productType != BillingClient.ProductType.SUBS }
                _productDetailsList.value = current + subQueryResult.productDetailsList
            }
        }

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppList)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, inAppQueryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val current = _productDetailsList.value.filter { it.productType != BillingClient.ProductType.INAPP }
                _productDetailsList.value = current + inAppQueryResult.productDetailsList
            }
        }
    }

    // ─── Restore Purchases ────────────────────────────────────────────────────

    /**
     * Khôi phục giao dịch đã mua — bắt buộc theo Google Play Policy.
     *
     * Google yêu cầu mọi ứng dụng có subscription/IAP phải cho phép người dùng
     * khôi phục giao dịch khi cài lại app hoặc đổi thiết bị.
     *
     * @param onComplete Callback với kết quả true nếu tìm thấy giao dịch VIP hợp lệ.
     */
    fun queryExistingPurchases(onComplete: ((Boolean) -> Unit)? = null) {
        if (!billingClient.isReady) {
            // Thử kết nối lại và retry sau
            startBillingConnection()
            onComplete?.invoke(false)
            return
        }

        // Gộp kết quả SUBS + INAPP rồi mới quyết định. Truy vấn lỗi KHÔNG được coi là "không có gói"
        // (sẽ thu hồi VIP oan) — giữ nguyên trạng thái đã lưu và báo thất bại.
        fun query(type: String, onResult: (List<Purchase>?) -> Unit) {
            val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                onResult(if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases else null)
            }
        }

        query(BillingClient.ProductType.SUBS) { subs ->
            query(BillingClient.ProductType.INAPP) { inApps ->
                val entitlement = resolveVipEntitlement(subs, inApps)
                if (entitlement.isVip) {
                    _isVipUser.value = true
                    _activeProductId.value = entitlement.best
                    syncEntitlements(entitlement.productIds)
                    // Acknowledge any older purchase that was never confirmed (retry path)
                    entitlement.active.forEach { acknowledgeIfNeeded(it) }
                } else if (entitlement.shouldRevoke) {
                    // Both queries OK and nothing valid (incl. suspended) -> revoke. A failed query keeps the stored state.
                    _isVipUser.value = false
                    _activeProductId.value = null
                    syncEntitlements(emptySet())
                }
                if (entitlement.definitive) _hasSyncedOnce.value = true
                onComplete?.invoke(entitlement.isVip)
            }
        }
    }


    // ─── Purchase Flow ────────────────────────────────────────────────────────

    /**
     * Mở cửa sổ thanh toán Google Play (BottomSheet).
     *
     * @param activity Activity hiện tại — bắt buộc để launch Google Play billing UI.
     * @param productDetails Chi tiết sản phẩm lấy từ [productDetailsList].
     * @param offerToken Offer token cho subscription tiers. Lấy từ
     *   [ProductDetails.subscriptionOfferDetails.firstOrNull()?.offerToken].
     *   Để trống cho gói In-App Purchase (lifetime).
     */
    fun launchPurchaseFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String = ""
    ) {
        if (!billingClient.isReady) {
            startBillingConnection()
            billingScope.launch {
                _purchaseEvent.emit(PurchaseEvent.Error("Đang kết nối Google Play, vui lòng thử lại."))
            }
            return
        }
        if (productDetails.productType == BillingClient.ProductType.SUBS && offerToken.isEmpty()) {
            billingScope.launch {
                _purchaseEvent.emit(PurchaseEvent.Error("Gói này hiện chưa khả dụng. Vui lòng thử lại sau."))
            }
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .apply {
                // Chỉ set offerToken cho gói thuê bao (SUBS), không dùng cho INAPP
                if (offerToken.isNotEmpty()) {
                    setOfferToken(offerToken)
                }
            }
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val launch = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            billingScope.launch {
                _purchaseEvent.emit(PurchaseEvent.Error("Không mở được Google Play (mã ${launch.responseCode}). Vui lòng thử lại."))
            }
        }
    }

    // ─── Purchase Updates ─────────────────────────────────────────────────────

    /** Callback từ Google Play khi giao dịch có cập nhật (mua mới, pending, thất bại) */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                // Giao dịch thành công — xử lý từng purchase
                purchases?.forEach { purchase -> handlePurchase(purchase) }
                // Re-derive the final answer from all owned products (lifetime + sub, suspended, ...)
                queryExistingPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Người dùng chủ động hủy — không cần thông báo lỗi
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Người dùng đã có gói này — sync lại trạng thái VIP
                queryExistingPurchases()
            }
            else -> {
                val errorMsg = billingResult.debugMessage.ifEmpty {
                    "Mã lỗi ${billingResult.responseCode}"
                }
                billingScope.launch {
                    _purchaseEvent.emit(
                        PurchaseEvent.Error("Thanh toán thất bại: $errorMsg")
                    )
                }
            }
        }
    }

    // ─── Purchase Handler ─────────────────────────────────────────────────────

    /**
     * Xử lý giao dịch:
     *  1. Trạng thái PENDING: thông báo người dùng thanh toán đang chờ xử lý ngoài app.
     *  2. Trạng thái PURCHASED: Mở khóa VIP ngay lập tức.
     *  3. BẮT BUỘC gọi [acknowledgePurchase] nếu chưa xác nhận.
     *     Nếu không acknowledge trong 3 ngày, Google Play SẼ TỰ ĐỘNG hoàn tiền và thu hồi VIP.
     */
    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                billingScope.launch {
                    _purchaseEvent.emit(PurchaseEvent.Pending)
                }
            }
            Purchase.PurchaseState.PURCHASED -> {
                if (purchase.isSuspended) {
                    _isVipUser.value = false
                    purchase.products.forEach { runCatching { com.app.plugin.iap.Entitlements.revoke(it) } }
                    billingScope.launch {
                        _purchaseEvent.emit(
                            PurchaseEvent.Error("Gói đăng ký của bạn đang bị tạm dừng. Vui lòng cập nhật phương thức thanh toán trên Google Play.")
                        )
                    }
                    return
                }

                // Mở khóa VIP ngay lập tức để trải nghiệm không bị gián đoạn
                _isVipUser.value = true
                _activeProductId.value = purchase.products.firstOrNull() ?: PRODUCT_VIP_YEARLY
                _hasSyncedOnce.value = true // a completed purchase is an authoritative answer: lets the entitlement cache persist it

                // Cầu nối quyền lợi sang appplugin: Entitlements.recompute() là chỗ DUY NHẤT
                // tắt quảng cáo (AdsHelper.setRemoveAds) — không có bước này thì subscriber
                // vẫn thấy quảng cáo dù BillingManager đã ghi nhận VIP.
                purchase.products.forEach { runCatching { com.app.plugin.iap.Entitlements.grant(it) } }

                // Emit success event cho UI
                billingScope.launch {
                    _purchaseEvent.emit(PurchaseEvent.Success)
                }

                // Acknowledge bắt buộc nếu chưa được xác nhận
                if (!purchase.isAcknowledged) {
                    acknowledgePurchaseWithRetry(purchase.purchaseToken, maxRetries = 3)
                }
            }
            else -> {
                // Unspecified or unknown state
            }
        }
    }

    /**
     * Đồng bộ tập Product ID VIP đang thực sự sở hữu (theo Google Play) sang
     * [com.app.plugin.iap.Entitlements]. Dùng `sync()` (REPLACE, không cộng dồn) vì đây là kết
     * quả truy vấn `queryPurchasesAsync` — sub đã huỷ đơn giản là không còn trong tập này.
     * Bọc `runCatching` vì Entitlements có thể chưa init trong môi trường test/Robolectric.
     */
    private fun syncEntitlements(ownedVipProductIds: Set<String>) {
        runCatching { com.app.plugin.iap.Entitlements.sync(ownedVipProductIds) }
    }

    /** Acknowledge a granted purchase if Google still shows it unacknowledged (3-day refund window). */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            acknowledgePurchaseWithRetry(purchase.purchaseToken, maxRetries = 3)
        }
    }

    private suspend fun acknowledgePurchaseSuspend(ackParams: AcknowledgePurchaseParams): BillingResult =
        suspendCancellableCoroutine { continuation ->
            billingClient.acknowledgePurchase(ackParams) { billingResult ->
                if (continuation.isActive) {
                    continuation.resume(billingResult)
                }
            }
        }

    private fun acknowledgePurchaseWithRetry(token: String, maxRetries: Int) {
        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()

        billingScope.launch {
            var attempts = 0
            var acknowledged = false
            while (attempts < maxRetries && !acknowledged) {
                attempts++
                val result = acknowledgePurchaseSuspend(ackParams)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    acknowledged = true
                } else if (attempts < maxRetries) {
                    delay((attempts * 2).seconds)
                }
            }
        }
    }
}

/** Helper extensions for selecting subscription offers and dynamic price formatting */
fun ProductDetails.findBestOffer(preferFreeTrial: Boolean = false): ProductDetails.SubscriptionOfferDetails? {
    val offers = subscriptionOfferDetails ?: return null
    if (offers.isEmpty()) return null

    if (preferFreeTrial) {
        // Locate offer containing a free trial phase (priceAmountMicros == 0)
        val trialOffer = offers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        }
        if (trialOffer != null) return trialOffer
    }

    // Default: base plan (without promotional offerId) or first available offer
    return offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
}

fun ProductDetails.getFormattedPrice(preferFreeTrial: Boolean = false): String? {
    oneTimePurchaseOfferDetails?.formattedPrice?.let { return it }
    val offer = findBestOffer(preferFreeTrial)
    val phases = offer?.pricingPhases?.pricingPhaseList
    return phases?.lastOrNull { it.priceAmountMicros > 0 }?.formattedPrice
        ?: phases?.firstOrNull()?.formattedPrice
}

/** Sealed class đại diện cho các sự kiện thanh toán được emit lên UI */
sealed class PurchaseEvent {
    data object Success : PurchaseEvent()
    data object Pending : PurchaseEvent()
    data class Error(val message: String) : PurchaseEvent()
}
