# 64 — Chạy thử 1.3.0 trên máy: consent trước khởi tạo, các định dạng quảng cáo, độ bền

**Ngày:** 2026-09-23
**Bản:** nhánh `release/1.3.0` (app demo `test.sdk.zd`, bản debug, `flagTest=1`, ad unit test/RC thật
của project demo).
**Máy:** emulator Pixel 8 Pro, Android 37 (API 37), trang nhớ 16 KB, Play services 26.33. Thêm một lượt
trên Samsung Galaxy A25 (SM-A256E, Android 16) cho kịch bản đầu; không chạy tiếp trên máy này vì một
phiên khác đang chạy instrumentation test của `com.lunaviet.calendar` trên chính nó.
**Cách đọc log:** `adb logcat -v threadtime`, lọc theo PID của app; tag `AppPlugin` (SDK),
`AppPluginDemo` (callback app nhận), `UserMessagingPlatform`, `Ads`.
**Nhãn:** [M] đo trực tiếp.

---

## A. Kết quả [M]

| # | Kịch bản | Kết quả |
|---|---|---|
| A1 | Cài mới, ngoài EEA | Mạng chờ UMP (`waiting for the consent answer`), khởi tạo ngay sau khi UMP ghi `gdprApplies:0`. Không dòng log nào của AdMob, Meta, AppLovin trước đó. |
| A2 | Mở lại, ngoài EEA | UMP báo UNKNOWN lúc mở app rồi mới báo đúng; sau bản sửa ở mục B.1 thì khởi tạo sau ~0,4 s. |
| A0 | Cài mới, mất mạng lúc mở | UMP lỗi `Error making request`: không mạng nào khởi tạo, app không treo. |
| B1 | Cài mới, EEA (debug geography) | Form hiện; lúc form đang hiện app chỉ có kết nối tới máy chủ Google (UMP, Firebase), không tới Meta, AppLovin, Unity, Vungle, Mintegral, Bigo, Pangle. |
| B2 | Bấm Consent | Mạng khởi tạo 0,4 s sau khi form đóng. |
| B3 | Mở lại, EEA đã đồng ý | Không hiện form; khởi tạo sau ~0,4 s (sau bản sửa B.1; trước đó ~2,5 s). |
| B4 | Bấm Do not consent | `IABTCF_PurposeConsents = 00000000000`, `canRequestAds` true, mạng khởi tạo; AdMob gắn `npa=1`. |
| C | Banner, interstitial, rewarded, native, native full, app open | Cả sáu hiện quảng cáo test, callback đúng thứ tự. Rewarded: `REWARDED` đúng một lần, trước `dismissed`. |
| D1 | Interstitial → Full2 | Full2 (native full) hiện 212 ms sau khi interstitial đóng, đã dựng sẵn phía sau (`prepared chained dialog`), không lộ màn hình app ở giữa. |
| D2 | Rewarded → Full2 | Thưởng một lần, rồi Full2 hiện 650 ms sau khi rewarded đóng. |
| D3 | App open rồi đóng | Không nối Full2 (đúng chuẩn doc 62 mục 3.M). Về nền rồi mở lại lúc quảng cáo còn hiện: không chồng thêm quảng cáo mở app. |
| E1 | Xoay màn hình 4 lần lúc form consent đang hiện | Sau bản sửa B.2: form vẫn trên màn hình, trên Activity mới; bấm Consent thì mạng khởi tạo. |
| E2 | Monkey 1.500 sự kiện (chạm, vuốt, xoay 5%, đổi app 2%) | Không crash, không ANR. Hai khung hình chậm (1,1 s và 0,8 s) lúc vẽ interstitial trên GPU phần mềm của emulator; ngưỡng ANR là 5 s. |

Unit test: 587/587. Lint: 0 lỗi. Demo release có R8: chạy qua.

## B. Lỗi tìm ra khi chạy thử, đã sửa trong 1.3.0 [M]

1. **Người dùng đã trả lời vẫn phải chờ UMP qua mạng.** UMP đọc câu trả lời đã lưu trên luồng nền:
   ngay lúc gọi `canRequestAds()` ở `onCreate` nó báo UNKNOWN, khoảng 0,2 s sau mới báo đúng. Cổng chỉ
   hỏi một lần rồi chờ listener, nên phải chờ cả lần cập nhật (1–2,5 s). Nay hỏi lại mỗi 100 ms trong
   3 s đầu.
2. **Form consent mất khi Activity bị tạo lại.** Xoay màn hình lúc form đang hiện: UMP trả lỗi
   `3 Activity is destroyed`, cổng mở mà người dùng chưa trả lời. Từ 1.3.0 mạng chỉ khởi tạo sau
   consent, nên ở EEA cả phiên không có quảng cáo. Nay form hiện lại trên Activity mới.
3. **AppLovin khởi tạo với key rỗng** ở app không dùng MAX: mở phiên, ghi lỗi kèm stack trace. Nay bỏ
   qua khi `admax_devKey` trống.
4. **Không biết mạng khởi tạo xong hay hết giờ.** Thêm log thời gian khởi tạo và trạng thái adapter.

## C. Không phải lỗi SDK [M]

- `GoogleApiManager: SecurityException … Unknown calling package name 'com.google.android.gms'`,
  `FlagStore … Phenotype.API is not available`: Play services trên emulator.
- `Volley 404` (ảnh của creative test), `Ads: JS: Refused to get unsafe header`: nội bộ Google.
- `ThemeUtils: ImageFilterView … Theme.AppCompat`: Activity của app demo không dùng theme AppCompat;
  app thật dùng AppCompat thì không có dòng này.
- Nút đóng màu xanh lá của native full: lưới debug có chủ đích khi `flagTest` bật (`isShowGrid`).
- AdMob khởi tạo chậm tới 10 s (`MobileAds: NOT_READY … Timeout`) trên emulator mạng chậm: cơ chế
  8 s của SDK giải phóng load đang chờ như thiết kế.

## D. Ghi nhận, chưa sửa (có từ trước 1.3.0)

> **2026-09-24, nhánh `fix/1.3.1`** (CHANGELOG mục 1.3.1): đã sửa ba mục đầu. Mục thứ ba mới
> sửa phần callback. Việc hoãn `onAdDismissed` của quảng cáo cha tới khi Full2 xong chờ chủ dự án
> chốt, vì nó đổi hợp đồng callback của mọi app. Chạy lại trên thiết bị: mục F.

