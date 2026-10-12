# 60 — Chuẩn kỹ thuật cho một SDK sống lâu: chạy được mọi app, không lộ IP, học từ SDK hàng đầu

**Ngày viết:** 2026-09-23
**Phạm vi:** chuẩn kỹ thuật, đóng gói, phân phối và bảo vệ của **chính SDK**. Phần luật (GDPR,
COPPA, loot box, dark pattern) đã có ở doc 13 và doc 22 — tài liệu này chỉ dẫn chiếu, không lặp lại.
**Đầu vào:** audit repo ngày 2026-09-23 (527/527 unit test pass, backend 146/146, lint 0 error,
AAR release 1,8 MB) và nguồn chính thức cập nhật tới tháng 9/2026.
**Bối cảnh chốt (2026-09-23):** đây là **SDK nội bộ**, chỉ dùng cho các app và game của chính chủ dự
án (hiện có LAM 2026, Speaker Clean, Invoice Maker). Nó **không** phải sản phẩm bán cho studio khác và
**không** cạnh tranh với AppLovin MAX. MAX là một dependency mediation mà SDK dùng bên trong, giống
AdMob hay Meta. Mọi chỗ nhắc tới MAX, RevenueCat, Adjust… trong tài liệu này là **nguồn pattern để học**,
không phải phép so sánh cạnh tranh. Cách ba app đang tích hợp và kế hoạch hợp nhất: doc 61.

### Khuyến nghị nào đổi khi SDK là nội bộ

| Khuyến nghị kiểu "SDK thương mại" | Với SDK nội bộ |
|---|---|
| Đăng ký Play SDK Index / SDK Console, badge | **Không cần.** SDK không phân phối ra ngoài; crash/ANR xem trên Play Console và Crashlytics của từng app |
| Play Integrity phải claim SDK trên SDK Console | **Không cần.** Gọi bằng Cloud project của chính app. Fork của Speaker Clean đã làm vậy (`segment/PlayIntegrityGuard.kt`) |
| EULA, API key theo khách hàng, docs công khai tiếng Anh | **Bỏ.** Thay bằng README nội bộ, CHANGELOG, key riêng cho từng app |
| Plugin Unity/Flutter/React Native cho "mọi studio" | **Chỉ khi** portfolio game của chủ dự án thực sự dùng engine đó |
| "Chạy trên mọi app" | **Mọi app trong portfolio**: app tiện ích (không dùng MAX) và game (có thể dùng MAX) — cần chọn module theo loại app |
| Bảo vệ IP | **Vẫn quan trọng, đổi góc nhìn:** đối thủ tải APK của app mình từ Play về dịch ngược. R8 của app quyết định; SDK không được mang keep rule giữ cả package |
| SemVer, ABI check, deprecation | **Vẫn cần, nhẹ hơn:** 3 app tự chọn thời điểm nâng version; ABI check giúp một thay đổi không âm thầm phá app khác |

## Nhãn evidence

- **[P]** nguồn chính thức: Google, Apple, Kotlin/JetBrains, IAB, FTC, cơ quan EU.
- **[V]** tài liệu của vendor SDK khác: AppLovin, Adjust, RevenueCat, Sentry, Unity.
- **[M]** đo trực tiếp trên repo/AAR này ngày 2026-09-23.
- **[D]** đề xuất thiết kế suy ra từ các nguồn trên; không phải điều vendor nào công bố.

---

## 0. Tóm tắt điều hành

SDK hiện **mạnh ở lõi logic** (brain, IAP, guardrail, test dày) nhưng **yếu ở ba thứ quyết định tuổi
thọ của một SDK dùng chung cho nhiều app**: (1) đóng gói — một khối AAR ép mọi app nhận mọi vendor; (2) bảo vệ IP —
keep rule hiện tại để lộ gần như toàn bộ source; (3) hợp đồng ổn định — chưa có công cụ nào chặn việc
vô tình phá API.

