# appplugin

SDK Android nội bộ `com.app.plugin:appplugin`: quảng cáo AdMob (banner, rect, interstitial, rewarded, native, app open),
billing/IAP, Remote Config qua Firebase, AppsFlyer tuỳ chọn và brain quyết định monetization. Không phải sản phẩm thương mại.
Bản hiện hành: xem bản mới nhất trong `CHANGELOG.md`. Gói publish lên GitHub Packages
(`https://maven.pkg.github.com/dungduong2899/appplugin`, khai ở `build.gradle.kts`).

## 1. Yêu cầu

- JDK của Android Studio: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Android SDK: `local.properties` ở gốc repo (file không commit, checkout mới chưa có). Tạo bằng một lệnh, sửa đường dẫn nếu SDK ở chỗ khác:
  `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties`.
- Thiết bị hoặc emulator để cài APK; `adb` nằm ở `$HOME/Library/Android/sdk/platform-tools/adb`. `adb devices` liệt kê `<serial>` dùng ở mục 2.

## 2. Dựng và chạy demo `utility`

App demo là module `app/` (applicationId `test.sdk.zd`); flavor `utility` là dạng app tiện ích, không kéo adapter game.
Chạy từ gốc repo (thư mục chứa `gradlew`); build đầu mất khoảng 2 phút:

```
./gradlew :app:assembleUtilityRelease
adb -s <serial> install -r app/build/outputs/apk/utility/release/app-utility-release.apk
```

Demo mang token giấy phép riêng của nó (`DEMO_LICENSE_TOKEN` trong `app/build.gradle.kts`, kid `dev1`, ràng buộc cert debug cục bộ; dựng ở máy khác ra `license: INVALID (cert)` nên không có quảng cáo: đổi bằng `-PlicenseToken=<token>`, xem mục 9). Build demo không cần khoá GitHub Packages (module `app/` dùng SDK cùng repo qua `project(":")`). Bản release của demo ký bằng khoá debug
(`app/build.gradle.kts`), nên không cần keystore. R8 in cảnh báo `AFj1kSDK` Companion của AppsFlyer: vô hại, build vẫn thành công.
Bản release bật R8 và shrinkResources như app thật (xem `app/build.gradle.kts`). Chạy test đơn vị: `./gradlew :testDebugUnitTest`.

## 3. Tích hợp vào app mới

Hướng dẫn đầy đủ, một nguồn và có kiểm tĩnh (`bash tools/integration_doc_check.sh`): **`docs/integration/INTEGRATION.md`** (tiếng Việt, kèm bản tiếng Anh ngắn); tài liệu này mô tả **SDK 2.2.0** (tag `v2.2.0`; `MonetizationSdk.VERSION` cho biết bản đang chạy), các mục "từ 2.2.0" không đúng với bản cũ hơn. Tóm tắt:

SDK publish **ba artifact cùng một số phiên bản** (`<phiên bản>` = bản mới nhất trong `CHANGELOG.md`; GitHub Packages cần `gpr.user`/`gpr.key`, PAT `read:packages`, hoặc `GITHUB_ACTOR`/`GITHUB_TOKEN`):

| Artifact | Cho ai | Mang gì |
|---|---|---|
| `com.app.plugin:appplugin:<phiên bản>` | mọi app | toàn bộ mã SDK: AdMob (GMA) + Meta, consent, đo lường, billing/paywall, brain, Remote Config, AppsFlyer tuỳ chọn |
| `com.app.plugin:appplugin-max:<phiên bản>` | app game dùng AppLovin MAX | chỉ phụ thuộc, 0 mã: AppLovin SDK 13.6.2 + 8 adapter + Bigo, GMA `chartboost`/`fyber`, `unity-ads`; cần thêm repo `https://artifacts.applovin.com/android` |
| `com.app.plugin:appplugin-game:<phiên bản>` | app game, hoặc app tiện ích dùng RC game/CRM | mã game (`PurchaseRouter`, `BattlePass`, `RecurringGrants`, `OfferwallManager`, `CrmBridge`, `GameModule`) + `api(appplugin)` cùng bản |

Các bước (chi tiết, lệnh và lỗi thường gặp ở `docs/integration/INTEGRATION.md`):