- `IapHelper setup failed: 5 Client is already in the process of connecting` ở mỗi lần mở app, ngay
  sau đó là `IapHelper connected`: kết nối billing vẫn thành công. **Chẩn đoán 1.3.0 sai, sửa lại
  2026-09-30 (1.6.4 F2B, `.superpowers/sdd/v164/f2b-analysis.md`):** không phải "lần tự kết nối lại
  của Billing 9 chạy chồng, không phải lỗi". Gốc là `IapHelper.isReady()` (và cổng `purchase` /
  `restorePurchases`) luôn `true`: với `enableAutoServiceReconnection()` thì
  `BillingClientImpl.isReady()` = `if (zzG) return true` ở mọi trạng thái (đọc bytecode
  `billing-9.0.0`). Lượt khôi phục lúc vào foreground (`AppPluginBase.onStart`) vì thế chạy giữa lúc
  kết nối đầu còn đang chạy; vòng thử kết nối lại của thư viện va vào kết nối đó và nhận
  `DEVELOPER_ERROR` 5; kết nối chậm (>~3 s) thì lượt đó lỗi sau `Max retries reached`, và kết quả 5
  tới trễ được phân loại sau khi đã `connected`. Có từ 1.3.0 (thấy trên Samsung A25, 1.6.4 vòng 1).
  **Đã sửa ở 1.6.4:** `isReady()` `false` trong lúc kết nối đầu / đang kết nối lại, `purchase()` trả
  `false` ngay, và kết quả "already connecting" tới trễ sau khi đã kết nối không còn là lỗi. Trên máy:
  Samsung vòng 2 (`t164-r-R5CX10P9EKZ-gate-2-{1,2,3}.log`) `setup failed` / `already connecting` /
  `Max retries` = 0/0/0 ở 6/6 chặng.
- Lỗi show khi quảng cáo chưa tải báo `not pl config` (thông báo của mạng cuối trong waterfall) thay
  vì "not loaded". **Đã sửa, và lỗi nặng hơn mô tả:** callback này tới app ngay giữa waterfall, nên
  khi mạng phía sau hiện được thì app nhận "lỗi" rồi quảng cáo vẫn hiện.
- App nhận `onAdDismissed` của quảng cáo cha trước khi Full2 hiện, và không nhận callback nào của
  Full2. App điều hướng ngay trong `onAdDismissed` thì Full2 hiện đè lên màn hình mới. **Đã sửa phần
  callback** như Speaker Clean: app nhận `onAdShowed`/`onAdImpression`/`onAdClick` của Full2
  (placement `full_2_*`). `onAdDismissed` vẫn tới đúng một lần, ngay khi quảng cáo cha đóng.
- Trước consent, các provider/initializer tự chạy lúc mở process của SDK quảng cáo bên thứ ba vẫn có
  mặt: `MobileAdsInitProvider`, `AudienceNetworkContentProvider`, `AppLovinInitProvider`,
  `VungleProvider`, `BigoAdsProvider`, `MBComponentLifecycleProvider`, Unity `AdsSdkInitializer`. Lúc
  form đang hiện không thấy kết nối nào tới các mạng đó, nhưng đó là ảnh chụp một thời điểm, không phải
  bằng chứng đầy đủ.
- AppsFlyer và Firebase Analytics chạy trước consent (ngoài phạm vi 1.3.0).

## E. Chưa kiểm

- Mạng trực tiếp Meta Audience Network và MAX tải quảng cáo thật: app demo không có id Meta, không có
  key MAX. Hàng chờ của hai mạng này mới được kiểm bằng log khởi tạo và review code.
- Trọn bộ kịch bản trên máy thật (mới chạy A1 trên Samsung).

## F. Kiểm lại 1.3.1 (2026-09-24) [M]

**Bản:** nhánh `fix/1.3.1` tại `9081988` (sau Task 7a, 7b). APK release `app-release.apk` 27.381.467 byte,
sha256 `d6ac040841646f76…` (R8 full, `shrinkResources`, `targetSdk` 36, ký debug); bản debug chỉ dùng cho
kịch bản (i). Máy thật: APK release dựng lại lúc 19:36 (`release-gate-20260924-193609.log`, sau commit
`f2c8913` lúc 19:33, commit đó chỉ đổi doc và số phiên bản): PASS, 8 ad unit, 217/221 = 98% đổi tên,
zipalign 16 KB OK.
**Máy:** emulator Pixel 8 Pro API 37 (`emulator-5570`, `-read-only`, Play services 26.33.32), AVD `api28`
(Play services 21.45.15) và `api26` (Play services 20.24.14) trên `emulator-5580`; máy thật Samsung
Galaxy A25 `SM-A256E`, API 36, serial `R5CX10P9EKZ`. Emulator cần
`-dns-server 8.8.8.8,1.1.1.1`: lần boot đầu không phân giải được tên miền (UMP `Error making request`,
Crashlytics `Unable to resolve host`), các log đó đổi tên `*-nodns*` và không tính.
**Log:** `DOC/testing/` (không commit). Kịch bản (a)–(j) như Task 7 lần 1 (`.superpowers/sdd/task-7-report.md`).

| Bước | Kết quả | Log |
|---|---|---|
| Unit ×3 (`:testDebugUnitTest --rerun`, XML xoá trước mỗi lần) | 625 test, 0 fail, 0 lỗi, 0 skip cả ba lần, exit 0 | `task7r-unit-{1,2,3}.log` |
| Test offline allowlist cổng thiết bị | PASS 30/30 | `task7r-allowlist-test.log` |
| `tools/release_gate.sh` | PASS: 8 ad unit trong `resources.arsc` (cần ≥ 8), 217/221 = 98% lớp SDK đổi tên, zipalign 16 KB "Verification successful" | `release-gate-20260924-173019.log` |
| `device_gate.sh` API 37, 1500 sự kiện × 3 seed | PASS: TotalTime 819 ms; chuỗi R8 sau cold start `=0 (raw=0, allowlisted=0)`, sau monkey `=0 (raw=0, allowlisted=0)`; FATAL/ANR/native 0/0/0; `ClassValue` 0/0 | `device-gate-emulator-5570-20260924-174540.log` |
| `device_gate.sh` API 28 | PASS: TotalTime 2531 ms; `=0 (raw=1, allowlisted=1)` cả hai lần (dòng ProviderInstaller); 0/0/0; `ClassValue` 0/0 | `device-gate-emulator-5580-20260924-183929.log` |
| `device_gate.sh` API 26 | PASS: TotalTime 1189 ms; `=0 (raw=1, allowlisted=1)` cả hai lần; 0/0/0; `ClassValue` 0/0 | `device-gate-emulator-5580-20260924-192120.log` |
| `EVENTS=1500 device_gate.sh` Galaxy A25 (API 36, máy thật) | PASS: TotalTime 1223 ms; chuỗi R8 sau cold start `=0 (raw=0, allowlisted=0)`, sau monkey `=0 (raw=0)`; FATAL/`am_anr`/native 0/0/0 lúc cold start và cả 3 seed | `task7r-phone-device-gate.log`, `device-gate-monkey-R5CX10P9EKZ-20260924-193856-seed{1,2,3}.log` |
| StrictMode (com.app.plugin / .demo) | 22/22 (API 37), 22/22 (28), 20/20 (26), 27/27 (Galaxy A25); R8 repackage giấu frame SDK, cổng từ 1.4.0 | log cổng |

