# 53 — Recurring Grants: cầu nối VIP ↔ virtual currency (doc 19, doc 23 mục C.3)

**Ngày viết:** 2026-08-25
**Trạng thái:** 424/424 unit test, backend 84/84, hai AAR build sạch.

---

## A. Bài toán

Doc 19 chỉ tên khoảng trống rõ nhất giữa subscription SDK và game:

> "Subscription SDK hiểu 'entitlement = access', không hiểu 'entitlement = 500 gems/ngày
> + battle pass tier unlock'."

RevenueCat/Adapty mở tính năng, bỏ quảng cáo — hết. Game thì entitlement còn là **dòng vật
phẩm định kỳ**: sở hữu VIP được 500 gem mỗi ngày, một vé quay mỗi tuần. Không SDK nào làm
phần này; mỗi game tự chế, và chế lệch ba chỗ: quên phát khi không mở app đúng ngày, phát
trùng khi mở hai lần trong ngày, mất lịch sử khi xoá app.

`RecurringGrants` trả lời đúng ba câu hỏi: **ai** sở hữu, **bao giờ** đến lượt, và **chống
phát trùng**. Doc 23 xếp nó vào "game-aware layer riêng biệt — không vendor nào có".

## B. Ba quyết định thiết kế

### B.1 Không truy lĩnh (no stacking)

Lỡ mười ngày không mở app, khi quay lại chỉ nhận MỘT chu kỳ — không phải mười. Đây là lựa
chọn thiết kế chuẩn của đa số game top-grossing: truy lĩnh tạo lạm phát tài nguyên và phá
cân bằng kinh tế ngay lúc cần kéo người chơi lại nhất. Ghi rõ như một quyết định, không
phải sơ suất — studio nào muốn cộng dồn biết chỗ phải tự làm.

### B.2 Chu kỳ tính từ lần claim trước, không theo ngày dương lịch

`period_hours` (cấu hình từng dòng; 24 cho gem ngày, 168 cho vé tuần, 4 cho sự kiện). Người
nhận lúc 23h59 rồi 00h01 không bị "mất ngày"; chu kỳ là thời gian thật kể từ lần nhận
trước. Đánh đổi: không có "reset lúc 0h" kiểu server game — đổi lại không cần server,
drop-in.

### B.3 Sở hữu = Entitlements HOẶC SubscriptionLifecycle ACTIVE

Hai nguồn vì hai loại sản phẩm: gói VIP vĩnh viễn (non-consumable) sống trong
`Entitlements`; subscription tháng được xác nhận qua trạng thái `ACTIVE` của
`SubscriptionLifecycle` — kể cả khi Entitlements chưa kịp đồng bộ sau lượt mua. Sub rơi vào
`LAPSED` (churn) thì đặc quyền tắt ngay — đúng tinh thần "hết quyền lợi là hết đặc quyền"
của vòng đời subscription (doc 51).

## C. Cấu hình từ xa

Khoá riêng `recurring_grants_android` — gắn với danh mục gói VIP, nhịp đổi theo gói bán:

```json
{
  "version": "rg_v1",
  "enabled": true,
  "grants": [
    {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500},
    {"product_id": "vip_pass", "period_hours": 168, "reward_type": "ticket", "reward_amount": 1}
  ]
}
```

Một sản phẩm mang được nhiều dòng phát (gem ngày + vé tuần), kiểm độc lập. Dòng trùng hệt
(sản phẩm + chu kỳ + loại) bị loại bỏ kèm chú thích trong report. Quy tắc nhà: JSON hỏng →
giữ bản đang chạy; `enabled=false` tắt toàn bộ.

## D. Đo lường

Event `recurring_grant_claim` (`product_id`, `reward_type`, `reward_amount`, `period_hours`,
version). Câu hỏi kinh doanh nó trả lời: **đặc quyền nào kéo người ta GIỮ subscription?**
Tần suất nhận gem ngày của người sắp churn so với người ở lại — tín hiệu retention của đặc
quyền mạnh hơn cả lý do mua ban đầu. Không dedup_key: SDK chặn trùng theo chu kỳ ở nguồn;
claim hôm nay và tháng sau đều là sự kiện thật.

## E. Giới hạn — ghi rõ

1. Lịch sử claim sống trên máy: xoá app là mất, cài lại nhận được chu kỳ hiện tại — cùng
   hạng rủi ro với battle pass claim, chấp nhận được tới khi có backend theo user_id.
2. Không đồng bộ đa thiết bị.
3. SDK không quyết định SỐ LƯỢNG vật phẩm — cân bằng kinh tế là của studio, cấu hình chỉ
   mang nó đi.

## F. Tích hợp (drop-in)

```kotlin
// Cấu hình tự nạp qua FirHelperConfig. Game chỉ cần:

// 1. Vẽ nút nhận quà khi có dòng đến lượt (gọi ở màn hình chính):
val due = RecurringGrants.dueGrants()
if (due.isNotEmpty()) showVipGiftButton(due.size)

// 2. Khi người chơi bấm nhận:
val r = RecurringGrants.claim(productId = "vip_pass", rewardType = "gem")
if (r.claimed) grantToPlayer(r.rewardType, r.rewardAmount) // game tự trao
else showHint(r.report)                                     // "chưa đến lượt — còn 7h"...
```