1. Khai repository (`google()`, `mavenCentral()`, GitHub Packages) và dependency theo artifact (mục 2, 3 của hướng dẫn).
2. `Application` kế thừa `AppPluginBase`, gọi `MonetizationSdk.configure(this) { ... }` rồi log `report.describe()` (mục 4).
3. **Token giấy phép** (bắt buộc từ 2.1.0): `tools/license_issue.py`, meta-data `com.app.plugin.LICENSE`, cert debug/upload/Play App Signing, một token cho mỗi `applicationId` (mục 5; lệnh ở mục 9 dưới đây).
4. Resource và manifest (mục 6 của hướng dẫn, mục 4 dưới đây); **xung đột backup-rules của AppsFlyer** cần `tools:replace="android:dataExtractionRules,android:fullBackupContent"`.
5. Firebase (`google-services.json`, bắt buộc), AdMob, AppsFlyer (mục 7), consent (mục 8), thứ tự gọi API (mục 9), khoá Remote Config (mục 10 và mục 5 dưới đây).
6. Kiểm trước khi phát hành: R8, `bash tools/release_gate.sh --variant utility`, `bash tools/device_gate.sh --app <applicationId>/<Activity>` (mục 12).

Nâng từ bản cũ: bảng "Phải làm khi nâng cấp" 1.3.0 → 2.2.0 ở mục 13 của hướng dẫn (nguồn: `CHANGELOG.md`).

## 4. Resource app phải khai

| Resource | Nội dung | Mẫu |
|---|---|---|
| Id quảng cáo, dạng `placement,id;id2` | các chuỗi `admob_banner`, `admob_full`, `admob_native`, `admob_openad`... | `app/src/main/res/values/ads_ids.xml` |
| `af_app_id` | khoá AppsFlyer; không khai thì AppsFlyer không init | `app/src/main/res/values/af_demo.xml` |
| AdMob app id | `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID">` | `app/src/main/AndroidManifest.xml` |

Demo dùng id thử của Google; app thật thay bằng id thật, bỏ `admob_test_device` và đặt `flagTest` = `0` (cả `1` lẫn `90351` đều bật chế độ test: thiết bị test AdMob, log chi tiết, form consent EEA).

Khoá app-contract giữ nguyên tên, **không** có tiền tố `appplugin_` (SDK đọc chúng bằng tên lúc chạy): `admob_*`, `adfb_*`, `admax_*`, `ads_cf_*`, `ads_admob_firebase_linked`, `flagTest`, `admob_test_device`, `fb_test_device`, `max_test_device`, `facebook_app_id`, `af_app_id`, `admax_devKey`, `@array/ads_*`. Mọi resource do SDK tự khai từ 2.0.0 mang tiền tố `appplugin_` (96 tên, xem `DOC/61_INTERNAL_SDK_THREE_APPS.md` mục G): app đang ghi đè layout, style, `gma_ad_services_config` hoặc `default_notification_channel_id` của SDK đổi sang tên mới.

## 5. Khoá Remote Config

Mỗi khoá là một chuỗi JSON đọc từ Firebase Remote Config; khoá RC không đổi tên ở 2.0.0. Bảng đầy đủ có schema và version ở `DOC/61_INTERNAL_SDK_THREE_APPS.md` mục G; dưới đây là nơi khai và các khoá thêm từ 1.9.0:

| Khoá | Nơi khai |
|---|---|
| `ads_policy_android` | `src/main/java/com/app/plugin/ads/AdsPolicy.kt` (`REMOTE_KEY`) |
| `sdk_kill_android` | `src/main/java/com/app/plugin/SdkKillSwitch.kt` (`REMOTE_KEY`, công tắc tắt SDK từ xa) |
| `brain_policy_android` và các khoá `*_android` khác | `src/main/java/com/app/plugin/firbase/FirHelperConfig.kt` (khối `remoteConfig.getString(...)`) |
| `ads_bidding_partners_android` (1.9.0) | `src/main/java/com/app/plugin/ads/` (registry bidding partner). `{"schema_version":1,"partners":[…]}`: chỉ **thu hẹp** tập partner mà app đã khai ở string-array `ads_bidding_partners` (không thêm được partner, không loại được adapter đang có khỏi phiên đấu giá); sai dạng thì bỏ cả giá trị, giữ resource, một logE |
| `ads_policy_android` khối `by_tier` (1.9.0) | `src/main/java/com/app/plugin/ads/AdsPolicy.kt`. Khối tuỳ chọn, cùng `schema_version` 1: `{"T1":{…},"T2":{…},"VN":{…},"OTHER":{…}}`, mỗi tier chỉ 7 trường v1, trường vắng giữ giá trị top-level; khối sai (tier lạ, trường lạ, lồng `by_tier`/`schema_version`) bị bỏ **cả khối**, top-level giữ, một logE |
| `brain_policy_android` kill / rollout (1.9.0) | `src/main/java/com/app/plugin/firbase/FirHelperConfig.kt`. `enabled:false` dừng mọi quyết định của brain; artifact được chấp nhận được lưu qua cold start tới khi khoá đổi; `rollout_fraction` của artifact thắng `brainRolloutFraction` app đặt trong `configure` |

