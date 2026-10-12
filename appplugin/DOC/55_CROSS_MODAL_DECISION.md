# 55 — Quyết định cross-modal: REWARDED_AD vs IAP_OFFER vs NO_ACTION tại cùng một điểm quyết định (doc 23 mục E.3, Phase 4)

**Ngày viết:** 2026-08-25
**Trạng thái:** 456/456 unit test, backend 89/89, hai AAR build sạch.

---

## A. Bài toán

Trước doc này, brain có hai cổng NHỊ PHÂN tách rời:

- `shouldInterstitial` — chèn interstitial hay không;
- `shouldOfferIap` — chào bán IAP hay không.

Mỗi cổng tự quyết trong không gian hai arm (`X` vs `NO_ACTION`). Nhưng tại CÙNG một
khoảnh khắc — người chơi vừa thua màn, currency sắp cạn, mở shop — game phải chọn giữa
**nhiều bề mặt kiếm tiền cùng lúc**: mời xem rewarded, chào gói IAP, hay im lặng. Nếu hai
cổng nhị phân chạy độc lập, chúng có thể đồng thời đòi khoảnh khắc đó (double-dip), hoặc
không cổng nào chịu trách nhiệm chọn bề mặt TỐT NHẤT cho người chơi này.

Đây là điểm doc 23 gọi tên là khác biệt với mọi vendor: quyết định cross-modal theo
**propensity trả tiền** — người nhiều khả năng trả tiền thấy IAP_OFFER, người không trả
thấy REWARDED_AD, người đang bế tắc thấy NO_ACTION.

## B. API

```kotlin
val d = AdsBrain.chooseCrossModal(
    decisionPoint = DecisionPoint.LEVEL_FAIL,
    allowRewarded = true,      // game có thể cấm từng arm (ví dụ hết ad fill)
    allowIapOffer = true,
    baseline = ActionMode.NO_ACTION  // hành vi của game khi KHÔNG có brain
)
when (d.action) {
    ActionMode.REWARDED_AD -> showRewardedPrompt()
    ActionMode.IAP_OFFER   -> showIapOffer()
    else                   -> d.action // NO_ACTION hoặc chính baseline
}
```

`CrossModalDecision(action, isHoldout)` — cờ holdout để game ghi nhận/log nếu muốn.

### B.1 `baseline` — hành vi cũ của game, không phải hằng số

Khi brain tắt, người chơi ngoài rollout, hoặc cả hai arm bị cấm, `chooseCrossModal` trả
về **baseline của game** — không ép về `NO_ACTION`. Game nào mặc định luôn mời booster khi
thua màn thì tắt brain vẫn mời booster. Đây là quy tắc "tắt = hành vi cũ" áp dụng đúng
nghĩa: brain không được chiếm quyền quyết định mặc định của game khi nó không chạy.

### B.2 Tập ứng viên co theo allow-flags

`NO_ACTION` LUÔN có mặt (an toàn là lối thoát mặc định); `REWARDED_AD`/`IAP_OFFER` thêm
vào theo `allowRewarded`/`allowIapOffer`. Cả hai cùng false → trả baseline NGAY, **không
hỏi engine** — không có quyết định nào để ra thì không ghi quyết định một-arm vào log OPE.

### B.3 Feedback qua đúng đường ống treo sẵn có

Arm được chọn trở thành quyết định treo (`pendingRequest`/`pendingResponse`) như mọi quyết
định khác:

- `REWARDED_AD` chốt khi người chơi thực sự xem rewarded (`onRewardedOffered`);
- `IAP_OFFER` chốt khi lượt mua tiếp theo xảy ra (`onPurchase` chỉ ghi công nếu quyết định
  treo mang mode `IAP_OFFER`).

Không có đường feedback thứ hai — một cơ chế, một nguồn sự thật về credit.

## C. Guardrail đạo đức là filter CỨNG trong không gian đa arm

