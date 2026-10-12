# 51 — Vòng đời subscription: dunning, win-back, phân loại trạng thái (doc 23 mục C.3)

**Ngày viết:** 2026-08-25
**Trạng thái:** **383/383 unit test PASS**, backend 77/77, `assembleDebug` + `assembleRelease` sạch.

---

## A. Bài toán

Play Billing cho biết HAI sự thật về một subscription: `purchaseState` và `isAutoRenewing`.
Nhưng bốn câu hỏi vận hành quan trọng nhất của lớp subscription thì không câu nào được trả lời:

1. Người này đang trả tiền bình thường, hay thanh toán vừa thất bại và Play đang retry?
2. Họ đã huỷ (nhưng còn quyền lợi tới hết kỳ), hay đã churn hẳn?
3. Có nên nhắc họ sửa phương thức thanh toán LẦN NỮA không, hay nhắc thế là spam?
4. Ai đủ điều kiện nhận offer win-back?

Doc 19 mục B.8 xác nhận khoảng trống lớn nhất: **Google Play không có win-back offer native**
(Apple có từ WWDC 2024, Google thì developer phải tự làm). Muốn phát offer win-back thì việc
đầu tiên là phải *biết ai đã churn* — và mảnh đó Google để trống hoàn toàn.

`SubscriptionLifecycle` ghép hai sự thật của store với MỘT sự thật cục bộ (lịch sử "đã từng
có sub" trên máy này) để trả lời cả bốn câu hỏi, **không cần backend**, drop-in vào dự án.

## B. Năm quyết định thiết kế

### B.1 Năm trạng thái — suy ra từ sự thật, không đoán

```text
NONE            chưa từng có sub nào trên máy này
ACTIVE          PURCHASED + autoRenewing — trả tiền bình thường
CANCELLED       PURCHASED + KHÔNG autoRenewing — đã huỷ, còn quyền lợi tới hết kỳ
IN_GRACE_PERIOD PENDING — thanh toán thất bại, Play retry tới 30 ngày, VẪN còn quyền lợi
LAPSED          từng có sub (lịch sử) nhưng store không còn trả về — mất quyền lợi
```

Nguyên tắc: **không có sự thật thì không kết luận** (trạng thái `UNSPECIFIED` hay sản phẩm
chưa từng thấy đều ở `NONE`). `LAPSED` chỉ được suy ra khi quan sát ĐẦY ĐỦ (`complete=true`,
kết quả `queryPurchasesAsync` thật) — một lượt cập nhật nhanh sau khi mua không được biến mọi
sub vắng mặt thành churn.

### B.2 Sửa lỗi entitlement trong grace period

Trước đây `restorePurchases` chỉ đếm `PURCHASED`: thanh toán vừa thất bại → PENDING → quyền lợi
bị tước NGAY, dù chính sách grace của Play cho giữ quyền lợi tới 30 ngày trong lúc retry.
Nay SUBS với `PurchaseState.PENDING` vẫn được tính là owned (INAPP PENDING thì KHÔNG — đó là
chờ phê duyệt, ngữ nghĩa khác).

### B.3 Một event phủ toàn bộ taxonomy doc 19

Event duy nhất `subscription_state` với cặp `from_state`/`to_state`:

| Cặp chuyển đổi | Nghĩa theo doc 19 |
|---|---|
| `ACTIVE → IN_GRACE_PERIOD` | vào grace period |
| `IN_GRACE_PERIOD → ACTIVE` | thanh toán hồi phục |
| `ACTIVE → CANCELLED` | huỷ tự nguyện |
| `* → LAPSED` | churn |
| `LAPSED → ACTIVE` | win-back thành công |

SDK chỉ phát khi trạng thái THẬT SỰ đổi — dedup nằm ở nguồn, backend không đặt `dedup_key`
(cùng một cặp chuyển đổi xảy ra lại sau nhiều tháng là chuyển đổi thật, không phải trùng).

### B.4 Dunning cadence — đếm lượt, đặt lại theo episode

`dunningNoticeDue(productId)` đếm lượt app vào foreground trong lúc `IN_GRACE_PERIOD`, chỉ trả
`true` ở lượt đúng lịch (mặc định mở app thứ 1, 2, 4). Hết lịch là im lặng — không nhắc mãi.
Khi episode grace kết thúc (hồi phục, hold hay churn), lịch đếm đặt lại từ đầu. SDK trả lời
"đã đến lượt chưa"; **app tự vẽ thông báo** (cùng ranh giới "SDK không vẽ hộ").

### B.5 Win-back = cờ LAPSED, ai vẽ offer là việc của app

`isWinBackEligible(productId)` / `winBackEligibleProductIds()` phát hiện người TỪNG trả tiền
và hiện KHÔNG còn quyền lợi — đúng mảnh Google không có native. Kèm
`managementUriFor(productId, packageName)` trả deep link chuẩn của Google mở màn hình quản lý
subscription — nơi người dùng sửa phương thức thanh toán trong grace period.

## C. Cấu hình từ xa

Khoá riêng `subscription_lifecycle_android` (nhịp vận hành subscription — cadence nhắc, công
tắc win-back — không trùng nhịp routing lẫn paywall):

```json
{
  "version": "sub_lifecycle_v1",
  "enabled": true,
  "dunning_cadence": [1, 2, 4],
  "win_back_enabled": true
}
```

Quy tắc nhà: JSON hỏng/thiếu version → **từ chối, giữ nguyên bản đang chạy**. `enabled=false`
là công tắc tắt khẩn cấp — ngừng phân loại, ngừng event, ngừng nhắc, app cư xử như thể tính
năng không tồn tại. `dunning_cadence: []` = cố ý không nhắc (được nhận, có chú thích trong
report); cadence chứa phần tử không phải số → từ chối cả cấu hình.

## D. Đo lường

- **Churn phát hiện được** = số chuyển đổi `* → LAPSED` — trước đây không đo được vì SDK
  không phân biệt "hết hạn" với "chưa từng mua".
- **Win-back funnel** = `LAPSED` (đủ điều kiện) → `LAPSED → ACTIVE` (thành công), theo từng
  sản phẩm và từng version cấu hình (`lifecycle_version`).
- **Hiệu quả dunning** = tỉ lệ `IN_GRACE_PERIOD → ACTIVE` (hồi phục) so với `IN_GRACE_PERIOD
  → LAPSED` (mất hẳn), so sánh giữa các cadence khác nhau qua version.

Backend: `normalize.py` có nhánh flatten riêng cho `subscription_state` (product_id, from_state,
to_state, lifecycle_version), schema `events_raw.json` thêm ba cột STRING.

## E. Giới hạn — ghi rõ

1. **Lịch sử sống trên máy.** Xoá app là mất; người churn quay lại cài đặt bị phân loại
   `NONE` thay vì `LAPSED`. Nguồn sự thật đúng là phía server theo user_id — chưa có backend.
2. **Prepaid plan không phân biệt được với sub đã huỷ** (cả hai đều PURCHASED + không
   autoRenewing).
3. **Lượt observe đầu tiên sau cài đặt** phát chuyển đổi `NONE→ACTIVE` dù sub có thể đã tồn
   tại nhiều năm — sự thật cấp máy, không phải cấp tài khoản.
4. **Lịch retry thanh toán của Play là việc của server Google**; lớp này chỉ phân loại kết
   quả, không nhìn thấy và không can thiệp lịch đó.
5. **Free-trial eligibility là việc của Play** (offer token đã chạy qua IapHelper); lớp này
   không tự quyết ai được thử miễn phí.
6. **Chưa có xác thực biên lai phía server** — cùng giới hạn với router (doc 50).

## F. Tích hợp (drop-in)

```kotlin
// IapHelper đã tự nối: mỗi lần đồng bộ mua hàng (restorePurchases, handlePurchase)
// đều nạp snapshot vào SubscriptionLifecycle. App chỉ cần ĐỌC:

when (SubscriptionLifecycle.stateFor("vip_monthly")) {
    SubState.IN_GRACE_PERIOD ->
        if (SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
            showFixPaymentDialog(SubscriptionLifecycle.managementUriFor("vip_monthly", packageName))
    SubState.LAPSED ->
        if (SubscriptionLifecycle.isWinBackEligible("vip_monthly"))
            showWinBackOffer("vip_monthly")
    else -> {}
}
```

Ba điểm nối đã có sẵn trong SDK:
- `IapHelper.queryOwned` thu snapshot SUBS (kể cả PENDING được tính quyền lợi);
- `IapHelper.handlePurchase` gọi `observe(complete=false)` để người vừa tái đăng ký rời
  LAPSED ngay lập tức;
- `FirHelperConfig` nạp khoá `subscription_lifecycle_android` mỗi lần fetch Remote Config.
