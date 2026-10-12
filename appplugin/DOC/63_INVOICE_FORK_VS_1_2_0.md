# 63 — Invoice Maker: fork `:adsplugin` so với appplugin 1.2.0, và việc cần làm để xoá fork

**Ngày viết:** 2026-09-23
**Bối cảnh:** doc 61 mục E, bước 2. LAM 2026 đã dừng, nên Invoice Maker là app đầu tiên chuyển sang
AAR: nó lệch ít hơn Speaker Clean (25 commit chạm `:adsplugin` so với 78, tính cả initial commit) và có
eCPM cao nhất portfolio.
**Phương pháp [M]:** diff từng file giữa `InvoiceMakerAI/adsplugin` (HEAD `6a4f901`) và repo SDK
(`main`, tức 1.2.0), đối chiếu từng khác biệt với commit đã tạo ra nó. Chỉ đọc repo Invoice.
**Nhãn:** [M] đo trực tiếp, [D] đề xuất.
**Cập nhật 2026-09-23:** mục B và C đã làm trong 1.3.0 (publish theo tag `v1.3.0`), chưa áp vào app
nào. Chủ dự án chốt: làm tốt ở SDK trước, khi nào dùng cho app thật sẽ báo. Mục C.5 đổi so
với đề xuất ban đầu (xem dưới). Mục F sửa theo 1.3.0.

---

## 0. Tóm tắt

- Khoảng 3.500 dòng khác, nhưng phần lớn là Invoice **đứng sau** SDK: cùng một đoạn code, SDK có bản
  mới hơn (chuyển cảnh full2, consent, waterfall, native reuse). Invoice nhận các bản đó khi chuyển (A).
- **4 bản sửa lỗi** của Invoice chưa có trong SDK: đưa về ở 1.3.0 (B).
- **3 thứ Invoice cần** mà SDK chưa có hoặc đang mặc định khác: thêm ở 1.3.0 để chuyển không đổi
  hành vi (C).
- 5 thứ không đưa về (D), 2 thứ chờ quyết định (E). Việc phía app khi chuyển ở mục F.

---

## A. Invoice nhận ngay khi chuyển (SDK đi trước) [M]

| Chủ đề | Fork Invoice | SDK 1.2.0 |
|---|---|---|
| Chuyển cảnh interstitial/rewarded → full2 | 7/15 lớp bảo vệ | chuẩn Speaker Clean (doc 62 mục 3.M) |
| Full2 sau app open | đã bỏ (`6a4f901`) | không nối |
| Consent | timeout 5 s vô điều kiện, thử hiện lại form (BUG-07) | `ConsentGate` + `ConsentFormWatchdog`: giữ cả hai bài học |
| `AdRequestFactory` | 4/8 định dạng; chưa có chuỗi TCF thì coi là được cá nhân hoá | 8/8; ở EEA chưa có chuỗi thì gắn `npa=1` |
| Trao thưởng rewarded | cờ trên đối tượng waterfall dùng chung | `RewardDeliveryGate` mỗi lần show |
| Waterfall (`AdsEcmps.listFromData`) | bản cũ | bản viết lại; config mới đến khi đang load thì chờ (`pendingConfig`) |
| Dùng lại native đã tải | không | có, kèm hạn 1 giờ |
| Kích thước banner | cố định `AdSize.BANNER` | adaptive theo `optimizationPolicy.adaptiveBanner` |
| Log FCM token | ghi nguyên văn | ẩn (`SensitiveLogSanitizer`) |
| Nhãn "Sponsored" của Meta | chuỗi cứng tiếng Anh | string resource, có bản dịch |
| Test device ghi đè cấu hình AdMob (B.1) | đã sửa (`c9d46cc`) | đã sửa |
| Gỡ quảng cáo theo định dạng | có | có, cùng API `setRemoveAdsMask` |
| Cooldown N màn hình sau quảng cáo | cứng 2 | cấu hình được, **mặc định tắt** — xem F.3 |
| Bộ đếm "flick" (`countNtFullFlick`, `setCountNtFullAfterlic`…) | còn trong `AdsMobMy`, `AdsFbMy`, `*PlNtFull` | đã gỡ (`a7c1223`) |

---

## B. Bản sửa lỗi của Invoice, đưa về SDK ở 1.3.0 [M → D]

