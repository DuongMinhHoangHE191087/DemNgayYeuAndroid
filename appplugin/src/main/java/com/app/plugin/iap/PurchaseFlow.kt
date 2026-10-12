package com.app.plugin.iap

/**
 * Luồng mua đang mở của [IapHelper] (D20/D22), tách khỏi `IapHelper` ở 1.9.0 Task 11: callback của `purchase()` và sản
 * phẩm của nó, cùng quyết định một kết quả purchases-updated có tiêu callback đó không. Mọi đọc/ghi trong monitor của object.
 */
internal object PurchaseFlow {
    /** Luồng mua đang mở (D22): khác `null` từ `purchase()` tới purchases-updated hay `onLaunch` báo lỗi. */
    @Volatile private var pendingFlowCallback: ((IapResult) -> Unit)? = null
    @Volatile private var flowProductId: String = ""

    /** D20: callback gán TRƯỚC khi mở luồng (kết quả tới sớm vẫn tìm thấy nó); D22: một luồng tại một thời điểm. */
    fun open(productId: String, flow: (IapResult) -> Unit): Boolean = synchronized(this) {
        if (pendingFlowCallback != null) false else { pendingFlowCallback = flow; flowProductId = productId; true }
    }

    /** `onLaunch` báo lỗi: đóng luồng nếu nó vẫn là [flow]. */
    fun closeIf(flow: (IapResult) -> Unit) {
        synchronized(this) { if (pendingFlowCallback === flow) pendingFlowCallback = null }
    }

    /**
     * Purchases-updated: lấy callback của luồng đang mở (tại chỗ, D20) và sản phẩm của luồng — chỉ khi kết quả thuộc luồng
     * (R-2 m1, 1.9.0 Task 11): mã khác OK hay OK không giao dịch là kết cục của lần mở luồng; OK có giao dịch thì phải có
     * giao dịch của [flowProductId]. Giao dịch sản phẩm khác (PENDING hoàn tất ngoài luồng) không tiêu callback: `null`.
     */
    fun take(code: Int, purchases: List<PortPurchase>?): Pair<((IapResult) -> Unit)?, String> = synchronized(this) {
        val product = flowProductId
        val belongs = code != BillingPort.OK || purchases.isNullOrEmpty() || purchases.any { product in it.products }
        val cb = if (belongs) pendingFlowCallback else null
        if (belongs) pendingFlowCallback = null
        cb to product
    }

    /** Test: không còn luồng mua nào mở. */
    internal fun resetForTest() {
        synchronized(this) { pendingFlowCallback = null }
    }
}
