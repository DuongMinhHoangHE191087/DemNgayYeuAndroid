# 38 — Hướng dẫn tích hợp: bật Monetization Brain trong 5 bước

**Ngày viết:** 2026-08-22
**Cập nhật:** 2026-08-24 — brain **đã được cắm vào luồng interstitial thật** trong `AdsHelper`.
**Trạng thái:** **218/218 unit test PASS**, `assembleDebug` + `assembleRelease` build sạch (JDK 17).

---

## A0. Đã cắm vào đâu (cập nhật 2026-08-24)

Trước bản này, `AdsBrain` tồn tại nhưng **không có call site nào trong `ads/`** — bật `enabled = true`
cũng không thay đổi được một impression nào. Nay ba móc sau đã có trong SDK:

| Móc | Vị trí | Tác dụng |
|---|---|---|
| Cổng quyết định interstitial | `AdsHelper.showInterstitial()` | Đặt **sau** mọi cổng tĩnh cũ → brain chỉ siết chặt hơn, không bao giờ nới lỏng |
| Chốt outcome | `AdsHelper.showInterstitial()` | `onInterstitialShown()` khi hiện được, `onAdSkipped()` khi không (hết fill, lỗi SDK, đang hiện ad khác) |
| Vòng đời phiên | `AppPluginManager.initPlugin()` + `AppPluginBase.onStart/onStop` | Cấp lại ngân sách mỗi phiên; app vào nền chốt quyết định treo thành "người chơi rời đi" |

Hệ quả: **studio không phải sửa gì trong app** để brain hoạt động ở lớp interstitial — chỉ cần bật.

**Hai chỗ CỐ TÌNH không cắm, không phải bỏ sót:**

1. **`showInterstitial(..., isDeltaTime = false)`** — call site truyền cờ này đang nói "lượt
   này do tôi quyết định" (splash/intro). Brain không phủ quyết ở đó vì nó không có đủ ngữ
   cảnh để đánh giá một luồng bắt buộc.
2. **`showRewarded()` không bị brain chặn.** Hàm này chạy khi người chơi **đã bấm nút** — chặn
   ở đó là chặn thứ họ chủ động yêu cầu, sai cả về UX lẫn về ý nghĩa của guardrail doc 14
   (guardrail nhắm vào việc *mời*, không phải việc *đáp ứng*). Cũng vì vậy SDK **không** tự
   gọi `onRewardedOffered()`: nó chỉ quan sát được lượt được chấp nhận, nên báo tự động sẽ
   đẩy `rewardedAcceptRateRecent` lên 1.0 vĩnh viễn và bẻ cong belief của mọi người chơi.
   Quyết định mời rewarded và báo `accepted = true/false` vẫn thuộc về app — xem bước 4.

## A. Nguyên tắc: mặc định TẮT

`AdsBrain.enabled = false` khi khởi tạo. Ở trạng thái tắt, mọi hàm `should*` trả về `true` — **hành vi y hệt khi chưa có brain**, luật tần suất tĩnh trong `AdsPlacementConfig` quyết định như trước.

Đây là quyết định thiết kế có chủ đích: bật brain là quyết định kinh doanh, không được là hệ quả phụ của việc nâng phiên bản SDK. Một studio cập nhật thư viện không bao giờ nên tự nhiên thấy mật độ quảng cáo của mình thay đổi.

## B. Năm bước

```kotlin
// 1. Bật — chỉ khi đã quyết định
AdsBrain.enabled = true

// 1b. Mở dần, đừng bật thẳng 100% (xem mục F). Điều khiển được từ xa qua policy artifact.
AdsBrain.rolloutFraction = 0.05

// 2. Báo vòng đời phiên — KHÔNG BẮT BUỘC nữa: SDK đã tự gọi bản không tham số với
//    player id ổn định của riêng nó. Chỉ gọi bản có tham số nếu app có id người dùng riêng
//    (tốt hơn: id đó sống qua cả lần cài lại app; hoặc truyền qua MonetizationSdk.configure { playerId }).
//    Id truyền vào CŨNG LÀ `user_id` của telemetry server SDK và id của web shop (PlayerIdSource, từ 1.4.0):
//    brain phân nhóm, experiment_exposure và user_id là một id (backend reconcile lớp 1). Các lần resume
//    (bản không tham số) giữ id này. Dùng id nội bộ mờ — không email, không số điện thoại.
AdsBrain.onSessionStart("player_123")

// 3. Interstitial — KHÔNG cần làm gì. `AdsHelper.showInterstitial()` đã tự hỏi brain và tự
//    chốt outcome. Giữ nguyên lời gọi hiện có:
AdsHelper.instance.showInterstitial(placement, 0, callback)

// 3b. Chào bán IAP — vòng cross-modal. Guardrail tự chặn khi người chơi đang bế tắc
//     hoặc đang chi dồn dập trong phiên (doc 45 mục G).
if (AdsBrain.shouldOfferIap(DecisionPoint.SHOP_OPEN)) {
    showOfferDialog()
    AdsBrain.onIapOfferShown()
}
// Lượt mua do IapHelper tự báo về — không cần gọi AdsBrain.onPurchase() thủ công.

// 4. Rewarded — guardrail đạo đức tự chặn khi chưa có thất bại thật
if (AdsBrain.shouldOfferRewarded()) {
    AdsHelper.instance.showRewarded(placement, callback)
}
AdsBrain.onRewardedOffered(accepted = userWatchedIt)

// 5. Nạp tín hiệu hành vi để segment dịch chuyển
AdsBrain.onLevelFail()
AdsBrain.onLevelWin()
AdsBrain.onPurchase(4.99)

// 6. Báo kết thúc phiên — quyết định còn treo sẽ được chốt là "người chơi rời đi"
AdsBrain.onSessionEnd()
```