Tên placement rewarded interstitial (1.9.0): tiền tố `rwi_` (`loadRewardedForThisTurn("rwi_<x>")` / `showRewarded("rwi_<x>", …)`), id từ resource `admob_rwi`; được định tuyến trước tiền tố `gift_`, không thêm API public.

SDK không đăng ký defaults XML; khoá rỗng hoặc vắng thì dùng mặc định trong code: `SdkKillSwitch.NONE` (không tắt gì), `AdsPolicy.DEFAULT`, và `brain_policy_android` rỗng thì giữ nguyên policy brain đang chạy (`src/main/java/com/app/plugin/firbase/FirHelperConfig.kt`).

## 6. Cổng chất lượng

Chạy từ gốc repo, mỗi lệnh Gradle một lượt, không song song (ba artifact `appplugin`, `appplugin-max`, `appplugin-game` dựng cùng lúc; từ 2.1.0 `api_dump.sh`, façade và `public_types.sh` đọc hai module có mã, `root` và `appplugin-game` — `--module` chọn một; `appplugin-max` 0 mã nên không có dump):

- `bash tools/release_gate.sh --variant utility`: dựng release như app thật rồi soi APK và mapping (G3).
- `bash tools/device_gate.sh --variant utility`: đo trên thiết bị (ANR, StrictMode, khởi động); cần `ANDROID_SERIAL`.
- `bash tools/api_dump.sh --check`: API public/protected không đổi so với `api/appplugin.api` và `api/appplugin-game.api`.
- `bash tools/api_facade_check.sh`: mặt public/protected của mọi lớp có từ `v1.5.0` không đổi so với `BASELINE_ROOT`/`BASELINE_GAME` (từ 2.1.0 là chính commit G1, `e2ed528`), cộng luật core-internal.
- `bash tools/public_types.sh --check --min-pct 70`: tỉ lệ kiểu `internal` (H9) ≥ 70 % cộng mọi module và không có kiểu public mới ngoài `tools/public-types.txt`.
- `bash tools/perf_gate.sh` + `python3 tools/perf_report.py` (2.1.0): đo hiệu năng trên một máy (≥ 5 lượt khởi động lạnh, request → fill, show latency, PSS); bảng và ngưỡng ở `DOC/65_SDK_RELEASE_GRADE_CRITERIA.md`, mục "Hiệu năng".
- `bash tools/pom_check.sh build/repo`: POM của ba artifact sạch (sau `./gradlew publishAllPublicationsToLocalCheckRepository`).

`--variant` do 1.6.4 Task 5 thêm; trước đó các script chỉ chạy bản `release` mặc định.

## 7. Tài liệu

- `DOC/61_INTERNAL_SDK_THREE_APPS.md`: SDK nội bộ, app đang dùng, cách phân phối; mục G: bảng resource và khoá Remote Config có schema + version (2.0.0; G.3 viết lại ở 2.1.0: khoá game chỉ chạy khi có `appplugin-game`).
- `DOC/62_AD_MONETIZATION_STANDARD.md`: chuẩn quảng cáo thống nhất cho mọi app.
- `DOC/67_SDK_HOAN_THIEN_DE_GHEP_APP.md`: kế hoạch hoàn thiện SDK trước khi ghép vào app.
- `DOC/65_SDK_RELEASE_GRADE_CRITERIA.md`: tiêu chí G1 đến G13 cho bản phát hành (G12: billing qua harness).
- `DOC/46_BACKEND_CONTRACT.md`: hợp đồng event và backend (`iap_purchase` v2, `/rtdn`, `signals`).
- `CHANGELOG.md`: thay đổi từng bản và mục "Phải làm khi nâng cấp".

