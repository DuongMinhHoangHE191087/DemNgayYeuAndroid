# 66. Đề xuất chốt trước khi tag 1.4.0

**Trạng thái: chủ dự án chấp nhận toàn bộ ngày 2026-09-25** (câu "tiếp tục thực hiện"); thực hiện ở các commit
`95d8568..c4749ca` (Task A–G, xem DOC/65 §7 cho từng quyết định kèm SHA).

Ngày 2026-09-25. Nhánh `feat/1.4.0` tại `6e6912a` (mã SDK không đổi so với `b9ec778`; commit cuối chỉ thêm id test
device của Redmi vào demo). Ba báo cáo nghiên cứu chi tiết (bằng chứng file:dòng, URL nguồn) nằm ở
`.superpowers/sdd/research-decisions.md` (15 quyết định, 53 nguồn), `research-startup-telemetry.md` (G2, `user_id`,
bisect, lỗi nhỏ, trailer), `research-g8.md` (tách `AdsHelper`, `internal`). Tài liệu này chỉ tóm tắt và xếp thành các
mục để chủ dự án trả lời "gật" hoặc "đổi".

Cách trả lời nhanh: mẫu ở mục 7.

---

## 1. Bằng chứng mới trên máy thật (Redmi 22021211RC, API 34, HyperOS)

Máy được phép dùng từ hôm nay. Bản cài: APK release của cây `b9ec778` (+ id test device).

| Cổng / kịch bản | Kết quả | Log `DOC/testing/` |
|---|---|---|
| `device_gate.sh` (cold start, 4 chuỗi R8, 3 seed monkey × 1.500) | FATAL 0, native 0, `am_anr` 0, chuỗi R8 0/0. **G2 FAIL**: StrictMode 13 vi phạm, 11 do SDK = 8 nạp prefs đầu + **3 "other"** (mục 2, D1) | `device-gate-65c9b673-20260925-092155.log` + 3 monkey |
| a1 cài mới, EEA, Consent | 0 mạng khởi tạo trước bấm; dismiss → `GRANTED`, Consent Mode 4× true, `collection=app-owned`, FCM auto-init bật, `starting` +79 ms, `sdk_health sdk_version=1.4.0` | `redmi14-a1-consent.log` |
| a2 `pm clear`, EEA, Do not consent | `DENIED`, Consent Mode 4× false, `collection=disabled`, FCM giữ, 0 token; mạng vẫn start (`admob,meta,max`) | `redmi14-a2-deny.log` |
| b Inter → Full2 | 1 `ondissmiss` của inter, Full2 hiện 68 ms sau khi xếp hàng | `redmi14-b-inter-full2.log` |
| c Rewarded → Full2 | `REWARDED` 1 trước dismiss; Full2 73 ms | `redmi14-c-reward-full2.log` |
| d xoay 4 lần khi Full2 hiện + 2 lượt | `host destroyed while showing` 1, `skip` 1, 2 lượt sau hiện ở cú bấm đầu, 3 dismiss cho 3 inter | `redmi14-d-rotate-full2.log` |
| h đóng + xoay cùng một lệnh, 3 lượt | 3 lượt, mỗi lượt đúng 1 dismiss, `dismiss already dispatched` 0 | `redmi14-h-close-rotate.log` |
| g cold start có consent | `TotalTime` 321 ms, `AppPluginBase.onCreate` main-thread **21 ms**, `consent on record` | `redmi14-g-coldstart.log` |

Mọi log: FATAL app 0, `WindowLeaked` 0, `BadToken` 0, `isAdsFullShowing` 0.

Ba điều cần bạn biết về máy này:
1. Redmi không phải test device tự động của GMA/UMP (điện thoại thật). Trong ~7 phút đầu (09:24–09:31), demo chưa có id
   băm của máy nên `force_eea` không tác dụng và GMA in gợi ý `setTestDeviceIds`: **có ít nhất một request quảng cáo
   thật** tới ad unit `ca-app-pub-2290293694594597/2559364435` (đến từ Remote Config `mem_cf_ads_placement`, không có
   trong repo). Interstitial nạp xong nhưng không hiện, nên không có impression; log của lượt đó đã bị ghi đè. Sau
   khi thêm id (`6e6912a`) mọi quảng cáo đều là "Test Ad".
2. Tôi đã bật/tắt xoay bằng `settings put system accelerometer_rotation 0` và `user_rotation`. Không ghi lại giá trị
   trước đó; hiện để `accelerometer_rotation=0` (tắt tự xoay). Bạn kiểm lại nút tự xoay của máy.
