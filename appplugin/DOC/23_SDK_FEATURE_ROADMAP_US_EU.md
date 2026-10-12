# 23 — SDK Feature Roadmap: từ Ads Plugin hiện tại đến Monetization SDK chuẩn thế giới (mục tiêu US/EU)

**Ngày tổng hợp:** 2026-08-21
**Phạm vi:** Tài liệu tổng hợp cấp cao nhất, ánh xạ toàn bộ nghiên cứu (docs 01–22) vào **kiến trúc thực tế của repo này** và trả lời câu hỏi trực tiếp: *SDK cần build thêm module gì, theo thứ tự nào, để đạt bộ tính năng monetization mà thế giới (US/EU) đang coi là chuẩn năm 2026.*

> Đây KHÔNG phải bản thay thế cho docs 01–22, mà là lớp tổng hợp thực thi (execution synthesis) đứng trên tất cả — vai trò tương đương "translation layer" giữa nghiên cứu và backlog kỹ thuật.

> **Hết hiệu lực làm lộ trình (chủ dự án, 2026-09-29).** SDK là nội bộ cho hai app tiện ích (Speaker Clean, Invoice
> Maker) và game của chủ dự án (doc 61), không bán cho studio. Lớp C.2, C.3, C.4, C.6 và toàn bộ mục D (phase 0–5)
> **không còn là kế hoạch**; C.5 chỉ giữ phần đã có (`logevent`, decision log). Đích thực tế của SDK là DOC/65 mục 5
> Phase C (2.0.0) với phạm vi chốt ở "Quyết định phạm vi 2026-09-29". Tài liệu này giữ lại làm nghiên cứu.

---

## A. Điểm xuất phát: repo đang có gì

Repo `appplugin` hiện là một **Android ads-mediation abstraction library** (package `com.app.plugin`), không phải Monetization Brain. Kiểm kê thực tế (~104 file Kotlin/Java):

```text
com.app.plugin.app/          → AppPluginManager: init, session count, app lifecycle
com.app.plugin.ads/          → lớp trừu tượng đặt trên 3 network: AdMob, AppLovin MAX, FB Audience
  ads/adsmob, ads/adsmax, ads/adsfb  → implementation cụ thể từng network
  AdsPlacementConfig.kt       → CONFIG TẦN SUẤT PER-PLACEMENT (flagShow, maxShow, delTime,
                                 countshow) — đây CHÍNH LÀ nơi rule-based frequency logic sống
  AdsPlacementConfigManager.kt→ quản lý nhiều AdsPlacementConfig theo placement id
com.app.plugin.firbase/      → Remote Config + Firestore fetch (currConfig), FCM
com.app.plugin.appsflyer/    → AppsflyerHelper — attribution/MMP wrapper
com.app.plugin.logevent/     → AdTracker, LogEventServer, Logs — event pipeline nội bộ đã có sẵn
com.app.plugin.until/        → PreferenceHelper, PreUtil, Constants, ADSEnums
```

**Quan sát quan trọng cho roadmap:**

1. ~~**`AdsPlacementConfig.flagShow/maxShow/delTime/countshow`** là cap tần suất *rule tĩnh, giống nhau cho mọi user*. Đây chính xác là điểm mà Phase 1 của brain cắm vào — thêm một lớp quyết định phía trên trả về `maxShow`/`delTime` *động theo user*.~~

   > **ĐÍNH CHÍNH (2026-08-24, sau khi đọc code):** khẳng định trên **SAI**. Grep toàn repo cho thấy `maxShow`, `delTime`, `countshow`, `timeShow` trong `AdsPlacementConfig` **được khai báo nhưng không nơi nào đọc** — chúng là field chết. Chỉ `flagShow` thực sự được dùng, và nó là cờ bật/tắt/chọn loại, không phải cap tần suất.
   >
   > Luật tần suất thật đang chạy nằm ở **`AdsHelper.isIntersAllowShowAds()`** — so `currConfig.fullStartTime` và `currConfig.fullDeltaTime` với đồng hồ, tức luật **toàn cục theo thời gian**, không theo placement.
   >
   > Hệ quả cho thiết kế: không có "giá trị `maxShow` động" nào để trả về, vì không ai đọc `maxShow`. Điểm cắm đúng là **một cổng quyết định đặt sau `isIntersAllowShowAds()` trong `showInterstitial()`** — đã implement ngày 2026-08-24, xem doc 38 mục A0. Cách này còn an toàn hơn: brain chỉ siết chặt thêm chứ không thể nới lỏng luật cũ.