## 8. Billing, paywall và subscription (1.8.0)

Tất cả nằm sau một cổng nội bộ (`BillingPort`); app chỉ khai sản phẩm và cấu hình từ xa. API public không đổi ở 1.8.0.

### 8.1 Khai sản phẩm

Trong `MonetizationSdk.configure(this) { ... }` (mẫu: `app/src/main/java/com/app/plugin/demo/DemoApplication.kt`):

```kotlin
inappProducts = listOf("demo_coin_pack")
subsProducts = listOf("demo_premium_monthly")
consumableProducts = setOf("demo_coin_pack")       // chỉ thứ thực sự tiêu hao: consume xoá quyền lợi vĩnh viễn
removeAdsProducts = setOf("demo_premium_monthly")  // bỏ sót = subscriber vẫn thấy quảng cáo
```

`configure` đặt đúng thứ tự (quyền lợi trước `IapHelper.init`); `report.describe()` cảnh báo id gõ nhầm và sub không có `removeAdsProducts`/`removeAdsMasks`.
Id phải khớp sản phẩm trên Play Console; id chưa khai trên Play (như id của demo) chỉ cho một dòng log mã lỗi query, SDK không giả vờ có giá.

### 8.2 Remove-ads theo mask

Sản phẩm trong `removeAdsProducts` mặc định gỡ **mọi** định dạng (`RemoveAdsMask.ALL`). Gói rẻ hơn khai `removeAdsMasks = mapOf("lite" to RemoveAdsMask.INTERRUPTIVE)`:
gỡ banner, native collapsible, rect, interstitial/app open/native full; native inline và rewarded vẫn hiện (rewarded vì người dùng cần nó để mở khoá nội dung).
Sau `grant`, mọi lượt show của định dạng bị gỡ trả `ad_show_fail` với `reason=remove_ad` và không phát `ad_show`. Quyền lợi lưu trên máy có chữ ký HMAC-SHA256 (khoá Android Keystore);
sửa tay prefs hay khôi phục sang máy khác làm quyền lợi rỗng cho tới khi SDK tự `restorePurchases()` (vài ms lúc mở app `Entitlements.isActive` trả rỗng, quảng cáo vẫn theo mask đã lưu).

Luật Play SDK tuân theo (không cấu hình): giao dịch `PENDING` và `isSuspended()` **không bao giờ** cấp quyền lợi; acknowledge trước khi báo thành công; một luồng mua tại một thời điểm
(`purchase()` thứ hai trả `IapResult.Failed(-2, …)`); mọi lệnh store chạy trên luồng `appplugin-billing`, callback về app trên main; máy không có Play (`BILLING_UNAVAILABLE`) một dòng log, không thử lại trong process.

### 8.3 Paywall schema v2 (`paywall_config_android`)

Khoá Remote Config chứa một JSON (mẫu lấy từ `src/test/java/com/app/plugin/paywall/PaywallV2Test.kt`):

```json
{"schema_version":2,"version":"pw_v2","holdout":{"fraction":0.0},"session_cap":1,"placements":{
  "shop":{"variants":[{"id":"a","weight":100,"products":["coin_small","coin_big"],"highlight":"coin_big"}]},
  "offer":{"decision_point":"CURRENCY_LOW","proactive":true,"variants":[{"id":"o","weight":100,"products":["vip"]}]}}}
```

- `holdout.fraction` mặc định **0** (vắng = 0; JSON v1 không có `schema_version` vẫn nhận, holdout 0). Muốn holdout thì khai tỉ lệ: một lần rút trên `user_id` của SDK trong không gian băm `paywall_holdout_v2`, độc lập với holdout của brain.
- `session_cap`: số paywall `proactive` tối đa mỗi process, đếm khi paywall thật sự hiện; vắng = không giới hạn; placement người dùng tự mở không bị giới hạn. `proactive` mặc định `false`.
- `schema_version` khác 2 (kể cả chuỗi `"2"`), `holdout.fraction` ngoài [0,1], `session_cap` không phải số nguyên không âm → **cả cấu hình bị từ chối**, SDK giữ cấu hình đang chạy và `logE` một dòng.
- Funnel: `paywall_impression` → `paywall_choice` → `iap_purchase` cùng `paywall_session_id` (phiên 30 phút, không đóng khi người dùng tắt paywall); `paywall_skip` có `reason` = `session_cap`, `brain` hoặc `no_config`. Bảng: DOC/46 B.3, DOC/62 §2.1.