Điểm dễ hỏng nhất khi gộp arm: một guardrail vốn chặn arm này có thể bị lách bằng arm kia.
Không:

- **ETH_FRUSTRATION_EXPLOIT**: đang trong chuỗi thua bế tắc → cấm `IAP_OFFER` kể cả khi
  arm kia là rewarded. Không kiếm tiền trên sự bế tắc, bất kể bề mặt.
- **ETH_MANUFACTURED_FAIL**: `REWARDED_AD` chỉ được mời sau một lượt thua THẬT
  (`failStreakCurrent > 0`) — giữ nguyên trong không gian gộp.
- **Circuit breaker phiên** (doc 29 mục 5.3): 3 lượt mua trong một phiên → ngắt
  `IAP_OFFER` khỏi tập ứng viên.
- Holdout vẫn chạy qua guardrail: nhóm đối chứng nhận baseline nhưng KHÔNG được bỏ hàng
  rào an toàn — holdout là để đo giá trị cá nhân hoá, không phải để bỏ bảo vệ.

## D. Holdout trong quyết định gộp

Người trong nhóm đối chứng nhận `baseline` với xác suất 1.0 và `isHoldout = true`. Cùng
một `HoldoutManager` (namespace `brain_holdout_v1`) dùng chung cho ads, paywall và quyết
định gộp — một người chơi chỉ thuộc HOẶC không thuộc nhóm đối chứng, nhất quán trên mọi
bề mặt. Phép đo incrementality vì vậy trả lời đúng câu hỏi: **toàn bộ cỗ máy** đáng giá
bao nhiêu, không phải từng bánh răng.

## E. Vì sao KHÔNG gộp luôn paywall vào đây

Paywall quyết định ở tầng khác (có hiện paywall khi mở shop không), không phải ở khoảnh
khắc micro trong gameplay. Gộp nó vào tập arm sẽ biến một quyết định 3-arm thành quyết
định vòng đời — sai nhịp. Paywall đã có cổng riêng `shouldOfferIap` và holdout riêng
đã thống nhất cùng namespace (doc 52+).

## F. Tích hợp (drop-in)

```kotlin
// Tại điểm quyết định của game (thua màn, sắp hết currency, mở shop...):
val d = AdsBrain.chooseCrossModal(
    DecisionPoint.LEVEL_FAIL,
    allowRewarded = AdsHelper.hasRewardedFill(),   // hết fill thì cấm arm đó
    allowIapOffer = !IapHelper.isRateLimited(),
    baseline = ActionMode.NO_ACTION                // hoặc hành vi mặc định của game
)
when (d.action) {
    ActionMode.REWARDED_AD -> showRewardedPrompt()
    ActionMode.IAP_OFFER   -> showOfferSheet()
    else                   -> { /* NO_ACTION — giữ màn hình yên */ }
}
// Sau đó feedback như cũ:
AdsBrain.onRewardedOffered(true)   // khi người chơi xem rewarded
AdsBrain.onPurchase(usd)           // khi mua — tự chốt credit nếu arm là IAP_OFFER
```

Không cần cấu hình remote mới — `chooseCrossModal` dùng chung cấu hình brain hiện có
(`enabled`, `rolloutFraction`, policy). Mặc định brain TẮT = hàm này trả baseline.

## G. Giới hạn — ghi rõ

1. Tập arm của cổng này là 3 (NO_ACTION / REWARDED_AD / IAP_OFFER). Half còn lại của
   full action space (difficulty assist + free gift) ĐÃ được nối dây qua `chooseAssist`
   — xem doc 58; social prompt vẫn để phase sau.
2. `baseline` do game tự khai báo — SDK không thể biết hành vi mặc định thật của game
   nếu game không nói. Khai sai thì phép đo holdout lệch; đây là trách nhiệm tích hợp.
3. Mỗi lần gọi `chooseCrossModal` là một lần hỏi engine — game không nên gọi trong vòng
   lặp frame; gọi tại điểm quyết định thực sự.
