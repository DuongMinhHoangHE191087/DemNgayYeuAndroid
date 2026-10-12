# 54 — Offerwall: lớp adapter đa vendor, bề mặt monetization thứ tư (doc 20, doc 23 mục C.4)

**Ngày viết:** 2026-08-25
**Trạng thái:** 445/445 unit test, backend 89/89, hai AAR build sạch.

---

## A. Bài toán

Offerwall phục hưng hậu-ATT vì attribution chạy server-to-server — không cần IDFA. Các con
số của doc 20: eCPM tier-1 **$350–$2,000+** so với ~$19 của rewarded, **93% doanh thu là
additive** (không ăn vào IAP), người engage có **D30 retention gấp ~7 lần**. Nhưng ba rào
cản khiến đa số studio không làm:

1. Mỗi vendor (Tapjoy/AppLovin, DT Exchange, adjoe, Torox, RevU, Mistplay) một SDK, một
   API, một luồng trả thưởng.
2. Fill rate chỉ 5–15% — doc 20 khuyến nghị **luôn chạy đa offerwall**, tức cái đau nhân
   lên số vendor.
3. Bài học ironSource đóng direct demand 30/4/2026: nhúng thẳng vendor SDK vào thư viện
   biến nó thành nô lệ của lịch release vendor.

Quyết định của doc 20 mục F.1: **adapter + reward delivery, không build content**. Lớp này
là đúng cái adapter đó: cổng vào có gate, event thống nhất, chống trả thưởng trùng — cắm
2–3 provider cùng lúc qua một API.

## B. Ba quyết định thiết kế

### B.1 Provider là interface do game implement

SDK không chứa dependency vendor nào. Game bọc vendor SDK trong một adapter mỏng:

```kotlin
interface OfferwallProvider {
    val providerId: String
    fun isAvailable(): Boolean
    fun show(onWallClosed: () -> Unit)
}
```

Vendor đổi API theo phiên bản là việc của adapter, không phải của SDK. Cùng `providerId`
đăng ký lại sẽ THAY adapter cũ — hot-swap không cần khởi động lại.

### B.2 Đóng tường ≠ hoàn thành offer

`show()` chỉ ghi nhận lượt mở tường (impression). Thưởng về qua `reportCompletion()` —
tách riêng vì nhiều vendor xác nhận hoàn thành bằng callback server-to-server đến SAU khi
người chơi đã đóng tường. Gộp hai sự kiện sẽ mất thưởng của người đã làm thật.

### B.3 SDK không trao thưởng

`reportCompletion` chống trùng theo (provider, transaction id) rồi trả dữ liệu — trao
currency là việc của game, cùng ranh giới với battle pass claim và recurring grant. Xác
thực chống gian lận thật sự nằm ở luồng S2S giữa vendor và server của studio; SDK không
thể và không cố làm thay.

## C. Gate consent + COPPA

Vendor SDK thu thập dữ liệu thiết bị cho fraud prevention/analytics — dữ liệu hành vi cần
consent theo GDPR, dù offerwall không cần IDFA. Vì vậy:

- Consent **DENIED** → chặn mở tường.
- Consent **UNKNOWN** KHÔNG chặn — chưa hỏi khác với từ chối (quy tắc nhà, doc 13).
- `childDirected == true` → chặn luôn, kể cả GRANTED: COPPA, và đa số vendor tự loại khỏi
  app trẻ em anyway.

## D. Cấu hình từ xa

Khoá riêng `offerwall_config_android` — nhịp đổi theo vendor (fill rate, điều khoản),
không trùng nhịp paywall hay routing:

```json
{
  "version": "ow_v1",
  "enabled": true,
  "providers": ["revu", "adjoe"]
}
```

`providers` vắng mặt hoặc rỗng = cho phép mọi provider đã đăng ký (SDK chạy được ngay khi
studio chưa kịp cấu hình). JSON hỏng → giữ bản đang chạy; thiếu `version` → từ chối.

**Kill switch đối xử công bằng:** `enabled=false` chặn lượt MỞ mới, nhưng completion của
việc đã hoàn thành vẫn được ghi nhận — từ chối trả công cho việc người chơi đã làm xong
là quỵt, không phải tắt tính năng.

## E. Đo lường

- `offerwall_impression` (`provider_id`, version) — mẫu số. Không dedup_key: mở tường nhiều
  lần là hành vi thật.
- `offerwall_completed` (`provider_id`, `currency_earned`, `transaction_id`, version) —
  tử số. Dedup_key phía backend ghép `provider_id:transaction_id` vì id giao dịch chỉ duy
  nhất trong MỘT vendor; SDK đã chặn trùng ở nguồn (prefs, trần 200 bản ghi), dedup server
  là lớp thứ hai chống replay sau kill-app.

Câu hỏi kinh doanh nó trả lời: **provider nào đáng giữ** — so tỉ lệ chuyển đổi
impression→completion giữa các vendor khi fill rate mỗi nhà một khác.

## F. Giới hạn — ghi rõ

1. Lịch sử transaction sống trên máy (prefs, trần 200): xoá app là mất khả năng chống trùng
   cho completion cũ.
2. Không xác thực S2S phía SDK — đó là việc backend studio với vendor.
3. SDK không vẽ UI của tường — vendor SDK tự vẽ; SDK chỉ giữ cổng vào và sổ sách.

## G. Tích hợp (drop-in)

```kotlin
// 1. Bọc vendor SDK (mỗi vendor một adapter mỏng):
class RevUProvider(ctx: Context) : OfferwallManager.OfferwallProvider {
    override val providerId = "revu"
    override fun isAvailable() = RevU.isInitialized(ctx)
    override fun show(onWallClosed: () -> Unit) { RevU.showWall(ctx) { onWallClosed() } }
}
OfferwallManager.register(RevUProvider(this))
// Cấu hình offerwall_config_android tự nạp qua FirHelperConfig.

// 2. Vẽ nút offerwall khi có provider mở được:
if (OfferwallManager.availableProviders().isNotEmpty()) showOfferwallButton()

// 3. Mở tường:
val r = OfferwallManager.show("revu") { onWallClosed() }
if (!r.shown) logD(r.report)   // lý do cụ thể: consent, COPPA, allow-list, chưa khả dụng

// 4. Vendor callback (client hoặc S2S báo về) → trao thưởng:
val c = OfferwallManager.reportCompletion("revu", currencyEarned = 120, transactionId = tx)
if (c.accepted) grantCoins(c.currencyEarned)   // game tự trao
```