| # | Trụ cột | Chuẩn thế giới | Hiện trạng [M] | Mức |
|---|---|---|---|---|
| 1 | Module & dependency | core + adapter tách artifact, BoM | 1 AAR, 227 artifact runtime, ép Firebase + 3 stack ads | CAO |
| 2 | Ổn định API | explicit API, ABI dump trong CI, SemVer, lịch deprecate | 160 type public, 0 `internal`, không ABI check, version đứng ở 1.1.0 | CAO |
| 3 | Chạy trên mọi app | init không kế thừa, resource prefix, Java/Unity | bắt kế thừa `AppPluginBase`, resource trùng tên phổ thông, 4 annotation Java | CAO |
| 4 | Privacy as code | consent gate đọc TCF 2.3/GPP, lưu trạng thái | xem audit: init trước consent, tier không lưu | CAO |
| 5 | Bảo vệ IP & chống giả mạo | client mỏng, server giữ "bộ não", request signing, artifact có chữ ký | 300/314 class giữ tên gốc; config từ xa không ký | CAO |
| 6 | Vận hành | không bao giờ làm app chết, kill switch, telemetry SDK | try/catch rộng, brain có kill switch; ads/iap chưa có | TRUNG BÌNH |
| 7 | Trải nghiệm dev | debugger UI, changelog, docs, CI | có `Report` + `BrainDebugView`; không CI, không README | TRUNG BÌNH |

---

## A. Năm thay đổi của 2025–2026 buộc phải thiết kế lại

1. **GMA legacy đã vào chế độ bảo trì.** [P] Trang SDK của AdMob ghi: *"Google Mobile Ads SDK is in
   maintenance mode. For the latest updates and features, migrate and set up GMA Next-Gen SDK."*
   Ngày 2026-07-06 Google công bố Next-Gen là SDK ưu tiên cho Android. Artifact mới là
   `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk` (1.4.0), minSdk 24, compileSdk 35,
   mediation đã hỗ trợ hơn 20 mạng (AppLovin, BIGO, Chartboost, InMobi, ironSource, Meta, Mintegral,
   Unity…), và `MobileAds.initialize()` phải gọi trên background thread.
   → Repo vừa nâng lên `play-services-ads:25.4.0`, tức nhánh đang bảo trì. **Hệ quả thiết kế:** lớp
   ads phải nằm sau một interface adapter để đổi engine mà không đụng API công khai (mục B).

2. **Target API 36 bắt buộc từ 2026-08-31.** [P] App mới và bản cập nhật phải target Android 16
   (API 36); gia hạn được tới 2026-11-01. App tích hợp SDK sẽ lên 36, nên SDK phải được kiểm thử ở
   target 36. App demo đang target 34 [M]. Riêng các layout quảng cáo toàn màn hình (`NtFull`) cần kiểm
   tra lại hành vi edge-to-edge trên target 35/36 **[cần kiểm chứng trên thiết bị]**.

3. **Play Billing chạy chu kỳ 2 năm.** [P] Mỗi major được hỗ trợ 2 năm: v7 ngừng nhận bản build mới
   từ 2026-08-31, v8 từ 2027-08-31, v9 từ 2028-08-31. Repo đang dùng 9.0.0 [M] — an toàn tới 2028,
   nhưng lịch này nên được đưa vào roadmap nâng cấp cố định, không để tới sát hạn.

4. **Consent có chuẩn mới và hạn cứng.** [P] TCF v2.3 bắt buộc với TC string sinh sau 2026-02-28
   (segment `disclosedVendors`); string thiếu segment bị coi là không hợp lệ và quảng cáo rơi về
   "Limited Ads". COPPA sửa đổi có hiệu lực đầy đủ từ 2026-04-22. Chi tiết luật: doc 13.

5. **Unity chính thức có trình quản lý dependency native.** [V] Ngày 2026-09-22 Unity công bố package
   `com.unity.external-dependency-manager` (Unity 2022.3+), đọc định dạng XML của EDM4U, resolve Gradle
   cho Android và CocoaPods/SPM cho iOS. Đây là đường phân phối chuẩn cho plugin Unity từ nay (mục D).

---

## B. Trụ cột 1 — Module hoá: "app chỉ trả cho thứ nó dùng"

### B.1 Chuẩn thế giới

- **AppLovin MAX** [V]: SDK lõi và **mỗi adapter mạng là một artifact riêng**, có changelog riêng;
  app tự chọn mạng. Mediation Debugger báo lệch phiên bản adapter ↔ SDK mạng.
- **Firebase** [P]: mỗi sản phẩm một artifact, đồng bộ phiên bản qua BoM.
- **RevenueCat** [V]: `purchases` (lõi) tách khỏi `purchases-ui` (paywall UI).
- **Sentry** [V]: *"dependencies must never be required for the base line functionality of an SDK."*
- **Google** [P]: ưu tiên codegen/đăng ký tường minh hơn reflection, vì reflection làm R8 xoá nhầm code.

### B.2 Hiện trạng [M]

