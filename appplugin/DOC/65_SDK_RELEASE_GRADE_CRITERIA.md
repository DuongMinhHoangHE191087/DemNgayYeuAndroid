# 65 — Chuẩn phát hành cao nhất cho SDK: đánh giá, tiêu chí, benchmark và lộ trình

**Ngày viết:** 2026-09-24
**Người viết:** kiến trúc sư SDK monetization (góc nhìn 20 năm làm SDK quảng cáo và thanh toán cho Android)
**Phạm vi:** toàn bộ repo `appplugin` (SDK, app demo, CI, backend) đối chiếu với `~/hienphap/HIEN-PHAP.md`,
`CHECKLIST-PHAT-HANH.md`, `DUC-RUT.md` và doc 60, 61, 62, 64.
**Đầu vào đo được:** đợt kiểm chứng 2026-09-24 (bản release R8 + shrinkResources + targetSdk 36 trên
emulator API 37, 3 lượt monkey, 597/597 unit test, lint 0 lỗi) và số đo trên mã nguồn hôm nay.
**Đầu ra:** một bộ tiêu chí có ngưỡng số và lệnh đo (mục 3), benchmark cạnh tranh (mục 4), lộ trình ba
bản (mục 5), và quy chế thực thi cho agent (mục 6). Kế hoạch từng task có code nằm ở
`docs/superpowers/plans/2026-09-24-sdk-release-grade-plan.md`.

Nhãn evidence như doc 60: **[P]** nguồn chính thức, **[V]** vendor, **[M]** đo trực tiếp trên repo,
**[D]** đề xuất thiết kế.

---

## 0. Kết luận điều hành

SDK **chưa đạt chuẩn phát hành rộng**. Lõi logic (brain, IAP, guardrail, consent gate, chuỗi Full2) là
phần mạnh và được test dày. Phần yếu nằm đúng ở nơi quyết định một app trên Play sống hay chết: **bản
release**, **crash ở đường quảng cáo**, và **đo lường**. Mọi lỗi nặng tìm được hôm nay đều thuộc họ lỗi
mà `DUC-RUT.md` §2.1–2.2 đã trả giá ở ba app trước: debug chạy, release chết; hỏng trong im lặng.

| # | Trụ cột | Mức | Bằng chứng ngắn [M] |
|---|---|---|---|
| 1 | Ổn định (crash) | **CHẶN** | 3 đường crash: release sập lúc mở (Room `<init>`), banner "child already has a parent", Full2 `BadTokenException` |
| 2 | ANR và khởi động | TRUNG BÌNH | 0 ANR qua ~6.000 thao tác; nhưng SDK đọc đĩa trên main thread ~0,5 s lúc mở (emulator) |
| 3 | Bản release là sản phẩm thật | **CHẶN** | SDK không mang `keep.xml` (8 → 0 ad unit khi shrink); demo targetSdk 34, R8 tắt; CI không dựng app có R8 |
| 4 | Consent và quyền riêng tư | CAO | Mạng quảng cáo đã chờ consent; nhưng "Do not consent" vẫn thành GRANTED; Firebase, FCM, AppsFlyer chạy trước consent; chưa có Consent Mode v2 |
| 5 | Luật quảng cáo và Play | CAO | Không có cơ chế dụ bấm; nút đóng 3 s; nhưng luật tần suất chưa gộp (doc 62 F), app open không hết hạn 4 giờ, remove-ads gỡ luôn rewarded |
| 6 | Đo lường doanh thu và sự kiện | CAO | `ad_impression` có thể đếm đôi khi AdMob đã nối Firebase; 2 tên event camelCase; gửi từng event, không giới hạn hàng đợi |
| 7 | Không hỏng câm, tự phục hồi | TRUNG BÌNH | Billing reconnect đã sửa; brain có kill switch, ads/iap/logevent chưa; Crashlytics chưa gắn phiên bản SDK |
| 8 | Kiến trúc, API, đóng gói | CAO | `AdsHelper` 3.669 dòng, 129 hàm public; 0 kiểm ABI; 1 AAR kéo 227 artifact; app tiện ích gánh cả adapter game |
| 9 | Kiểm thử và ma trận thiết bị | TRUNG BÌNH | 597 unit test; chưa có máy thật, chưa có API 26–35, không có test hợp đồng cho keep rule và tên event |
| 10 | Trải nghiệm tích hợp | TRUNG BÌNH | Doc 60–64 và CHANGELOG tốt; không README ở gốc; báo cáo tự kiểm tra có |

**Thứ tự việc:** 1.3.1 xoá ba đường crash và vá điểm mù release (mục 5, Phase A). 1.4.0 làm consent,
luật tần suất, đo lường và kill switch (Phase B). 1.5.0–1.6.x đóng follow-up của A/B (mục 5). 1.7.0 đưa
IAP/subscription/paywall và brain lên chuẩn phát hành (hết fork billing ở từng app). 2.0 tách MAX và phần
game thành module tuỳ chọn, `explicitApi` + `resourcePrefix`, GMA Next-Gen, tách `AdsHelper` (Phase C, phạm
vi chốt 2026-09-29 — mục 5 "Quyết định phạm vi"). **Đích của SDK là 1.7.0 + Phase C, không phải lộ trình
DOC/23** (chủ dự án 2026-09-29). Thu hẹp nhưng **đầy đủ cho app tiện ích + game của chủ dự án**, và app
dùng SDK phải hơn app đối thủ theo bảng mục 4.

---

## 1. Phương pháp đánh giá

1. **Chuẩn đối chiếu** lấy từ ba nguồn, theo thứ tự ưu tiên: Hiến pháp (luật cứng của dự án), chính sách
   và hướng dẫn của Google (Play, AdMob, Android), rồi pattern của các SDK dẫn đầu (MAX, GMA Next-Gen,
   RevenueCat, Adjust, Sentry) mà doc 60 đã tổng hợp.
2. **Số đo** chỉ được ghi khi có lệnh đo kèm theo (Điều 6.3). Số của đợt 2026-09-24 nằm trong
   `appplugin-verify-2026-09-24` (memory) và scratchpad phiên đó; bộ lệnh đo chuẩn được cố định ở mục 3.
3. **"Cạnh tranh được đối thủ mạnh nhất"** với một SDK nội bộ nghĩa là: app dùng SDK này phải có
   crash/ANR thấp hơn ngưỡng Play, quảng cáo hiện nhanh và đúng luật, doanh thu đo đúng đến từng
   impression, và không bao giờ bị SDK kéo tụt Android vitals. Không có chỉ số "SDK tốt hơn MAX"; chỉ có
   chỉ số của app trên Play Console và AdMob (doc 62 mục 2.1).

---

## 2. Đánh giá chi tiết theo mười trụ cột

### 2.1 Ổn định — CHẶN

**Chuẩn.** Play phạt user-perceived crash ≥ 1,09% (Điều 21); mục tiêu nội bộ < 0,5% (Phụ lục A). Một SDK
quảng cáo chạy trên mọi màn hình nên **ngân sách crash của SDK phải gần 0**: Sentry ghi *"never be the cause
of customer breakage"* [V].

**Hiện trạng [M].**
- **Release sập lúc mở.** GMA 25.4 và Unity Ads 4.19 kéo WorkManager 2.7.0 + Room 2.2.5. Rule Room chỉ giữ
  lớp con `RoomDatabase`, không giữ `<init>()`; R8 full mode xoá constructor của `WorkDatabase_Impl` và
  `androidx.startup` chết trước màn đầu. Đối chứng: chỉ ghim WorkManager 2.9.1 vẫn sập; thêm
  `-keep class * extends androidx.room.RoomDatabase { <init>(); }` thì mở bình thường. Speaker Clean và
  Invoice sống nhờ rule riêng trong app; app-template **không có** rule này, nên dự án mới sẽ dính.
- **Banner:** `IllegalStateException: The specified child already has a parent` tại `AdsHelper.loadBannerCircle`
  nhánh `bnCurrAdd == null` (không detach). Kịch bản: Activity tạo lại → `clearActivity()` → banner cache
  vẫn gắn ở container cũ bị `addView` vào container mới. Cửa sổ lỗi: tuổi banner 50–60 s. Có từ commit đầu.
- **Full2:** `WindowManager.BadTokenException` tại `AdsMobPlNtFull.showWidthDialog`: dialog dựng sẵn được
  dùng lại với Activity đã chết; điều kiện dùng lại không kiểm Activity, `show()` không có try/catch. Meta
  và MAX cùng mẫu. Đường này mở ra do bản sửa `flagShow 0` (đúng chuẩn doc 62) trên `fix/1.3.1`; Speaker
  Clean production có cùng logic.
- Dấu hiệu tốt: 108 điểm `catch (e: Exception)` bao quanh mọi điểm vào công khai; unit test không có
  test nào bị tắt.

**Khoảng cách:** ba bản sửa nhỏ, nhưng không có cơ chế nào ngăn chúng tái phát: không có test cho
"attach view", không có CI chạy bản R8, không có monkey.

### 2.2 ANR và khởi động — TRUNG BÌNH

**Chuẩn.** ANR < 0,3% (Phụ lục A). GMA Next-Gen ghi rõ `initialize()` trên main thread *"may cause an
ANR"* [P]. Core App Quality: TTID ≤ 2 s trên máy tầm trung; mục tiêu ≤ đối thủ nhanh nhất.

**Hiện trạng [M].** 0 ANR (`am_anr`) qua 1.794 + 3.000 + 1.162 sự kiện monkey và toàn bộ kịch bản.
`MobileAds.initialize` chạy ở `Dispatchers.IO`. Nhưng StrictMode trên emulator ghi: `AppPluginBase.onCreate`
đọc/ghi prefs ~366 ms qua 12 lượt; `DecisionLogger.attachQueue` đọc file ~129 ms trên main; UMP nội bộ
825 ms (của Google). `MobileAds.initialize` mất 31,8 s trên emulator swiftshader vì gánh toàn bộ adapter
game. 33 chỗ dùng `Handler(Looper.getMainLooper())`.

**Khoảng cách:** cần ngân sách main thread đo được (mục 3, G2) và đo lại trên máy thật.

### 2.3 Bản release là sản phẩm thật — CHẶN

**Chuẩn.** Điều 8: bản release chạy từ tuần đầu; mọi thứ gọi bằng tên có keep rule ngay lúc viết;
`CHECKLIST-PHAT-HANH.md` A: số ad unit trong `resources.arsc` > 0; 4 chuỗi R8 = 0.

**Hiện trạng [M].** SDK đọc ~28 tên resource bằng `getIdentifier` (`admob_*`, `adfb_*`, `admax_*`,
`ads_cf_*`, `flagTest`, `*_test_device`, `facebook_app_id`, `af_app_id`, `admax_devKey`) mà **không kèm
`res/raw/keep.xml`**: bật `shrinkResources` là 8 ad unit còn 0. Firebase BoM 33.4.0 kéo firebase-components
18.0.0, rule của nó không giữ `<init>` nên Crashlytics, FCM, Installations chết câm (`NoSuchMethodException
<init>`). App demo targetSdk 34, `isMinifyEnabled = false`, nên cả hai lỗi chưa từng lộ. CI chỉ chạy unit
test, lint và `assembleRelease` của thư viện; consumer rules chưa từng được CI kiểm.

### 2.4 Consent và quyền riêng tư — CAO

**Chuẩn.** Điều 13.3 (luật cứng): không mạng nào khởi tạo trước consent. Điều 20.9: mục "Lựa chọn quyền
riêng tư" khi UMP yêu cầu. Doc 60 E: đọc TCF 2.3 theo purpose, lưu tier, chuyển tiếp Consent Mode v2 và
AppsFlyer/Meta. Google yêu cầu SDK *"reads and adheres to the app developer-collected user preference"* [P].

**Hiện trạng [M].**
- Đạt: không mạng nào khởi tạo trước cú bấm; người quay lại khởi tạo sau 0,3 s; "Do not consent" vẫn khởi
  tạo (đúng, vì UMP cho phép quảng cáo giới hạn); xoay 4 lần form còn; API privacy options có.
- Chưa đạt: `ConsentManager.translate` map `OBTAINED` + `canRequestAds` → **GRANTED** kể cả khi
  `IABTCF_PurposeConsents` toàn 0. Các cổng profiling, CRM, offerwall, cá nhân hoá vì thế mở cho người đã
  từ chối. Tier không được lưu qua phiên. `FirebaseApp.initializeApp`, `Firebase.messaging.isAutoInitEnabled
  = true`, `AppsflyerHelper.initHelper` chạy trong `AppPluginManager.initPlugin` **trước** consent; không
  gọi `setConsent` (Consent Mode v2) và `setConsentData` (AppsFlyer). Tổng cộng 7 provider của mạng bên thứ
  ba vẫn tự chạy lúc mở process (doc 64 D); không có kết nối nào lúc form hiện, nhưng chỉ là ảnh chụp.

### 2.5 Luật quảng cáo và Play — CAO

**Chuẩn.** Doc 62 E–F (đã chốt), Điều 13.2/13.4/13.5/13.6, chính sách interstitial và app open của AdMob [P].

**Hiện trạng [M].** Đạt: không `GridForExcludeTouch`/`flagClick4AutoClose`; lưới xanh chỉ dưới `flagTest`;
Full2 đếm ngược 3 s (≤ 15 s); chuyển cảnh interstitial → Full2 220 ms, rewarded → Full2 256 ms, thưởng đúng
1 lần; app nhận đúng 1 `onAdDismissed`. Chưa đạt: luật tần suất nằm ở 5 nơi, chưa có `ads_policy_android`;
mặc định `fullDeltaTime` 30 s, không có phiên đầu miễn interstitial, app open không đợi lần mở thứ 2
(Phase B, Task 10: SDK có một policy `AdsPolicy`, khoá `ads_policy_android`; mặc định vẫn là ba điều này, áp
tạm theo §7 #1, #2, luật doc 62 F bật bằng Remote Config);
`AdsEcmps.getAdLoaded` không kiểm tuổi quảng cáo (app open có thể hiện quảng cáo đã hết hạn 4 giờ);
`Entitlements.recompute` gọi `setRemoveAds(1)` = mask `ALL`, gỡ cả rewarded (Điều 13.4 yêu cầu mask theo
định dạng).

### 2.6 Đo lường doanh thu và sự kiện — CAO

**Chuẩn.** Doc 62 mục 2: một định nghĩa doanh thu, `ad_impression` đúng một lần mỗi impression trả tiền
[P]; Điều 9.2: test hợp đồng tên event (≤ 40 ký tự, snake_case, không tiền tố `firebase_/google_/ga_`).

**Hiện trạng [M].** `FirHelper.logAdRevAdmob` luôn bắn `ad_impression` lên Firebase; app đã nối AdMob ↔
Firebase sẽ đếm đôi. 14 tên event của SDK có 2 tên camelCase (`reloadSplash_acnull`,
`setAc_waitreloadsplash`); không có test hợp đồng. `LogEventServer` gửi từng event một, không gom, không
giới hạn số request đang bay, không backoff; hàng đợi decision log thì đã bounded 5.000 và bền. Không có
event sức khoẻ SDK gắn phiên bản.

### 2.7 Không hỏng câm, tự phục hồi — TRUNG BÌNH

**Chuẩn.** Điều 7. Doc 60 G: kill switch theo module, phiên bản SDK là custom key trên Crashlytics.

**Hiện trạng [M].** Billing 9 reconnect đã phân loại đúng (`BillingSetupOutcome`). `AdsConfigOb` có mặc
định cho mọi khoá nên Remote Config chưa về không tắt quảng cáo. `Entitlements.sync` thay thế chứ không
cộng dồn. Brain có 3 mức tắt; ads, iap, logevent không có. Crashlytics không gắn `appplugin_version`,
nên trên Play Console của từng app không lọc được crash theo bản SDK.

### 2.8 Kiến trúc, API, đóng gói — CAO

**Chuẩn.** Doc 60 B–C: core không phụ thuộc vendor, adapter tách artifact, `explicitApi`, ABI dump khoá
trong CI, không kế thừa `Application`. Google: thư viện không được keep cả package [P].

**Hiện trạng [M].** 157 file, 25.257 dòng; `AdsHelper.kt` 3.669 dòng với 129 hàm không private, là
god-object của toàn bộ lớp quảng cáo (banner, rect, native, open, inter, full2, rewarded, cover, policy).
Consumer rules đã hẹp và đúng (Gson, Retrofit, enum, Meta). Version một nguồn (`sdkVersion` → BuildConfig).
0 kiểm ABI, `explicitApi` tắt, bắt kế thừa `AppPluginBase : MultiDexApplication`. Một AAR 2,0 MB kéo 227
artifact runtime; demo release 27 MB; app tiện ích gánh 16 adapter game.

### 2.9 Kiểm thử và ma trận thiết bị — TRUNG BÌNH

**Hiện trạng [M].** 597 unit test / 63 lớp, chạy được không cần Robolectric; test cho ads dựng `AdsHelper`
bằng reflection. Không có instrumentation test. Chưa chạy máy thật (Redmi bận phiên khác), chưa API 26–35,
chưa Meta/MAX tải thật, chưa luồng mua. Không có test hợp đồng cho keep rule, keep.xml, tên event.

### 2.10 Trải nghiệm tích hợp — TRUNG BÌNH

**Hiện trạng [M].** Doc 38, 47, 60–64 và CHANGELOG theo Keep a Changelog; `MonetizationSdk.configure`
trả báo cáo tự kiểm tra; CI trên PR và release theo tag. Thiếu README gốc và quickstart 10 phút; thiếu
Inspector (doc 60 H); app demo không giống app thật (mục 2.3).

---

## 3. Bộ tiêu chí phát hành (cổng G1–G10; từ 1.7.0 thêm G11, G13; từ 1.8.0 thêm G12)

Đây là **cổng**, không phải mong muốn. Một bản SDK chỉ được gắn tag khi qua hết cổng của phase tương ứng
(mục 5). Cột "Cách đo" là lệnh hoặc script chạy được; cột "2026-09-24" là số đo lúc viết (thêm số Phase B
tới lúc đó); cột "2026-09-25 (1.4.0)" là số đo lại khi chốt 1.4.0 (Task 17, cây `05ca0bf` + số phiên bản 1.4.0,
emulator API 26/28/37; sau fix wave 1 — `6529164`, `9621b30` — chạy lại cổng release và cổng thiết bị API 37 với
`EVENTS=600`; log ở doc 64 mục G).

Quy ước: `APK=app/build/outputs/apk/utility/release/app-utility-release.apk`, `MAP=app/build/outputs/mapping/utilityRelease/mapping.txt` (từ 1.6.4 demo có hai flavor; `tools/release_gate.sh`, `tools/device_gate.sh` nhận `--variant utility|game`, mặc định `utility`),
`ANDROID_SERIAL` bắt buộc đặt.

### G1. Ổn định

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) |
|---|---|---|---|---|---|
| Đường crash đã biết trong SDK | 0 | 0 | Sổ crash `DOC/testing/*` + memory; mỗi đường có test hồi quy | **3** | 0 ✓ (3 đường của 1.3.0 đã sửa, có test hồi quy; không đường mới) |
| `FATAL` trong logcat bản release, cold start + 3.000 monkey × 3 seed | 0 | 0 | `tools/device_gate.sh` (Task 6) | 1 (startup) + 2 (monkey) | 0 ✓: cổng thiết bị API 37/28/26 (1.500 sự kiện × 3 seed) và 15 lượt kịch bản tay API 37 (bản release và debug) |
| Crash rate user-perceived của app (Play vitals, 28 ngày) | < 1,09% | < 0,5%; phần có frame `com.app.plugin` ≤ 0,1% tuyệt đối | Play Console + Crashlytics lọc custom key `appplugin_version` | chưa có key | chưa có dữ liệu Play (key có từ 1.4.0; console chưa kiểm) |
| Test hồi quy cho từng crash đã sửa, đã cố tình phá một lần | bắt buộc | — | Điều 6.5; log đỏ lưu `DOC/testing/<task>-red.log` | 0/3 | ✓ Phase A; Task 17: `DOC/testing/task17-*-red.log` |

### G2. ANR và ngân sách main thread

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) | 2026-09-29 (1.6.1) | 2026-09-29 (1.6.2) | 2026-09-29 (1.6.3) | 2026-09-30 (1.6.4) |
|---|---|---|---|---|---|---|---|---|---|
| `am_anr` trong monkey và kịch bản | 0 | 0 | `adb logcat -b events -d \| grep -c am_anr` | 0; Phase B: 0 ×4 (Galaxy A25, `5a18948`) ✓ | 0 ✓ ở 3 cổng thiết bị và mọi kịch bản tính điểm. Một lượt kịch bản trên emulator có cửa sổ bị bỏ: máy ảo quá tải (load 42, CPU pressure 92%), ANR `Input dispatching timed out`; chạy lại headless: 0 | Monkey seed 1 đầu (API 37, `af4161b`, APK `3de1ace…`): am_anr=1 (`Input dispatching timed out (Application does not have a focused window)`, host load 49,77, CPU 99%, nhiều lane chạy song song; `DOC/testing/device-gate-monkey-emulator-5570-20260928-223435-seed1.log`); 2 lần chạy lại cùng APK trên host yên hơn (load 7,3/9,5) PASS am_anr=0 (`t161-r-emu37-rerun{1,2}-gate.log`); APK release cuối (R2, `46af047`) API 37 PASS am_anr=0 (`t161-r2-emu37-gate.log`), API 28 R2 PASS am_anr=0 (`t161-r2-emu28-gate.log`), API 26 cùng luật R8 PASS am_anr=0 (`t161-g2-api26-gate.log`), Samsung A25 R2 PASS am_anr=0 (`t161-r2-R5CX10P9EKZ-gate.log`). **Phân loại: tranh chấp host, 1 lần, không lặp lại 3/3** | Mọi lượt 1.6.2 (APK `b2824c54…` từ `40fe798`, `EVENTS=1500`): `am_anr` = 0 ở cả 6 lần đếm của API 37, API 28, API 26 (Task R), API 26 (G3 chạy lại) và Redmi 65c9b673; không lượt đỏ về ANR, không phải chạy lại vì ANR (`t162-r-emu37-gate.log`, `t162-r-emu28-gate.log`, `t162-r-emu26-gate.log`, `t162-g3-emu26-gate.log`, `t162-r-65c9b673-gate.log`). Lượt API 26 Task R **FAIL** vì R8 0/6/6 (không phải ANR/crash), xem G3 và §5 "Trạng thái 1.6.2". Samsung R5CX10P9EKZ không cắm: chưa đo | `am_anr` 0 ở mọi chặng của mọi lượt 1.6.3: emulator API 37/28/26 và Galaxy A25 (2 lượt); không lượt đỏ về ANR (`t163-r-{emu37,emu28,emu26,R5CX10P9EKZ}-gate.log`, `metrics-163` #16) | `am_anr` 0 ở Galaxy A25 3/3 lượt, API 28 và API 26 (PASS lượt đầu, mọi chặng); **API 37: 3/4 lượt `am_anr=1` ở seed 1, phân loại contention** (A/B 6/6 sạch, §5 1.6.4). Vòng 1 (`8a1e74f`, APK cũ, đã thay): API 28 đỏ 2/3 lượt (`t164-r-emu28-gate.log`, `…-rerun2.log`). Log vòng 2: `t164-r-R5CX10P9EKZ-gate-2-{1,2,3}.log`, `t164-r-emu{28,26}-gate-2.log`, `t164-r-emu37-gate-2{,-rerun1,-rerun2,-rerun3}.log` |
| ANR rate của app (Play vitals) | < 0,47% | < 0,3%; phần SDK ≤ 0,05% | Play Console | — | — | — | — | — | — |
| Vi phạm StrictMode disk read/write có frame `com.app.plugin` khi cold start | — | 0 | `tools/device_gate.sh` với harness Task 5: dòng `strictmode cold start: violations=…, with sdk frame=…, by sdk=…` (retrace bằng `$MAP`, in cả số raw; thiếu mapping/java/r8.jar thì FAIL); từ 1.4.0 thêm `prefs first load=…, other=…` (theo dõi) và dòng `strictmode gate cold start: by sdk=N (need 0; …)`. **Thước đo từ Task B: by sdk = 0, không trừ prefs** (FAIL khi N > 0; `c7c9610`) | ~12 lượt, ~0,5 s (emulator); Phase B (Galaxy A25, `13a395c`, log `wt15-app-cold.log`): 11 vi phạm DiskRead, 9 có frame SDK, **7 do SDK** sau retrace (2 còn lại là `super.onCreate()` của `Application` — Typeface của Samsung); 7 vi phạm đó là lần nạp SharedPreferences đầu trên main (kể cả của UMP) ✗ — ngưỡng chờ §7 #9 | **Task B (`0bb53ea`, `c7c9610`): by sdk = 0, không trừ prefs.** `PrefsWarmup` nạp sẵn 4 file prefs trên luồng nền từ `attachBaseContext`, `onCreate` chờ ≤ 250 ms (log `prefs warmup waited=N ms`). **Đo sau Task B (DOC/64 "Cổng cuối 1.4.0"):** emulator API 37/28/26 trên APK `c4749ca` StrictMode 0 dòng, by sdk 0 ✓ (`device-gate-emulator-5570-20260925-161032.log`, `…-5580-20260925-161334.log`, `…-5580-20260925-161543.log`); Redmi trên APK `c216628` (sau Task B, trước Task C–G) by sdk 0 (prefs 0, other 0), 3 cold start `prefs warmup waited=0 ms` ✓ (`device-gate-65c9b673-20260925-103157.log`). APK cuối `c4749ca` chưa chạy trên máy thật. Trước Task B, ngưỡng áp tạm §7 #9: vi phạm do SDK trừ lần nạp SharedPreferences đầu = 0 (cổng FAIL khi > 0). Lần đầu (API 37): 9 do SDK, prefs 8, **khác 1** (`File.exists` trong `ContextImpl.ensurePrivateDirExists`, từ `context.filesDir` trong `initPlugin`) → FAIL → sửa `05ca0bf`, làm lại ở `6529164` (gắn ngay bằng `applicationInfo.dataDir`, không đụng đĩa). Sau `05ca0bf`: API 37 6 do SDK (prefs 6, khác 0), API 28 8 (8, 0), API 26 8 (8, 0); sau `6529164` (API 37, `EVENTS=600`): 5 (5, 0) ✓ theo ngưỡng áp tạm; "= 0 kể cả prefs" ✗. (`~duration` StrictMode in ra tính tới hết message của looper, không phải chi phí của lần đọc.) | **1.6.1 P1 (`PrefsWarmup` nạp thêm `appsflyer-data`/`com.applovin.sdk.shared`):** API 37 và API 28 (R2, `46af047`) — `by sdk` 0/0 cả hai chặng (prefs first load 0, other 0), `appsflyer started` n=1 (`t161-r2-emu37-gate.log`, `t161-r2-emu28-gate.log`); API 26 (cùng luật R8, chưa chạy lại trên R2) — `by sdk` 0/0, `appsflyer started` n=1 (`t161-g2-api26-gate.log`); Samsung A25 (R2) — `by sdk` 0/0, `appsflyer started` n=1 (`t161-r2-R5CX10P9EKZ-gate.log`). Hàng 16 kế hoạch (đỏ → xanh P1 trên release, API 28): đỏ `by sdk=1` (`t161-t3-gate-red-api28.log`) → xanh `by sdk=0` (R2), cả hai `appsflyer started … n>=1`. `prefs warmup waited=` trên API 28/26 (SHOULD-FIX đo thêm, review cuối T1 m4, bản debug `46af047`, ×3 mỗi API):
**0/0/0 ms cả hai API** (cần ≤ 250 ms), 0 dòng warning/error mỗi lượt, `appsflyer started` 1/1/1 mỗi API, FATAL 0/0/0
(`t161-w-emu28-warmup.log`, `t161-w-emu26-warmup.log`). StrictMode debug đối chứng đường AppsFlyer (vòng 1, `af4161b`,
tham khảo — không chặn release): `by sdk` 0/0/0 ×3 (1.6.0: 1/1/1) (`t161-r-emu37-strictmode-debug.log`) | **`by sdk` = 0 ở cả hai chặng** (cold start, cold start 2), `strictmode armed` n=1/1 (canary có mặt), `appsflyer started` n=1 trên API 37, API 28, API 26 (cả hai lượt) và Redmi API 34 (Redmi: violations 1/2, frame SDK 0). Cửa sổ chờ `prefs warmup waited=0 ms`, `timeouts=0` ở mọi lượt; `optional prefs ms` cold/cold2: API 37 3/4, API 28 94/3, API 26 21/13 (chạy lại 15/23), Redmi 32/1 → max `min(waited, optional)` = 0 ms nên Task 1b không kích (`t162-r-emu37-gate.log`, `t162-r-emu28-gate.log`, `t162-r-emu26-gate.log`, `t162-g3-emu26-gate.log`, `t162-r-65c9b673-gate.log`) | `by sdk` 0/0 ở emulator API 37, 28, 26 và Galaxy A25 lượt chạy lại (`strictmode armed` n=1/1, `appsflyer started` n=1). Galaxy A25 **lượt 1 FAIL**: cold start 2 `by sdk=3` (cold start 1: 0), 3 vi phạm `DiskRead` nằm trong `system_server` (`FreecessController.unfreezePackageForOLAF`) qua binder từ `BillingClient.startConnection` ← `IapHelper.connect`, phân loại trạng thái máy / gốc OS, không phải hồi quy 1.6.3; lượt chạy lại 14 phút sau cùng APK `842bbf93…a42e` PASS 0/0 (`t163-r-R5CX10P9EKZ-gate.log`, `t163-r-R5CX10P9EKZ-gate-rerun1.log`); `prefs warmup waited=0 ms`, `timeouts=0` ở mọi máy. Redmi chưa đo | `by sdk` 0/0 ở Galaxy A25 3/3 lượt (`strictmode armed` n=1/1, 0 dòng StrictMode có `IapHelper`/`BillingClient`/Freecess — #11 của 1.6.3 không tái diễn), emulator API 37 (4 lượt), 28, 26 (`violations=0` + armed n=1 ×2); `appsflyer started` n=1 mọi lượt; `prefs warmup waited=0 ms`, `timeouts=0` ở 9/9 lượt. Redmi chưa đo (`t164-r-*-gate-2*.log`) |
| Thời gian SDK chiếm main thread trong `Application.onCreate` | — | ≤ 30 ms máy tầm trung; ≤ 80 ms emulator | log `AppPlugin: AppPluginBase.onCreate main-thread ms=` (Task 15) | Phase B: **42 ms** (Galaxy A25, `13a395c`) ✗ trên mục tiêu 30 ms | emulator 112 ms (API 37), 131 (28), 106 (26) ✗ trên 80 ms; sau fix wave 1 API 37: 35 ms. Mỗi số là một lần đo trên emulator ồn (206 ms ở lượt FAIL đầu), không dùng để so trước/sau. Redmi (APK `c216628`, sau Task B): 15–17 ms qua 3 cold start ✓ (`redmi14-B-warmup-coldstart.log`); APK cuối chưa đo trên máy thật | Chưa đo lại trên APK R2. Vòng 1 (`af4161b`, chặng 2 non-EEA có AppsFlyer, API 37): **16 ms** (mục tiêu ≤ 80 ms emulator, không chặn) (`t161-r-emu37-coldstart2-recovered.log`); StrictMode debug (tham khảo, API 37): 86/102/141 ms, trung vị 102 (`t161-r-emu37-strictmode-debug.log`) | Bản release, cold/cold2: API 37 **32/26** ✓, API 28 **85**/29 (cold vượt 80), API 26 63/**89** (cold2 vượt 80), chạy lại API 26 **124/193** ✗, Redmi **33**/11 (cold vượt 30). Mục tiêu không chặn; vượt ở 4/8 chặng emulator và 1/2 chặng Redmi, chưa rõ nguyên nhân (lượt chạy lại API 26 host yên, load1 2,90–7,77); Samsung không cắm: chưa đo (`t162-r-emu37-gate.log`, `t162-r-emu28-gate.log`, `t162-r-emu26-gate.log`, `t162-g3-emu26-gate.log`, `t162-r-65c9b673-gate.log`) | `onCreate main-thread ms` cold/cold2 và `initPlugin ms` (manager/meta/helper+gate/logevent/total): Galaxy A25 **31/40** (16/1/11/0/28 và 26/0/14/0/40; cold2 vượt 30 ms, không chặn); emulator API 37 115/12 (12/34/66/2/114 và 8/0/4/0/12), API 28 135/40 (103/0/19/0/122 và 35/1/4/0/40), API 26 263/209 (190/15/53/4/262 và 179/0/29/0/208) — vượt 80 ms ở API 37 cold, API 28 cold, API 26 cả hai chặng, không chặn; `total ≤ main-thread` mọi lượt, bước lớn nhất thường là `manager`. Ngưỡng chặn máy thật API 26–28 bỏ theo quyết định phạm vi 2026-09-29. Redmi chưa đo (`t163-r-*-gate.log`) | `onCreate main-thread ms` cold/cold2: Galaxy A25 **16/20, 36/21, 15/29** (`initPlugin total` 15/19, 35/20, 15/26; vượt 30 ms 1/6, không chặn); API 28 22/258, API 26 44/767, API 37 (4 lượt) 303/1008, 891/179, 992/421, 510/249; bước lớn nhất `manager` (API 28 cold2 224, API 26 cold2 682); `total ≤ main-thread` và `initPlugin ≠ none` mọi lượt; ≤ 80 ms emulator chỉ đạt ở cold1 API 28 và API 26 (không chặn; emulator ngày 09-30 chậm 4–8× từ trước mã app — giả thuyết tranh chấp host, chưa chứng minh). Redmi chưa đo (`t164-r-*-gate-2*.log`) |
| Chênh `TotalTime` của `am start -W` giữa demo có SDK và demo tắt SDK (`flagTest`=0, không init) | — | ≤ 150 ms | 5 lượt cold start, lấy trung vị | chưa đo chênh; TotalTime có SDK: 720 ms (`13a395c`), 942 ms (`5a18948`), Galaxy A25 | chưa đo chênh; TotalTime cài mới 1284 / 930 / 879 ms (API 37/28/26), cold start có consent 486 ms (API 37) | Chưa đo chênh có/tắt SDK. TotalTime R2 (`46af047`, hai chặng cold start / cold start 2, không phải chênh): API 37 1174/519 ms, API 28 843/1196 ms, Samsung A25 889/683 ms (`t161-r2-emu37-gate.log`, `t161-r2-emu28-gate.log`, `t161-r2-R5CX10P9EKZ-gate.log`) | Chưa đo chênh có/tắt SDK. TotalTime (cold start / cold start 2, không phải chênh): Redmi 802/454 ms (`t162-r-65c9b673-gate.log`), API 26 chạy lại 832/879 ms (`t162-g3-emu26-gate.log`); API 37 794/1349 ms (`t162-r-emu37-gate.log`), API 28 845/1348 ms (`t162-r-emu28-gate.log`) | Chưa đo chênh có/tắt SDK. TotalTime (cold start / cold start 2, không phải chênh): Galaxy A25 1310/640 ms (1.6.2: 761/496), API 37 2834/718, API 28 2309/1620, API 26 1006/993 ms (`t163-r-*-gate.log`) | Chưa đo chênh có/tắt SDK. TotalTime (cold start / cold start 2, không phải chênh): Galaxy A25 592–1391 / 590–606 ms (1391/602, 763/606, 592/590; 1.6.3 1310/640), API 28 486/8005, API 26 320/4665, API 37 26017/20156, 15912/8825, 56292/9190, 23535/11481 ms (1.6.3: 2309/1620, 1006/993, 2834/718; emulator chậm hơn, xem hàng trên) (`t164-r-*-gate-2*.log`) |

### G3. Bản release là sản phẩm thật

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) | 2026-09-29 (1.6.1) |
|---|---|---|---|---|---|---|
| App demo build như app thật | targetSdk 36, R8 full, shrinkResources | có ở CI mỗi PR | `app/build.gradle.kts`; job `release-gate` | targetSdk 34, R8 tắt | ✓ targetSdk 36, R8 full, shrinkResources; job `release-gate` | — (không đổi) |
| Ad unit sống trong `resources.arsc` của demo release | > 0 | = số khai trong `ads_ids.xml` | `unzip -p $APK resources.arsc \| LC_ALL=C grep -a -o "ca-app-pub-[0-9/~]*" \| wc -l` | **0** (không keep.xml) / 8 (có) | 8 (cần ≥ 8) ✓ | 9 (≥ 9) ✓ (R2, `46af047`, `t161-r2-release-gate.log`) |
| 4 chuỗi R8 trong logcat cold start | 0 | 0 | `adb logcat -d \| grep -cE "ClassNotFound\|NoSuchMethod\|NoClassDef\|FATAL"` | 4 (registrar + startup) | 0 ✓ sau cold start và sau monkey (API 37 raw 0; API 28/26 raw 1 = dòng ProviderInstaller trong allowlist) | 0 ✓ trên API 37/28 (R2) và Samsung A25 (R2), API 26 (cùng luật R8, G2) sau cold start/monkey/cold start 2 (`t161-r2-emu37-gate.log`, `t161-r2-emu28-gate.log`, `t161-r2-R5CX10P9EKZ-gate.log`, `t161-g2-api26-gate.log`) |
| Tỉ lệ lớp `com.app.plugin` bị đổi tên trong `mapping.txt` | ≥ 90% | ≥ 95% | `tools/release_gate.sh` | chưa đo trên demo | 248/252 = 98% ✓ | 284/288 = 98% ✓ (R2, `t161-r2-release-gate.log`) |
| Consumer rules có test hợp đồng | bắt buộc | — | `ConsumerRulesTest`, `KeepXmlTest` | chưa có | ✓ | ✓ + test soi trường `kotlinx.serialization.internal.CachingKt.useClassValue` (phải còn `private static final boolean`; G2 review fix I1(a), `ConsumerRulesTest`) |
| Trang 16 KB | pass | pass | `zipalign -c -P 16 -v 4 $APK` | chưa chạy trên demo | Verification successful ✓ | Verification successful ✓ (R2, `t161-r2-release-gate.log`) |
| Chữ `ClassValue` trong `mapping.txt` của bản release (R8 9.2 outline `ClassValue.get` của kotlinx.serialization vào lớp outline dùng chung `AndroidAttribution$$ExternalSyntheticApiModelOutline0` của Unity Ads) | 0 | 0 | `tools/release_gate.sh` (`tools/lib/release_gate_map.sh`, hàm `check_mapping_classvalue`; FAIL khi grep không đọc được mapping) | — (cổng chưa tồn tại) | — (cổng chưa tồn tại) | Đỏ 51 dòng trên mapping trước sửa (`t161-g2-fix-gate-red.log`) → xanh 0 dòng sau sửa G2 (`t161-g2-fix-gate-green.log`), PASS trên APK R2 cuối (`t161-r2-release-gate.log`) |

### G4. Consent và quyền riêng tư

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) | 2026-09-28 (1.6.0) | 2026-09-29 (1.6.1) |
|---|---|---|---|---|---|---|---|
| Dòng khởi tạo mạng quảng cáo trước khi form UMP đóng (cài mới, EEA) | 0 | 0 | logcat tag `AppPlugin` + `UserMessagingPlatform` | 0 ✓ | 0 ✓ (bản release, cài mới, EEA, API 37) | — | — |
| Byte gửi tới mạng bởi uid app trước cú bấm consent (cài mới, EEA, chỉ SDK) | — | ≤ 50 KB (UMP + Remote Config) | `adb shell dumpsys netstats detail \| grep -A3 "uid=$UID"` trước và sau | chưa đo; biết trước: `session_start` của log server SDK mang `ANDROID_ID` trước consent (§7 #13) | chưa đo; `user_id` không còn là ANDROID_ID (§7 #13), nhưng `first_open`/`session_start` vẫn đi trước consent khi app bật DataBucket (DOC/66 P2, chờ chốt). | **1.6.0 Task 6 (O3 (a), §7 #17):** `TelemetryConsentGate` giữ event server tới khi có tầng. Emulator API 37, bản debug demo bật DataBucket tới sink cục bộ (`10.0.2.2:8089`), cài mới, EEA: **POST trước cú bấm consent = 0** (17 event giữ); Consent → 17 xả (`flushed 17`); "Do not consent" → 0 trong 17 event giữ được gửi (`dropped 17`) (`t160-t6-sink.log`, `t160-t6-emu-run*.log`). Byte netstats chưa đo lại (chỉ còn UMP + Remote Config + quảng cáo theo cổng khác). | Kill switch `logevent` bật lúc event server đang giữ chờ consent: khi xả, các event đó bị `onResult(false)`, không gửi (trước đây gửi hết); event phát sau kill switch không đổi (1.6.1 CHANGELOG Sửa; unit `TelemetryConsentGate*`/`LogEventServer*` 41 test, 0 fail — chỉ unit, không giả lập Remote Config trên máy, `t161-r-row13.log`) |
| Tier sau "Do not consent" | ≠ GRANTED | DENIED (hoặc PARTIAL nếu chỉ purpose 1) | log `consent tier=` + `IABTCF_PurposeConsents` | Phase B: **DENIED** ✓ (Task 8, Galaxy A25: `consent tier=DENIED (ump=OBTAINED, tcf=DENIED)`, tầng từ TCF) | DENIED ✓ (release và debug, API 37) | — | — |
| Firebase Analytics, FCM, AppsFlyer chờ consent ở EEA | bắt buộc | — | logcat `FA`, `FirebaseMessaging`, `AppsFlyer_` trước form | Phase B ✓ (Task 9, Galaxy A25: trước câu trả lời 0 dòng `AppsFlyer_`/`FirebaseMessaging`, token 0; FCM và AppsFlyer chạy sau consent); còn ping không định danh của FA trước câu trả lời đầu (§7 #10) | ✓ trước câu trả lời: 0 dòng `AppsFlyer_`/`FirebaseMessaging`, `fcm auto-init held`; từ chối: token 0, AppsFlyer start với consent toàn `false` (`start=activity`, bản debug có `af_app_id` giả, cả lần mở lại); đồng ý: token 1 | — | — |
| Consent Mode v2 `setConsent` được gọi với 4 loại theo tier | bắt buộc | — | logcat `FA-SVC` "Setting consent" (bật `adb shell setprop log.tag.FA VERBOSE`) | Phase B: unit ✓ (`AnalyticsConsentTest`); máy: dòng SDK `analytics consent applied: Settings(…)` đúng theo tầng (Task 9); dòng `FA-SVC` chưa thấy | ✓ máy: `analytics consent applied: Settings(…)` đúng theo tầng, kèm `collection=disabled` (DENIED) / `app-owned` (GRANTED); `FA-SVC` chưa thấy | — | — |
| Tier được lưu và khôi phục sau khi mở lại | bắt buộc | — | `ConsentStateTest` + log lúc mở | Phase B ✓ (Task 8: `consent tier restored=DENIED` khi mở lại) | ✓ mở lại: `consent on record, starting` 249 ms sau `waiting` | — | — |
| Mục Privacy options khi `REQUIRED` | có API + demo có nút | — | Điều 20.9; demo UI | API ✓, demo chưa | API ✓, demo chưa | API ✓, demo ✓ (nút Privacy options, 1.6.0 Task 8b, `2085168`; `isPrivacyOptionsRequired=true` ở vùng US-state) | — |
| GPP opt-out (bang Mỹ, US states) chuyển cho 4 consumer | — | — | logcat tag `AppPlugin`: `analytics consent applied: … gpp=`, `appsflyer sharing filter: all partners`, `ads max: doNotSell=…`, `ads fb: LDU on\|off`, `consent updated, tier unchanged: gpp=…, applying again` | — | — | GPP opt-out chuyển cho 4 consumer (1.6.0 Task 8a): Firebase Consent Mode `ad_user_data`/`ad_personalization` denied, AppsFlyer `setSharingFilterForAllPartners()` trước start, MAX `setDoNotSell`, Meta AN + facebook-core LDU; tầng và `tierAllows` không đổi; đánh giá lại khi UMP cập nhật mà tầng không đổi. Unit: 1444 → 1473 (Task 8a, 29 test mới, 10/10 đột biến đỏ) → 1495 → 1497 (Task 8b, vector thật, `1735555`); `release_gate` PASS. Số đo emulator (Task 8b, emulator-5570 API 37, debug + release): UMP không hiện form US-state lúc mở app (`NOT_REQUIRED`, GPP `DBABL~BAAAAAAAAg` = không áp dụng; đo trên emulator với debug geography US, `t160-t8-emu-us.log`); form "My data preferences" mở qua Privacy options (`isPrivacyOptionsRequired` = true). Chọn "Don't sell or share" → `DBABL~BVQVAAAAAg` → `gpp=optOut`: Consent Mode `ad_user_data`/`ad_personalization` denied, Meta AN LDU on, AppsFlyer `setSharingFilterForAllPartners` trước start ở lần mở kế, MAX bỏ qua (demo không có devKey); giá trị tầng không đổi (`consent tier=GRANTED` trước và sau, `privacy options closed: tier=GRANTED`), có dòng `consent updated, tier unchanged: gpp=optOut, applying again`, và đúng một `analytics consent applied` sau dòng đó; banner `tryLoad ok`. Chọn lại "Allow" → `DBABL~BVQqAAAAAg` → `gpp=ok`. StrictMode do GPP = 0; bản release bật StrictMode (`flagTest=1` ở `app/src/main`) nhưng không có `af_app_id` nên AppsFlyer không `init`: 0 vi phạm; bản debug (có `af_app_id`) cho `by sdk=1` mỗi cold start — đường `AppsflyerHelper.startAfterConsent → af.init`, không phải do GPP (xem CHANGELOG "Giới hạn đã biết"). `device_gate EVENTS=600` PASS. Mục Privacy options: demo có nút (Task 8b, `2085168`). | **1.6.1:** tên hàm AppsFlyer đổi — GPP opt-out nay gọi `setSharingFilterForPartners("all")` thay `setSharingFilterForAllPartners()` (deprecated từ AppsFlyer 6.4; javap AAR 6.16.2: hàm cũ gọi đúng hàm mới với `"all"`) — **hành vi không đổi**, hết cảnh báo deprecation; DOC/62:302 cập nhật theo tên mới (CHANGELOG 1.6.1 Sửa). AppsFlyer start lại trong cùng process (rút rồi cấp lại consent) với GPP không opt-out: gỡ bộ lọc partner do SDK đặt trước đó (`setSharingFilterForPartners()` rỗng, log `appsflyer sharing filter: cleared`); GPP vắng thì không gọi gì (hết giới hạn 1.6.0 "bộ lọc đã đặt trong process không được gỡ", review Task 8a m8). Dòng `appsflyer started after consent` ghi `consent tier=<tầng> in\|outside GDPR scope` thay `tier=<tầng>` (review Task 8b); unit AppsFlyer (m8 + nhãn) 47 test, 0 fail (`t161-r-row14-unit.log`); máy (R2, API 37/28, Samsung A25; API 26 cùng luật G2): `appsflyer started` n=1 mỗi chặng 2 |

### G5. Luật quảng cáo

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) |
|---|---|---|---|---|---|
| Cơ chế dụ bấm nhầm (Điều 13.2) | 0 | 0 | grep `GridForExcludeTouch\|flagClick4AutoClose` = 0; lưới test chỉ dưới `flagTest` | 0 ✓ | 0 ✓ |
| Nút đóng full-screen ≤ 15 s (Điều 13.6) | ≤ 15 s | 3–5 s | hằng `timeShowBtClose` của lớp placement (3 s native full, 1 s NtCl), không có giá trị từ xa; `grep -rn "timeShowBtClose = " src/main/java` | 3 s ✓ | 3 s ✓ |
| Một policy tần suất, một khoá `ads_policy_android` có `schema_version` | bắt buộc | — | `AdsPolicyTest`; log `ads policy schema=` | Phase B: unit ✓ (Task 10, `AdsPolicyTest`, `AdsPolicyWiringTest`); máy chưa | unit ✓; máy: `ads policy schema=1 firstSession=false appOpenFrom=1 gap=fullDeltatime screens=0 clickCooldownMs=5000 appOpenMaxAge=240min fullscreenMaxAge=60min` (mặc định; `fullscreenMaxAge` thêm ở fix wave E) |
| Phiên đầu không interstitial; khoảng cách ≥ 240 s; app open từ phiên 2 | mặc định theo doc 62 F | — | `AdsPolicyTest` + kịch bản device | Áp tạm (§7 #1, #2): mặc định = hành vi 1.3.1; luật doc 62 F bật bằng Remote Config, unit ✓; máy chưa | Đã chốt §7 #1, #2 (DOC/66, 2026-09-25): mặc định giữ nguyên = 1.3.1, A/B ở 1.5.0; luật doc 62 F bằng Remote Config: unit ✓, máy chưa (cần console). "Phiên" = một lần process khởi động (doc 62 F) |
| App open hết hạn sau 4 giờ | bắt buộc [P] | — | `AdsEcmpsTest` tuổi > 4 h → không hiện, load lại | Phase B: unit ✓ (Task 11, `AdsPlBaseMaxAgeTest`, `AdsEcmpsTest`); máy chưa | unit ✓; máy không kiểm (đổi giờ máy làm hỏng emulator). **Ngoại lệ MAX app open** (Task I1): `MaxAppOpenAd` tự hết hạn và nạp lại trong cùng đối tượng (`MaxFullscreenAdImpl.onAdExpired`, applovin-sdk 13.6.2), nên chỉ AdMob app open áp 4 giờ; `AdsMaxPlOpenExpiryTest`: MAX phút 241 vẫn hiện, 0 `app_open_expired`. MAX hết hạn khi app ở nền (không Activity) báo `onAdLoadFailed(MISSING_ACTIVITY)` vào listener không có request chờ: từ đợt sửa review Task I (I-2/M1) listener bỏ callback đó (`LateLoadCallbackTest`), không request tự phát, không `onLoadFailed` cho app; từ review Task J IMP-2 (`beab06e`) đối tượng đang giữ cũng bị bỏ khỏi mục và `destroy()` (`dropHeldAdOnIdleLoadFailure`), nên lần `showOpenAd`/load sau tạo `MaxAppOpenAd` mới |
| Interstitial, rewarded, native full hết hạn sau 60 phút | bắt buộc [P] (GMA: hết hạn sau 1 giờ) | — | `AdsFullscreenExpiryTest` (7 placement), `AdsMobPlIntersExpiryTest`: > 60 phút → không hiện, `ad_expired`, load lại; MAX interstitial/rewarded phút 61 vẫn hiện, 0 `ad_expired` | — | Task E (D5): unit ✓, `fullscreen_max_age_minutes` (60; 1–240); máy không kiểm (đổi giờ máy). **Ngoại lệ MAX interstitial và rewarded** (review E I1): MAX tự hết hạn và nạp lại trong cùng đối tượng (`onAdExpired`), mốc load của SDK không đặt lại nên hạn của SDK chỉ từ chối nhầm; MAX native full vẫn áp hạn |
| Remove-ads theo mask từng định dạng (Điều 13.4) | bắt buộc | — | `EntitlementsTest`: gói Pro = ALL, gói rẻ = INTERRUPTIVE, rewarded vẫn còn | Phase B: unit ✓ (Task 12, `EntitlementsTest`, `RemoveAdsMaskTest`) | unit ✓; nhãn `err` thống nhất `<hàm> isremoveads or disable` (Task 17) |
| `onAdDismissed` tới app đúng 1 lần mỗi lượt show | bắt buộc | — | `Full2ChainDismissTest` + device | ✓ | ✓ kịch bản b, c, d, h (API 37): một `dismissed` mỗi lượt; (h) `dismiss already dispatched, skip` 1 |
| Full2 không nối sau quảng cáo cha đóng vì host bị huỷ (§7 #6) | bắt buộc [P] (Play: không hiện toàn màn hình bất ngờ) | — | `Full2HostDestroyTest` (a–d) + kịch bản máy (d) và xoay lúc cha native full hiện | — | Task F: unit ✓; **máy chưa — controller kiểm.** Kỳ vọng mới cho kịch bản (d) của Task 17: sau xoay lúc Full2 hiện, không Full2 nào hiện lại trên host mới. Thêm: xoay lúc interstitial cha native full (flagImage 1) hiện → `showFull2 skipped: parent closed by host destroy` 1, `ad_show_fail_reason` `host_destroyed` 1, 0 `queue Full2 until host resume`, 1 `dismissed`. Cha GMA (j1): như cũ, `queue Full2 until host resume` rồi Full2 trên host mới |

### G6. Đo lường

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) | 2026-09-28 (1.6.0) | 2026-10-02 (1.7.0) |
|---|---|---|---|---|---|---|---|
| `ad_impression` mỗi impression trả tiền | đúng 1 | — | Firebase DebugView với app đã nối AdMob: đếm 1; `AdRevenueRoutingTest` | Phase B: unit ✓ (Task 13, `ads_admob_firebase_linked`); DebugView chưa; caveat mediation (doc 62 mục 2.3) | unit ✓; DebugView chưa | — | H6a (`t170-r-{emu37,emu28,emu26,65c9b673}-h6a.log`): 12/12 phiên hợp lệ, Firebase `ad_impression` (theo `imp_id`) = AppsFlyer `rev af … started` = JSONL `ad_revenue`, ba số bằng nhau (3/3/3 ở pha A, 2/2/2 ở hai phiên hằng ngày), hiệu tập rỗng, chênh 0; DebugView chưa (đếm bằng logcat FA verbose). |
| Tên event: ≤ 40 ký tự, `^[a-z][a-z0-9_]*$`, không tiền tố cấm | 100% | — | `EventNameContractTest` | Phase B: unit ✓ (Task 14b: mọi tên trong `EventNames`, chỗ gọi không viết literal, 0 vi phạm; 2 tên camelCase đổi, áp tạm §7 #5; `eventTag` hằng chữ thay tên lớp; ngoại lệ đóng 5 mốc tROAS `AdLTV_OneDay_Top*Percent`; tên ghép lúc chạy `aad_*`, `show_ads_*` ngoài tầm kiểm tĩnh) | unit ✓, thêm tự kiểm `breaksContract` và getter bắt buộc cho `eventTag` (Task 17). Ngoài hợp đồng (follow-up): tên ghép lúc chạy `aad_*`/`show_ads_*` (Task E D4: 8 tên `fir_rm_fet_*` đã vào `EventNames`, tên không đổi, `RemoteConfigFetchBucketTest`); test chỗ gọi bị vượt khi tên đi qua biến. Task E (D3): nhãn `not_plm_cf` thành `not_plm_cf_<eventTag>_<adsType>`, `eventTag` hằng chữ của 18 lớp mạng (`AdsBase`), không còn `simpleName` trong `AdsBase.kt` (`EventNameContractTest`, `AdsBaseNotPlmCfLabelTest`) | — | unit ✓ trong 1653 test ×3 (`t170-r-full-{1,2,3}.log`, gồm `EventNameContractTest`); 9 hằng mới trong `EventNames` (`ad_request`, `ad_fill`, `ad_no_fill`, `ad_show_attempt`, `ad_show_fail`, `ad_click`, `consent_form_shown`, `consent_choice`, `session_end`). |
| Hàng đợi event: trần 500, tối đa 4 request đang bay, backoff 1→60 s | bắt buộc | — | `LogEventQueueTest` | Phase B: unit ✓ (Task 14a: trần 500, 4 đang bay, backoff 1→60 s theo đồng hồ đơn điệu, 4xx không thử lại; `LogEventQueueTest`, `LogEventSenderTest`, `DecisionLogReplayTest`) | unit ✓; Task 17: replay decision log chờ `LogEventServer.init`; bản không dựng được body xếp cuối | — | unit ✓; thêm kiểm lại **lúc gửi** (P-1: kill switch, `logevent_control`, `DENIED` giữa phiên → 0 POST thêm), `Retry-After` (trần 600 s), tập bền trần 2 000. Yêu cầu đã giao OkHttp lúc `DENIED` (≤ 4 đang bay) không thu hồi (quyết định P-1). |
| Chênh doanh thu ngày giữa Firebase và AppsFlyer cho cùng app | — | ≤ 1% | đối soát bảng doc 62 mục 2.2 | — | — | — | số lượng: chênh 0 trên 12/12 phiên test ads (H6a); giá trị ± 1 %/ngày: chưa đo (test ads trả giá 0; H6b, program gate mở). |
| Lý do không hiện được log 100% (`ad_show_fail` có `reason`) | bắt buộc | — | mọi `return false` trong `show*` có `logAdShowFailWithReason` | gần đủ; Phase B thêm rewarded bị chặn (Task 16) và `app_open_expired` (Task 11); thiếu ở `isIntersAllowShowAds` | gần đủ; thiếu `isIntersAllowShowAds`. | Cổng "cf not"/"đang load" của bốn hàm load (`loadRewardedForThisTurn`, `loadNativeFullForThisTurn`, `loadNtClForThisTurn`, `loadRect4Show`) hết im lặng: báo `onLoadFailed(…, "cf not")` ngay trên luồng gọi, hoặc gắn callback vào lượt đang chạy (1.6.0 Task 5, `f5cd3f2`); interstitial/app open vẫn báo đồng bộ như trước, chưa dùng `CfNotDispatcher` chung (N6, đề xuất 1.6.1). O7 trên máy (release, Load Rect ×2 cách 296 ms): `[rect load] loaded`=2, `load timeout`=0, `attaching callback`=1 → PASS (`t9-metrics.md` #16, `t160-t9-emu37-o7.log`). | `ad_show_fail.reason` rỗng = **0** trong 392 dòng, `ad_show_attempt` ở 7 định dạng, orphans 0 (`t170-r-be-views.log`, #14); 8 đường `return false` im lặng và cổng tần suất interstitial đã phủ (T2). |

Fill-rate: request banner/rect/toàn màn hình/native bị watchdog slot 60 s bỏ có sự kiện bắt đầu (`onAdLoadStart`:
`aad_lst_*`, `logAdStartLoad`) mà không có sự kiện kết thúc (`aad_led_*`/`aad_lfa_*`, `logAdLoad`: response muộn không
được đếm từ Task I/J, và `load timeout` không ghi event analytics nào), nên truy vấn fill-rate phải xử lý tường minh
các lần bắt đầu không có kết thúc (loại khỏi mẫu số, hoặc tính là timeout) (Task K mục 4, re-review Task J Minor 4).

### G7. Không hỏng câm

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) |
|---|---|---|---|---|---|
| Kill switch `sdk_kill_android` cho ads / iap / logevent, và theo phiên bản | bắt buộc | — | `SdkKillSwitchTest`; device: bật khoá → 0 request quảng cáo | unit ✓ (Task 16); device chưa (cần console) | unit ✓; máy (bản debug, khoá tiêm qua prefs `mem_sdk_kill_android` = `{"ads":true}`, không qua console): `kill switch: … → tắt ads`, `sdk_health … networks=killed, kill=ads`, 0 `tryLoad`, 5 lượt show bị chặn `show_blocked_kill_switch`, Remote Config gỡ khoá giữa phiên → `ads stay disabled until next open`. Console chưa |
| Crashlytics custom key `appplugin_version` | bắt buộc | — | Crashlytics console sau 1 crash thử | unit ✓ (Task 16); console chưa | unit ✓; console chưa |
| Event `sdk_health` mỗi phiên: version, tier, mạng đã khởi tạo, ms init | bắt buộc | — | DebugView | unit ✓ (Task 16); DebugView chưa | máy ✓ `sdk_health {sdk_version=1.4.0, consent_tier=…, networks=…, since_request_ms=…, kill=…}` (release và debug); DebugView chưa |
| Mọi API công khai gọi sai thứ tự hoặc thiếu dependency không ném | bắt buộc | — | `PublicApiNoThrowTest` (Phase C) | phần lớn try/catch ✓ | phần lớn try/catch ✓ |
| Remote Config chưa về → giá trị mặc định an toàn (Điều 7.3) | bắt buộc | — | `AdsConfigObTest`, `AdsPolicyTest` | ✓ | ✓ |

### G8. Kiến trúc và API

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) | 2026-09-28 (1.5.0) | 2026-09-28 (1.6.0) |
|---|---|---|---|---|---|---|---|
| ABI dump `api/appplugin.api` khoá trong CI; `apiCheck` fail khi ABI đổi ngoài ý muốn | bắt buộc (1.4) | — | `tools/api_dump.sh` + job CI; thử phá một hàm public phải đỏ | ✗ | ✓ `tools/api_dump.sh --check` trong job `verify` (4.155 dòng tại `12b7b0a`, gồm `protected`); đối chứng: `RemoveAdsMask.sanitize` sang internal → exit 1. Dump gồm cả thành viên `protected` (`javap -protected`, review E M5: lớp con ngoài SDK của `AdsBase`/`AdsPlBase` thấy chúng, vd `eventTag`, `loadedAdMaxAgeMs`). Giới hạn: javap chỉ thấy chữ ký JVM, không thấy metadata Kotlin — đổi lớp public → internal, nullability (vd `peekAll(): List<String>?` của 1.4.0), `suspend`, tham số mặc định không làm đổi dump (cần BCV/đọc metadata, Phase C) | ✓ `tools/api_dump.sh --check` → `API unchanged (4387 lines)` (4.209 dòng tại `6d8f454`; +178 là khối lớp mới `com.app.plugin.ads.core.*`, javap thấy `internal` là public); `tools/api_facade_check.sh` PASS: 0 dòng public/protected đổi so với `6d8f454`, chỉ thêm lớp trong `ads.core`; `tools/core_internal_check.sh` PASS (mọi khai báo top-level của `ads/core/*.kt` là `internal`/`private`, không `h.` cấp field, không `init {}`/`by lazy`) | `tools/api_dump.sh --check` → `API unchanged (4484 lines)` (`wc -l api/appplugin.api` = 4484 sau Task 10, thêm 1 thành viên `internal` mangle `releaseNativeAdView$appplugin()` trên lớp public có sẵn `AdsMobNtFullView` — không phải bề mặt API mới); `tools/api_facade_check.sh`: `core internal check: PASS`, `facade check vs a76f77e: PASS`, 9 khối `internal` mới cho phép (`CfNotDispatcher`, `FanOutLoadCb`, `LoadCallbacks`, `OneShotLoadCb`, `GppConsent`, `GppSignals`, `TelemetryConsentGate` + 2 lồng `$Decision`/`$WhenMappings`); D7 mọi commit `a76f77e..f183dcb` (32 commit): dump PASS=32 FAIL=0, facade PASS=32 FAIL=0 (`t9-metrics.md` #4, #4-đuôi; `t160-t9-d7.log`). |
| Lớp chỉ dùng nội bộ đánh dấu `internal` | ≥ 40% type (1.4) | ≥ 70% (2.0) | `grep -c '^internal' \| ^class` | ~5 | 99/197 kiểu top-level Kotlin = 50,3% ✓ (Task G: 66 lớp adapter/nội bộ → `internal` theo research-g8.md Phụ lục A; `tools/api_dump.sh --check` không đổi dump, 0 tham chiếu trong 2 app + demo); 20 lớp "chủ dự án xác nhận" + 3 lớp chỉ demo còn public, chờ chủ dự án chốt (lên tới 60,0–61,5% nếu đổi) | 112/210 kiểu top-level Kotlin = 53,3% ✓ mức 1.4, ✗ mức 2.0 (≥ 70%): +12 controller `internal` của `ads.core`; 105 file có khai báo top-level `internal` (`grep -rlE '^internal (class\|object\|…)'`). Đếm bằng regex của research-g8 §5 trên `src/main/java/**/*.kt` (186 file); 20 lớp "chủ dự án xác nhận" + 3 lớp chỉ demo vẫn public, chờ chốt | 109/190 file có khai báo top-level `internal` = 57,4% ✓ mức 1.4 (≥ 40%), tăng từ 53,3% (1.5.0); mức 2.0 (≥ 70%) chưa đạt (`t9-metrics.md` #6). |
| File ≤ 800 dòng | `AdsHelper` ≤ 1.500 (dời từ mức 1.4 sang **mức 1.5**: DOC/66 mục 4 T3, chủ dự án chấp nhận 2026-09-25) | mọi file ≤ 800 (2.0) | `wc -l` | 3.669 | `AdsHelper` 3.865 (ngưỡng nay ở 1.5, không còn chặn 1.4); `LogEventServer` 942 ✗ (ngưỡng 2.0). Kế hoạch tách 12 file: `.superpowers/sdd/research-g8.md` mục 2, ước 22–25 giờ agent | `AdsHelper` **692** ✓ mức 1.5 và ✓ mức 2.0 (4.018 tại `6d8f454`; façade + 12 controller `internal` trong `ads/core/`, 13 file; trần `AdsHelperSizeGateTest` 700 (số đo + 8)); mọi file `ads/core/*.kt` ≤ 512 (`InterstitialController` 512, `Full2Chain` 484, `NativeController` 454, `BannerController` 435, `AppOpenController` 422, `RectController` 332, `RewardedController` 317, `NativeCollapseController` 245, `NativeFullController` 220, `AdsTelemetry` 194, `AdsNetworkRegistry` 170, `AdsFrequencyGate` 166; tổng 3.951); `LogEventServer` **955** ✗ ngưỡng 2.0 (đếm lại 2026-09-28; số 942 ở cột trước là lần đếm cũ, file không đổi từ `6d8f454`; 1.5.0 không chạm) — là file duy nhất của SDK > 800 | `LogEventServer.kt` **961** dòng (trần riêng ≤ 970, R-13/§7 #17) ✓; `AdsHelper.kt` **696** ✓ (trần `AdsHelperSizeGateTest` 700); file mới ≤ 400: `TelemetryConsentGate.kt` 226 (trần 260), `LoadCallbacks.kt` 105, `GppConsent.kt` 229, `GppSignals.kt` 26 — ✓ toàn bộ; không file SDK nào khác > 800 (top-5: LogEventServer 961, AdsMobPlNtFull 785, AdsHelper 696, AdsBrain 663, AdsFbPlNtFull 662) (`t9-metrics.md` #5). |
| App tiện ích không gánh adapter game | 2.0 | — | profile `core + ads-gma + ads-meta`; APK delta ≤ 8 MB [D] | 1 AAR | 1 AAR | 1 AAR | — |
| `init(application, config)` không bắt kế thừa `Application` | 2.0 | — | demo Java thuần chạy | bắt kế thừa | bắt kế thừa | bắt kế thừa | — |

### G9. Kiểm thử và thiết bị

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-24 | 2026-09-25 (1.4.0) | 2026-09-28 (1.5.0) | 2026-09-28 (1.6.0) |
|---|---|---|---|---|---|---|---|
| Unit test xanh, 0 test bị tắt, 0 flaky trong 3 lượt | 100% | — | `./gradlew :testDebugUnitTest` ×3 | 597/597 ✓ | 931/931 ×3 ✓ (0 skip) | 1360/1360 ×3 ✓ (0 fail, 0 skip; `t150-t16-full-{1,2,3}.log`) | 1498/1498 ×3 ✓ (0 fail, 0 skip; `t160-t9-full-{1,2,3}.log`, `t9-metrics.md` #1) → 1505 sau Task 10 (`t160-t10-full.log`) → 1509 sau review fix (`t160-t10fix-full.log`), 0 fail 0 skip; `@Ignore` thật = 0 (2 chuỗi KDoc "không @Ignore" trong `AdsHelperSizeGateTest.kt`/`SdkFileSizeGateTest.kt` là comment, `grep` đếm nhầm). |
| Ma trận: API 26, 30, 33, 36 emulator + 2 máy thật | mỗi release | — | `tools/device_gate.sh` cho từng serial | chỉ API 37 emu | emulator API 26, 28, 37 ✓; máy thật: cổng tự động PASS tại `b4a268b` (Galaxy A25, `wtB2-*`), chưa chạy lại ở 1.4.0 (khoá màn hình); thiếu API 30/33 và máy thứ hai | emulator API 37, 28, 26 ✓ (`EVENTS=1500 tools/device_gate.sh`, APK release `app-release.apk` sha256 `8239c35e697eb1e9…`: StrictMode 0, chuỗi R8 0, FATAL/`am_anr`/native 0; sau consent, cold start + bấm từng nút: banner, NtCl, rect, native, interstitial, native full, app open, rewarded đều `tryLoad ok` trên cả ba API); **máy thật:** Redmi 22021211RC API 34 PASS trên APK `ee4991a` (`device-gate-65c9b673-20260928-083034.log`: cold start 956 ms, R8 0, StrictMode do SDK 0, FATAL/`am_anr`/native 0 ×4; interstitial, rect, banner, native, native full nạp được — `t150-realdevice-redmi-65c9b673.log`); Galaxy A25 API 36 PASS trên cùng APK (`device-gate-R5CX10P9EKZ-20260928-084431.log`: cold start 766 ms, R8 0, StrictMode do SDK 0, FATAL/`am_anr`/native 0 ×4; rect, banner, native, native full, rewarded, Full2 nạp được — `t150-realdevice-a25-R5CX10P9EKZ.log`); kịch bản tay chỉ trên emulator (R-12); thiếu API 30/33 | `EVENTS=1500 tools/device_gate.sh` PASS trên emulator API 37 (TotalTime 1671 ms, StrictMode `com.app.plugin`=0/violations=0, FATAL/`am_anr`/native=0 ×4 giai đoạn, `t160-t9-emu37-device-gate.log`), API 28 và API 26 (`device-gate-emulator-5580-*.log`: R8=0, StrictMode by sdk=0, FATAL/`am_anr`/native=0); sau consent, 8 định dạng `tryLoad ok` trên cả ba API (API 37 tổng 12 lượt `t160-t9-emu37-formats.log`; API 28 tổng 26 lượt `t160-t9-emu28-formats.log`; API 26 tổng 19 lượt `t160-t9-emu26-formats.log`); cold start SDK main-thread trung vị 31 ms, TotalTime trung vị 1003 ms (`t160-t9-emu37-coldstart.log`); `release_gate` PASS (APK sha256 `f7f182adbd4bb6867e53945cff8689e9020a8e1d6b6e2d0d1dc93a9608e505e9`: 9 ad unit, 98% lớp đổi tên, zipalign 16 KB, `t160-t9-release-gate.log`), PASS lại tại `ffe0505` sau Task 10 (`t160-t10-release-gate.log`, cùng số đo); sau Task 10, `EVENTS=600 tools/device_gate.sh` PASS lại trên emulator API 37 tại `ffe0505` (`t160-t10-device-gate.log`: TotalTime 1181 ms, StrictMode 0, FATAL/`am_anr`/native 0). **Máy thật:** Redmi 65c9b673 API 34 PASS (`t160-t9-real-65c9b673*.log`: R8=0, by sdk=0, FATAL/native/`am_anr`=0 ×4 giai đoạn; xoay `accelerometer_rotation`/`user_rotation` khôi phục đúng về giá trị trước; gói `test.sdk.zd` gỡ sạch); Galaxy A25 (R5CX10P9EKZ) API 36 PASS trên APK `ffe0505` (`t160-t9-real-R5CX10P9EKZ-gate.log`: TotalTime 1129 ms, StrictMode by sdk=0, FATAL/native/`am_anr`=0 ×4 giai đoạn, xoay không đổi so với trước — không cần khôi phục, gói gỡ sạch, sha256 APK trước/sau khớp; `t9-metrics.md` #17). API 30/33 vẫn thiếu (kế thừa 1.5.0). |
| Cỡ chữ 200% trên native full và Full2 không cắt chữ | 0 lỗi | — | `settings put system font_scale 2.0` + ảnh | chưa | chưa | chưa | — |
| Xoay 4 lần khi có form, khi có Full2: không leak Activity | 1 Activity sau GC | — | `dumpsys meminfo` | ✓ (form) | form ✓; Full2 (bản debug, luồng tham chiếu): sau GC 1 hiện tại + 1 cũ giữ mạnh (WebView GMA), 5 chỉ còn ref yếu ✗ (như 1.3.1). Task I5: `plcf.cbShow` của mọi placement không đang hiện được nhả trong `clearActivity` (unit `Full2HostDestroyTest`; trên Galaxy A25 và emulator log `clearActivity: released 2 idle show callbacks` mỗi lần xoay). **Đo lại 2026-09-26 (emulator API 37, bản debug `e692c2a`, `am dumpheap -g`):** 1 → 4 Activity sau 2 vòng quảng cáo có xoay → 5 sau 5 lần xoay → 6 sau thêm 1 vòng ✗. Phân tích heap (shark): cả 3 Activity cũ do **GMA** giữ (WebView `zzcmp` qua `WindowManagerImpl.mContext`, registry OMID, `InterstitialAd`/`AppOpenAd` nạp bằng Activity); không đường nào qua `com.app.plugin`; bỏ mọi cạnh của SDK vẫn giữ. Sửa gốc = nạp GMA bằng `applicationContext` → quyết định #18 | Full2 (bản debug, emulator API 37, `am dumpheap -g` ×2, cờ `gma_load_app_context` tắt): luồng Task 12 (2 lượt Interstitial → Full2, đóng + xoay cùng lệnh, xoay khi Full2 hiện, xoay thêm 5 lần) **3 Activity** sau GC (chuẩn 2026-09-26: 4; Task 12: 3) — không tệ hơn, vẫn ✗ so với 1; mỗi request đúng một dismiss; `FATAL`/`WindowLeaked`/`BadToken` 0. Thêm bước rect trước luồng (Load → Show Rect → xoay 2 lần): 3 Activity ngay sau bước rect, 5 sau cả luồng — phân tích heap sau đó (1.6.0 Task 3: cùng quy trình, cùng số 1 → 3 → 5, shark, `t160-t3-g9-heap-{a2,b2}.log`): **cả 4 Activity cũ do đường #18** (GMA toàn màn hình nạp bằng Activity: AppOpen `full_splash` lúc khởi động giữ Activity đầu; interstitial tầng sau của `full_splash` mà demo nạp trong `onCreate` sau lần xoay đầu của bước rect; các interstitial `full_default` của luồng Full2, nạp trên hai host sau bước rect vì hai lần xoay đã rời Activity đầu); SDK giữ **thêm** Activity đầu qua `AdsPlacementConfig("rect_default").cbLoad → MainActivity$DemoLoadCb.this$0` (`clearActivity` chỉ nhả `cbShow` rảnh), không là người giữ duy nhất (cắt `cbLoad`: vẫn còn; cắt GMA + `cbLoad`: hết); AdView rect (context ứng dụng `AdView(mContext)`, đã gỡ khỏi container) không giữ Activity nào; hai app đang dùng không gọi API rect (`t150-t16-emu-g9.log`) | Bước rect (bản debug, sink chạy, quy trình 1.5.0: Load/Show Rect → xoay 2 lần → luồng Full2), đo ở Task 9 (`t160-t9-emu37-g9.log`, xoay `adb emu rotate`): Activities sau rect 3 (≤ 3), sau cả luồng **6** (> 5 **FAIL**); một đường class A (qua `com.app.plugin`) qua Full2 native full dựng sẵn giữ Activity: `AdsMobPlNtFull.adsEcmpShow → AdsEcmp.adObject → NativeAd → NativeAdView.mContext` (có từ 1.5.0, `cfb9726`). Vá ở 1.6.0 Task 10 (`discardPreparedDialog` gọi `NativeAdView.destroy()`); đo lại (`t160-t10-emu37-g9.log`, xoay `settings put system user_rotation`): Activities sau rect vẫn **3** (≤ 3 PASS), sau cả luồng **5** (≤ 5 PASS, từ 6); đường class A = **0/0/0** ở ba lần dump a2/b2/c2 (từ 1 ở Task 9); lượt Full2 kế tiếp hiện lại quảng cáo đã giữ mà không nạp lại (`tryLoad has loaded`), impression 1, dismissed 1; FATAL/WindowLeaked/BadToken/`am_anr` = 0. **Còn lại toàn bộ là class C** (GMA nạp quảng cáo bằng Activity, #18) — sửa gốc là nạp GMA bằng `applicationContext` (cờ `gma_load_app_context`, DOC/65 §7 #18), chưa bật mặc định, chưa đo trên máy với cờ bật. |
| Mua hàng trên Internal testing với License tester | mỗi release có đổi iap | — | checklist B4 | chưa | chưa | chưa (1.5.0 không đổi iap) | — |

### G10. Trải nghiệm tích hợp

| Tiêu chí | Ngưỡng cứng | Mục tiêu | Cách đo | 2026-09-25 (1.4.0) |
|---|---|---|---|---|
| README gốc + quickstart ≤ 10 phút, khớp demo | 1.4 | — | agent mới đọc README dựng được demo có quảng cáo | không có README gốc ✗ |
| CHANGELOG có mục "Phải làm khi nâng cấp" cho mọi thay đổi hành vi | mỗi release | — | review | ✓ `[1.4.0]` có Phải làm / Đổi / Thêm / Xoá / Sửa; "Phải làm" gồm 4 lớp thành `internal` (Kotlin lỗi biên dịch, nhị phân không đổi) và đổi nhãn `err` remove-ads |
| Báo cáo tự kiểm tra cảnh báo mọi cấu hình câm | — | — | `MonetizationSdk.Report` | ✓ |

### G11. Đường ống đo lường kết thúc-kết thúc (cổng cứng từ 1.7.0)

`tools/device_gate.sh --variant utility --measure --apk <frozen> --mapping <frozen>` (kèm `tools/measure_check.py`, fail-closed). Sink cục bộ `127.0.0.1:8089`, pha A (phiên hợp lệ có quảng cáo), pha B (sink không tới được, kill), pha C (mở lại, replay).

| Tiêu chí | Ngưỡng cứng | Cách đo | 2026-10-02 (1.7.0) |
|---|---|---|---|
| Pha A hợp lệ | ≥ 1 impression (FA `origin=app,name=ad_impression` và `ad_revenue` của session), focus đúng; lượt `invalid` không tính, chạy lại ≤ 2 | `measure_gate.sh` | 4/4 máy hợp lệ **lượt đầu** trên công cụ đã sửa (FA `ad_impression` 2, JSONL `ad_revenue` 2) |
| (i) event đã chấp nhận có trong JSONL | `accepted` = số `event_id` distinct của phiên pha A | dòng `logevent stats … accepted=n` | API 37 / 28 / 26 / Redmi: 45 = 45 ở cả bốn |
| (ii) event bền không mất | mọi `logevent durable stored` của pha B có trong JSONL sau pha C: **mất = 0** | log app + JSONL | 2 event bền, mất 0 ở cả bốn |
| (iii) không trùng | sau dedup mỗi `dedup_key` đúng 1: **trùng = 0** | `measure_check.py` | trùng 0 ở cả bốn; MERGE thật (#11): raw 59 = distinct 59, lần 2 +0 |
| (iv) ts replay | `client_ts_ms` hàng replay = ts lúc phát (pha B) | `measure_check.py` | PASS ở cả bốn |
| (v) chuỗi | mỗi `request_id` ≤ 1 kết thúc | `measure_check.py` | 6 `request_id`, hơn một kết thúc: 0 ở cả bốn |
| (vi) best-effort mất ở pha B | ghi (thông tin) | `measure_check.py` | dựng 9, bền 2, mất 7 ở cả bốn |
| FATAL / ANR | 0 | logcat | 0 |
| Máy | 3 emulator + máy thật | — | API 37, 28, 26 và Redmi 65c9b673 PASS; Samsung R5CX10P9EKZ chưa đo (máy không cắm) |

Log: `t170-r-{emu37,emu28,emu26,65c9b673}-g11.log`, `t170-r-be-merge.log`. Lượt đầu bị bỏ vì lỗi công cụ (Redmi trước TF4: `invalid (ui)` 3/3; API 26 trước TF5: `am start -W` treo) được giữ nguyên (`*-pre-tf4.log`, `*-pre-tf5.log`), không tính vào ngân sách chạy lại.

### G12. Billing qua harness (cổng cứng từ 1.8.0)

`tools/device_gate.sh --variant utility --iap --sink-port <p> --sink-dir <d> --apk <frozen> --mapping <frozen>` (kèm `tools/iap_check.py`, fail-closed; Task 16). Harness (`IapHarness`, `ScriptedBillingPort`, `IapScripts`)
thay cổng store bằng bản kịch bản nhưng chạy **qua state machine thật** của `IapHelper` (quyền lợi, ack, sổ token, `iap_result`, vòng đời), nên kiểm được luật Play mà không cần mua thật.
Arm khi và chỉ khi đủ 3 khoá: `flagTest` bật, meta-data `com.app.plugin.IAP_HARNESS=true` (chỉ `app/src/utility/AndroidManifest.xml`) và extra `--es appplugin_iap_script <tên>`; app release (`flagTest` 0) không nạp `ScriptedBillingPort`/`IapScripts`.
12 script: `buy_sub`, `buy_cheap`, `cancel`, `pending_then_paid`, `kill_mid_purchase` (→ `restore_unacked`), `restore_owned`, `reopen`, `restore_store_error`, `revoke`, `grace`, `denied`, `messages_off`.

| Tiêu chí | Ngưỡng cứng | Cách đo | 1.8.0 | 1.9.0 |
|---|---|---|---|---|
| 12/12 script PASS mỗi máy | khẳng định từng hàng hợp đồng B của plan 1.8.0; lượt `invalid` liệt kê, chạy lại ≤ 2 | `device_gate.sh --iap` + `iap_check.py` | API 37 **12/12** PASS (FAIL/`invalid` không); API 28 và 26 lượt đầu 11/12 do lỗi công cụ (script 11 `denied`, node Consent bounds `[0,0]`, sửa ở TF-1), đo lại sau TF-1 INVALID 11/12 (UMP WebView 28/26 báo node bounds `[0,0]`), sửa tiếp ở TF-2; với công cụ TF-2 (`410c855`, code `12e1aa1`) API 28 **12/12** và API 26 **12/12** PASS; Redmi 65c9b673 API 34 **12/12** PASS lượt đầu (by-sdk 0 mọi script, `t180-r-redmi-g12.log`); Samsung: chưa đo (`t180-r-emu{37,28,26}-g12.log`, `t180-r-emu28-g12-tf2.log`, `t180-r-emu26-g12-tf2-rerun.log`) | API 37 **12/12** PASS (FAIL/`invalid` không; lượt 1 `invalid (contention)` ở gate, không ở G12); API 33, 28, 26 **12/12** PASS; Redmi 65c9b673 API 34 **12/12** PASS lượt đầu (by-sdk 0 mọi script); phần rwi `remove_ad` của `buy_cheap` **chưa đo (không có driver)**: script chỉ chạm Interstitial và Rewarded (D53) (`t190-r-emu{37,33,28,26}-g12.log`, `t190-r-redmi-g12.log`) |
| Quyền lợi theo luật Play | sau `grant`: `ad_show_fail reason=remove_ad` = 100 %, `ad_show` 0 (mask ALL); PENDING và `isSuspended()` 0 grant; `cancel` 0 grant | log + JSONL | API 37: `buy_sub`, `buy_cheap`, `cancel` (#3), `pending_then_paid` (#4) PASS do `iap_check.py` khẳng định từng script (mask ALL: `remove_ad` 100 %, `ad_show` 0; PENDING và `cancel` 0 grant); API 28/26: chưa đo (đang chạy lại G12 28/26 sau TF-1) | cùng khẳng định `iap_check.py` nằm trong 12/12 mỗi máy; `buy_cheap`: interstitial `ad_show_fail reason=show_full_when_remove_ads` 1; rwi `remove_ad` chưa đo (rwi `remove_ad` = 0 hàng trong dữ liệu E-BE; logic ghim bằng unit T2) |
| `iap_purchase` đúng một lần theo token | 1 theo `purchase_token`, kể cả sau kill giữa chừng và MERGE | JSONL, `iap_check.py` | API 37 PASS (kể cả `kill_mid_purchase` → `restore_unacked`); MERGE trên JSONL G12 thật: 329 hàng, raw 327 = distinct, lần 2 +0, `iap_purchase` raw 6 = 6 token (`t180-r-be-merge-dev.log`) | MERGE trên JSONL G12 thật (12 file): staging +346 → raw 542 = distinct, lần 2 +0; `iap_purchase` raw 6 = 6 token (`t190-r-be-merge.log`) |
| Consent R-P1 cho `iap_*`/`paywall_*` | từ DENIED tới GRANTED: 0 POST; sau GRANTED cùng process: flush | script `denied` | API 37: DENIED 0 hàng, 0 POST, 0 `consent_*`/`iap_*`/`paywall_*`; sau GRANTED flush (`consent_form_shown` 1, `consent_choice` 1, `flushed 9 held`); script `denied` PASS (`t180-r-emu37-consent.log`); 28/26: chưa đo (đang chạy lại G12 28/26 sau TF-1) | API 37 (chặng N1, #13): DENIED 0 hàng, 0 POST, kể cả hàng rwi (`ad_show_attempt`/`ad_revenue`/`ad_show`/`ad_request`/`ad_fill` `hold-denied`), brain và partner 0; sau GRANTED `flushed 16 held after DENIED`, +17 hàng (`consent_form_shown`, `consent_choice` GRANTED, rwi `ad_show`) (`t190-r-emu37-consent.log`); 33/28/26: không đo ở 1.9.0 |
| Tín hiệu server | `/rtdn` fixture `IN_GRACE_PERIOD` → `/push` kế trả `signals` → `subscription_state IN_GRACE_PERIOD source=server` 1; quyền lợi không đổi | script `grace`, `messages_off` | API 37: `grace` và `messages_off` PASS, by-sdk 0; backend `/rtdn` 11/11, `/push` trả đúng 1 signal `sub_state pro_sub EXPIRED`, 401 khi sai bearer (`t180-r-be-rtdn.log`); 28/26: chưa đo (đang chạy lại G12 28/26 sau TF-1) | backend `/rtdn` không đổi ở 1.9.0: chưa đo lại (giữ kết luận cột 1.8.0); script `grace`, `messages_off` nằm trong 12/12 |
| `showInAppMessages` tự gọi (T11) | foreground 1: 1 lần; foreground 2 (< 24 h): 0; `subscription_lifecycle_android.in_app_messages.enabled=false`: 0 | dòng `iap harness: in-app-messages call` | API 37: `grace` và `messages_off` PASS; 28/26: chưa đo (đang chạy lại G12 28/26 sau TF-1) | nằm trong 12/12 (`grace`, `messages_off`); không có số riêng ở 1.9.0 |
| Billing không chạm main | dòng `IapHelper unavailable: 3` = 1 mỗi cold start (API 26/28), `setup failed` trên main = 0 | log app | API 28 và 26: `Billing service unavailable` 1 và `IapHelper unavailable` 1 mỗi cold start, ngoài main; `setup failed` trên main 0 (1.7.0: ×7) (`t180-r-emu{28,26}-gate.cold{1,2}.app.log`) | chưa đo lại riêng ở 1.9.0 (giữ kết luận cột 1.8.0); gate 33/28/26 PASS, FATAL/ANR/native 0 |
| FATAL / ANR / StrictMode by-sdk | 0 | logcat | FATAL/ANR/native 0 ở gate 37/28/26; by-sdk 0 ở G12 37 (sau F-P1; trước đó 9); G12 28/26: chưa đo (đang chạy lại G12 28/26 sau TF-1) | FATAL/ANR/native 0 ở mọi gate; by-sdk 0 ở mọi script G12 trên API 37 và Redmi |
| Chặng Play thật (37 + Redmi) | `IapHelper connected`, restore OK owned=0 (thông tin: gói demo không có trên Play) | cold start không script | API 37: `IapHelper connected` 1, không dòng lỗi query/restore, by-sdk 0, FATAL/ANR 0 (khoảng trống thông tin: mã không in dòng thành công, 1.9.0); Redmi API 34: `IapHelper connected` 1, FATAL/ANR 0, không dòng lỗi query/restore (cùng khoảng trống) (`t180-r-emu37-realplay.log`, `t180-r-redmi-realplay.log`) | không đo ở 1.9.0 (giữ kết luận cột 1.8.0); T11 thêm dòng debug thành công `IapHelper products query code=<mã> n=<n>` và `IapHelper restore OK owned=<n>` để lần đo Play thật sau không còn khoảng trống thông tin |

Log: `t180-r-emu{37,28,26}-g12.log` (+ JSONL từng script), `t180-r-emu{37,28,26}-gate.log`, `t180-r-emu37-{consent,realplay}.log`, `t180-r-be-{rtdn,merge-dev}.log`. Lượt 11/12 của 28/26 được giữ nguyên (lỗi công cụ TF-1), không tính là PASS.

Ranh giới của cổng: harness chứng minh **logic của SDK**; nó không chứng minh màn mua thật của Play, grace/hold thật, RTDN thật, hay NPE `ProxyBillingActivity` trên máy (chỉ có unit K1–K4) — những thứ đó là H7b (DOC/67 §4, OWNER-NOTES #4).
Cách chạy một script tay: README mục 8.

### G13. Backend (cổng cứng từ 1.7.0)

| Tiêu chí | Ngưỡng cứng | Cách đo | 2026-10-02 (1.7.0) | 1.9.0 |
|---|---|---|---|---|
| Test backend | `pytest -q` đủ (kể cả `integration`): 0 failed | E-BE | 275 passed + 1 skipped (`444372e`); sau F2-be2 unit 267 + 1 skip, integration 13 (`t170-f2be2-*.log`); đo lại trên `9fbadcf` (mã cuối `336525a`): **280 passed + 1 skipped** (= 268 không-integration + 13 integration, dẫn xuất) (`t170-r-be-g13.log`) | `pytest -q`: **334 passed / 1 skipped / 0 failed** (315 unit + 19 integration, 1.8.0: 312 + 1 skip) (`t190-r-be-g13.log`) |
| Mã HTTP | `/health` 200 và 503 khi BigQuery dừng; 401 ×2; 413; 429 | `curl` | đúng cả sáu; `/health` 503 lần 1 trên `444372e` chậm 575 s (`DEFAULT_RETRY`) → sửa F2-be2, integration ghim < 15 s; đo lại trên `9fbadcf`: **0,083 s** (`t170-r-be-g13.log`) | không chạm ở 1.9.0: chưa đo lại (giữ kết luận 1.7.0) |
| **p95** | `loadtest.py --rps 50 --seconds 60` **< 200 ms**, lỗi 0 | sink JSONL native (quyết định CEO R-10: lớp emulator BigQuery tuần tự hoá insert, chỉ là thông tin) | 3000 request, lỗi 0, p50 2,1 / **p95 6,7** / p99 10,6 / max 105,6 ms; emulator BigQuery (thông tin): 1762 lỗi lần đầu, container mới p95 1608 ms, 0 lỗi; BigQuery thật đo ở H5 | không chạm ở 1.9.0: chưa đo lại (giữ kết luận 1.7.0) |
| Guard | `gcp_guard_test.sh` PASS; từ chối production, 0 lệnh `gcloud`/`bq` trực tiếp | shell | 111/111; D7 = 0 | `gcp_guard` 128/128 |
| Parity SQL | view = `ad_metrics.py`, Δ = 0 (FLOAT64 ≤ 1e-9) | `pytest -m integration -k parity` | 8 passed, Δ = 0 | **11/11 view Δ 0** (13 test integration; thêm `v_brain_decisions`, `v_ecpm_network_format`) (`t190-r-be-parity.log`) |
| Runbook | mọi bước trên stack cục bộ; `deploy` bị từ chối thiếu `GCP_GUARD_OWNER_GO` | E-BE | PASS (shim `PATH`, 0 lệnh `gcloud` thật) | không chạm ở 1.9.0: chưa đo lại |
| Báo cáo ngày | 3 ngày UTC+7 liên tiếp, đủ 7 bảng, fill/show/imp-DAU > 0 | `tools/report_daily.sh` | ba ngày xong (2026-09-30, 2026-10-01, 2026-10-02 liên tiếp; mỗi ngày 7 bảng, fill/show/imp-DAU > 0) (`t170-r-be-reports.log`) | `report_daily.sh --date 2026-10-01` **11 bảng**; `v_ecpm_network_format` 4 hàng có cột `ad_network` (`admob`), hàng `ad_format=rewarded_interstitial` 1 impression (doanh thu 0 = quảng cáo thử) (`t190-r-be-report.log`) |

Log: `t170-r-be-{g13,merge,views,parity,reports-day1}.log`, `t170-r-runbook.log`.

### Số đo 1.9.0 (Task R, mã `99b7d5d`, công cụ `bcfe3c6`)

APK utility sha256 `8ad41174047b9e90658dcc71ac3a90a20aa5ffc8e62354bb1c2129bc6bf407b6` (9 046 588 B, đóng băng), game `9bee22c9dd61ded6a2c99bffbb773921ef229cd4ca9fe8f21b374f631195d291` (27 483 403 B); số byte và sha lấy từ dòng `apk sha256=… bytes=…` của log release gate. Nguồn: `metrics-190.md`, log `DOC/testing/t190-r-*`.

| Cổng | Chỉ số | 1.9.0 |
|---|---|---|
| G9 | Unit ×3 | 1831 / 1831 / 1831 (1747 + 84), 0 fail / error / skipped, `@Ignore` 0 (`t190-r-full-{1,2,3}.log`) |
| G8 | API dump | `API unchanged (5494 lines)`; 0 dòng `-` ngoài `access$`; 178 `+` (5316 + 178); façade PASS; D7 17/18 commit = 0, `4a551f1` = 1 (`PurchaseFlow.take()`, lớp internal mới trong cùng dải, cả dải = 0) |
| G8 | Cỡ file | 0 > 800; `AdsHelper` 696, `IapHelper` 453 ≤ 455, `DecisionLogger` 570 ≤ 580, `AdsMobPlNtFull` 791 |
| G1/G2 | Shell | `device_gate_allowlist` 222/222, `gcp_guard` 128/128, `lane_pack` 10/10; FAIL 0 (sau F3-t1; plan ước 215, đếm thật 222) |
| G3 | Release gate | `utility` PASS: `ad units in resources.arsc=11`, `absent-class sdk=0 allow=52`, mapping 399/403 = 99 %, `IAP_HARNESS=1`, `BRAIN_HARNESS=1`; `game` PASS: harness 0/0, `absent-class game info=862 allow=30` |
| Size | `bundle_size.sh --variant utility` | min 7 642 770 / max 7 702 160 B ≤ 26 214 400; +16 325 B so với 1.8.0 (thông tin) |
| G1–G3 | Gate `utility` | PASS cả 5 máy: API 37, 33 (mới), 28, 26 và Redmi 65c9b673 (API 34): FATAL/ANR/native 0, R8 0, by-sdk 0 ×2, JSONL tăng, `precheck docker=0 sink=up`. `initPlugin total` cold1/cold2: 37 = 11/26 ms (1.8.0 88/47), 33 = 41/48, 28 = 155/46 ms (đo lại D54; lượt đầu 274/77) (1.8.0 108/120), 26 = 158/61 ms (đo lại D54; lượt đầu 56/268; host load1 5–15 ngoài lane, PASS trong dải ± nhiễu, D56) (1.8.0 187/113), Redmi = 28/11 (1.8.0 53/16) |
| G1–G3/G5 | rwi chặng 1–5 | chặng 1 (hiện, thưởng, đóng) PASS ở API 37 (intro 1, `ad_show` rwi 1, reward 1, dismiss 1) và Redmi; 33/28/26: `ad_show` rwi 1 và `ad_revenue` rwi 1 (intro/reward/dismiss không tách); chặng 2 (`rwi_intro_skipped`), 3 (`policy_min_gap`), 4 (`remove_ad`): **chưa đo (không có driver)**; chặng 5 (N1) PASS API 37 |
| G1/G5 | Banner/NtCl | lượt đầu trên API 37, 33, 28, 26 và Redmi **FAIL** chỉ ở `adview=0/0` (lỗi công cụ: R8 đổi tên `AdView`, D52); sau sửa: **PASS cả năm máy**, `parent=0 show=6 ntcl_ok=1 ntcl_bad=0 adview=1/1`, `AdView name after R8: i4`, FATAL/ANR/native 0 — API 37 và Redmi tools `82d1176` (F3-t1), API 33 sau một lần đo lại (D55: vòng 2 không fill trong cửa sổ), API 28/26 tools `bcfe3c6` (F3-t2); chevron chưa đo (R10) |
| G11/G6 | G11 + show rate | G11 (i)–(v) PASS ở API 37, 33, 28, 26 và Redmi (API 37: accepted 52 = distinct 52, bền 2 mất 0, distinct 68 trùng 0, 9 request_id > 1 kết thúc 0); show rate interstitial/rewarded/rwi 100 % (n = 1, mẫu mỏng) |
| G4 | Consent N1 (API 37) | DENIED 0 POST kể cả hàng rwi/brain/partner; sau GRANTED flush `flushed 16 held after DENIED` |
| G1–G3 | Chặng pangle (D15, API 37) | gate PASS (FATAL/ANR/native 0, by-sdk 0, pangle trên classpath); `ads admob adapter pangle:` **0 dòng = chưa đo** (cần nhóm mediation trên AdMob console, D50); không tuyên bố impression partner; APK dựng từ `aee3418`, không phải APK cuối |
| H8 | D-RC 3 chặng (API 37) | PASS qua harness brain: (1) `BrainPolicy: đã nạp 'calib-20260930-821a919f'`; (2) `enabled:false` → 0 `brain_decision` sau dòng nạp (đối chứng yếu); (3) `rollout_fraction:0` giữ qua cold start không extra; 0 ghi Firebase |
| H8 | calibrate end-to-end | rc 0; cây khoá = mẫu (14 khoá, 0 lệch); `calib-20261001-f4bc6102`; 16/16 ô support n = 0 → prior; **không tuyên bố `mu`** (`t190-r-be-calibrate.log`) |
| G13 | Backend | 334 passed + 1 skipped, parity 11/11 Δ 0, MERGE lần 2 +0, báo cáo 11 bảng; Docker 0 (`pgrep` = 0, `precheck docker=0` mỗi gate) |
| — | D-GMA | **no-go cho 2.0.0** (Meta bidding trên Next-Gen đạt nhưng không drop-in); Next-Gen ở 2.1.0 sau C2 (`gma-nextgen-spike.md`) |
| #40 | Che token | 6 mẫu quét `DOC/testing/t190-r-*`: 0 sau che (`t190-r-redact.log`) |


### Số đo 2.0.0 (cây `63e9f33`; Task R đủ 22 hàng; mốc trước đó `a47d7dc` = C3b, IG-C1 ở `7c8c9ef`)

**Số trong cột là số Task R (`metrics-200.md`, cây `63e9f33`; APK `4c29d4de…` cho thiết bị) kèm số các lane tới C3b để đối chiếu.** Nguồn từng số: báo cáo `.superpowers/sdd/v200/*-report.md`, `r-*-review.md`; log `DOC/testing/t200-*` (cục bộ, gitignored).

| Cổng | Chỉ số | 2.0.0 (đến `a47d7dc`) |
|---|---|---|
| G9 | Unit | **1869** (= 1831 + 38 `@Test` mới, 0 bị xoá; `git diff v1.9.0 HEAD -- src/test`), 0 fail / error / skipped, `@Ignore` thật 0 (`t200-c3b-unit2.log`). Dọc đường: IG-C1 1857, C2b 1862, C3c 1865, C3a 1867. Task R ×3 (`--rerun-tasks`, đếm từ XML): **1869 / 1869 / 1869**, skipped 0, failures 0, errors 0, `@Ignore` thật 0 (`t200-r-full-{1,2,3}.log`) |
| G8 | API dump | `API unchanged (5605 lines)` = 5494 + 120 `+` − 9 `-`; `-` ngoài `access$` = 8: **6** của C1d (`FirHelperConfig$RemoteCfSnapshot`: constructor + 5 getter game, lớp `private` dead fields, D23) + **2** của C3b (`AdsMobMy.getAdnetName`, `AdRequestFactory.build`, nay mangle `…$appplugin`). C3a, C3c, C2a, C2b: 0 `-`. Task R: `api_dump.sh --check` rc 0 "API unchanged (5605 lines)", diff `v1.9.0` `+` 120 / `-` 9, D7 24 commit chỉ 3 commit khác 0: `46f57c9` 6, `0756054` 1, `a47d7dc` 2 (D29; `t200-r-apidump.log`) 25 kiểu đổi `internal` = 0 diff dump (javap không thấy visibility Kotlin); `createRequestBody` top-level `internal` không mangle nên dòng dump giữ nguyên |
| G8 | Façade | C3b đổi 2 thành viên public nên façade với `BASELINE` = commit cha **FAIL** đúng 2 khối (có chủ ý, D27); `BASELINE` = `a47d7dc` (chính commit C3b) **PASS** + core internal PASS; Task R lặp lại ở `63e9f33` PASS (`t200-r-facade.log`) |
| G8 | H9 (`internal` ÷ kiểu top-level, `tools/public_types.sh`) | **202/275 = 73,5 %** ≥ 70 % cứng (trước C3b 177/275 = 64,4 %; plan ước 72,18 % trên 266 kiểu, tổng nay 275 sau C2); baseline `tools/public-types.txt` 98 → **73** kiểu public; 25 kiểu top-level + 3 thành viên chuyển `internal` (19 Q7 + 5 margin D17 + `UserProperty` D28; thành viên `getAdnetName`, `build`, `createRequestBody` D28-2); đột biến: bỏ `internal` ở một margin → 72,7 % và `--check` đỏ; kiểu public mới → `--check` đỏ (`t200-c3b-pubtypes2.log`, `t200-c3b-mut.log`). Task R: `public_types.sh --check --min-pct 70` rc 0, **73,5 %** (202/275), `explicitApi()` = 1 (`t200-r-h9.log`); apiCheck CI đỏ đúng bước `API surface unchanged`: PR #12 `tmp/apicheck-proof` (1100fe6 = `0275045` + 1 tham số thêm vào `MonetizationSdk.configure`, không chạy api_dump) https://github.com/dungduong2899/appplugin/actions/runs/36994736898 (PR đã đóng, nhánh đã xoá) |
| G8 | `explicitApi()` | bật ở module gốc (hai module con 0 mã); 1094 sửa `src/main` (83 file) + 1 `src/debug`, 0 đổi visibility, 0 đổi dòng (`t200-c3a-explicit-*.log`); `resourcePrefix = "appplugin_"` 96 tên, lint `ResourceName` `error`, 0 vi phạm (`t200-c3a-lint.log`) |
| G8 | Cỡ file | 0 > 800; `AdsMobPlNtFull` 791 → 581 (≤ 589; tách `AdsMobNtFullBinder` 94 + `AdsMobNtFullBackground` 146 trần 104/156); `AdsHelper` 695 ≤ 700; `IapHelper` 453 → 436 ≤ 455 (tách `PurchaseRecorder`) (`c1a`, `c3a`, `b1` reports). Task R (`t200-r-sizes.log`): 0 file `.kt` > 800 (lớn nhất `LogEventServer` 720), `AdsMobPlNtFull` 581 ≤ 700, `AdsHelper` 695 ≤ 700, 0 vượt trần trên 24 file có trần |
| G1/G2 | Shell | runner `device_gate_allowlist_test.sh` **267/267** (C2b sau fix I1, từ `git archive HEAD`; plan ước 258; C3b không thêm test shell; Task R chạy lại từ `git archive HEAD`: 267/267, `dg_tools` 43/43, `t200-r-shell.log`), `gcp_guard` 128/128, `lane_pack` 10/10, `public_types_test` 8/8; FAIL 0 |
| G3 | Release gate | `utility` PASS: `absent-class sdk=0 allow=52`; `game` PASS (`absent-class game info=862 allow=30`); harness IAP/BRAIN utility 1/1, game 0/0 (`t200-ig1-rg-*.log`, `t200-c3b-gate-*.log`). Task R (`63e9f33`, `t200-r-release-gate-*.log`): `utility` PASS (apk `4c29d4de…`, 9 050 591 B), `game` PASS (apk `1ef936fa…`, 27 500 609 B); ad unit 11/11 ×2; absent-class utility `sdk=0 allow=52`, game `info=862 allow=30`; mapping utility 416/422 = 98 %, game 413/419 = 98 %; harness utility IAP=1 BRAIN=1, game 0/0 |
| G3 | POM + 3 AAR (C2b, trước C3a/C3b; đo lại ở Task R) | POM `appplugin` 26 phụ thuộc (bản 1.9.0: 39, bớt đúng 13 nhóm game), 0 nhóm game, `pom_check.sh` PASS; AAR `appplugin` **2 581 326 B** (1.9.0: 2 556 956 B, +24 370 thông tin), `appplugin-max` **1 287 B**, `appplugin-game` **1 157 B** (manifest + rules, `classes.jar` rỗng 22 B); proof (2) tập game 229 = 229, proof (1) tập utility 162 với 0 nhóm game. C3a sau đó xoá 4 font không dùng (1 078 572 B thô trong AAR). Task R (`63e9f33`, `publishAllPublicationsToLocalCheckRepository` rc 0, `pom_check.sh` PASS Q1–Q4, 3 POM version 2.0.0, `unzip -l` ×3): AAR `appplugin` **2 065 728 B** (−491 228 so với 1.9.0 2 556 956 B, thông tin), `appplugin-max` **1 287 B**, `appplugin-game` **1 157 B** (`t200-r-pom.log`) |
| Size | `bundle_size.sh --variant utility` | IG-C1 min 7 641 961 / max 7 702 476 B; C2b min 7 644 577 / max 7 703 848 B ≤ 26 214 400 (+1 372 B so với IG-C1, thông tin; so với 1.9.0 7 702 160: +1 688 B); partner (thông tin, C2b): `applovin` 8 883 699 / 9 047 013 B, `unity` 9 448 424 / 9 570 320 B (R8 qua, D51 đóng: unity cần thêm `unity-ads:4.19.0` ở app). Task R (`63e9f33`, `t200-r-bundle-size*.log`): `utility` min 7 646 288 / max **7 706 048** B ≤ 26 214 400 PASS (+3 888 B so với 1.9.0 7 702 160, thông tin); `game` max 19 383 859 B (APK 27 500 609 B); partner max: pangle 9 762 058, mintegral 9 948 628, unity 9 573 284, vungle 8 070 607, applovin 9 047 379 B, cả 5 PASS |
| G1–G3 | Cổng thiết bị IG-C1 (`7c8c9ef`, API 37 + 26) | 8 lượt PASS lần đầu, 0 INVALID: API 37 gate FATAL/native/ANR 0, R8 0; `--banners` `parent=0 show=6 ntcl_ok=1 ntcl_bad=0 adview=1/1 adview_ntcl=1` (AdView sau R8 = `i4`); `--measure` G11 (i)–(v) PASS (accepted 52, dedup 68/0, replay 2, 9 request_id), rwi bỏ qua `rwi_intro_skipped=1`, rwi gap `policy_min_gap=1`; `--drc` leg 1 `brain_decision=1` (đối chứng thoả, D22), leg 2 0 sau dòng nạp, leg 3 giữ qua cold start, 0 ghi Firebase; `--iap` 12/12 với `remove_ad` rwi 1; API 26 gate/banners/measure PASS (`t200-ig1-emu{37,26}-*.log`). Smoke 5570 sau C3a và C3c: gate thường + `--banners` PASS |
| G1–G3 | Gate 37 / 33 / 28 / 26 (emulator, APK `4c29d4de…`, `63e9f33`) | PASS ×4: FATAL/ANR/native 0, R8 0, by-sdk 0 ×2, `precheck docker=0 sink=up`; `initPlugin total` cold1/cold2 (ms): 37 108/103, 33 26/46, 28 155/72, 26 63/128 (`t200-r-emu{37,33,28,26}-gate.log`) |
| G1–G3 | Máy thật (Task R E-real, `63e9f33`) | Redmi 65c9b673 (API 34): gate PASS (init 28 ms), `--banners`, `--drc` PASS, `--iap` 12/12; `--consent` **driver-limited trên MIUI** (D31: 6 lần bấm; đường sản phẩm có bằng chứng log app `flushed 18 held after DENIED`, F3-t1 không land). Samsung R5CX10P9EKZ (API 36, D5 đo được): gate PASS, `--banners`, `--consent` (`flushed 16`), `--drc` PASS; `--measure` (#10/#12) và `--iap` (#13) **chưa đo tự động (driver)**: INVALID ×3 (không tìm thấy "Show Interstitial" / NotificationShade; iap 9/12; một lần by-sdk=7 do prefs lần đầu). H4 giữ nhờ Redmi + Samsung các hàng còn lại + 4 emulator |
| G11 / G6 | G11 + rwi chặng 1–3 + banner (Task R, 37/33/28/26) | G11 (i)–(v) PASS ×4 (37: accepted 52/52, bền 2/0, distinct 68/0, 9 request_id kết thúc 0); rwi chặng 1 (intro 1, `ad_show` rwi 1, reward 1, dismiss 1) đếm từ sink + log app ở API 37, chặng 2 (`rwi_intro_skipped`, `ad_show` 0) và 3 (`policy_min_gap`) PASS ×4; `--banners` PASS ×4 `parent=0 show=6 ntcl_ok=1 ntcl_bad=0 adview=1/1`; chevron chưa đo (R10). Redmi: G11 (i)–(v), rwi 1/2/3, banners PASS |
| G12 | `--iap` (Task R) | API 37 12/12 và 28 12/12 (rwi `remove_ad` 1); **ngoại lệ ghi rõ**: 33 11/12 (D30: `kill_mid_purchase` INVALID do Play Billing tạm không khả dụng; chạy lại 11/12 do GMS persistent chết, app bị kill trong cửa sổ, D32-2), 26 11/12 (D32/D32-2: `buy_sub` `not_contain_placement` là đua harness held-flush với `show_from`, quảng cáo bị chặn đủ, `ad_show` sau grant 0); Redmi 12/12; by-sdk 0 |
| G4 | Consent N1 (Task R) | DENIED 0 hàng/0 POST và flush sau GRANTED: 37 +17 (held 16), 33 242→242 +19, 28 286→286 +17, 26 241→241 +17; máy ấm (#29) |
| H8 | D-RC (API 37, Task R) | PASS: chặng 1 `brain_decision` 1 (đối chứng, ad shown 0), chặng 2 0 sau dòng nạp (ad đã hiện), chặng 3 `roll0-r17` giữ qua cold start, 0 ghi Firebase (D22) |
| G1–G3 | Chặng pangle (Task R, API 37, D33) | lượt 3 PASS (host yên, tải 3,58): FATAL/ANR/native 0, by-sdk 0 ×2, R8 0, monkey 0 crash/anr, init 147/33 ms; lượt 1 INVALID, lượt 2 `am_anr=1` ở host tải 20; `adapter pangle:` **chưa đo (console, D50/owner)** |
| G13 | Backend (Task R) | `pytest -q` **334 passed / 1 skipped / 0 failed** (315 unit + 19 integration); parity 13 passed (11 view Δ 0); `stack_load.py --merge-runs 2` +2394 → 2392 distinct, lần 2 **+0**; `/push` thiếu/sai khoá → 401 cả ba; `report_daily` 11 bảng; Docker không cùng thiết bị: `precheck docker=0` mọi log thiết bị (Docker Desktop do E-BE bật còn chạy, chủ dự án dừng, D34) |
| G11 | Show rate (backend, 6 sink Task R, D34) | `v_show_rate`: interstitial 15/18 = 83,3 %, rewarded 5/5 = 100 %, rwi 10/27 = 37,0 % (n nhỏ); banner 14/98, native_collapse 10/18. Phân rã: rwi 32 lượt = 10 hiện + 5 `show_not_loaded` + 8 `policy_min_gap` + 9 `rwi_intro_skipped` (chặng cố ý) nên mọi lượt đã sẵn sàng và không bị chặn đều hiện (10/10); interstitial trượt 3 = `ad_showing` ×2 (chống chồng quảng cáo) + brain từ chối (đối chứng `--drc`) nên lượt tự nhiên đã sẵn sàng 100 %; view chưa tách lý do: 2.1.0 |
| G6 | rwi chặng 1 trên 2.0.0 | gate chưa assert tường minh chặng 1 (intro/reward/dismiss); Task R #10 đếm từ sink + log app (xem hàng G11 / G6 ở trên) |
| #40 | Che token | 6 mẫu quét 174 log `t200-*`: 0 thật; 1 dòng mô tả test X8 (`preferred account via installer`, `t200-t0c-green.log:266`) nêu tên, không đếm; không file nào cần che (`t200-r-redact.log`); sau Task R: 0 trên 86 log `t200-r-*` + mọi `device-gate-*.log` (6 mẫu, controller 22:23; từng lane tự quét = 0) |

### Số đo 2.1.0 (Task R: mã APK `26609b7`, công cụ `79ea1f3`)

**Trạng thái:** Task R đủ 32 hàng (`.superpowers/sdd/v210/metrics-210.md`) trên APK 2.1.0 đóng băng `26609b7` (`utility` `f168c835`, `game` `a3067f26`, `licbad` `1cb55a80`, `pangle` `ef1ae00e`); `sdkVersion` 2.1.0 (dấu vết dex `appplugin/2.1.0/26609b7`). Quy ước sha: hàng APK `26609b7`; hàng công cụ là đầu nhánh công cụ `79ea1f3` (công cụ đổi sau khi APK đóng băng không đổi APK): `8cddca3` PERF-fix (`perf_report.py` đọc lượt first-run, R11 `perf-first-run`), `5c45f2c` ngưỡng PERF-B (D22 sàn nhiễu 20 / 40 ms), `c4bf167` FC1 (`api_facade_check.sh` ưu tiên điều kiện BuildSha `A || { B && C; }`, 78 dòng, runner 302), `79ea1f3` ngưỡng fill/show Redmi từ A/B ghép cặp (D24). **SMK-1 và IG-G PASS** (IG-G gộp vào SMK-1 mục E trên API 26 và 37 với mã cuối; `smk1-report.md`); **E-emu PASS ×4** (API 37, 33, 28, 26); **E-real Redmi PASS** (consent MIUI 0 lần chạm tay, A/B fill không hồi quy); **Samsung R5CX10P9EKZ chưa đo (máy vắng)** (không có trong `adb devices`); show rate theo D21 (bên dưới). Nguồn: `.superpowers/sdd/v210/r-{e1,e2,eemu,ereal,ebe,ebe12}-report.md`, `smk1-report.md`, `perf-b.md`, `ceo-decisions.md` D17–D24; log `DOC/testing/t210-*` (cục bộ, gitignored).

| Cổng | Chỉ số | 2.1.0 (Task R) |
|---|---|---|
| G9 | Unit | **1915** `@Test` chạy thật = root 1805 + `appplugin-game` 110, 0 fail / 0 skip / `@Ignore` 0: ba lượt giống hệt nhau ở `2b980ee` (`t210-r-full-{1,2,3}`) và một lượt ở `26609b7` (`full-4`, `BUILD SUCCESSFUL` 2 phút 5 giây; từ `2b980ee` tới `26609b7` chỉ đổi công cụ, build, dump, 0 test). Nền kế hoạch là 1907 (D10); thực tế 1915 (SA1 +18 thay vì +14, P1 +5, P2 +11, P3 +5) |
| G8 | API dump | `api_dump.sh --check` rc 0 hai module: root `api/appplugin.api` **5329** dòng, `api/appplugin-game.api` **389**; so với `v2.0.0`: `-` **394** (chỉ ở G1: 32 khối lớp game + 5 thành viên đổi tên mangle), `+` **118** = 109 + 9 (SC1-2 `BuildSha` ra khỏi `BuildConfig`); 5605 − 394 + 118 = 5329 |
| G8 | Façade + D7 | root và `-game` PASS (`BASELINE_ROOT` = `BASELINE_GAME` = `e2ed528`, `core_internal_check` PASS, kể cả tiện ích SC1-2; FC1 `c4bf167` ghi nhận điều kiện BuildSha); D7 theo từng commit: dòng `-` khác 0 chỉ ở `e2ed528` (394) |
| G8 | H9 | `public_types.sh --check --min-pct 70`: **72,7 %** (205/282); `explicitApi()` 1 + 1 = 2. Đối chứng CTRL: PR #14 (`tmp/apicheck-proof-210`, `26609b7` + một tham số public, không dump) → run 37073511130 job `verify` FAIL tại "API surface unchanged" đúng như kỳ vọng (release-gate và backend xanh); PR đóng, nhánh xoá |
| G8 | Cỡ file | 0 file `.kt` > 800 ở mọi module (lớn nhất `LogEventServer` 720); `AdsMobPlNtFull` 586 ≤ 589, `AdsHelper` 696 ≤ 700, `AppPluginBase` 293 ≤ 295, `LogEventServer` 721/721, `measure_gate.sh` 325 ≤ 343, `iap_gate.sh` 297 ≤ 297, `consent_gate.sh` 116 ≤ 136, `device_gate.sh` 258 ≤ 264, `api_facade_check.sh` 78/78, `perf_report.py` 275/285; 27 trần công cụ đều OK |
| G1/G2 | Shell | runner `device_gate_allowlist_test` **302/302** (142 + 32 + 26 + 18 + 36 + 48); `gcp_guard` 128, `lane_pack` 13, `public_types` 12, `perf_test` 11 (R11), `license_issue` 11; FAIL 0 |
| G3 | Release gate, APK, AAR | `release_gate.sh` PASS ×4 (`utility`, `game`, `licbad`, `striplogs`): ad unit trong `resources.arsc` 11 (≥ 11), `absent-class sdk=0 allow=52` (`game` info 862), mapping 98 % ×4 (418/434/418/417 lớp), harness `utility` 1 / `game` 0, zipalign OK; `pom_check.sh` PASS (3 POM 2.1.0, `-game` → `appplugin:2.1.0`, `-max` 13 phụ thuộc). AAR: `appplugin` **2 022 468 B** (2.0.0 2 065 728: −43 260), `appplugin-game` **81 187 B** (`classes.jar` 84 642), `appplugin-max` 1 287 B (`classes.jar` 22 B). APK `utility` 9 046 859 B. Biến thể `pangle` (D17): `absent-class sdk=1` (`org.apache.http.conn.ConnectTimeoutException`, Pangle tham chiếu stack HTTP cũ, không phải mã SDK) = thông tin, không phải cổng Task R |
| Size | `bundle_size.sh --variant utility` | max **7 702 334** B (min 7 640 874) ≤ 26 214 400; so với 2.0.0 7 706 048: −3 714 (thông tin, phẳng). Thông tin: `pangle` 9 757 802, `mintegral` 9 945 048, `unity` 9 570 237, `vungle` 8 069 227, `applovin` 9 046 966; `game` max 19 395 479 (APK 27 517 385) |
| G13 | Backend (E-BE, Docker) | `pytest -q` **354 passed / 1 skipped**, 0 fail (355 collected; plan nói 350 + 1, 4 test hơn); parity **12 view Δ 0** (+ `test_show_rate_sql_bo_full_2`); `stack_load.py --merge-runs 2`: raw 37 / distinct 37 cả hai lượt (+0); `/push` không khoá và khoá sai = 401; `/policy` PO1–PO13 13/13. Lane BE không Docker (BE2): 334 + 1. `stack_load.py` treo (timeout 124) ngay sau khi pytest dừng/khởi động lại emulator BigQuery; chạy lại sau `down -v` và `up` thì PASS (E-BE phải dựng lại stack trước `stack_load`) |
| G1–G3 | Gate 37 / 33 / 28 / 26 + Redmi (#9) | PASS ×5, FATAL/ANR/native 0, R8 0, by-sdk 0 ×2, `api=` khớp, `precheck docker=0 sink=up`, `license: VALID kid=dev1 exp=2027-10-02` đúng 1 dòng mỗi process (#28), `game: không có … — 0 section game` có mặt, 0 dòng `(RemoteConfig):` game. `initPlugin` total ở hai cold start của gate: API 37 81 / 59 ms, 33 140 / 76, 28 78 / 106, 26 122 / 152, Redmi 37 / 37. Không lượt gate nào bị phân loại INVALID (T0d); `emu_boot` quiet-wait trả `INVALID (contention)` trước gate ở API 37 ×4 và 33 ×1, và gate không chạy trên boot nhiễu |
| G1/G5 | rwi chặng 1–3 (#10) | PASS ×5 (37, 33, 28, 26, Redmi): chặng 1 intro 1 / `ad_show` rwi 1 / reward 1 / dismiss 1; chặng 2 `rwi_intro_skipped` 1, `ad_show` 0; chặng 3 `policy_min_gap` 1, `ad_show` 0 |
| G1/G5 | Banner / NtCl (#11) | PASS ×5: `parent=0 show=6 ntcl_ok=1 ntcl_bad=0 adview=1/1 adview_ntcl=1`; chevron "chưa đo (R10)" như đã ghi trước |
| G6/G11 | G11 (i)–(v), show rate (#12) | G11 (i)–(v) PASS ×5 (accepted 52, bền mất 0, dedup 68 / trùng 0, replay giữ session, 9 `request_id` một điểm kết thúc). Show rate (D20 oracle Python, không Docker; D21 PASS có tách lý do), gộp 4 emulator: `interstitial` 11/11, `rewarded` 4/4 (organic 100 %); `rwi` 9/22 thô = 11 `blocked_policy` (chặng 2 và 3 của driver) + 4 `show_blocked_kill_switch` (chặng kill switch của `--measure`) + 1 `show_when_act_null` (harness) → mọi lượt sẵn sàng có tổ chức đều hiện; `banner` 12/109, `native_collapse` 9/14, `rect` 3/4, `opened` 0/2 = view đếm chu kỳ làm mới trên một lần hiện mỗi view (cách đọc như 2.0.0 D34), không phải lỗi sản phẩm. So với 2.0.0: `interstitial` thô 83 % → 100 % (hết va chạm `ad_showing`), `rwi` 37 % → 41 % thô, lý do nay hiển thị |
| G12 | Billing + rwi `remove_ad` (#13) | 12/12 PASS ×5; FAIL không, INVALID không; `ad_show_fail reason=remove_ad` 1 ở rwi mỗi máy |
| G4 | Consent N1 (#14) | PASS ×5: sau "Do not consent" xác minh `tier=DENIED`, cửa sổ DENIED 60 s: 0 hàng / 0 POST; GRANTED flush +17. **Redmi đo được**: thang MIUI T0b online, **0 lần chạm tay** ('Do not consent' chạm node lần 2/3 xác minh; 'Consent' lần 1/3). emu28: lượt cuối là chạy lại lúc yên (lượt 0 bị máy host ngủ giữa chừng, cửa sổ DENIED 17 427 s vẫn 0 / 0, giữ làm `consent.run0.log`) |
| G7/H8 | D-RC (#15) | PASS (37 và Redmi): ch1 `brain_decision` 1 (`INTERSTITIAL_OPPORTUNITY`), ch2 kill switch sau khi load `brain_decision` 0 + ad hiện 1, ch3 cold không thêm; 0 ghi Firebase/RC |
| — | `game` flavor (#32), pangle (#20) | `game` trên 37: `game: GameModule installed — 5 section` mỗi process + `license: VALID`, FATAL/ANR/native 0; 0 dòng `(RemoteConfig):` vì RC thật của dự án chưa có giá trị cho 5 khoá game (trường hợp RC rỗng, đã báo). Pangle trên 37: gate PASS (FATAL/ANR/native 0, by-sdk 0), `adapter pangle:` **chưa đo (cần nhóm mediation trên AdMob console, O4)** |
| G1–G12 | E-real (#19) | Redmi 65c9b673 (API 34): #9, #10, #11, #12 (G11), #13, #14, #15 PASS; **Samsung R5CX10P9EKZ chưa đo (máy vắng)**, driver One UI T0c chỉ có fixture |
| #40 | Che token (#18, `t210-r-redact-final.log`) | 6 mẫu #40 + `ALPK1.` (+ kèm b64) + PEM khoá riêng trên 1025 file (`DOC/testing/t210-*` đệ quy + `device-gate-*-2026100[23]-*`): **0 hit**, không che, không xoá dòng |
| — | Docker không cùng thiết bị (#17) | PASS: 75/75 log thiết bị có dòng `precheck docker=0`; 0 dòng `precheck docker=[1-9]` trong 176 log `t210-*` |
| CI | CI trên HEAD (#21) | ghi ở `metrics-210.md` #21 bởi controller sau khi mở PR (không phải số của lane tài liệu) |

**SEC (bậc A/B/C-lite).**

| Bậc | Việc | Bằng chứng | Task R |
|---|---|---|---|
| A (SA1 `8ce7ab7`, SA2 `2d8df10`) | Token `ALP1.<payload>.<chữ ký>` Ed25519, meta-data `com.app.plugin.LICENSE`, gói + SHA-256 cert + hạn + tính năng `ads`/`brain`; xác minh offline một lần mỗi process trên luồng nền | unit 18 (L1–L17 + L6b), vectơ RFC 8032 byte-exact, đột biến M1–M6 đỏ rồi hoàn tác; `license_issue.py` 11/11 (khoá riêng ngoài repo, `chmod 600`, từ chối đường dẫn trong repo); khoá công khai `dev1` `a6dac8eb…d036e7` | #28 PASS ×5 (đúng 1 dòng `license: VALID kid=dev1 exp=2027-10-02` mỗi process, 0 INVALID). #29 `licbad` PASS trên 37 và Redmi: `license: INVALID (malformed) — quảng cáo và brain tắt` đúng 1 dòng mỗi process, `ad_request` 0, `sdk_health networks=unlicensed license=INVALID:malformed` 1 lần mỗi process (logcat; sink không nhận `sdk_health`), FATAL/ANR 0. `brain_decision` thô 3 (37) và 2 (Redmi) **đều từ `BrainPlaygroundActivity` của demo** gọi thẳng `DecisionEngine` (`decision_id d_demo_player_*`), nguồn AdsBrain = 0 (D19: `DecisionEngine` là lớp public, không bị chặn bởi verdict; chặn đường tiền `isDisableAds`/`startAdNetworks`/AdsBrain nên không thể có yêu cầu hay hiện quảng cáo; chặn hoặc đóng gói `internal` ở 2.2.0). Thời gian xác minh trên ART API 26: SDK không ghi log nên **chưa đo** |
| B (BE2 `65efcf1`) | `GET /policy` kiểm khoá API trước, verdict Integrity (giả trong test), fail-closed về artifact mặc định; lý do trên dây chỉ `integrity_off`/`no_token`/`not_accepted`/`no_policy` | 13 test PO1–PO13; đột biến M1/M2 đỏ; log không chứa token | #30 PASS: PO1–PO13 13/13 trong pytest; `import app.main` không nạp `google.auth` (chỉ import lười trong `integrity.verify`, `AnonymousCredentials` ở nhánh emulator của `config`/`bq_setup`). Chân Integrity thật: owner-gated O3 (`sec-gioi-han`) |
| C-lite (SC1 `1b39437`, SC1-2) | Dấu vết dựng `appplugin/<phiên bản>/<git sha>` (`BuildInfo.ID` đọc `BuildSha` sinh lúc build), `sdk_health.build`; rule bỏ log SDK tuỳ chọn | shell W1/W2/W4 + unit W1u/W2u | #31 PASS: dex `utility` có `appplugin/2.1.0/26609b7` ×1 (`striplogs` cũng ×1); chuỗi `logD` mẫu `utility` 1/1/1, `striplogs` 0/0/0; `consumer-rules.pro` và `proguard.txt` của AAR 0 `assumenosideeffects`; AAR `BuildConfig` không còn `BUILD_ID` |

### Hiệu năng (2.1.0): bảng `perf_report` theo máy

**Cách đo:** `bash tools/perf_gate.sh --runs 5` mỗi máy trên APK đóng băng (≥ 5 lượt cold `am start -W` sau `force-stop`, một lượt priming không tính, `onCreate`/`initPlugin`/`prefs`/StrictMode, một vòng show, `dumpsys meminfo` PSS sau settle và sau show, một lượt first-run sau `pm clear`; chỉ `PASS`/`INVALID`, không phán ngưỡng) rồi `python3 tools/perf_report.py` (trung vị, p90 nội suy tuyến tính; request → fill, fill rate, show latency, time-to-first-request từ sink) so `tools/perf-thresholds.json`. Ngưỡng `hard` **chỉ trên máy thật**; emulator 37/33/28/26, từng mạng và PSS là **thông tin** (baseline cho 2.2.0, D5). Quảng cáo là **test ad** (`ca-app-pub-3940256099942544`), fill/latency không dự báo production. Chạy trong khoá thiết bị, Docker 0. Baseline 2.0.0 của Task R là PERF-B: APK 2.0.0 đóng băng (`4c29d4de`) đo bằng chính công cụ mới (`perf-b.md`), n = 5 mỗi máy; PERF-0 (log `t200-*`, n 1–2) chỉ còn là nguồn lịch sử. Bảng đủ 6 máy là các bảng dưới đây (và bảng PERF trong CHANGELOG `[2.1.0]`); `DOC/testing/t210-r-perf-table.md` (hàng #27) chỉ có ở máy cục bộ (gitignored), không nằm trong commit. **Samsung chưa đo (máy vắng).**

Ngưỡng hard của Redmi (`perf-thresholds.json` @`79ea1f3`): khởi động từ PERF-B (cold n = 5) × 1,2 với sàn nhiễu D22 (chỉ số có trung vị < 20 ms: `med` = max(×1,2, 20 ms), `p90` = max(×1,2, 40 ms)) nên `onCreate` ≤ 20 / 40, `initPlugin` ≤ 20, `TotalTime` ≤ 437 / 543; `prefs_waited_ms` 0, `by_sdk` 0, `first_by_sdk` ≤ 1 (D16); fill và show từ A/B ghép cặp gộp 2.0.0 (D24, ×1,5 vì phụ thuộc mạng): `interstitial` ≤ 3632 / 5111, `rwi` ≤ 2832 / 3327, `opened` ≤ 2851 / 2879, `show_ms.interstitial` ≤ max(×1,2, 200) = 200, `fill_rate` ≥ 0,95, `fill_per_request` ≥ 0,90; khoá `fill_ms.banner` bỏ vì `perf_gate` không yêu cầu banner (2.2.0: thêm chặng banner). Khối Samsung còn tạm (`provisional`, máy vắng). Emulator: `info`.

**Khởi động** (cold n = 5, trung vị / p90, ms; "A → B" = 2.0.0 PERF-B → 2.1.0 Task R; Redmi 2.1.0 = lượt chạy lại lúc yên):

| Chỉ số | Redmi (API 34) | Samsung (API 36) | emu37 | emu33 | emu28 | emu26 |
|---|---|---|---|---|---|---|
| `TotalTime` | 364 / 452,4 → **379 / 418,8** | chưa đo (máy vắng) | 1037 / 1160,6 → 1066 / 1248,2 | 864 / 983,4 → 1555 / 1901,6 | 719 / 835,2 → 796 / 835,6 | 657 / 774 → 702 / 788,4 |
| `onCreate` | 12 / 25,8 → **17 / 25** | chưa đo (máy vắng) | 85 / 174,6 → 70 / 243,4 | 38 / 64,6 → 75 / 148,2 | 67 / 78,2 → 88 / 104,6 | 131 / 163,4 → 104 / 126,4 |
| `initPlugin` total | 11 / 23,4 → **16 / 22,2** | chưa đo (máy vắng) | 83 / 173,6 → 33 / 176,4 | 34 / 59,2 → 73 / 128,8 | 39 / 58 → 73 / 82,8 | 104 / 154 → 94 / 119 |
| `prefs_waited_ms`, by-sdk | 0, 0 | chưa đo (máy vắng) | 0, 0 | 0, 0 | 0, 0 | 0, 0 |
| first-run sau `pm clear`: by-sdk · `TotalTime` | **0** (≤ 1) · 355 (lượt 1: 424) | chưa đo (máy vắng) (2.0.0: 7) | 0 · 1181 → 1010 | 0 · 1235 → 979 | 0 · 1041 → 788 | 0 · 779 → 781 |
| Ngưỡng 2.1.0 | **hard** PASS: ≤ 437 / 543, ≤ 20 / 40, ≤ 20, 0, 0, ≤ 1 | tạm (không phán) | info | info | info | info |

Redmi lượt 1 của 2.1.0 (máy tải ≈ 4) FAIL `onCreate` p90 50,6 và `TotalTime` p90 659 vì đuôi warm-up (689, 614 ms ở hai lượt đầu); chạy lại **một lần** lúc yên (tải 1,4) PASS (hai log giữ: `t210-r-65c9b673-perf{,-rerun}`). A/B ghép cặp xen kẽ (2.0.0 → 2.1.0 → 2.0.0 → 2.1.0, n = 10 mỗi bên): `TotalTime` 365 / 392,8 vs 353,5 / 426,7; `onCreate` 16,5 / 29 vs 16,5 / 39,6; `initPlugin` 15,5 / 26,1 vs 15 / 37,3; first-run 366 vs 359 → **2.1.0 bằng 2.0.0 trong nhiễu**. First-run by-sdk **0** trên Redmi ở cả 2.0.0 và 2.1.0 (ngưỡng ≤ 1, D16) và trên bốn emulator; ca 7 của Samsung ở 2.0.0 (M47), cái P1 nhắm tới, **chưa kiểm lại vì Samsung chưa đo (máy vắng)**. Emulator: không có chiều thống nhất giữa các API và các lượt chạy lại (host tải 4–16; 37 chỉ một lượt vì boot yên INVALID ×3), nên không kết luận hồi quy hay cải thiện; máy thật quyết định.

**Request → fill** (ms, trung vị / p90, **test ad**; 2.1.0 Task R):

| Format | Redmi (lượt chạy lại) | Redmi A/B gộp 2.0.0 → 2.1.0 | Samsung | emu37 | emu33 | emu28 | emu26 |
|---|---|---|---|---|---|---|---|
| `interstitial` | **2891 / 3116** (n = 7) | 2421 / 3407 → 2465,5 / 3517 (n = 14) | chưa đo (máy vắng) | 2013 / 3247,2 | 3790 / 4887 | 2291 / 2738 | 4194 / 5348,2 |
| `rwi` | 2148 / 2541,2 (n = 7) | 1888 / 2218 → 2109 / 2549 (n = 15 / 14) | chưa đo (máy vắng) | 1474 / 2964 | 3882,5 / 4741,5 | 2068,5 / 2203,4 | 2150 / 2872,8 |
| `opened` | 1819 / 1849,4 (n = 2) | 1900,5 / 1919 → 2094,5 / 2145 (n = 4) | chưa đo (máy vắng) | 3029,5 / 3367,5 | 3304 / 3831,2 | 2241,5 / 2395,5 | 2937 / 2995,4 |
| Tất cả định dạng | 2285 / 3054,7 | 2192 / 3581 → 2329,5 / 3010 | chưa đo (máy vắng) | 1890,5 / 3453,2 | 3784 / 4875 | 2099 / 2476 | 2835 / 4659 |
| Ngưỡng 2.1.0 (D24) | **hard**: ≤ 3632 / 5111, ≤ 2832 / 3327, ≤ 2851 / 2879: PASS cả ba (cả lượt chạy lại lẫn A/B gộp) | | tạm | info | info | info | info |

`banner` không có số (không có yêu cầu banner trong cửa sổ đo của `perf_gate`). Redmi **không hồi quy fill**: `interstitial` +1,8 %, `rwi` +11,7 %, `opened` +10,2 % ở trung vị gộp, trong chênh lệch lượt-với-lượt của chính 2.0.0 (`interstitial` 7 %, `rwi` 32 %); các số 3103 / 2891 so với 2572 của lần đo đơn đầu là biến thiên mạng và AdMob.

Các chỉ số còn lại:

| Chỉ số | 2.0.0 (PERF-B / A/B) | 2.1.0 (Task R) | Ngưỡng |
|---|---|---|---|
| Fill rate = fill / (fill + no_fill) | Redmi 1,0 (37/37, gộp); emulator 1,0 | Redmi **1,0** (18/18; gộp 36/36); emu37 1,0 (18/18), emu33 1,0 (17/17), emu28 1,0 (19/19), emu26 1,0 (18/18) | máy thật `hard` ≥ 95 %: PASS |
| Fill / request | Redmi 0,97 (37/38, gộp); 0,95 (18/19) lượt online đơn | Redmi **0,95** (18/19; gộp 36/38, 2 request treo); emu37 0,95, emu33 0,89, emu28 1,0, emu26 0,95 | máy thật `hard` ≥ 90 %: PASS |
| Show latency `interstitial` (`ad_show_attempt(ready=1)` → `ad_show`) | Redmi 144,5 ms (A/B gộp, n = 2); 130 (lượt online đơn) | Redmi **133 ms** (n = 1; gộp 136,5, n = 2); emu37 173, emu33 396, emu28 158, emu26 188 (n = 1 mỗi máy); `rwi` ≈ 5,2 s gồm màn intro, không phải trễ SDK | máy thật `hard` ≤ 200 ms: PASS |
| Time-to-first-request (consent xong → `ad_request` đầu) | Redmi 568,5 ms (A/B gộp, n = 14) | Redmi 565 ms (lượt chạy lại) / 572,5 ms (A/B gộp); emu37 1363, emu33 2163, emu28 1230, emu26 969 | thông tin |
| PSS (`dumpsys meminfo`, KB): settle / sau show | Redmi 189802 / 256439; emu37 160720 / 184692, emu33 142248 / 156706, emu28 103751 / 146324, emu26 104786 / 117081 | Redmi **188227 / 254155** (lượt 1: 175817 / 232480; heap Java 15,7 / 34,3 MB, native 20,3 / 32,2 MB); emu37 156132 / 180849, emu33 107557 / 152367, emu28 108062 / 156008, emu26 104668 / 112939 | **thông tin**, không ngưỡng số (baseline cho 2.2.0); Samsung chưa đo (máy vắng) |

---

### Hiệu năng (2.2.0): Task R, bảng `perf_report` theo máy (APK 2.2.0; nguồn `metrics-220.md` #23–#26)

**Cách đo:** như 2.1.0 (`perf_gate.sh --runs 5`, `perf_report.py`); ngưỡng `hard` chỉ trên Redmi, emulator và PSS là thông tin. Cột 2.1.0 lấy từ mục trên.

| Chỉ số | Redmi (API 34) | Samsung (API 36) | emu37 | emu33 | emu28 | emu26 |
|---|---|---|---|---|---|---|
| `TotalTime` cold | **376 / 474** (2.1.0 379 / 418,8) | chưa đo | 700 / 898 | chưa đo | chưa đo | 750 / 811 |
| `onCreate` | **15 / 33,6** | chưa đo | 55 / 85,8 | chưa đo | chưa đo | 185 / 231 |
| `initPlugin` total | **14 / 31,4** (2.1.0 16 / 22,2) | chưa đo | 55 / 81,4 | 89 / 52 (chỉ số gate) | 74 / 152 (chỉ số gate) | 142 / 212,2 |
| `prefs_waited_ms`, by-sdk | 0, 0 | chưa đo | 0, 0 | chưa đo | chưa đo | 0, 0 |
| first-run (`pm clear`): `first_by_sdk` · `TotalTime` | **0** (≤ 1) · 393 | chưa đo | chưa đo | chưa đo | chưa đo | 0 · 676 |
| `fill_ms` `interstitial` / `rwi` / `opened` | 2274 / 3446, 1560 / 2424, 2344 / 2613 (≤ hard) | chưa đo | chưa đo | chưa đo | chưa đo | chưa đo |
| `fill_ms` `banner` | n = 1: 6196 (info; A/B cần n ≥ 10) | chưa đo | chưa đo | chưa đo | chưa đo | chưa đo |
| Fill rate · fill/request | **1,0 · 1,0** | chưa đo | 1,0 · chưa đo | chưa đo | chưa đo | 0,93 · 0,65 (info) |
| Show latency `interstitial` | **164** (≤ 200) | chưa đo | chưa đo | chưa đo | chưa đo | chưa đo |
| PSS settle / sau show, KB | 172491 / 234017 | chưa đo | chưa đo (vòng show INVALID) | chưa đo | chưa đo | 111167 / 133963 |
| Ngưỡng | **hard** PASS | tạm | info | info | info | info |

Ghi chú: (1) Redmi lượt đầu INVALID (tải host 4,7, adb timeout, `initPlugin` 23 > 20), chạy lại **một lần** lúc yên PASS; log giữ cả hai. Sink của số quảng cáo gộp lượt đầu và lượt chạy lại (n = 36), nên chỉ là chiều tham khảo. (2) A/B ghép cặp PERF2-AB n = 10: `initPlugin` 13,5 vs 13,5, `onCreate` 14,5 vs 18, `TotalTime` 350 vs 352,5 (2.1.0 vs 2.2.0) nên SEG không làm chậm khởi động trong nhiễu; ngưỡng không đổi (`7f2fa0e`). (3) emu37: vòng show INVALID (ui) hai lần vì host bận (tải 4–10), chỉ có số cold (thông tin). (4) Game flavor trên emu37 INVALID (billing unavailable) hai lần, APK game 2.1.0 cho kết quả giống hệt: môi trường emulator. (5) Ô "chưa đo" và INVALID, theo nguyên nhân: Samsung (màn hình bảo mật tự khoá giữa lúc đo); API 24 (không có image arm64-v8a, O11); SQL parity 13 view và `stack_load` (Docker tắt); CI và `release.yml` (bước P); vòng show perf trên emu37 (host bận, INVALID ui; số cold chỉ là thông tin).

### Số đo 2.3.0 (Task R: APK `c1903247…` @66e6e6e, công cụ/test @75597c1 → b30c753; nguồn `v230/metrics-230.md`)

| Chỉ số | Kết quả |
|---|---|
| Unit ×3 (#1) | root 1980 + game 110 = **2090** ×3, 0 fail/skip (E = 2041 + 49) |
| API (#2, #3) | dump root 5806 / game 389, `-` ngoài `access$` = 0; façade ×2 PASS (BASELINE 2ccd17e); D7 = 0 |
| H9 (#6) | **71,8 %** (224/312) ≥ 70 |
| Shell (#5) | runner 354/354 (sau IG2), guard 128, lane_pack 14, public_types 12, perf 15, license 11, lanelock 9 |
| Release gate + size (#7, #8) | utility/game/licbad PASS, AAR minSdk 24 ×3; download max 7 731 445 B (+5 299 vs 2.2.0) ≤ 26 214 400 |
| Lint (#20) | `NewApi` 0, Errors 0 ×3 module (Warnings 90 / 11 / 4) |
| Gate thiết bị (#9) | Redmi + emu37/33/28/26 PASS (FATAL/ANR/native 0, by-sdk 0, license VALID 1/process, `policy: off` 1/process, 0 `policy: applied/default`) |
| Preload (#41, #42) | 5/5 slot nạp (D33: 3 `load` + `skip (loading)`), lần show đầu "đã nạp sẵn" 3/3 trên 5 máy; app open resume chưa đo (cờ app, D35) |
| IAP (#13) | 12/12 trên 5 máy; StrictMode MIUI trên Redmi (D34), by-sdk 0 trên 4 emulator |
| Backend (#16, #36) | 383 passed + 1 skipped (367 + 16); parity 13 view + `stack_load` chưa đo (Docker tắt) |
| Cổng IM (#40) | build + unit PASS trên nhánh IM (`ff40b65`) |
| SPIKE GMA (#38) | GO có điều kiện (D16-230) → 2.4.0 |
| Chưa đo | Samsung (vắng), API 24 (O11), SQL parity/`stack_load` (Docker tắt), timing emu37/33 (INVALID), app open resume, CI HEAD + `release.yml` (P) |

### Hiệu năng (2.3.0): Task R, bảng `perf_report` theo máy (APK 2.3.0 `c1903247…`; nguồn `metrics-230.md` #23–#26)

**Cách đo:** như 2.2.0 (`perf_gate.sh --runs 5`, `perf_report.py`); ngưỡng `hard` chỉ trên Redmi (luật noise-floor ×1,5, D24-210), emulator và PSS là thông tin. Cột trong ngoặc = 2.2.0.

| Chỉ số | Redmi (API 34) | Samsung (API 36) | emu37 (INVALID: tải host) | emu33 (INVALID: tải host) | emu28 | emu26 |
|---|---|---|---|---|---|---|
| `TotalTime` cold | **337 / 376,6** (376 / 474) | chưa đo (vắng) | 1386 / 1591 (700 / 898) | 946 / 1048 | 822 / 942 | 894 / 921 (750 / 811) |
| `onCreate` | **18 / 25,8** (15 / 33,6) | chưa đo (vắng) | 95 / 193 (55 / 85,8) | 91 / 104,8 | 110 / 116 | 170 / 203 (185 / 231) |
| `initPlugin` total | **16 / 23,6** (14 / 31,4; hard med ≤ 20) | chưa đo (vắng) | 93 / 175,8 (55 / 81,4) | 56 / 67,8 | 91 / 106,8 | 141 / 184,2 (142 / 212,2) |
| `prefs_waited_ms`, by-sdk | 0, 0 | chưa đo (vắng) | 0, 0 | 0, 0 | 0, 0 | 0, 0 |
| first-run (`pm clear`): `first_by_sdk` · `TotalTime` | **0** (≤ 1) · 377 (393) | chưa đo (vắng) | 0 · 1350 | 0 · 1025 | 0 · 904 | 0 · 941 (676) |
| `fill_ms` `interstitial` / `rwi` / `opened` | 3103,5 / 3878,7, 1743 / 2377,7, 2417,5 / **2933,9** (n = 2; raw > p90 2879, PASS ×1,5, D35-230) | chưa đo (vắng) | chưa ghi | chưa ghi | chưa ghi | chưa ghi |
| `fill_ms` `banner` | n = 1: 1623 (info; A/B cần n ≥ 10) | chưa đo (vắng) | chưa ghi | chưa ghi | chưa ghi | chưa ghi |
| Fill rate · fill/request | **1,0 · 1,0** | chưa đo (vắng) | 1,0 · 1,0 (39/39) | 1,0 · 1,0 (39/39) | 0,95 · 0,95 (39/41) | 0,95 · 0,95 (39/41) (2.2.0: 0,93 · 0,65) |
| Show latency `interstitial` | **143** (≤ 200) (164) | chưa đo (vắng) | 267 | 170 | 183 | 185 |
| PSS settle / sau show, KB | 217611 / 249953 (172491 / 234017) | chưa đo (vắng) | 163232 / 181031 | 166332 / 178807 | 121733 / 151580 | 113518 / 126192 (111167 / 133963) |
| Ngưỡng | **hard** PASS (×1,5) | tạm | info | info | info | info |

Ghi chú: (1) Redmi: `perf_gate` RESULT PASS, FATAL/ANR/native 0, by-sdk 0 ×5, tải host 6,18 → 4,33 (≤ 8); `perf_report` thô RESULT FAIL chỉ vì `fill_ms.opened` p90 2933,9 > 2879 với n = 2 (phụ thuộc mạng), trong ×1,5 = 4318 nên PASS (×1,5), raw FAIL n = 2 (D35-230). (2) PSS Redmi tăng so với 2.2.0 vì SDK nạp sẵn 5 slot sau consent (PRELOAD); thông tin. (3) emu37 và emu33 boot khi host bận (tải 8–21, boot lần 3 "boot anyway"), tải lúc đo 13 và 8–15 > 8: số timing INVALID, chỉ thông tin (chức năng vẫn hợp lệ); emu28 và emu26 tải 3,4–6 hợp lệ. (4) Ô `fill_ms` emulator chưa ghi trong báo cáo E-emu (chỉ `fill_rate`, `show_ms`). (5) Samsung: máy vắng. API 24: chưa đo (không có image, O11). SQL parity 13 view và `stack_load`: chưa đo (Docker tắt). CI trên HEAD cuối và `release.yml`: bước P.

### Số đo 2.4.0 (Task R: R0 = `18fbaf9`; E1 @`18fbaf9`; nextgen đóng băng lại ở E2b @`48c6aef`; nguồn `v240/metrics-240.md`, `v240/e1-report.md`, `v240/r0-report.md`; số cuối sau FIX-GATE 5: Samsung `apk240c` công cụ `0d83b7f`, 5570 theo E-emu 2d)

Cột `nextgen` là artifact `appplugin-nextgen` (GMA Next-Gen `ads-mobile-sdk` 1.5.0); cột `legacy` là `appplugin`/`-game`/`-max` như 2.3.0.

| Chỉ số | legacy (utility/game) | nextgen |
|---|---|---|
| Unit ×3 (#1, #43) | E = 2090 + 39 − 9 = **2120** (root 2010 + game 110; công thức E1, D24-240); đo ×3 @`18fbaf9`: 2120 mỗi lượt, 0 fail/err/skip, 0 `@Ignore` thật (metrics #1) | `En` **1686** ×1 @`b02b871` (F3: `testDebugUnitTest` BUILD SUCCESSFUL, XML tests=1686 failures=0 errors=0 skipped=0; shared sinh 1582 + `src/test` 104); E1 đếm 1683 ×3 @`18fbaf9`, Δ +3 do test FIX-OOM (f3-report.md:4-7) |
| API (#2, #44) | dump root **5806** / game **389** Δ 0 vs `v2.3.0` (`--check` ×2; diff rỗng); façade ×2 vs `82c22cd` PASS (root + game, core internal PASS) (metrics #2, #3) | dump **6113** (×2; không đổi ở E2b); parity PASS (legacy 444 khối, nextgen 456 khối, **431** y hệt, 25 khác biệt internal) |
| H9 (#6) | **71,8 %** (224/312) ≥ 70 | — |
| Shell (#5) | runner **405**/405 (`device_gate_allowlist_test.sh`; `dg_ab` 14/14), guard 128, lane_pack 14, public_types 12, perf 16, license 11, lanelock 9, 0 FAIL | (chung runner) |
| Release gate (#7, #45) | utility/game/licbad PASS @R0 (utility `absent-class sdk=0 allow=52`, 461 lớp đổi tên 98 %); AAR minSdk 24 | PASS, `absent-class sdk=0 allow=55` (allowlist D23-240), 471 lớp đổi tên 98 %; POM ×4 PASS (Q6 0 `play-services-ads`); `consumer_check.sh` combo 1–2 PASS, combo 3 báo trùng |
| Size (#8, #46) | utility download min 7 676 594 / max **7 732 507** B (+1 062 B vs 2.3.0, info), aab 14 100 660; ≤ 26 214 400 | @R0: min 7 543 830 / max 7 600 817 B; **@E2b `48c6aef`: min 7 543 772 / max 7 600 903 B**, aab 13 669 314, aab không trần (r0-report E2b) |
| AAR | `appplugin` 2 141 556 B, `appplugin-game` 81 187 B, `appplugin-max` 1 287 B (R0) | `appplugin-nextgen` **2 176 548 B** (E2b `48c6aef`, sha256 `a739c544…`; R0: 2 176 433 B; minSdk 24; 0 tham chiếu `com/google/android/gms/ads/`) |
| Lint (#20) | `lintDebug` NewApi **0** ×3 (root Errors 0 / Warnings 91; game 0/11); lượt 1 thật, 2–3 một phần UP-TO-DATE; API 24 chưa đo (O11) (metrics #20) | NewApi **0** ×3 (nextgen 0/46) |
| Gate thiết bị (#9, #47) | PASS (utility, `18fbaf9`): emu37 174 s, emu33 186 s, emu28 122 s, emu26 132 s + Samsung A25; FATAL/ANR/native 0, R8 0, `license: VALID` 1/process, `policy: off` 1/process, preload 3 `load` + 3 `skip (loading)`/process (D33); `initPlugin` cold1/cold2 emu37 126/122, emu33 105/39, emu28 53/55, emu26 153/107 ms (metrics #9) | @`18fbaf9`: emu37 PASS (177 s), emu26 PASS (126 s), Samsung PASS (process non-EEA: `gma: init done` 1, `ready` 1/process, `init failed` 0); **emu28 INVALID ×2 (billing unavailable) + `NoClassDefFoundError PackageManager$OnChecksumsReadyListener` từ `am3` của `ads-mobile-sdk` 1.5.0 (kiểu API 31): API 26 không có dòng lỗi, API 28 8 dòng không crash (quảng cáo vẫn chạy), API 33/37 không, API 29–32 chưa đo; phán D27/D28 = FAIL do SDK Google, preview, không đặt sàn minSdk** (metrics #47). Samsung A25 với APK E2b (`apk240b`, công cụ `b02b871`): nextgen gate PASS, banners PASS (`adview` 1/1), `--resume` PASS, consent PASS (e-real-report "Phần A'/B"). 5570 `apk240b` @`48c6aef`: #47 gate PASS (231 s, `gma` 1/1 mỗi process, `init failed` 0, `queue full` 0) (e-emu-report "Phần 2"). Emu28 nextgen trên APK E2b: chưa đo lại |
| A/B telemetry (#48), Full2 (#50), rwi/banners/NtCl (#49) | — | A/B `ab_compare.py` PASS T1–T7 trên emu37, emu28, emu26 và 5570 (3 cặp), MT lượt đầu; rwi chặng 1–3 PASS trên emu37 (440 s); banners nextgen PASS theo mục tiêu (show 6, ntcl_ok 1, banner `ad_request`/`ad_fill`/`ad_show` 1/1/1; bộ đếm `adview` đã sửa ở FIX-GATE, D25/D27, luật 31); Full2 emu37 PASS 3/3 cả hai stack; emu33/28/26 chưa đo (no-fill `full_2_default` ở cả hai stack, D27; RESULT FAIL của cổng, không phải SDK); Samsung nextgen **FAIL do SDK Google** (`OutOfMemoryError` `ads_mobile_sdk.r60`, D26; Legacy 3/3 PASS). 5570 `apk240b` @`48c6aef` (E-emu Phần 2, E-ab Delta): #49 rwi PASS (482 s; công cụ `18fbaf9` + `apk240b`; công cụ sau FIX-GATE 1–3 INVALID(ui) trên 5570, đo lại ở phần 2b với `7bd82fd`) + banners PASS (`adview` 1/1, `adview_ntcl` 1); #50 PASS 3/3 `show_full2 … 1/1`, `RESULT: PASS (kèm Full2)`; #48 một cặp `ab_compare` PASS có ghi chú (T1–T5, T7 PASS, T6 INFO; `ad_no_fill` 6/0 một bên = info D23); **bằng chứng D26: `ad_request full_2_default` A 4 / B 4, 3 s trước mỗi lần hiện Full2 A [0,0,0] / B [0,0,0]** (apk240 trước sửa: B 6 và 5 với [1,1,0]). **Samsung A25 `apk240c` (công cụ `0d83b7f`, FIX-GATE 5; e-real-report "Phần C (lại)")**: 4/4 `--measure` PASS kèm Full2 (legacy A1, A2; nextgen B1, B2), chặng gap rwi `drive=intent action=show_rwi` rồi `show refused: policy_min_gap` ở cả 4 `-gap.log`, Full2 ×3 `full2=1 (cần 1/1)` 4/4, FATAL 0, 0 `unbound`, 0 OOM trong các lượt này; #48 hai cặp `ab_compare` RESULT PASS T1–T7 (`ad_request` 68/68, `ad_fill` 68/67, `ad_no_fill` 0/1 info, `ad_show` 18/18, `ad_revenue` 20/20) (sink `ereal/sinksC2/{A1,A2,B1,B2}`, log `t240-r-R5CX10P9EKZ-c1-*`); **D26 trên Samsung: `full_2_default` `ad_request` = 3 ở mỗi lượt A1/B1/A2/B2 (`ad_show` 3), 0 request trong 3 s trước mỗi lần hiện Full2**. Lưu ý D26 giữ nguyên: nextgen 2.4.0 vẫn là preview, OOM `ads_mobile_sdk.r60` phụ thuộc creative (SDK Google), các lượt này không gặp. Chặng gap rwi INVALID(ui)/FAIL trước FIX-GATE 5 là lỗi công cụ (D30/D32, rwigap-analysis, gapintent-analysis), không phải SDK. **5570 `apk240c` (công cụ `0d83b7f`; e-emu-report "Phần 2d")**: `--measure` legacy PASS kèm Full2 (504 s) và nextgen PASS kèm Full2 (499 s), cả hai: `rwi gap: policy_min_gap=1 ad_show rwi=0`, `drive=intent action=show_rwi` ×1 rồi `show refused: policy_min_gap` ×1, chặng 1 1/1/1/1, skip 1/0, Full2 ×3 `full2=1`, FATAL/ANR/native 0, chạm cao nhất y 2675/2742 (< 2848, ngoài dải cử chỉ), 0 `startRecentsAnimation`, 0 focus NotificationShade; một cặp A/B RESULT PASS (T1–T5, T7 PASS, T6 INFO; `ad_request` 38/39, `ad_fill` 30/39, `ad_no_fill` 6/0 info); **D26 trên 5570: `full_2_default` `ad_request` A 4 (gồm một preload no-fill đầu, 3 sau đó) / B 3, 3 s trước mỗi lần hiện Full2 A [0,0,0] / B [0,0,0], `ad_show` 3/3**; banners nextgen không chạy lại trên `apk240c` (kết quả E2b PASS giữ) |
| Preload / resume (#41, #53) | #41 PASS theo D33: mỗi slot một `load` hoặc một `skip (loading)` có placement (emu37/33/28/26 + Samsung non-EEA); process EEA của gate = 0 dòng `preload:` (quan sát). #53 emu37 PASS (85 s), emu33 PASS (88 s): `resume_open ON: shows=1`, `OFF: shows=0`; Samsung `--resume` legacy PASS với công cụ có dải dự phòng FIX-GATE 2 (chạm tâm dải `540 2189`, `ON=1 OFF=0`) | #53 emu37 PASS (83 s) và 5570 `apk240b` PASS (`ON=1 OFF=0`); Samsung nextgen `--resume` PASS |
| IAP + consent (#13, #14) | `--iap` 12/12 trên 4 emulator (37/33/28/26) + Samsung lượt 04:22; Redmi chỉ có log các bản trước (t180/t190/t230), không đo ở 2.4.0; Samsung các lượt sau (4 lượt 06:48–12:35) `grace` 11/12 = **INVALID(env)** (D29): từ khoảng 06:48 Samsung chặn mạng nền của app demo ~5,5 s sau HOME (`Destroyed live tcp sockets`, DNS `isBlocked=true`), `session_end` đến sink sau fg1 nên nhãn GRACE áp giữa fg1 và fg2; SDK đúng thiết kế, backend 200 GRACE; #13 PASS giữ theo các lượt trên (4 emulator + Samsung 04:22) (iapgrace-analysis.md). Consent Samsung PASS | nextgen: cùng phép đo, chưa tách riêng |
| Backend (#16, #36) | 383 passed + 1 skipped (383 + 0 so với công thức); parity 13 view + `stack_load`: chưa đo (Docker tắt) | — |
| PERF (#23–#26, #51) | `perf_gate` cold n = 5, `initplugin` med/p90 (info trên emulator, host tải 3–13): emu37 35/54,2; emu33 52/92; emu28 54/86,6; emu26 100/155 (hard med ≤ 125 p90 ≤ 185 OK); Samsung 23/35,6 (hard med ≤ 29 OK); `fill_rate` 1,0 ở 4 emulator; chi tiết và PSS ở metrics #23–#26 và bảng "Hiệu năng (2.4.0)" bên dưới | Samsung nextgen @`18fbaf9`: lượt 1 `initplugin` med 33 / p90 44 (> hard 29, tải 3→6) FAIL, chạy lại med 22 nhưng INVALID (meminfo); **Samsung nextgen (`apk240b`, công cụ `b02b871`) PASS: `initplugin` med 23 / p90 28,6** (e-real-report "Phần A'/B"), 0 OOM ở các lượt đó (OOM D26 là phụ thuộc creative, không hết); chỉ `initplugin` được ghi cho nextgen; bảng đầy đủ legacy ở "Hiệu năng (2.4.0)" bên dưới |
| Carry (#54) | mọi mục "làm" của `minors-230` có commit land (CARRY-T `c21d3fc`, CARRY-ADS `c6cfa1a`, CARRY-POL `1d736ec`, DEMO-RES `7b87e00`, T0a `5b93677`, IG3 `7db05bb`) | — |
| Chưa đo | release local `tools/release_local.sh` (bước P, D33); Samsung/Redmi theo MT; API 24 (O11); Unity Activity (không driver, #52) | |

### Hiệu năng (2.4.0): Task R, bảng `perf_report` theo máy (APK utility `57530f71…`; nguồn `metrics-240.md` #23–#26, #51)

**Cách đo:** như 2.3.0 (`perf_gate.sh --runs 5`, `perf_report.py`); ngưỡng `hard` chỉ trên máy thật; emulator và PSS là thông tin (host tải 3–13). Redmi vắng cả ngày, không đo ở 2.4.0. Số chỉ gồm những gì metrics-240 ghi; ô không có số = không ghi.

| Chỉ số | Samsung A25 (API 36) | emu37 | emu33 | emu28 | emu26 |
|---|---|---|---|---|---|
| `initPlugin` total med / p90 | **23 / 35,6** (hard med ≤ 29 OK) | 35 / 54,2 | 52 / 92 (info vượt) | 54 / 86,6 (info vượt) | 100 / 155 (OK; hard med ≤ 125 p90 ≤ 185) |
| `onCreate` med / p90 | 24 / 39,8 (≤ 30/40 OK) | 35 / 62 | 63 / 95,8 | 75 / 114,6 | 141 / 171 |
| `total_ms` cold med / p90 | 353 / 378 (≤ 880/960) | 875 / 978,6 | 839 / 1099 | 955 / 993 | 776 / 901 |
| `prefs_waited`, by-sdk | 0, 0 | 0, 0 | 0, 0 | 0, 0 | 0, 0 |
| first-run (`pm clear`) total · `initPlugin` | 460 · 23 (`first_by_sdk` 0) | 833 · 43 | 946 · 78 | 835 · 57 | 882 · 199 |
| Fill rate | 1,0 (n = 39) | 1,0 (39/39) | 1,0 (37/37) | 1,0 (39/39) | 1,0 (39/39) |
| `fill_ms` interstitial / rwi (med / p90) | 2552 / 2877 · 2399 / 2766 | chưa ghi | chưa ghi | chưa ghi | chưa ghi |
| Show latency `interstitial` | 168 (≤ 210) | 203 | 208 | 226 | 153 |
| PSS settle / sau show, KB | 148790 / 202470 | 166280 / 180607 | 152058 / 183092 | 123172 / 161572 | 111911 / 138638 |
| Ngưỡng | **hard** PASS (luật 17: `opened` n = 2, banner n = 1 là n nhỏ) | info | info | info | info |

- **Nextgen (Samsung, `apk240b`, công cụ `b02b871`, #51):** `initplugin` med 23 / p90 28,6, PASS; lượt trước FIX-OOM (`18fbaf9`) med 33 / p90 44 FAIL lúc host tải 3→6 rồi INVALID (meminfo). Không có bảng đầy đủ cho nextgen.
- Nguồn: metrics-240 #23 (initplugin, oncreate, total), #24 (first-run), #25 (fill, show_ms), #26 (PSS), #51.

### Số đo 2.4.1 (bản vá HOSTFIX D36-240; R-light @8cf6849, 2026-10-07; nguồn `v241/metrics-241.md`, `v241/r-light-report.md`; log `t241-r-*.log`)

| Chỉ số | Ngưỡng PASS | Số đo |
|---|---|---|
| Unit ×1 ×3 module (#1) | root + game = E; nextgen = En; 0 fail/skip | root 2019 + game 110 = 2129, nextgen `En` 1695; 0 fail/skip |
| Dump `--check` ×3 Δ 0 (#2) | 5806 / 389 / 6113 unchanged vs `v2.4.0` | dump root 5806 / game 389 / nextgen 6113, Δ 0 vs `v2.4.0` |
| Façade + parity + public types (#3) | PASS; parity 431 y hệt; H9 ≥ 70 % | façade PASS; parity 431 y hệt / 25 internal; H9 71,8 % |
| Release gate (#4) | utility + nextgen PASS; `absent-class sdk=0` | utility PASS / nextgen PASS, `absent-class sdk=0`; APK utility sha256 `f78921aa…` |
| Bundle size (#5) | ≤ 26 214 400 B | utility 7 732 401 B / nextgen 7 600 616 B (≤ 26 214 400) |
| Gate emu37 utility (#6) | FATAL/ANR/native 0, R8 0, `license: VALID` 1/process | PASS: FATAL/ANR/native 0, R8 0, `license: VALID` 1/process |
| IM debug trên máy thật, 2.4.1 từ `build/repo` (`-Pappplugin.localRepo`), không workaround (#7) | `queue Full2 until host resume` rồi `showFull2 call show` ≤ 1 s; 0 `discarding unused prepared dialog` | PASS trên Redmi `65c9b673`, IM debug `com.pro.invoice.maker.ai.debug`: `queue Full2 until host resume` 09:49:16.459 → `showFull2 call show` 09:49:16.543 (84 ms) → `onAdImpression` native_full 09:49:16.714; 0 `discarding unused prepared dialog` trước show (2 discard sau show = dọn dialog chuẩn bị dư, theo thiết kế); FATAL 0 |
| IM release 1 lần save (#8, info) | không hồi quy | chưa đo (OWNER 20: unit thật, máy chưa đăng ký test device AdMob) |

Ghi chú: hàng #7 lần đầu ở session 2 bị dialog POST_NOTIFICATIONS của app che MainActivity, nên đo lại ở session 3 (hành vi "Full2 chờ resume thật" giữ nguyên).

### Số đo 2.4.2 (Task R @3131631, lane E-R Sonnet, 2026-10-07; nguồn `v242/metrics-242.md`; log `t242-r-*.log`)

| Chỉ số | Ngưỡng PASS | Số đo |
|---|---|---|
| 1. Unit ×3 module | root + game = `E` (= 2129 + Δ `@Test`); nextgen = `En` (= 1695 + Δ); 0 fail/skip, 0 `@Ignore` mới | PASS: root 2075 + game 110 = 2185 (= E), nextgen 1751 (= En), 0 fail / 0 error / 0 skip; @Ignore mới 0 (2 @Ignore còn trong SdkFileSizeGateTest/AdsHelperSizeGateTest, y như v2.4.1); SdkFileSizeGate + AdsHelperSizeGate xanh (nằm trong số đếm) (log `t242-r-full.log`) |
| 2. Dump ×3 chỉ thêm | `--check` unchanged ×3; `git diff v2.4.1 -- api/` dòng `-` = 0; game 389; `+` chỉ ở khối Hợp đồng A–D | PASS: --check x3 unchanged (5862 / 389 / 6169 dòng), `-` = 0 (MINUS_TOTAL=0), game 389 / +0, root +56, nextgen +56 (đối chiếu v2.4.1); phần `+` = đúng khối Hợp đồng A–D (MonetizationSdk.clearLocalData, AdsHelper.isPlacementEnabled + 2 overload fixedMoment + 2 $default, FullScreenAdPolicy.shouldChainFull2AfterInterstitial, PlacementSource, PlacementSwitchState, InstallReferrerHelper.captureAfterConsent + internal, BrainStorage.forgetPlayerId, LocalDataReset, DurableEvents.clearAll, SegmentHooks.localGuardrailSeconds, các thành viên `$appplugin`) (log `t242-r-api.log`) |
| 3. Façade + parity + H9 | façade ×2, parity PASS; `public_types.sh --check --min-pct 70` | PASS: façade root + game PASS, parity PASS (435 y hệt / 25 khác biệt internal), H9 72.1% >= 70. Ghi chú: BASELINE_ROOT mặc định = 8f9539c (commit dump 2.4.2) nên façade root không tự đo phần thêm so với v2.4.1; phần đó do hàng 2 (`-` = 0) phủ (log `t242-r-facade.log`) |
| 4. Release gate | utility + nextgen PASS; `absent-class sdk=0` | PASS / PASS: absent-class sdk=0 (allow 52 utility / 55 nextgen), R8 renamed 98% (465/471) utility; utility apk sha256 ad6f52cea8b9724526fcc77a8a04011a856e0e76b1b077304affb21e4e343f31, mapping sha256 d6b89d012edb7d9d63ab35494906fe9a17c562b28a794b1c6f648e51cba03120; nextgen apk sha256 b5b890a7582610374dd8f90dde15672ce27286d0f95da7a80ebf791c6b1506ee (log `t242-r-release-utility.log`) |
| 5. Bundle size | ≤ 26 214 400 B (info Δ vs 7 732 401 / 7 600 616) | PASS: utility max 7742150 B (delta +9749 vs 7732401 [R]), nextgen max 7608893 B (delta +8277 vs 7600616 [R]); both <= 26214400 (log `t242-r-bundle-size.log`) |
| 6. Gate emu37 | FATAL/ANR/native 0, R8 0, `license: VALID` 1/process | PASS (lần 3): FATAL 0, native 0, am_anr 0 (coldstart, seed 1-3, post-monkey, coldstart2), R8 strings 0 (x3), license: VALID 1/process (cold1 pid 5745, cold2 pid 7671), sink +160 dòng, AppsFlyer started n=1. APK utility release đóng băng sha256 ad6f52ce..., emulator-5570 pid 24540. Lần 1 FAIL môi trường (emu_boot rc 3 contention + thiếu venv sink), lần 2 INVALID (package update: GMS tự cập nhật sau boot mới), cả hai không có tín hiệu sản phẩm; lần 3 chờ 270 s sau boot rồi chạy cổng (log `t242-r-emu37.log`) |
| 7. SLOT (máy thật) | `showOpenAd call show` + `onAdImpression` `full_splash` | PASS: slot riêng full_splash rỗng, slot mặc định có quảng cáo: showOpenAd hiện từ slot mặc định (originPl=full_splash qua fallback), FATAL 0. Chạy cold (am force-stop + am start --es drive); đã đóng quảng cáo bằng chạm theo ảnh chụp (log `t242-r-slot.log`) |
| 8. FIXED + INTRO3 (máy thật) | lượt thường `not_allow_show`; lượt cố định impression, `queue Full2 until host resume` rồi `showFull2 call show` (`full_2_intro_3`) ≤ 1 s sau resume | PASS: lượt thường `not_allow_show` (normal show returned=false; ready normal=false fixed=true); lượt cố định show=true + impression (full_intro_3); sau đóng: queue Full2 until host resume -> showFull2 call show cách 76 ms (<= 1 s), full_2_intro_3 origin=full_2_default (slot mặc định của demo, FX9) + onAdImpression native_full; FATAL 0. Driver bật lại full_intro_3 (RC demo đặt -1: before=false after=true) như lm-report; đóng 2 quảng cáo bằng chạm theo ảnh chụp/uiautomator (không bấm mù) (log `t242-r-fixed.log`) |
| 9. SWITCH (máy thật) | tắt: `cf not`, show false, `isPlacementEnabled=false`, giữ sau cold start; clear: bật lại và nạp chạy | PASS theo D10-SC (`sc/ceo-decisions.md`) (chân cold start = bằng chứng JVM): tắt: `loadInterstitialForThisTurn cf not`, show=false, isPlacementEnabled=false; gỡ (switch_clear sau force-stop): enabled=true và load chạy (loaded originPl=full_default). Cold start vẫn false: KHÔNG quan sát được trên máy vì driver gọi parserAdsConfig trực tiếp và lần fetch RC 1,4 s sau dựng lại trạng thái (`reEnabled=[clean_done, full_clean_done]`, prefs không giữ clean_done) — đúng cơ chế D10-SC mô tả; bằng chứng thay: PlacementSwitchTest SW5 'cold start tu ban gop van tat' xanh (10/0/0) trong lần chạy hàng 1. FATAL 0 (log `t242-r-switch.log`) |
| 10. REFERRER (máy thật, khách ấm) | đúng 1 dòng `InstallReferrer captured\|unavailable` sau dòng tầng consent; lần 2: 0; từ chối: 0 | PASS: lần 1 (pm clear + warm-up, force_eea, Consent): đúng 1 dòng `InstallReferrer captured, empty=false` lúc 15:35:10.237, 222 ms SAU dòng tầng consent (15:35:10.015); cold start 2: 0 dòng (tier GRANTED, install_referrer_captured=1); lần 3 (pm clear + warm-up, force_eea, Do not consent): tier=DENIED, 0 dòng trong 30 s. Ghi chú: dòng 'unavailable' thực tế là `Install Referrer unavailable, code=` (Log.w), không thấy trong lần chạy; chạm form bằng toạ độ landscape lấy từ ảnh chụp (Consent 1540,728; Do not consent 936,728), vì máy đang ở ROTATION_90 (log `t242-r-referrer.log`) |
| 11. CLEAR (máy thật) | 0 khoá `seg_`/`install_referrer_url`; khoá giữ còn; `files/appplugin_events` rỗng | PASS theo D10-SC (`sc/ceo-decisions.md`) (mục tiêu = 0 khoá lịch sử người dùng): trước: 57 khoá App_prefer_file (13 khoá seg_) gồm seg_session_timestamps, seg_ad_impression_count, seg_bounce_count, seg_features_used, seg_rewarded_ad_completions, seg_ecpm_bucket... + brain_decision_queue.log 7003 B; sau clearLocalData: 0 khoá lịch sử (seg_* còn đúng 2 khoá do engine nạp lại: seg_first_seen_millis = 1791362031469 = 2026-10-07 15:33:51.469 = đúng lúc xoá, seg_geo_tier), install_referrer_url không có trước/sau (demo cài bằng adb, không có referrer); khoá giữ còn: install_referrer_captured=1, mem_cf_ads_placement(+_rc), cfcurr_*/plmem_* cấu hình, XgameHCM: iap_remove_ads_products, consent_tier, SEQUENCE_ID, brain_player_id; XgameHCM mất 12 khoá phiên (IS_FETCH_FIREBASE, TIME_OPEN, EXPERIMENT_GROUP...); files/appplugin_events rỗng (total 6, 0 file). Theo chữ '0 khoá seg_' thì còn 2 khoá do engine nạp lại, đã được D10-SC xác nhận không FAIL (log `t242-r-clear.log`) |
| 12. SPLASH | chưa đo trên thiết bị (không có driver, #43): JVM SP1–SP3 thay | chưa đo (không driver; JVM SP1–SP3) |
| 13. IM hồi quy | dòng `2.4.1 -> 2.4.2`; BUILD SUCCESSFUL; unit 34/0/0; `git status --short` rỗng trước và sau | PASS: init-script force ăn (`2.4.1 -> 2.4.2` x2), BUILD SUCCESSFUL (:app:testDebugUnitTest :app:assembleDebug, 37 s), unit 34 / 0 fail / 0 error / 0 skip; `git status --short` rỗng trước (0 dòng) và sau (0 dòng); không cần toml fallback; không commit IM. Lệch nhỏ so với plan: -Pappplugin.localRepo trỏ <wt242-r>/build/repo (nơi publishAllPublicationsToLocalCheckRepository ghi 2.4.2), vì /Users/dungdm/appplugin/build/repo của checkout chính chỉ có 2.2.0/2.4.0/2.4.1 (log `t242-r-im.log`) |

### Số đo 2.4.3 (Task R @033a12e, lane R-SDK + E-SPEED Sonnet, 2026-10-09; nguồn `v243/metrics-243.md`; log `t243-r-*.log`, SC `sc/v2/logs/e243-*.log`)

| Chỉ số | Ngưỡng PASS | Số đo |
|---|---|---|
| 1. Unit ×3 module | root + game = `E`, nextgen = `En`; 0 fail/skip, 0 `@Ignore` mới | PASS: root 2103 + game 110 = 2213 (v2.4.2 = 2185, +28), nextgen 1776 (v2.4.2 = 1751, +25); 0 fail / 0 error / 0 skip; `@Ignore` 2 = 2, mới 0; BUILD SUCCESSFUL 5 phút 8 giây (`t243-r-full.log`) |
| 2. Dump ×3 chỉ thêm | `--check` unchanged ×3; `-` = 0; game 389; `+` chỉ khối `LicenseCache` | PASS: unchanged ×3 (5868 / 389 / 6175), `-` = 0, game 389 / +0, root +6, nextgen +6; `+` = đúng khối `com.app.plugin.license.LicenseCache` (INSTANCE, FILE, resolve, clear) (`t243-r-api.log`) |
| 3. Façade + parity + H9 | façade ×2, parity PASS; H9 ≥ 70% | PASS: façade root PASS vs `8f9539c`, game PASS vs `82c22cd`, parity PASS (449 / 461 khối, 436 y hệt, 25 khác biệt internal), H9 316 types, internal 228 = 72,2% (`t243-r-facade.log`) |
| 4. Release gate | utility + nextgen PASS; `absent-class sdk=0` | PASS / PASS: utility `absent-class sdk=0` (allow 52), R8 đổi tên 466/472 (98%), apk sha256 `0c7cde86af1b023b946e9d3527b2f1746f06f6dbc51b82fda5df2651d7a941d4`; nextgen sdk=0 (allow 55), 475/481 (98%), apk sha256 `4d7e61a8c11c0f2bfc2fb3cfbc4b52816dd45551ebdb2488c48dc45126cca3ec` (`t243-r-release-{utility,nextgen}.log`) |
| 5. Bundle size | ≤ 26 214 400 B | PASS: utility max 7 746 409 B (Δ +4259 vs 2.4.2), nextgen max 7 609 351 B (Δ +458 vs 2.4.2) (`t243-r-bundle-size.log`) |
| 6. Gate emu37 | FATAL/ANR/native 0, R8 0; `license: VALID` 1/process; cold1 `license: source=verify` | PASS (gate RESULT PASS, GATE_RC=0) trên APK utility release R8 sha256 `0c7cde86af1b023b946e9d3527b2f1746f06f6dbc51b82fda5df2651d7a941d4` (mapping `bb2f9d8d…`): FATAL 0, native 0, `am_anr` 0 mọi chặng, R8 strings 0 (×3), `license: VALID` đúng 1/process (pid 7099, 7373, 9033), 0 `NoClassDefFound`/`ClassNotFound`/`NoSuchMethod` (0 lớp thiếu trên `com.app.plugin`); preload chờ splash (`preload: wait splash` → `splash done` (settled) → mới `preload … load`, pid 7373 và 9033). `source=`: cold1 (pid 7099) `source=verify ms=351`; chặng cold2 của gate chạy sau `pm clear` nên xoá cache theo thiết kế và in `source=verify ms=29`; cache hit `source=cache ms=103` thấy ở process mở lại không xoá (pid 7373). Không đánh giá thời gian (host load 4,1–9,8; `emu_boot` rc 3 do load1 4,10 > 4, chờ settle 270 s rồi chạy cổng) (`t243-r-emu37.log`) |
| 7. IM hồi quy | `2.4.2 -> 2.4.3`; BUILD SUCCESSFUL; unit như baseline 2.4.2; `git status --short` rỗng trước và sau | PASS: init-script force `appplugin:2.4.2 -> 2.4.3` ×2, `:app:testDebugUnitTest :app:assembleDebug` BUILD SUCCESSFUL 29 s, unit 34 / 0 / 0 / 0 (= baseline 2.4.2), status rỗng trước và sau, 0 sửa file IM (`t243-r-im.log`) |
| 8. LCACHE trúng (SC, Samsung A25) | #1–5 `source=cache` 5/5, ms ≤ 100, `since_request_ms` ≤ 300 | PASS: 5/5 cache, ms 19 / 8 / 27 / 21 / 28, `since_request_ms` 0 / 1 / 1 / 1 / 0 (control 2.4.2: 1008–1033), 0 `source=verify` ở #1–5 |
| 9. Init bắt đầu ngay | init start − starting ≤ 150 ms (≤ 300 theo C3) | PASS: 1 / 0 / 1 / 1 / 1 ms (control 1009–1035); init start trung vị 1370 ms từ `Start proc` (control 2392) |
| 10. FASTSTART-A (cold #0) | init start − `license: VALID` ≤ 60 ms | PASS: −4 ms (`source=verify ms=1595` lúc 3004, VALID 3010, init 3006). Chi phí verify Ed25519 thật trên máy = **1595–1628 ms** (cài sạch hàng 14: 1628 ms), chạy song song với init chứ không cộng sau VALID; phần IPC cert 3–31 ms |
| 11. PREORDER | 0 dòng preload load giữa tryLoad đầu của splash và lúc splash xong, 6/6 | PASS: 6/6 `preload: wait splash` rồi `splash done after 4500 / 3750 / 3500 / 3500 / 3250 / 2750 ms (settled)` (đếm theo nhịp, cận dưới) rồi `interstitial full_default load` sau 7–15 ms; không `(cap)`. Load `full_splash` trung vị 3694 ms vs control 5049 ms (−27%) |
| 12. Tổng (info) | `showOpenAd call show` trung vị ≤ 8000 ms | info, cải thiện rõ: 6620 ms (6367–7180) vs control 2.4.2 8299 ms (−1679 ms, −20%); onimpression 6775 vs 8430. Máy bận hơn lúc control (load 18,9–23,2 vs 18,6–20,9) nên số thật thuận lợi hơn |
| 13. FASTSTART-C A/B | info | info: `ads_cf_admob_init_wait_ms=1` (3 cold): tryLoad splash sớm hơn (2432–2571 vs 2852–3020 ms) nhưng load splash lâu hơn (4198–4488 ms), `showOpenAd` 7067 / 6932 / 6660, trung vị **6932 vs 6620 ms**: không có lợi trên SC (+312 ms, trong nhiễu). Resource **giữ TẮT mặc định và không khuyến nghị bật** |
| 14. First run + cổng (SC) | cổng test device/Facebook/click/FATAL/ANR mọi lượt | PASS: cài sạch, `source=verify ms=1628`, 1 dòng; lần kế `source=cache ms=13`; splash/ngôn ngữ/onboarding như speed-v2 §2; mọi lượt: test-device 5–15 dòng, 0 cảnh báo Facebook, 0 `setTestDeviceIds(Arrays.asList(`, 0 `onAdClicked`, 0 FATAL/ANR |

So sánh với control 2.4.2 (cùng nhánh, cùng máy, ngay trước, người dùng cũ, trung vị): `showOpenAd` 8299 → 6620 ms (−20%); xác minh giấy phép tới khởi tạo mạng ~1019 ms → 8–28 ms nhờ cache (lần đầu hoặc sau xoá vẫn verify 1595–1628 ms trên máy); nạp `full_splash` 5049 → 3694 ms.

Ghi chú SA1 (2.4.3 LCACHE): cache verdict giấy phép nằm ở prefs riêng của app, nên trên máy đã root (hoặc bằng chính mã app) một lần ghi SharedPreferences giả được mục cache hợp lệ mà **không cần token hợp lệ nào** (khoá cache chỉ băm dữ liệu công khai: token tuỳ ý, `applicationId`, chứng chỉ, khoá công khai trong AAR, bản SDK). Năng lực này ngang reflection (licensee kiểm soát toàn bộ app và không có bí mật nào trên máy để MAC) và được chấp nhận, không đổi kết luận của SA1. Bên thứ ba không root không ghi được; Auto Backup chỉ khôi phục cho cùng chữ ký và cùng khoá cache.

### Số đo 2.5.1
Task R @`09dabbb` (lane R-SDK251; nguồn `v251/metrics-251.md`; log `t251-r-*.log`). Mốc đối chứng 2.5.0: root 2185, game 110, nextgen 1855; dump 5949 / 389 / 6256.
| # | Chỉ số | Ngưỡng PASS | Kết quả |
|---|---|---|---|
| 1 | Unit ×3 module | root + game = `E`, nextgen = `En`; 0 fail/error/skip, `@Ignore` 2 = 2 | PASS: root 2196 + game 110 = 2306 (2.5.0 = 2295, +11), nextgen 1860 (2.5.0 = 1855, +5); 0 fail / 0 error / 0 skip; `@Ignore` 2 = 2 (`t251-r-full.log`) |
| 2 | Dump ×3 | unchanged ×3 (root 5958, game 389, nextgen 6265); `-` = 0; `+` chỉ khối `PlacementPaidEvent` + 1 `EventNames …$appplugin`; game 0 | PASS: unchanged ×3 (5958 / 389 / 6265, so với 5949 / 389 / 6256: +9 / 0 / +9 = khối `PlacementPaidEvent` + `getAD_PLACEMENT_IMPRESSION$appplugin`); dòng `-` = 0 (`t251-r-api.log`) |
| 3 | Façade + parity + H9 | façade PASS vs `d03bdb3` / `82c22cd`; parity PASS gọi thẳng (rc 0); H9 ≥ 70 % | PASS: façade root vs `d03bdb3` PASS ×2, game vs `82c22cd` PASS; parity PASS rc 0 gọi thẳng (454 / 466 khối, 441 y hệt, 25 khác biệt internal); H9 72,6 % (`t251-r-facade.log`) |
| 4 | Release gate | utility + nextgen PASS; `absent-class sdk=0` | PASS / PASS: utility `sdk=0` (allow 52), nextgen `sdk=0` (allow 55); APK utility đóng băng u.apk sha256 `93e6167c485360cc4cfe1cb35c73d342873e1c06582416f4d3619af451e46eb4`, u-mapping.txt sha256 `5c55c4ae8f4e486a77032d9e0a791d671a72c51319aa5c4124fc572224985fa5` (`t251-r-release-{utility,nextgen}.log`) |
| 5 | Bundle size | ≤ 26 214 400 B | PASS: download max utility 7 754 336 B (Δ +686 vs 2.5.0), nextgen 7 618 033 B (Δ +1 644); ngưỡng 26 214 400 (`t251-r-bundle-size.log`) |
| 6 | Lint | Errors 0, `NewApi` 0; Warnings ghi | PASS: Errors 0, `NewApi` 0; Warnings root 98, nextgen 46 (`t251-r-lint.log`) |
| 7 | Gate emu37 (điều kiện tag) | host load ≤ 4 trước boot; FATAL/ANR/native 0, R8 0; `license: VALID` 1/process | PASS (RESULT PASS, GATE_RC=0, 2026-10-11 20:40, try 57, load1 3,48 trước boot) trên APK đóng băng hàng 4 sha256 `93e6167c485360cc4cfe1cb35c73d342873e1c06582416f4d3619af451e46eb4` (mapping `5c55c4ae…`): FATAL 0, native 0, `am_anr` 0 ở cold start, monkey ×3 (exit 0 ×3), post-monkey, cold start 2; R8 strings 0 (×3); `license: VALID` đúng 1/process (pid 5643, 5927, 7677), cold1 `source=verify ms=77`; 0 `NoClassDefFound`/`ClassNotFound`/`NoSuchMethodError`; F1b Đ1: 0 `ads_cf_native_no_reload`, 0 `ads_cf_placement_event read failed`, 0 `ad_placement_impression`; sink +154. Trước đó: lượt 15:38 ngày 10 INVALID (billing unavailable, GATE_RC=4); lượt 17:06 FAIL môi trường (monkey seed 3 timeout ở host load 17–19 để lại SystemUI, app không được start sau `pm clear`, 0 crash) — không nới tiêu chí; còn lại rc 3 contention (`/Users/dungdm/appplugin/DOC/testing/t251-r-emu37{,-try2,-try3}.log`, `device-gate-emulator-5570-20261011-203956.log`) |
| 8 | IM hồi quy | `2.5.0 -> 2.5.1`; BUILD SUCCESSFUL; unit như baseline; status rỗng trước và sau | PASS: IM head `ef9667b`, `appplugin:2.4.4 -> 2.5.1`, BUILD SUCCESSFUL, unit 34 / 0 / 0 / 0, `git status` rỗng trước và sau (`t251-r-im.log`) |
| 9 | SC build từ localRepo | `appplugin:2.5.1` từ localRepo; BUILD SUCCESSFUL; 0 fail/error | PASS: SC `wt-v2-251` @`1142ba8`, `appplugin:2.5.1` từ localRepo, BUILD SUCCESSFUL, 704 test, 0 fail / error / skip, status rỗng (`t251-r-sc.log`) |
| 10 | Placement trên máy (SC, Samsung) | mọi `origin=app,name=ad_impression` có `ad_placement=`; chỉ có nghĩa khi SC chưa khai cờ liên kết; chưa đo = 0 impression sau 3 lượt | PASS (DV5, 2026-10-11, SC `v2-251` @1142ba8 + 2.5.1, cài sạch + 2 cold): 18 `origin=app,name=ad_impression`, 0 thiếu `ad_placement`; mọi giá trị khớp một dòng show cùng lượt; 18 `origin=am` (info); 0 `ad_placement_impression`; test device có; 0 onAdClicked/FATAL/ANR/Meta (`sc/v2/logs/e251-{firstrun,cold1,cold2}.log`) |

### Số đo 2.5.0 (Task R @409704e (mã đo tại `2f38d19`, docs-only sau đó), lane R-SDK250 + E-SPEED Sonnet, 2026-10-09; nguồn `v250/metrics-250.md`, `v250/f1b.md`; log `t250-r-*.log`, SC `sc/v2/logs/e250-*.log`)

| Chỉ số | Ngưỡng PASS | Số đo |
|---|---|---|
| 1. Unit ×3 module | root + game = `E`, nextgen = `En`; 0 fail/skip, 0 `@Ignore` mới | PASS: root 2185 + game 110 = 2295 (2.4.4 = 2247, +48), nextgen 1855 (2.4.4 = 1808, +47); 0 fail / 0 error / 0 skip (XML); `@Ignore` 2 = 2, mới 0; `--rerun-tasks`, BUILD SUCCESSFUL (`t250-r-full.log`) |
| 2. Dump ×3 | `--check` unchanged ×3; `-` = 0 ngoài `UnitGate*`; game 389; `+` đúng ABI | PASS: unchanged ×3 (5949 / 389 / 6256); root và nextgen mỗi file `+` 22 (1 public `AdsHelper.setAutoShowOpenAdOnForeground`, 3 `$appplugin`, `AdsCfOverrides` 7, `UnitGate*` 11), `-` 1 (ctor internal `UnitGate$Waiter`, ngoại lệ plan, F1b §3.5), game +0/−0 (`t250-r-api.log`) |
| 3. Façade + parity + H9 | façade ×2, parity PASS; H9 ≥ 70% | PASS: façade root PASS vs `d03bdb3`, game PASS vs `82c22cd`, parity PASS (453 / 465 khối, 440 y hệt, 25 khác biệt internal; chạy bằng `bash`, script thiếu bit thực thi, sửa 2.5.1), H9 232 / 320 = 72,5% (`t250-r-facade.log`) |
| 4. Release gate | utility + nextgen PASS; `absent-class sdk=0` | PASS / PASS: utility `sdk=0` (allow 52, không đổi), R8 đổi tên 475/481 (98%), apk sha256 `dc6edbb0bd8ee16910daf6d7de789674e51263fcd2013bb0bc0082563c6d863e`, mapping `4f82fc936311e9b36eedff4dde4d1a272150b6965aff74756c162eec603dd306`; nextgen `sdk=0` (allow 55, không đổi), 486/492 (98%), apk `7284f640e54e766c6ebbbfc68d5c31283c144b686a59e2ff3b3a307fdd499531`, mapping `6f7fab5869bd934516209ed981b5666fee11d8889b6968b515f97dbd85a38ae6` (`t250-r-release-{utility,nextgen}.log`) |
| 5. Bundle size | ≤ 26 214 400 B | PASS: utility max 7 753 650 B (Δ +3 886 vs 2.4.4), nextgen max 7 616 389 B (Δ +2 163) (`t250-r-bundle-size.log`) |
| 6. Gate emu37 | host load ≤ 4 trước boot; FATAL/ANR/native 0, R8 0; `license: VALID` 1/process | PASS (gate RESULT PASS, GATE_RC=0, 2026-10-10 04:12, load1 2,52 trước boot, 3,88 sau settle 213 s) trên APK utility release R8 đóng băng sha256 `dc6edbb0bd8ee16910daf6d7de789674e51263fcd2013bb0bc0082563c6d863e` (mapping `4f82fc93…`), dựng tại `2f38d19`: FATAL 0, native 0, `am_anr` 0 ở cold start, 3 lượt monkey, post-monkey, cold start 2; R8 strings 0 (×3); `license: VALID` đúng 1/process (pid 5730, 7699), cold1 `source=verify ms=136`; 0 `NoClassDefFound`/`ClassNotFound`/`NoSuchMethodError`; sink +249 dòng. Trước đó: 64 lượt chưa boot (load > 4) và 41 lượt boot không hợp lệ do host bận (emu_boot rc 3, không tính); 1 lượt FAIL môi trường lúc 03:36 (sink không chạy vì worktree t250-r không có `backend/.venv`; app chưa cài), chạy lại với `BACKEND_PY` của repo chính (`t250-r-emu37-try{2..6}.log`, `device-gate-emulator-5570-20261010-041234.log`) |
| 7. IM hồi quy | `2.4.x -> 2.5.0`; BUILD SUCCESSFUL; unit như baseline; status rỗng trước và sau | PASS: IM head `ef9667b` (pin 2.4.4), `appplugin:2.4.4 -> 2.5.0` ×2, BUILD SUCCESSFUL 32 s, unit 34 / 0 / 0 / 0 (6 file) = baseline; `git status --short` rỗng trước và sau; không sửa file IM (`t250-r-im.log`) |
| 8. INFLIGHT: 0 request chồng cùng unit (SC) | overlap/err1/throttle/after_fail<2s = 0 | PASS: overlap 0 / err1 0 / throttle 0 / after_fail 0 ở 9/9 lượt (control + 2.5.0 cold 1–5 + first run); `merged` 1/cold, 2 ở first run |
| 9. Không làm chậm luồng chính | trung vị `showOpenAd` ≤ control + 300 ms | PASS: `Start proc` → `showOpenAd call show`: control trung vị 5 134 ms (11 110 / 5 134 / 5 043), 2.5.0 trung vị 5 128 ms (5 274 / 5 405 / 5 020 / 5 128 / 4 822) |
| 10. PRIO: native màn trước open ad (SC `f4bd0c5`) | `nt_splash` hiện trước open ad ở first run; control tái hiện F-Q1 | PASS (hàng đợi): first run `load yields unit to nt_splash: full_2_extra_clean` 16:31:21.810, request đầu trên `6781010236` = `nt_splash`, `nt_splash showNative` 16:31:28.661 < `showOpenAd` 16:31:30.201; control cold1 `merged nt_splash` 16:24:07.404, 0 `nt_splash show` (F-Q1 tái hiện). **F-Q1 first run đã sửa phía SDK**, chứng trên chờ init (consent không hiện, xem setup); chờ consent cùng đường `holdUntilReady` |
| 11. Chờ unit (info) | cột `unit gate: wait cap` mỗi lượt (C2) | info: `wait cap` 0 mọi lượt; `full_2_default released after` 2 031 / 2 739 / 961 / 2 948 / 2 538 ms, control 8 675 (nt_splash) / 1 929 / 2 010; first run `full_2_extra_clean` 4 231, `nt_language` 3 567 |
| 12. Tổng (info) | `splash_exit` → `nt_language` show | info (một phần): control cold1 5 165 ms, 2.5.0 first run 4 403 ms (−762); không trung vị (máy không chạm qua splash/language) |
| 13. ASPECT + INITCB | `ads native media ratio: ANY` đúng 1/process; gap `complete after` → `tryLoad` đầu trung vị ≤ 20 ms | PASS: `ANY` 1/process ×10; gap 0 / 1 / 0 / 1 / 0 (trung vị 0; control 0 / 2 / 0) |
| 14. First run + cổng (SC) | test device, Facebook, click, FATAL, ANR mọi lượt | PASS AdMob-only: `flagTest = 1` cả hai APK (smoke 90351 0 `fb_` ⇒ bất định); 0 `fb_` request, 0 impression Meta, 0 `onAdClicked`, 0 FATAL/ANR, test-device ≥ 5/lượt, 0 lần chạm (1 tapq NOTFOUND, không bắn); `When testing your app with Facebook's ad units` 1/lượt = Meta in hash lúc init |
| 15. RCSW / LAM1 / LAM2 trên máy | — | không đo theo plan (RC cấm ghi): JVM RC1–RC6, RC2b, L1–L5, O1–O3 thay |

Setup: control = SC `f4bd0c5` + 2.4.4 (`t244-r/build/repo`); 2.5.0 = `v2-250` @`7f59f89` chỉ đổi pin. `ump_debug_eea = 0` tạm ở cả hai (không form UMP, không chạm) ⇒ số hàng 9 và 12 **không so với hàng 2.4.4 lịch sử**, chỉ với control cùng phiên. `strings.xml` khôi phục, `diff --quiet` rc 0 ×2. Host load 9–30 suốt phiên SC (chỉ ảnh hưởng số tuyệt đối, control cùng điều kiện). APK control `cae880de…d759`, 2.5.0 `9456b3b6…114f`; shots `shots/e250/`. Hàng "5 PRIO" và "6 RCSW/LAM1/LAM2 trên máy" của khung cũ gộp vào hàng 10 và 15. Sau E5/E5b: `git diff --name-only 2f38d19 HEAD` chỉ `.md`.

### Số đo 2.4.4 (Task R @aca3a70 (mã đo tại `e84c4d9`, docs-only sau đó), lane R-SDK + E-SPEED Sonnet, 2026-10-09; nguồn `v244/metrics-244.md`; log `t244-r-*.log`, SC `sc/v2/logs/e244-*.log`)

| Chỉ số | Ngưỡng PASS | Số đo |
|---|---|---|
| 1. Unit ×3 module | root + game = `E`, nextgen = `En`; 0 fail/skip, 0 `@Ignore` mới | PASS: root 2137 + game 110 = 2247 (v2.4.3 = 2213, +34), nextgen 1808 (v2.4.3 = 1776, +32); 0 fail / 0 error / 0 skip; `@Ignore` 2 = 2, mới 0; BUILD SUCCESSFUL 5 phút 16 giây (`t244-r-full.log`) |
| 2. Dump ×3 chỉ thêm | `--check` unchanged ×3; `-` = 0; game 389; `+` chỉ 3 khối `UnitGate`, `NativeMediaRatio`, `MainFront` | PASS: unchanged ×3 (5928 / 389 / 6235), `-` = 0, game 389 / +0, root +60, nextgen +60; `+` = đúng ba khối internal `com.app.plugin.ads.core.UnitGate` (kèm 4 lớp lồng private `$Fail`, `$Turn`, `$TurnCb`, `$Waiter`), `com.app.plugin.ads.NativeMediaRatio`, `com.app.plugin.ads.MainFront` (`t244-r-api.log`) |
| 3. Façade + parity + H9 | façade ×2, parity PASS; H9 ≥ 70% | PASS: façade root PASS vs `8f9539c`, game PASS vs `82c22cd`, parity PASS (452 / 464 khối, 439 y hệt, 25 khác biệt internal), H9 319 types, internal 231 = 72,4% (`t244-r-facade.log`) |
| 4. Release gate | utility + nextgen PASS; `absent-class sdk=0` | PASS / PASS: utility `absent-class sdk=0` (allow 52, không đổi so 2.4.3), R8 đổi tên 474/480 (98%), apk sha256 `a2d0f5de2fbaff9de20212341a442fe884c32fb5d9c202c77bf58974a277cf6e`; nextgen sdk=0 (allow 55), 485/491 (98%), apk sha256 `2990f90f03f81908c2dce24d95bce823d2727302baf1466dadefbc6073888d3c` (`t244-r-release-{utility,nextgen}.log`) |
| 5. Bundle size | ≤ 26 214 400 B | PASS: utility max 7 749 764 B (Δ +3355 vs 2.4.3), nextgen max 7 614 226 B (Δ +4875 vs 2.4.3) (`t244-r-bundle-size.log`) |
| 6. Gate emu37 | FATAL/ANR/native 0, R8 0; `license: VALID` 1/process | PASS (gate RESULT PASS, GATE_RC=0) trên APK utility release sha256 `a2d0f5de2fbaff9de20212341a442fe884c32fb5d9c202c77bf58974a277cf6e`: FATAL 0, native 0, `am_anr` 0 mọi chặng, R8 0, `license: VALID` đúng 1/process, 0 `NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError` chứa `com.app.plugin`. Lượt 1 INVALID (cổng sink bị lane khác chiếm, không tính; `t244-r-emu37-try1-INVALID.log`), lượt 2 là số đo. Tải máy: 3,87 trước boot, nhưng boot gặp tranh chấp (load1 6,63 > 4 sau 240 s do lane khác), chờ ổn định 270 s rồi chạy cổng ở tải 4,48, nên không đánh giá thời gian (`t244-r-emu37.log`, `t244-r-emu37-tries2.log`) |
| 7. IM hồi quy | `2.4.x -> 2.4.4`; BUILD SUCCESSFUL; unit như baseline; status rỗng trước và sau | PASS: IM head `45730bd` (toml IM còn 2.4.2, nên dòng là `appplugin:2.4.2 -> 2.4.4` ×2, không phải `2.4.3 -> 2.4.4`); BUILD SUCCESSFUL 25 s; unit 34 / 0 / 0 / 0 (baseline 34); `git status --short` rỗng trước và sau (`t244-r-im.log`) |
| 8. INFLIGHT: 0 request chồng cùng unit (SC) | overlap/err1/throttle/after_fail<2s = 0; 0 dòng `load skipped unit backoff` mà không có `tryLoad` cùng placement trong 20 s sau; merged/waits/skipped/released after (trung vị, max) = info | PASS (SC Samsung, 5 cold + first run): overlap 1 → 0 (control 2.4.3 overlap 1/1/1, `full_2_default` ∥ native cùng unit `6781010236`); pairs<1s, `after_fail<2s`, throttle, err1 đều 0; merged 1 mỗi lượt; `load released after` 1922–2853 ms (cold 1–5: 1922 / 2692 / 2621 / 2853 / 2011, trung vị 2621; first run 4277); waits 0, skipped 0 (backoff không đo được trên máy, JVM IF thay); không lượt nào chạm trần 10 s |
| 9. INFLIGHT không làm chậm luồng chính | trung vị `showOpenAd` ≤ control + 300 ms | PASS: trung vị `showOpenAd call show` kể từ `Start proc` 5982 → 5695 ms (−287 ms, −4,8%; ngưỡng ≤ +300); host bận nên trong nhiễu |
| 10. ASPECT | `ads native media ratio: ANY (ads_cf_native_media_ratio=0)` đúng 1/process; ảnh small/cl/language_2/Full2 ≥ 1 creative không dọc, cắt/không = info | PASS (một phần): đúng 1 dòng `ads native media ratio: ANY (ads_cf_native_media_ratio=0)` mỗi process ×7, control 0 dòng. Ảnh chỉ chụp small (language, không cắt) và home; cl, language_2, Full2 chưa chụp (info) |
| 11. INITCB | gap `complete after` → `tryLoad` đầu: trung vị ≤ 20 ms; max, `Choreographer: Skipped <n> frames` = info | PASS: gap 51 → 0 ms (control 105 / 48 / 51; 2.4.4 0 / 0 / 0 / 1 / 0, max 105 → 1); `Choreographer: Skipped` trong 5 s sau `complete after` 0 / 0; `tid == pid` 8/8; thời gian init (info) 1289 → 1374 ms, nhiễu máy bận |
| 12. Tổng (info) | `showOpenAd`, `onimpression` so với control cùng khung | info: như hàng 9, `showOpenAd` 5982 → 5695 ms; máy bận (tải 4–17) |
| 13. Ad Inspector SC (info) | Meta "No ad returned"/khác → OWNER-NOTES | chưa đo (tuỳ chọn, hết thời gian cửa sổ máy) |
| 14. First run + cổng (SC) | test device/Facebook/click/FATAL/ANR mọi lượt | PASS: 9 lượt cold + first run: test device 6–7 dòng mỗi lượt (first run AdMob 14 dòng), 0 `Facebook's ad units` ở các lượt cold, 0 `setTestDeviceIds`, 0 `onAdClicked`, 0 FATAL/ANR của app, 0 impression Meta. First run: hash Meta đổi sau cài sạch nên Meta chạy không test mode (1 dòng cảnh báo lúc init), nhưng không có impression hay click Meta; FATAL duy nhất là tiến trình `UiAutomation`, không phải app |
| 14b. INFLIGHT không làm chậm native màn (SC) | trung vị vào màn → `showNative call show` ≤ control + 500 ms (vượt ⇒ F1b quyết, không tự FAIL) | đo một phần (PASS `nt_language`): từ `splash_exit` tới `nt_language showNative call show` 842 → 816 ms (control 843 / 842 / 824; 2.4.4 814 / 872 / 814 / 816 / 822; ngưỡng ≤ +500). `nt_intro_1` và `nt_home` chưa đo: không có control first run cùng khung (cài sạch đổi hash Meta, lặp lại sẽ chạy Meta không test mode, rủi ro hiển thị thật); first run 2.4.4 cả ba native intro `has loaded`, 0 chờ. **Finding F-Q1 (first run):** `nt_splash` xếp hàng 4277 ms sau lượt Full2 preload cùng unit (SC gọi `loadFull2ForThisTurn` 30 s trước consent); first run 2.4.4 mất một lượt hiện `nt_splash` (ad object dùng lại làm `nt_language_alt`), tham chiếu 2.4.2 hiện được. Ghi handoff 2.5.0 và OWNER-NOTES 29 |

## 4. Benchmark cạnh tranh — SDK phải cho app đạt gì

Không so "SDK với SDK". So **app dùng SDK** với ngưỡng Play, hướng dẫn AdMob và Phụ lục A Hiến pháp.
Số [D] là mục tiêu tự đặt kèm cách đo; đổi được sau khi có dữ liệu thật của từng app (doc 62 mục 2.2).

| Nhóm | Chỉ số | Ngưỡng | Nguồn | Đo ở đâu | 1.7.0 (2026-10-02) | 1.8.0 | 1.9.0 | 2.0.0 (đến `a47d7dc`) | 2.1.0 (`26609b7`, Task R) |
|---|---|---|---|---|---|---|---|---|---|
| Ổn định | Crash / ANR user-perceived | < 0,5% / < 0,3% | Phụ lục A | Play Console | cần app thật (Play Console); demo: FATAL/native/`am_anr` = 0 ở mọi cổng thiết bị, không thay số Play Console | không đổi so với 1.7.0: demo FATAL/native/`am_anr` = 0 ở gate 37/28/26; số Play Console cần app thật | không đổi: demo FATAL/native/`am_anr` = 0 ở gate 37/33/28/26 và Redmi; số Play Console cần app thật | không đổi: demo FATAL/native/`am_anr` = 0 ở IG-C1 (API 37, 26); số Play Console cần app thật; Task R: FATAL/native/`am_anr` = 0 ở gate 37/33/28/26, Redmi và Samsung (pangle lượt 2 `am_anr=1` ở host tải 20 là nhiễu, lượt 3 sạch, D33) | demo FATAL/native/`am_anr` = 0 ở mọi cổng thiết bị 2.1.0 (gate 37/33/28/26 + Redmi; Samsung chưa đo, máy vắng); số Play Console cần app thật |
| Tốc độ | TTID cold start máy tầm trung | ≤ 2 s; SDK ≤ 150 ms trong đó | Core App Quality [P]; [D] | `am start -W` | đo được trên demo (#8): cold1 `TotalTime` API 37 1783 ms, API 28 1198, API 26 890, Redmi 565; `initPlugin total` của SDK 15 / 164 / 88 / 21 ms (API 28 emulator vượt 150 ms, ba máy còn lại đạt) (`t170-r-*-gate.log`) | `initPlugin total` của SDK cold1/cold2: API 37 88/47 ms, API 28 108/120, API 26 187/113 (`t180-r-emu{37,28,26}-gate.log`; host có nhiễu lane khác); Redmi API 34 53/16 ms (`TotalTime` 733/374 ms, `t180-r-redmi-gate.log`); Samsung: chưa đo | `initPlugin total` cold1/cold2: API 37 11/26 ms, 33 41/48, 28 155/46 (1.8.0 108/120), 26 158/61 (1.8.0 187/113) sau đo lại D54 (lượt đầu 274/77 và 56/268 vượt dải do host bận; D56: PASS trong dải ± nhiễu host), Redmi 28/11; by-sdk 0, FATAL/ANR 0 (`t190-r-*-gate.log`) | `initPlugin total` cold1/cold2 (Task R): API 37 108/103 ms, 33 26/46, 28 155/72, 26 63/128 (`TotalTime` cold1 37 2613 ms, 33 1540 ms), Redmi 28 ms cold1; API 28 cold1 155 ms vượt 150 ms (emulator, thông tin) | máy thật, mục "Hiệu năng": Redmi `TotalTime` cold **379 / 418,8 ms** (SDK `onCreate` 17 / 25, `initPlugin` 16 / 22,2; 2.0.0 364 / 452,4), ngưỡng `hard` PASS; Samsung chưa đo (máy vắng); emulator thông tin |
| Tốc độ | Quảng cáo đầu tiên (người dùng quay lại, đã consent, Wi-Fi) | app open hiện ≤ 3 s sau cold start [D] | AdMob: app open cần load trước [P] | log `onAdShowed` − `am start` | đo được trên demo (ts `ad_show` app open − cold start, pha A #10); chưa có giá trị: pha A của demo chỉ bấm interstitial và rewarded, không có app open | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 1.9.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 2.0.0 (giữ kết luận cột 1.7.0) | time-to-first-request: Redmi 565 ms (lượt chạy lại), 572,5 ms (A/B gộp; 2.0.0 568,5 ms), emulator 969–2163 ms (thông tin); quảng cáo đầu cho người dùng quay lại chưa đo lại ở 2.1.0 (giữ kết luận cột 1.7.0) |
| Tốc độ | Trễ hiện khi đã load (trigger → `onAdShowed`) | p50 ≤ 300 ms, p95 ≤ 800 ms [D] | đo 2026-09-24: Full2 220–256 ms | log | đo được trên demo (`ad_show_attempt(ready=1)` → `ad_show`, JSONL pha A #10): 8 mẫu 110, 157, 158, 166, 202, 285, 336, 1832 ms, p50 ≈ 184 ms (mẫu 1832 ms = interstitial API 26 emulator); 8 mẫu chưa đủ cho p95 | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 1.9.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 2.0.0 (giữ kết luận cột 1.7.0) | Redmi **133 ms** (A/B gộp 136,5 so với 2.0.0 144,5), ngưỡng `hard` ≤ 200 ms PASS; emulator 158–396 ms (n = 1); Samsung chưa đo (máy vắng) |
| Doanh thu | Show rate interstitial (hiện / lượt gọi có quảng cáo đã load) | ≥ 95% [D] | — | `ad_show` / `ad_show_fail` theo `reason` | đo được trên demo (#14, `v_show_rate` trên JSONL): interstitial 13/13, rewarded 12/12, native_collapse 3/3, rect 3/4, banner 2/13 (`t170-r-be-views.log`) | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | đo được trên demo (`v_show_rate`, ngày 2026-10-01, mẫu mỏng): interstitial 1/1, rewarded 1/1, **rewarded_interstitial 1/1 (n = 1)** = 100 %; NtCl 0/0 (thông tin); banner 2/7; `declined_app_day` tách ở `v_brain_decisions` (`t190-r-be-showrate.log`) | đo được trên demo (Task R, `v_show_rate` 6 sink): interstitial 15/18 = 83,3 %, rewarded 5/5, rwi 10/27 = 37,0 %; dưới 95 % do chặng/guard cố ý (rwi chặng 2/3, `ad_showing`, brain declined): lượt tự nhiên đã sẵn sàng 100 % (D34, n nhỏ) | D21 (oracle Python, `v_show_rate_reasons`): `interstitial` 11/11 và `rewarded` 4/4 (100 %); `rwi` 9/22 thô, mọi lượt sẵn sàng có tổ chức đều hiện (9/11; 11 `blocked_policy`, 4 kill switch, 1 harness); banner/native/rect đếm chu kỳ làm mới (12/109), không phải lỗi sản phẩm |
| Doanh thu | Match rate theo định dạng | ≥ trung vị nhóm quốc gia (doc 62 mục 2.1) | AdMob | AdMob report | cần app thật (importer `admob_import.py` của T12 chỉ thử trên fixture) | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 1.9.0 (giữ kết luận cột 1.7.0) | cần app thật | cần app thật |
| Doanh thu | Impression đếm đúng | Firebase = AppsFlyer ± 1%/ngày | doc 62 mục 2.3 | đối soát | đếm: đo được trên demo (H6a #12: 12/12 phiên, chênh 0); giá trị: cần app thật (H6b, program gate mở) | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 1.9.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 2.0.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 2.1.0 (giữ kết luận cột 1.7.0) |
| Doanh thu | eCPM | Google optimized floors, không tự dựng waterfall giá | doc 62 C [P] | AdMob | cần app thật (view `v_ecpm_format_tier` có hàng nhưng giá trị 0 với test ads) | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | view `v_ecpm_network_format` (theo `ad_network` × `ad_format`) có 4 hàng, giá trị 0 với test ads; cần app thật | cần app thật | cần app thật |
| Chính sách | Cảnh báo chính sách AdMob / Play | 0 | Điều 13, 14 | console | cần app thật | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | cần app thật | cần app thật | cần app thật |
| Giữ chân | D1/D7 nhóm có quảng cáo so với holdout | không giảm có ý nghĩa | doc 62 J | brain holdout + Firebase | đường ống đo được trên demo (`v_retention_d1_d7_holdout` có hàng, cohort cùng ngày nên tỉ lệ 0); số cần app thật | chưa đo lại ở 1.8.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 1.9.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 2.0.0 (giữ kết luận cột 1.7.0) | chưa đo lại ở 2.1.0 (giữ kết luận cột 1.7.0) |
| Dung lượng | Bản tải về (Play Console) app tiện ích | ≤ 25 MB | Phụ lục A | Play Console | đo được trên demo (#7): `bundletool get-size total` `utility` max 7 652 009 B ≤ 25 MB (`t170-r-bundle-size.log`) | đo được trên demo (#7): `bundletool get-size total` `utility` min 7 630 564 / max 7 685 835 B ≤ 25 MB, +33 826 B so với 1.7.0 (`t180-r-bundle-size.log`) | đo được trên demo (#7): `utility` min 7 642 770 / max **7 702 160** B ≤ 25 MB, +16 325 B so với 1.8.0 (7 685 835; ước ≤ +60 KB đạt); partner (thông tin): pangle +2 056 907 B, mintegral +2 242 047 B, vungle +368 310 B, unity và applovin chưa đo (R8 thiếu lớp, D51) (`t190-r-bundle-size*.log`) | đo được trên demo (C2b, thông tin): `utility` min 7 644 577 / max **7 703 848** B ≤ 25 MB (+1 372 B so với IG-C1); ba AAR 2 581 326 / 1 287 / 1 157 B; partner `applovin` max 9 047 013, `unity` max 9 570 320 B; Task R #8: `utility` min 7 646 288 / max **7 706 048** B (+3 888 B so với 1.9.0), AAR 2 065 728 / 1 287 / 1 157 B | max **7 702 334** B ≤ 25 MB (min 7 640 874; 2.0.0 7 706 048: −3 714, thông tin; #8) |
| IAP | Subscriber không thấy quảng cáo | = 100 % (`ad_show_fail reason=remove_ad` sau `grant`, `ad_show` 0) | DOC/67 H7 | G12 (`iap_check.py`) | — (mới ở 1.8.0) | đo được trên demo qua harness (không phải mua thật): API 37 `buy_sub`/`buy_cheap` (mask ALL) PASS, `cancel` (#3) và `pending_then_paid` (#4) 0 grant PASS (`t180-r-emu37-g12.log`); API 28/26: 12/12 PASS với TF-2 (cùng khẳng định `iap_check.py`); Redmi API 34: 12/12 PASS (`t180-r-redmi-g12.log`); Samsung: chưa đo; mua thật trên Play: H7b (OWNER-NOTES #4) | đo được trên demo qua harness: 12/12 trên API 37/33/28/26 và Redmi (mục G12; không phải mua thật, H7b) | đo được trên demo qua harness: `--iap` 12/12 trên API 37 ở IG-C1, `remove_ad` rwi 1 (không phải mua thật, H7b); Task R: 37/28 và Redmi 12/12, 33/26 11/12 có ngoại lệ harness/môi trường (D30, D32-2); `ad_show` sau grant 0 ở mọi máy | 12/12 trên 37/33/28/26 và Redmi, `ad_show_fail reason=remove_ad` 1 ở rwi mỗi máy (#13); mua thật: H7b |
| Hiệu năng | Cold start, request → fill theo format, fill rate, show latency, PSS trên 6 máy (N ≥ 5, trung vị/p90) | máy thật: `hard` = baseline v2.0.0 × 1,2; emulator và PSS: thông tin | `perf-baseline.md` (PERF-0), D5 | `tools/perf_gate.sh` + `tools/perf_report.py` | — | — | — | chưa có (không có PSS, n = 1–2 mỗi máy) | mục "Hiệu năng" bên dưới: 5/6 máy có số (Redmi `hard` PASS, 4 emulator thông tin); Samsung chưa đo (máy vắng); bảng đủ là mục này; `t210-r-perf-table.md` chỉ cục bộ (gitignored) |
| Bảo mật | Giấy phép offline: không token hợp lệ thì không quảng cáo, không brain | hợp lệ: đúng 1 dòng `license: VALID` mỗi process; sai: `ad_request` 0, `brain_decision` 0, 1 dòng INVALID | DOC/67 2.1.0, `security-proposal.md` | #28/#29 Task R (5570 + Redmi) | — | — | — | — | unit 18 + RFC 8032; #28 PASS ×5; #29 PASS (37 và Redmi, `ad_request` 0, `brain_decision` nguồn AdsBrain 0, D19); #30 PASS; #31 PASS; thời gian xác minh ART chưa đo |

Bốn đòn bẩy để "cạnh tranh được" nằm trong tay SDK: **(1)** không bao giờ là nguyên nhân crash/ANR;
**(2)** quảng cáo hiện nhanh, đúng lúc, đúng luật (policy + preload + Full2 liền mạch); **(3)** đấu giá tốt
nhất trong một phiên (AdMob bidding + Meta bidding, floor của Google, không stack song song); **(4)** đo
đúng để tối ưu được. Mọi task ở mục 5 đều phục vụ một trong bốn đòn bẩy này.

---

## 5. Lộ trình

### Phase A — 1.3.1 "Không crash ở bản release" (Task 1–7)

Xoá ba đường crash; vá điểm mù release; CI dựng app demo như app thật; chạy lại cổng trên emulator và
máy thật. **Xong khi:** G1 (đường crash = 0, FATAL = 0), G3 (ad unit > 0, 4 chuỗi = 0, rename ≥ 90%,
`ConsumerRulesTest` + `KeepXmlTest` xanh), G2 (`am_anr` = 0) và G9 (API 26 + 36 emulator, 1 máy thật).

**Trạng thái (2026-09-24):** 1.3.1: G1–G8 và G10 đủ; G9 một phần. Emulator API 26/28/37 đo tại
`9081988`; máy thật Galaxy A25 (API 36) đo lại tại `a2428cd`, sau `e19e36f` đổi đường show của vòng
open/interstitial/rewarded: cổng release PASS (225 lớp, 98% đổi tên), cổng thiết bị PASS (TotalTime
1002 ms, R8 0/0, FATAL/`am_anr`/native 0/0/0 ×4), kịch bản (d) PASS (doc 64 mục F, dòng "d (máy thật,
sau đợt sửa cuối)"). Các kịch bản tay khác chỉ trên emulator, chưa chạy lại sau `e19e36f`. Lần đo đầu
trên máy thật (tại `f2c8913`): Máy thật
(`SM-A256E`, serial `R5CX10P9EKZ`, APK release của `release-gate-20260924-193609.log`):
`EVENTS=1500 tools/device_gate.sh` PASS, TotalTime 1223 ms, chuỗi R8 0 sau cold start và sau monkey,
FATAL/`am_anr`/native 0/0/0 lúc cold start và cả 3 seed; kịch bản (d) xoay 4 lần lúc Full2 hiện: đúng một
dismiss, hai lượt interstitial → Full2 sau đó hiện ở lần bấm đầu, FATAL/`WindowLeaked`/`am_anr`/`BadToken`
0. Phần ma trận của G9 cho Phase A (emulator + 1 máy thật) đã có. Dòng G9 "1 Activity sau GC" chưa đạt ở
luồng Full2 (còn 1 Activity cũ, tới 3 trên luồng dài; doc 64 mục F). Đóng Phase A hay không: chủ dự án
chốt. Kết quả và log: doc 64 mục F.

### Phase B — 1.4.0 "Đúng luật, đo đúng, không hỏng câm" (Task 8–17)

Tier consent từ TCF và lưu tier; Firebase/FCM/AppsFlyer sau consent với Consent Mode v2; một policy
tần suất; app open hết hạn; remove-ads mask; `ad_impression` không đếm đôi; hàng đợi event; tên event;
main thread; kill switch + `sdk_health` + Crashlytics key; ABI dump. **Xong khi:** G4, G5, G6, G7 đủ, G8
mức 1.4, G2 mục StrictMode = 0.

**Trạng thái 1.4.0 (2026-09-25, nhánh `feat/1.4.0`, Task 17):** xong Task 8–16 (gồm 14a, 14b) và Task 17;
`sdkVersion = "1.4.0"`, **chưa tag, chưa publish**. Số đo lại ở cột "2026-09-25 (1.4.0)" của mục 3; log ở doc 64
mục G. Cây đo: `05ca0bf` + số phiên bản (commit chốt ngay sau chỉ đổi doc và số phiên bản); fix wave 1 của review
Task 17 (`6529164`: gắn hàng đợi decision log ngay bằng `applicationInfo.dataDir` thay cho gắn trên luồng nền của
`05ca0bf`; `9621b30`: `logService` volatile) chạy lại cổng release (PASS) và cổng thiết bị API 37 `EVENTS=600`
(PASS, StrictMode do SDK 5 = 5 lần nạp prefs đầu, khác 0).
- Chạy lại cổng: unit 931/931 ×3; `tools/release_gate.sh` PASS (8 ad unit, 248/252 = 98% đổi tên, zipalign OK);
  `EVENTS=1500 tools/device_gate.sh` PASS trên emulator API 37, 28, 26 (chuỗi R8 0, FATAL/`am_anr`/native 0,
  StrictMode do SDK ngoài lần nạp prefs đầu = 0); `tools/api_dump.sh --check` PASS; `:lintDebug` 0 lỗi (sau
  `96f9fa8`); kịch bản tay (a)–(j) và (k), (l) trên API 37 PASS. Trong lúc chạy, hai cổng lần đầu FAIL và được sửa
  trước khi đổi số phiên bản: `:lintDebug` (`MissingPermission` WAKE_LOCK, có từ Task 9 → `96f9fa8`) và StrictMode
  (`context.filesDir` trên main trong `initPlugin`: một vi phạm DiskRead `File.exists` trong `ensurePrivateDirExists`
  → `05ca0bf`, làm lại ở `6529164`).
- Máy thật (Galaxy A25): **chưa chạy kịch bản tay của 1.4.0 (khoá màn hình)**; số cổng tự động của máy thật vẫn
  là lần đo tại `b4a268b` (`wtB2-*`: cổng release PASS, cổng thiết bị PASS). Redmi 22021211RC (khác máy, phép
  dùng từ 2026-09-25) đã chạy cổng và kịch bản a–h, g trên cây `b9ec778` (trước Task A–G) — xem DOC/64 §G và
  DOC/66 mục 1; cần chạy lại trên cây sau Task A–G cho "cổng cuối".
- **2026-09-25, sau DOC/66:** chủ dự án chấp nhận toàn bộ đề xuất DOC/66 ("tiếp tục thực hiện"). **1.4.0: mọi
  quyết định §7 đã chốt, trừ #4 (chờ chủ dự án tự xem Crashlytics trước khi quyết định backport) và #17 (P2,
  mục mới, chưa chốt).** #1, #2 chốt giữ mặc định tần suất = 1.3.1 (A/B ở 1.5.0). #5 (đổi tên event) chốt ở
  DOC/66 (Task E). #9 (ngưỡng G2 trừ lần nạp prefs đầu) đã giải quyết bằng ấm hoá (Task B), không cần chốt
  ngưỡng. G8 dòng `AdsHelper` ≤ 1.500 chuyển ngưỡng từ mức 1.4 sang mức 1.5 (T3, xem §3 G8). Còn chờ: cổng
  cuối trên máy thật (controller, xem DOC/64 §G) và review toàn nhánh; **chưa tag**.
- **Task G (2026-09-25, research-g8.md + task-G-report.md):** thêm `internal` cho 66 lớp adapter/nội bộ (49
  adapter `adsmob`/`adsmax`/`adsfb`, `ads` 4, `logevent` 5, `until` 4, `brain` 3, `firbase` 1) theo Phụ lục A của
  research-g8.md, đưa dòng `internal` của G8 mức 1.4 từ 16% lên **50,3%** (99/197) — qua ngưỡng ≥ 40%. Trình biên
  dịch xác nhận 0 lớp phải quay lại public; `tools/api_dump.sh --check` PASS không đổi dump (4.155 dòng); grep
  `~/AndroidStudioProjects/*` (bỏ `adsplugin/`, `build/`, `.claude/worktrees/`) và `app/src` (demo) cho 0 tham
  chiếu ở cả 66 lớp. `AdsHelper.kt` (3.865 dòng) và `LogEventServer.kt` (942 dòng) vẫn > 800; ngưỡng riêng
  `AdsHelper` ≤ 1.500 nay ở **mức 1.5** (DOC/66 mục 4 T3, chủ dự án chấp nhận 2026-09-25) — tách hoãn sang 1.5.0/Phase
  C, kế hoạch 12 file ở `research-g8.md` mục 2, ước 22–25 giờ agent.
- Tiêu chí "xong Phase B" (G8 mức 1.4): **nay đủ** — dòng `internal` 50,3% ✓ (Task G), ABI dump khoá trong CI ✓,
  ngưỡng `AdsHelper` ≤ 1.500 không còn ở mức 1.4 (dời sang 1.5, T3). Vẫn còn thiếu cho "xong Phase B" nói chung: G7
  chưa kiểm console (Remote Config, Crashlytics, DebugView); G5/G6 phần máy (luật doc 62 F bật bằng Remote Config,
  DebugView); G2 theo thước đo mới (by sdk = 0, Task B) đạt trên emulator API 37/28/26 (APK `c4749ca`) và Redmi (APK
  `c216628`, `waited=0 ms`; DOC/64 "Cổng cuối 1.4.0"), APK cuối chưa chạy trên máy thật; máy thật chưa chạy hết kịch bản tay
  (Redmi §G DOC/64, xem mục G). Theo mục 3, **không gắn tag 1.4.0** tới khi có cổng cuối (controller) trên máy thật
  và review toàn nhánh.

### Follow-up sau 1.4.0 (thu thập từ report Task A–G và ledger §7)

- GPP/US-state (§7 #14): đọc `IABGPP_*` và chuyển cho Meta/MAX/AppsFlyer/Firebase/CRM — lộ trình 1.5.0.
- P2, telemetry trước consent (§7 #17): chủ dự án chưa chọn giữa hàng đợi tới khi có tầng hay chấp nhận bí danh —
  quyết định riêng, không chặn 1.4.0.
- facebook-core: override auto-log phía server (Meta) không có bước kiểm trên máy — cần một lượt cấp → rút → mở
  lạnh trên thiết bị thật để xác nhận `fb_mobile_activate_app` không lọt qua trước khi cờ `false` được ghi (N1,
  task-CD fix wave 2).
- MAX rewarded dùng chung ad unit giữa hai placement (review Task 14 của 1.5.0): `MaxRewardedAd` là singleton theo unit
  của AppLovin nên listener của placement nạp sau thắng (last-writer-wins), placement kia mất callback hiện/đóng/thưởng;
  có từ bản đầu khi hai danh sách cùng bắt đầu bằng unit đó, từ 1.5.0 cả khi unit chung ở vị trí sau trong danh sách; cách tránh hiện tại: mỗi placement rewarded MAX một ad unit riêng (CHANGELOG 1.5.0 "Giới hạn đã biết") — 2.0.

**Task 16 (2026-09-24):** G7: chưa kiểm trên console; unit test + log `kill switch:` khi inject
`SdkKillSwitch.current` qua test. Còn lại cho chủ dự án: đặt `sdk_kill_android` = `{"ads":true}` trên
console, mở app hai lần (lần đầu nhận và lưu khoá, lần sau không khởi tạo mạng), đếm request quảng cáo
bằng 0; một crash thử để thấy `appplugin_version`; DebugView để thấy `sdk_health`.

### Ghi chú PR (D7, D8 — DOC/66 mục 2)

- **D7, `api_dump --check`:** chạy `--check` trên 13 commit trong một worktree tạm cho thấy 4 commit —
  `a60a696`, `acca98c`, `ef2b918`, `6e2745c` — FAIL khi kiểm đứng riêng, vì dump `api/appplugin.api` chỉ được
  cập nhật gộp ở commit `a7d17e4` sau đó (không phải lỗi ABI thật, chỉ là dump chưa theo kịp từng commit).
  Không rebase để sửa lại lịch sử. Khi cần `git bisect` qua đoạn này, `git bisect skip` bốn commit trên. Luật
  từ nay: dump phải đi cùng commit đổi API (mỗi task trong 1.4.0 đã làm đúng vậy — xem cột "Commit" của từng
  report).
- **D8, trailer commit:** nhánh mang trailer `Co-Authored-By` của hai model khác nhau — 64 commit trước
  `b9ec778` là `Claude Opus 5.5` (9 commit đầu của đoạn đó đã ở `origin/release/1.3.0`), 11 commit là
  `Claude Fable 5.1`, và các commit mới của 1.4.0 (Task A trở đi) lại là `Claude Opus 5.5` (mỗi report Task
  A–G đều ghi rõ concern này: brief yêu cầu trailer `Fable 5.1` nhưng phiên chạy việc là Opus 5.5, và quy tắc
  session bắt trailer phải đúng model đã tạo commit). **Giữ nguyên, không rebase**: đồng nhất trailer sẽ phải
  rebase 66+ commit và đổi mọi SHA đang được trích dẫn trong DOC/64, DOC/65 và CHANGELOG.

### 1.5.0 "Tách AdsHelper, đóng nốt follow-up 1.4.0" (plan `docs/superpowers/plans/2026-09-27-sdk-1.5.0-plan.md`)

**Trạng thái 1.5.0 (2026-09-28, nhánh `feat/1.5.0`, Task 16):** xong Task 0–16; `sdkVersion = "1.5.0"`, **chưa tag, chưa
push**. `AdsHelper.kt` 4.018 → **692** dòng: façade giữ nguyên mọi chữ ký public/protected, thân chuyển sang 12 controller
`internal` trong `com.app.plugin.ads.core` (13 file, lớn nhất 512 dòng) — G8 dòng file đạt mức 1.5 và mức 2.0 cho
`AdsHelper`; `LogEventServer.kt` 955 dòng vẫn > 800 (ngưỡng 2.0). Đóng final review 1.4.0 Minor 6 (rect `getAdForShow`
không còn báo `LOAD_FAIL_BUT_ADS_NOT_NULL` giả, Task 15) và Minor 7 (MAX dựng đối tượng một lần: mỗi bước waterfall nay
request đúng unit của mục, Task 14); vòng banner có `loadCycle`; bỏ hai getter-null của callback load. Số đo ở cột
"2026-09-28 (1.5.0)" của §3 G8, G9; log `DOC/testing/t150-t16-*.log` (không commit).
- Cổng: unit 1360/1360 ×3 (0 fail, 0 skip); `tools/api_dump.sh --check` → `API unchanged (4387 lines)`;
  `tools/api_facade_check.sh` PASS so với `6d8f454`; `--check` xanh trên cả 56 commit `6d8f454..bca25e2` và facade PASS trên 55 commit có script (worktree tạm, final review
  2026-09-28 — D7); `tools/release_gate.sh` PASS (9 ad unit, 272/276 = 98% lớp SDK đổi
  tên, zipalign 16 KB OK; 12/12 controller `ads.core` còn dòng thành viên trong `mapping.txt`, 5 còn tiêu đề lớp riêng —
  R8 gộp ngang/inline controller một thể hiện là bình thường; `AdsNetworkRegistry$Companion.build(` 123 dòng khung);
  `EVENTS=1500 tools/device_gate.sh` PASS trên emulator API 37, 28, 26 (StrictMode 0, chuỗi R8 0, FATAL/`am_anr`/native 0);
  sau consent, cold start + bấm từng nút: 8 định dạng (banner, NtCl, rect, native, interstitial, native full, app open,
  rewarded) `tryLoad ok` trên cả ba API; `:lintDebug` 0 lỗi (90 cảnh báo).
- Kịch bản tay (emulator API 37, bản release): resume app open — mỗi lần về foreground đúng một quyết định
  `full_resume` (`open_flag_resume`, demo không bật cờ), cả sau xoay; rect (Task 15) — `load_fail_but_ads_not_null` 0,
  đúng một callback mỗi lần bấm, cả sau Home → mở lại; banner mất mạng 2 phút (Task 15) — hai vòng thử lại thất bại, có
  mạng lại thì vòng kế nạp và hiện (`load timeout` 0, không chạm watchdog 90 s); FATAL/`WindowLeaked`/`BadToken`/`am_anr`
  0. G9 (bản debug): luồng Full2 của Task 12 → 3 Activity sau GC (chuẩn 4), vẫn ✗ so với 1 (#18).
- **Chưa kiểm:** kịch bản tay trên máy thật (R-12; Redmi API 34 và Galaxy A25 API 36 cổng thiết bị PASS trên `ee4991a`, định dạng
  hiện trong màn hình nạp được; NtCl, show rewarded/app open và kịch bản tay chỉ trên emulator); CI (nhánh chưa push); MAX trên máy (demo không có `admax_devKey`/`admax_*`, O10); nhánh `load timeout` của vòng banner (chỉ
  unit `BannerLoadCycleTest`). Mục chờ chủ dự án: O1–O10 của plan 1.5.0 ("Blocked on owner"); O7 (cổng load im lặng),
  O8 (rewarded thôi xoay mạng đầu), O10 chưa cấp key — ghi ở CHANGELOG 1.5.0 "Giới hạn đã biết"; O9 (brain reward cũ)
  đã sửa ở 1.6.0 Task 1 (`76ac6fd`). §7 không đổi.

### 1.6.0 "Đóng O1/O3/O7/O9, GPP opt-out bang Mỹ, sửa G9 bước rect" (plan `docs/superpowers/plans/2026-09-28-sdk-1.6.0-plan.md`)

**Trạng thái 1.6.0 (2026-09-28, nhánh `feat/1.6.0`, Task 9 + Task 10):** xong Task 0–10; `sdkVersion = "1.6.0"`,
**chưa tag, chưa push**. Đóng O9 (Brain reward, Task 1), O3 (a) (telemetry giữ tới khi có tầng, Task 6), O7 (a) (bốn
cổng load hết im lặng, Task 5), O2 (GPP opt-out bang Mỹ cho 4 consumer, Task 7–8); phân tích G9 bước rect (Task 3–4)
rồi vá thêm đường giữ Activity qua Full2 native full dựng sẵn tìm thấy ở Task 9 (Task 10, `ffe0505` + review fix
`4abbefe`/`df626d5`). **API public/protected không đổi** so với `a76f77e` (`tools/api_dump.sh --check` →
`API unchanged (4484 lines)`, `tools/api_facade_check.sh` PASS); lớp mới đều `internal` (`CfNotDispatcher`,
`FanOutLoadCb`, `LoadCallbacks`, `OneShotLoadCb`, `GppConsent`, `GppSignals`, `TelemetryConsentGate`); Task 10 thêm
một thành viên `internal` mangle (`releaseNativeAdView$appplugin()`) trên lớp `internal` có sẵn `AdsMobNtFullView`
(javap thấy public) — không phải bề mặt API mới. Số đo ở cột "2026-09-28 (1.6.0)" của §3 G4, G6, G8, G9; log `DOC/testing/t160-t9-*.log`,
`t160-t10*.log` (không commit).
- Cổng: unit 1498/1498 ×3 (Task 9) → 1505 (Task 10, +7 `NtFullPreparedViewReleaseTest`) → **1509** (review fix, +4
  `NtFullPreparedViewOrderTest`), 0 fail, 0 skip; `tools/api_dump.sh --check` → `API unchanged (4484 lines)`;
  `tools/api_facade_check.sh` PASS so với `a76f77e`; D7 32/32 commit (`a76f77e..f183dcb`) dump PASS, facade PASS;
  `tools/release_gate.sh` PASS (APK sha256 `f7f182adbd4bb6867e53945cff8689e9020a8e1d6b6e2d0d1dc93a9608e505e9`, 9 ad
  unit, 98% lớp SDK đổi tên, zipalign 16 KB) và PASS lại tại `ffe0505` sau Task 10; `EVENTS=1500 tools/device_gate.sh`
  PASS trên emulator API 37, 28, 26 (StrictMode 0, chuỗi R8 0, FATAL/`am_anr`/native 0); sau consent, 8 định dạng
  `tryLoad ok` trên cả ba API; sau Task 10, `EVENTS=600 tools/device_gate.sh` PASS lại tại `ffe0505` trên emulator API
  37; `:lintDebug` 0 lỗi (chênh lệch 0 so với `a76f77e`).
- Kịch bản tay (emulator API 37): telemetry trước consent (bản debug + sink cục bộ) — POST trước consent 0, Consent →
  17 xả, "Do not consent" → 0/17, `pre_consent_lost` đúng theo kịch bản thiết kế; GPP 4 consumer (`--ez force_us true`,
  bản debug + release) — opt-out chuyển đúng cho Consent Mode/AppsFlyer/Meta AN, MAX bỏ qua (demo không có devKey),
  tầng không đổi, banner `tryLoad ok`; O7 trên máy (release, Load Rect ×2 cách 296 ms) — 2 `loaded`, 0 `load timeout`.
  G9 bước rect (debug, xoay `adb emu rotate`, Task 9): Activities sau rect 3 (≤ 3), sau cả luồng 6 (> 5 FAIL); một
  đường class A qua Full2 native full dựng sẵn (`AdsEcmp.adObject → NativeAd → NativeAdView.mContext`, có từ 1.5.0
  `cfb9726`). Vá ở Task 10, đo lại (xoay `settings put system user_rotation`): Activities sau rect vẫn 3 (≤ 3 PASS),
  sau cả luồng **5** (≤ 5 PASS); đường class A = 0/0/0 ở ba lần dump; còn lại toàn bộ là class C (GMA, #18).
- Máy thật (checklist R-12, Step 3): Redmi 65c9b673 API 34 PASS (`t160-t9-real-65c9b673*.log`; xoay
  `accelerometer_rotation`/`user_rotation` đọc-trước/khôi phục-sau đúng; gói gỡ sạch); Galaxy A25 (R5CX10P9EKZ) API 36
  PASS trên APK `ffe0505` (`t160-t9-real-R5CX10P9EKZ-gate.log`; xoay không đổi so với trước, gói gỡ sạch, sha256 APK
  trước/sau khớp).
- **Chưa kiểm:** API 30/33; MAX + GPP trên máy (demo không có `admax_devKey` — chỉ unit, O10); kịch bản tay ngoài
  checklist R-12 trên máy thật; CI (nhánh chưa push). Mục chờ chủ dự án: O5, O8, tag/push `v1.5.0` rồi `v1.6.0`, N1
  (telemetry sau `DENIED`), N5 (G9 nhánh B — nay giảm nhẹ, class A đã vá ở Task 10, còn lại là class C/#18), N6
  (`CfNotDispatcher` cho interstitial/app open), N7 (baseline façade → `v1.5.0`) — xem "Blocked on owner" của
  `t9-brief.md`. §7 #14, #17 "Đã chốt"; #18 không đổi (chưa chốt, chờ chủ dự án).

### 1.6.1 "AppsFlyer/AppLovin hết đọc đĩa trên main, G2 đo được đường AppsFlyer, kill switch áp cho event đang giữ" (plan `docs/superpowers/plans/2026-09-28-sdk-1.6.1-plan.md`)

**Trạng thái 1.6.1 (2026-09-29, nhánh `feat/1.6.1`, Task 0–6 + G1 + G2 + R + R2 + F):** xong T0–T6 (`PrefsWarmup` nạp
sẵn `appsflyer-data`/`com.applovin.sdk.shared` trên luồng nền; kill switch `logevent` áp cho event server đang giữ chờ
consent; AppsFlyer m8 gỡ bộ lọc partner khi start lại trong cùng process + nhãn `in|outside GDPR scope`;
`setSharingFilterForPartners("all")` thay hàm `setSharingFilterForAllPartners()` đã deprecated; `AdsMaxMy.resetForTest`
+ test GPP header cận cộng dồn), G1 (allowlist API ≤ 28: MIUI referrer, ART monitor contention), G2 (fix + review fix
`ClassValue` outline của kotlinx.serialization qua `consumer-rules.pro` `-assumevalues`), Task R (đo số vòng 1 tại
`af4161b`), R2 (đo lại measure-only trên APK cuối `46af047` sau fix G2), F (final review `final-161-review.md`, verdict
"Ready to release: YES after fixes", 0 Critical, 2 Important đã sửa ở E5: I1 số đo lại đúng commit, I2 CHANGELOG/DOC/65
đã viết). `sdkVersion = "1.6.1"` (commit `af4161b`), **chưa tag, chưa push**. **API public/protected không đổi** so với
`56cfd1d` (`tools/api_dump.sh --check` → API unchanged 4486 dòng; diff 0 dòng bớt, đúng 2 dòng thêm —
`PrefsWarmup.names$appplugin(Context, boolean, boolean)` `api/appplugin.api:4481`, `AdsMaxMy.resetForTest$appplugin()`
`:1416`, cả hai `internal`; `tools/api_facade_check.sh` PASS). Số đo ở cột "2026-09-29 (1.6.1)" của §3 G2, G3, G4; log
`DOC/testing/t161-*.log` (không commit), báo cáo `.superpowers/sdd/v161/{r2-report.md,g2-report.md,final-161-review.md,e5-report.md}`.
- Cổng: unit **1535/0/0/0** tại HEAD (cây giống hệt `74f27db`, `g2-report.md`; vòng 1 `af4161b` ×3 = 1533/0/0/0,
  `t161-r-full-{1,2,3}.log`); `tools/api_dump.sh --check` → API unchanged (4486 dòng, `t161-r-apidump.log`,
  `t161-r-apidiff.log`); facade PASS (`t161-r-facade.log`); D7 13/13 trên `56cfd1d..af4161b` (`t161-r-d7.log`);
  `:lintDebug` 0 lỗi so `56cfd1d`; cỡ file không đổi (`LogEventServer.kt` 966 ≤ 970, `AdsHelper.kt` 696,
  `PrefsWarmup.kt` 142 ≤ 150, `AppsflyerHelper.kt` 435 ≤ 440); `tools/release_gate.sh` PASS trên APK R2 cuối (sha256
  `629fb5c7af13f2e46e267eef77ea26d386baa778996c53d7b2eb670e449c2d86`): 9 ad unit, 98% lớp đổi tên (284/288), zipalign 16
  KB, **`ClassValue lines in mapping=0`** (`t161-r2-release-gate.log`); `EVENTS=1500 tools/device_gate.sh` PASS trên
  emulator API 37, 28 (R2, `t161-r2-emu{37,28}-gate.log`: `by sdk` 0/0 cả hai chặng, `appsflyer started` n=1, R8
  0/0/0, `ClassValue` 0, FATAL/native/`am_anr` 0 ×5 chặng, TotalTime 1174/519 và 843/1196 ms) và API 26 cùng luật R8
  (G2, `t161-g2-api26-gate.log`: `by sdk` 0/0, `appsflyer started` n=1, R8 0/0/0 raw 1/15/15 allowlisted, TotalTime
  986/736 ms).
- ANR (đủ số, không giấu lượt đỏ): monkey seed 1 đầu API 37 tại `af4161b` (APK `3de1ace…`) am_anr=1 (`Input dispatching
  timed out`, host load 49,77, CPU 99%, nhiều lane chạy song song; `device-gate-monkey-emulator-5570-20260928-223435-seed1.log`);
  2 lần chạy lại cùng APK trên host yên hơn (load 7,3/9,5) PASS am_anr=0 (`t161-r-emu37-rerun{1,2}-gate.log`); APK
  release cuối (R2, `46af047`) API 37 PASS am_anr=0 (`t161-r2-emu37-gate.log`). Phân loại: tranh chấp host, 1 lần,
  không lặp lại 3/3.
- `ClassValue`: tồn tại tiềm ẩn từ 1.6.0 (mapping `56cfd1d` đã có outline trong lớp Unity dùng chung
  `AndroidAttribution$$ExternalSyntheticApiModelOutline0`, `g2-report.md` mục 0); sửa ở G2 bằng `-assumevalues class
  kotlinx.serialization.internal.CachingKt { static boolean useClassValue return false; }`; `tools/release_gate.sh` nay
  FAIL nếu mapping còn chữ `ClassValue` (đỏ 51 dòng `t161-g2-fix-gate-red.log` → xanh 0 dòng
  `t161-g2-fix-gate-green.log`). Đỏ → xanh P1 (hàng 16 kế hoạch, API 28): đỏ `by sdk=1` (`t161-t3-gate-red-api28.log`)
  → xanh `by sdk=0` (R2, `t161-r2-emu28-gate.log`), cả hai `appsflyer started … n>=1`.
- Máy thật: Samsung A25 (R5CX10P9EKZ, API 36) PASS trên APK R2 (`t161-r2-R5CX10P9EKZ-gate.log`: `by sdk` 0/0,
  `appsflyer started` n=1, R8 0/0/0, `ClassValue` 0, FATAL/native/`am_anr` 0, TotalTime 889/683 ms, xoay không đổi, gói
  gỡ sạch, sha256 khớp E2). Redmi 65c9b673 **chưa đo (máy không cắm), chủ repo chạy**.
- `prefs warmup waited=` trên API 28/26 (đo bổ sung SHOULD-FIX, review cuối T1 m4, bản debug `46af047`, ×3 mỗi API):
  **0/0/0 ms cả hai API** (cần ≤ 250 ms), 0 dòng warning/error mỗi lượt, `appsflyer started` 1/1/1 mỗi API, FATAL 0/0/0
  (`t161-w-emu28-warmup.log`, `t161-w-emu26-warmup.log`).
- **Carry sang 1.6.2** (triage `final-161-review.md`): tách latch `PrefsWarmup` — nạp 4 file gốc trước, tính `names()`
  tuỳ chọn sau, thêm latch "late" nếu `waited=` API 26/28 > 0 (m1); re-đánh giá/gỡ dòng allowlist "ART monitor
  contention" nếu 0 lượt khớp trong log vòng xác nhận, vì bằng chứng duy nhất của nó là triệu chứng của lỗi `ClassValue`
  đã sửa (m2); canary "strictmode armed" cho trường hợp không bắn StrictMode vì chưa arm (T3 m9); copy `APPLOG*`/tee
  dòng `prefs warmup`/`appsflyer started` vào `$LOG` trước khi EXIT trap xoá log thô (T3 n1); kiểm trên máy đường
  AppsFlyer gỡ bộ lọc partner qua kịch bản US-GPP opt-out → allow → restart cùng process (T5); MIUI regex neo
  `AFj1sSDK$2` (G1-m1); thêm hạng mục "từ khoá R8 nhưng không phải lỗi nạp lớp" vào header allowlist (G1-m2);
  `carriedLost` bất đối xứng khi kill switch bắn lúc xả so với lúc phát (T4 m4, chỉ ảnh hưởng bộ đếm telemetry).
- **Chưa kiểm:** API 30/33; MAX + GPP trên máy (demo không có `admax_devKey` — chỉ unit, O10); Redmi 65c9b673 (máy
  không cắm vòng này); kịch bản tay ngoài checklist R-12 trên máy thật; CI (nhánh chưa push). §7 không đổi. **Chưa
  tag.**

### 1.6.2 "Cổng thiết bị giữ bằng chứng, allowlist hết dòng mồ côi, warm-up ưu tiên file gốc" (plan `docs/superpowers/plans/2026-09-29-sdk-1.6.2-plan.md`)

**Trạng thái 1.6.2 (2026-09-29, nhánh `feat/1.6.2`, HEAD `87b51c5`, Task 0–5 + R + G3 + E5):** xong: `PrefsWarmup` nạp 4 file gốc
trước rồi mới dò `af_app_id`/AppLovin, latch mở trong `finally` (review cuối 1.6.1 m1); `tools/device_gate.sh` giữ log thô app và
buffer crash trong `DOC/testing/device-gate-app-<serial>-<ts>-*.log`, chép dòng số đo vào log cổng (`metrics <chặng>: …`), canary
`strictmode armed`, `pm clear` vào log; allowlist gỡ dòng contention mồ côi, neo MIUI vào `AFj1sSDK$2`; nhãn log AppsFlyer
`consent tier=<tầng> in|outside GDPR scope` cho `not started`/`stopped`/`resumed`/`consent updated`; G3 thêm allowlist dò Samsung
cloud game của AppsFlyer trên API 26. `sdkVersion = "1.6.2"` (`40fe798`), **chưa tag, chưa push**. **API public/protected không đổi**
(`tools/api_dump.sh --check` → API unchanged 4486 dòng, diff so `b96fab3` 0 dòng; facade PASS, 9 khối mới đều internal được phép;
`t162-r-apidump.log`, `t162-r-facade.log`). Số đo ở cột "2026-09-29 (1.6.2)" của §3 G2; log `DOC/testing/t162-*.log` (không commit),
báo cáo `.superpowers/sdd/v162/{metrics-162.md,e-dev-report.md,e4a-report.md,e1-report.md,g3-report.md,t5-report.md,e5-report.md}`.
- Cổng: unit **1540/0/0/0** ×3, `@Ignore` 0 (`t162-r-full-{1,2,3}.log`, `40fe798`; `87b51c5` chỉ tooling); D7 10/10 trên `b96fab3..40fe798`
  (`t162-r-d7.log`); `:lintDebug` 0 lỗi, chênh so `b96fab3` 0 (`t162-r-lint-{head,base}.log`); cỡ file `LogEventServer.kt` 966,
  `AdsHelper.kt` 696, `PrefsWarmup.kt` 160 (≤ 160), `AppsflyerHelper.kt` 435, lớn nhất 793 (`t162-r-sizes.log`); test shell cổng
  87/87 tại `40fe798` (`t162-r-shell.log`), **101/101** tại HEAD (G3 +12, `t162-g3-green.log`; F2 +2 ca âm dấu `;`, `t162-f2-mutations.log`); `tools/release_gate.sh` PASS trên APK
  sha256 `b2824c5466a7012ebbfd737201578773bc6c155f3ee3269ab176992c544bc940`: 9 ad unit, 284/288 = 98%, zipalign 16 KB,
  `ClassValue lines in mapping=0`, `af_app_id` 1 (`t162-r-release-gate.log`).
- Cổng thiết bị `EVENTS=1500` (mọi lượt: `by sdk` 0/0, armed 1/1, `appsflyer started` n=1, FATAL/native/`am_anr` 0 ×6, `pm clear`
  Success): API 37 PASS R8 0/0/0, TotalTime 794/1349 ms (`t162-r-emu37-gate.log`); API 28 PASS R8 0/0/0, TotalTime 845/1348 ms (`t162-r-emu28-gate.log`); Redmi 65c9b673 API 34
  PASS R8 0/0/0, UMP `NOT_REQUIRED`, TotalTime 802/454 ms, xoay khôi phục, gói gỡ sạch, sha khớp (`t162-r-65c9b673-gate.log`);
  **API 26 hai lượt cùng APK, cả hai được giữ**: Task R **FAIL** R8 0/6/6 (`t162-r-emu26-gate.log`) — 3 cặp dòng AppsFlyer
  `AFe1hSDK$AFa1vSDK` dò `com.samsung.android.game.cloudgame.dev.sdk.CloudDevCallback` (lớp vắng ở AAR 6.16.2,
  `releaseRuntimeClasspath` và mapping, AppsFlyer bắt `Throwable`; FATAL/native/`am_anr` 0) → G3 (bằng chứng + allowlist neo lớp chủ,
  `t162-g3-red.log` → `t162-g3-green.log`, 4/4 đột biến đỏ `t162-g3-mutations.log`) → đếm lại offline log thô lượt Task R 0/0/0
  (`t162-g3-offline-recount.log`) → G3 chạy lại cùng APK **PASS** R8 0/0/0 raw 1/15/15 allowlisted, dòng Samsung không xuất hiện lại
  (`t162-g3-emu26-gate.log`). Galaxy A25 R5CX10P9EKZ (API 36, cắm lại sau Task R, cùng APK `b2824c54…`): **PASS** — `by sdk` 0/0, `strictmode armed` 1/1, `appsflyer started` n=1, R8 0/0/0, FATAL/native/`am_anr` 0 mọi lần đếm, `waited` 0/0 ms (`optional` 14/3 ms, `timeouts` 0), `onCreate main-thread ms` 30/22, TotalTime 761/496 ms, xoay không đổi, gói gỡ sạch (`t162-r-R5CX10P9EKZ-gate.log`).
- ANR: 0 ở mọi lượt 1.6.2, không lượt đỏ về ANR (lượt API 26 đỏ là R8, không phải ANR).
- Task 1b: **không cần** — `waited=0 ms`, `timeouts=0` mọi lượt; `optional prefs ms` cold/cold2 API 37 3/4, API 28 94/3, API 26 21/13
  (chạy lại 15/23), Redmi 32/1; max `min(waited, optional)` = 0 ms (`t162-r-*-gate.log`, `t162-g3-emu26-gate.log`). Main-thread ms
  `onCreate` (release) cold/cold2: API 37 32/26, API 28 85/29, API 26 63/89 (chạy lại 124/193), Redmi 33/11 — vượt mục tiêu (≤ 80
  emulator, ≤ 30 máy tầm trung, không chặn) ở API 28 cold, API 26 cold2 và cả hai chặng chạy lại, Redmi cold.
- Allowlist theo regex: contention gắn `ClassNotFoundException` 0 ở mọi log thô (tổng contention thô 0/3/2/0 API 37/28/26/Redmi chỉ tham
  khảo — ngưỡng "raw = 0" của plan sai thiết kế, R-B), thời gian main bị chặn 0; API 37 và Redmi 0 dòng allowlisted, API 28
  `appsflyer[./]lvl` 12 + `reportRequestStats2` 3, API 26 `AFj1sSDK$2` 6 + `appsflyer[./]lvl` 16 + `com.miui.referrer` 6 +
  `reportRequestStats2` 3 (`metrics-162.md` #14).
- Task 5 (AppsFlyer gỡ bộ lọc partner trên máy): **N/A** — dừng ở S4, form US không mở được sau rút consent bằng form EEA trong cùng
  process (UMP EEA ghi `GppSID=-1`); pid 6160: `all partners` 1, `stopped` 1, `resumed` 1, `cleared` 0, FATAL 0
  (`t162-t5-emu37-filter.log`, `t5-report.md`). Quan sát: bộ lọc `"all"` ở lại sau khi trả lời EEA trong cùng process vì GPP đọc là
  vắng (`AppsflyerHelper.kt:200-204`) — ghi ở CHANGELOG "Giới hạn đã biết".
- Nhãn log: trong khối 1.6.2 nhãn mới là `appsflyer not started: consent tier=<tầng> in|outside GDPR scope (gdprApplies=…)`; chuỗi
  dạng cũ ở khối 1.6.0 (dòng lịch sử, tầng DENIED kèm `gdprApplies`) giữ nguyên. Trên API 37 `key[cold start 2]` có
  `consent tier=UNKNOWN outside GDPR scope` 1 lần (`t162-r-emu37-gate.log`); test `AppsflyerHelper` 48 chạy 0 fail, `grep -c
  'tier=$tier'` = 0 (`t162-r-full-1.log`).
- **Carry sang 1.6.3:** kiểm trên máy đường GPP allow → `cleared` (cần thứ tự form khác, hoặc đổi thiết kế demo) và quyết giữ/sửa giới
  hạn bộ lọc `"all"` sau EEA (cần rà soát pháp lý consent); allowlist Samsung cloud game chưa từng khớp trên thiết bị, chỉ gỡ sau nhiều lượt
  API 26 sạch liên tiếp (G3 m2); nguyên nhân dòng Samsung xuất hiện lần đầu ở 1.6.2 chưa giải thích; `onCreate main-thread ms` API 26/28 và
  Redmi vượt mục tiêu (đánh giá ngân sách ANR máy yếu); nhãn `outside GDPR scope` mới chỉ có test cho `consent updated`, chưa cho `not
  started`/`stopped`/`resumed` (R-C); test ghim dấu `;` cuối regex `Rejecting re-init` Samsung/MIUI (G3 m1); log thô app (~450 KB mỗi
  file) tích luỹ trong `DOC/testing` chưa gitignore (R-B n2).
- **Chưa kiểm:** API 30/33; MAX + GPP trên máy (demo không có `admax_devKey`, chỉ unit — O10); regex Samsung
  trên thiết bị (chỉ offline); GPP allow → `cleared` trên máy (Task 5 N/A); CI (nhánh chưa push). §7 không đổi. **Chưa tag.**

### 1.6.3 "main-thread quy được về bước, allowlist neo hết lớp chủ, baseline façade = tag" (plan `docs/superpowers/plans/2026-09-29-sdk-1.6.3-plan.md`)

**Trạng thái 1.6.3 (2026-09-29, nhánh `feat/1.6.3`, HEAD kiểm `4f2125a`, Task 0–6 + R + F1a/F1b + E5):** xong: `AppPluginBase.initPlugin` in một dòng `initPlugin ms: manager=… meta=… helper+gate=… logevent=… total=…` để `onCreate main-thread ms` quy được về bước (Task 1); `LogEventServer` phân biệt "telemetry tắt" (`DATABUCKETS_BASE_URL` rỗng, một `Log.i` mỗi process) với "chưa init" (Task 1b); dòng LVL của AppsFlyer trong allowlist neo lớp chủ `AFf1lSDK$2` + lớp thiếu `AppsFlyerLVL$resultListener`, dòng `metrics` của cổng thêm `initPlugin ms` và `TotalTime` (Task 2, 3); test nhãn `outside GDPR scope` cho `not started`/`stopped`/`resumed` (Task 4); `BASELINE` façade mặc định `v1.5.0` + `ci.yml` `fetch-tags: true` (Task 5); Task 6 xem dưới. `sdkVersion = "1.6.3"` (`4f2125a`), **chưa tag, chưa push** (Task P: chờ CI và quyết định chủ dự án về tag). **API public/protected không đổi** (`tools/api_dump.sh --check` → `API unchanged (4488 lines)`; so `7806644` chỉ +2 dòng `internal` `headerHasUsSection$appplugin`, 0 dòng xoá; façade so `v1.5.0` PASS, 9 khối `new:` đều được phép; `t163-r-apidump.log`, `t163-r-facade.log`). Số đo ở cột "2026-09-29 (1.6.3)" của §3 G2; log `DOC/testing/t163-r-*.log` và `t163-f1b-*.log` (20 log bằng chứng được commit), báo cáo `.superpowers/sdd/v163/{metrics-163.md,final-163-review.md,minors-163.md}`.
- Cổng: unit **1555/0/0/0** ×3, `@Ignore` 0 (`t163-r-full-{1,2,3}.log`); D7 17/17 trên `7806644..4f2125a` (`t163-r-d7.log`); `:lintDebug` 0 lỗi, `7806644` 0 lỗi, chênh 0 (`t163-r-lint-{head,base}.log`); cỡ file `LogEventServer.kt` 970 (trần 970), `AdsHelper.kt` 696, `PrefsWarmup.kt` 160, `AppsflyerHelper.kt` 444, `AppPluginBase.kt` 280, `GppConsent.kt` 249 (`t163-r-sizes.log`); test shell cổng **115/115** (`t163-r-shell.log`); `AppsflyerHelperConsentTest` 57 test, `tier=$tier` = 0 (`t163-r-full-1.log`); `tools/release_gate.sh` PASS trên APK sha256 `842bbf9373f76883700193bca8e91f6a9746ed89e5819d64077ad90ca221a42e`: 9 ad unit, 98% lớp SDK đổi tên, zipalign 16 KB, `ClassValue lines in mapping=0`, `af_app_id` 1, chuỗi `initPlugin ms` 1 và `telemetry off` 1 (`t163-r-release-gate.log`). F1b chạy lại độc lập: 1555/0/0/0, 4488, façade PASS, 115/115 và 20/20 số của CHANGELOG khớp log thô (`t163-f1b-{unit,apidump,facade,shell}.log`).
- Cổng thiết bị `EVENTS=1500` (cùng APK `842bbf93…a42e`; mọi lượt PASS: R8 0/0/0, `strictmode armed` n=1/1, `appsflyer started` n=1, FATAL/native/`am_anr` 0 ×6, `pm clear` Success): emulator API 37 PASS (`t163-r-emu37-gate.log`); API 28 PASS, raw R8 1/7/7 đều allowlisted (`t163-r-emu28-gate.log`); API 26 PASS, raw 1/15/15 đều allowlisted (`t163-r-emu26-gate.log`); mỗi máy một lượt, không đỏ.
- **Máy thật — Galaxy A25 R5CX10P9EKZ (API 36), hai lượt cùng APK, cả hai được giữ:** lượt 1 **FAIL** (`t163-r-R5CX10P9EKZ-gate.log`): dòng FAIL duy nhất là StrictMode cold start 2 `by sdk=3` (`prefs first load` 0, `other` 3), cold start 1 `by sdk` 0, R8 0/0, FATAL/native/`am_anr` 0, monkey 3 seed exit 0, xoay không đổi, gói gỡ sạch. Lượt chạy lại 14 phút sau (`t163-r-R5CX10P9EKZ-gate-rerun1.log`): **PASS**, `by sdk` 0/0, FATAL/native/`am_anr` 0. **Phân loại (quyết định điều phối 2026-09-29, route A): trạng thái máy / gốc từ OS, không phải hồi quy 1.6.3; lượt 1 vẫn ghi FAIL, lượt chạy lại là bằng chứng.** Cả 3 vi phạm lượt 1 là `DiskReadViolation` phát sinh trong `system_server` của Samsung (`FreecessController.unfreezePackageForOLAF`, 12 dòng `Freecess` ở cold start 2 lượt 1, 0 ở lượt chạy lại và ở mọi log thô A25 khác) và tới luồng app qua binder từ `BillingClient.startConnection → bindService` tại `IapHelper.kt:137`, file 0 commit trong `7806644..HEAD` (không đổi từ `f710795`); OS bỏ đóng băng gói, không phải I/O của SDK, có xảy ra hay không phụ thuộc trạng thái đóng băng lúc đó. Sửa cấu trúc: DOC/67 1.6.4 task #1 (chuyển `IapHelper.connect` bind ra khỏi đường khởi động); CHANGELOG "Giới hạn đã biết" có một dòng.
- Máy thật Galaxy A25 (số khác): `prefs warmup waited=0/0 ms` (`optional` 3/5, `timeouts` 0); `onCreate main-thread ms` **31/40** (`initPlugin` manager/meta/helper+gate/logevent/total 16/1/11/0/28 và 26/0/14/0/40; cold2 vượt 30 ms, không chặn); TotalTime **1310/640 ms** (1.6.2: 761/496, cold start chậm hơn, không chặn); `am_anr` 0 ở mọi chặng.
- Máy ảo: `main-thread ms` cold/cold2 API 37 115/12, API 28 135/40, API 26 263/209; bước lớn nhất là `manager` (API 37 cold: `helper+gate` 66); vượt 80 ms ở API 37 cold, API 28 cold, API 26 cả hai chặng, không chặn; `total ≤ main-thread` và `initPlugin ≠ none` mọi lượt (6/6). TotalTime cold/cold2 API 37 2834/718, API 28 2309/1620, API 26 1006/993 ms.
- Allowlist theo regex: LVL neo mới khớp trên thiết bị API 28 (6) và API 26 (8); `AFj1sSDK$2` 6, `GetAppsReferrer` 6, `reportRequestStats2` 3 (API 26); `Long monitor contention` thô tổng 6 (API 28 1, API 26 4, Samsung lượt chạy lại 1) chỉ để tham khảo — thời gian main bị chặn 0, contention gắn `ClassNotFoundException` 0, không frame `com.app.plugin` → PASS theo luật 1.6.2 (`metrics-163` #15-reclass). `telemetry off` = 1, `not initialized` = 0 ở 8/8 log app cold1/cold2 (#18).
- ANR: 0 ở mọi lượt 1.6.3 (3 emulator + Samsung 2 lượt); không lượt đỏ về ANR (FAIL của Samsung lượt 1 là StrictMode).
- Task 6 (AppsFlyer, quyết định chủ dự án 2026-09-29): **"C: gỡ chỉ khi header hết US 7–12" + "Có, yêu cầu GRANTED"**, và trả lời phạm vi **"Có, chỉ nhánh mới"** (GRANTED chỉ đòi ở nhánh mới; luật 1.6.1 giữ nguyên). Sau khi SDK đặt bộ lọc partner `"all"` (GPP opt-out), lần start kế trong cùng process gỡ bộ lọc **chỉ khi** `IABGPP_HDR_GppString` được UMP viết lại không còn section US 7–12 **và** tầng consent là `GRANTED` (log `cleared (gpp=none, header không còn section US, tier=GRANTED)`); `GppSID=-1` một mình, chuỗi hỏng hay chuỗi vắng không gỡ (fail-closed ở mọi đầu vào đọc không được). `headerHasUsSection` là `internal` (+2 dòng dump). Review R-F (Fable): spec khớp từng mệnh đề, 2 ô PARTIAL và KDoc đã sửa (`4bae88a`). Giới hạn `GppSID=-1` một mình vẫn giữ (CHANGELOG "Giới hạn đã biết").
- Review cuối 1.6.3: F1a (code, consent, tooling, CHANGELOG) + F1b (số đo) — Critical 0 / Important 0; Minor F1a-m1 (CHANGELOG dòng nhãn có số dòng cũ + đột biến `logNotStarted(tier, null)` giết 2 test, không phải 1) đã sửa ở E5; kết luận sẵn sàng tag, có điều kiện E5 + commit vệ sinh chỉ docs/config + F3 kiểm lại trên head cuối (luật APK 0 dòng; 1555 / 4488 / PASS / 115 không đổi).
- **Carry sang 1.6.4** (id theo `minors-163.md` / `final-163-review.md`):
  - T5-m1: `tools/api_facade_check.sh:20-21` đầu file còn viết `BASELINE=v1.5.0` như ví dụ giả định; gộp khi đổi BASELINE lần tới.
  - T1-R1: đột biến "đặt sai `t0` / thứ tự ranh giới bước" sống sót (test-only; #13 trên máy là kiểm vận hành).
  - T1-R2: đột biến "API key rỗng ghi đè mặc định" sống sót (`dataBucketConfig` với meta-data rỗng chưa ghim, lỗ hổng có từ trước).
  - T1b-m1: hai lần đọc `endpoint` không đồng bộ ngoài luồng (chỉ lý thuyết; kết quả vẫn DROPPED).
  - T1b-m3: một `Log.i` "telemetry off" mỗi process cho host tắt log SDK (chấp nhận, đã ghi ở CHANGELOG).
  - T6-m2: nhánh `gpp == null` thừa trong `when` mới (chặt hơn cần thiết, giữ có chủ ý; gỡ sẽ chạm `src/main`).
  - F1a-m2: ba file sát trần G8 — `LogEventServer.kt` 970/970, `GppConsent.kt` 249/250, `AppsflyerHelper.kt` 444/445 — mọi thay đổi 1.6.4 ở các file này phải mở đầu bằng tách file, không nâng trần.
  - 1.6.4 #1 (DOC/67): chuyển `IapHelper.connect` bind BillingClient ra khỏi đường khởi động — gốc của ca Samsung `FreecessController` ở trên; khi lập kế hoạch #1, rà soát nhánh lưu trữ `archive/rogue-episodic-2026-09-24` commit `edad9e3` (bản vá "billing issues" của `IapHelper` từ phiên plugin bộ nhớ lạc quyền 2026-09-24, chưa từng được review).
  - Còn mở, không phải carry mã: T5-m2 (chứng minh tag `v1.5.0` phân giải được trên CI thật) đóng ở Task P #19; T6-M7 (đột biến `GppConsent.kt:106` `return null` → `return false`) đo tuỳ chọn ở F3.
- **Chưa kiểm:** Redmi 65c9b673 không cắm nên không có số 1.6.3 trên máy này; ngưỡng chặn `onCreate main-thread ms` trên máy thật API 26–28 bỏ khỏi 1.6.3 theo quyết định phạm vi 2026-09-29 (không có máy, hạng mục đã hạ); API 30/33; MAX + GPP trên máy (demo không có `admax_devKey`, chỉ unit — O10); đường `cleared` trên máy (không tới được theo cấu tạo UMP: form EEA ghi `GppSID=-1`, plan mục "Không lập task"; chỉ unit); CI (nhánh chưa push).
- **Publish:** draft PR #6 (`feat/1.6.3` → `main`); CI run `36591426012` trên `25ef42d`: `verify` success, `release-gate` success, bước `API facade unchanged vs v1.5.0` success (T5-m2 đóng: `fetch-tags` hiệu lực trên runner). Tag `v1.6.3`, run `release.yml` và sha `main` ghi ở ledger và khối 1.6.4 (commit sau tag làm `main ≠ v1.6.3`). §7 không đổi.

### 1.6.4 "billing nền + `isReady` đúng nghĩa, tách 3 file sát trần, N1, flavor utility/game, README" (plan `docs/superpowers/plans/2026-09-30-sdk-1.6.4-plan.md`)

**Trạng thái 1.6.4 (2026-09-30, nhánh `feat/1.6.4`, HEAD kiểm `51c553a`, Task 0–8 + R + F1a + F2 + E5):** xong: `IapHelper.init` dựng `BillingClient` và `startConnection` trên luồng nền `appplugin-billing` (T1, gốc #11 Samsung 1.6.3) và `restorePurchases` không coi lỗi store là "không sở hữu gì"; ba file sát trần G8 tách ra (`LogEventServer` 970 → 776, `GppConsent` 249 → 172, `AppsflyerHelper` 444 → 406; T2); test còn nợ 1.6.3 (T1-R1/R2, T6-m2, T1b-m1; T3, T3b); `sdk_health` thêm `init_ms` (T4; khoá Crashlytics `appplugin_version` đã có từ 1.4.0, không thêm khoá trùng); demo hai flavor `utility` (AdMob + Meta, 162 artifact) và `game` (227), `--variant` cho `device_gate.sh`/`release_gate.sh`/`bundle_size.sh`, CI chạy cả hai (T5); N1 — sau `DENIED` telemetry giữ tối đa 500 event trong bộ nhớ, chỉ gửi khi tầng đổi sang `GRANTED`/`PARTIAL` cùng process (T6, quyết định Q2 2026-09-29); README gốc v0 (T7); `LagrangianBudgetTest` hết đỏ ngẫu nhiên (T8, test sai chứ không phải sản phẩm). Hai sửa sản phẩm phát sinh từ vòng đo 1 trên `8a1e74f`: **F2B** (`IapHelper.isReady()` và cổng `purchase`/`restorePurchases` đúng nghĩa dưới Billing 9 auto-reconnect; `purchase()` trong lúc kết nối trả `false` ngay) và **F2-MAX** (app không có AppLovin không còn khởi tạo `AdsMaxMy`). `sdkVersion = "1.6.4"` (`8a1e74f`), **chưa tag, chưa push**. **API public/protected không đổi** (`API unchanged (4513 lines)`; so `v1.6.3` 0 dòng `-` ngoài `access$`, 28 dòng `+` `internal`; façade so `v1.5.0` PASS; `t164-r-apidump-2.log`, `t164-r-facade-2.log`). Số đo ở cột "2026-09-30 (1.6.4)" của §3 G2; log `DOC/testing/t164-r-*.log`, báo cáo `.superpowers/sdd/v164/{metrics-164.md,final-164-review.md,minors-164.md}`.
- **Vòng 1 và vòng 2 (không giấu lần đỏ đầu):** vòng 1 đo trên `8a1e74f` (APK `e33a45de…`) thấy hai thứ, đã sửa ở F2: (1) Samsung lượt 3 log `already connecting → Max retries reached → setup failed: 5` sau `connected` (`t164-r-R5CX10P9EKZ-gate-3.log`) → F2B; (2) API 26 `r8 strings=4` (`Rejecting re-init … AdsMaxMy$$ExternalSyntheticLambda2`, `t164-r-emu26-gate.log`) → F2-MAX. Vòng 1 API 28 còn `am_anr=1` ở 2/3 lượt (`t164-r-emu28-gate.log`, `…-rerun2.log`; `…-rerun1.log` PASS). Luật APK của plan (F2 đổi `src/main`) buộc dựng lại APK và đo lại toàn bộ: đầu tag là `51c553a`, APK `287f8b52c095…19e6`, mọi số dưới đây là vòng 2; log vòng 1 giữ trong `DOC/testing`.
- Cổng: unit **1584/0/0/0** ×3, `@Ignore` 0 (`t164-r-full-2-{1,2,3}.log`); D7 19/19 (`t164-r-d7-2.log`); `:lintDebug` 0 lỗi, `v1.6.3` 0, chênh 0 (`t164-r-lint-{head,base}-2.log`); shell **119/119** (`t164-r-shell-2.log`); cỡ file `LogEventServer` 776, `LogEventTransport` 226, `GppConsent` 172, `GppBits` 86, `AppsflyerHelper` 406, `AppsflyerGppFilter` 55, `IapHelper` 583, `BillingSetupOutcome` 30, `AppPluginBase` 282, `SdkHealth` 62, `TelemetryConsentGate` 266, `AdsHelper` 696, `AdsNetworkRegistry` 183, lớn nhất `AdsMobPlNtFull` 793, > 800 = 0 (`t164-r-sizes-2.log`); trần `SdkFileSizeGateTest` = số đo + 10 (LES 786, GppConsent 182, AppsflyerHelper 416); `tier=$tier` = 0. Release gate `utility` PASS: 9 ad unit, 297/301 = 98%, zipalign 16 KB, `ClassValue` 0; `game` PASS (`t164-r-release-gate-{utility,game}-2.log`). Tải về `utility` 7 584 849–7 641 195 B; 162 artifact, 0 nhóm game (`t164-r-bundle-size-2.log`, `t164-r-deps-2.log`).
- Cổng thiết bị `EVENTS=1500`, APK `287f8b52…19e6` xác minh sha ở mọi lượt (lane kiểm sha trước mỗi lượt — cổng chưa in sha; bằng chứng nội tại: dòng `không đăng ký mạng MAX` của F2-MAX có ở 18/18 log thô cold1/cold2 vòng 2, 0/8 ở vòng 1; 8 985 364 B ở `t164-r-crashkey-abc-2.log`):

| Máy | Kết quả | Chi tiết |
|---|---|---|
| Galaxy A25 R5CX10P9EKZ (API 36) | **3/3 PASS** | `by sdk` 0/0 ×3, Freecess 0/0, đếm billing (`setup failed`/`already connecting`/`Max retries`/`connected`/`store lỗi`) = 0/0/0/1/0 ở cả 6 chặng, main-thread 16/20, 36/21, 15/29 ms, TotalTime 1391/602, 763/606, 592/590 (`t164-r-R5CX10P9EKZ-gate-2-{1,2,3}.log`) |
| Emulator API 28 | PASS lượt đầu | `am_anr` 0 ×6, R8 0, `by sdk` 0, armed 1 ×2 (`t164-r-emu28-gate-2.log`) |
| Emulator API 26 | PASS | R8 0/0/0, dòng `y5` biến mất, `am_anr` 0 ×6 (`t164-r-emu26-gate-2.log`) |
| Emulator API 37 | 3/4 lượt đỏ `am_anr` (giữ), **A/B 6/6 sạch → contention** | dòng phân loại bên dưới (`t164-r-emu37-ab-{1..6}.log`) |
| Redmi 65c9b673 | chưa đo | không cắm (`get-state` → `device not found`) |

- API 37 utility vòng 2 (APK `287f8b52…19e6`, `EVENTS=1500`): 4 lượt (`t164-r-emu37-gate-2{,-rerun1,-rerun2,-rerun3}.log`), 1 PASS (rerun1) và 3 FAIL `am_anr=1` ở seed 1 — giữ nguyên trong bảng; mọi mục khác PASS ở cả 4. Các lượt đó cả guest bị bỏ đói (bind→app 1,3–24 s, cold1 16–56 s, monkey 93–291 s/seed, system/systemui/launcher ANR cùng cửa sổ, app chỉ 22–34% CPU). **Phân loại §4.3 = CONTENTION**: A/B trên cùng AVD, host yên (2026-09-30 05:55–06:17; Docker 0, không emulator khác, 0 Gradle ngoài 2 daemon nhàn rỗi của IDE 0–5 % CPU có mặt từ B1 trở đi; load1 sau khi boot 5,5–10,2 là của chính emulator), mỗi APK 2 lượt: 1.6.3 `842bbf93` 2/2, 1.6.4 utility `287f8b52` 2/2, 1.6.4 game `e2dd7849` 2/2 — cả 6 lượt `am_anr` 0, cold1 952–1138 ms, monkey 28–41 s/seed, không khác biệt giữa các APK (2/2 lượt sạch yên của cùng APK); yếu tố host cụ thể lúc vòng 2 chưa xác định (`t164-r-emu37-ab-{1..6}.log`, 1,3 = 1.6.3; 2,4 = utility; 5,6 = game; `t164-r-anr37-report.log`).
- Thiết bị (số khác, `metrics-164` #14–#20, #24): `prefs warmup waited=0 ms`, `timeouts=0` ở 9/9 lượt; `sdk_health` `sdk_version=1.6.4`, `init_ms` = main-thread, `networks=admob,meta,` ×6; khoá Crashlytics `appplugin_version=1.6.4` có trong file khoá trên máy (`t164-r-crashkey-emu37-2.log`); telemetry tắt: `telemetry off` 1, `not initialized` 0 ở cả 6 cổng; allowlist `AFf1lSDK$2` 10, `AppsFlyerLVL$resultListener` 10, `reportRequestStats2` 6, `AFj1sSDK$2` 3, `GetAppsReferrerStateListener` 3; `Long monitor contention` API 37 0, API 28 4, API 26 7, Samsung 4 tổng, không frame `com.app.plugin`.
- **N1 trên máy (T6, #24, APK debug `utility` `d5c52b47…`, API 37, lần thử 4):** POST trước T1 0; T1→T2 (DENIED, ~93 s) POST **0**; `hold-denied` 7; `flushed 7 held after DENIED` 1; POST 45 s sau GRANTED 8; `am_anr` 0. Lần 1–3 vô hiệu (form consent không tới được trên máy ảo khởi động nguội chậm) và được giữ trong log (`t164-r-n1-emu37-2.log`).
- **F2 (sản phẩm):** F2B — gốc "setup failed: 5": Billing 9 với `enableAutoServiceReconnection()` trả `BillingClient.isReady() = true` ở mọi trạng thái (`BillingClientImpl.isReady()` = `if (zzG) return true`, đọc bytecode `billing-9.0.0`), nên `IapHelper.isReady()` (public) và cổng `purchase`/`restorePurchases` không bao giờ chặn; lượt khôi phục lúc vào foreground chạy giữa lúc kết nối đầu, thư viện thử kết nối lại, va vào kết nối đang chạy → `DEVELOPER_ERROR` 5 (có từ 1.3.0). Mức: Important, có từ trước; ở 1.6.3 còn tệ hơn (`queryOwned` bỏ qua mã lỗi → có thể thu hồi quyền lợi INAPP), T1 (`inappOk && subsOk`) đã chặn phần đó. T1 còn mở một cửa sổ mới cỡ micro-giây (client công bố trước `startConnection`, cả hai nay trên luồng nền) có thể để mua hàng chết cả phiên; cùng sửa đóng cả hai. Sửa: `usable = connectionStarted && trạng thái ∉ {CONNECTING, CLOSED}` (DISCONNECTED sau khi đã start vẫn "sẵn sàng" để kết nối lại lười như cũ) và `DEVELOPER_ERROR` khi đã `CONNECTED` = kết quả "already connecting" tới trễ, không phải lỗi. Hành vi thấy được: `isReady()` `false` trong lúc kết nối đầu (0,6–3,1 s trên máy cổng), `purchase()` trả `false` ngay với `IapResult.Failed(-1, …)` thay vì lỗi sau ~3 s; đã ghi ở CHANGELOG. Trên máy: 0/0/0 lỗi billing ở 6/6 chặng Samsung vòng 2. F2-MAX — `AdsNetworkRegistry.build()` đọc `AdsMaxMy.instance` khi chưa kiểm `isMaxSdkAvailable`; ART (Android 8) log `Rejecting re-init … NoClassDefFoundError AppLovinSdk$SdkInitializationListener`; không crash; sửa gate bằng `isMaxSdkAvailable` + một log debug. Review R-F2B (Fable, bytecode + 9 đột biến), R-F2C, R-F2M (RV-A): PASS, 0 Critical / 0 Important. F2C: bốn mục test-only (S3, `onDone`, thứ tự xả DENIED, shell V4), mỗi mục có đột biến đỏ.
- Review cuối 1.6.4: F1a (Fable, code) — 0 Critical / 0 Important, 4 Minor; F1b (số) và F3 (luật APK) chờ sau E5.
- **Carry sang 1.6.5** (id theo `minors-164.md` / `final-164-review.md`):
  - T1-m4: `productCache` (HashMap) đọc/ghi qua nhiều luồng (có từ trước) — dòng đầu của hạng mục IAP 1.6.5, cần một test đỏ. (T1-m1, KDoc "callback về main" sai: **đã sửa ở F2B**, `IapHelper.kt:115`.)
  - F2B-RB: mệnh đề `CLOSED` của cổng sẵn sàng chưa có test (SDK không bao giờ gọi `BillingClient.endConnection`; không tới được).
  - T6-m1: dòng log flush `", N failed"` in ở cả hai dòng (`TelemetryConsentGate.kt:163-164`, cosmetic).
  - T6-m3: mục đã vào hàng đợi gửi trước lúc GRANTED → DENIED trong cùng process vẫn gửi lại — cần quyết định chủ dự án (huỷ hay đỗ hàng đợi khi thu hồi); 1.6.5/1.7.0.
  - T3-m1: D2 `"" to ""` thay vì mặc định `Constants` (không đỏ được hôm nay; làm khi mặc định đổi).
  - F1a-m3: `AdsMobPlNtFull.kt` **793/800** — file lớn nhất; lần chạm tới phải tách trước (như T2).
  - F2C-m1: hai dòng trống liền nhau ở `TelemetryConsentGateEdgeTest.kt:311-312`.
  - F2M-m1: `import com.applovin.mediation.ads.MaxAdView` thừa ở `AdsHelper.kt:32` (không sinh bytecode); F2M-m2: "chỉ API 26 log dòng đó" suy từ lịch sử ART, chưa đo; F2M-m3: cảnh báo biên dịch `AdsMaxAbsentRegistryTest.kt:61:28` (`Any?`/`Any`).
  - ANR37 → 1.7.0 T8/T13: `device_gate.sh` tự nhận contention (ghi `vm_stat`/swap host và bind→app mỗi lượt, nhãn `CONTENTION-SUSPECT`, kéo `/data/anr` trước khi kill trên AVD `-read-only`).
  - Đã đóng ở 1.6.4: T1-m2/T1-m3/T6-m2/T5-m1/T7-m5 (F2), T5-m2 (DOC/65 đường dẫn APK, E5), F1a-m1 (DOC/67), F2B follow-up (DOC/64 §D), F1a-m2 (CHANGELOG lấy số của E2); ghi nhận, không sửa: T1-ruling, T2-ruling, T3b-m1/m2, T6-rulings, T8-m1.
- **Chưa kiểm:** Redmi 65c9b673 không cắm (không có số 1.6.4 trên máy này); API 30/33; MAX trên máy (O10); mua/khôi phục với tài khoản Play thật; đường `cleared` của AppsFlyer trên máy, 8 định dạng quảng cáo sau consent (Bỏ khỏi 1.6.4); README sau 7 chỗ bổ sung chưa qua agent mới lần hai; CI (nhánh chưa push, chưa PR, chưa tag).
- **Publish:** draft PR #7 (`feat/1.6.4` → `main`); CI run `36646159324` trên `8e44f67`: `verify` success (allowlist offline, API surface unchanged, façade vs v1.5.0), `release-gate` success cả hai flavor (`utility` AdMob + Meta, `game` đủ adapter). Tag `v1.6.4`, run `release.yml` và sha `main` ghi ở ledger và khối 1.7.0 (commit sau tag làm `main ≠ v1.6.4`). §7 không đổi.

### 1.7.0 "Đo được hiệu quả của ads và của SDK" (plan `docs/superpowers/plans/2026-09-30-sdk-1.7.0-plan.md`)

**Trạng thái 1.7.0 (2026-10-02, nhánh `feat/1.7.0`, mã cuối `336525a`, APK `utility` sha256 `7ad240d2…cc67` dựng ở `222a34f`; số đo: `.superpowers/sdd/v170/metrics-170.md`, CHANGELOG `[1.7.0]` "Đã kiểm"):** xong T1–T14 (envelope và chuỗi event v2, show/click, doanh thu và `imp_id`, consent, phiên, event bền, `logevent_control`, demo `utility` → sink cục bộ, backend production-ready, bảng chỉ số SQL, importer AdMob, gcp_guard và runbook, cổng G11 + H6a + phiên hằng ngày, tài liệu), cộng sửa tại chỗ: T3b (placement gọi show), P-1 (kiểm lại lúc gửi), ba test flaky, TF2–TF5 (công cụ đo), F-BE, F2-be (phân vùng `received_date`), F2-be2 (chặn thời gian BigQuery). Cổng: unit 1653 ×3, dump 4713 (`-` = 0), façade PASS, D7 24/24, shell 151 + guard 111, release gate `utility` + `game` PASS, Size max 7 652 009 B, gate 37/28/26 + Redmi PASS, **G11 4/4 máy**, **H6a 12/12 phiên**, consent funnel và `logevent_control` PASS, **G13** p95 6,7 ms (sink JSONL), parity Δ = 0, runbook PASS (chi tiết ở §3 G6/G11/G13 và §4 cột "1.7.0").

- **Chưa kiểm (không giấu):** Samsung R5CX10P9EKZ (máy không cắm); H5 (pipeline trên Cloud Run + BigQuery thật, chờ billing) và H6b (đối soát giá trị, ad unit thật, Q3) = program gate mở; tỉ lệ đồng ý consent (AdMob Privacy & messaging, chủ dự án xuất); CI và release (#21–#22, P).
- **Carry sang 1.8.0** (id theo `minors-170.md`, `final-170-review.md`):
  - SDK / `src/main` (luật APK): R-P1-m: comment "CHỈ DÙNG TRONG TEST" mồ côi ở `LogEventControl.kt:129` chuyển lên trên `resetForTest` (F1a m5; chỉ comment, ABI không đổi); trường `slot` riêng cạnh `placement` trong hợp đồng event v2 (T3b; `ad_request`/`ad_fill` hiện theo slot); R-3b m2: impression muộn sau lượt show/get kế tiếp trên cùng slot nhận placement mới; R-3: `precision_type` của MAX là −1, cần mapping chuỗi → 0–3; R-1 (F1a m7): test E1 sót 2 đột biến (`consent_tier` gán cứng, `schema_version` kiểu chuỗi), `close()` có thể phát hai lần nếu callback tới ngoài main, `LoadRequest` là data class, regex quét tên event quá hẹp; file sát trần: `LogEventTransport` 267/270, `DecisionLogger` 552/555, `TelemetryConsentGate` 266/270.
  - Công cụ đo: cổng sink theo lane (`local_sink.sh --port`, `reverse tcp:8089 tcp:<port>` trong `device_gate`/`daily_session`/`measure_gate`) để E-real ∥ E-emu (L3) chạy song song; `device_gate.sh` FAIL khi JSONL +0 (F1a m4, lời hứa R-8) và in dòng tiền kiểm `precheck docker=0 sink=up` (#20); R-TF5 m1 (`mg_start` in đầu ra chứa `TotalTime` trước khi FAIL ở hạn), m2 (trap dọn adb nền khi Ctrl-C), `uiautomator dump`/`logcat -d`/`dumpsys` chưa có hạn → bọc adb có hạn; R-13 m3 (dòng của app khác trong H6a, làm nếu H6a FAIL — chưa xảy ra), m4 (tính lại số trước khi công bố), m10.
  - Backend: R-12 (T12): nhiều phần tử `header`, `matched > requests`, BOM UTF-8 (fail-closed tới khi có bản xuất thật, P9), ngoại lệ ngoài miền còn sót; R-11 (T11): `--set-env-vars` là chuỗi tự do ở `deploy` có cổng chủ dự án; R-9 (T9): `bq_project` không rỗng, comment alias `main.py:342-347`, khoá chính xác `test_paywall.py:93`; R-10 m1: lọc phân vùng D−1..D chỉ ở câu đếm hàng, view và bảng đích của MERGE quét toàn bộ (chi phí BigQuery thật) → H5; F1a m3: `gcp_guard` vô hiệu hoá `~/.bigqueryrc` (`BIGQUERYRC=/dev/null`); chế độ dry `GCP_GUARD_DRY` (plan #19 nêu, guard không có) chỉ xét như tính năng thiết kế cố định có review, không thêm cửa vòng; loadtest lớp emulator BigQuery nhiễu (1762 lỗi lần đầu) — chỉ là thông tin (R-5).
  - Kiểm ở H5 (BigQuery/Cloud Run thật, emulator không thấy): hàng `ad_revenue` tới muộn > 2 ngày còn trong bảng (F2-be); `/health` 503 trong hạn của probe Cloud Run (F2-be2).
  - Quy trình: F1a m2: 9 commit squash của lane Sonnet chỉ mang trailer Opus — ghi ở `retro.md`, không viết lại lịch sử.
  - Bỏ khỏi carry (đã xong trong 1.7.0): R-14 (tài liệu), F1a m6 (câu dạng v1 ở DOC/46, DOC/62, DOC/67), R-P1 chữ CHANGELOG, `DecisionLogReplayTest` và `TelemetryConsentGateEdgeTest` N1-d flaky, rò rỉ giữa test (TF3), R-10 m2 và R-2 m4 (F-BE), F1a I1 và m1 (F2-be).

### Quyết định phạm vi 2026-09-29 (chủ dự án): bỏ gì, giữ gì từ 1.6.3 tới đích

> **Kế hoạch chính từ 1.6.4 tới lúc ghép app là DOC/67** (viết cùng ngày, sau mục này). DOC/67 đổi thứ tự:
> **1.7.0 = đo lường hiệu quả ads/SDK** (đường ống backend + bảng DOC/62 §2.1) đi trước, **1.8.0 =
> IAP/paywall** (đề cương "1.7.0" bên dưới chuyển thành 1.8.0), **1.9.0 = đòn bẩy eCPM + brain**, rồi 2.0.0
> Phase C. Khi hai tài liệu lệch nhau, DOC/67 thắng.

**Căn cứ đo được (2026-09-29):** hai app đang dùng SDK (Speaker Clean, Invoice Maker) chỉ import
`com.app.plugin.{ads, until, segment, firbase, logevent, app, attribution}`; **không app nào import `brain`,
`iap`, `paywall`, `offerwall`, `crm`**; `~/AndroidStudioProjects` không có repo game. SDK là nội bộ (doc 61),
không bán cho studio. Mọi mục dưới đây không lập task nữa; muốn mở lại phải có consumer thật nêu tên.
**Thứ tự (chủ dự án 2026-09-29): app tiện ích trước, game để sau** — mọi task từ 1.6.4 tới 2.0.0 chỉ cần đo
trên cấu hình AdMob + Meta của Speaker Clean/Invoice Maker; MAX và adapter game giữ build xanh, không lập cổng
thiết bị riêng cho tới khi có game.

**Bỏ khỏi lộ trình (không làm):**

| Nguồn | Mục bỏ | Lý do |
|---|---|---|
| DOC/23 C.2, phase 1 | Payment routing đa kênh: web shop D2P, Epic-style store, `purchase(sku)` router, receipt validation cross-channel, deep-link giữ attribution | Hai app bán qua Play Billing; D2P là kinh tế của game top-grossing |
| DOC/23 C.3, phase 1–2 | Paywall builder từ xa, A/B holdout cho paywall, dunning/win-back, cầu nối VIP ↔ virtual currency, battle pass | Speaker Clean đã có `PaywallPlaybook`/`NonPayerPlaybook`/`PaywallSessionMutex` trong app; battle pass, virtual currency là khái niệm game |
| DOC/23 C.4, phase 3–4 | Offerwall adapter, reward engine, event bus engagement, webhook Braze/OneSignal | App tiện ích không có offerwall, không có CRM bên thứ ba |
| DOC/23 C.5, phase 0 | AAK/SKAN conversion-value, clean room export, MMM | AAK/SKAN là iOS; clean room/MMM cần đội data |
| DOC/23 C.5, phase 0 | Server-side entitlement service | Không có kênh mua thứ hai để cần nguồn sự thật chung |
| DOC/23 C.6, phase 4–5 | Cross-modal brain (REWARDED_AD vs IAP_OFFER), full action space, on-device inference | DOC/23 tự ghi lớp này chỉ có giá trị khi C.2–C.5 tồn tại |
| DOC/26 A | Control Plane (dashboard 5 module cho đội vận hành) | Không có đội vận hành riêng; Firebase Console + Remote Config đang làm việc này |
| DOC/60 F.3.6, Phase C cũ | Ký config từ xa bằng Ed25519 | Sinh ra để bảo vệ `PurchaseRouter` (đã bỏ); Remote Config của Firebase đã có xác thực |
| DOC/60 F.3, Phase C cũ | Play Integrity như cổng riêng của SDK (claim SDK Console, verdict cho mọi lời gọi) | Doc 60 mục "SDK nội bộ" ghi "Không cần". **Giữ** `iap/PlayIntegrityGuard.kt` (58 dòng, classic request theo `purchaseToken`) vì nó thay bản fork của Speaker Clean khi IAP về SDK (1.7.0) |
| DOC/60 K.4 | DexGuard / Promon / Appdome | Hardening cho SDK bán ra ngoài |
| Phase C cũ | Tách `ads-gma` và `ads-meta` thành hai module riêng | Cả hai app dùng cả AdMob lẫn Meta; tách thêm không ai hưởng |
| Phase C cũ | `init(application, config)` + `Config.Builder` | Đổi hợp đồng tích hợp của cả hai app; **hoãn**, chỉ xét gộp lúc app migrate sang AAR (doc 61) |
| 1.6.3 carry | Ngưỡng chặn main-thread máy thật API 26–28 | Không có máy; không đo được thì không để trong danh sách chờ |
| 1.5.0 O10 | `admax_devKey` cho demo (MAX + GPP trên máy) | Chưa có consumer nào dùng MAX; giữ unit test là đủ |
| §7 #4 | Backport bản vá Full2 sang fork Speaker Clean | Hạ ưu tiên; bỏ hẳn nếu app migrate sang AAR |

**Đóng băng (giữ code, không lập task mới, tách ra module tuỳ chọn ở 2.0.0) — chỉ phần game-only:**
`iap/BattlePass.kt`, `iap/RecurringGrants.kt` (VIP ↔ virtual currency), `iap/PurchaseRouter.kt` (D2P),
`offerwall` (255 dòng), `crm` (214 dòng), `AdsBrain.chooseCrossModal`/`shouldOfferIap` (cross-modal). Không xoá
(brain đã port sang iOS, `monetization-brain-ios`).

**Mở lại (chủ dự án 2026-09-29: "thu hẹp nhưng vẫn phải đầy đủ tính năng"):** hai nhóm dưới đây có code nhưng
chưa app nào dùng; chúng là phần còn thiếu để SDK **đầy đủ** cho một app tiện ích kiếm tiền bằng quảng cáo +
subscription, và là đòn bẩy để app hơn đối thủ. Lên chuẩn phát hành ở **1.7.0** (mục riêng dưới đây).

1. **IAP / subscription / entitlement / paywall** — `iap/IapHelper.kt` (565, bọc Play Billing),
   `iap/Entitlements.kt` (225, một nguồn sự thật → `setRemoveAdsMask`), `iap/SubscriptionLifecycle.kt` (338,
   dunning/grace/win-back, cấu hình JSON có version), `iap/FxRates.kt` (63, giá về USD hoặc `null`),
   `iap/PlayIntegrityGuard.kt` (58), `paywall/PaywallManager.kt` (281, paywall từ xa có A/B + holdout, SDK không
   vẽ UI). Lý do: Speaker Clean và Invoice Maker đang **mỗi app fork billing riêng** (`PaywallPlaybook`,
   `EntitlementCrypto`, `PaywallSessionMutex`…); "subscriber vẫn thấy quảng cáo" là anti-pattern đã thấy ở app
   đối thủ (doc 27 B.3) và chỉ tránh được khi entitlement và remove-ads là một nguồn.
2. **Brain (tần suất quảng cáo theo người dùng, holdout A/B)** — `brain/*` (21 file, 4.007 dòng), cắm ở
   `InterstitialController` sau mọi cổng tĩnh (chỉ siết, không nới), policy qua khoá Remote Config
   `brain_policy_android` (`FirHelperConfig`), `AdsBrain.enabled` mặc định tắt, `rolloutFraction` + `HoldoutManager`.
   Lý do: đây là thứ duy nhất trong SDK có thể nâng ARPDAU **mà không giảm D1/D7** (mục 4 dòng "Giữ chân"); luật
   tần suất tĩnh (`ads_policy_android`) là mức sàn mọi app đối thủ đều có.

**Định nghĩa "đầy đủ tính năng" cho SDK này (chuẩn nhận task từ 2026-09-29):** một app tiện ích hoặc game của
chủ dự án chỉ cần `appplugin` (+ `appplugin-max` nếu là game) để có trọn: (a) quảng cáo 5 định dạng + Full2 +
luật tần suất + remove-ads; (b) consent UMP/TCF/GPP + Consent Mode v2 + cổng cho mọi SDK bên thứ ba; (c) đo
lường Firebase/AppsFlyer/`logevent` + ad revenue + tROAS + IAP revenue theo kênh; (d) subscription/IAP/
entitlement/paywall từ xa; (e) tối ưu tần suất theo người dùng có holdout. Tính năng nào ngoài (a)–(e) phải chỉ
được consumer thật. **"Chạy tốt, ổn định, không bug"** = mọi bản tag đều qua G1–G10 (mục 3) trên 3 máy, và
(d)/(e) phải có cổng thiết bị riêng như ads đã có trước khi được gọi là "xong". **"Vượt đối thủ"** = bảng mục 4
(so app dùng SDK với ngưỡng Play/AdMob, không so SDK với SDK) cộng hai dòng mới: subscriber không thấy quảng cáo
= 100% (đo bằng `ad_show_fail reason=remove_ad` sau `grant`), và ARPDAU nhóm brain ≥ nhóm holdout với D1/D7
không giảm có ý nghĩa.

**Giữ (tốt cho hai app đang dùng):**

- **1.6.4:** tách 3 file chạm trần G8 (`LogEventServer.kt` 970/970, `GppConsent.kt` 249/250,
  `AppsflyerHelper.kt` 444/445 — tách trước, không nâng trần); test còn thiếu T1-R1 (thứ tự bước `initPlugin`),
  T1-R2 (`dataBucketConfig` meta rỗng), T6-m2 (guard `gpp == null` thừa), T1b-m1 (`endpoint` đọc ngoài luồng);
  T5-m1 (chú thích `api_facade_check.sh:20-21`); dời `IapHelper.connect` (bind `BillingClient`) khỏi đường
  khởi động nếu chủ dự án chọn hướng A cho #11 Samsung 1.6.3; N1 (telemetry sau `DENIED`, pháp lý — chờ chốt);
  CI `device-gate.yml` API 30 sau publish (đọc run, ghi ledger, không chặn).
- **Chưa rà lại (giữ nguyên trạng thái chờ):** O5, O8, #18 (`gma_load_app_context`), N5, N6, 20 lớp public
  chờ `internal`, `carriedLost` (ứng viên 2.0.0 vì đổi dump).

### 1.7.0 "IAP, paywall và brain đạt chuẩn phát hành" (đề cương cũ; theo DOC/67 §4 nội dung này là 1.8.0, còn 1.7.0 là "Đo được hiệu quả của ads và của SDK")

Mục tiêu: hai app tiện ích bỏ fork billing, dùng `iap`/`paywall` của SDK; brain bật được trên 1 placement thật
với holdout. Chưa lập task chi tiết; đề cương để planner lấy làm brief:

1. **Đối chiếu fork:** đọc `PaywallPlaybook`, `NonPayerPlaybook`, `PaywallSessionMutex`, `EntitlementCrypto`
   của Speaker Clean và phần billing của Invoice Maker; liệt kê hành vi nào SDK chưa có (dự kiến: mutex phiên
   paywall, mã hoá entitlement lưu cục bộ, playbook cho non-payer). Bổ sung vào SDK, không chép nguyên.
2. **Cổng thiết bị cho IAP** như ads đã có: Play Billing test account trên máy thật (mua, huỷ, grace, restore, mở
   lại app), StrictMode by-sdk = 0 trên đường `IapHelper.connect` (đang là #11 Samsung 1.6.3 — dời bind khỏi
   khởi động ở 1.6.4), `ad_show_fail reason=remove_ad` = 100% sau `grant`.
3. **Brain bật được:** khoá `brain_policy_android` có schema + version + kill switch; `rolloutFraction` từ Remote
   Config; log `brain declined`/`holdout` vào `logevent`; đo ARPDAU và D1/D7 nhóm brain vs holdout trên 1 placement
   của 1 app ≥ 2 tuần trước khi bật rộng. `chooseCrossModal`/`shouldOfferIap` giữ đóng băng.
4. **Cổng chung:** G1–G10 trên 3 máy; unit cho `iap`/`paywall`/`brain` có đột biến đỏ; ABI dump chỉ thêm, không
   đổi; CHANGELOG "Phải làm khi nâng cấp" cho từng app.

**Xong khi:** một app (Speaker Clean hoặc Invoice Maker) chạy bản release với billing hoàn toàn từ SDK qua cổng
thiết bị 3 máy; brain bật 1 placement có số holdout; 0 crash/ANR mới trên Play Console sau 7 ngày.

### Phase C — 2.0.0 "Tách module tuỳ chọn, chốt API" (phạm vi chốt 2026-09-29)

1. **Tách MAX + adapter game + phần game-only (`BattlePass`, `RecurringGrants`, `PurchaseRouter`, `offerwall`,
   `crm`) thành module tuỳ chọn** — ưu tiên cao nhất: đóng #8 "1 AAR kéo 227 artifact; app tiện ích gánh cả
   adapter game". Hình dạng: `appplugin` (core + GMA + Meta + consent + logevent + firbase + `iap` + `paywall` +
   `brain`, đủ cho hai app tiện ích) + `appplugin-max` + `appplugin-game` (tuỳ chọn). Không tách `ads-gma`/`ads-meta`.
2. **`explicitApi()` + `resourcePrefix = "appplugin_"`** — rẻ, chặn lỗi thật (doc 60: resource `black`, `white`
   trùng tên với app); đi cùng mục tiêu G8 `internal` ≥ 70%.
3. **Adapter GMA Next-Gen** — Google đưa GMA legacy vào maintenance mode (2026-07-06); ảnh hưởng cả hai app.
4. **Tách `AdsHelper` ≤ 800 dòng/file** (**đã tách ở 1.5.0: `AdsHelper` 692, 0 file > 800; 2.0.0 = seams: `AdsMobPlNtFull` 791 → 581, seam core → MAX/game, D1**) — kế hoạch 12 file ở `research-g8.md` mục 2, ước 22–25 giờ agent, rủi ro
   hồi quy ở đường hiện quảng cáo → cần cổng thiết bị đủ 3 máy trước khi tag.

**Xong khi:** G8 đủ (`internal` ≥ 70%, `explicitApi` bật, `apiCheck` chặn được một thay đổi ABI thử nghiệm);
Speaker Clean/Invoice Maker chỉ kéo `appplugin` mà không có artifact MAX/game trong `dependencies` của bản
release; 4 mục trên có cổng G1–G3 đo lại trên 3 máy.

---

## 6. Quy chế thực thi cho agent (Opus 5.5)

Áp cho mọi task trong plan. Vi phạm một điều là task bị trả lại.

1. **Một task, một agent mới, một commit** (Điều 17). Agent đọc `Global Constraints` của plan và task của
   mình; không đọc trước code của task khác để "làm luôn".
2. **TDD thật** (Điều 6.5): viết test → chạy thấy **đỏ** → lưu output đỏ vào `DOC/testing/<task>-red.log`
   → sửa → xanh → lưu `DOC/testing/<task>-green.log`. Test xanh ngay lượt đầu là chưa biết gì.
3. **Bằng chứng, không lời nói** (Điều 6.2): số nào ghi vào doc hay commit message phải có lệnh đo và log
   kèm. Log chỉ được tin khi reviewer chạy lại độc lập.
4. **Bản release** (Điều 5.2): task nào đụng ads, consent, R8, resource thì phải chạy lại
   `tools/release_gate.sh`; task nào đụng đường hiện quảng cáo thì chạy `tools/device_gate.sh`.
5. **Không làm ngoài phạm vi:** không tag, không publish, không sửa repo app (`~/AndroidStudioProjects/*`
   chỉ đọc), không đổi hành vi mặc định mà CHANGELOG không ghi ở "Phải làm khi nâng cấp".
6. **Thiết bị:** đặt `ANDROID_SERIAL`; không dùng Samsung `R5CX10P9EKZ` và Redmi `65c9b673` (phiên khác
   đang dùng); emulator riêng: `emulator -avd Pixel_8_Pro -read-only -port 5570`; chỉ kill process mình
   tạo, theo PID.
7. **Báo cáo cuối task** theo mẫu: (a) cổng nào đã đo, số trước/sau; (b) file đổi; (c) test đỏ → xanh
   (đường dẫn log); (d) điều chưa kiểm được và vì sao; (e) rủi ro cho app đang dùng SDK.
8. **Reviewer** (agent thứ hai): chạy lại test và script gate, đọc diff theo checklist mục 3, ghi
   PASS/FAIL từng cổng. Không PASS theo mô tả.
9. **Chấm điểm phase:** phase chỉ đóng khi bảng G tương ứng ở mục 3 được điền lại bằng số đo mới, có
   ngày và serial máy.

---

## 7. Quyết định chủ dự án cần chốt trước Phase B

**2026-09-25: chủ dự án chấp nhận đề xuất DOC/66 toàn bộ** (câu "tiếp tục thực hiện"). Từng mục #1–#16 dưới đây
ghi "Đã chốt" kèm commit thực hiện (mục nào chỉ là quyết định tài liệu, không có mã, thì ghi rõ là "chỉ doc").
Ngoại lệ: #4 (chờ chủ dự án tự xem Crashlytics trước khi có quyết định backport), #17 (P2, chốt 2026-09-28 theo O3 (a), xem mục) và #18 (G9, mục mới 2026-09-26, chưa chốt).

1. **Khoảng cách interstitial mặc định 240 s** (doc 62 F) sẽ giảm số interstitial của Speaker Clean và
   Invoice khi nâng SDK, trừ khi mỗi app đặt `ads_policy_android` riêng trước khi nâng. Chấp nhận mặc định
   này, hay giữ 30 s tới khi có A/B?
   **Đã chốt (2026-09-25, DOC/66 mục 4 #1 — chỉ doc, mã đã làm ở Task 10):** giữ hành vi hiện tại — mặc định
   của `AdsPolicy` là `fullDeltatime` cũ (30 s khi chưa đặt), không chặn phiên đầu, app open ở mọi phiên; bật
   theo doc 62 F bằng Remote Config
   `{"schema_version":1,"no_interstitial_first_session":true,"app_open_from_session":2,"interstitial_min_gap_seconds":240}`
   (Task 10, commit `58971d5`, đã có trước Task A–G, không đổi ở các task này). Mỗi app tự A/B 30/90/240 s
   bằng `ads_policy_android` ≥ 2 tuần (ARPDAU, guardrail D1/D7), rồi chốt mặc định chung ở 1.5.0; đổi mặc định
   khi đó là một dòng "Phải làm khi nâng cấp".
2. **`screensBeforeInterstitial`**: doc 62 F ghi 4, nhưng app không gọi `recordScreenChange()` sẽ bị chặn
   interstitial vĩnh viễn sau lượt đầu. Đề xuất mặc định 0 (tắt) và app nào báo màn thì bật qua Remote
   Config. Cần chốt.
   **Đã chốt (2026-09-25, DOC/66 mục 4 #2 — chỉ doc, mã đã làm ở Task 10):** giữ hành vi hiện tại — khoá vắng
   thì dùng giá trị app đặt bằng `setPostAdScreenCooldown` (mặc định 0 = tắt); bật theo doc 62 bằng
   `"screens_before_interstitial":4`. Khoá bật mà app chưa gọi `recordScreenChange()` lần nào trong process
   thì cooldown không bật và SDK log lỗi một lần, không chặn vĩnh viễn.
3. **Hoãn `onAdDismissed` của quảng cáo cha tới khi Full2 xong** (doc 64 D): đổi hợp đồng callback của mọi
   app. Đề xuất giữ như hiện nay (không hoãn) và ghi rõ trong doc 62 M.
   **Đã chốt (2026-09-25, DOC/66 mục 4 #3 — chỉ doc, không đổi mã):** giữ như hiện nay, **không hoãn**
   `onAdDismissed` của quảng cáo cha. `Full2TransitionListener.onTransitionChanged(false)` (hoặc lớp che mặc
   định tự hạ, app không đăng ký listener) là tín hiệu riêng báo "xong cả chuỗi chuyển cảnh"; app cần biết
   trạng thái chuỗi thì dùng tín hiệu này, không suy luận từ `onAdDismissed`. Ghi ở DOC/62 mục M.
4. **Speaker Clean production** đang có logic Full2 dùng lại dialog với Activity chết. Có backport bản sửa
   Task 4 sang fork không, hay chờ app chuyển sang SDK chung?
   **Chưa chốt (2026-09-25) — chờ chủ dự án.** DOC/66 mục 4 #4 và mục 6 (2): chủ dự án tự xem Crashlytics của
   Speaker Clean/Invoice cho `BadTokenException`/`WindowLeaked` trước; có thì mới backport bản vá tối thiểu
   Task 4 (~60–100 dòng, nửa ngày + release). Đây là ngoại lệ duy nhất ngoài #17 chưa có quyết định cuối cùng
   sau khi chủ dự án chấp nhận DOC/66.
5. **Đổi tên 2 event camelCase** làm gãy liên tục báo cáo Firebase của event chẩn đoán đó. Chấp nhận?
   **Áp tạm theo plan (Task 14b, 2026-09-25); chủ dự án có thể đảo lại trước khi tag:** `reloadSplash_acnull` →
   `reload_splash_ac_null`, `setAc_waitreloadsplash` → `set_ac_wait_reload_splash`, kèm event placement
   `ads_<tên lớp><adsType>` → `ads_pl_<eventTag>_<adsType>` (tên lớp bị R8 của app đổi nên tên cũ ở bản release
   vốn đã là `ads_a0`…). CHANGELOG "Phải làm khi nâng cấp": dashboard gộp tên cũ với tên mới. Chưa đổi (cần quyết
   định riêng): năm mốc tROAS `AdLTV_OneDay_Top*Percent` — Google Ads nhập làm conversion theo tên.
   **Đã chốt (DOC/66, 2026-09-25; Task E, commit `8ec056c`):** giữ đổi tên 2 event chẩn đoán và `ads_pl_*`; **không bao giờ** đổi 5 event
   `AdLTV_OneDay_Top*Percent` — đóng băng làm hợp đồng: `EventNameContractTest` khoá từng hằng đúng từng ký tự và cấm
   literal `"AdLTV_` ngoài `EventNames`, `FirHelperAdLtvEventTest` khoá mốc `ThresholdTopAdRev` → tên (0.094 → Top50 …
   0.468 → Top10). Nếu có ngày phải đổi: bắn cả tên cũ và mới ít nhất 30 ngày, chuyển conversion action trong Google
   Ads, rồi mới bỏ tên cũ.
6. **Full2 chain sau đóng không chủ ý (xoay khi ad cha hiện)**: giữ (hiện tại) hay bỏ (skip `showFull2` +
   `finishFull2Transition()` trong cửa sổ `isReleasingHost`). Hiện tại Full2 đã load xếp hàng
   (`pendingFull2`), hiện trên màn hình tái tạo khi resume, và `pendingFull2` giữ `cbShow` tới lần resume kế.
   **Đã chốt: bỏ (DOC/66 mục 4; Task F, commit `85ee82a`, 2026-09-25).** Trong cửa sổ `isReleasingHost`, `showFull2` log
   `showFull2 skipped: parent closed by host destroy`, gửi `host_destroyed`, gọi `finishFull2Transition()`, không
   xếp `pendingFull2`; `clearActivity` xoá `pendingFull2` sau cả hai lượt; app nhận đúng một `onAdDismissed` của
   cha và đúng một `onTransitionChanged(false)`. Bất biến: Full2 chỉ nối sau quảng cáo cha do người dùng đóng.
   Cha GMA interstitial (kịch bản j1) không thuộc ca này, giữ như cũ. `Full2HostDestroyTest` (a–d).
7. **`allowsProfiling`/`allowsPersonalization` với `UNKNOWN`**: giữ `true` (cũ) hay `false` khi
   `gdprApplies != false`? Task 9 đã siết `UNKNOWN` cho Analytics, FCM, AppsFlyer, nhưng hai hàm này
   (brain tích luỹ hồ sơ, cá nhân hoá, CRM, offerwall) còn cho `UNKNOWN`. Siết thì người ngoài GDPR mà
   `IABTCF_gdprApplies` vắng (app chưa chạy UMP, hoặc UMP chưa ghi) mất profiling/cá nhân hoá tới khi
   UMP trả lời; giữ thì người EEA chưa trả lời form vẫn bị tích luỹ hồ sơ.
   **Đã chốt (2026-09-25, DOC/66 mục 4 #7; Task C, commit `c630abe`):** `false` khi `UNKNOWN`, **trừ khi** đọc
   được rõ `IABTCF_gdprApplies = 0`; `DENIED` luôn `false`; `GRANTED` luôn `true`; `PARTIAL` như cũ (không
   tích luỹ hồ sơ, vẫn cá nhân hoá). API mới `ConsentState.allowsProfiling(gdprApplies: Boolean?)` /
   `allowsPersonalization(gdprApplies: Boolean?)`; ba nơi gọi trong SDK không có `Context`
   (`DecisionEngine.observe`, `PersonalizationScenario.resolve`, `CrmBridge.emit`) dùng bản không tham số
   (`gdprApplies = null`), tức `UNKNOWN` luôn bị chặn ở cả ba. Offerwall không đổi: `OfferwallManager` chặn
   thẳng theo `tier() == DENIED`, không dùng hai hàm này.
8. **Push gắn với consent quảng cáo** (Task 9, `FcmConsent`): hiện FCM (auto-init và token) chỉ chạy khi
   tầng consent quảng cáo là `PARTIAL`/`GRANTED` hoặc ngoài GDPR, nên người EEA bấm "Do not consent" không
   có push nào. Push không phải một purpose TCF; giữ gắn (bảo thủ, mất kênh push với nhóm từ chối) hay
   tách push khỏi tầng quảng cáo (cần căn cứ pháp lý riêng, ví dụ quyền thông báo của Android)? Và khi
   người dùng rút lại consent: chỉ tắt auto-init (hiện tại, token cũ vẫn nhận push) hay gọi thêm
   `FirebaseMessaging.deleteToken()`?
   **Đã chốt (2026-09-25, DOC/66 mục 4 #8+11):** **1.4.0** giữ gắn với tầng consent quảng cáo (không tách);
   thêm gọi `FirebaseMessaging.deleteToken()` khi tầng chuyển từ cho phép sang không cho phép trong process
   (Task C, commit `ca62dbc`), với thử lại ở lần mở app kế nếu lần xoá đó lỗi (cờ `mem_fcm_delete_pending`,
   fix wave, commit `41b8947`). Khoảng giữa lúc rút và lúc xoá thành công, token cũ vẫn có thể nhận push.
   **1.5.0**: tách push khỏi tầng quảng cáo, căn cứ = người dùng bật thông báo (`POST_NOTIFICATIONS`), có API
   cho app không chạy UMP (xem #11).
9. **Ngưỡng G2 với lần nạp prefs đầu** (Task 15b): lúc cold start (Galaxy A25, `13a395c`) có 11 vi phạm
   StrictMode, 9 có frame SDK, 7 do SDK (2 là `super.onCreate()` của `Application`). 7 vi phạm đó đều là lần
   nạp SharedPreferences đầu trên main (`PreferenceHelper.init`, `PreUtil.getInt` ×2 trong `initPlugin`,
   `TcfConsent.read` ×2, `ConsentManager.canStartAdSdks` ×2 qua UMP). Đạt "= 0" cần nạp trước prefs trên luồng
   nền có latch trong `Application.onCreate` (code, Task 15b). Hay G2 đếm vi phạm do SDK trừ lần nạp prefs
   đầu? Cần chốt ngưỡng, và chốt đếm theo "có frame SDK" hay "do SDK" (cổng in cả hai).
   **Áp tạm (Task 17, 2026-09-25):** G2 đếm vi phạm do SDK **trừ** lần nạp SharedPreferences đầu (stack có
   `SharedPreferencesImpl`/`getSharedPreferences`, in riêng `prefs first load=…`); phần còn lại phải = 0,
   `tools/lib/device_gate_r8.sh` cho cổng FAIL khi > 0. Cổng này bắt được `context.filesDir` trên main trong
   `initPlugin` (đã sửa, `05ca0bf`, làm lại `6529164`). Đo 1.4.0: API 37 6 lần nạp prefs, 0 khác (5, 0 sau fix
   wave 1); API 28 và 26 8, 0. Chủ dự án chốt
   sau; muốn "= 0" thật thì cần nạp trước prefs trên luồng nền (Task 15b).
   **Đã giải quyết bằng ấm hoá (Task B, commit `0bb53ea`), không cần chốt ngưỡng.** `PrefsWarmup` nạp 4 file
   (`XgameHCM-BaseAndroid`, `App_prefer_file`, `<pkg>_preferences`, `__GOOGLE_FUNDING_CHOICE_SDK_INTERNAL__` của UMP
   4.0.0) trên luồng nền `appplugin-prefs-warmup` từ `AppPluginBase.attachBaseContext`; `onCreate` chờ tối đa 250 ms
   trước lần đọc đầu, hết hạn thì main đọc như trước. Thứ tự đọc không đổi. Cổng (`c7c9610`) đòi mọi vi phạm do SDK
   = 0, vẫn in `prefs first load=…` để theo dõi; UMP đổi tên file ở bản sau thì cổng bắt lại.
10. **Tắt hẳn thu thập Firebase Analytics khi từ chối** (hằng `AnalyticsConsent.DISABLE_COLLECTION_WHEN_DENIED`
    = `true`, quyết định B#6 trong addendum của Task 9, làm theo plan khi chưa có quyết định): người EEA
    `DENIED`/`UNKNOWN` mất `ad_impression`, `sdk_health` và mọi sự kiện; `false` thì chỉ Consent Mode denied,
    Firebase gửi ping không định danh và Google mô hình hoá. Kèm theo: **trước câu trả lời đầu** trên máy
    cài mới ở EEA, thu thập vẫn bật với Consent Mode denied (ping không định danh như `first_open`). Giữ cả
    hai, hay đổi? (Từ đợt sửa cuối Phase B, SDK chỉ bật lại thu thập do chính nó tắt, không đè công tắc của
    app.)
    **Đã chốt (2026-09-25, DOC/66 mục 4 #10 — chỉ doc, không đổi mã):** giữ `true`
    (`AnalyticsConsent.DISABLE_COLLECTION_WHEN_DENIED`, không đổi thành `false`); ping không định danh của
    Firebase Analytics trước câu trả lời đầu ở EEA vẫn còn (Consent Mode denied cả bốn loại, không cookie,
    không advertising id). App muốn tắt hẳn tới khi có câu trả lời thì tự khai
    `firebase_analytics_collection_enabled = false` và tự bật lại khi tầng cho phép.
11. **FCM gắn với cổng UMP** (quyết định B#5 trong addendum của Task 9, làm theo plan): FCM tắt auto-init tới
    khi có tầng cho phép; app không chạy UMP (và không tự `ConsentState.setTier`) thì không có push trừ khi tự
    bật auto-init mỗi lần mở app. Lần mở đầu sau khi nâng từ bản ≤ 1.3.x có thể còn đồng bộ token một lần.
    Chấp nhận, hay cho push một căn cứ riêng (xem cả #8)?
    **Đã chốt (2026-09-25, DOC/66 mục 4 #8+11 — chỉ doc, không đổi mã thêm):** giữ nguyên theo plan ở 1.4.0
    (FCM gắn với cổng UMP/tầng consent quảng cáo); tách push sang căn cứ riêng (quyền thông báo Android) ở
    1.5.0 — xem #8 cho phần `deleteToken()` đã làm ở 1.4.0.
12. **AppsFlyer start cho người `DENIED`** với consent toàn `false` (`AppsFlyerConsent(isUserSubjectToGDPR =
    true, …false)`; Task 9, Galaxy A25: launch request đi ngay sau câu trả lời). AppsFlyer vẫn nhận launch
    (không định danh quảng cáo). Giữ, hay không start AppsFlyer cho `DENIED`?
    **Đã chốt (DOC/66, 2026-09-25; Task C, commit `d9de55a`):** không start AppsFlyer cho `DENIED` (kể cả `IABTCF_gdprApplies = 0`) — không
    `setConsentData`, `init`, `start`; log `appsflyer not started: consent tier=DENIED gdprApplies=…` (một dòng cho mỗi
    tầng bị từ chối trong process); doanh thu/IAP không tới AppsFlyer khi nó không chạy (log `appsflyer event dropped`
    một lần; review C Minor 1, 6). Tài liệu
    AppsFlyer không nói consent `false` ngăn thu thập, còn `stop` thì có; tiền lệ CNIL/Voodoo. `UNKNOWN` trong vùng GDPR
    (hoặc chưa biết GDPR) cũng không start, tới khi tầng thành cho phép (cổng chỉ xét `canRequestAds()` nên có thể mở
    khi tầng còn `UNKNOWN`). Đã start mà tầng không còn cho phép (`DENIED` khi rút qua privacy options, hoặc `UNKNOWN`
    trong vùng GDPR/chưa biết GDPR sau `ConsentManager.reset()` hay `IABTCF_*` không đọc được; cổng dừng trùng cổng
    start `ConsentRules.tierAllows`, review cuối I1) → `stop(true)` một lần, log kèm tầng; tầng cho
    phép lại trong cùng process → `stop(false)` rồi start (`AppsflyerHelper.followTier`, từ listener tầng của
    `AdSdkStartup.applyTier`). `PARTIAL` giữ `AppsFlyerConsent(true, true, false, true)`.
13. **`ANDROID_ID` làm `userId` trong telemetry server của SDK** (`LogEventServerHelper.kt:49`, có từ trước
    Phase B): gửi với mọi event từ `session_start`, trước consent và cả cho người EEA `DENIED`; hàng đợi của
    Task 14a làm việc gửi đáng tin hơn. Mâu thuẫn với G4 ("byte trước consent = UMP + Remote Config"). Giữ
    (căn cứ lợi ích chính đáng, ghi vào chính sách), bỏ, hay hash / thay bằng id cài đặt ngẫu nhiên cho tầng
    GDPR/`UNKNOWN`, hoặc chặn theo tầng?
    **Đã chốt (DOC/66 mục 4, 2026-09-25; Task D, commit `b91bc0d`; fix wave `cd324d6`, `fd794ae`, `ef16c73`):** bỏ ANDROID_ID. `user_id` = player id của brain: id app truyền qua
    `MonetizationSdk.configure { playerId }` nếu có, nếu không thì `BrainStorage.playerId()` (UUID mỗi lần cài);
    `PlayerIdSource.current()`, đọc lúc dựng body. `PurchaseRouter` (URL web shop) dùng cùng id. SDK không còn đọc
    `Settings.Secure` (test hợp đồng nguồn `LogEventServerHelperTest`). Sửa luôn lỗi có sẵn: backend `reconcile.py`
    lớp 1 và `metrics.py` giả định `user_id` = playerId (DOC/57 mục E.5). Hệ quả, ghi ở CHANGELOG "Phải làm khi nâng
    cấp": `user_id` gãy liên tục một lần; web shop muốn giữ đơn qua cài lại phải truyền id tài khoản qua `configure`.
    **G4 chưa đạt đúng nghĩa:** khi app bật DataBucket (`DATABUCKETS_BASE_URL`), `first_open`/`session_start` vẫn
    gửi trước consent, nay mang id bí danh thay cho định danh phần cứng. Byte trước consent không giảm. Giữ hàng đợi
    tới khi có tầng hay chấp nhận telemetry bí danh là mục #17 dưới đây (P2 của DOC/66), chờ chốt. **Đã xử lý (review Task D):**
    `user_id` = id brain phân nhóm trên cả ba đường review chỉ ra — resume sau ≥ 30 phút giữ id app
    (`AdsBrain.onSessionStart()` đọc `PlayerIdSource`), app gọi thẳng `AdsBrain.onSessionStart(id)` (id đó thành nguồn
    `user_id`), replay decision log mang `user_id` lúc ghi (trường 13 của dòng hàng đợi). Id truyền vào tới server
    telemetry: app dùng id nội bộ mờ, không email/số điện thoại. **Review cuối I2:** `session_start`/`first_open`
    (gửi trong `super.onCreate()`, trước `configure`) và exposure tự động của `initPlugin` mang id app từ lần mở kế
    sau khi id được lưu: SDK lưu id hợp lệ cuối cùng app truyền (`BrainStorage.appPlayerId()`, khoá `brain_app_player_id`), và
    `PlayerIdSource.current()` = id app của process → id app đã lưu → UUID. Giới hạn đã biết: mọi lần mở trước lần đầu
    app truyền id (lần mở đầu tiên sau khi nâng lên 1.4.0 hoặc sau khi cài, hoặc mọi lần mở trước lần đăng nhập đầu
    nếu app chỉ truyền id sau đăng nhập) các hàng đó vẫn có thể mang UUID; backend ghép theo `userProperty.session_id`,
    trừ hàng `brain_decision` replay mang `user_id` lúc ghi của process trước (CHANGELOG "Phải làm khi nâng cấp",
    DOC/57 mục E.5).
14. **Opt-out theo luật bang Mỹ (GPP / "do not sell or share")**: Consent Mode, FCM, AppsFlyer hiện chỉ đọc
    TCF (EEA); ngoài GDPR (`IABTCF_gdprApplies = 0`) là cấp hết trừ `DENIED`. Người dùng Mỹ chọn opt-out qua
    thông điệp US-state của UMP chưa được tôn trọng ở các consumer này. Đưa GPP (`IABGPP_*`) vào lộ trình
    (thị trường chính US/EU), và luật `gdprApplies == false && tier != DENIED` có đúng với người có thông
    điệp US-state `REQUIRED` không — cần kiểm.
    **Đã chốt (2026-09-25, DOC/66 mục 4 #14 — chỉ doc, không đổi mã ở 1.4.0):** 1.4.0 **chưa** chuyển opt-out
    bang Mỹ cho Meta/MAX/AppsFlyer/Firebase/CRM; chủ dự án tự bật thông điệp US-state trên AdMob console cho
    từng app (DOC/66 mục 6). **1.5.0**: đọc `IABGPP_*` và chuyển cho các consumer trên (`setDoNotSell`, Meta
    LDU, Consent Mode denied, AppsFlyer). Luật hiện tại `gdprApplies == false && tier != DENIED` **không tôn
    trọng opt-out US-state**: người Mỹ chọn opt-out qua thông điệp UMP vẫn được SDK coi là cấp hết trừ
    `DENIED`, vì SDK chưa đọc `IABGPP_*`; chưa kiểm lại bằng debug geography US.
    **Đã chốt (2026-09-28)** — 1.6.0 đọc `IABGPP_*` (Task 7) và chuyển opt-out bang Mỹ cho Firebase Consent Mode,
    AppsFlyer, MAX, Meta AN + facebook-core (Task 8a, `45638eb`); không có GPP thì không gọi API nào. Chủ dự án vẫn
    phải bật thông điệp US states trên AdMob console cho từng app (DOC/66 §6.2). Vector thật UMP 4.0.0 chốt decoder
    (Task 8b, `1735555`). App phải có mục Privacy options: người Mỹ chỉ opt-out qua đó (Task 8b).
15. **Gói rẻ `INTERRUPTIVE` giữ native collapsible** (Task 12): mask `INTERRUPTIVE` gỡ banner, rect và toàn màn
    hình; native collapsible (NtCl, dạng banner mở rộng) vẫn hiện. Giữ, hay tính NtCl là "gây gián đoạn"?
    **Đã chốt (DOC/66, 2026-09-25; Task E, commit `b5ab359`):** `INTERRUPTIVE` gỡ cả NtCl (`1 shl FLAG_NATIVE_COLLAPSIBLE`, giá trị 21 → 23):
    NtCl là lớp phủ đáy che ~45 % màn hình, gây gián đoạn hơn banner 50dp mà gói rẻ đã gỡ. Native inline và rewarded
    vẫn còn; `ALL`/`NONE` không đổi. Khoá bằng `RemoveAdsMaskTest`, `EntitlementsTest` và cổng thật
    `loadNtClForThisTurn` (`AdsDisabledLoadGateTest`). CHANGELOG "Phải làm khi nâng cấp": người mua gói rẻ mất thêm
    NtCl, mô tả gói trên store đổi theo, app build lại (hằng inline).
16. **Meta Audience Network ở EEA** (DOC/66 P1): Meta không có trong GVL, dựa vào Additional Consent
    (`IABTCF_AddtlConsent`); SDK không đọc chuỗi đó và khởi tạo Meta cả cho `DENIED` (a2 ngày 2026-09-25:
    `networks=admob,meta,max`).
    **Đã chốt (DOC/66, 2026-09-25; Task C, commit `a52784b`):** 1.4.0 không khởi tạo Meta AN (`AdsFbMy.startNetwork`) khi tầng là `DENIED`
    và `IABTCF_gdprApplies` khác `0` (vắng tính là trong GDPR); log lỗi `meta an: not started, consent tier=DENIED in
    GDPR scope`; `sdk_health` `networks=admob,max`. Hàng chờ của Meta đóng lại: load `fb_*` đang giữ và load đến sau
    báo `onLoadFailed` (`network_not_started`) ngay, một lần, waterfall đi tiếp. Tầng cho phép lại trong process →
    khởi tạo Meta lúc đó, một lần (`AdSdkStartup.applyTier`). (Từ FX bên dưới `DENIED` cũng không khởi tạo Meta khi
    `IABTCF_gdprApplies = 0`: `DENIED` thắng `0`.) **Lộ trình 1.5.0:** đọc `IABTCF_AddtlConsent` (Google Additional Consent, id Meta trong danh sách ATP) để
    quyết Meta theo đúng consent của nó thay vì theo tầng Google, cho cả `PARTIAL`/`GRANTED`. **Chưa phủ:** adapter Meta
    trong mediation của AdMob (`com.google.ads.mediation:facebook`) và MAX (`com.applovin.mediation:facebook-adapter`)
    được MobileAds/AppLovin khởi tạo khi các SDK đó khởi tạo; cần kiểm trên máy (Meta AN có khởi tạo qua adapter với
    người `DENIED` không) trước khi coi P1 là xong.
    **Bổ sung (review Task C, 2026-09-25):** khởi tạo muộn chỉ khi tầng cho phép (`ConsentRules.tierAllows`:
    `GRANTED`/`PARTIAL`, hoặc ngoài GDPR) — `DENIED` → `UNKNOWN` không khởi tạo Meta (Điều 13); quyết định bỏ Meta và
    đóng hàng chờ là một bước trong khoá. Cùng luật cho **Meta SDK (app events, `facebook-core`,
    `MetaSdkStarter`)**: `DENIED` trong GDPR thì không `fullyInitialize`, không `AppEventsLogger.activateApp`, log lỗi
    `meta sdk: not started, consent tier=DENIED in GDPR scope`; tầng cho phép sau đó trong process → khởi tạo một lần
    (`AdSdkStartup.applyTier` → `MetaSdkStarter.followTier`).
    **Sửa (review N1, 2026-09-25):** "không khởi tạo" chưa đủ: facebook-core 17.0.2 **lưu** `AutoInitEnabled`,
    `AutoLogAppEventsEnabled`, `AdvertiserIDCollectionEnabled` trên máy (`UserSettingsManager.writeSettingToCache` →
    prefs `com.facebook.sdk.USER_SETTINGS`, đọc bytecode), giá trị lưu thắng manifest, và `FacebookInitProvider` đọc nó
    trước `initPlugin` rồi tự `fullyInitialize` + theo dõi app events. Máy từng được bật (mọi máy nâng cấp từ ≤ 1.3.x)
    giữ `true`. Cơ chế 1.4.0: khi tầng từ chối (lúc `start`, hoặc Meta đang chạy mà tầng chuyển sang từ chối), SDK ghi
    `false` cho cả ba cờ trên luồng nền `appplugin-meta` (log `meta sdk: auto-init disabled (tier=…)`); tầng cho phép
    lại → ghi `true` như cũ. Tests: `MetaSdkStarterTest` (mock tĩnh `FacebookSdk`). **Chưa phủ:** lần mở app mà cờ
    `false` được ghi (lần mở **đầu** sau nâng cấp, hay lần đầu sau khi rút) vẫn có thể có một lượt auto-init và app
    event tự động của facebook-core trước khi cờ `false` được ghi; từ lần mở kế thì im. Chưa kiểm trên máy (cấp → rút →
    mở lạnh → tìm `fb_mobile_activate_app` qua proxy/Events Manager).
    **Bổ sung FX (Task F, commit `139171c`, 2026-09-25):** cả hai đường Meta (AN ở `AdSdkStartup.startAdNetworks`, app
    events ở `MetaSdkStarter.start`) dùng cùng cổng với AppsFlyer (#12), `ConsentRules.tierAllows(tier, gdprApplies)`:
    `UNKNOWN` trong GDPR hoặc `gdprApplies` vắng không khởi tạo (Điều 13; #7 đã chốt UNKNOWN không được profiling), và
    `DENIED` với `IABTCF_gdprApplies = 0` cũng không (`DENIED` thắng `0`). Log `… not started, consent tier=<tầng>
    in|outside GDPR scope`. `UNKNOWN` ngoài GDPR vẫn khởi tạo. Tests: `AdSdkStartupMetaConsentTest`, `MetaSdkStarterTest`.
17. **Telemetry trước consent khi app bật DataBucket** (P2, DOC/66 mục 5): khi app khai `DATABUCKETS_BASE_URL`,
    `first_open`/`session_start` vẫn gửi trước khi có tầng consent (demo không bật khoá này nên G4 trên demo
    không thấy hiện tượng). Từ 1.4.0 các event này mang id bí danh (playerId/UUID của brain, #13) thay cho
    ANDROID_ID, nhưng vẫn gửi trước consent — G4 ("byte trước consent = UMP + Remote Config") chưa đạt đúng
    nghĩa. Chọn: giữ hàng đợi tới khi có tầng consent (mất `first_open` của người thoát trước khi trả lời) hay
    chấp nhận telemetry bí danh trước consent là chính sách của SDK?
    **Chưa chốt (2026-09-25).**
    **Đã chốt (2026-09-28, O3 (a)):** giữ tới khi có tầng — 1.6.0 Task 6, `logevent/TelemetryConsentGate`. Event server dựng
    xong (JSON, `ts` lúc phát) được giữ trong bộ nhớ khi tầng chưa dùng được (`UNKNOWN` trong GDPR hoặc `IABTCF_gdprApplies`
    vắng), trần 50 (bỏ cũ nhất trừ `first_open`/`session_start` — hai event này không bao giờ bị đẩy ra);
    `GRANTED`/`PARTIAL`/ngoài GDPR → xả theo thứ tự; `DENIED` → bỏ event đang giữ. Người thoát
    trước khi trả lời mất event: đo bằng `session_start.pre_consent_lost` (`pre_consent_lost` là số event, không phải số
    người; chỉ người quay lại và đồng ý báo). Event phát **sau** `DENIED` vẫn gửi như trước — N1, chờ chủ dự án. Emulator:
    xem G4.
18. **G9 — nạp quảng cáo GMA bằng Activity hay `applicationContext`** (mục mới 2026-09-26). Bằng chứng: G9 ở trên
    (Activity cũ tăng dần 1 → 6 trên emulator; heap dump cho thấy chỉ GMA giữ, `.superpowers/sdd/g9-heap-analysis.md`).
    SDK đang truyền `mActivity` khi nạp: `AdsMobPlInters` (`InterstitialAd.load`), `AdsMobPlOpen`, `AdsMobPlrewarded`,
    `AdsMobPlNt`/`NtCl`/`NtFull` (`AdLoader.Builder`). Google và các adapter mediation khuyên truyền Activity lúc nạp; vài
    adapter (Teads, Equativ) nạp lỗi với app context. Speaker Clean và Invoice Maker dùng `com.google.ads.mediation:facebook`;
    adapter Meta tạo `InterstitialAd` bằng context lúc nạp và `showAd(context)` bỏ qua context lúc hiện
    (`FacebookRtbInterstitialAd.java:73, 87-89`), nên đổi sang app context làm quảng cáo Meta qua mediation mở từ
    Application. Chọn: (a) giữ Activity, ghi G9 là giới hạn do GMA; (b) cờ Remote Config nạp bằng `applicationContext`,
    mặc định tắt, bật thử trên một nhóm người dùng rồi so fill/eCPM (**đề xuất**); (c) bật `applicationContext` luôn.
    **Đo 2026-09-26 (emulator API 37, bản debug `a31271b`, cùng quy trình: mở → Interstitial → Full2 đóng cùng lúc xoay → Interstitial → Full2 xoay 2 lần khi hiện → xoay thêm 5 lần → `am dumpheap -g` ×2):** cờ tắt **4** Activity sau GC; cờ bật (bản thí nghiệm ép cờ = true trong worktree tạm, không commit, vì Remote Config của demo ghi đè giá trị `run-as`) **1** Activity ✓. Cờ bật: 6/6 lần nạp `ads gma load context=application`, 6 `tryLoad ok`, interstitial và Full2 hiện bình thường, FATAL/`WindowLeaked`/`BadToken` 0 (`DOC/testing/g9-flag-{off,on}-emu.log`). Chưa đo được: fill/eCPM thật và adapter mediation Meta (demo không có adapter mediation) — đó là phần bật thử theo nhóm.
    **Chưa chốt (2026-09-26).**
    Đã làm phần cơ chế (b), mặc định tắt (Task N, 2026-09-26: khoá `gma_load_app_context` trong `ads_policy_android`,
    CHANGELOG 1.4.0 "Thêm"); bật hay không: chủ dự án.
19. Callback mạng (AdsLoadInnerCb) phải tới trên main: vòng waterfall hạ `isLoading` trước khi gọi `cbLoad`, và
    `clearActivity` (main) nhả `cbLoad` rảnh — callback ngoài main có thể lọt vào giữa (1.6.0 Task 4 review M4;
    AdMob/MAX gọi trên main).

---

## Nguồn

- `~/hienphap/HIEN-PHAP.md` v1.4 (Điều 5–9, 13, 17, 20–22, Phụ lục A); `CHECKLIST-PHAT-HANH.md`; `DUC-RUT.md` §2.
- DOC/60 (chuẩn SDK dài hạn), 61 (ba app), 62 (chuẩn quảng cáo A–M), 64 (chạy thử 1.3.0).
- Google: [Disallowed interstitial implementations](https://support.google.com/admob/answer/6201362),
  [App open ads best practices](https://developers.google.com/admob/android/app-open),
  [Android vitals thresholds](https://developer.android.com/topic/performance/vitals),
  [Optimization for library authors (R8)](https://developer.android.com/topic/performance/app-optimization/library-optimization),
  [Shrink resources — keep.xml](https://developer.android.com/build/shrink-code#keep-resources),
  [Consent Mode v2 on Android](https://developers.google.com/tag-platform/security/guides/app-consent?platform=android),
  [IAB TCF v2.2 CMP API — IABTCF_* keys](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md).
- AppsFlyer: `AppsFlyerConsent` (đọc bằng `javap` trên af-android-sdk 6.16.2, 2026-09-24).
- Sentry SDK development guidelines; AppLovin MAX adapter/changelog model (qua doc 60 I).