3. Demo đã gỡ khỏi máy. Kịch bản i (heap), k (kill switch), l (AppsFlyer DENIED) cần bản debug, chưa chạy trên Redmi;
   đã có trên emulator API 37 (Task 17).

---

## 2. Lỗi phải sửa trước tag (không cần quyết định sản phẩm, chỉ cần bạn gật)

| # | Lỗi | Bằng chứng | Sửa đề xuất | Cỡ |
|---|---|---|---|---|
| D1 | `FirebaseMessaging.setAutoInitEnabled(true)` gọi trên main trong `FcmConsent.Follower.follow` → Firebase tạo `Store` (`getNoBackupFilesDir`, `File.exists`, `createNewFile`) trên main: 3 vi phạm StrictMode, cổng G2 FAIL trên máy thật. Emulator không lộ vì thư mục đã có sẵn | `device-gate-65c9b673-…:232-298`, `FcmConsent.kt:55-73` | Chuyển `setAutoInitEnabled` (cả nhánh hold lúc init và nhánh follow) sang executor nền của SDK; token vẫn lấy sau. Test JVM: luồng gọi ≠ main. Chạy lại cổng trên Redmi | nhỏ, 1 commit |
| D2 | Replay decision log có thể mang `user_id=""`: `LogEventServer.init` (replay trên luồng IO) chạy trước `LogEventServerHelper.init` (gán `userId`) | `AppPluginBase.kt:141-142` | Đảo hai dòng; test thứ tự | rất nhỏ |
| D3 | Nhãn `not_plm_cf_<tên lớp>` bị R8 của app làm méo (`not_plm_cf_z71`) | `AdsBase.kt:180/196`, mapping demo | Dùng `eventTag` chữ như Task 14b (`not_plm_cf_mob_full_100`); mở rộng `EventNameContractTest` | nhỏ |
| D4 | 8 event `fir_rm_fet_{1,2,3,4,5,6,8,9}s` phát ngoài `EventNames` | `FirHelperConfig.kt:66-84` | Đăng ký làm hằng trong `EventNames`, giữ nguyên tên (đổi tên sẽ gãy dashboard) | nhỏ |
| D5 | Interstitial, rewarded, native full **không bao giờ hết hạn** (`loadedAdMaxAgeMs = null`); GMA ghi quảng cáo hết hạn sau 1 giờ | `AdsPlBase.kt:42`, developers.google.com/admob/android/interstitial | Đặt 60 phút cho interstitial/rewarded/native full (app open đã 4 giờ), lý do `AD_EXPIRED` có sẵn; khoá Remote Config trong `ads_policy_android` nếu muốn | vừa, có test |
| D6 | Kịch bản e "không thấy refresh banner trong 65 s" | `AdsHelper.kt:1006-1011`, log 1.3.1 | Không phải lỗi: chu kỳ 60 s, mỗi lần xoay `hideBanner` huỷ timer, banner chưa bao giờ ở trên màn 60 s liền. Đóng mục | 0 |
| D7 | M4: `api_dump --check` FAIL ở 4 commit `a60a696`, `acca98c`, `ef2b918`, `6e2745c` (dump chỉ được cập nhật ở `a7d17e4`) | chạy `--check` trên 13 commit trong worktree tạm | Không rebase. Ghi trong PR "4 commit không bisect được, `git bisect skip`"; từ nay dump đi cùng commit đổi API | 0 |
| D8 | Trailer: 64 commit Opus 5.5, 11 Fable 5.1 (9 commit đầu đã ở `origin/release/1.3.0`) | `git log 6075f98..b9ec778` | Giữ; đồng nhất phải rebase 66 commit và đổi 54 SHA đang được trích trong DOC/64, 65, CHANGELOG | 0 |

---

## 3. Việc kỹ thuật tôi tự làm được nếu bạn gật (mỗi việc một task, test đỏ trước, review riêng)