Một AAR kéo theo 227 artifact runtime: 3 stack quảng cáo (AdMob mediation, MAX mediation, Meta trực
tiếp) với 16 adapter, Firebase (Analytics, Crashlytics, Remote Config, Messaging, Firestore kèm gRPC),
AppsFlyer, Play Billing. APK debug của app demo (chưa minify) là 41,6 MB. Một app chỉ cần IAP vẫn phải
nhận toàn bộ.

### B.3 Bản đồ module đề xuất [D]

```text
appplugin-bom                       ← khoá phiên bản mọi module, app chỉ khai version một lần
│
├── appplugin-core                  ← MonetizationSdk, consent gate, storage, event pipeline, kill switch
│                                     KHÔNG phụ thuộc vendor nào (không Firebase, không ads, không AppsFlyer)
├── appplugin-brain                 ← DecisionEngine, Guardrail, Bandit, Holdout      (→ core)
├── appplugin-iap                   ← IapHelper, Entitlements, PurchaseRouter          (→ core, billing)
├── appplugin-paywall-ui            ← UI paywall                                       (→ iap)
├── appplugin-ads                   ← AdsHelper + interface AdNetworkAdapter          (→ core)
│   ├── appplugin-ads-gma           ← adapter GMA legacy (hiện tại)
│   ├── appplugin-ads-gma-nextgen   ← adapter GMA Next-Gen (mục A.1)
│   ├── appplugin-ads-max
│   └── appplugin-ads-meta
├── appplugin-attribution-appsflyer
├── appplugin-config-firebase       ← hiện thực RemoteConfigProvider bằng Firebase
├── appplugin-offerwall, appplugin-crm
└── appplugin-debug                 ← Inspector UI (mục H), chỉ dùng debugImplementation
```

**Luật phụ thuộc:**
- Mũi tên chỉ đi về `core`, không bao giờ ngược lại.
- `core` biết adapter qua **đăng ký tường minh**, ví dụ `configure { adapters += MaxAdapter() }`,
  không qua reflection.
- API công khai không bao giờ lộ kiểu của vendor. Lớp IAP đã làm đúng điều này
  (`IapProduct`/`IapPurchase`); lớp ads hiện cũng không lộ [M] — cần giữ nguyên khi tách.
- Mediation adapter **không** nằm trong module nào của ta. Mỗi app tự khai adapter nó cần; ta chỉ
  công bố bộ phiên bản đã kiểm thử (như ghi chú GMA 25.4 trong `build.gradle.kts` hiện nay).
- Config từ xa đi qua interface `RemoteConfigProvider`, để app không dùng Firebase vẫn chạy được.

---

## C. Trụ cột 2 — Hợp đồng API ổn định nhiều năm

### C.1 Công cụ

| Công cụ | Việc nó làm | Nguồn |
|---|---|---|
| `kotlin { explicitApi() }` | bắt khai `public`/`internal` tường minh; mặc định mọi thứ thành API là lỗi | [P] Kotlin |
| Kotlin binary-compatibility-validator | dump ABI ra file `.api`, `apiCheck` fail khi ABI đổi ngoài ý muốn | [P] — bản nhúng trong KGP 2.2 (`abiValidation {}`) còn experimental, Kotlin khuyên dùng bản plugin riêng cho production |
| Kotlin backward-compatibility guidelines | danh sách thay đổi phá binary mà nhìn source không thấy (thêm tham số mặc định, đổi `data class`…) | [P] |

Hiện trạng [M]: 160 type public, 0 `internal`. Mọi class nội bộ đều đang là API phải giữ tương thích.

### C.2 Quy tắc phiên bản [D]

- **SemVer nghiêm ngặt.** Tính là *breaking* (phải lên major):
  - thay đổi ABI công khai;
  - đổi hành vi mặc định;
  - nâng minSdk;
  - **nâng major của dependency mà app phải nâng theo.** Ví dụ GMA 24 → 25 buộc app thay toàn bộ
    adapter, lẽ ra phải là 2.0.0, không phải tiếp tục 1.1.0 [M].
- **Một nguồn version duy nhất:** khai trong `gradle.properties`, sinh ra `BuildConfig`, thay cho hai
  chỗ khai tay hiện nay (`build.gradle.kts` và `MonetizationSdk.VERSION`).
- **Lịch deprecate học từ Play Billing** [P]: mỗi major được hỗ trợ tối thiểu 18–24 tháng, công bố
  ngày ngừng từ trước. API cũ đi qua `@Deprecated(WARNING)` → `ERROR` → xoá, trải ít nhất 2 minor.
