# 52 — Battle Pass Engine: progression, hai track phần thưởng, catch-up (doc 23 mục C.3)

**Ngày viết:** 2026-08-25
**Trạng thái:** **408/408 unit test PASS**, backend 81/81, `assembleDebug` + `assembleRelease` sạch.

---

## A. Bài toán

Battle pass xuất hiện trong ~60% game top-grossing và chiếm 1–40% doanh thu của chúng
(doc 19). Nhưng các SDK subscription chuẩn (RevenueCat/Adapty) chỉ hiểu "entitlement =
access" — không hiểu "entitlement = mở track phần thưởng cao cấp của MÙA NÀY". Khoảng trống
doc 19 chỉ tên: **không ai bắc cầu virtual currency ↔ subscription/battle pass cho game**.
Mỗi studio vì thế tự ghép: cấu hình mùa, ngưỡng tier, phát thưởng, chống claim trùng,
catch-up cho người vào muộn.

Doc 19 cũng cho sự thật định hình thiết kế: battle pass **không phải auto-renewable
subscription** — nó là **non-renewing IAP mua một lần mỗi mùa**.

## B. Ba quyết định thiết kế

### B.1 Premium là non-renewing IAP theo mùa — không cần code xoá quyền

`premium_product_id` nằm trong cấu hình mùa; mua sản phẩm đó cấp entitlement qua
`Entitlements` (luồng IapHelper/PurchaseRouter sẵn có — **không cần nối thêm gì**). Mỗi mùa
dùng một product id khác, nên entitlement mùa cũ *tự động* không mở track premium mùa mới.
Cơ chế "hết mùa mất quyền premium" không cần dòng code nào — nó rơi ra từ thiết kế khoá
entitlement theo sản phẩm.

### B.2 Game sở hữu XP, SDK sở hữu luật

Định lượng XP (bao nhiêu mỗi trận, mỗi mission) là thiết kế game — SDK không quyết định hộ.
SDK giữ tổng XP, đổi tổng sang bậc theo ngưỡng cấu hình, phát event khi vượt bậc, và thi
hành luật: ngoài mùa không có XP, hết mùa không được claim. `xp_cap` chặn số liệu chạy
tung, `addXp` trả XP thực nhận để game hiển thị "+N XP" đúng (kể cả khi catch-up nhân đôi).

**Catch-up** (doc 23 yêu cầu): cấu hình `catch_up: {enabled, start_fraction, multiplier}` —
khi mùa đã trôi qua `start_fraction` thời lượng (mặc định 50%), mỗi điểm XP được nhân bội
số. Người mua pass giữa mùa đuổi kịp được. Đây là cơ chế chuẩn của các game top-grossing,
không phải phát minh của SDK.

### B.3 Claim là dữ liệu, không phải vật phẩm

`claim(tier, track)` thi hành luật — mùa đang chạy, bậc đã đạt, chưa claim lần nào, track
PREMIUM cần entitlement — rồi trả `ClaimResult(claimed, rewards, report)`. **Trao vật phẩm
là việc của game.** Cùng ranh giới "SDK không vẽ hộ" của paywall, dunning, win-back.
`claimable()` trả danh sách cặp (bậc, track) còn nhận được — để app vẽ chấm báo đỏ.

## C. Cấu hình từ xa

Khoá riêng `battle_pass_android` — nhịp đổi là nhịp mùa (vài tuần/mùa), nhanh hơn cả
paywall:

```json
{
  "version": "bp_v1",
  "season_id": "season_3",
  "start_ts_ms": 1725148800000,
  "end_ts_ms": 1727740800000,
  "premium_product_id": "bp_season_3_premium",
  "xp_cap": 100000,
  "tiers": [
    {"tier": 1, "xp_required": 0,
     "rewards_free":    [{"id": "f1", "type": "gem",  "amount": 10}],
     "rewards_premium": [{"id": "p1", "type": "skin", "amount": 1}]},
    {"tier": 2, "xp_required": 100, "rewards_free": [], "rewards_premium": []}
  ],
  "catch_up": {"enabled": true, "start_fraction": 0.5, "multiplier": 1.5}
}
```

Quy tắc nhà: JSON hỏng/thiếu trường/thứ tự bậc sai → **từ chối, giữ nguyên mùa đang chạy**.
Một quyết định đáng nói: cấu hình bậc xếp sai thứ tự (XP không tăng dần) bị TỪ CHỐI chứ
không được sort lại hộ — im lặng sửa sẽ giấu lỗi người cấu hình, và thứ tự bậc có ý nghĩa
hiển thị. `season_id` mới khác mùa đang chạy thì XP và lịch sử claim đặt lại từ đầu —
mùa mới là cuộc đua mới.

## D. Đo lường

Hai event riêng vì chúng trả lời hai câu hỏi khác nhau:

- `battle_pass_tier` (`from_tier → to_tier`, `season_id`, `is_premium`, version) — funnel
  **tiến độ/giữ chân**: người chơi leo bậc nhanh thế nào, catch-up version nào giúp người
  mua muộn đuổi kịp. Chỉ phát khi bậc thật sự tăng.
- `battle_pass_claim` (`tier`, `track`, `reward_count`, `season_id`, `is_premium`) — funnel
  **chuyển đổi premium**: bao nhiêu người mua pass rồi claim track premium (claim PREMIUM
  của người không mua pass = 0 về định nghĩa).

Backend: `normalize.py` có nhánh flatten cho cả hai; schema thêm `season_id`, `from_tier`,
`to_tier`, `tier`, `track`, `reward_count`, `is_premium`, `battle_pass_version`. Không
dedup_key — dedup nằm ở nguồn (SDK chỉ phát khi bậc thật sự tăng; claim một lần).

## E. Giới hạn — ghi rõ

1. **Toàn bộ tiến độ sống trên máy** (prefs). Xoá app là mất XP và lịch sử claim; nguồn sự
   thật đúng là phía server theo user_id — chưa có backend.
2. **Chống claim trùng bằng trạng thái cục bộ** — máy root sửa prefs có thể claim lại.
   Phần thưởng battle pass là tài nguyên game (không quy đổi tiền thật), rủi ro cùng hạng
   với `removeAds` trong Entitlements — chấp nhận được tới khi có backend.
3. **Không có đồng bộ đa thiết bị** — cùng một tài khoản chơi hai máy là hai tiến độ.
4. SDK không quyết định ĐỊNH LƯỢNG XP — chỉ đặt ngưỡng bậc, bội số catch-up, trần XP.
   Cân bằng kinh tế phần thưởng (amount/type) là việc studio.

## F. Tích hợp (drop-in)

```kotlin
// Cấu hình đã tự nạp qua FirHelperConfig (khoá battle_pass_android).
// Game chỉ cần ba việc:

// 1. Báo XP khi người chơi đáng nhận:
val gained = BattlePass.addXp(25)          // SDK tự nhân catch-up, tự phát event vượt bậc
showXpToast("+$gained XP")

// 2. Mua premium track qua luồng IAP sẵn có (không cần nối thêm):
PurchaseRouter.purchase(activity, "bp_season_3_premium") { ... }
// Entitlements.grant tự chạy → BattlePass.isPremiumActive() = true

// 3. Claim — SDK trả phần thưởng, game tự trao:
val r = BattlePass.claim(tier = 3, track = BattlePass.Track.FREE)
if (r.claimed) r.rewards.forEach { grant(it.type, it.amount) }
else showHint(r.report)                    // "chưa đạt bậc 3", "chưa sở hữu premium mùa này"...

// Vẽ chấm báo:
val redDots = BattlePass.claimable()       // List<Pair<bậc, track>>
```