Kịch bản trên API 37, bản release. Mọi log: FATAL của app 0, `WindowLeaked` 0, `am_anr` của app 0,
`BadToken` 0, `isAdsFullShowing` 0. Cờ full-screen không có dòng log riêng: nó về 0 được chứng minh bằng
lượt interstitial kế tiếp hiện ở lần bấm đầu (show bị chặn với `isAdsFullShowing` khi cờ > 0).

| # | Kịch bản | Kết quả | Log |
|---|---|---|---|
| a1 | Cài mới, EEA, **Consent** | 0 dòng khởi tạo mạng trước cú bấm (chỉ `waiting for the consent answer` và dòng dex của FB); UMP dismiss → `consent allows ad requests, starting` +162 ms | `task7r-consent-accept.log`, `…-before-tap.log` |
| a2 | `pm clear`, EEA, **Do not consent** | dismiss → `starting` +147 ms | `task7r-consent-reject.log` |
| a3 | Xoay 4 lần lúc form consent hiện | 4 × `form lost with its activity, showing it on the new one`; Consent → `starting` +160 ms | `task7r-consent-rotate.log` |
| b | Interstitial → Full2 | 1 `dismissed` của cha; Full2 hiện sau 118 ms | `task7r-inter-full2.log` |
| c | Rewarded → Full2 | `REWARDED` 1 lần, trước `dismissed` (1 lần); Full2 sau 386 ms | `task7r-reward-full2.log` |
| d | Xoay 4 lần lúc Full2 hiện, rồi 2 lượt Interstitial → Full2 | `host destroyed while showing, dismissing` 1, `loadFull2ForThisTurn: host destroyed, skip` 1; hai lượt sau: interstitial hiện ở lần bấm đầu, Full2 hiện; 3 `dismissed` cho 3 interstitial | `task7r-rotate-full2.log` |
| d (máy thật) | Như (d), trên Galaxy A25 (API 36), cùng APK release với cổng thiết bị máy thật | Interstitial hiện 19:43:06, `dismissed` 19:43:17.623, Full2 hiện 19:43:17.689 (66 ms). Xoay 4 lần từ 19:43:22: `ads full nt admob20 full_2_default host destroyed while showing, dismissing` 19:43:23.013 và `loadFull2ForThisTurn: host destroyed, skip` 19:43:23.050, mỗi dòng 1 lần. Hai lượt sau (19:44:35, 19:45:04): interstitial hiện ở lần bấm đầu, Full2 hiện sau mỗi lượt, 1 `dismissed` mỗi lượt (3 cho cả log). Cả log: FATAL (app và mọi process) 0, `WindowLeaked` 0, `am_anr` 0, `BadToken` 0, `isAdsFullShowing` 0, `dismiss already dispatched, skip` 0, tombstone 0. Cold start lần này (`am start -W`) TotalTime 479 ms (chỉ có ở stdout phiên chạy, không có log riêng) | `task7r-phone-rotate-full2.log`, `task7r-phone-rotate-{before,after}.png` |
| d (máy thật, sau đợt sửa cuối) | Như dòng trên, trên `a2428cd` (sau `e19e36f` đổi đường show của vòng open/interstitial/rewarded). Cổng release PASS: 225 lớp, 221 đổi tên (98%), zipalign OK. Cổng thiết bị `EVENTS=1500` PASS: TotalTime 1002 ms, chuỗi R8 0 sau cold start và sau monkey (raw 0), FATAL/`am_anr`/native 0/0/0 lúc cold start và cả 3 seed | Kịch bản (d): cold start 431 ms; interstitial hiện 20:05:30, Full2 hiện 20:05:42; xoay 4 lần từ 20:05:47: `host destroyed while showing, dismissing` 1, `loadFull2ForThisTurn: host destroyed, skip` 1; hai lượt sau (20:07:00, 20:07:31) interstitial hiện ở lần bấm đầu, Full2 hiện sau mỗi lượt, 1 `dismissed` mỗi lượt; cả log FATAL 0, `WindowLeaked` 0, `am_anr` 0, `BadToken` 0, `isAdsFullShowing` 0, `dismiss already dispatched, skip` 0, tombstone 0. Demo đã gỡ khỏi máy | `final-phone-release-gate.log`, `final-phone-device-gate.log`, `final-phone-rotate-full2.log`, `final-phone-rotate-{before,after}.png`, `final-phone-run-a2428cd.log` (stdout cả chuỗi, có TotalTime) |
| e | Banner + xoay 8 lần, qua refresh 60 s, rồi Banner + NtCl | 0 "already has a parent", 0 `detach failed`; 4 impression banner, 1 NtCl | `task7r-banner-rotate.log` |
| g | Cold start (consent đã có) | TotalTime 500 ms; `consent on record, starting` sau 166 ms; `ads admob initialize: not complete after 8000 ms` rồi `complete after 30137 ms` (mạng emulator chậm, như mục C) | `task7r-coldstart.log` |
| h | Bấm đóng Full2 và xoay trong cùng giây, 3 lần | Lần xoay trước: `host destroyed while showing` 1, rồi listener tới muộn `dismiss already dispatched, skip` 1: đúng một dismiss (một `loadFull2ForThisTurn`); hai lần bấm trước: đóng bình thường. 4 `dismissed` cho 4 lượt | `task7r-close-rotate.log` |
| i | Heap bản debug, ép GC bằng `am dumpheap` | Luồng như Task 7a: Full2 xoay 2 lần + banner + 4 lần xoay + 95 s: **2 Activity** (hiện tại + 1 cũ). Đường giữ cái cũ: WebView GMA (`zzcmp → WindowManagerImpl.mContext`), `AdsPlacementConfig.cbShow → DemoShowCb`, và `AdsEcmp.adObject` (interstitial GMA đã preload với Activity đó làm context). Luồng dài hơn: tới 3 cái cũ bị giữ mạnh, về 1 sau khi interstitial đang cache được hiện; không tăng dần | `task7r-leak-debug-ref.log`, `task7r-leak-debug.log`, `task7r-leak-debug-bounded.log` |
| j | Guard `host destroyed, skip` và cold start | Xoay lúc Full2 hiện: có dòng (d, h). Host bị huỷ 4 lần lúc interstitial GMA cha hiện: 0 dòng (không load nào trong cửa sổ `clearActivity`), Full2 xếp hàng và hiện trên host mới 68 ms sau khi đóng cha. Cold start: 0 dòng `host destroyed`; mất mạng thì AdMob `tryLoad fail` rồi waterfall đi tiếp 3 bước (`loadOpenCircle`), app demo không có id Meta/MAX nên các bước đó không có dòng load mạng | `task7r-rotate-parent2.log`, `task7r-coldstart-offline.log` |

