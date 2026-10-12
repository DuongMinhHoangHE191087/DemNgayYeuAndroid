# 61 — Một SDK nội bộ, ba app, bốn bản code: hiện trạng đo được và kế hoạch hợp nhất

**Ngày viết:** 2026-09-23
**Bối cảnh:** appplugin là **SDK nội bộ** cho portfolio app và game của chủ dự án. Nó không phải sản
phẩm thương mại và không cạnh tranh với AppLovin MAX; MAX chỉ là một dependency mediation (xem đầu doc 60).
**Phạm vi:** ba app đang dùng SDK — **LAM 2026**, **Speaker Clean**, **Invoice Maker** — mỗi app đang
dùng một cách khác nhau. Tài liệu đo cách từng app dùng, cái giá của sự khác nhau đó, và đề xuất một
cách phân phối duy nhất.
> **Cập nhật 2026-09-23 (chủ dự án chốt):** LAM 2026 là dự án cũ, không làm tiếp, nên ra khỏi lộ
> trình. Các số đo về LAM bên dưới giữ nguyên làm dữ kiện lịch sử. App còn dùng SDK: **Speaker Clean** và
> **Invoice Maker**, cả hai ở AGP 9.2.1. Repo SDK đã chuyển ra khỏi iCloud, sang `/Users/dungdm/appplugin`.

**Phương pháp:** chỉ đọc ba repo app (không sửa gì), so sánh từng file bằng hash, đọc file mapping R8
của bản release. Nhãn: **[M]** đo trực tiếp ngày 2026-09-23, **[P]** tài liệu chính thức, **[D]** đề xuất.

---

## 0. Tóm tắt

Hiện có **bốn bản code SDK** cùng sống:

1. repo `appplugin` trên máy (`main` = `568b0a4`, chưa push);
2. `origin/main` trên GitHub (`746326b`), đang được LAM dùng qua submodule;
3. bản chép trong Speaker Clean;
4. bản chép trong Invoice Maker.

Mỗi bản tự sửa bug và tự thêm tính năng. Hậu quả đo được:
- cùng một bug được sửa hai lần ở hai fork, nhưng bản gốc vẫn còn;
- **2/3 app đang chạy tổ hợp Meta adapter mà chính SDK đã ghi nhận là làm hỏng native ad**;
- repo SDK đã phân kỳ, lần push tới sẽ bị từ chối;
- tính năng tốt nhất về consent và chống gian lận đang nằm kẹt trong fork.

**Đề xuất:** một nguồn sự thật duy nhất là repo `appplugin`. SDK publish thành AAR có version lên Maven
private (GitHub Packages đã cấu hình sẵn). Composite build chỉ dùng khi dev cục bộ. Trước khi chuyển các
app, đưa tính năng của fork về SDK.

---

## A. Bản đồ hiện trạng [M]

| | LAM 2026 | Speaker Clean | Invoice Maker | repo `appplugin` |
|---|---|---|---|---|
| **Cách nhúng** | git submodule `libs/appplugin` + `includeBuild`, thay `com.app.plugin:appplugin:1.1.0` bằng source | chép source vào module `:adsplugin` | chép source vào module `:adsplugin` | — |
| **Nguồn** | GitHub `origin/main` `746326b` | fork từ ~2026-06-19 | fork từ ~2026-06-27 | local `main` `568b0a4` |
| **Commit riêng** | 5 commit chỉ có trên GitHub (full2 transition, bỏ init MAX khi thiếu SDK) | **78** commit trên `adsplugin` | **23** commit | 1 commit chưa push |
| **Kích thước** | 141 file | 105 file, ~15.455 dòng | 101 file, ~15.055 dòng | 139 file |
| **Lệch so với repo SDK** | 13 file khác, 2 file chỉ có ở đây | 42 file khác, 20 chỉ có ở fork, 54 chỉ có ở SDK | 38 file khác, 16 chỉ có ở fork, 54 chỉ có ở SDK | — |
| **AGP / Gradle** | 8.10.1 / 8.11.1 | **9.2.1 / 9.4.1** | **9.2.1 / 9.4.1** | 8.10.1 / 8.11.1 |
| **minSdk / targetSdk** | 26 / 36 | 24 / 36 | 24 / 36 | 26 / (thư viện) |
| **GMA + Meta adapter** | 24.9.0 + **6.21.0.1** | 25.4.0 + 6.21.0.4 | 24.9.0 + **6.21.0.1** | 25.4.0 + 6.21.0.4 |
| **Mạng quảng cáo** | AdMob + Meta; phải `exclude` MAX, 8 adapter MAX và Unity/Chartboost/Pangle/… trong `configurations.all` | AdMob + Meta | AdMob + Meta | 3 stack, 16 adapter |
| **Brain / IAP / paywall** | có; brain bật 5%, `childDirected = false` | không có | không có | có |
| **Consent API** | `ConsentManager` là `object` của SDK; app tự dịch `canRequestAds()` ra tier ở 3 chỗ (receiver, widget, analytics) | `ConsentManager.instance` + `ConsentGate` riêng của fork | `ConsentManager.instance` + `ConsentGate` + `AdRequestFactory` | `ConsentManager` là `object` |
| **Class SDK giữ tên trong APK release** | chưa đo được (máy không có bản release). Suy ra gần như toàn bộ, vì compile từ source kèm consumer rules keep cả package | 21/115 (18%) — do app tự keep `com.app.plugin.logevent.**` | 4/120 (3%) | — |
| **Application** | `LunaVietApplication : AppPluginBase()` + Hilt | `SpeakerCleanApplication : AppPluginBase()` | `InvoiceMakerApp : AppPluginBase(), Configuration.Provider` | — |

