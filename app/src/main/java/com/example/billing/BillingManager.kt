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
    // Phải khớp CHÍNH XÁC với Product ID được khai báo trên Google Play Console.
    companion object {
        const val PRODUCT_VIP_MONTHLY = "vip_monthly"
        const val PRODUCT_VIP_YEARLY = "vip_yearly"     // Có ưu đãi 3 ngày Free Trial
        const val PRODUCT_VIP_LIFETIME = "vip_lifetime" // In-App Purchase (mua đứt)
    }

    // ─── StateFlows (Single Source of Truth) ─────────────────────────────────

    /** Trạng thái VIP từ Google Play — toàn bộ app lắng nghe qua StateFlow này */
    private val _isVipUser = MutableStateFlow(false)
    val isVipUser: StateFlow<Boolean> = _isVipUser.asStateFlow()

    /** Product ID đang hoạt động của người dùng (null nếu không có gói VIP) */
    private val _activeProductId = MutableStateFlow<String?>(null)
    val activeProductId: StateFlow<String?> = _activeProductId.asStateFlow()

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
                // Dịch vụ Google Play bị ngắt (hiếm gặp) — tự động thử kết nối lại
                billingScope.launch {
                    delay(3.seconds)
                    startBillingConnection()
                }
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

        val productList = listOf(
            // Gói thuê bao tháng
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            // Gói thuê bao năm (có free trial 3 ngày cấu hình trong Play Console)
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            // Gói mua đứt trọn đời (In-App Purchase — không phải SUBS)
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_VIP_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, detailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetailsList.value = detailsList
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

        // 1. Kiểm tra gói thuê bao (SUBS: monthly & yearly)
        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subParams) { _, subPurchases ->
            val activeSubPurchases = subPurchases.filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            if (activeSubPurchases.isNotEmpty()) {
                val purchasedSub = activeSubPurchases.first()
                _isVipUser.value = true
                _activeProductId.value = purchasedSub.products.firstOrNull() ?: PRODUCT_VIP_YEARLY
                // Đảm bảo acknowledge các giao dịch cũ chưa được xác nhận
                activeSubPurchases.forEach { handlePurchase(it) }
                onComplete?.invoke(true)
                return@queryPurchasesAsync
            }

            // 2. Kiểm tra gói mua đứt trọn đời (INAPP: lifetime)
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(inAppParams) { _, inAppPurchases ->
                val lifetimePurchases = inAppPurchases.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                val hasLifetime = lifetimePurchases.isNotEmpty()
                if (hasLifetime) {
                    _isVipUser.value = true
                    _activeProductId.value = PRODUCT_VIP_LIFETIME
                    lifetimePurchases.forEach { handlePurchase(it) }
                } else {
                    // Không có gói VIP hợp lệ nào (đã hết hạn hoặc chưa mua) -> Thu hồi VIP
                    _isVipUser.value = false
                    _activeProductId.value = null
                }
                onComplete?.invoke(hasLifetime)
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

        billingClient.launchBillingFlow(activity, billingFlowParams)
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
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Người dùng chủ động hủy — không cần thông báo lỗi
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Người dùng đã có gói này — sync lại trạng thái VIP
                queryExistingPurchases()
            }
            else -> {
                // Lỗi thanh toán — emit event để UI hiển thị thông báo
                billingScope.launch {
                    _purchaseEvent.emit(
                        PurchaseEvent.Error("Thanh toán thất bại (${billingResult.responseCode}): ${billingResult.debugMessage}")
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
                // Mở khóa VIP ngay lập tức để trải nghiệm không bị gián đoạn
                _isVipUser.value = true
                _activeProductId.value = purchase.products.firstOrNull() ?: PRODUCT_VIP_YEARLY

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

    private fun acknowledgePurchaseWithRetry(token: String, maxRetries: Int) {
        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()

        billingScope.launch {
            var attempts = 0
            var acknowledged = false
            while (attempts < maxRetries && !acknowledged) {
                attempts++
                billingClient.acknowledgePurchase(ackParams) { ackResult ->
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        acknowledged = true
                    }
                }
                if (!acknowledged && attempts < maxRetries) {
                    delay((attempts * 2).seconds)
                }
            }
        }
    }
}

/** Sealed class đại diện cho các sự kiện thanh toán được emit lên UI */
sealed class PurchaseEvent {
    data object Success : PurchaseEvent()
    data object Pending : PurchaseEvent()
    data class Error(val message: String) : PurchaseEvent()
}
