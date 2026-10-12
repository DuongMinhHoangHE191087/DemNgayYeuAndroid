# 45 — Lớp IAP / Play Billing: SDK bắt đầu nhìn thấy tiền

**Ngày viết:** 2026-08-24
**Cập nhật:** 2026-08-24 (cùng ngày) — thêm **lớp quyết định chào bán** và nối `ComfortIndex` vào vòng đời.
**Cập nhật:** 2026-08-25 — vế xác thực Play Billing phía server: cơ chế hoàn tất (`play_verify.py` + `GET /verify/purchases`), còn lại là deploy với service account thật.
**Trạng thái build:** `testDebugUnitTest --rerun-tasks` + `assembleDebug` + `assembleRelease` (có R8) đều PASS, **218/218 test**.

---

## A. Vì sao mảnh này quan trọng hơn kích thước của nó

Trước bản này, `ActionMode.IAP_OFFER` là một enum rỗng: SDK không có cách nào biết ai đã mua gì.
Hệ quả dây chuyền — ba thứ đã spec kỹ nhưng không chạy được:

| Thứ đã spec | Vì sao không chạy được | Nay |
|---|---|---|
| Cross-modal IAA↔IAP decisioning (doc 23 mục E.3 — định vị khác biệt DUY NHẤT) | Không biết ai là payer | Có tín hiệu payer |
| ARPDAU (doc 26 mục C.1) | Chỉ đo được vế IAA | Đủ cả hai vế |
| Doanh thu theo kênh (doc 26 mục C.2) | Không có giao dịch nào để phân loại | Event có `channel` |

## B. Ba file, ba trách nhiệm tách bạch

| File | Trách nhiệm |
|---|---|
| [IapModels.kt](../src/main/java/com/app/plugin/iap/IapModels.kt) | Model riêng, **không để lộ kiểu của Play Billing** ra API công khai |
| [Entitlements.kt](../src/main/java/com/app/plugin/iap/Entitlements.kt) | Quyền lợi đang sở hữu + cây cầu DUY NHẤT sang `AdsHelper.setRemoveAds()` |
| [FxRates.kt](../src/main/java/com/app/plugin/iap/FxRates.kt) | Quy đổi USD **chỉ khi biết chắc tỉ giá**, ngược lại trả `null` |
| [IapHelper.kt](../src/main/java/com/app/plugin/iap/IapHelper.kt) | Lớp bọc `BillingClient` — kết nối, giá, mua, khôi phục, acknowledge/consume |

`implementation` chứ không `api`: API công khai chỉ dùng model riêng, nên nâng cấp billing bên
trong không bắt app tích hợp phải sửa theo. Đây không phải chi tiết vụn — v8 của Google đã đổi
chữ ký `queryProductDetailsAsync` một lần rồi.

## C. Tích hợp

```kotlin
// 1. Khai báo sản phẩm. `consumable` app PHẢI tự khai — xem mục E.2.
IapHelper.setProducts(
    inapp = listOf("coin_pack_small", "no_ads_forever"),
    subs  = listOf("premium_monthly"),
    consumable = setOf("coin_pack_small")
)

// 2. Kết nối (sau AppPluginManager.initPlugin)
IapHelper.init(context)

// 3. Sản phẩm nào tắt quảng cáo — nên đẩy từ Remote Config thay vì hard-code
Entitlements.setRemoveAdsProducts(setOf("premium_monthly", "no_ads_forever"))

// 4. Mua
IapHelper.purchase(activity, "premium_monthly") { result ->
    when (result) {
        is IapResult.Success   -> showThanks()
        is IapResult.Pending   -> showPendingNotice()   // CHƯA được cấp quyền lợi
        IapResult.UserCanceled -> {}
        IapResult.AlreadyOwned -> {}
        is IapResult.Failed    -> showError(result.message)
    }
}

// 5. Đọc quyền lợi ở bất cứ đâu — theo TỪNG định dạng (Điều 13.4): gói rẻ `INTERRUPTIVE` chỉ gỡ banner,
//    rect, native collapsible và toàn màn hình (#15); rewarded và native inline vẫn còn. Không dùng `hasRemoveAds()` để ẩn mọi chỗ
//    quảng cáo: nó đúng với MỌI mask khác NONE, nên ẩn cả nút rewarded của người mua gói rẻ.
val mask = Entitlements.removeAdsMask()
if (RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_BANNER)) hideBannerSlots()
if (RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_REWARDED)) hideRewardedButtons()
```