2. **`FirHelper` đã có Remote Config + Firestore fetch theo mỗi app open** — đây là hạ tầng policy-artifact-cache có sẵn (đúng pattern Adobe Target ở doc 16/D.1); không cần xây lại từ đầu, chỉ cần mở rộng schema của `currConfig` để chứa policy artifact của brain.
3. **`AppsflyerHelper` + `logevent/*`** là nền tảng attribution + event pipeline đã tồn tại — đây là chỗ cắm decision log (doc 06) và conversion-value strategy cho SKAN/AAK (doc 21) mà không cần thêm SDK bên thứ ba mới.
4. **Chưa có bất kỳ lớp nào cho:** IAP/billing, paywall, subscription, offerwall, CRM/messaging, payment routing đa kênh, decision/personalization engine. Toàn bộ 6 lớp tính năng mô tả bên dưới là **net-new**.

Kết luận: repo hiện tại là **execution layer của lớp IAA** (đúng vai trò "adapter xuống mediation" mà doc 12 gọi là *"không phải brain"*). Roadmap dưới đây biến nó thành SDK hoàn chỉnh bằng cách thêm 5 lớp tính năng mới xung quanh execution layer sẵn có, không viết lại nó.

---

## B. Đổi định hướng thị trường: US/EU thay vì SEA

Theo yêu cầu, thị trường mục tiêu là **US & EU** — nơi sẵn sàng chi trả cao (doc 22 xác nhận ARPU Mỹ ~$60.58, cao gấp 8–20 lần SEA ~$3–8; EU ~$50–55 sau điều chỉnh VAT/PPP). Hệ quả trực tiếp lên ưu tiên tính năng, khác với doc 15 (frame SEA-first cũ):

| Yếu tố | SEA (bối cảnh cũ) | US/EU (bối cảnh mới) |
|---|---|---|
| Đòn bẩy kinh tế lớn nhất | Volume × frequency ad thấp | ARPPU cao, top 1% payer tạo ~38% doanh thu (doc 22, Zendle et al.) |
| Tính năng ưu tiên | IAA frequency/timing bandit | **Paywall/subscription tooling + payment routing đa kênh** (docs 18, 19) |
| Áp lực pháp lý chính | Nghị định 356/2025 VN | EU Digital Fairness Act, PEGI loot-box 16+ (6/2026), FTC click-to-cancel, 20 bang Mỹ có luật privacy toàn diện (Minnesota MCDPA mạnh nhất — quyền chất vấn profiling) |
| Kênh thanh toán | Chủ yếu qua mediation/store thông thường | **Web shop/D2P đang bùng nổ** — thị trường $17B, tăng 26%/năm tại Mỹ trong khi tổng thị trường chỉ +0.2% (doc 18) |
| Rủi ro cạnh tranh | Vendor SEA phân mảnh | AppLovin AXON self-serve (6/2026), RevenueCat/Adapty đã chiếm lớp paywall — phải định vị SDK khác họ, không cạnh tranh trực diện |

**Vì vậy độ ưu tiên tính năng đảo ngược so với doc 15**: payment/paywall/subscription (lớp payer-value) đứng trước IAA frequency optimization (lớp volume), vì đó là nơi US/EU tạo giá trị kinh tế lớn nhất.

---

## C. Sáu lớp tính năng chuẩn thế giới — kiểm kê đầy đủ

Tổng hợp từ docs 11, 12, 18, 19, 20, 21, 22. Mỗi lớp ghi rõ: có trong repo chưa, ai đang làm chuẩn thế giới, và tính năng cụ thể cần có.

### C.1 Lớp Ad Monetization (đã có execution, thiếu decisioning)

**Đã có:** adapter AdMob/MAX/FB, `AdsPlacementConfig` (frequency cap tĩnh).
**Thế giới đang làm:** contextual bandit chọn frequency/timing theo per-user context (Metica SmartConfig, Firebase Remote Config Personalization — doc 03); offerwall như surface bổ sung, không thay thế rewarded (eCPM $350–$2,000+ so với rewarded $19.63 — doc 20).
**Cần thêm:**
- Decision layer trả `maxShow`/`delTime` động theo user state (bandit Thompson Sampling, doc 05) — cắm trực tiếp vào `AdsPlacementConfig`.
- Offerwall adapter (Tapjoy/AppLovin, DT Exchange, adjoe, Mistplay) như network mới trong `ads/` — chỉ nên là 1 network integration, không phải core.
- Rewarded timing logic theo doc 14 (chỉ mời rewarded sau fail thật, không "manufactured fail").

### C.2 Lớp Payment Routing đa kênh (net-new, ưu tiên cao nhất cho US/EU)

