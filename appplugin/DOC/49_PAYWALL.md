# 49 — Paywall: cấu hình từ xa, A/B, và holdout native

**Ngày viết:** 2026-08-25
**Trạng thái:** **330/330 unit test PASS**, `assembleRelease` (R8) sạch.

---

## A. Ranh giới: SDK không vẽ giao diện

`PaywallManager.paywallFor()` trả về *nên bán gì, theo thứ tự nào, với biến thể nào*. App tự vẽ.
Cùng ranh giới `AdsBrain` đã theo: brain trả lời "có nên", caller thực thi.

Vì sao không vẽ hộ: giao diện là thứ đặc thù nhất của từng game — font, khung, hoạt ảnh, ngôn
ngữ thiết kế. Một thư viện vẽ paywall sẽ hoặc trông lạc lõng, hoặc phải mang theo một hệ thống
theming đủ lớn để trở thành gánh nặng lớn hơn chính tính năng nó cung cấp. Doc 19 ghi nhận đúng
hệ quả này: **game top-grossing tự build paywall** thay vì dùng vendor.

```kotlin
val pw = PaywallManager.paywallFor("shop", playerId)
if (pw != null) {
    renderShop(pw.products, highlight = pw.highlight)   // app tự vẽ
    PaywallManager.onPaywallShown(pw)
}
// pw == null: SDK không có ý kiến → dùng luồng cũ của app
```

`null` nghĩa là *"SDK không có ý kiến"*, **không** phải "hiện paywall rỗng". Trả về một paywall
không sản phẩm sẽ khiến app vẽ một màn hình trống — tệ hơn hẳn việc không vẽ gì.

## B. Holdout native — khoảng trống đã xác minh của thị trường

Doc 19 mục 5: **không vendor paywall lớn nào** (RevenueCat, Adapty, Superwall, Apphud,
Qonversion) có holdout native.

Khác biệt cụ thể:

| | Vendor hiện có | Ở đây |
|---|---|---|
| Đo biến thể A so với B | ✅ | ✅ |
| Đo **có paywall so với KHÔNG có paywall** | ❌ | ✅ |

Vế thứ hai mới là câu hỏi về **giá trị tuyệt đối**: paywall này có đáng tồn tại không, hay nó
chỉ đang chuyển doanh thu từ chỗ khác sang. Không đo được thì mọi cải tiến biến thể đều là tối
ưu cục bộ quanh một giả định chưa ai kiểm chứng.

Nhóm holdout **không nhận paywall nào cả**, và số lần bỏ qua được đếm (`holdoutSkipCount()`).
Bỏ qua im lặng sẽ khiến nhóm đối chứng trông như không tồn tại trong dữ liệu — mất mẫu số.

## C. Ba không gian băm độc lập

Hệ thống nay có ba phép phân nhóm chạy song song:

| Phân nhóm | Không gian băm |
|---|---|
| Holdout của brain | `salt` |
| Staged rollout của brain | `"$salt\|rollout"` |
| Biến thể A/B paywall | `"paywall_variant"` |

Dùng chung không gian sẽ khiến chúng **tương quan hoàn toàn** — ví dụ mọi người trong holdout
rơi vào cùng một biến thể paywall, và hai thí nghiệm không chạy song song được. Có test khoá
tính độc lập này.

## D. Cấu hình

Khoá Remote Config riêng: **`paywall_config_android`**, tách khỏi `brain_policy_android`.

Lý do tách: hai thứ có nhịp thay đổi rất khác nhau. Paywall đổi theo chiến dịch/sự kiện (hàng
tuần), policy brain đổi theo chu kỳ huấn luyện. Gộp chung buộc phải đẩy lại cả hai mỗi lần sửa
một cái — và mỗi lần đẩy lại là một cơ hội đẩy nhầm.