**SDK tự làm, app không phải gọi:** acknowledge biên lai (bỏ sót 3 ngày là Google tự hoàn tiền),
đồng bộ quyền lợi mỗi lần vào foreground, tắt quảng cáo cho subscriber, gửi event doanh thu,
báo tín hiệu payer cho brain.

**SDK cố tình không tự làm:** `IapHelper.init()` không được gọi tự động trong `AppPluginManager` —
SDK không biết product id của app, nên tự mở kết nối billing cho một app không bán gì chỉ là
lãng phí.

## D. Hai quyết định thiết kế đáng giải thích

### D.1 Không bao giờ bịa tỉ giá

Store trả giá theo tiền tệ địa phương. Nhét thẳng con số đó vào `purchaseValueLifetimeUsd` sẽ
ghi một giao dịch 99.000 VND thành "99000 USD" — sai kiểu không bao giờ crash nên không ai
phát hiện, và mọi thứ đọc trường đó về sau (LTV, ComfortIndex, báo cáo doanh thu) đều hỏng theo.

Nhúng sẵn bảng tỉ giá vào code cũng không phải lời giải: tỉ giá đổi hằng ngày, app hàng tháng
mới release. Vì vậy **chỉ `USD = 1.0` là hằng số biên dịch**; tỉ giá khác nạp từ policy artifact
(`fx_rates`) và cập nhật được mà không cần release.

Không quy đổi được **không làm mất dữ liệu**: event `iap_purchase` luôn gửi `price_micros` +
`currency_code` nguyên bản, backend có tỉ giá thật để quy đổi đúng. Chỗ duy nhất bị ảnh hưởng
là tổng USD trên máy — và nó được để trống một cách trung thực.

### D.2 Guardrail payer đã phải sửa theo

Hệ quả trực tiếp của D.1, và là loại lỗi chỉ lộ ra khi ghép hai lớp lại:

`GuardrailEngine` nhận diện payer bằng `purchaseValueLifetimeUsd > 0.0`. Một người trả 9,99 EUR
trên máy chưa nạp tỉ giá EUR có tổng USD bằng **0** — tức người vừa trả tiền sẽ tiếp tục bị dội
quảng cáo như người chưa bao giờ trả đồng nào, **im lặng và chỉ ở một số thị trường**.

Đã sửa: nhận diện payer bằng `purchaseCountLifetime > 0`. Đó vốn là phép thử đúng — "đã từng
trả tiền chưa" là câu hỏi về số lượt, không phải về số tiền. Xem
[PayerProtectionTest.kt](../src/test/java/com/app/plugin/brain/PayerProtectionTest.kt).

## E. Giới hạn phải nói rõ