**Chưa có gì trong repo.**
**Thế giới đang làm (doc 18):** sau settlement Epic–Google (3/2026, phí Play giảm còn 20%+5% billing tùy chọn) và Apple US cho phép link ra web checkout 0% hoa hồng (5/2025), D2C/web shop đã thành ngành riêng $17B; vendor hạ tầng: Appcharge, Xsolla SDK v3.1, Unity+Stripe Commerce.
**Cần thêm:**
- Purchase router: một API duy nhất `purchase(sku)` chọn kênh (Store IAP / web shop / Epic-style store) theo entitlement rule, ẩn khỏi game logic.
- Server-side entitlement là nguồn sự thật duy nhất (single source of truth) — bắt buộc để tránh gian lận cross-channel.
- Cross-channel receipt validation (App Store, Play Billing, web shop webhook).
- Deep-link out-to-web-shop kèm attribution giữ nguyên qua App Clips/Universal Links (doc 21, vì Safari 26 đã phá UTM/gclid truyền thống).

### C.3 Lớp Paywall & Subscription (net-new, đã có chuẩn thị trường rõ ràng)

**Chưa có gì trong repo.**
**Thế giới đang làm (doc 19):** RevenueCat (50K+ app, $16B tracked) và Adapty (A/B không giới hạn, predictive LTV) là chuẩn de facto cho non-gaming apps; nhưng **game top-grossing tự build** vì thiếu cầu nối virtual-currency ↔ subscription — đây là khoảng trống game-specific.
**Cần thêm (tối thiểu để đạt parity với chuẩn thế giới):**
- Paywall builder cấu hình từ xa (remote-configured, không cần release app) — tận dụng `FirHelper` sẵn có.
- A/B/multivariate testing cho paywall/price/copy, có **holdout group native** (đây là white space đã xác nhận — không vendor lớn nào có, doc 19 mục 5).
- Dunning/billing-recovery (retry schedule, grace period, account hold) theo lifecycle chuẩn Google Play/Apple 2026.
- Win-back offer cho subscriber đã churn; introductory offer/free trial logic đúng theo App Store/Play Billing.
- **Game-aware layer riêng biệt** (không vendor nào có): cầu nối VIP subscription ↔ virtual currency, season/battle-pass như subscription có thời hạn, offer gắn theo progression thay vì thời gian thuần túy.

### C.4 Lớp Offerwall, Loyalty & Engagement Monetization (net-new)

**Chưa có gì trong repo** (ngoài banner/interstitial/rewarded/native chuẩn).
**Thế giới đang làm (doc 20):** offerwall bùng nổ hậu-ATT (D30 retention của user offerwall gấp ~7x); battle pass chiếm 30–60% tổng doanh thu ở game áp dụng; lớp CRM (Braze, Airship, OneSignal, CleverTap) đẩy IAP qua push/in-app message có case uplift đã công bố (Etermax +87% gameplay).
**Cần thêm:**
- Offerwall như adapter tùy chọn (không native bắt buộc — theo khuyến nghị "decision matrix" của doc 20).
- Event bus nội bộ đủ tổng quát để engagement hub (streak, mission, event calendar) bắn signal vào decision engine.
- Reward engine tách biệt khỏi core (fulfill reward qua nhiều nguồn: ad, offerwall, IAP bundle).
- Tích hợp webhook cho CRM bên thứ ba (Braze/OneSignal) thay vì tự xây CRM — build cái thế giới thiếu (game-aware decisioning), mua cái thế giới đã chuẩn hóa (CRM).

### C.5 Lớp Measurement & Attribution 2026 (đã có nền, cần nâng cấp)

**Đã có:** `AppsflyerHelper`, `logevent/*` (AdTracker, LogEventServer).
**Thế giới đang làm (doc 21):** AdAttributionKit dần thay SKAN làm chuẩn khuyến nghị của Apple; Safari 26 phá web-to-app attribution kiểu cũ; MMP chuyển từ "attribution engine" sang "modeled intelligence + pLTV"; Google Meridian (MMM open-source, 1/2025) và Meta Robyn phổ biến hóa MMM cho studio vừa và nhỏ; clean room (LiveRamp, AWS Clean Rooms, Snowflake) là hạ tầng chuẩn cho first-party data.
**Cần thêm:**
- Conversion-value strategy có sẵn cấu hình mặc định tối ưu cho gaming (AAK/SKAN hierarchical conversion value) — không bắt mỗi studio tự thiết kế từ đầu.
- Decision log chuẩn hóa (doc 06) làm nguồn sự thật cho outcome capture — SDK **sở hữu first-party event**, đây là lợi thế cạnh tranh trực tiếp so với vendor chỉ đọc log từ MMP.
- Incrementality hook có sẵn: holdout assignment, ghost-event logging, experiment metadata — dùng chung cho cả UA lẫn monetization (paywall, offer).
- Export ẩn danh tương thích clean room cho studio muốn tự làm MMM/clean-room join.

### C.6 Lớp Decision/Personalization Brain (lõi khác biệt hóa — đã nghiên cứu sâu ở docs 01–17)