Ghi chú: consumer rules của hai fork **hẹp và đúng hơn** bản gốc — chỉ keep hai model Gson
(`EventRequest`, `UserProperty`). Đây là lý do Invoice Maker obfuscate được 97% code SDK.

---

## B. Cái giá của việc fork — bằng chứng cụ thể [M]

1. **Một bug, sửa hai lần, bản gốc chưa sửa.** Khi bật cờ test, SDK tạo `RequestConfiguration.Builder()`
   mới và ghi đè cấu hình cũ, làm mất cờ trẻ em, cờ tuổi đồng ý và rating nội dung
   (`AdsMobMy.kt:125-129`).
   - Speaker Clean sửa ngày 2026-09-18 (*"the AdMob request configuration was set twice from two
     threads, and one always lost"*).
   - Invoice Maker sửa ngày 2026-09-17 (*"apply the test-device list before the SDK starts, and stop
     it wiping the rest"*).
   - Repo SDK và LAM vẫn còn nguyên bug.
2. **Bản sửa không lan tới app đang cần.** Ghi chú trong `build.gradle.kts` của SDK: Meta 6.21.0.1 trên
   GMA 24.9 báo *"Adapter failed to render native ad. Method is not found"* với **mọi** native fill.
   - Speaker Clean đã nâng (6.21.0.4). SDK local cũng đã nâng nhưng chưa push.
   - **LAM và Invoice Maker vẫn ở 6.21.0.1** → nhiều khả năng mọi native Meta không render được. Cần
     xác nhận bằng log trên thiết bị.
3. **Chính repo SDK đã phân kỳ.** Local `main` có `568b0a4` (GMA 25.4, chưa push); `origin/main` có 5
   commit từ phía LAM mà local không có. Push sẽ bị từ chối; cần rebase/merge có kiểm soát.
4. **Cùng tên nhưng khác API.**
   - `ConsentManager` là `object` ở SDK nhưng là class có `.instance` ở fork.
   - App đang gọi các hàm `AdsHelper` mà SDK không có: `suppressNextOpenAd`, `grantBannerHideReward`,
     `recordScreenChange` (Speaker Clean + Invoice Maker); `isInterstitialLoaded`,
     `isInterstitialReadyToShow` (Speaker Clean); `setRemoveAdsMask` (Invoice Maker).
   - Muốn đưa app về bản chung thì phải hợp nhất API trước.
5. **Chỗ vá tạm ở phía app.**
   - LAM cần 11 dòng `exclude` để gỡ phần của game.
   - Theo comment trong `LAM2026/app/build.gradle.kts`, ngày 2026-09-19 LAM **không mở được**: SDK
     kéo Crashlytics vào classpath, trong khi app thiếu plugin Crashlytics.
   - LAM tự dịch consent tier ở 3 chỗ.
6. **Toolchain lệch** (hết từ 1.3.0: SDK lên AGP 9.2.1). SDK ở AGP 8; Speaker Clean và Invoice Maker ở AGP 9. Composite build dựng SDK
   bằng chính toolchain của build đó, nên cách làm của LAM không chép sang hai app kia được cho tới khi
   SDK build được trên AGP 9 **[D, cần thử]**. AAR đã publish thì không có ràng buộc này.
7. **Code chết/lệch do gỡ không đồng bộ.** SDK đã gỡ cơ chế lưới chặn chạm cho native Meta
   (`a7c1223`, "remove obsolete flick configuration"), nhưng Speaker Clean vẫn giữ
   `GridForExcludeTouch`. Cơ chế này chỉ thu hẹp vùng bấm, không vi phạm chính sách, nhưng cần chốt
   giữ hay bỏ **một lần** cho cả portfolio.

---

## C. Chọn cách phân phối cho SDK nội bộ

| Mô hình | Ưu | Nhược | Đang dùng |
|---|---|---|---|
| **Chép source** | sửa tại chỗ rất nhanh | lệch dần; bug sửa N lần; không có version; backport tay (mới 2 lần: `a029b63`, `568b0a4`) | Speaker Clean, Invoice Maker |
| **Submodule + composite build** | sửa SDK và app cùng lúc, một nguồn | app phải chung toolchain với SDK; version là commit hash; dễ quên cập nhật submodule; CI phải checkout đệ quy | LAM |
| **AAR có version trên Maven private** | tách toolchain; version rõ; rollback bằng đổi một con số; sau khi tách module thì app chỉ nhận thứ nó cần | cần CI publish; mỗi máy dev và CI cần token đọc package | chưa app nào, dù khối `publishing` tới GitHub Packages đã có sẵn |
| **Monorepo** (SDK + 3 app) | một PR đổi SDK và cả 3 app, test cùng lúc | phải gộp lịch sử 4 repo; 3 app lệch AGP; repo nặng | — |

Dữ kiện chính thức:
- **Gradle** [P]: composite build cho phép *"work across both at the same time without publishing the
  library first"*. Build được include **không** chia sẻ repositories, plugin management hay dependency
  versions với build gốc.
- **GitHub Packages** [P]: cần personal access token (classic) để *cài* package, kể cả package public.
  GitHub Actions dùng được `GITHUB_TOKEN` để publish package của chính repo đó.
- **Renovate** [P]: tự cấp credential cho `*.pkg.github.com`. Nhưng với Gradle, credential phải có ở cả
  pha extract; bản hosted hay lỗi với repo Maven private, nên cần self-hosted hoặc một workflow tự viết
  để bump version.

### Đề xuất [D]: một nguồn + AAR có version + composite chỉ khi dev cục bộ

1. **Repo `appplugin` là nơi duy nhất được sửa code SDK.** Luật: không commit code SDK vào repo app.
2. **CI publish khi gắn tag `vX.Y.Z`** lên GitHub Packages, dùng khối `publishing` hiện có.
3. **App khai version qua version catalog**, ví dụ `appplugin = "1.2.0"`. Nâng version là một PR một
   dòng, rollback cũng vậy.
4. **Cần sửa SDK và app cùng lúc thì bật composite bằng property cá nhân, không commit:**

   ```kotlin
   // settings.gradle.kts của app — đặt appplugin.localPath trong ~/.gradle/gradle.properties
   providers.gradleProperty("appplugin.localPath").orNull?.let { path ->
       includeBuild(path) {
           dependencySubstitution {
               substitute(module("com.app.plugin:appplugin")).using(project(":"))
           }
       }
   }
   ```

   LAM đã khai đúng toạ độ `com.app.plugin:appplugin:1.1.0`, nên chỉ cần thay `includeBuild` cố định
   bằng đoạn trên và gỡ submodule.
5. **Điều kiện tiên quyết:** SDK lên AGP 9, để build được cùng Speaker Clean và Invoice Maker khi dev
   cục bộ (mục B.6).
6. **Hotfix gấp cho một app:** publish bản `X.Y.Z-rc.N` từ nhánh của repo SDK, không sửa trong repo app.

---

## D. Hợp nhất tính năng: đưa gì từ fork về SDK [M → D]

| Tính năng | Đang ở | Đề xuất | Module đích (doc 60 B.3) |
|---|---|---|---|
| `ads/consent/ConsentGate` — hàng đợi callback chờ consent xong mới chạy | Speaker Clean, Invoice Maker | **Đưa về.** Chính là mảnh còn thiếu cho lỗi "init ads trước consent" trong audit | `core` |
| `ads/consent/AdRequestFactory` | Invoice Maker | Đưa về cùng `ConsentGate`: một nơi dựng `AdRequest` theo trạng thái consent | `ads` |
| `segment/PlayIntegrityGuard` — gắn nonce vào purchase token, fail-open, server quyết | Speaker Clean | **Đưa về.** Code đang gọi request kiểu classic (`IntegrityTokenRequest`) trong khi comment nói "Standard"; chốt một kiểu khi đưa về | `iap` |
| `firbase/SensitiveLogSanitizer` | Speaker Clean, Invoice Maker | Đưa về, mở rộng cho mọi log chứa token/ID | `core` |
| `segment/*`: `UserSegmentEngine`, `SegmentScorer`, `RevenueTierEvaluator`, `GeoTierResolver`, `EcpmBucketResolver`, `AdCadenceGate` | Speaker Clean, Invoice Maker | **Cần quyết định** (mục F.1): trùng một phần với `brain` (segment, guardrail, cadence) | `segment` riêng hoặc gộp vào `brain` |
| `RewardDeliveryGate`, `FullScreenAdPolicy`, `Full2TransitionListener` | Speaker Clean; hai file sau cũng có trên `origin/main` | Đưa về sau khi hoà nhánh (B.3) | `ads` |
| `attribution/InstallReferrerHelper` | Speaker Clean, Invoice Maker | Đưa về | `attribution` |
| API `AdsHelper` mà app đang gọi (B.4) | fork | Đưa về thành API công khai có test; `ConsentManager` chốt một dạng (`object`) và giữ alias `.instance` trong 1.x | `ads`, `core` |
| Consumer rules hẹp (chỉ keep 2 model Gson) | Speaker Clean, Invoice Maker | **Thay** keep-all của bản gốc bằng bản này (doc 60 F.3.2) | tất cả |
| `GridForExcludeTouch` | Speaker Clean | Chốt giữ hay bỏ (B.7) | `ads` |
| `brain`, `iap`, `paywall`, `crm`, `offerwall`, `consent` của SDK | chỉ SDK | Giữ; là module tuỳ chọn, app tiện ích không bắt buộc bật | như doc 60 B.3 |

---

## E. Lộ trình chuyển đổi [D]

> **Tiến độ 2026-09-23:**
> - Bước 0 xong: hai nhánh của repo SDK đã hoà (PR #2, rebase-merge), GMA 25.4 + Meta 6.21.0.4 +
>   chuẩn chuyển cảnh full2 cho AdMob, Meta, MAX đã lên `main`. Bản sửa test device (B.1) nằm trong 1.2.0.
> - Bước 1 phần lớn xong trong **1.2.0**: các mục "Đưa về" ở bảng D, API `AdsHelper` ở B.4 (chính
>   sách mới đều mặc định tắt), `ConsentManager` một dạng `object` + lớp chuyển tiếp `.instance`,
>   consumer rules hẹp, CHANGELOG, CI và publish theo tag (`.github/workflows/release.yml`).
>   Còn lại của bước 1: SDK lên AGP 9. Trước đây hoãn vì LAM còn build SDK từ source bằng AGP 8.10.1;
>   LAM đã dừng nên không còn gì chặn, và hai app còn lại đều ở AGP 9.2.1. `segment/*` và
>   `GridForExcludeTouch` chờ quyết định ở mục F.
> - AAR release trước 1.2.0 bị minify với danh sách keep viết tay và mất `crm/`, `offerwall/`,
>   `appsflyer/`; chưa app nào dùng AAR nên chưa ai gặp. Từ 1.2.0 thư viện không minify nữa.
> - `release.yml` đã chạy thật: tag `v1.2.0` publish lên GitHub Packages, GitHub Release kèm AAR.
> - Phép thử AAR trên một app thật (bản clone LAM `79e76eb`, trước khi LAM dừng): gỡ `includeBuild`,
>   dùng AAR 1.2.0, 739/739 test, build release R8 chạy qua, R8 đổi tên SDK mà vẫn giữ đúng phần
>   reflection (Retrofit, Gson). Repo LAM thật không bị sửa.
> - Invoice đã commit cục bộ (chưa push): nâng GMA 25.4.0 + Meta 6.21.0.4 (`bea052f`), và bỏ nối full2
>   sau app open theo quyết định của chủ dự án (`6a4f901`).
> - **1.3.0 (publish theo tag `v1.3.0`):** SDK lên AGP 9.2.1 (bước 1 xong); mạng quảng
>   cáo chỉ khởi tạo sau consent; bản sửa và API của Invoice ở doc 63 mục B, C. Chủ dự án chốt: chưa
>   áp vào app thật, làm tốt ở SDK trước, khi nào dùng sẽ báo. Nên bước 2 (Invoice) chờ lệnh đó.

**Bước 0 — làm ngay, không đổi kiến trúc**
- Hoà hai nhánh của repo SDK: rebase `568b0a4` lên `origin/main`, chạy lại 527 test, rồi push.
- LAM và Invoice Maker: xác nhận trên log thiết bị lỗi Meta native render; nếu đúng, nâng lên bộ đã
  kiểm thử (GMA 25.4.0 + Meta 6.21.0.4 + adapter đi kèm, như Speaker Clean).
- Lấy bản sửa bug test-device (B.1) từ fork về SDK.

**Bước 1 — SDK 1.2.0 "bản hợp nhất"**
- Đưa về các mục "Đưa về" ở bảng D, hợp nhất API (B.4).
- Nâng SDK lên AGP 9; thay consumer rules bằng bản hẹp.
- CHANGELOG; CI publish lên GitHub Packages.

**Bước 2 — chuyển từng app, ít lệch trước**

> Từ 2026-09-23 bước 2 chỉ còn Invoice Maker rồi Speaker Clean. Mục 1 (LAM) giữ lại làm hồ sơ, không làm.

1. ~~**LAM**~~ (dừng) — đã dùng đúng toạ độ Maven: gỡ submodule, thay `includeBuild` bằng property (C.4), khai `1.2.0`.
   Giữ các `exclude` cho tới khi có module ở bước 3.
   - **Điều kiện hiện tại:** LAM ở AGP 8.10.1 + Hilt 2.57.2 (kapt). Meta adapter 6.21.0.4 của SDK kéo
     `kotlin-stdlib` 2.3.0 làm `kaptDebugKotlin` vỡ, còn Hilt 2.60 lại cần AGP 9. Cho tới khi LAM lên
     AGP 9 + Hilt 2.60, LAM ghim Meta adapter về 6.21.0.3 (đã có bản sửa native) trong
     `app/build.gradle.kts`:

     ```kotlin
     dependencies {
         constraints {
             implementation("com.google.ads.mediation:facebook") {
                 version { strictly("6.21.0.3") }
                 because("Hilt 2.57.2 (kapt) cannot read kotlin-stdlib 2.3 metadata pulled by 6.21.0.4")
             }
         }
         // ...
     }
     ```

   - Đã kiểm ngày 2026-09-23 trên bản clone của LAM `d697540`, với SDK nhánh
     `feat/full2-standard-sync`: `:app:testDebugUnitTest` 735/735 pass, `:app:assembleDebug` ra được APK.
     Không có constraint trên thì `kaptDebugKotlin` vỡ.
2. **Invoice Maker** (23 commit fork) — diff `:adsplugin` với 1.2.0; mọi khác biệt còn lại hoặc đưa
   về SDK, hoặc biến thành config; xoá `:adsplugin`; khai dependency.
3. **Speaker Clean** (78 commit fork, lệch nhiều nhất) — làm tương tự.

Mỗi app xong một bước khi: build release có R8 chạy qua; test thiết bị; fill rate và eCPM 7 ngày
không giảm so với trước khi chuyển.

**Bước 3 — SDK 2.0, tách module (doc 60 B.3) — đã làm ở dạng phụ thuộc, xem mục G**
- App tiện ích dùng `core + ads + ads-gma + ads-meta`, và `brain`/`iap` nếu cần. LAM bỏ hết `exclude`.
- Game dùng thêm `ads-max` và adapter game.

**Xong khi:**
- không repo app nào còn code SDK;
- mỗi app khai SDK bằng đúng một dòng version;
- ba app build từ AAR đã publish;
- mỗi release SDK có CHANGELOG.

---

## F. Quyết định cần chủ dự án chốt

1. **`segment/*` và `brain`:** gộp bộ phân khúc rule-based vào `brain`, hay giữ module `segment` nhẹ cho
   app tiện ích không bật brain? Brain có thể dùng output của `segment` làm feature.
2. **Nơi host package:** GitHub Packages (đã cấu hình, mỗi máy cần PAT `read:packages`) hay Google
   Artifact Registry.
3. **`GridForExcludeTouch`:** giữ hay bỏ cho cả portfolio.
4. **`AppPluginBase`:** cả ba app đều kế thừa được (kể cả với Hilt và WorkManager `Configuration.Provider`),
   nên việc đổi sang `init(application)` không gấp — dời sang 2.0.

---

## G. Cập nhật 2.0.0 (2026-10-02), bổ sung 2.1.0: ba artifact, resource, khoá Remote Config

**Hướng dẫn tích hợp đầy đủ (2.2.0): `docs/integration/INTEGRATION.md`; bảng khoá và resource dưới đây vẫn là nguồn của bảng đó.** **Nguồn:** `CHANGELOG.md` mục `[Unreleased]` (`c2-app`, `c3a-app`, `c3b-app`, bảng gộp 1.3.0 → 2.0.0), `README.md` mục 3, báo cáo C2a/C2b/C3a/C3b. Bước 3 của mục E ("SDK 2.0, tách module") đã làm ở dạng **phụ thuộc**: mã vẫn ở module gốc, hai artifact kia 0 mã (chốt D16). **2.1.0 (G1, D1/D7/D8) dời mã game sang `appplugin-game`** — commit phá vỡ ABI duy nhất của bản đó (xem G.1, G.3).

### G.1 Ba artifact (cùng một số phiên bản)

| Artifact | Dùng cho | Nội dung |
|---|---|---|
| `com.app.plugin:appplugin` | mọi app | mã SDK + GMA, Meta, UMP, Firebase, billing, integrity, AppsFlyer; POM 26 phụ thuộc (bản 1.9.0 có 39, bớt đúng 13 nhóm game) |
| `com.app.plugin:appplugin-max` | game dùng MAX | `api(appplugin)` + AppLovin SDK 13.6.2 + 8 adapter `com.applovin.mediation:*` + `com.bigossp:admob-mediation:5.7.0.0` + GMA `chartboost:9.12.1.1`, `fyber:8.4.5.1` + `unity-ads:4.19.0` |
| `com.app.plugin:appplugin-game` | game, hoặc app tiện ích dùng RC game/CRM | `api(appplugin)` + keep rule `GameModule` + **mã game từ 2.1.0**: 5 lớp (`BattlePass`, `RecurringGrants`, `PurchaseRouter`, `OfferwallManager`, `CrmBridge`; 1 kiểu top-level mỗi file) và `GameModule`, FQN không đổi (`com.app.plugin.iap.*`, `.offerwall.*`, `.crm.*`, `.game.*`); `api/appplugin-game.api` 389 dòng (= 32 khối lớp rời gốc); gốc `appplugin` không còn các lớp này (dump gốc 5605 → 5224 ở G1). Mã game gọi lõi chỉ qua API public và 3 điểm SPI `@AppPluginInternalApi` (`RcSectionRegistry.register`, `BrainSignalListener`, `GameHost`); các test game dùng friend-path chỉ trên `testDebugUnitTest` |

App tiện ích chỉ khai `appplugin` (từ 2.1.0 không còn chạy phần game, xem G.3; thêm `appplugin-game` nếu app dùng 5 khoá RC game hoặc tín hiệu CRM); app game khai cả ba. MAX là `compileOnly` trong `appplugin` (dò `isMaxSdkAvailable` + `MaxBridge`). App từng nhận Bigo/Chartboost/Fyber/Unity/AppLovin bắc cầu từ POM cũ phải tự khai hoặc thêm `appplugin-max`. Partner bidding `unity` trên app tiện ích: adapter GMA `unity:4.19.0.0` + `unity-ads:4.19.0`.

### G.2 Resource của SDK (96 tên đổi) và khoá app-contract (không đổi)

SDK khai `resourcePrefix = "appplugin_"`: mọi resource nó tự khai là `appplugin_<tên cũ>` (trong 96 tên đổi có 4 font bị xoá cùng commit, còn 92; 25 màu, 18 dimen, 22 drawable, 15 layout, 8 font, 5 string, 2 style, 1 xml; `AppTheme` → `appplugin_AppTheme`). Bốn font không dùng bị xoá. Lint `ResourceName` ở mức `error`.

| Loại | Tên mới (ví dụ) | Ai bị ảnh hưởng |
|---|---|---|
| layout | `appplugin_admob_native_cl`, `appplugin_fb_native_full`, `appplugin_max_native_cl_dialog`… (15, kể cả `layout-land`) | app ghi đè layout native của SDK; view id bên trong không đổi |
| style | `appplugin_AppTheme`, `appplugin_AppTheme.AdAttribution` | app tham chiếu `@style/AppTheme` của SDK |
| xml | `@xml/appplugin_gma_ad_services_config` | app khai `android.adservices.AD_SERVICES_CONFIG` trỏ vào file này (Speaker Clean) |
| string | `appplugin_default_notification_channel_id`, `appplugin_ad_attribution`… | app từng khai `default_notification_channel_id` để đổi kênh thông báo của SDK |
| color / dimen / drawable / font | `appplugin_` + tên cũ | app khai trùng tên để chỉnh màu/kích thước quảng cáo |
| **giữ nguyên (app-contract)** | `admob_*`, `adfb_*`, `admax_*`, `ads_cf_*`, `ads_admob_firebase_linked`, `flagTest`, `admob_test_device`, `fb_test_device`, `max_test_device`, `facebook_app_id`, `af_app_id`, `admax_devKey`, `@array/ads_*`; `raw/appplugin_keep`; 3 chuỗi `appplugin_rwi_*` | app khai, SDK đọc bằng `getIdentifier` |

### G.3 Khoá Remote Config (không đổi tên; phần game chỉ chạy khi có `appplugin-game` từ 2.1.0)

| Khoá | Dạng và version | Đọc ở | Nhóm |
|---|---|---|---|
| `ads_policy_android` | `schema_version` 1; 7 trường v1 + khối tuỳ chọn `by_tier` (`T1`/`T2`/`VN`/`OTHER`, 1.9.0); schema khác hoặc JSON hỏng → mặc định | `ads/AdsPolicy.kt` | lõi |
| `ads_bidding_partners_android` | `{"schema_version":1,"partners":[…]}` (1.9.0); chỉ thu hẹp tập partner app đã khai | `ads/core/BiddingPartners.kt` | lõi |
| `brain_policy_android` | artifact có `version` (chuỗi bắt buộc), `enabled` (mặc định true; `false` = kill), `experiment.rollout_fraction` (1.9.0); rỗng thì giữ policy đang chạy | `brain/BrainPolicy.kt`, `firbase/FirHelperConfig.kt` | lõi |
| `sdk_kill_android` | `{"ads":bool,"iap":bool,"logevent":bool,"below_version":"x.y.z"}`, không có `schema_version`; trường sai kiểu chỉ bỏ trường đó | `SdkKillSwitch.kt` | lõi |
| `logevent_control` | `{"version":1,"enabled":true,"default_sample":1.0,"sample":{…}}`; `version` ≠ 1 → mặc định (1.7.0) | `logevent/LogEventControl.kt` | lõi |
| `paywall_config_android` | `schema_version` vắng = v1, `2` = v2 (1.8.0; holdout mặc định 0, `session_cap`); schema khác → từ chối cả cấu hình | `paywall/PaywallConfigV2.kt` | lõi |
| `subscription_lifecycle_android` | `version` bắt buộc (vd `sub_lifecycle_v1`), `enabled`, `in_app_messages.enabled` (1.8.0) | `iap/SubscriptionLifecycle.kt` | lõi |
| `purchase_routing_android` | `version` bắt buộc (vd `routing_v1`) | `iap/PurchaseRouter.kt` (DOC/50) | game |
| `battle_pass_android` | `version` bắt buộc (vd `bp_v1`) | `iap/BattlePass.kt` (DOC/52) | game |
| `recurring_grants_android` | `version` bắt buộc (vd `rg_v1`) | `iap/RecurringGrants.kt` (DOC/53) | game |
| `offerwall_config_android` | `version` bắt buộc (vd `ow_v1`) | `offerwall/OfferwallManager.kt` (DOC/54) | game |
| `crm_bridge_android` | `version` bắt buộc (vd `crm_v1`) | `crm/CrmBridge.kt` (DOC/56) | game |
| `seg_cadence_guardrail_seconds` | số nguyên giây, không JSON/version trong giá trị; mặc định **0** = tắt (2.2.0 SEG, D10-220; fork IM 180, SC 60); ≤ 0 = tắt | `segment/UserSegmentEngine.kt`, `segment/SegmentHooks.kt` | lõi |
| `seg_*` khác (`seg_weight_*`, `seg_threshold_*`, `seg_cadence_*_tab_switch`, `seg_cold_start_window_hours`, `seg_churn_*`, `seg_revenue_*`, `seg_bounce_window_millis`, `seg_ecpm_min_impressions`, `seg_ecpm_window_size`, `seg_ecpm_high_threshold_micros`, `seg_geo_tier{1,2}_countries`) | số (Long) hoặc CSV mã nước; tên và mặc định y hệt fork IM (2.2.0 SEG); vắng → mặc định; đọc trên luồng nền lúc khởi động — trước khi nạp xong `UserSegmentEngine.segment` = `COLD_START`, `geoTier` = 4 (app thu StateFlow hoặc đọc sau khung hình đầu) | `segment/UserSegmentEngine.kt`, `segment/EcpmBucketResolver.kt` | lõi |
| `ads_region_android`, `ads_region2_android`, `cf_ids_placement`, `cf_ads_placement` | bản đồ JSON placement/id, không có trường version trong mã | `firbase/FirHelperConfig.kt` | lõi |

Năm khoá nhóm "game" (`purchase_routing_android`, `battle_pass_android`, `recurring_grants_android`, `offerwall_config_android`, `crm_bridge_android`) và tín hiệu brain → CRM **chỉ chạy khi app có artifact `appplugin-game`** (2.1.0, G1, D7; ở 2.0.0 mã game còn trong `appplugin` nên chúng chạy dù có `-game` hay không — câu cũ đó hết hiệu lực từ 2.1.0). Cơ chế: `GameModule` (trong `-game`) đăng ký 5 section qua `RcSectionRegistry` và nghe tín hiệu brain qua `BrainSignalListener`; lõi dò `com.app.plugin.game.GameModule` bằng tên lớp đúng một lần mỗi process. Có mặt: khi bật log SDK in `game: GameModule installed — <N> section` (N = số section `install()` thêm); vắng: `game: không có com.app.plugin.game.GameModule — 0 section game` và 0 dòng `… (RemoteConfig):` của nhóm game. Khoá RC game vẫn có thể có mặt trong Remote Config của app tiện ích: lõi bỏ qua (không lỗi, không crash). Thứ tự áp và năm dòng `… (RemoteConfig):` khi có `-game` giữ nguyên (dòng `SubscriptionLifecycle (RemoteConfig):` đứng trước `PurchaseRouter (RemoteConfig):`); năm dòng chỉ in khi giá trị RC của khoá đó không rỗng. Keep rule `GameModule` chỉ còn ở `appplugin-game/consumer-rules.pro` (gốc không giữ lớp game nào; R8 trên app tiện ích bỏ sạch 6 lớp game). SPI là nội bộ giữa các artifact appplugin: opt-in chỉ được ép với Kotlin, mã Java gọi được mà không cần opt-in, không hỗ trợ; `appplugin-game` luôn phải cùng bản với `appplugin`. Bảng khoá ở trên không đổi (cột "Nhóm" = "game" là nhóm chỉ áp khi có `-game`). SDK không đăng ký defaults XML; khoá rỗng hoặc vắng dùng mặc định trong mã.

### G.4 Fork và việc của app đầu tiên (không làm ở 2.0.0)

Speaker Clean và Invoice Maker vẫn chạy bản chép (`:adsplugin`); chuyển sang AAR là việc của cổng "App đầu tiên" (DOC/67), không thuộc 2.0.0. Lớp chỉ có trong fork (`segment.*`, `PlacementSwitches`, `isPlacementEnabled`) và `@xml/gma_ad_services_config` của Speaker Clean nằm trong danh sách chuyển của cổng đó. Từ 2.1.0 cổng đó cũng cần **token giấy phép** cho mỗi app (`applicationId` + SHA-256 cert ký phát hành; `tools/license_issue.py`, README mục 9): không token thì không quảng cáo, không brain. Speaker Clean và Invoice Maker là app tiện ích, không dùng RC game/CRM: 0 việc về `appplugin-game`.

## Nguồn

- [Gradle — Composite builds](https://docs.gradle.org/current/userguide/composite_builds.html)
- [GitHub Docs — Working with the Gradle registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)
- [Renovate — Private packages](https://docs.renovatebot.com/getting-started/private-packages/) · [Renovate discussion: Gradle lockfiles from private repositories](https://github.com/renovatebot/renovate/discussions/41708)
- [Android Developers — Optimization for library authors](https://developer.android.com/topic/performance/app-optimization/library-optimization)
- Dữ liệu [M]: ba repo `~/LAM2026`, `~/AndroidStudioProjects/speakerclean`,
  `~/AndroidStudioProjects/InvoiceMakerAI` và repo `appplugin`, đọc ngày 2026-09-23; file mapping R8
  bản release của Speaker Clean và Invoice Maker cùng ngày.