### 8.4 Vòng đời subscription và nhắc sửa thanh toán (`subscription_lifecycle_android`)

Khoá Remote Config `subscription_lifecycle_android` (cần `version`):

```json
{"version":"sl_on","enabled":true,"in_app_messages":{"enabled":true}}
```

- (T11) `in_app_messages.enabled` mặc định **bật**: khi có sản phẩm `IN_GRACE_PERIOD`, `ON_HOLD` (nhãn từ server) hoặc giao dịch `isSuspended()` (chưa hoạt động ở 1.8.0, xem giới hạn t11), lần vào foreground kế tiếp SDK tự gọi `showInAppMessages` của Play
  (hộp thoại "sửa thanh toán" của Google), mỗi sản phẩm tối đa một lần mỗi 24 giờ, rồi `restorePurchases()` nếu Play báo sub đã cập nhật. `"in_app_messages":{"enabled":false}` tắt hẳn (0 lời gọi). Không có API public mới.
- (T11) `BillingProxyCrashGuard`: Play Billing đôi khi ném `NullPointerException` từ `com.android.billingclient.api.ProxyBillingActivity`; SDK bọc uncaught-exception handler và nuốt **chỉ** lỗi đó (NPE có frame `ProxyBillingActivity`), tối đa 5 lần mỗi process, mọi lỗi khác chuyển cho handler trước.
- **Giới hạn grace:** client không thấy grace trực tiếp — Play báo sub đang trong grace như sub đang gia hạn (`PURCHASED`). Grace/hold chỉ biết khi backend nhận RTDN rồi trả `signals` trong phản hồi `/push` (DOC/46 C.3).
  Không có backend/RTDN thì nhãn suy từ Play như trước. Nhãn server chỉ thắng quan sát của client trong 7 ngày kể từ `event_ms`, và **chỉ đặt nhãn**: không cấp/thu hồi quyền lợi, không ảnh hưởng `RecurringGrants`.
- `iap_purchase` phía client của người chọn `DENIED` (consent) không được báo; sự thật về tiền là RTDN/`subscriptionsv2` (chờ H7b, DOC/67 §4).

### 8.5 Chạy harness billing G12 (12 script)

Harness chạy kịch bản mua/huỷ/restore/grace qua state machine thật của `IapHelper` trên máy, không cần Play. Chỉ bật khi đủ 3 khoá: `flagTest` bật (`1` hoặc `90351`), meta-data `com.app.plugin.IAP_HARNESS=true`
(chỉ có trong `app/src/utility/AndroidManifest.xml`; app release không có) và extra `appplugin_iap_script` khi khởi động. Thiếu khoá nào thì không nạp `ScriptedBillingPort`/`IapScripts`.

```
adb -s <serial> shell pm clear test.sdk.zd
adb -s <serial> shell am start -W -n test.sdk.zd/com.app.plugin.demo.MainActivity --es appplugin_iap_script buy_sub
adb -s <serial> logcat -d | grep 'iap harness'      # armed script=buy_sub / buy_sub step=<k> ... / buy_sub done
```

Mười hai tên: `buy_sub`, `buy_cheap`, `cancel`, `pending_then_paid`, `kill_mid_purchase` (sau `am force-stop` mở lại bằng `restore_unacked`), `restore_owned`, `reopen` (không script), `restore_store_error`, `revoke`,
`grace`, `denied` (thêm `--ez force_eea true`, chạm "Do not consent"), `messages_off`. `pm clear` chỉ trước script cài mới (`buy_sub`, `buy_cheap`, `cancel`, `pending_then_paid`, `kill_mid_purchase`, `restore_owned`, `grace`, `denied`, `messages_off`); **không** `pm clear` giữa `kill_mid_purchase` và `restore_unacked`, và `reopen`/`restore_store_error`/`revoke` chạy sau một `buy_sub` mà không `pm clear` (hợp đồng B dòng 5, 7, 8, 9); `grace` và `messages_off` dùng chung token fixture `rtdn-fixture-sub-token` nên `pm clear` giữa hai script đó. `grace`/`messages_off` cần sink cục bộ (đặt `BRAIN_RTDN_TOKEN` ≥ 16 ký tự cho `/rtdn`; cờ của `local_sink.sh` là Task 16)
(`bash tools/local_sink.sh start --port <p> --dir <d>`) có `/rtdn` và fixture `backend/tests/fixtures/rtdn/sub_06_in_grace_period.json`.
Chạy đủ 12 script và kiểm khẳng định tự động: `bash tools/device_gate.sh --variant utility --iap --sink-port <p> --sink-dir <d> --apk <apk> --mapping <mapping>` (Task 16; cổng G12 ở `DOC/65_SDK_RELEASE_GRADE_CRITERIA.md`).
Harness không thay cho mua thật: màn mua của Play, grace/hold và RTDN thật là việc của H7b (`DOC/67_SDK_HOAN_THIEN_DE_GHEP_APP.md` §4).

