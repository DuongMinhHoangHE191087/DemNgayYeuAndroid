# 50 — Purchase Router: một API mua hàng, kênh do cấu hình chọn (doc 23 mục C.2)

**Ngày viết:** 2026-08-25
**Trạng thái:** **352/352 unit test PASS**, backend 73/73, `assembleRelease` sạch.

---

## A. Bài toán

Doc 18: phí store 15–30% đang bị phá từ ba phía (settlement Epic–Google, anti-steering Apple,
DMA châu Âu). Web shop/D2C tại Mỹ ~$17B, +26%/năm, biên lợi nhuận ~92% so với ~70% qua store.
Nhưng đa số studio không tự làm được vì phải ghép: vendor thanh toán (Stripe/Xsolla/Appcharge),
xác thực webhook, entitlement phía server, và đo lường tách kênh.

Doc 23 mục C.2 đặt yêu cầu cho SDK: **một API duy nhất** `purchase(sku)`, kênh nào do hệ thống
chọn, **ẩn hoàn toàn khỏi game logic**. `PurchaseRouter` thực hiện đúng yêu cầu đó.

## B. Năm quyết định thiết kế

### B.1 Một API, kênh do Remote Config chọn

```kotlin
// Game logic chỉ biết đúng một lệnh — không biết Play Billing hay web shop tồn tại:
PurchaseRouter.purchase(activity, "coin_pack_1") { result ->
    when (result) {
        is IapResult.Success -> grantCoins(result.purchase)
        is IapResult.WebCheckoutOpened -> openCheckout(result.url) // app tự chọn cách mở
        else -> ...
    }
}
```

Khoá cấu hình `purchase_routing_android` (tách riêng khỏi `brain_policy_android` và
`paywall_config_android` — ba nhịp thay đổi khác nhau: huấn luyện / chiến dịch / điều khoản
phí của store). Hình dạng:

```json
{
  "version": "routing_v1",
  "web_shop": {
    "enabled": true,
    "url_template": "https://shop.example.com/checkout?product={product_id}&user={user_id}",
    "eligible_countries": ["US", "DE"]
  },
  "rules": [
    {"product_id": "coin_pack_1", "channel": "web_shop"},
    {"product_id": "vip_pass", "channel": "play_store"}
  ]
}
```

Rule đầu tiên khớp thắng. Không có rule → Play Store. Cấu hình hỏng → **giữ nguyên bản đang
chạy** (cùng nguyên tắc `PaywallManager`/`BrainPolicy`).

### B.2 Hướng an toàn — router không bao giờ tạo đường mua không hoàn tất được

WEB_SHOP chỉ thắng khi TẤT CẢ điều kiện đúng; sai một điều kiện là rơi về PLAY_STORE (hành vi
trước khi có router — tức việc bật router không thể làm tệ hơn không bật):

1. rule nói `web_shop`;
2. `enabled = true`;
3. `url_template` khác rỗng **và bắt buộc https** — dẫn người dùng mang thẻ tín dụng tới http
   là lỗ hổng bảo mật, không phải lỗi cấu hình cần bao dung;
4. quốc gia thuộc `eligible_countries` (rỗng = không giới hạn; **không xác định được** quốc gia
   mà danh sách khác rỗng → Play Store — thà mất một lượt routing còn hơn dẫn nhầm vào kênh
   không phục vụ nước đó).

### B.3 Entitlement phía web shop KHÔNG tự cấp

Mua qua Play Billing: kết quả store trả về ngay trên máy, đủ để cấp cờ tắt quảng cáo (xem giới
hạn trong doc 45). Mua qua web shop thì máy **không thấy gì** — chỉ studio và vendor biết.
Luồng đúng:

```
vendor webhook → server studio xác thực chữ ký → studio gọi PurchaseRouter.grantFromServer(...)
```

`grantFromServer()` nhận dữ liệu ĐÃ xác thực; KDoc ghi thẳng: gọi nó với dữ liệu client tự khai
(deep link tự nhận "tôi mua rồi") là mở cửa gian lận, SDK không xác thực giúp vì không có khóa
bí mật của vendor và mọi xác thực phía client đều giả lập được (doc 18 mục 6.5). Webhook retry
thì an toàn: backend dedup theo `order_id`, `Entitlements.grant` trùng là no-op.

### B.4 Router KHÔNG hỏi brain — cố ý