| # | Việc | Đề xuất | Công | Kết quả |
|---|---|---|---|---|
| T1 | **G2 = 0 thật (Task 15b)** thay vì ngưỡng #9 | Ấm hoá 4 file prefs (`XgameHCM-BaseAndroid`, `App_prefer_file`, `<pkg>_preferences`, UMP `__GOOGLE_FUNDING_CHOICE_SDK_INTERNAL__`) trên luồng nền từ `attachBaseContext`, main chờ latch ≤ 250 ms trước `AppPluginBase.onCreate:48`; log `prefs warmup waited=N ms`. Không đổi thứ tự khởi tạo, không mở race consent, không đổi API public. Sau đó cổng đòi `by sdk = 0`, bỏ ngoại lệ #9 | 0,5–1 ngày + cổng 3 API | #9 hết phải chốt |
| T2 | **G8 `internal`** 16 % → 49,7 % | Đổi 66 lớp chắc chắn (49 adapter mạng + 17 lớp khác; 0 tham chiếu ở Speaker Clean, Invoice, demo; dump javap không đổi). Thêm 20 lớp nữa lên 60 % nếu bạn xác nhận danh sách trong `research-g8.md` mục 3.2 | 3–4 giờ | G8 dòng `internal` đạt |
| T3 | **G8 `AdsHelper` ≤ 1.500 dòng** | **Hoãn sang 1.5.0**: không có cách tách rẻ (phải tách cả app open + rewarded, vùng dính `flagAdsFull*` và chuỗi Full2, nơi lỗi 1.3.x chỉ lộ trên máy); dump javap chắc chắn đổi (30 dòng `access$`); 22–25 giờ agent + đo lại toàn bộ máy. Kế hoạch 12 file đã viết sẵn ở `research-g8.md` mục 2. Cần bạn hạ ngưỡng ở `DOC/65:276` từ mức 1.4 xuống mức 1.5 | 0 cho 1.4.0 | tag được |

---

## 4. 15 quyết định — hiện trạng, đề xuất, hệ quả

Ký hiệu bản: **1.4.0** = làm trước tag; **1.5.0** = lộ trình; **doc** = chỉ sửa tài liệu.