Đây là lớp không vendor nào trên thế giới làm trọn vẹn cross-modal (white space đã xác nhận ở doc 12). Không lặp lại chi tiết ở đây — xem docs 04, 05, 06, 11; chỉ nhắc: **lớp này chỉ có giá trị khi C.2–C.5 đã tồn tại**, vì nó cần dữ liệu payment/paywall/measurement làm input. Đây là lý do thứ tự roadmap ở mục D đặt personalization brain sau, không phải trước.

---

## D. Roadmap thực thi (ưu tiên theo giá trị kinh tế US/EU)

```text
Phase 0 — Nền tảng đo lường & entitlement (4–6 tuần)
  - Chuẩn hóa decision log (doc 06) trên nền logevent/* sẵn có
  - Server-side entitlement service (nguồn sự thật cho mọi kênh mua)
  - AAK/SKAN conversion-value schema mặc định cho gaming

Phase 1 — Payment routing đa kênh + Paywall cơ bản (6–10 tuần)
  - Purchase router (Store IAP + web-shop deep link)
  - Paywall builder remote-config (dùng FirHelper), A/B cơ bản
  - Dunning + win-back tối thiểu theo chuẩn Store 2026
  → Đây là lớp tạo giá trị kinh tế lớn nhất cho US/EU, làm trước IAA optimization

Phase 2 — Subscription game-aware + Holdout-native A/B (6–8 tuần)
  - Cầu nối VIP subscription ↔ virtual currency
  - Battle pass/season pass engine có catch-up mechanics
  - Holdout group native cho paywall/offer test — khác biệt hóa so với RevenueCat/Adapty

Phase 3 — IAA decisioning per-user (cắm vào AdsPlacementConfig) (4–6 tuần)
  - Bandit Thompson Sampling chọn maxShow/delTime theo user state
  - Offerwall adapter tùy chọn
  - Rewarded timing theo ethics guardrail (doc 14)

Phase 4 — Cross-modal decisioning + CRM webhook + Incrementality (8–12 tuần)
  - REWARDED_AD vs IAP_OFFER vs NO_ACTION theo propensity trả tiền (đây là điểm khác mọi vendor)
  - Webhook ra Braze/OneSignal
  - Holdout/incrementality hook dùng chung UA + monetization

Phase 5 — Full action space, guardrail productized, on-device inference (doc 16) (dài hạn)
```

Ghi chú: roadmap này **thay thế thứ tự của doc 15** (vốn đặt IAA-side lên Phase 1 vì frame SEA-first) — với US/EU, giá trị kinh tế lớn nhất nằm ở payer, không phải impression, nên payment/paywall đi trước.

---

## E. Định vị cạnh tranh (US/EU)

Không cạnh tranh trực diện với RevenueCat/Adapty (paywall thuần) hay AppLovin AXON (mediation AI). Định vị:

> **"Cross-modal monetization decision layer cho game — kết hợp payment routing đa kênh, subscription game-aware, và ad decisioning per-user trong một SDK duy nhất, với holdout/incrementality native — thứ mà hiện tại một studio phải ghép từ 4-5 vendor khác nhau."**

Ba khoảng trống cụ thể xác nhận qua nghiên cứu (docs 12, 18, 19, 20):
1. Không paywall vendor nào có holdout group native.
2. Không ai bắc cầu virtual currency ↔ subscription cho game.
3. Không ai làm cross-modal IAA↔IAP decisioning theo causal uplift, mediation-neutral.

## Không xác minh được (ghi rõ)

1. Độ chính xác dài hạn của các con số settlement Epic–Google và mốc thời gian Apple EU (doc 18) — đây là lĩnh vực pháp lý đang tiếp diễn, có thể thay đổi sau ngày viết tài liệu.
2. Chưa có benchmark thực tế nào (nội bộ repo này) đo được hiệu quả của kiến trúc đề xuất — toàn bộ mục D là *design synthesis*, chưa phải capability đã triển khai.
3. ~~Mức độ tương thích chi tiết giữa `AdsPlacementConfig` hiện tại và một bandit engine — cần review kỹ thuật riêng trước khi implement Phase 3.~~ **Đã review (2026-08-24)** và kết quả lật ngược giả định gốc — xem đính chính ở mục A.1. Cắm bằng cổng quyết định trong `showInterstitial()`, không đụng vào `AdsPlacementConfig`.

## Nguồn chính

Tài liệu này tổng hợp từ toàn bộ docs 01–22 đã có trong KB; không có nguồn mới ngoài source library (doc 09). Xem trích dẫn chi tiết trong từng doc gốc tương ứng với mỗi lớp tính năng (C.1→doc 03/05/14/20; C.2→doc 18; C.3→doc 19; C.4→doc 20; C.5→doc 21; C.6→docs 04/05/06/11).