- **Sửa tên package một lần, ở 2.0:** `until` → `util`, `firbase` → `firebase`; phần nội bộ chuyển
  vào `com.app.plugin.internal.*`. Giữ `typealias` + `@Deprecated` cho tên cũ trong suốt 2.x.
- **groupId theo domain mình sở hữu.** `com.app.plugin` quá chung, dễ trùng.

### C.3 Dùng được từ Java và từ wrapper [D]

`configure(context) { … }` (lambda có receiver) rất khó gọi từ Java, trong khi mọi wrapper Unity,
Flutter, React Native đều đi qua lớp Java/JNI. Nên theo mẫu `PurchasesConfiguration.Builder` của
RevenueCat [V]:
- cung cấp `Config.Builder` bất biến;
- callback dùng `fun interface`;
- dùng `@JvmStatic`/`@JvmOverloads` ở mọi điểm vào.

Toàn bộ API hiện chỉ có 4 annotation hỗ trợ Java [M].

---

## D. Trụ cột 3 — Chạy được trên mọi app

| Yêu cầu | Chuẩn | Hiện trạng [M] |
|---|---|---|
| Không bắt kế thừa `Application` | `MonetizationSdk.init(application, config)`; tuỳ chọn auto-init qua `androidx.startup` | Bắt `extends AppPluginBase : MultiDexApplication` — đụng app đã có base class riêng |
| Không đụng tên resource | [P] Khi trùng tên, **resource của app thắng**; Google khuyên dùng prefix riêng. Bật `resourcePrefix = "appplugin_"` để lint cưỡng chế | Có `black`, `white`, `transparent`, `bg_circle`, `activity_horizontal_margin`, `default_notification_channel_id`, font `inter_bold`… — app host định nghĩa trùng là UI quảng cáo đổi âm thầm |
| Không ép font/theme | Dùng font hệ thống hoặc downloadable fonts; style có namespace để app override | 8 file TTF đóng gói trong AAR |
| Permission tối thiểu | Quyền nằm ở module cần nó | `AD_ID` khai ở manifest lõi — app cho trẻ em không gỡ được nếu chỉ dùng IAP |
| Cùng tồn tại với stack sẵn có | Chế độ "bring your own mediation": không tự `initialize` GMA/MAX nếu app đã làm | SDK luôn tự init cả ba stack |
| Game engine | Chỉ khi game trong portfolio dùng Unity: UPM + dependency XML cho Unity EDM mới (mục A.5) | Chưa có. Ba app hiện tại đều là app Android native, nên chưa cần |
| Target mới | Kiểm thử target 36 (mục A.2) | Demo target 34 |
| Ngôn ngữ EU | Chuỗi UI (paywall, nhãn "Ad"/"Sponsored") dịch được, hoặc cho app truyền vào | Chuỗi cứng tiếng Anh, lint báo 6 `HardcodedText` |
| Accessibility | [P] European Accessibility Act áp dụng từ 2025-06-28 cho dịch vụ e-commerce, gồm cả app; đo theo EN 301 549 (WCAG 2.1+). Paywall/luồng mua hàng nằm trong phạm vi | Lint báo 8 `ContentDescription`, 12 `RtlHardcoded` |
| Backend nhiều app | Key riêng từng app, dữ liệu tách theo `app_id`, rate limit; API đọc dùng auth quản trị riêng | Một key chung cho cả ghi và đọc; key nằm trong mọi APK |

---

## E. Trụ cột 4 — Privacy as code (tóm tắt; luật ở doc 13)

Google yêu cầu rõ với nhà làm SDK [P]:
- SDK phải *"reads and adheres to the app developer-collected user preference"*, hoặc cho app cách
  khởi tạo SDK đúng theo lựa chọn consent;
- phải công bố công khai dữ liệu thu thập và mục đích;
- không bán dữ liệu;
- tuân chính sách về **persistent identifiers**.

Kiến trúc chuẩn [D]:

1. **Consent gate trong `core`.** Mỗi module khai purpose nó cần (lưu trữ, cá nhân hoá quảng cáo,
   analytics). Mọi luồng dữ liệu đi qua gate. Trạng thái được **lưu lại**, và mặc định từ chối ở EEA
   cho tới khi biết chắc.
2. **Đọc tín hiệu chuẩn, không tự suy luận:**
   - TCF 2.3 qua `IABTCF_*`;
   - GPP cho các bang Mỹ;
   - chuyển tiếp sang Google Consent Mode v2, AppsFlyer, Meta, AppLovin, Unity.