1. **Banner chết hẳn sau một vòng load lỗi** (Invoice BUG-07, `b17b3b2`). Khi cả waterfall trả lỗi,
   SDK đóng dấu `bnTimeShow` rồi dừng: không thử lại, và dấu thời gian vừa đóng còn chặn luôn lần
   `showBanner()` kế tiếp. Thường gặp nhất khi request đầu bắn ra trước lúc `MobileAds.initialize`
   xong. Invoice thử lại tối đa 3 lần, cách 5 s / 15 s / 45 s.
2. **Gỡ kẹt `isLoading` của banner không an toàn.** SDK hạ `isLoading` ở lần gọi `loadBanner()` thứ hai
   khi lần đầu còn đang chạy, nên có thể có hai vòng waterfall song song cho cùng một chỗ đặt. Invoice
   thay bằng watchdog theo "thế hệ" load: sau 30 s mà đúng vòng đó chưa có callback nào thì mới hạ cờ
   và xếp lịch thử lại.
3. **`AdsConfigOb.loadFromMem()`/`saveCurr()` dùng `mContext!!`** → crash nếu được gọi trước khi có
   context. Invoice dùng `mContext ?: return`.
4. **`AdSettings.setIntegrationErrorMode(INTEGRATION_ERROR_CRASH_DEBUG_MODE)`** chạy ở mọi bản build
   trong SDK; Invoice chỉ bật khi `flagTest`.

---

## C. SDK cần có để Invoice chuyển mà không đổi hành vi (1.3.0) [D]

5. **Mọi mạng quảng cáo khởi tạo sau consent — đã làm, và là mặc định, không phải cờ.** Đề xuất ban
   đầu là cờ `deferAdSdkInitUntilConsent` mặc định tắt, để Speaker Clean giữ khởi tạo sớm. Bỏ đề xuất
   đó, vì trang Get Started của Google Mobile Ads Android ghi: "If you need to obtain consent from
   users in the European Economic Area (EEA), the United Kingdom (UK), or Switzerland, set any
   request-specific flags … or otherwise take action before loading ads, ensure you do so before
   initializing Google Mobile Ads SDK (Legacy)." và "Ads may be preloaded by Google Mobile Ads SDK
   (Legacy) or mediation partner SDKs upon initialization." `MobileAds.initialize` khởi tạo luôn khoảng 9 adapter
   mediation, còn Meta Audience Network và AppLovin MAX được khởi tạo trực tiếp. Trước khi UMP trả
   lời thì chưa biết người dùng ở vùng nào, nên không có ngoại lệ "ngoài EEA".

   Trong 1.3.0: `AdsMobMy.startSdkInit(context)` (cùng chữ ký với hai fork) chỉ *yêu cầu* khởi tạo;
   `AdSdkStartGate` khởi tạo cả ba mạng khi UMP `canRequestAds()` là `true`. Nhờ vậy lời gọi sớm của
   Speaker Clean (ngay ở `onCreate` của Activity, trước `gatherConsent`) cũng hợp lệ: nó chờ. Mối lo
   độ trễ của Speaker chỉ còn ở lần mở đầu tiên, vì UMP lưu câu trả lời của phiên trước. Load của
   Meta và MAX đến trước lúc đó được giữ trong hàng chờ, như AdMob.
6. **`AppPluginBase.initMetaSdkAfterConsent()` — đã làm.** Cùng chữ ký với hai fork, đi qua cùng cổng
   consent. Khác đề xuất: `facebook-core` chỉ ở `compileOnly`, để app không đo chiến dịch Meta không
   phải mang `FacebookInitProvider`. Manifest của SDK đặt `AutoInitEnabled`,
   `AutoLogAppEventsEnabled`, `AdvertiserIDCollectionEnabled` về `false`. Speaker Clean gọi
   `AppEventsLogger` trực tiếp trong `BillingManager`, hiện nhờ `api(facebook-android-sdk)` của fork;
   khi chuyển, app đó phải tự khai dependency này.
7. **Log DataBucket mặc định tắt.** Cả Speaker Clean và Invoice đặt `Constants.DataBucket.BASE_URL = ""`,
   tức không gửi log sự kiện lên `ingest.databuckets.com`; SDK mặc định gửi, với API key rỗng. Đổi mặc
   định về rỗng; app nào cần thì bật bằng meta-data `DATABUCKETS_BASE_URL` / `DATABUCKETS_API_KEY`
   (cơ chế đã có).