1. **Xác thực biên lai phía server — cơ chế đã xong, chờ deploy credential.** Quyền lợi ở đây
   tin vào những gì Play Billing trả về trên máy. Với `removeAds` thì rủi ro chấp nhận được —
   hậu quả tệ nhất của việc bị qua mặt là một người xem ít quảng cáo hơn. **Không được dùng
   lớp này để cấp vật phẩm có giá trị quy đổi cho tới khi adapter chạy với service account
   thật.** Doc 23 mục C.2 vẫn đúng: server-side entitlement là nguồn sự thật duy nhất, và nó
   thuộc server của studio — `compute()` phía dưới chỉ trả lời "Google xác nhận biên nhận này
   thế nào", KHÔNG cấp hay thu hồi quyền lợi.

   Vế ĐẦU VÀO của xác thực đã hoàn tất: event `iap_purchase` nay mang `purchase_token` — biên
   nhận Play Billing — chảy trong log pipeline (`iapPurchaseEventData` phía SDK → flatten và cột
   `purchase_token` STRING trong `events_raw` phía backend, hai đầu đều có test khoá).

   Vế ĐẦU RA (cơ chế xác thực) cũng đã hoàn tất trong `play_verify.py`: `compute()` phân loại
   TỪNG giao dịch thành bảy ngăn không gộp mờ — `verified`, `order_mismatch` (token thật nhưng
   orderId của Google khác hàng log), `canceled_or_refunded`, `invalid_token` (Google không biết
   token), `pending`, `unverifiable_no_token` (độ phủ — bản SDK cũ), `skipped_non_play` (web-shop
   đi đường khác). `HttpPlayVerifier` hỏi androidpublisher v3 — toàn bộ logic dựng URL / đọc đáp
   án tách khỏi việc lấy OAuth token (tiêm `fetch`), `google-auth` là extra tuỳ chọn `[playverify]`
   import lười đúng pattern `google-cloud-bigquery`. `GET /verify/purchases` chỉ hoạt động khi đặt
   đủ `BRAIN_PLAY_CREDENTIALS` + `BRAIN_PLAY_PACKAGE`; chưa cấu hình thì **503 nói thẳng chứ
   không trả kết quả giả** — một dashboard xanh từ verifier giả nguy hiểm hơn một endpoint từ
   chối. Test khoá: backend 131/131 (stub + module google-auth giả + fetch tiêm; phần duy nhất
   chưa phủ là trao đổi credential thật — xác nhận khi deploy).

   Kênh web-shop không có token Play (trường chuỗi rỗng, phân biệt qua `channel`) — con
   đường xác thực của nó là server studio gọi `grantFromServer` (doc 47 mục G).

   Đối soát gian lận THUẦN trên hàng đã lưu (chạy được ngay cả khi chưa có credential dịch vụ):
   `GET /reconcile/purchases` (`purchase_reconcile.py`) bắt ba nghi vấn cổ
   điển — cùng token hoặc order_id xuyên ≥2 tài khoản, và token gắn nhiều đơn hàng ngoài
   subscription (subscription gia hạn giữ token sinh order mới là hợp lệ, được miễn).
2. **SDK không đoán sản phẩm nào là consumable.** Mặc định là acknowledge (giữ quyền lợi); app
   khai báo consumable qua `setConsumableProducts`. Đoán sai theo hướng consume sẽ **xoá vĩnh
   viễn quyền lợi người dùng đã trả tiền** — bất đối xứng rủi ro quá lớn để đoán.
3. **Chưa có paywall, chưa có purchase router đa kênh.** `PurchaseChannel.WEB_SHOP` đã có trong
   enum nhưng không có đường thực thi — để sẵn cho schema event không phải đổi. Đó là Phase 1
   của doc 23, vẫn chưa làm.
4. ~~**Chưa có decision point IAP.**~~ **ĐÃ LÀM** — xem mục G. Thứ tự được giữ đúng như đã nêu:
   guardrail bảo vệ xây TRƯỚC, `shouldOfferIap()` xây SAU.
5. **Chi phí kích thước: +0,34 MB.** APK demo 32,13 MB → 32,49 MB, đo sau `--rerun-tasks`
   (build sạch hoàn toàn). Doc 31 mục 8 ghi ngưỡng đáng lo là **+6 MB ≈ −1% tỉ lệ cài đặt** —
   còn rất xa.

   > **Đính chính:** bản đầu của mục này ghi "+4,6 MB", đo từ một lần build tăng dần khi thư
   > mục `build/` còn lẫn file rác `... 2.class` do trình đồng bộ thư mục `Documents` tạo ra.
   > Con số đó sai và làm phóng đại rủi ro. Bài học: **luôn đo kích thước artifact sau build
   > sạch**, không đo sau build tăng dần.

## F. Thay đổi kèm theo, ngoài package iap