3. **Trẻ em:** chương trình Families Self-Certified Ads SDK của Play hiện **không nhận hồ sơ mới** [P].
   Khi `childDirected = true`, SDK chỉ được bật các mạng đã tự chứng nhận; stack nào không chứng nhận
   thì phải tắt hẳn, không chỉ gắn cờ.
4. **Tài liệu cho app tích hợp:** bảng "Data safety" theo từng module, để từng app điền form Play mà
   không phải đoán. Bản iOS cần privacy manifest và XCFramework có chữ ký [P]; Apple bắt buộc hai thứ
   này với các SDK trong danh sách "commonly used", nên làm từ đầu.

Các lỗi cụ thể cần sửa trước (init chạy trước consent, `OBTAINED` → `GRANTED`, tier không lưu,
ANDROID_ID gửi bất kể consent) đã liệt kê trong audit 2026-09-23.

---

## F. Trụ cột 5 — Bảo vệ IP: đối thủ tải AAR về sẽ thấy gì

### F.1 Nói thẳng về giới hạn

OWASP MASVS-RESILIENCE [P] xếp obfuscation, anti-debug, kiểm tra toàn vẹn là **phòng thủ nhiều lớp**,
không phải bảo vệ tuyệt đối. Mọi thứ chạy trên máy người dùng đều có thể bị dịch ngược. Vì vậy mục tiêu
thực tế là:
- **(a)** thứ có giá trị nhất không nằm trên máy;
- **(b)** thứ buộc phải nằm trên máy thì tốn nhiều công để đọc;
- **(c)** dữ liệu giả mạo không đầu độc được hệ thống.

### F.2 Đo thực tế [M]

Mở `appplugin-release.aar` (R8 full mode đã bật):
- **300/314 class giữ nguyên tên.**
- Field private cũng giữ nguyên, ví dụ `GuardrailEngine.churnRiskProtectionThreshold`,
  `frustrationStreakThreshold`, `maxPurchasesPerSessionBeforeBreak`.
- Kotlin metadata còn nguyên, nên jadx dựng lại gần như đúng source Kotlin.

Nguyên nhân: keep rule theo cả package (`-keep class com.app.plugin.brain.** { *; }`, tương tự với
`ads`, `iap`, `paywall`…). Google cấm đúng kiểu rule này [P]: *"Don't include package-wide keep rules
(such as -keep class com.mylibrary.** {*; })"*.

Consumer rules còn giữ nguyên toàn bộ `com.google.firebase.**`, `com.applovin.**`,
`com.facebook.ads.**` trong mọi app tích hợp. Thêm nữa, app demo build release với
`isMinifyEnabled = false`, nên consumer rules chưa từng được kiểm chứng.

### F.3 Sáu lớp bảo vệ [D]

1. **Đưa "bộ não" về server — lớp mạnh nhất.** Trên máy chỉ giữ phần thực thi, guardrail và cache.
   Trọng số, ngưỡng, định nghĩa segment, prior của bandit đi về dưới dạng policy artifact từ server;
   training và OPE ở backend (đã có doc 59). Đối thủ dịch ngược chỉ thấy *cơ chế*, không thấy *con
   số đã tinh chỉnh*.
   Đây là mô hình của RevenueCat [V]: client mã nguồn mở, giá trị (offerings, experiment, dữ liệu)
   nằm ở server.
2. **Obfuscate cho đúng.**
   - Bật `explicitApi()`; chuyển phần nội bộ sang `internal` và package `internal`.
   - `proguardFiles` của chính thư viện chỉ keep public API. Được dùng
     `-repackageclasses 'com.app.plugin.internal'`, vì Google cho phép rule này trong build của thư
     viện, miễn repackage vào package con của chính nó [P].
   - Consumer rules chỉ giữ đúng chỗ dùng reflection/JNI.
   - Mục tiêu đo được: **≥ 90% class bị đổi tên**, không còn tên field private nào đọc được.
3. **Xoá dấu vết trong release.** Dùng `-assumenosideeffects` cho `android.util.Log` và `LogUtil`,
   gỡ các chuỗi mô tả thiết kế nội bộ khỏi bản release (ví dụ phần mô tả backend trong
   `Report.describe()`).
4. **Chống giả mạo request — học Adjust** [V]. Adjust SDK Signature ký mỗi request bằng bí mật riêng
   của app, tham số động và dữ liệu thiết bị/phiên, dùng thuật toán mangling riêng. Thư viện ký được
   obfuscate và tự kiểm tra toàn vẹn; server chỉ nhận app có vân tay chứng chỉ trong allowlist. Với
   brain, đây là điều sống còn: đối thủ hay kẻ gian bơm event giả vào `/push` là đầu độc được mô hình.