**G9 "không leak Activity: 1 Activity sau GC", luồng Full2: chưa đạt.** Đo (i) được 2 Activity sau GC
(hiện tại + 1 cũ) trên luồng tham chiếu. Trên luồng dài có tới 3 Activity cũ bị giữ mạnh. Đường giữ
thứ ba là `AdsEcmp.adObject`: nó giữ interstitial GMA đã preload, và GMA (`zzbtd`) giữ Activity truyền
vào `InterstitialAd.load(mActivity!!, …)` (`AdsMobPlInters.kt:49`, có từ trước nhánh này). Số Activity
cũ bị giữ có chặn trên: mỗi interstitial đang cache giữ một cái, nhả ra khi quảng cáo đó được hiện, và
không tăng dần. Sửa (load bằng application context nếu GMA cho phép, hoặc bỏ quảng cáo cache khi host
chết) nằm ngoài phạm vi 1.3.1. Mục "✓" của G9 ở doc 65 chỉ áp cho form consent.

**Trạng thái:** 1.3.1: G1–G8 và G10 đủ; G9 một phần. Emulator API 26/28/37 đo tại `9081988`; máy thật
Galaxy A25 (API 36) đo lại tại `a2428cd` (sau `e19e36f` đổi đường show): cổng thiết bị + kịch bản (d)
PASS. Các kịch bản tay (a)–(c), (e)–(j) chỉ trên emulator và chưa chạy lại sau `e19e36f`. G9 "1 Activity
sau GC" của luồng Full2 chưa đạt (đoạn trên).

## G. Kiểm 1.4.0 (2026-09-25) [M]

**Bản:** nhánh `feat/1.4.0`, cây `05ca0bf` + `sdkVersion = "1.4.0"` (commit chốt ngay sau chỉ đổi doc và số phiên
bản). APK release `app-release.apk` 27.414.415 byte, sha256 `70286d4fc40ab131…` (`release-gate-20260925-035745.log`);
APK debug (có `af_app_id` giả trong `app/src/debug`) 43.939.601 byte, sha256 `e7e4ebd8da0f22be…`, chỉ cho kịch bản
(i), (k), (l).
**Máy:** emulator Pixel 8 Pro API 37 (`emulator-5570`), AVD `api28` và `api26` (`emulator-5580`), tất cả
`-read-only -dns-server 8.8.8.8,1.1.1.1`; kịch bản chạy headless (`-no-window`). **Máy thật Galaxy A25: chưa chạy
kịch bản tay (khoá màn hình)** — `dumpsys window` báo `mDreamingLockscreen=true` lúc bắt đầu và lúc kết thúc; số cổng
tự động của máy thật vẫn là lần đo tại `b4a268b` (`wtB2-release-gate.log`, `wtB2-device-gate.log`).
**Log:** `DOC/testing/` (không commit).

| Bước | Kết quả | Log |
|---|---|---|
| Unit ×3 (`--rerun`, XML xoá trước mỗi lần) | 931 test, 0 fail, 0 lỗi, 0 skip cả ba lần | `task17-unit-final-{1,2,3}.log`, `task17-unit-final-summary.log` |
| Test offline cổng thiết bị | PASS 49/49 (gồm retrace thật bằng r8.jar của Android Studio) | `task17-strict-prefs-green.log` |
| `tools/api_dump.sh --check` | `API unchanged (3865 lines)` | `task17-api-check-final.log` |
| `./gradlew :lintDebug` | 0 lỗi, 89 cảnh báo; lần đầu 1 lỗi `MissingPermission` (WAKE_LOCK) → sửa `96f9fa8` | `task17-lint-red.log`, `task17-lint-final.log` |
| `tools/release_gate.sh` | PASS: 8 ad unit (cần ≥ 8), 248/252 = 98% lớp SDK đổi tên, zipalign 16 KB OK | `release-gate-20260925-035745.log` |
| `device_gate.sh` API 37, lần 1 (trước `05ca0bf`) | **FAIL**: StrictMode 9 vi phạm do SDK, prefs 8, **khác 1** (`File.exists` trong `ContextImpl.ensurePrivateDirExists`, từ `context.filesDir` trong `AppPluginManager.initPlugin`; `~duration` 156 ms của StrictMode là thời gian tới hết message của looper, không phải chi phí lần đọc); còn lại sạch (R8 0/0, FATAL/ANR/native 0/0/0) | `device-gate-emulator-5570-20260925-035431.log` |
| `device_gate.sh` API 37, lần 2 | bỏ: emulator có cửa sổ quá tải (load 34), `am start -W` chờ 53.793 ms không in TotalTime → khởi động lại emulator | `device-gate-emulator-5570-20260925-040046.log` |
| `device_gate.sh` API 37 (sau `05ca0bf`) | PASS: TotalTime 1284 ms; chuỗi R8 `=0 (raw=0)` sau cold start và sau monkey; StrictMode 6 vi phạm, 6 do SDK = 6 lần nạp prefs đầu, `other=0`; FATAL/`am_anr`/native 0/0/0 lúc cold start và 3 seed; `ClassValue` 0/0; onCreate main-thread 112 ms | `device-gate-emulator-5570-20260925-040707.log`, `task17-gate-api37c-applog{,-post}.log` |
| Fix wave 1 (`6529164` + `9621b30`): `tools/release_gate.sh` | PASS: 8 ad unit, 248/252 = 98%, zipalign OK; APK sha256 `fb3c288d26532a61…` | `release-gate-20260925-052328.log` |
| Fix wave 1: `EVENTS=600 device_gate.sh` API 37 (headless) | PASS: TotalTime 728 ms; chuỗi R8 `=0 (raw=0)` hai lần; StrictMode 5 vi phạm, 5 do SDK = 5 lần nạp prefs đầu, `other=0`; FATAL/`am_anr`/native 0/0/0; onCreate main-thread 35 ms (một lần đo) | `device-gate-emulator-5570-20260925-052942.log`, `task17-gate-api37-fix1-applog{,-post}.log` |
| `device_gate.sh` API 28 | PASS: TotalTime 930 ms; `=0 (raw=1, allowlisted=1)` cả hai lần (ProviderInstaller); StrictMode 8/8, prefs 8, `other=0`; 0/0/0; onCreate 131 ms | `device-gate-emulator-5580-20260925-045515.log` |
| `device_gate.sh` API 26 | PASS: TotalTime 879 ms; như API 28; onCreate 106 ms; in `failed to clear the 'main' log` như các lần trước | `device-gate-emulator-5580-20260925-045813.log` |

