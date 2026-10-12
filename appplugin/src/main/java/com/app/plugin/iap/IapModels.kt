package com.app.plugin.iap

/**
 * Mô hình dữ liệu IAP, cố ý **không để lộ kiểu của Play Billing** ra API công khai.
 *
 * Vì sao: đây là thư viện được publish dưới dạng AAR. Nếu API công khai nhận/trả
 * `ProductDetails`, `Purchase`... thì mọi app tích hợp bị khoá cứng vào đúng phiên bản
 * billing mà SDK này chọn, và một ngày Google đổi API (v8 đã đổi chữ ký
 * `queryProductDetailsAsync` một lần rồi) là mọi app phải sửa theo. Bọc lại thành model
 * riêng cho phép nâng cấp billing bên trong mà không phá vỡ ai.
 *
 * Đây cũng là lý do `com.android.billingclient` được khai báo `implementation` chứ không
 * `api` trong `build.gradle.kts`.
 */

public enum class IapProductType { INAPP, SUBS }

/**
 * Kênh thanh toán (doc 26 mục C.2). Doanh thu phải báo cáo **tách theo kênh** vì biên lợi
 * nhuận khác nhau rõ rệt (~70% net qua store so với ~92% net qua web-shop, doc 18) — gộp
 * một số thì đội vận hành không thấy được hiệu quả thật của payment routing.
 *
 * `WEB_SHOP` đã có trong enum nhưng CHƯA có đường thực thi nào — nó là Phase 1 của doc 23.
 * Để sẵn ở đây để schema event không phải đổi khi kênh đó xuất hiện.
 */
public enum class PurchaseChannel { PLAY_STORE, WEB_SHOP }

/** Trạng thái giao dịch, ánh xạ từ `Purchase.PurchaseState` của Play Billing. */
public enum class IapPurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/**
 * Một sản phẩm đã lấy được thông tin giá từ store.
 *
 * `priceMicros` + `currencyCode` giữ nguyên đơn vị gốc của store, KHÔNG quy đổi sẵn.
 * Quy đổi tại client là nơi sai số và tỉ giá cũ len vào; backend có tỉ giá thật nên
 * việc quy đổi thuộc về nó (xem `FxRates`).
 */
public data class IapProduct(
    val productId: String,
    val type: IapProductType,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    val title: String = "",
    val description: String = "",
    /** Bắt buộc khi mua SUBS; rỗng với INAPP thông thường. */
    val offerToken: String = ""
)

/**
 * Một giao dịch đã xảy ra.
 *
 * `priceMicros`/`currencyCode` được ghép lại từ `IapProduct` đã cache, vì bản thân
 * `Purchase` của Play Billing **không mang giá** — thiếu bước ghép này thì event doanh thu
 * không có số tiền, và ARPDAU (doc 26 mục C.1) không tính được.
 */
public data class IapPurchase(
    val productId: String,
    val orderId: String,
    val purchaseToken: String,
    val purchaseTimeMs: Long,
    val state: IapPurchaseState,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val quantity: Int,
    val priceMicros: Long,
    val currencyCode: String,
    val channel: PurchaseChannel = PurchaseChannel.PLAY_STORE
)

/** Kết quả một lượt mua, đủ để app hiển thị đúng thông báo mà không cần biết mã lỗi của Google. */
public sealed class IapResult {
    public data class Success(val purchase: IapPurchase) : IapResult()
    /** Giao dịch chờ duyệt (thanh toán tiền mặt, phụ huynh phê duyệt...). Chưa được cấp quyền lợi. */
    public data class Pending(val purchase: IapPurchase) : IapResult()
    public object UserCanceled : IapResult()
    public object AlreadyOwned : IapResult()
    public data class Failed(val code: Int, val message: String) : IapResult()
    /**
     * Router chọn kênh web shop (doc 23 mục C.2): SDK trả về URL checkout, app tự mở theo cách
     * của mình (custom tab / WebView / trình duyệt ngoài) — cùng ranh giới "SDK không vẽ hộ"
     * của paywall. **Chưa cấp quyền lợi**: entitlement chỉ đến sau xác nhận phía server, qua
     * `PurchaseRouter.grantFromServer()`.
     */
    public data class WebCheckoutOpened(val productId: String, val url: String) : IapResult()
}