5. **Play Integrity** [P]. Verdict cho biết app có bị sửa không, bản cài có hợp lệ không, thiết bị có
   thật không, và **chỉ được xác minh ở server**. Dùng cho grant quyền lợi, claim battle pass,
   offerwall.
   Với SDK nội bộ, mỗi app gọi bằng Cloud project của chính nó; không cần đường "SDK claim trên SDK
   Console", vốn dành cho SDK bên thứ ba, quota 10.000 request/ngày chung cho mọi app.
   Fork của Speaker Clean đã có `PlayIntegrityGuard` gắn nonce vào purchase token — nên đưa ngược về
   SDK (doc 61).
6. **Ký mọi config điều khiển từ xa.** Policy artifact, paywall config và đặc biệt URL web shop của
   `PurchaseRouter` được ký Ed25519; public key ghim trong SDK; version chỉ được tăng; bản không ký thì
   bỏ. Hiện chưa có chữ ký nào [M]. Nếu ai chiếm được Firebase project, họ có thể chuyển hướng luồng
   thanh toán sang trang lừa đảo.

### F.4 Kiểm soát phân phối [D]

- **Maven private.** Đính chính audit trước: với SDK đóng, **không** publish sources jar; chỉ publish
  javadoc/Dokka của API công khai.
- **Tách tài liệu ra khỏi repo SDK.** Tài liệu nghiên cứu và teardown app đối thủ
  (`TỔNG HỢP PHÂN TÍCH…`, doc 27, 42–44, 48) nên nằm ở repo riêng quyền hạn chế — vừa đỡ lộ chiến lược,
  vừa giảm rủi ro pháp lý.
- Mỗi app một API key riêng, để khi key lộ thì thu hồi được mà không ảnh hưởng app khác.

### F.5 Thứ không thể giấu — lên kế hoạch theo

Endpoint và schema event (đi qua mạng), layout quảng cáo, danh sách dependency (POM công khai với người
có quyền tải), tên resource. Thiết kế sao cho biết những thứ đó cũng không sao chép được giá trị.

---

## G. Trụ cột 6 — SDK không bao giờ được là lý do app chết

Nguyên tắc của Sentry [V]: *"We never want our SDKs to be the cause of customer breakage"*; khi môi
trường không phù hợp thì *"gracefully fall back to silently not doing anything"*.

- **Cách ly lỗi ở mọi điểm vào công khai.** Đã làm khá tốt [M]; cần thêm test tự động kiểu "mỗi API
  gọi sai thứ tự hoặc thiếu dependency đều không ném exception".
- **Ngân sách main thread.** Init nặng phải chạy nền. GMA Next-Gen ghi rõ: gọi `initialize()` trên main
  thread *"may cause an ANR"* [P]. Hiện `AppPluginBase.onCreate()` chạy khá nhiều việc đồng bộ.
- **Kill switch cho từng module và từng phiên bản SDK**, qua config có chữ ký. Brain đã có ba mức tắt
  (doc 38); ads, iap, network cần có tương đương.
- **Tự đo sức khoẻ SDK.** Đã có `sdk_init_time`, `admob_adapter_init` [M]. Gắn phiên bản SDK vào mọi
  event và làm custom key trên Crashlytics, để trên Play Console/Crashlytics của từng app lọc được crash
  và ANR theo phiên bản SDK. Với SDK nội bộ, cách này thay cho Play SDK Console.
- **Pipeline event:** gom batch (hiện gửi từng event một), backoff, giới hạn kích thước hàng đợi.
- **Ma trận kiểm thử:**
  - API 26–36;
  - app mẫu **bật R8 full mode**. Google yêu cầu thư viện không crash dưới full mode [P]; kiểm bằng
    R8 Configuration Analyzer;
  - app Java thuần, app Compose, app đa process, app không có Firebase.

---

## H. Trụ cột 7 — Trải nghiệm dev: lý do các app dùng bản chung thay vì tự fork

- **Inspector trong app.** Học MAX Mediation Debugger [V] (trạng thái từng adapter, lệch phiên bản)
  và GMA Ad Inspector. Ta đã có `Report` và `BrainDebugView` [M]; mở rộng thành "AppPlugin Inspector"
  trong module `appplugin-debug`, hiển thị:
  - adapter đã đăng ký và phiên bản;
  - consent tier, TC string có hợp lệ theo TCF 2.3 không;
  - version và chữ ký của config;
  - trạng thái kill switch;
  - 20 lỗi gần nhất.