Kịch bản trên API 37. Mọi log tính điểm: FATAL của app 0, `WindowLeaked` 0, `am_anr` của app 0, `BadToken` 0,
`isAdsFullShowing` 0, tombstone 0 (FATAL của process `UiAutomation` trong (a3) là công cụ `uiautomator dump`).
Một lượt (a1) đầu tiên bị bỏ: emulator có cửa sổ quá tải (load 42, CPU pressure 92%), app ANR `Input dispatching
timed out` khi form UMP đang tải, UMP `Error making request`; log đổi tên `task17-consent-accept-aborted-overload*.log`.
Chạy lại trên emulator headless.

| # | Kịch bản | Kết quả | Log |
|---|---|---|---|
| a1 | Cài mới, EEA, **Consent** (release) | Trước cú bấm: 0 dòng khởi tạo mạng, 0 `AppsFlyer_`, 0 `FirebaseMessaging`, `fcm auto-init held` 1. UMP dismiss → `consent allows ad requests, starting` +159 ms; `consent tier=GRANTED (tcf=GRANTED)`; Consent Mode cả bốn `true`, `collection=app-owned`; `sdk_health {sdk_version=1.4.0, consent_tier=GRANTED, …, kill=none}`; token FCM +2,2 s | `task17-consent-accept.log`, `…-before-tap.log` |
| a2 | `pm clear`, EEA, **Do not consent** | `DENIED (ump=OBTAINED, tcf=DENIED)`; Consent Mode cả bốn `false`, `collection=disabled`; `fcm held … no token`; dismiss → `starting` +162 ms | `task17-consent-reject.log` |
| a3 | Xoay 4 lần lúc form hiện | 4 × `form lost with its activity, showing it on the new one`; Consent → `starting` +70 ms | `task17-consent-rotate.log` |
| b | Interstitial → Full2 | 1 `dismissed`; Full2 sau 174 ms (lần bấm đầu `show not loaded`: cold start chưa load xong) | `task17-inter-full2.log` |
| c | Rewarded → Full2 | `REWARDED` 1 lần, trước `dismissed`; Full2 sau 135 ms | `task17-reward-full2.log` |
| d | Xoay 4 lần lúc Full2 hiện, rồi 2 lượt | `host destroyed while showing` 1, `loadFull2ForThisTurn: host destroyed, skip` 1; 4 lần huỷ `MainActivity`; hai lượt sau: interstitial ở lần bấm đầu, Full2 sau mỗi lượt; 3 `dismissed` | `task17-rotate-full2.log` |
| e | Banner + xoay 4, chờ 65 s, hiện lại + xoay 2, Banner + NtCl + xoay 2 | "already has a parent" 0, `detach failed` 0; banner 2 impression, NtCl 1; 8 lần huỷ. Không thấy dòng refresh banner trong 65 s (khác lần 1.3.1: `refreshBn2` 1) — **không phải lỗi (D6, DOC/66 mục 2, đã chốt):** chu kỳ refresh là 60 s, nhưng mỗi lần xoay `hideBanner` huỷ timer đang đếm; banner chưa bao giờ ở trên màn đủ 60 s liền trong kịch bản có xoay, nên không có dòng refresh nào là kỳ vọng đúng, không phải bằng chứng lỗi | `task17-banner-rotate.log` |
| g | Cold start, consent đã có | TotalTime 486 ms; `waiting` → `consent on record, starting` 249 ms; `sdk_health` `since_request_ms=250`; AdMob giữ load 8 s rồi `tryLoad ok`, khởi tạo xong sau 30.080 ms | `task17-coldstart.log` |
| h | Bấm đóng Full2 và xoay cùng lúc, 3 lần + 1 lượt thử | Lần xoay trước: `host destroyed while showing` 1, `dismiss already dispatched, skip` 1 (đúng một dismiss); hai lần bấm trước: đóng bình thường; 4 `dismissed` cho 4 lượt | `task17-close-rotate.log` |
| i | Heap (debug), luồng tham chiếu, ép GC bằng `am dumpheap` | 7 `MainActivity` trong heap; **2 giữ mạnh**: hiện tại (`DemoApplication.currentActivity`) + 1 cũ (WebView GMA: `ObjectWrapper → zzcmi → zzcmp.zzX → WindowManagerImpl.mContext`); 5 chỉ còn ref yếu/finalizer. Như 1.3.1: G9 "1 Activity sau GC" của luồng Full2 chưa đạt | `task17-leak-debug-ref.log`, `task17-leak-debug-ref-paths.txt` |
| j1 | Xoay 4 lần lúc interstitial GMA cha hiện (từ +12 s) | 4 lần huỷ, `host destroyed` 0; đóng cha → `queue Full2 until host resume` → Full2 sau 125 ms trên host mới (§7 #6: cha GMA do người dùng đóng sau cửa sổ `clearActivity`, không thuộc ca bỏ Full2 của Task F; kỳ vọng giữ nguyên); lượt thử hiện ở lần bấm đầu | `task17-rotate-parent.log` |
| j2 | Cold start mất mạng | TotalTime 698 ms; AdMob `tryLoad fail … err=0` rồi `loadOpenCircle` ×3; `host destroyed` 0 | `task17-coldstart-offline.log` |
| k | Kill switch `ads` (debug; khoá tiêm vào prefs `mem_sdk_kill_android` = `{"ads":true}` bằng `run-as`, **không qua Remote Config console**) | `kill switch: … từ bản lưu của phiên trước = {"ads":true} → tắt ads (SDK 1.4.0)`, `không khởi tạo mạng quảng cáo`, `sdk_health {…, networks=killed, …, kill=ads}`; 0 `tryLoad`; 5 lượt show bị chặn `show_blocked_kill_switch` (interstitial, rewarded ×2, native, app open), cổng `showBanner`/`loadNativeFullForThisTurn`/`showFull2` chặn; Remote Config về không có khoá → `kill removed mid-session; ads stay disabled until next open`. App demo không gắn `AdsLoadCb`/`cbShow` cho các nút này, nên callback `onLoadFailed` đúng một lần chỉ có ở unit test (`AdsDisabledLoadGateTest`). **G7 chưa kiểm console** | `task17-debug-kill-ads.log` |
| l | EEA từ chối (debug, `af_app_id` giả) | `DENIED`; Consent Mode `false`, `collection=disabled`; token FCM 0; `appsflyer started after consent: tier=DENIED isUserSubjectToGDPR=true hasConsentForDataUsage=false hasConsentForAdsPersonalization=false hasConsentForAdStorage=false start=activity`; request launch bị server từ chối (khoá giả, `code=50 … 404`). Mở lại: như trên, `start=activity` | `task17-debug-eea-deny.log`, `task17-debug-reopen-denied.log` |
| m | App open quá 4 giờ | Không chạy trên máy: đổi giờ máy làm hỏng emulator; chỉ unit (`AdsPlBaseMaxAgeTest`, `AdsEcmpsTest`) | — |

**Trạng thái (trước Redmi):** mọi cổng chạy lại PASS trên emulator API 26/28/37 (sau hai bản sửa `96f9fa8`,
`05ca0bf`; `05ca0bf` làm lại ở `6529164`, chạy lại cổng release và cổng thiết bị API 37: PASS); máy thật (Galaxy
A25) chưa chạy lại 1.4.0; G9 "1 Activity sau GC" của luồng Full2 và các tiêu chí console (G6 DebugView, G7)
chưa đạt/chưa kiểm.

### Máy thật Redmi 22021211RC (API 34, HyperOS), 2026-09-25 — DOC/66 mục 1

Bản cài: APK release của cây `b9ec778` (+ id test device thêm ở `6e6912a`). Máy được chủ dự án cho phép dùng
từ ngày này; không phải test device tự động của GMA/UMP (điện thoại thật của chủ dự án).

| Cổng / kịch bản | Kết quả | Log `DOC/testing/` |
|---|---|---|
| `device_gate.sh` (cold start, 4 chuỗi R8, 3 seed monkey × 1.500) | FATAL 0, native 0, `am_anr` 0, chuỗi R8 0/0. **G2 FAIL trước Task B:** StrictMode 13 vi phạm, 11 do SDK = 8 nạp prefs đầu + 3 "other" (D1, đã sửa ở Task A commit `95d8568`; chưa chạy lại cổng này trên chính máy Redmi sau Task A/B — controller kiểm) | `device-gate-65c9b673-20260925-092155.log` + 3 monkey |
| a1 cài mới, EEA, Consent | 0 mạng khởi tạo trước bấm; dismiss → `GRANTED`, Consent Mode 4× true, `collection=app-owned`, FCM auto-init bật, `starting` +79 ms, `sdk_health sdk_version=1.4.0` | `redmi14-a1-consent.log` |
| a2 `pm clear`, EEA, Do not consent | `DENIED`, Consent Mode 4× false, `collection=disabled`, FCM giữ, 0 token; mạng vẫn start (`admob,meta,max` — trước Task C #7/#12/#16; chưa chạy lại sau các fix đó) | `redmi14-a2-deny.log` |
| b Inter → Full2 | 1 `ondismiss` của inter, Full2 hiện 68 ms sau khi xếp hàng | `redmi14-b-inter-full2.log` |
| c Rewarded → Full2 | `REWARDED` 1 trước dismiss; Full2 73 ms | `redmi14-c-reward-full2.log` |
| d xoay 4 lần khi Full2 hiện + 2 lượt | `host destroyed while showing` 1, `skip` 1, 2 lượt sau hiện ở cú bấm đầu, 3 dismiss cho 3 inter (đo trước Task F #6 — kỳ vọng "không Full2 nào hiện lại trên host mới" của Task F chưa được xác nhận trên chính Redmi; controller chạy lại) | `redmi14-d-rotate-full2.log` |
| h đóng + xoay cùng một lệnh, 3 lượt | 3 lượt, mỗi lượt đúng 1 dismiss, `dismiss already dispatched` 0 | `redmi14-h-close-rotate.log` |
| g cold start có consent | `TotalTime` 321 ms, `AppPluginBase.onCreate` main-thread **21 ms**, `consent on record` | `redmi14-g-coldstart.log` |

Mọi log: FATAL app 0, `WindowLeaked` 0, `BadToken` 0, `isAdsFullShowing` 0.

Hai ghi chú (DOC/66 mục 1):
1. Trong ~7 phút đầu (09:24–09:31, 2026-09-25), demo chưa có id băm của Redmi nên `force_eea` không tác dụng và
   GMA in gợi ý `setTestDeviceIds`: **có ít nhất một request quảng cáo thật** tới ad unit
   `ca-app-pub-2290293694594597/2559364435` (Remote Config `mem_cf_ads_placement`, không có trong repo).
   Interstitial nạp xong nhưng không hiện nên không có impression; log của lượt đó đã bị ghi đè. Sau khi thêm id
   (`6e6912a`) mọi quảng cáo đều là "Test Ad".
2. **Galaxy A25 chưa chạy 1.4.0** (khoá màn hình lúc kiểm, xem "Trạng thái (trước Redmi)" ở trên): số cổng tự
   động của Galaxy A25 vẫn là lần đo tại `b4a268b` (1.3.1). Kịch bản i (heap), k (kill switch), l (AppsFlyer
   DENIED) và m (app-open 4 giờ) chưa chạy trên Redmi (cần bản debug hoặc đổi giờ máy; đã có trên emulator API 37,
   Task 17).

**Cổng cuối 1.4.0 (controller, 2026-09-25, APK dựng từ `c4749ca`; `65b3630` chỉ đổi tài liệu):**

| Cổng | Kết quả | Log `DOC/testing/` |
|---|---|---|
| `release_gate.sh` | PASS: 8 ad unit, 255/259 lớp SDK đổi tên = 98 %, zipalign 16 KB OK | `release-gate-20260925-160752.log` |
| `device_gate.sh` API 37 | PASS: StrictMode 0 dòng (by sdk 0), chuỗi R8 0, FATAL/native/`am_anr` 0, monkey 1.500 × 3 seed | `device-gate-emulator-5570-20260925-161032.log` |
| `device_gate.sh` API 28 | PASS: StrictMode 0 (trước ấm hoá prefs: 8 lần nạp prefs đầu), R8 0 (raw 1 allowlisted ProviderInstaller), FATAL/ANR 0 | `device-gate-emulator-5580-20260925-161334.log` |
| `device_gate.sh` API 26 | PASS: như API 28 | `device-gate-emulator-5580-20260925-161543.log` |
| Redmi, sau Task B (APK `c216628`) | PASS: by sdk 0 (prefs 0, other 0); 3 cold start `prefs warmup waited=0 ms`, `AppPluginBase.onCreate` main-thread 15–17 ms | `device-gate-65c9b673-20260925-103157.log`, `redmi14-B-warmup-coldstart.log` |

**Bản cuối trên Redmi (2026-09-25 17:40–17:57, APK dựng lại ở `22c0198` qua `release_gate.sh`, mã giống `729638f`):**

| Cổng / kịch bản | Kết quả | Log `DOC/testing/` |
|---|---|---|
| `release_gate.sh` | PASS: 8 ad unit, 255/259 = 98 %, zipalign OK | `release-gate-20260925-174026.log` |
| `device_gate.sh` Redmi | PASS: StrictMode by sdk 0 (prefs 0, other 0), R8 0, FATAL/native/`am_anr` 0, monkey 1.500 × 3 seed | `device-gate-65c9b673-20260925-174335.log` |
| a1 cài mới, ép EEA, Consent | 0 mạng khởi tạo trước bấm; GRANTED; `networks=admob,meta,max`; FCM bật; `prefs warmup waited=0 ms` | `redmi-final-a1-consent.log` |
| d xoay khi Full2 hiện + 2 lượt | `host destroyed while showing` 1, `skip` 1; **không Full2 nào hiện lại trên host mới** (#6); 2 lượt sau Inter → Full2 bình thường | `redmi-final-d-rotate-full2.log` |
| a2 `pm clear`, ép EEA, Do not consent | DENIED; Consent Mode 4× false, `collection=disabled`; `meta an: not started`; `networks=admob,max`; `appsflyer not started`; FCM giữ, 0 token | `redmi-final-a2-deny.log` |
| Cold start với DENIED đã lưu, ép EEA | `consent tier restored=DENIED`; Meta AN không khởi tạo, AppsFlyer không start, FCM giữ; UMP xác nhận lại DENIED; onCreate main-thread 14 ms | `redmi-final-g-cold-denied-eea.log` |

Mọi log trên: FATAL app 0, `WindowLeaked` 0, `am_anr` 0, `BadToken` 0.

Ghi chú:
- Cold start **không** ép EEA sau khi từ chối (`redmi-final-g-cold-denied.log`): UMP trả `NOT_REQUIRED` theo vị trí thật của
  máy (Việt Nam) nên tầng chuyển sang GRANTED và Meta AN khởi động muộn. Đúng luật ngoài GDPR, là hiện tượng của cách thử.
- Kịch bản h (đóng + xoay) và c (rewarded → Full2) **không** chạy trên bản cuối: mẫu quảng cáo test đổi bố cục làm cú bấm
  "Close" theo toạ độ rơi vào quảng cáo và mở trang nhà quảng cáo trong Chrome trên máy (khoảng 3 tab). Hai kịch bản này
  PASS trên `b9ec778` và có test đơn vị (`Full2HostDestroyTest`, `Full2ChainDismissTest`).
- Demo không khai `facebook_app_id` nên đường Meta app events (`MetaSdkStarter`, cờ facebook-core) không kiểm được trên demo.
- Chưa kiểm trên máy: interstitial sau 61 phút (cần chờ thật, đổi giờ máy làm hỏng emulator), `ConsentManager.reset()` →
  `appsflyer stopped … (tier=UNKNOWN)`, `session_start` mang id app ở lần mở thứ hai (demo không truyền playerId).

**Sau Task I (sửa follow-up, `9a1e351..b811745`) trên Galaxy A25 `R5CX10P9EKZ` (API 36), 2026-09-25 22:38–23:19.**
Máy này trước đó không phải test device của GMA: log cũ `task7r-phone-rotate-full2.log` có gợi ý
`setTestDeviceIds("0E96196B…")`. Id này đã được thêm vào `admob_test_device` ở commit `e453148`. Từ đó log in
`This request is sent from a test device` và quảng cáo hiện nhãn "Quảng cáo thử nghiệm". Mỗi cú bấm đóng đều được đặt theo
ảnh chụp màn hình hoặc bounds của `id/ad_close` lấy bằng `uiautomator dump`, không bấm theo toạ độ đoán.

| Cổng / kịch bản | Kết quả | Log `DOC/testing/` |
|---|---|---|
| `release_gate.sh` (APK `e453148`) | PASS: 8 ad unit, 257/261 = 98 %, zipalign OK | `release-gate-build-20260925-223833.log` |
| `device_gate.sh` (APK `e453148`) | PASS: StrictMode by sdk 0, R8 0, FATAL/native/`am_anr` 0, monkey 1.500 × 3 seed | `device-gate-R5CX10P9EKZ-20260925-224126.log` |
| `release_gate.sh` (APK `06b1ee6`, sau đợt sửa review) | PASS: 8 ad unit, 260/264 = 98 %, zipalign OK | `release-gate-build-20260925-231102.log` |
| `device_gate.sh` (APK `06b1ee6`) | PASS: StrictMode by sdk 0, R8 0, FATAL/native/`am_anr` 0, monkey 1.500 × 3 seed | `device-gate-R5CX10P9EKZ-20260925-231345.log` |
| h, không xoay: Interstitial → đóng → Full2 → đóng | Trên cả hai APK: `dismissed pl=full_default` 1, Full2 hiện 1, `ondissmiss` Full2 1 | `samsung-I-h-inter-full2-norotate.log`, `samsung-Ifix-h-inter-full2-norotate.log` |
| c: Rewarded → Full2 | Trên cả hai APK: `REWARDED` 1, `dismissed pl=gift_default` 1, Full2 hiện 1 (sau dismiss 79 ms) và đóng 1 | `samsung-I-c-reward-full2.log`, `samsung-Ifix-c-reward-full2.log` |

Mọi log trên: FATAL app 0, `WindowLeaked` 0, `am_anr` 0, `BadToken` 0.

Ghi chú:
- **Chưa chạy bước xoay của kịch bản h** (đóng Full2 và xoay màn hình cùng lúc). Bước này phải đổi `accelerometer_rotation`
  và `user_rotation` trong cài đặt của máy, nhưng lệnh đó bị chặn vì quy tắc "không đổi cài đặt máy". Cài đặt xoay vẫn là
  `accelerometer_rotation=1`, `user_rotation=0` như trước khi chạy. Việc xoay khi Full2 đang hiện đã PASS trên Redmi (kịch
  bản d ở trên) và có test đơn vị.
- Commit `b811745` chỉ thêm comment, KDoc và một test, không đổi mã chạy, nên không chạy lại trên máy sau commit này.
- Demo đã được gỡ khỏi máy sau khi chạy. Hộp thoại cập nhật phần mềm của Samsung (`com.wssyncmldm`) có sẵn trên máy từ
  trước; không ai chạm vào (monkey chỉ chạy trong gói demo).

**Kịch bản xoay trên Galaxy A25 (chủ dự án cho phép đổi cài đặt xoay, 2026-09-25 23:44–23:49, APK release `06b1ee6`).**
Cài đặt xoay đọc được trước khi chạy là `accelerometer_rotation=0`, `user_rotation=0` (lúc 22:4x là `1/0`); sau khi chạy đã
trả về `0/0`. Mỗi cú bấm đóng đặt theo ảnh chụp; ở vòng 2 nút "Đóng" của creative nằm ở y≈2176, nếu bấm theo toạ độ vòng 1
(y=2040) đã rơi vào nút "Open".

| Kịch bản | Kết quả | Log `DOC/testing/` |
|---|---|---|
| h: đóng Full2 cùng lúc xoay, 3 vòng | Interstitial `dismissed` 3, Full2 hiện 3 và `ondissmiss` 3; `clearActivity: released 2 idle show callbacks` sau mỗi lần xoay (Task I5); `dismiss already dispatched` 0 | `samsung-h-close-rotate.log` |
| d2 + d: xoay khi interstitial GMA đang hiện, rồi xoay 2 lần khi Full2 hiện | Interstitial giữ callback qua xoay, `dismissed` đúng 1; Full2 `host destroyed while showing` 1, `host destroyed, skip` 1, không hiện lại trên host mới | `samsung-d-rotate-inter-full2.log` |

Mọi log trên: FATAL app 0, `WindowLeaked` 0, `am_anr` 0, `BadToken` 0, không mở Chrome.

**Sau Task J–M và đợt sửa cuối (2026-09-26), emulator Pixel_8_Pro API 37 riêng (`-read-only`, cổng 5570):**

| Cổng / kịch bản | Kết quả | Log `DOC/testing/` |
|---|---|---|
| `release_gate.sh` + `device_gate.sh` Galaxy A25, APK `e692c2a` (Task J) | PASS: 263/267 = 98 %; StrictMode by sdk 0, R8 0, FATAL/native/`am_anr` 0 | `release-gate-build-20260926-001142.log`, `device-gate-R5CX10P9EKZ-20260926-001439.log` |
| `release_gate.sh` + `device_gate.sh` emulator, APK `61763e2`, `603ca96`, `444bc90` | PASS mỗi lần: 98 %; StrictMode 0, R8 0, FATAL/native/`am_anr` 0, monkey 3.000 × 3 seed | `release-gate-build-20260926-070932.log`, `device-gate-emulator-5570-20260926-071224.log` (bản cuối) |
| Interstitial giữ quá hạn 60 phút | Lần bấm show sau 5 giờ 37 phút: `ad_expired after 337 min, dropped; next load fetches a new one`, nạp lại rồi hiện bình thường | `emu5570-expiry-337min.log` |
| G9, bản debug `e692c2a`, `am dumpheap -g` rồi `dumpsys meminfo` | 1 Activity lúc mở → 4 sau 2 vòng Interstitial → Full2 có xoay → 5 sau 5 lần xoay không hiện quảng cáo → 6 sau thêm 1 vòng. ✗ | `samsung-d-meminfo.txt` (lần đo không GC trên Galaxy A25), phân tích heap `.superpowers/sdd/g9-heap-analysis.md` |

Ghi chú G9: heap dump cho thấy cả 3 Activity cũ (lần đo 4) do Google Mobile Ads giữ — WebView của GMA qua
`WindowManagerImpl.mContext`, registry OMID của GMA, và `InterstitialAd`/`AppOpenAd` được nạp bằng Activity. Không đường giữ
nào qua `com.app.plugin`; bỏ hết tham chiếu của SDK và demo thì 3 Activity vẫn bị giữ. Cách sửa gốc (nạp GMA bằng
`applicationContext`) đụng tới adapter mediation nên thành quyết định #18 trong DOC/65 mục 7. Bản release không cho
`dumpheap` (không debuggable) nên G9 đo trên bản debug.

Chưa kiểm trên máy (danh sách chuyển cho cổng thiết bị của chủ dự án): MAX app open `destroy()` trong `onAdHidden`; MAX rect
refresh trong lúc đã bị lấy đi hiển thị; `loadRect4Show` trong lúc rect đang hiện refresh (demo không cấu hình MAX cho các
placement này).

**G9 với cờ `gma_load_app_context` (Task N, 2026-09-26 07:43–07:50, emulator API 37 riêng, bản debug `a31271b`).** Cùng quy
trình cho hai bản: mở → Interstitial → Full2 đóng cùng lúc xoay → Interstitial → Full2 xoay 2 lần khi hiện → xoay thêm 5 lần →
`am dumpheap -g` hai lần → `dumpsys meminfo`. Bản cờ bật dựng từ một worktree tạm (ép cờ = true, không commit, đã xoá), vì
Remote Config của demo ghi đè giá trị ghi bằng `run-as` sau vài giây.

| Bản | Activity sau GC (lúc mở → cuối) | Nạp GMA | Log `DOC/testing/` |
|---|---|---|---|
| Cờ tắt (hành vi mặc định) | 1 → **4** ✗ | 6 `tryLoad ok`, context = Activity | `g9-flag-off-emu.log` |
| Cờ bật | 1 → **1** ✓ | 6 `tryLoad ok`, 6 `ads gma load context=application` | `g9-flag-on-emu.log` |

Cả hai: `host destroyed while showing` 1, Full2 hiện 2, FATAL/`WindowLeaked`/`BadToken` 0. Chưa đo: fill/eCPM thật và adapter
mediation Meta (demo không có) — quyết định bật thuộc DOC/65 §7 #18.