| Thay đổi | Vì sao |
|---|---|
| `compileSdk` 34 → 36 (cả 2 module) | Billing 9.0.0 kéo `androidx.core 1.15.0`, yêu cầu 35+. `targetSdk` giữ nguyên 34 — không đổi hành vi runtime |
| `LogEventServerHelper.getVersionName` | `versionName` là nullable thật; compileSdk 34 chưa gắn `@Nullable` nên Kotlin không bắt lỗi, từ 35 thì bắt |
| `EventNames.IAP_PURCHASE` (trước 1.4.0: `Constants.LogEvent`) | Event doanh thu, vế còn lại của ARPDAU |
| `BrainPolicy`: `fx_rates`, `remove_ads_products` | Sửa được cấu hình sai từ xa — một subscriber bị bỏ sót khỏi danh sách remove-ads sẽ tiếp tục thấy quảng cáo dù đã trả tiền |
| `AppPluginBase.onStart`: `restorePurchases()` | Huỷ sub / hoàn tiền / mua ở máy khác đều xảy ra NGOÀI app, không có event nào báo về |

## Không xác minh được (ghi rõ)

1. Chưa test với giao dịch thật trên Play Console (cần app đã publish + tài khoản tester). Toàn
   bộ khẳng định về hành vi đến từ 218 unit test và API đã đối chiếu trực tiếp với bytecode của
   `billing-9.0.0.aar`, **không phải** từ quan sát giao dịch thật.
2. `IapHelper` chưa có unit test riêng — nó là lớp bọc mỏng quanh `BillingClient`, không tách
   được khỏi Android nếu không thêm Robolectric hoặc một interface trung gian. Phần logic tách
   được (`FxRates`, `Entitlements`) đã có test đầy đủ.
3. Đường xử lý giao dịch PENDING chưa được kiểm chứng thực tế — cần thanh toán tiền mặt hoặc
   luồng phê duyệt của phụ huynh để tạo ra tình huống đó.


---

## G. Lớp quyết định chào bán (bổ sung 2026-08-24)

### G.1 Guardrail xây trước, quyết định xây sau

Thứ tự này không phải hình thức. Một `shouldOfferIap()` không có guardrail sẽ để bandit tự do
tối ưu doanh thu chào bán — và cách tối ưu hiệu quả nhất luôn là chào bán đúng lúc người chơi
dễ tổn thương nhất. Cơ chế phải tồn tại trước khi cái nút tồn tại.

Bốn guardrail, ánh xạ thẳng từ doc 29:

| Mã | Loại | Cơ sở |
|---|---|---|
| `IAP_VELOCITY_BREAKER` | **HARD** | doc 29 mục 5.3 số 4 — "circuit breaker khi phát hiện deposit-frequency spike trong phiên"; mục 5.2 xác định đây là *marker at-risk mạnh nhất và dễ đo nhất trong IAP* |
| `ETH_FRUSTRATION_EXPLOIT` | **HARD** | doc 29 mục 10.2 câu Q4 — cường độ cơ chế không được tăng theo mức độ dễ tổn thương |
| `IAP_OFFER_SESSION_CAP` | RESOURCE | trần tần suất chào bán mỗi phiên |
| `IAP_OFFER_COOLDOWN` | RESOURCE | giãn cách giữa hai lần chào bán |

**Vì sao hai cái đầu là HARD chứ không RESOURCE.** Xếp vào RESOURCE sẽ biến chúng thành khoản
phạt mềm mà bandit có thể "mua lại" bằng đủ doanh thu — mà doanh thu từ đúng nhóm chi dồn dập
thì theo doc 29 mục 4.1 luôn đủ lớn để mua. Bảo vệ người dễ tổn thương không được phép có giá.
Đây là đúng lập luận mà doc 28 mục E.4 dùng để phân đôi `HARD`/`RESOURCE` ngay từ đầu.

**Ranh giới của `ETH_FRUSTRATION_EXPLOIT`.** Không phải "có thua thì không được chào bán" —
chào bán sau vài lần thua là mẫu thiết kế game hoàn toàn bình thường. Ranh giới nằm ở chỗ mức
sẵn lòng trả **tăng theo độ dài chuỗi thua**, nên chào bán càng lúc bế tắc càng "hiệu quả", và
đó chính xác là lý do không được làm. Ngưỡng mặc định 5, kẹp cứng không cho artifact nâng lên.