- **Tài liệu nội bộ:** README ở gốc repo, quickstart ≤ 10 phút, changelog theo từng module (MAX làm
  theo từng adapter [V]), migration guide cho mỗi major (Adjust có guide v4 → v5 [V]), app mẫu. Ba app
  thật (doc 61) chính là bộ test tích hợp tốt nhất.
- **CI cho mỗi PR:** build, unit test, lint, `apiCheck`, build app mẫu có R8, audit dependency và
  giấy phép, **báo cáo kích thước** (fail nếu tăng quá ngưỡng), publish khi gắn tag.
- **Play SDK Index** [P]: không áp dụng. SDK Index dành cho SDK phân phối công khai từ Maven
  repository kiểm chứng được; SDK nội bộ không nên xuất hiện ở danh bạ công khai này.

---

## I. Học gì từ ai

Đây là bảng **mượn pattern**, không phải so sánh cạnh tranh: appplugin là SDK nội bộ, còn MAX, GMA, Meta
là các dependency nó dùng bên trong.

| SDK | Điều đáng học | Áp vào appplugin |
|---|---|---|
| AppLovin MAX [V] | Core + adapter artifact riêng, changelog riêng, Mediation Debugger | Module `ads-*` (MAX thành module tuỳ chọn cho game), Inspector |
| GMA Next-Gen [P] | Kotlin-first, init nền, cùng tồn tại với legacy trong giai đoạn chuyển | Interface adapter để đổi engine không đụng API |
| Firebase [P] | BoM; gộp KTX vào module chính rồi bỏ KTX ở BoM 34 (7/2025) | `appplugin-bom`; bỏ import `firebase.ktx` để không kẹt ở BoM 33 |
| RevenueCat [V] | `Configuration.Builder`; client mở, giá trị ở server; UI paywall tách module; xác minh entitlement bằng chữ ký | `Config.Builder`; "bộ não" ở server; ký config (F.3.6) |
| Adjust [V] | SDK Signature: ký request, tự kiểm toàn vẹn, allowlist vân tay chứng chỉ | Ký request `/push` và grant |
| Play Billing [P] | Chu kỳ deprecate 2 năm, công bố trước | Chính sách hỗ trợ theo major |
| Sentry [V] | Không bao giờ làm hỏng app; dependency không bắt buộc cho chức năng lõi | Nguyên tắc cho `core` |
| Unity EDM [V] | Khai dependency native bằng XML, Unity tự resolve | Kênh phân phối cho plugin Unity |

---

## J. Lộ trình đề xuất [D]

**Phase 0 — ổn định 1.x (1–2 tuần)**
- Sửa nhóm lỗi CAO trong audit 2026-09-23 (thứ tự init, consent gate, lưu tier).
- CI với build/test/lint; bật `explicitApi()` ở chế độ warning.
- Thu hẹp keep rules, đo tỉ lệ obfuscate; bật minify cho app demo.
- Phát hành 1.2.0 kèm CHANGELOG.
- **Xong khi:** CI xanh; ≥ 90% class bị đổi tên; app demo release có R8 chạy qua `AdDeliveryDeviceTest`.

**Phase 1 — 2.0.0 (phá tương thích, một lần)**
- Tách module theo B.3; đổi tên package; `init(application, config)` + `Config.Builder`.
- `resourcePrefix`; interface `AdNetworkAdapter` và `RemoteConfigProvider`.
- Config từ xa có chữ ký; consent gate đọc TCF 2.3/GPP; ABI dump khoá trong CI.
- **Xong khi:** `core` có 0 dependency vendor; app chỉ dùng `core + iap` chạy được mà không có
  Firebase; `apiCheck` chặn được một thay đổi ABI thử nghiệm.

**Phase 2 — nền tảng**
- Adapter GMA Next-Gen.
- Play Integrity cho grant (đưa `PlayIntegrityGuard` từ fork Speaker Clean về); ký request.
- Backend: key theo từng app, API đọc tách auth.

**Phase 3 — mở rộng**
- iOS ngang bằng (privacy manifest, XCFramework có chữ ký) cho Invoice Maker (đã có `iosApp`) và repo
  `monetization-brain-ios`.
- Plugin Unity chỉ khi có game Unity trong portfolio.

---

## K. Quyết định cần chủ sản phẩm chốt

1. **Phân phối:** đã rõ với SDK nội bộ — Maven private (GitHub Packages đã cấu hình sẵn trong
   `build.gradle.kts`). Cách ba app chuyển sang: doc 61.