```json
{
  "version": "pw_2026_w35",
  "placements": {
    "shop": {
      "variants": [
        {"id": "control",   "weight": 50, "products": ["coin_small", "coin_big"]},
        {"id": "reordered", "weight": 50, "products": ["coin_big", "coin_small"]}
      ]
    },
    "no_ads": {
      "variants": [
        {"id": "only", "weight": 100, "products": ["premium_monthly"], "highlight": "premium_monthly"}
      ]
    }
  }
}
```

Cùng nguyên tắc an toàn với `BrainPolicy`: cấu hình hỏng thì **giữ nguyên cấu hình đang chạy**,
không rơi về mặc định. Biến thể không có sản phẩm bị loại kèm cảnh báo trong report.

## E. Chưa làm — ghi rõ

1. **Chưa có A/B giá.** Biến thể hiện chỉ đổi *sản phẩm nào và thứ tự*, chưa đổi giá. Pricing
   bandit (doc 11) cần nhiều sản phẩm cùng loại ở các mức giá khác nhau trên Play Console —
   một quyết định vận hành, không phải một tính năng SDK.
2. **Dunning / win-back đã xong ngày 2026-08-25 — xem doc 51.** Còn lại của mục này chỉ là
   free-trial eligibility, và đó là việc của Play (offer token) chứ không phải việc SDK tự quyết.

> Mục 3 cũ (purchase router đa kênh) đã xong ngày 2026-08-25 — xem **doc 50**.

## E'. Đã xong (2026-08-25) — từng là mục E.2 và E.3

1. **Nối vào brain.** `paywallFor()` hỏi `AdsBrain.shouldOfferIap(decisionPoint)` TRƯỚC khi
   render, với thứ tự ưu tiên: cấu hình từ xa → holdout → brain → chọn biến thể. Câu hỏi
   "holdout của ai thắng khi cả hai cùng có nhóm đối chứng" được giải bằng thiết kế
   **holdout thống nhất** (doc 46 mục F): holdout paywall CHÍNH LÀ holdout brain — cùng một
   namespace `brain_holdout_v1`, kiểm tra TRƯỚC khi tốn một quyết định bandit, và mọi lượt bỏ
   qua vẫn ghi `counterfactual_variant_id` (biến thể LẼ RA đã hiện) để phép so sánh với nhóm
   đối chứng không mất. Hợp đồng: một lần gọi `paywallFor()` tốn nhiều nhất MỘT quyết định
   SHOP_OPEN; trả `null` thì không render gì, không mất gì thêm.
2. **Theo dõi chuyển đổi theo biến thể.** Client chỉ gửi mẫu số của phễu
   (`paywall_impression`, `paywall_holdout_skip`); việc ghép với `iap_purchase` thuộc về
   backend — đúng chỗ, vì client chỉ thấy dữ liệu của chính máy nó. Backend có
   `GET /metrics/paywall`: chuyển đổi theo từng (placement, biến thể), và lift của nhóm được
   hiện paywall so với nhóm holdout tính bằng ĐIỂM PHẦN TRĂM — không phải % tương đối, vì với
   tỉ lệ chuyển đổi nhỏ % tương đối phồng lên rất dễ gây hiểu nhầm.
   Giới hạn phải nói thẳng: chuyển đổi đo ở cấp USER (impression không mang danh sách sản
   phẩm nên không gán lượt mua về đúng biến thể được — gán bừa sẽ tạo chuyển đổi ảo); người
   nhận paywall ở nhiều placement xuất hiện trong nhóm của TỪNG placement đó, và đây là
   overlap thật của phép đo chứ không phải lỗi.

## Không xác minh được (ghi rõ)

1. Chưa chạy trên traffic thật. Mọi khẳng định đến từ 330 unit test.
2. Phân bố biến thể được kiểm trên 400 id tổng hợp trong test, chưa kiểm trên phân bố id thật.
3. Khẳng định "không vendor nào có holdout native" lấy từ doc 19, ngày viết 2026-08-21 — thị
   trường này đổi nhanh, cần kiểm lại trước khi dùng làm luận điểm bán hàng.