Cùng đợt: **SDK lên AGP 9 — đã làm** (AGP 9.2.1, Gradle 9.4.1, Kotlin tích hợp sẵn 2.2.10), để Invoice
và Speaker (AGP 9.2.1) dựng được SDK bằng composite build khi cần sửa SDK và app cùng lúc.

Thêm trong 1.3.0, ngoài danh sách trên: `admob_test_device`, `fb_test_device`, `max_test_device` nhận
nhiều id cách nhau bằng dấu phẩy (Invoice đã làm cho AdMob); refresh banner không còn dừng hẳn sau một
vòng refresh lỗi; watchdog banner tính theo từng bước và từng chỗ đặt, không theo cả vòng.

---

## D. Không đưa về [D]

| Thứ | Lý do |
|---|---|
| Firebase dự phòng bằng API key giả (`AppPluginManager`) | che lỗi thiếu `google-services.json`, và khởi tạo Firebase vào một project không tồn tại |
| Bộ đếm "flick" | SDK đã gỡ cơ chế này (`a7c1223`) |
| `Constants.App.PACKAGE = "com.speaker.clean.wave"` | chép nhầm từ Speaker Clean; không nơi nào trong SDK dùng |
| Bỏ khởi tạo Audience Network khi `facebook_app_id` là placeholder | Audience Network qua mediation không cần `facebook_app_id`, adapter Meta tự khởi tạo; điều kiện này có thể tắt nhầm ở app khác. Khởi tạo trên luồng nền: đo trước rồi mới đưa |
| `logAdRevFb` (ghi `ad_impression` cho Meta trực tiếp, `value` = 0) | tuỳ quyết định 2 của doc 62 (có giữ stack Meta trực tiếp không); `value` 0 làm lệch tổng impression mà không thêm doanh thu |

---

## E. Chờ quyết định

- `segment/*`, guardrail toàn cục và `recordAdImpressionForSegment` (doc 61 mục F.1).
- Stack Meta trực tiếp: step mặc định của Invoice để Meta làm tầng dự phòng (`#re:FB…`) cho banner,
  interstitial và rewarded (doc 62, quyết định 2).

---

## F. Việc phía app Invoice khi chuyển [D]

1. **Quyền đọc package:** PAT (classic) có `read:packages` trong `~/.gradle/gradle.properties`
   (`gpr.user`, `gpr.key`) của từng máy dev và secret của CI; khai repo
   `https://maven.pkg.github.com/dungduong2899/appplugin`.
2. Xoá module `:adsplugin`; khai `implementation("com.app.plugin:appplugin:1.3.0")`. Lời gọi
   `startSdkInit` và `initMetaSdkAfterConsent` trong `MainActivity` giữ nguyên.
3. **Giữ nguyên hành vi đang chạy:**
   - `AdsHelper.instance.setPostAdScreenCooldown(2)` và `recordScreenChange()` như hiện tại;
   - step mặc định có Meta dự phòng: đặt qua resource `ads_cf_bn_step`, `ads_cf_full_step`,
     `ads_cf_gift_step` (nếu giữ stack Meta trực tiếp);
   - **banner không refresh:** `refreshBn()` của fork return ngay từ initial commit (`863d8db`), nên
     giá trị mặc định 20 s của fork không bao giờ được dùng (bản trước của mục này ghi nhầm là "refresh
     20 s"). Giữ nguyên thì đặt refresh banner = 0 trong Remote Config. Bật refresh (SDK giới hạn
     30–150 s) là một quyết định doanh thu riêng, cần đo.
4. `InstallReferrerHelper.captureOnce(context)` trong `onDone` của consent. Fork đang gọi trong
   `Application.onCreate`, tức trước consent.
5. QA form EEA: `ConsentManager.debugForceEea = BuildConfig.DEBUG` (fork tự ép EEA khi `flagTest`).
6. Kiểm layout chỗ đặt banner với banner adaptive (có thể cao hơn 50 dp).
7. Xong khi: build release R8 chạy qua; thử trên máy thật consent EEA, rewarded, chuyển cảnh full2;
   fill rate và eCPM 7 ngày không giảm so với trước khi chuyển (doc 61 mục E).