2. **Số hiệu phiên bản:** bản GMA 25 phát hành là 1.2.0 (kèm cảnh báo) hay gộp luôn vào 2.0.0?
3. **Ba stack quảng cáo:** ba app tiện ích hiện tại chỉ dùng AdMob + Meta; MAX chỉ cho game. Nên tách
   MAX thành module tuỳ chọn thay vì để LAM phải `exclude` (doc 61).
4. **Ngân sách hardening thương mại** (DexGuard/Promon/Appdome) — chỉ đáng khi F.3.1–F.3.6 đã xong.
5. **Tách repo tài liệu nghiên cứu/teardown** ra khỏi repo SDK.

---

## Nguồn

**Google / Android / Play**
- [Google Mobile Ads SDK (Legacy) — maintenance mode](https://developers.google.com/admob/android/sdk)
- [Announcing GMA Next-Gen SDK as the preferred SDK for Android (2026-07-06)](https://ads-developers.googleblog.com/2026/07/announcing-google-mobile-ads-next-gen.html)
- [Set up GMA Next-Gen SDK](https://developers.google.com/admob/android/next-gen/quick-start)
- [Target API level requirements for Google Play apps](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Google Play Billing Library version deprecation](https://developer.android.com/google/play/billing/deprecation-faq)
- [Optimization for library authors (R8)](https://developer.android.com/topic/performance/app-optimization/library-optimization)
- [Configure and troubleshoot R8 Keep Rules (2025-11)](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html)
- [Create an Android library — resource merge conflicts](https://developer.android.com/studio/projects/android-library)
- [About user safety and SDKs](https://developer.android.com/guide/practices/sdk-best-practices)
- [Use the Play Integrity API in your SDK](https://support.google.com/googleplay/android-developer/answer/15299193?hl=en)
- [Google Play SDK Console](https://play.google.com/sdk-console/about/) · [Sign in and claim your SDKs](https://support.google.com/googleplay/android-developer/answer/12244916?hl=en) · [SDK Index](https://developer.android.com/distribute/sdk-index)
- [Families Self-Certified Ads SDK Program](https://support.google.com/googleplay/android-developer/answer/9900633?hl=en)
- [Firebase KTX migration (BoM 34 removes KTX)](https://firebase.google.com/docs/android/kotlin-migration)

**Kotlin**
- [Binary compatibility validation in the Kotlin Gradle plugin](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html)
- [Kotlin/binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
- [Backward compatibility guidelines for library authors](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html)

**Chuẩn bảo mật, privacy, luật**
- [OWASP MASVS-RESILIENCE](https://mas.owasp.org/MASVS/11-MASVS-RESILIENCE/)
- [IAB Europe — transition to TCF v2.3](https://iabeurope.eu/all-you-need-to-know-about-the-transition-to-tcf-v2-3/)
- [Federal Register — COPPA Rule amendments](https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule)
- [Apple — Third-party SDK requirements](https://developer.apple.com/support/third-party-SDK-requirements)
- [European Commission — CPC action on online games / virtual currencies](https://commission.europa.eu/topics/consumers/consumer-rights-and-complaints/enforcement-consumer-protection/coordinated-actions/social-media-online-games-and-search-engines_en)
- [European Parliament — Digital Fairness Act legislative train](https://www.europarl.europa.eu/legislative-train/theme-protecting-our-democracy-upholding-our-values/file-digital-fairness-act)
- [EAA & EN 301 549 (Davis Wright Tremaine)](https://www.dwt.com/insights/2025/07/european-accessibility-act-digital-products)

**Vendor SDK**
- [AppLovin MAX — Mediation Debugger](https://support.applovin.com/en/max/android/testing-networks/mediation-debugger) · [Preparing mediated networks](https://support.applovin.com/en/max/android/preparing-mediated-networks)
- [Adjust — SDK Signature](https://help.adjust.com/en/article/sdk-signature) · [Signature library integration](https://dev.adjust.com/en/sdk/android/integrations/signature-library/)
- [RevenueCat purchases-android](https://github.com/RevenueCat/purchases-android) · [Configuring the SDK](https://www.revenuecat.com/docs/getting-started/configuring-sdk)
- [Sentry — SDK philosophy](https://develop.sentry.dev/sdk/philosophy/)
- [Unity — Introducing External Dependency Manager (2026-09-22)](https://discussions.unity.com/t/introducing-external-dependency-manager-edm-unitys-official-package-for-mobile-native-dependencies/1737255) · [EDM4U](https://github.com/googlesamples/unity-jar-resolver)