**Thứ không xây, cũng theo doc 29.** Mục 5.3 số 5 xếp risk scoring 3 màu kiểu Playscan vào nhóm
"dịch kém": false positive cao, người chơi phản ứng tiêu cực khi bị gán nhãn, và **không có bằng
chứng giảm harm**. Vì vậy hệ thống này không có `vulnerabilityScore` nào cả — chỉ có các ngưỡng
hành vi cụ thể, quan sát được, giải thích được.

### G.2 `AdsBrain.shouldOfferIap()` — vòng cross-modal khép lại

```kotlin
if (AdsBrain.shouldOfferIap(DecisionPoint.SHOP_OPEN)) {
    showOfferDialog()
    AdsBrain.onIapOfferShown()
}
// khi mua thành công, IapHelper tự gọi AdsBrain.onPurchase()
```

`SHOP_OPEN` (người chơi tự mở cửa hàng) và `CURRENCY_LOW` (hết tài nguyên) được học **riêng**,
không gộp một arm: hai bối cảnh khác nhau về mặt đạo đức nên cũng phải khác nhau về mặt chính sách.

**Ghi công đúng quyết định.** `onIapOfferShown()` cố tình KHÔNG đánh dấu là đã giao được giá trị —
hiện một gói mà không ai mua thì chưa tạo ra gì. Giá trị chỉ được ghi khi `onPurchase()` chốt lại,
và chỉ khi quyết định đang treo thật sự là `IAP_OFFER`. Đánh dấu vô điều kiện sẽ ghi công một lượt
mua cho quyết định *interstitial* đang treo, và bandit học sai theo hướng có lợi cho quảng cáo.

### G.3 ComfortIndex thôi là dead code

Trước bản này `ComfortIndex` tồn tại nhưng **không có call site nào** — một North Star Metric
không được tính ở đâu thì trên thực tế là chưa có. Nay được chốt mỗi cuối phiên và gửi qua event
`brain_comfort`, đúng việc mà doc 26 mục E.1 đã yêu cầu.

Độ phủ **vẫn là 2/5 và `isTrustworthy()` vẫn trả false** — có billing rồi cũng không lấp được ba
thành phần còn thiếu (organic purchase probability cần causal model; refund và retention sau mua
cần theo dõi mà SDK chưa có). Con số này dùng để theo dõi xu hướng, chưa dùng để ra quyết định.

`policy_aggressiveness` đi cùng event nhưng **không** nằm trong công thức — đúng theo đính chính
đã ghi ở đầu `ComfortIndex.kt`.

### G.4 Một lỗi bắt được khi tự rà lại

Bản đầu của đoạn parse `iap.frustration_streak_threshold` kẹp theo **giá trị hiện tại** của
guardrail thay vì theo hằng số:

```kotlin
clampUpper(it.toInt(), guardrails.frustrationStreakThreshold, ...)   // SAI
```

Hệ quả: mỗi lần nạp artifact ngưỡng lại tụt xuống một nấc và không bao giờ phục hồi được — một
cái bẫy ratchet chỉ lộ ra sau nhiều lần fetch, tức sau nhiều ngày chạy thật. Đã đổi sang
`HARD_MAX_FRUSTRATION_STREAK_THRESHOLD`.

## H. Không xác minh được (bổ sung cho mục G)

1. Bốn ngưỡng chào bán (`3` offer/phiên, `10` phút cooldown, `3` lượt mua ngắt, chuỗi thua `5`)
   là **lựa chọn thiết kế bảo thủ, chưa calibrate từ dữ liệu thật** — giống mọi ngưỡng khác
   trong `GuardrailEngine`.
2. Doc 29 mục 5.2 ghi rõ một cảnh báo cần nhắc lại ở đây: tổng quan literature cho thấy **nhiều
   feature responsible-gambling KHÔNG làm giảm harm một cách đo được**. Circuit breaker này được
   xây vì lập luận duty-of-care (doc 29 mục 5.4) và vì rủi ro pháp lý, **không** vì có bằng chứng
   nó hiệu quả. Không được trình bày với studio như một giải pháp đã được chứng minh.