`AdsBrain.shouldOfferIap()` chặn **chào mời** (paywall hỏi brain vì paywall LÀ lời chào mời).
Còn `purchase()` là người dùng ĐÃ CHỦ ĐỘNG bấm mua — chặn một ý định mua rõ ràng không bảo vệ
ai, chỉ phá doanh thu. Guardrail bảo vệ người chơi khỏi bị mời quá mức, không phải khỏi việc
mua thứ họ muốn mua.

### B.5 SDK không mở trình duyệt

WEB_SHOP → SDK dựng URL checkout rồi trả `IapResult.WebCheckoutOpened(productId, url)`. App tự
chọn Custom Tab / WebView / trình duyệt ngoài — cùng ranh giới "SDK không vẽ hộ" của paywall.

## C. Đo lường

| Event | Vai trò |
|---|---|
| `web_checkout_opened` | Tử số phễu routing: người được dẫn ra web shop (product_id, channel, routing_version) |
| `iap_purchase` channel `WEB_SHOP` | Xác nhận phía server qua `grantFromServer()` |

Ghép hai event → tỉ lệ chuyển đổi theo kênh (doc 18 mục 6.7). Backend **không cần endpoint
mới**: `GET /metrics/northstar` đã có `revenue.by_channel_usd` khoá trên cột `channel`, doanh thu
WEB_SHOP chảy vào tự động. Ba lưu ý đo lường:

- **URL checkout không bao giờ được gửi vào hàng đợi log** — template của studio có thể nhúng
  session token; gửi nó là phát tán bí mật phiên ra ngoài hệ thống thanh toán. Có test khoá.
- `web_checkout_opened` không có dedup key: một người mở checkout ba lần rồi quay lại là ba lần
  thật trong phễu.
- `web_checkout_opened` đếm số **được dẫn ra**, không đếm số hoàn tất — khoảng cách giữa hai số
  chính là thứ tối ưu (và cũng là lý do entitlement không cấp ở bước này).

## D. Giới hạn phải nói thẳng

1. **Chưa có server-side entitlement SSoT thật.** `grantFromServer()` là điểm nối đúng, nhưng
   nguồn sự thật vẫn là `Entitlements` trên máy (mất khi xoá app, không đồng bộ đa thiết bị).
   Luồng đầy đủ cần endpoint `GET /entitlements?user_id=...` của studio — thuộc phần backend,
   chưa build.
2. **Chưa có xác thực biên lai Play Billing phía server** (giới hạn lớn nhất của doc 45 vẫn còn
   — router không thay đổi điều đó).
3. **`restorePurchases` chỉ đi Play Store.** Đã mua qua web shop thì "restore" phải là server
   studio cấp lại từ lịch sử giao dịch của chính họ — SDK không có dữ liệu đó. App cần biết để
   không hứa "restore" cho kênh web.
4. **Router không tự đo phí/chuyển đổi để chọn kênh.** Nó thuần hàm số của cấu hình. Heuristic
   (kênh nào rẻ hơn, chuyển đổi cao hơn cho segment nào) là việc của brain khi có dữ liệu phễu
   — đó mới là lúc `route()` hỏi chính sách thay vì rule tĩnh.
5. **Chưa có deep-link quay về app sau thanh toán** (doc 23 C.2 có nêu). URL template là của
   studio; deep-link về app là việc của trang checkout. SDK chỉ nhận kết quả khi studio gọi
   `grantFromServer()`.

## E. Tích hợp (cho app)

```kotlin
// 1. Không cần làm gì để bật — mặc định mọi sản phẩm đi Play Store (hành vi cũ).

// 2. Remote Config: thêm khoá purchase_routing_android như mục B.1.

// 3. Thay IapHelper.purchase bằng PurchaseRouter.purchase:
PurchaseRouter.purchase(activity, productId) { result -> ... }

// 4. Xử lý IapResult.WebCheckoutOpened: mở URL (khuyến nghị Custom Tab).

// 5. Server studio (khi có web shop): webhook vendor → xác thực → gọi SDK:
PurchaseRouter.grantFromServer(productId, orderId, priceMicros, currencyCode)
//    (qua bridge của app, vì server không gọi thẳng vào máy — thường qua push/sync
//     hoặc app hỏi server khi mở lại)
```

Bước 5 nói thẳng một điều: `grantFromServer()` chạy TRÊN MÁY, nên phải có cơ chế để server báo
cho máy (push notification, hoặc app hỏi `GET /entitlements` khi khởi động). Không có cơ chế đó
thì người mua qua web phải chờ lần mở app kế tiếp mới nhận vật phẩm — chấp nhận được cho MVP,
phải nói rõ với studio.