## 9. Giấy phép (2.1.0, token bắt buộc)

Token `ALP1.<payload>.<chữ ký>` (Ed25519) gắn gói + chứng chỉ ký + hạn; app khai trong `AndroidManifest` meta-data `com.app.plugin.LICENSE` (string). Công cụ stdlib-only `tools/license_issue.py`:

```bash
python3 tools/license_issue.py keygen --out ~/.appplugin/license_<kid>.alpk     # khoá riêng ngoài repo, mode 600; in khoá công khai hex
python3 tools/license_issue.py issue --key <file> --kid <kid> --pkg <appId> --cert <sha256hex> [--cert …] --days 365 --feat ads,brain
python3 tools/license_issue.py verify --pub <hex> <token>                        # rc 0 hợp lệ, 1 sai/malformed; --now <unix> thêm: quá hạn rc 3
```

`verify` mặc định không kiểm `exp` (SDK thi hành hạn lúc chạy); `--now` chỉ để kiểm tay. Khoá công khai hiện hành (kid `dev1`, hex 64 ký tự, không phải bí mật): `a6dac8ebd0ac4becd6dc0e67335b9d92e13f529760a6585d430fd2dfb6d036e7`.

Token = `applicationId` + SHA-256 chứng chỉ ký + hạn (mặc định 12 tháng, `--days`) + tính năng `ads`, `brain`. `--cert` lặp được cho nhiều cert (ví dụ cert upload và cert Play App Signing); lấy SHA-256 từ `keytool -list -v -keystore <file>` hoặc `apksigner verify --print-certs <apk>`; bản phát hành qua Play dùng cert của Play App Signing. Mỗi process kiểm một lần, trên luồng nền, 0 IPC thêm trên main; không token hoặc token sai (`missing`, `malformed`, `kid`, `sig`, `pkg`, `cert`, `expired`, `feat`) thì không quảng cáo, không brain, một dòng `license: INVALID (<lý do>)`; hợp lệ in `license: VALID kid=<kid> exp=<ngày>`. Hết hạn thì phải phát token mới và phát hành lại app (không có thời gian ân hạn).

Khoá riêng (`ALPK1.…`) không bao giờ vào repo (`*.alpk` bị ignore; `keygen` từ chối đường dẫn trong repo hoặc file đã có). Test: `bash tools/tests/license_issue_test.sh`.

### 9.1 Bỏ chuỗi log SDK khỏi dex (tuỳ chọn, 2.1.0)

Thêm `docs/integration/appplugin-strip-logs.pro` vào `proguardFiles` của bản release của app: rule `-assumenosideeffects` bỏ lời gọi `LogUtil.logD`/`logW` của SDK nên chuỗi log không còn trong dex. Không bật ở bản app chạy cổng thiết bị của SDK (các cổng đọc dòng `logD`); production vốn đã im lặng. Rule này **không** nằm trong `consumer-rules.pro`, nên app không làm gì thì không đổi. Mỗi bản dựng còn mang dấu vết `appplugin/<phiên bản>/<git sha>` (`BuildInfo.ID`, vẫn trong dex sau R8) để truy nguồn APK.

### 9.2 Hiệu năng

Cách đo, ngưỡng và bảng số theo máy: `DOC/65_SDK_RELEASE_GRADE_CRITERIA.md`, mục "Hiệu năng" (công cụ `tools/perf_gate.sh`, `tools/perf_report.py`).