Không cần tự dựng `PlayerState`, tự đếm quảng cáo mỗi phiên, hay tự gọi `observe()`/`feedback()` — facade quản lý hết.

## C. Ranh giới trách nhiệm

`AdsBrain` **không** tự hiện quảng cáo và **không** đụng vào `AdsHelper`. Nó chỉ trả lời "có nên hay không". Đây là nguyên tắc từ doc 06 mục 12: business rule và execution thuộc về caller; brain chỉ xếp hạng trong tập ứng viên hợp lệ mà caller đưa ra.

Hệ quả thực tế: bật brain không thể làm hỏng luồng quảng cáo hiện có, vì luồng đó vẫn do code cũ chạy. Brain chỉ thêm một câu hỏi `if` phía trước.

## D. Những gì chạy tự động khi bật

| Cơ chế | Tài liệu | Ghi chú |
|---|---|---|
| Phân khúc theo belief (4 latent type) | doc 24, 33 | Cần ≥2 phiên mới đủ tín hiệu |
| Kịch bản personalization | doc 26 | Dưới ngưỡng tin cậy thì dùng kịch bản trung tính |
| Guardrail cứng (đạo đức, chống gian lận) | doc 14, 27 | Không thể tắt bằng policy artifact |
| Điều tiết nhịp Lagrangian | doc 37 | Giãn quảng cáo thay vì dồn cục |
| Holdout 5% | doc 36 | Nhóm đối chứng để đo giá trị thật của brain |
| Sàn khám phá 3% | doc 36 | Giữ propensity > 0 cho OPE |
| Circuit breaker chi dồn dập | doc 29, doc 45 | Ngắt chào bán khi mua nhiều lần trong một phiên |
| Không chào bán lúc bế tắc | doc 29 | Chuỗi thua sâu → chặn `IAP_OFFER` |
| ComfortIndex chốt cuối phiên | doc 26, doc 40 | Event `brain_comfort`, kèm `coverage` |
| Decision log bền vững | doc 35 | Xuống đĩa trước khi gửi, replay khi mở lại app |
| Policy artifact từ Remote Config | doc 35 | Key `brain_policy_android` |

## E. Tắt khẩn cấp

Bốn mức, từ nhanh nhất tới triệt để nhất — ba mức đầu **không cần release app**:

1. **Lùi rollout về 0 qua Remote Config** — đẩy `{"version":"panic","experiment":{"rollout_fraction":0}}`
   vào `brain_policy_android`. Brain thôi điều khiển **mọi** player ngay lần fetch kế tiếp;
   luồng quảng cáo quay về đúng luật tĩnh cũ. Đây là mức triệt để nhất trong các mức từ xa.
2. **Kill switch personalization** — `{"version":"panic","enabled":false}`. Brain vẫn chạy
   nhưng mọi player về kịch bản trung tính. Dùng khi nghi ngờ *cá nhân hoá* sai, không phải
   toàn bộ cơ chế.
3. **`AdsBrain.enabled = false`** — nếu app có cờ điều khiển từ xa riêng.
4. **Gỡ lời gọi** — cần release; chỉ dùng khi muốn xoá hẳn.

Phân biệt mức 1 và mức 2 là quan trọng: mức 2 **không** tắt bandit, chỉ trung hoà kịch bản.
Sự cố nghi do brain nói chung thì phải dùng mức 1.

## F. Nghiệm thu trước khi bật diện rộng

```text
1. Mở Brain Playground trong app demo, xác nhận segment dịch chuyển đúng hướng
   khi mô phỏng hành vi (doc 25 mục F).
2. Bật cho 5% dân số trước: AdsBrain.rolloutFraction = 0.05, hoặc đẩy
   {"version":"...","experiment":{"rollout_fraction":0.05}} qua Remote Config.
   Nâng dần 0.05 → 0.2 → 1.0, mỗi bước đối chiếu với holdout.
3. Theo dõi 4 North Star Metrics (doc 26 mục C) — đặc biệt ComfortIndex,
   KHÔNG chỉ nhìn ARPDAU.
4. So sánh treatment vs holdout sau ít nhất 2 tuần; đừng kết luận sớm hơn vì
   hiệu ứng retention cần thời gian bộc lộ.
5. Đối chiếu số impression mất đi: event `show_full_brain_declined` tách riêng
   khỏi `not_allow_show`, nên biết được bao nhiêu lượt mất là do brain chứ
   không phải do luật tĩnh.
```

`rolloutFraction` mặc định là **1.0**, không phải 0. Công tắc an toàn là `enabled`; đặt hai
cổng cùng mặc định tắt sẽ tạo một no-op im lặng (bật brain mà không có gì xảy ra, không có
dấu hiệu nào để chẩn đoán). Vì vậy bước 2 là **thao tác bắt buộc của quy trình**, không phải
giá trị mặc định của code.

## Không xác minh được (ghi rõ)

1. Chưa chạy trên thiết bị thật với traffic thật — mọi khẳng định về hành vi đến từ 218 unit test, không phải quan sát production.
2. Reward hiện dùng **downstream continuation** (doc 39) chứ không còn nhị phân — đã hết tautological, nhưng vẫn **chưa phải incremental LTV** như công thức doc 11. Tín hiệu "người chơi có tiếp tục chơi không" là proxy hợp lý cho retention (pattern Pinterest), song không đo được giá trị dài hạn. Đây vẫn là khoảng cách lớn nhất giữa hiện trạng và mục tiêu ở doc 26.
3. `AdsBrain` gom tín hiệu tại client vì SDK chưa có feature store (doc 31 mục 1); độ chính xác thấp hơn tổng hợp phía server.