| # | Chủ đề | Mã hiện tại | Đề xuất | Lý do chính (nguồn trong `research-decisions.md`) | Hệ quả nếu theo đề xuất | Bản |
|---|---|---|---|---|---|---|
| 1 | Khoảng cách interstitial | 30 s (`fullDeltatime` cũ), doc 62 = 240 s chỉ qua Remote Config | **Giữ 30 s**; mỗi app A/B 30/90/240 s bằng `ads_policy_android` ≥ 2 tuần (ARPDAU, guardrail D1/D7) rồi chốt mặc định ở 1.5.0 | Chính sách Google đếm theo thao tác, không theo giây; không có benchmark công khai giây ↔ eCPM; 240 s là số nội bộ LAM | Không đổi doanh thu hai app khi nâng | 1.4.0 giữ |
| 2 | `screensBeforeInterstitial` | 0 (tắt) | **Giữ 0**; app gọi `recordScreenChange()` thì đặt ≥ 2 (đúng luật "2 thao tác"), A/B 2 với 4 | Google: interstitial ở điểm chuyển tự nhiên, không sau mỗi thao tác | 0 | 1.4.0 |
| 3 | Hoãn `onAdDismissed` cha tới khi Full2 xong | Không hoãn | **Không hoãn**; doc 62 M ghi app dùng `Full2TransitionListener(false)` làm tín hiệu "xong cả chuỗi" | Đổi hợp đồng callback của mọi app, không có lợi ích đo được | 0 | doc |
| 4 | Backport sửa Full2 sang fork Speaker Clean | Chưa | **Bạn xem Crashlytics trước**: có `BadTokenException`/`WindowLeaked` thì backport bản vá tối thiểu Task 4 (~60–100 dòng, nửa ngày + release). Fork đang có đúng mã lỗi (`AdsMobPlNtFull.kt:496` dùng lại dialog không kiểm Activity, `show()` không try/catch); targetSdk 36 nên khoá dọc bị bỏ trên tablet Android 16, đổi ngôn ngữ/dark mode cũng tái tạo Activity | Mất nửa ngày nếu làm | bạn quyết |
| 5 | Đổi tên 2 event chẩn đoán + `ads_pl_*` | Đã đổi | **Giữ**; **không bao giờ** đổi 5 event `AdLTV_OneDay_Top*Percent` (Google Ads nhập conversion theo tên; tROAS cần lịch sử 10 conversion/ngày) — đóng băng làm hợp đồng trong `EventNameContractTest` | camelCase hợp lệ với Firebase, snake_case chỉ là quy ước của ta | Dashboard gộp tên cũ/mới (đã ghi CHANGELOG) | 1.4.0 |
| 6 | Full2 sau khi ad cha bị hệ thống đóng (xoay) | Giữ: Full2 hiện trên màn tái tạo | **Bỏ**: khi cha đóng vì host bị huỷ, không hiện Full2, gọi `finishFull2Transition()`, thả `cbShow` | Play cấm toàn màn hình hiện "bất ngờ"; `pendingFull2` đang giữ callback của app → giữ Activity cũ (G9) | Mất 1 Full2 ở ca xoay hiếm; sửa được G9 một phần | 1.4.x |
| 7 | `allowsProfiling`/`allowsPersonalization` khi `UNKNOWN` | `true` | **`false`**; chỉ cho khi đọc được rõ `IABTCF_gdprApplies = 0`; `DENIED` luôn thắng | Người EEA chưa trả lời form không được tích luỹ hồ sơ; người ngoài EEA gần như không mất gì vì UMP trả `NOT_REQUIRED` → `GRANTED` trong vài trăm ms lần mở đầu và tầng được lưu (tiền đề #7 trong DOC/65 nói quá) | Brain/CRM/offerwall im trong vài trăm ms lần mở đầu | 1.4.0 |
| 8+11 | Push gắn với consent quảng cáo | Gắn; rút consent chỉ tắt auto-init, token cũ vẫn nhận push | **1.4.0**: giữ gắn, **thêm `deleteToken()`** khi tầng chuyển từ cho phép sang không. **1.5.0**: tách push khỏi tầng quảng cáo, căn cứ = người dùng bật thông báo (POST_NOTIFICATIONS), có API cho app không chạy UMP | Push marketing có cần consent riêng hay không là điểm pháp lý chưa ngã ngũ; giữ bảo thủ cho 1.4.0 | Nhóm từ chối ở EEA không có push (như hiện nay) | 1.4.0 / 1.5.0 |
| 10 | Tắt hẳn thu thập Analytics khi `DENIED` | `true` | **Giữ `true`** | Ping không định danh ở EEA khi chưa consent là vùng chưa rõ; lưu lượng EEA của hai app có lẽ dưới ngưỡng mô hình hoá của Google (1.000 event/ngày, 7 ngày) nên Consent Mode nâng cao không mang lại gì; lấy AdMob làm nguồn sự thật cho doanh thu EEA | Mất `ad_impression`/`sdk_health` của nhóm DENIED | 1.4.0 |
| 12 | Start AppsFlyer cho `DENIED` | Start với consent toàn `false` | **Không start**; tầng chuyển sang `DENIED` sau khi đã start thì `stop(true)`, cấp lại thì `stop(false)` + start | Tài liệu AppsFlyer không nói cờ `false` ngăn thu thập; CNIL phạt Voodoo 3 M€ ca tương tự | Mất attribution nhóm DENIED (vốn không được phép) | 1.4.0 |
| 13 | `ANDROID_ID` làm `user_id` telemetry | Gửi ở mọi event, trước consent | **Thay bằng playerId của brain** (id app truyền qua `configure`, không thì `BrainStorage.playerId()` UUID); `PurchaseRouter` dùng cùng nguồn | Bỏ định danh phần cứng; **sửa luôn lỗi có sẵn**: backend `reconcile.py` giả định `user_id = playerId` nhưng SDK gửi ANDROID_ID → lệch gần như mọi hàng, cài lại bị đánh dấu "conflicting" | `user_id` gãy liên tục một lần khi nâng SDK ("Phải làm khi nâng cấp"); web shop cần id tài khoản để giữ đơn qua cài lại. **Không đủ để đạt G4**: `first_open`/`session_start` vẫn đi trước consent (chỉ khi app bật `DATABUCKETS_BASE_URL`) — cần quyết định riêng: giữ hàng đợi tới khi có tầng consent, hay chấp nhận telemetry bí danh | 1.4.0 |
| 14 | Opt-out bang Mỹ (GPP) | Chỉ đọc TCF; ngoài GDPR cấp hết trừ `DENIED` | **1.4.0**: bạn bật thông điệp US state trên AdMob console; CHANGELOG ghi rõ SDK chưa chuyển opt-out cho Meta/MAX/AppsFlyer/Firebase/CRM. **1.5.0**: đọc `IABGPP_*`, chuyển cho mọi consumer (`setDoNotSell`, Meta LDU, Consent Mode denied, AppsFlyer) | UMP chỉ ghi `IABGPP_*`; tầng vẫn `GRANTED` nên luật hiện tại **sai** với người Mỹ đã opt-out qua form UMP (chỉ quảng cáo Google tự tôn trọng). Khẳng định "trạng thái UMP vẫn `NOT_REQUIRED`" là suy luận, cần kiểm bằng debug geography US | Rủi ro pháp lý CCPA/CPRA còn tới 1.5.0 | 1.4.0 doc / 1.5.0 |
| 15 | `INTERRUPTIVE` giữ native collapsible | Giữ NtCl | **Thêm `FLAG_NATIVE_COLLAPSIBLE` vào `INTERRUPTIVE`** | NtCl là overlay che ~45 % màn, trong khi gói rẻ đã gỡ banner ít gây gián đoạn hơn | Người mua gói rẻ mất thêm NtCl | 1.4.0 |
| 9 | Ngưỡng G2 với lần nạp prefs đầu | Áp tạm: trừ prefs | **Làm T1** để hết phải chốt | — | — | 1.4.0 |

---

## 5. Phát hiện phụ cần quyết định riêng

| # | Vấn đề | Đề xuất |
|---|---|---|
| P1 | **Meta Audience Network ở EEA**: Meta không có trong GVL, dựa vào Additional Consent (`IABTCF_AddtlConsent`). SDK không đọc chuỗi này và khởi tạo Meta cả cho `DENIED` (a2 hôm nay: `networks=admob,meta,max`) | 1.4.0: không init Meta khi tầng `DENIED` và `gdprApplies = true`; 1.5.0: đọc Additional Consent |
| P2 | **Telemetry trước consent (G4)**: chỉ khi app bật `DATABUCKETS_BASE_URL`; demo không bật nên cổng G4 trên demo không thấy | Chọn: giữ hàng đợi tới khi có tầng (mất `first_open` của người thoát trước khi trả lời) hay chấp nhận bí danh; ghi vào chính sách |
| P3 | **`AdLTV_*` vs Google Ads**: bài AppsFlyer về chính sách consent EU trả 403, chưa kiểm được Google còn attribute khi `ad_user_data=0` không | Không chặn tag |
| P4 | Fork Invoice có kiểm Activity trước khi dùng lại dialog nhưng `show()` vẫn không try/catch | Cùng lúc với #4 |

---

## 6. Việc chỉ bạn làm được

1. Cho phép push nhánh (hoặc tự push) để CI Linux chạy lint + `api_dump --check` một lần.
2. Bật thông điệp US state + GDPR trên AdMob console cho các app (#14). Từ SDK 1.6.0 (Task 8a) SDK đọc `IABGPP_*` mà
   thông điệp US state ghi và chuyển opt-out cho Firebase Consent Mode, AppsFlyer, MAX và Meta (AN + facebook-core);
   chưa bật thông điệp thì không có `IABGPP_*` và 1.6.0 không áp gì (như 1.5.0). Trên console: AdMob → Privacy &
   messaging → US state regulations → tạo thông điệp, chọn app, publish; kiểm bằng debug geography US của UMP
   (`--ez force_us true` trên demo, Task 8-0/8b) rồi `IABGPP_GppSID` phải có `7` (hoặc id bang). Không bật thêm gì trên
   console AppsFlyer/AppLovin/Meta: SDK gọi API của từng mạng.
   App phải có mục **Privacy options** (gọi `ConsentManager.showPrivacyOptions` khi `ConsentManager.isPrivacyOptionsRequired`
   = `true`): UMP không hiện thông điệp US state lúc mở app, đó là đường duy nhất để người Mỹ chọn "Don't sell or share"
   (emulator API 37, 1.6.0 Task 8b).
3. Xem Crashlytics của Speaker Clean/Invoice cho `BadTokenException` (#4).
4. Nếu muốn đối chứng thêm trên Galaxy A25: cắm và mở khoá; Redmi đã đủ cho cổng và kịch bản a–h.

---

## 7. Mẫu trả lời

```
Mục 2 (D1–D8): làm hết / trừ …
Mục 3: T1 làm / không; T2 làm 66 lớp / thêm 20 lớp; T3 hoãn 1.5.0 và hạ ngưỡng DOC/65:276 / không
Mục 4: theo đề xuất hết / đổi: #… = …
Mục 5: P1 … ; P2 … 
```

Thứ tự tôi sẽ chạy nếu bạn gật toàn bộ: D1 → D2 → T1 (bỏ ngoại lệ #9) → #7, #12, #15, #8 `deleteToken`, P1, #13 →
D3, D4, D5 → #6 → T2 → #5 đóng băng `AdLTV_*` → docs (#3, #14, D7, D8, DOC/65 §3/§5/§7, CHANGELOG) → cổng 3 API +
Redmi → review toàn nhánh. Ước 3–4 ngày agent; không tag, không push.
