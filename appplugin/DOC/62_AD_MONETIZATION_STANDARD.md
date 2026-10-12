# 62 — Chuẩn quảng cáo thống nhất A–Z cho mọi app trong portfolio

**Ngày viết:** 2026-09-23
**Câu hỏi xuất phát:** eCPM của Invoice Maker đang cao nhất trong ba app. Đó là do dòng app, hay do cách
làm? Nếu có phần do cách làm thì chuẩn hoá phần đó cho mọi app, để không còn mỗi app một kiểu.
**Bối cảnh:** SDK nội bộ (doc 60, doc 61). Ba app: LAM 2026 ("Lịch Việt"), Speaker Clean ("Clear Wave"),
Invoice Maker ("Invoice Unlimited"). Từ 2026-09-23 LAM là dự án cũ, không làm tiếp; cột LAM giữ làm dữ kiện.
**Nhãn:**
- **[M]** đo trực tiếp trên 3 repo, chỉ đọc, ngày 2026-09-23;
- **[P]** tài liệu chính thức của Google/Firebase;
- **[S]** số liệu thứ cấp từ báo cáo ngành, chỉ dùng để thấy độ lớn, không dùng làm mục tiêu;
- **[D]** đề xuất.

**Giới hạn:** trong repo không có số eCPM thật của từng app. Phần 1 giải thích *cơ chế* và *độ lớn*,
chưa thay được một bảng AdMob tách theo app × định dạng × quốc gia (cách xuất bảng: mục 2.2).

---

## 0. Trả lời ngắn

Phần lớn chênh lệch **đến từ dòng app và thị trường, không chuyển sang app khác được**:
- Invoice nhắm US/EU với người dùng là freelancer/doanh nghiệp;
- LAM gần như toàn bộ là người dùng Việt Nam;
- Speaker Clean là app tiện ích phủ nhiều nước.

Theo số liệu ngành, eCPM interstitial Android ở Mỹ cao khoảng 8–9 lần Việt Nam [S].

Nhưng Invoice cũng có **bốn cách làm tốt, chuyển được**, và đó là lõi của chuẩn này:
1. Interstitial chỉ hiện ở điểm dừng tự nhiên sau khi xong việc.
2. Phiên đầu tiên không hiện interstitial.
3. Rewarded là van tự nguyện, có giá trị rõ và có trần mỗi ngày.
4. Tên placement theo khoảnh khắc nghiệp vụ, nhờ đó đo được từng vị trí.

**Chuỗi "full2"** (native toàn màn hình hiện ngay sau khi interstitial / rewarded đóng) **đã được Account
Manager của AdMob xác nhận là được dùng** (chủ dự án báo ngày 2026-09-23). Vì vậy full2 là một phần của
chuẩn. Cách chuyển cảnh lấy theo Speaker Clean — bản đầy đủ nhất trong ba app (mục 3.M).

**Không so eCPM gộp giữa các app.** eCPM gộp bị chi phối bởi quốc gia và tỉ lệ giữa các định dạng. Chuẩn
đo lường ở mục 2.

---

## 1. Vì sao Invoice có eCPM cao nhất — tách từng yếu tố

| Yếu tố | Invoice Maker | Speaker Clean | LAM 2026 | Chuyển sang app khác được không? |
|---|---|---|---|---|
| **Thị trường** [M] | US/EU: ASO cho EN, DE, FR, ES, IT, PL; UI 21 locale | Toàn cầu: 14 locale | Việt Nam: UI chỉ tiếng Việt | **Không** — đây là yếu tố lớn nhất |
| **Dòng app / người xem** [M] | Công cụ kinh doanh cho freelancer, SMB | Tiện ích thiết bị | Lịch âm, tâm linh | **Không** |
| **Cơ cấu định dạng** [M] | 1 banner (dashboard); interstitial ở 2 sự kiện; rewarded; app open khi quay lại; chuỗi native full | 1 banner, rewarded (4 chỗ gọi), interstitial, app open | 2 banner, native, interstitial, rewarded, app open | **Có một phần.** Ít banner, nhiều full-screen thì eCPM gộp cao hơn, nhưng doanh thu/người dùng không nhất thiết cao hơn |
| **Chỗ đặt interstitial** [M] | chỉ sau `invoice_saved`, `invoice_shared` — xong việc mới hiện | theo lượt load (`loadInterstitialForThisTurn`) | 4 lần chuyển màn + cách nhau ≥ 4 phút, cấm ở màn tâm linh | **Có** → mục 3.E |
| **Phiên đầu** [M] | không hiện interstitial (`session1_interstitial_grace_consumed`) | không có | không có | **Có** |
| **Rewarded** [M] | mở template premium, tối đa 5 lần/ngày | trao thưởng đúng một lần (`RewardDeliveryGate`) | có gọi | **Có** — gộp cả hai |
| **Tên placement** [M] | theo nghiệp vụ: `invoice_saved`, `dashboard_banner`, `gift_premium_template` | chỉ `*_default` | chỉ `*_default` (app open chỉ có `full_splash`) | **Có** — không có tên riêng thì không đo được từng vị trí |
| **Consent EEA** [M] | tự thử hiện lại form UMP khi hiện thất bại, chặn form hiện chồng | `ConsentGate` | tự dịch tier ở 3 chỗ | **Có** — tỉ lệ đồng ý quyết định có quảng cáo cá nhân hoá hay không |
| **Meta adapter** [M] | 6.21.0.1 (**lỗi render native**) | 6.21.0.4 | 6.21.0.1 (**lỗi render native**) | Sửa ngay (doc 61 bước 0). Invoice cao nhất dù đang dính lỗi này, nên cách làm của nó không phải nhờ Meta |
| **Chuỗi full2** [M] | bật, cả 5 placement `full_2_*`, có nối sau app open | bật sau interstitial và rewarded, đủ 15/15 lớp bảo vệ chuyển cảnh | theo SDK; **không có lớp che phía app** | **Có** — theo chuẩn Speaker Clean, mục 3.M |

Độ lớn tham khảo [S]:
- eCPM interstitial Android: Mỹ khoảng $12–14, Việt Nam khoảng $1,5 cuối 2025.
- Theo định dạng (toàn cầu, 2024): banner ~$2,8, interstitial ~$4,8, rewarded ~$10,5.
- Kế hoạch của chính Invoice (`docs/superpowers/plans/2026-08-20-…`) ước eCPM Mỹ: banner $0,5–1,5, interstitial $5–8, rewarded $10–20; EU thấp hơn 30–50%.

**Kết luận:**
- Không đặt mục tiêu "LAM phải có eCPM như Invoice".
- Mục tiêu đúng: **mọi app áp cùng một chuẩn, rồi so từng app với chính nó trên các chỉ số ở mục 2.**

---

## 2. Chuẩn đo lường — so sánh cho công bằng

### 2.1 Bộ chỉ số bắt buộc [D]

Luôn báo cáo **theo định dạng × nhóm quốc gia**, không bao giờ gộp:

| Chỉ số | Định nghĩa | Vì sao | Nguồn (1.7.0) |
|---|---|---|---|
| eCPM theo định dạng × quốc gia | doanh thu / impression × 1000, tách banner / interstitial / app open / rewarded / **rewarded_interstitial** (1.9.0) / native | eCPM gộp chỉ phản ánh cơ cấu định dạng | view SQL `v_ecpm_format_tier` (từ `ad_revenue` v2, tiền qua `fx_daily`); đối chiếu **observed eCPM** của báo cáo AdMob (`admob_report`, `backend/app/admob_import.py`) |
| Impression / DAU theo định dạng | số impression chia DAU | đo độ dày quảng cáo — đòn bẩy thật của doanh thu | view SQL `v_imp_per_dau_format` |
| **ARPDAU quảng cáo** theo quốc gia | doanh thu quảng cáo / DAU | chỉ số chính để so một app với chính nó | view SQL `v_arpdau_ads_country` |
| Fill rate, show rate | trả về quảng cáo / request; hiện được / sẵn sàng | tách lỗi kỹ thuật khỏi thiếu nhu cầu | view SQL `v_fill_rate` (chuỗi `ad_request` → `ad_fill`/`ad_no_fill`), `v_show_rate` (`ad_show_attempt(ready=1)` → `ad_show`); **match rate** riêng của AdMob: báo cáo mediation (`admob_report`) |
| **ARPDAU tổng (quảng cáo + IAP)** theo quốc gia (1.8.0) | (doanh thu quảng cáo + doanh thu IAP) / DAU; IAP = `iap_purchase.price_micros` (số thực thu) quy đổi USD **ở server** theo `fx_daily` của ngày giao dịch (DOC/46 B.3, D3); `phase = trial` tính 0; không có tỉ giá → `iap_unconverted`, không bỏ | app có subscription/IAP phải nhìn cả hai vế: chỉ số quảng cáo một mình thổi phồng hay hạ thấp hiệu quả của paywall | view SQL `v_arpdau_country` (khử trùng theo `iap_purchase:tok:<purchase_token>`, nên bản phát lại sau restore chỉ tính một lần); oracle `ad_metrics.py`. Renewal/hoàn tiền chỉ tới từ server (RTDN, DOC/46 C.3); doanh thu thật từ RTDN thật = H7b |
| **Phễu paywall** theo `paywall_session_id` (1.8.0) | hiện (`paywall_impression`) → chọn (`paywall_choice`) → mua (`iap_purchase` cùng `paywall_session_id`); `closes` = phiên có impression mà không có choice; `paywall_skip`/`paywall_holdout_skip` báo riêng theo `reason`; mọi số nhân `1/sample_rate` | tách "không thấy paywall" (cap, holdout, brain) khỏi "thấy mà không mua" | view SQL `v_paywall_funnel`; `metrics.paywall_funnel` (oracle). Impression ≥ choice ≥ purchase là điều kiện cổng |
| Tỉ lệ đồng ý consent (EEA) | người đồng ý / người thấy form | quyết định có quảng cáo cá nhân hoá hay không | **AdMob Privacy & messaging** (chủ dự án xuất từ console). Backend **không** có cột "tỉ lệ": theo Q2 không event nào của người `DENIED` tới server nên tỉ lệ đọc từ backend luôn ≈ 100 % (số sai trông như đúng). Backend chỉ có `v_consent_funnel_nondenied` (độ trễ form → chọn, tỉ lệ GRANTED/PARTIAL, GPP) |
| Retention D1/D7, uninstall | như thường | **guardrail**: tăng ARPDAU mà giảm D7 là thua | view SQL `v_retention_d1_d7_holdout` (holdout compare); uninstall: Play Console / Firebase (ngoài backend) |

Một định nghĩa, hai bản cài đặt khoá bằng test: view SQL (`backend/sql/views/`, bản chạy) và oracle Python
`backend/app/ad_metrics.py`; `backend/tests/test_views_parity.py` (integration) đòi Δ = 0. Sửa định nghĩa = sửa cả hai. Ngày = ngày UTC+7
của `client_ts_ms`; mọi số nhân `1/sample_rate`; tiền không có tỉ giá ghi `unconverted`, không bỏ. Báo cáo ngày: `tools/report_daily.sh --date D`
→ một file `<ngày>.md` trong `DOC/reports/` (9 bảng từ 1.8.0: thêm ARPDAU tổng và phễu paywall; 7 bảng ở 1.7.0). Doanh thu client so với báo cáo AdMob ở mức nhóm quốc gia (AdMob gán quốc gia theo IP, `country` của ta theo SIM/mạng) —
lệch nhỏ theo ngày là bình thường, không so từng quốc gia.

Nhóm quốc gia dùng chung cho mọi app:
- **T1:** US, CA, GB, AU, DE, FR, NL, Bắc Âu, CH;
- **T2:** phần còn lại của EU, JP, KR;
- **VN:** tách riêng;
- **Khác.** (Server tính tier từ `country` qua bảng `country_tier`, seed `backend/sql/seed/country_tier.csv`; `DEFAULT`/không có SIM = `OTHER`; client không gửi tier.)

#### Event v2 (hợp đồng tên và tham số)

Envelope thêm ở cấp cao nhất của body bởi `EventEnvelope.wrap` (giữ nguyên `sequenceId`, `eventName`, `eventData`, `ts`, `userProperty`):
`event_id` (UUID v4 sinh lúc dựng, giữ nguyên khi gửi lại), `schema_version` = `2`, `sdk_version`, `package_name` (backend **không** tin nó cho
`app_id`), `consent_tier`. Trạng thái trong cột cuối: **có** = phát từ SDK 1.7.0.

| Event | eventData (snake_case) | Phát ở | Trạng thái |
|---|---|---|---|
| `ad_request` | `ad_format`, `mediation` (`admob`\|`max`\|`meta_direct`), `placement`, `ad_unit`, `request_id`, `screen_name`, `is_sdk_init` | `AdsTelemetry.onAdLoadStart` | có |
| `ad_fill` | + `latency_ms` (≥ 0; `-1` = không có mốc bắt đầu), `ad_source` | `onAdLoaded` | có |
| `ad_no_fill` | + `latency_ms`, `error_code` (mã mạng dạng chuỗi hoặc `timeout`), `reason` (≤ 100 ký tự) | `onAdLoadFail`; watchdog slot (`load timeout`) | có |
| `ad_show_attempt` | `ad_format`, `placement`, `ready` (1/0), `screen_name` | đầu mọi hàm `show*` của controller (trước cổng) | có |
| `ad_show` | `ad_format`, `mediation`, `ad_source`, `placement`, `ad_unit`, `imp_id` | paid event, cùng `imp_id` với `ad_revenue` | có |
| `ad_show_fail` | `ad_format`, `placement`, `reason`, `is_action`, `screen_name` | `LogEventServer.logAdShowFailWithReason` + các đường `return false` im lặng | có |
| `ad_click` | `ad_format`, `placement`, `ad_unit` | `onAdClick` của controller | có |
| `ad_revenue` | `ad_format`, `mediation`, `ad_source`, `ad_unit`, `placement`, `value_micros` (Long), `currency`, `precision_type` (0–3; `-1` không có), `imp_id`, `screen_name` | `PaidEvent` | có |
| `consent_form_shown` | `gdpr_applies` (0/1/-1), `form_kind` (`initial`\|`privacy_options`) | `ConsentManager` | có |
| `consent_choice` | `tier`, `gdpr_applies`, `gpp_sections`, `latency_ms`, `form_kind` | callback form | có |
| `session_end` | `duration_ms`, `ads_shown_json`, `screens`, `fg_index` | `ProcessLifecycle` `ON_STOP` | có |

Quy ước: placement có tiền tố `full_` báo `ad_format=fullscreen`; tiền tố `rwi_` báo `ad_format=rewarded_interstitial` (1.9.0, `AdEventLog.formatOfPlacement`); tiền tố lạ = `unknown`. Rewarded interstitial dùng cùng các event v2 như rewarded (`ad_show_attempt` → `ad_show`/`ad_show_fail`, `ad_revenue`), chỉ khác `ad_format`; eCPM theo mạng và định dạng đọc ở view `v_ecpm_network_format` (1.9.0, DOC/46 §D.2). Tên cũ `ad_start_load`, `ad_load`, `ad_start_action`,
`ad_show_fail_reason` không còn phát từ SDK. Event tiền và phễu (`ad_revenue`, `iap_purchase`, `iap_result`, `paywall_*`, `first_open`, `session_start`, `session_end`,
`consent_*`) được ghi đĩa và replay đúng body cũ (`DurableEvents`); event chuỗi quảng cáo khác là best-effort.

### 2.2 Xuất bảng từ AdMob [D]

Report AdMob:
- dimensions: `App × Ad format × Country × Ad source`;
- metrics: `Estimated earnings, Impressions, Observed eCPM, Requests, Match rate` (API mediation report không có metric show rate);
- 28 ngày gần nhất.

Chạy cho cả ba app, gom theo nhóm quốc gia ở 2.1. Đây là bước đầu tiên để kiểm chứng phần 1 bằng số thật.

**Nhập vào backend (1.7.0):** `backend/app/admob_import.py` đọc luồng JSON của `accounts.mediationReport:generate` (dimension `APP, FORMAT, COUNTRY, AD_SOURCE, DATE`;
tiền tính bằng micros; dimension "Ad source" chỉ có ở báo cáo **mediation**, không có ở báo cáo network), thả file vào `DOC/reports/admob/inbox/`; CSV xuất từ giao diện
chỉ qua `HEADER_MAP`, cột lạ là lỗi (chờ một file xuất thật của chủ dự án). `match_summary` cho match rate và observed eCPM theo `format × tier` và lệch so với
`ad_revenue` của ta theo ngày × tier. Metric `Show rate` không có trong API; show rate của ta lấy từ event (`v_show_rate`).

### 2.3 Tracking doanh thu — cùng một định nghĩa [P → D]

- Firebase định nghĩa event `ad_impression` với tham số `ad_platform`, `ad_source`, `ad_format`,
  `ad_unit_name`, `value`, `currency`. Firebase ghi: *"automatically logs the ad_impression event
  whenever your users see an ad impression"* khi app đã **liên kết AdMob với Firebase** [P].
- SDK hiện **tự log `ad_impression` cho cả AdMob** (`FirHelper.logAdRevAdmob`) [M]. App nào đã liên kết
  thì doanh thu AdMob trên GA4 bị **đếm hai lần**.
- **Chuẩn:**
  - App đã liên kết thì SDK chỉ tự log cho nền tảng **không phải** AdMob.
  - Mọi event doanh thu gắn thêm `placement`, `app_id`, `sdk_version`.
  - Doanh thu nội bộ và AppsFlyer lấy từ **cùng** một callback paid event.
- **SDK 1.4.0 [M]:** integer resource `ads_admob_firebase_linked` = `1` bỏ `ad_impression` của AdMob phía
  SDK (`AdRevenueRouting`).
- **Caveat mediation (chưa kiểm):** cờ này bỏ **mọi** impression đi qua AdMob, kể cả của mạng bên thứ ba
  (Meta, Pangle, AppLovin…) chạy qua mediation AdMob. Chưa kiểm `ad_impression` Firebase tự ghi có mang đủ
  doanh thu của các impression mediation đó không. App có waterfall bên thứ ba qua mediation AdMob phải kiểm
  DebugView (một impression mediation → đúng một `ad_impression` có `value`) trước khi khai `1`. **[D]** hướng
  sửa nếu thiếu: chỉ bỏ khi `ad_source` là AdMob Network hoặc bidding của AdMob.

---

## 3. Chuẩn A–Z

### A. Một SDK, một bộ phiên bản

- Mọi app dùng SDK từ Maven private theo version (doc 61 mục C). Không app nào giữ bản chép.
- SDK công bố **một bộ dependency đã kiểm thử**: GMA + từng adapter + SDK mạng tương ứng. Hiện tại là
  GMA 25.4.0 + Meta 6.21.0.4 (theo ghi chú trong `build.gradle.kts` của SDK).
- App không tự nâng lẻ một adapter.
- **Điều kiện đi kèm bộ hiện tại [M]:** Meta adapter 6.21.0.4 phụ thuộc `kotlin-stdlib` 2.3.0, kéo cả
  app lên 2.3.0. Với Dagger/Hilt < 2.60 chạy bằng kapt, build vỡ ở `kaptDebugKotlin` với lỗi
  *"Provided Metadata instance has version 2.3.0, while maximum supported version is 2.2.0"*.
  Đo ngày 2026-09-23: LAM (Hilt 2.57.2, kapt) vỡ; Invoice (Hilt 2.60, KSP) và Speaker Clean (không Hilt)
  không sao. **App có Hilt phải lên ≥ 2.60 cùng lúc với bộ quảng cáo.**
- Lịch nâng: GMA Next-Gen (doc 60 A.1) được đưa vào một major riêng.

### B. Stack quảng cáo theo loại app

| Loại app | Stack chuẩn |
|---|---|
| **App tiện ích / công cụ** (3 app hiện tại) | AdMob mediation + Meta qua adapter bidding, cùng một phiên đấu giá. **Không** chạy song song stack Meta trực tiếp (`adfb_*` ở Speaker Clean) trừ khi A/B chứng minh có lời. **Không** MAX |
| **Game** | Quyết định riêng giữa AdMob mediation và MAX; mạng game (Unity, Chartboost, Mintegral, Pangle…) chỉ ở đây |

**Bidding partner: cấu hình được, mặc định Meta; A/B qua AdMob console (D1, 1.9.0).** Tập đóng `meta`, `pangle`, `mintegral`,
`unity`, `vungle`, `applovin`; `meta` luôn có. App khai string-array `ads_bidding_partners` (sai dạng: bỏ cả resource, về `meta`);
Remote Config `ads_bidding_partners_android` chỉ **thu hẹp** tập đó. Registry chỉ là hiện diện lúc build + nhãn: client **không loại**
được adapter đang có khỏi phiên đấu giá, nên A/B partner làm ở AdMob console (mediation A/B) hoặc bằng hai bản dựng, không bằng
Remote Config. 5 adapter tuỳ chọn là `compileOnly` trong SDK: app muốn partner nào tự khai adapter đó và liệt kê tên ở
`ads_bidding_partners`. Dòng init `ads admob adapter <p>:` của GMA chỉ có khi nhóm mediation của app ID trên console gồm partner đó
(owner-only, D50). Bật `unity`/`applovin` ở bản dựng còn cần thêm keep/dontwarn (2.0.0).

Sau khi tách module (doc 60 B.3), app tiện ích chỉ khai `core + ads + ads-gma + ads-meta`, không cần
`exclude` như LAM đang phải làm.

### C. Ad unit và floor

- **Mỗi định dạng một ad unit cho mỗi app.** Chỉ tách thêm unit khi cần báo cáo riêng.
- Không khai trùng `unit;unit` (Speaker Clean đang khai như vậy): danh sách hai phần tử giống nhau không
  tạo tầng floor nào, chỉ gây nhầm.
- **Floor:** bắt đầu bằng Google optimized floors, mặc định "All prices" [P]. Thử "High floor"/"Medium
  floor" (đang Beta) bằng A/B của AdMob, theo từng app. Không tự dựng waterfall nhiều unit theo giá.
  Google ghi rõ floor *"is likely to affect overall monetization"* vì làm giảm fill [P].
- Tên ad unit trên console: `<app>_<format>_<nhóm placement>`, ví dụ `invoice_inter_task_done`.

### D. Tên placement — một bộ từ vựng chung

Giữ các tiền tố SDK đã quy định trong code:
- `bn_`, `cl_`, `rect_`, `nt_`, `full_`, `full_2_`;
- `gift_` là **bắt buộc** cho rewarded, vì `showRewarded()` tự thêm tiền tố này;
- `rwi_` (1.9.0) cho **rewarded interstitial**: `loadRewardedForThisTurn("rwi_<x>")` / `showRewarded("rwi_<x>", …)`, id từ resource
  `admob_rwi` (`rwi_default,<ad unit>`; `default` bắt buộc như mọi định dạng). Tiền tố `rwi_` được định tuyến **trước** `gift_`.
  Thứ tự cổng: `ad_show_attempt` → kill switch → remove-ads → phiên đầu + khoảng cách (chung mốc với interstitial) → quảng cáo hết hạn →
  **màn intro** (đếm ngược 5 giây, nút "Bỏ qua" = `ad_show_fail reason=rwi_intro_skipped`; lựa chọn an toàn của SDK, không phải trích dẫn
  chính sách AdMob) → show. Chỉ AdMob; brain không tham gia; không chuỗi Full2.

Phần sau tiền tố lấy từ bộ từ vựng chung:

| Khoảnh khắc | Ví dụ |
|---|---|
| `splash`, `resume` | `full_splash`, `full_resume` (app open) |
| `task_done_<việc>` | `full_task_done_save_invoice`, `full_task_done_clean` |
| `nav_<màn>` | `full_nav_calendar` |
| `home`, `list_<màn>`, `detail_<màn>` | `bn_home`, `nt_list_invoices` |
| `unlock_<tính năng>`, `extra_<tài nguyên>` | `gift_unlock_premium_template`, `gift_extra_invoice` |
| `default` | **bắt buộc có** cho mọi định dạng đang dùng — SDK rơi về `*_default` khi không tìm thấy placement |

Luật:
- Mọi app khai ad unit trong **một file duy nhất cùng định dạng**: `ads_ids.xml` (trong `res/values/`), kiểu của
  Invoice. Không dùng `resValue` trong Gradle (LAM) hay trộn vào `strings.xml` (Speaker Clean).
- Bản debug dùng unit test qua `ads_ids.xml` trong `src/debug/res/values/`.
- Hiện có một lỗ hổng do mỗi app đặt tên một kiểu [M]. App open dùng lớp full-screen nên rơi về
  `full_default`, và chỉ tính cấu hình trong app thì:
  - **LAM** chỉ khai `full_splash` → app open khi quay lại app không có ad unit;
  - **Invoice** chỉ khai `full_resume` → không có app open lúc mở app lần đầu;
  - Remote Config (`cf_ids_placement`) có thể đang bù — cần kiểm tra trên console.
- **Chuẩn:** khai tường minh cả `full_splash` và `full_resume`.

### E. Luật theo từng định dạng (theo chính sách AdMob)

**App open** [P]
- Cold start: chỉ hiện từ màn loading.
- Hiện lần đầu sau khi người dùng đã dùng app vài lần (*"after your users have used your app a few
  times"*).
- Không hiện khi app đã vào nội dung chính.
- Quảng cáo đã load hết hạn sau 4 giờ, phải load lại.
- Không hiện khi quay lại app từ một intent của chính app (share, xin quyền, thanh toán, đăng nhập). Dùng
  `suppressNextOpenAd` — Speaker Clean đã gọi ở 14 chỗ; đưa về SDK làm mặc định.

**Interstitial** [P]
- Chỉ đặt *"in between page content, levels, or stages"*, tức ở điểm dừng sau khi xong một việc.
- Load trước để không hiện bất ngờ.
- Bị cấm:
  - lúc mở hoặc thoát app;
  - sau mỗi thao tác (tối đa 1 quảng cáo cho 2 thao tác);
  - ngay sau một interstitial khác vừa đóng;
  - khi người dùng đang tập trung vào việc (điền form, đọc nội dung).
- Chuẩn nội bộ [D]:
  - phiên đầu không hiện (Invoice);
  - cách nhau ≥ 4 phút (LAM);
  - không hiện trong 5 giây sau khi quay về từ click quảng cáo (Speaker Clean).

**Rewarded** [D]
- Chỉ khi người dùng chủ động bấm, với lợi ích nói rõ.
- Trao thưởng **đúng một lần** cho mỗi lần xem (`RewardDeliveryGate`).
- Có trần theo từng lợi ích (Invoice: 5/ngày).
- Thưởng có giá trị thật thì bật server-side verification.

**Banner / native inline** [D]
- Banner adaptive, để AdMob tự refresh; không tự refresh nhanh hơn.
- Collapsible chỉ trên màn nội dung, không che nút điều khiển. Từ 1.9.0 (GMA): app khai string-array `ads_collapsible_placements` (slot `bn_` có trong `admob_banner`; một mục sai thì bỏ cả danh sách); mọi request của slot mang extras `collapsible=bottom` cho tới lần nạp **thành công** đầu tiên của slot trong process (nạp lỗi không dùng hết extras, D37), sau đó load lại/refresh không mang extras.
- Native collapsible (NtCl): mỗi host giữ tối đa một view (1.9.0).
- Native trong danh sách tối đa 1 card mỗi khoảng 8 hàng.
- Đủ thành phần bắt buộc (nhãn "Ad", tên nhà quảng cáo); kiểm bằng Native Validator trong bản debug [P].

**Native toàn màn hình và chuỗi full2**
- Native toàn màn hình phải đủ thành phần bắt buộc và không giả nội dung app [P].
- Việc nối full2 ngay sau interstitial / rewarded **đã được AM của AdMob xác nhận** (2026-09-23).
- **Chuẩn:**
  - bật bằng `full2FlagShow`: `1` = sau interstitial, `2` = sau rewarded, `3` = cả hai;
  - **không nối sau app open**, và không nối sau `full_splash` / `full_2_splash` / `full_intro_3`;
  - cách chuyển cảnh theo mục 3.M.

**Vùng cấm quảng cáo** [D]
- Khai bằng danh sách route trong SDK, tổng quát hoá từ `AdPlacementRules` của LAM.
- Mặc định cấm: paywall, luồng mua, onboarding, và nội dung nhạy cảm do từng app khai (LAM: kinh,
  thiền, pháp thoại).
- Chưa biết đang ở màn nào thì coi như bị cấm.

**Tắt quảng cáo khi đã mua**
- Hỗ trợ tắt theo từng định dạng (mask) như Invoice (`setRemoveAdsMask`).
- Nối với `Entitlements` của SDK.

### F. Luật tần suất nằm ở một chỗ

Hiện có **năm** nơi chứa luật tần suất [M]:
- `InterstitialGate` + `AdPlacementRules` (app LAM);
- `FullScreenAdPolicy` (fork Speaker Clean);
- first-session grace trong `AdMobAdsController` (app Invoice);
- `AdsOptimizationPolicy` (SDK);
- `GuardrailEngine` (brain).

**Chuẩn:** gộp về SDK thành một policy duy nhất, cấu hình bằng một khoá Remote Config
`ads_policy_android` có schema và version. Giá trị mặc định:

| Luật | Mặc định | Lấy từ |
|---|---|---|
| Không interstitial ở phiên đầu | bật | Invoice |
| App open lần đầu | từ lần mở app thứ 2 trở đi | Google [P] |
| Khoảng cách interstitial tối thiểu | 4 phút | LAM (nằm trong dải 3–5 phút của khuyến nghị ngành) |
| Số lần chuyển màn trước interstitial | 4 | LAM |
| Cooldown sau khi click quảng cáo | 5 giây | Speaker Clean |
| Chặn app open sau intent của app | bật | Speaker Clean |
| Chuỗi full2 | theo `full2FlagShow` (1 / 2 / 3); không sau app open | mục E, 3.M |
| Trần rewarded theo lợi ích | do app khai | Invoice |
| Vùng cấm | danh sách route do app khai | LAM |

Brain (khi bật) chỉ được **chặt hơn** các giá trị này, không bao giờ nới ra. Nguyên tắc này đã có ở
doc 38: lượt nào luật tĩnh đã chặn thì vẫn bị chặn.

**SDK 1.4.0 (Task 10, áp tạm DOC/65 §7 #1, #2) [M].** `AdsPolicy` đọc `ads_policy_android`. Chủ dự án chưa chốt
hai quyết định đó, nên **mặc định trong SDK vẫn là hành vi 1.3.1** (không chặn phiên đầu, app open mọi phiên,
khoảng cách = `fullDeltatime` của `ads_region_android`, 30 s khi chưa đặt; cooldown N màn hình = giá trị app đặt,
0 = tắt; cooldown sau click 5 s; app open hết hạn 240 phút). Bảng trên là chuẩn đích, bật cho từng app bằng
Remote Config:

```json
{"schema_version":1,"no_interstitial_first_session":true,"app_open_from_session":2,
 "interstitial_min_gap_seconds":240,"post_click_cooldown_seconds":5,"app_open_max_age_minutes":240}
```

Thêm `"screens_before_interstitial":4` chỉ khi app gọi `AdsHelper.recordScreenChange()` mỗi lần đổi màn; app
chưa báo màn nào thì SDK không bật cooldown này (log lỗi một lần) thay vì chặn interstitial vĩnh viễn. Khoảng
kẹp: `app_open_from_session` 1–100, `interstitial_min_gap_seconds` 0–3600, `screens_before_interstitial` 0–50,
`post_click_cooldown_seconds` 0–60, `app_open_max_age_minutes` 1–240. Trường sai kiểu, khoá lạ, JSON hỏng hay
`schema_version` khác 1 đều có log lỗi `ads policy: …` và rơi về mặc định. Lượt bị chặn gửi
`ad_show_fail_reason` = `policy_first_session`, `policy_min_gap`, `policy_app_open_session`. Giá trị có hiệu lực
ngay khi Remote Config về và được lưu cho lần mở kế.

**"Phiên" trong SDK 1.4.0 [M]:** một lần process chính khởi động — `AppPluginManager.initPlugin` tăng bộ đếm
`mem_app_session_count` — kể cả khi process được khởi động nền (push, WorkManager) mà người dùng không mở màn hình
nào. Không phải phiên 30 phút của `LogEventServer`. Nên "phiên đầu" và "từ phiên thứ 2" trong bảng trên có thể
qua sớm hơn số lần người dùng thật sự mở app; app cần đếm theo lần mở màn hình thì phải chờ một bộ đếm riêng
(follow-up).

### G. Consent

Một luồng cho mọi app:
- UMP được hỏi mỗi lần mở app.
- `ConsentGate` giữ việc init và load quảng cáo lại cho tới khi consent xong.
- Tier được lưu lại.
- Đọc TCF 2.3.
- Tự thử hiện lại form nếu hiện thất bại, không hiện chồng form (các bản sửa của Invoice).
- Có mục "Quyền riêng tư" trong Settings khi `isPrivacyOptionsRequired`.
- `childDirected` do từng app khai rõ.
- Opt-out theo luật bang Mỹ (GPP, từ SDK 1.6.0): mỗi app bật thông điệp **US states** cùng GDPR trên AdMob console
  (Privacy & messaging). UMP ghi `IABGPP_*`; SDK đọc section US National (7) và CA/VA/CO/UT/CT (8–12). Người dùng chọn
  "Do not sell or share" → Firebase Consent Mode `ad_user_data`/`ad_personalization` denied, AppsFlyer không chia sẻ
  với partner nào (`setSharingFilterForPartners("all")` trước start, từ 1.6.1 — `setSharingFilterForAllPartners()` đã
  deprecated từ AppsFlyer 6.4), MAX `doNotSell`, Meta Audience Network và facebook-core
  Limited Data Use. Tầng consent và luật `tierAllows` không đổi, quảng cáo vẫn hiện (GMA tự đọc GPP). Không có GPP thì SDK
  không gọi API nào. App **không** tự gọi `setDoNotSell`/`setDataProcessingOptions`/`setSharingFilter*`. Số liệu
  attribution/remarketing của nhóm opt-out giảm là đúng luật, không phải lỗi tích hợp (DOC/65 §7 #14).

Chi tiết lỗi hiện tại: audit 2026-09-23 và doc 61 mục D.

### H. Remote Config — cùng khoá, cùng schema, có version

- Mọi app dùng đúng bộ khoá SDK đọc: `ads_config`, `cf_ads_placement`, `cf_ids_placement`,
  `ads_region_android`, `cf_*`, cùng `ads_policy_android` mới. Mỗi JSON có trường `schema_version`.
- Template Remote Config **nằm trong repo app** và được review qua PR. Firebase lưu tới 300 phiên bản mỗi
  loại template và có lệnh `firebase remoteconfig:get -o <file>`, `remoteconfig:versions:list`,
  `remoteconfig:rollback -v <n>` [P].
- Không sửa thẳng trên console mà không export lại vào repo.
- Giá trị mặc định trong app sinh từ cùng một template, để bản debug (không có Remote Config) chạy giống
  bản release.

### I. Kiểm thử và QA

- **Bản debug:**
  - chỉ dùng unit test của Google (click quảng cáo thật khi dev là vi phạm, như LAM đã ghi);
  - danh sách test device áp **trước** khi SDK init và không ghi đè cấu hình khác — bản sửa của
    Invoice/Speaker Clean, đưa về SDK (doc 61 B.1).
- **Công cụ:** Ad Inspector, Native Validator, UMP debug geography EEA.
- **Cổng build release** cho mọi app, chép từ `verifyReleaseConfiguration` của LAM: không dùng App ID
  test, App ID đúng định dạng, có keystore.
- **Trước mỗi release:** kiểm bằng log trên thiết bị rằng mỗi định dạng load và hiện được, gồm native
  Meta (lỗi 6.21.0.1).

### J. Thí nghiệm và phát hành thay đổi quảng cáo

- Mỗi lần chỉ đổi **một** biến: floor, tần suất, placement hoặc định dạng.
- Chạy A/B bằng Remote Config hoặc A/B của AdMob, tối thiểu 7 ngày, đọc theo nhóm quốc gia.
- Chỉ chốt khi ARPDAU quảng cáo tăng **và** D1/D7 không giảm có ý nghĩa.
- Kết quả ghi vào changelog quảng cáo của app.

### K. Tài liệu và sổ đăng ký

- Mỗi app có một bảng placement (tên, định dạng, màn, điều kiện, ad unit) trong repo của app. Đây là thứ
  AdMob không tự tách cho mình (Invoice đã tự ghi chú điều này trong `ads_ids.xml`).
- SDK có CHANGELOG. Mỗi app ghi phiên bản SDK đang dùng.

### L. Duyệt định kỳ

Mỗi tháng một lần, đặt ba app cạnh nhau:
- bảng 2.2;
- trạng thái bộ dependency;
- lỗi consent;
- tỉ lệ fill của từng định dạng.

Mỗi app được so với **chính nó** tháng trước và với ngưỡng của nhóm quốc gia — không so với Invoice.

### M. Chuẩn chuyển cảnh interstitial / rewarded → full2 (theo Speaker Clean)

Mục tiêu: người dùng thấy **một lần bàn giao liền mạch**, không phải "hai quảng cáo với một lỗ hở ở
giữa". Không được làm treo app, và không được hiện full2 cho người đã trả tiền hoặc vừa nhận thưởng
"không quảng cáo".

**Luồng chuẩn** (rewarded chạy giống hệt interstitial):

1. **Khi quảng cáo cha hiện được** và `full2FlagShow` cho phép, bắt đầu chuyển cảnh:
   - bật lớp che phía app;
   - load full2 ngay phía sau quảng cáo cha — đo ở Speaker Clean: load lúc đóng thì fill tới sau
     3,4 giây, khi chuỗi đã bỏ cuộc;
   - dựng sẵn dialog native full phía sau;
   - **chưa** bắt đầu timeout, vì quảng cáo cha thường mở lâu hơn 3 giây.
2. **Nếu full2 fill trễ** trong lúc chuyển cảnh: dựng lại dialog ngay khi load xong.
3. **Khi quảng cáo cha đóng**, lần lượt kiểm:
   - người dùng đã remove-ads hoặc định dạng này đang bị tắt → dừng. Với rewarded, thưởng được trao
     **trước** khi đóng, nên thưởng "không quảng cáo" có hiệu lực ngay;
   - đang trong cửa sổ `suppressNextOpenAd` hoặc vừa click quảng cáo (5 giây) → dừng;
   - chưa có full2 nào load → **nhả lớp che ngay**, rồi load cho lượt sau;
   - quảng cáo cha bị đóng vì Activity host bị huỷ (xoay, đổi ngôn ngữ, dark mode — trong `clearActivity`), không
     phải người dùng đóng → dừng, gửi `host_destroyed`, **không** xếp hàng lên màn hình tái tạo (1.4.0, #6);
   - app chưa resume → xếp hàng, hiện ở frame đầu tiên sau khi app resume;
   - hiện full2, bắt đầu timeout 3 giây.
4. **Giữ lớp che tới `onAdImpression`** của full2, có dự phòng 1,5 giây cho SDK không gọi callback này.
5. **Kết thúc:**
   - luôn báo cho app để hạ lớp che, kể cả khi SDK chưa từng bật (tránh màn đen treo);
   - huỷ dialog dựng sẵn không dùng tới;
   - khi activity bị huỷ thì cũng kết thúc chuyển cảnh.

**Đếm ngược nút đóng** của native full bắt đầu ở `onStart` (lúc dialog thật sự hiện), không phải
`onCreate`. Nhờ vậy dựng sẵn không ăn mất thời gian đếm.

**Theo từng mạng:**

| Mạng | Dựng sẵn phía sau | Ghi chú |
|---|---|---|
| AdMob | inflate + populate trong `create()` | GMA chỉ tính impression khi view hiện |
| Meta (Audience Network trực tiếp) | inflate + `registerViewForInteraction` trong `create()` | Meta: *"All media need to be loaded and displayed for an eligible impression"*. Khi huỷ thì `unregisterView()` |
| MAX | chỉ inflate trong `create()`; `render()` dời sang `onStart` | AppLovin không nói rõ `render()` có tính impression không, nên không render khi chưa hiện |

**Phía app:**
- App nào có lớp che riêng thì đăng ký `setFull2TransitionListener` (Speaker Clean, Invoice).
- App không đăng ký thì SDK dùng **lớp che mặc định**: view đen chặn chạm, hạn chót 2 giây kể từ khi
  app resume.

**Hợp đồng callback (DOC/65 §7 #3, đã chốt 2026-09-25 — không đổi):** `onAdDismissed` của quảng cáo cha
**không bị hoãn** tới khi full2 xong — app nhận nó ngay khi cha đóng, như trước 1.4.0, kể cả khi full2 sau đó
còn hiện. `Full2TransitionListener.onTransitionChanged(false)` (hoặc lớp che mặc định tự hạ, khi app không
đăng ký listener riêng) là tín hiệu duy nhất báo "xong cả chuỗi chuyển cảnh". App cần biết khi nào cả chuỗi
(cha + full2) đã xong thì dùng tín hiệu này, không suy luận từ `onAdDismissed`.

Hiện trạng bốn bản code, đo ngày 2026-09-23 [M] (15 lớp bảo vệ ở trên):

| Bản | Số lớp có | Thiếu đáng kể |
|---|---|---|
| Speaker Clean | 15/15 | — |
| Invoice | 7/15 | load sau cha, nhả che sớm, chờ app resume, chặn remove-ads, không nối sau app open |
| LAM (`origin/main` SDK) | 6/15 | dựng sẵn, chặn remove-ads, chặn sau click; **app không có lớp che** |
| SDK trên máy (`568b0a4`) | 0/15 | nối full2 mà không có lớp che |

Nhánh `feat/full2-standard-sync` của SDK đã đưa đủ chuẩn này vào, cho cả AdMob, Meta và MAX.

---

## 4. Mỗi app cần làm gì để đạt chuẩn [M → D]

| Việc | Invoice Maker | Speaker Clean | LAM 2026 |
|---|---|---|---|
| GMA 25.4.0 + Meta adapter 6.21.0.4 (bộ đã kiểm thử) | đã sửa trong `build.gradle.kts` của module adsplugin (repo app, ngoài repo này; 2026-09-23, chưa commit) | đã xong | nhận qua SDK: nhánh `feat/full2-standard-sync`, cần push rồi cập nhật submodule |
| Chuyển ad unit về `ads_ids.xml` chuẩn | đã đúng | chuyển từ `strings.xml`, bỏ `unit;unit` | chuyển từ `resValue` |
| Đặt lại tên placement theo mục D | đổi `invoice_saved` → `full_task_done_save_invoice`… (giữ bảng quy đổi cho báo cáo cũ) | đặt tên cho từng điểm gọi, thay vì chỉ `*_default` | như Speaker Clean |
| Khai cả `full_splash` và `full_resume` | thiếu `full_splash` | có `full_default` | thiếu `full_resume` |
| Phiên đầu không interstitial | đã có | thêm | thêm |
| Chặn app open sau intent của app | thêm | đã có | thêm |
| Chuỗi full2 theo chuẩn 3.M | nhận khi chuyển sang SDK chung; đã bỏ nối sau app open (`6a4f901`) | đã đúng chuẩn | nhận qua SDK, dùng lớp che mặc định hoặc tự đăng ký listener |
| Stack Meta trực tiếp (`adfb_*`) | không dùng | quyết định giữ hay bỏ bằng A/B | không dùng |
| Luật tần suất riêng trong app | bỏ sau khi SDK có policy (mục F) | bỏ | bỏ `InterstitialGate`, giữ danh sách route làm cấu hình |
| Kiểm log trùng `ad_impression` (2.3) | cần | cần | cần |
| Cổng build release | thêm | thêm | đã có |

Thứ tự làm khớp với doc 61:
1. **Bước 0** (ngay): Meta adapter, kiểm `ad_impression` trùng, kiểm Remote Config có đang bù app open
   không.
2. **SDK 1.2.0:** gộp policy tần suất, `ConsentGate`, `suppressNextOpenAd`, sửa bug test device.
3. **Chuyển từng app:** LAM → Invoice → Speaker Clean, mỗi app kèm bảng placement mới.

---

## 5. Quyết định cần chủ dự án chốt

1. ~~**Full2 sau app open**~~ — **đã chốt 2026-09-23: không nối.** Đã sửa ở bản fork của Invoice
   (`6a4f901`, chưa push); SDK 1.2.0 vốn không nối.
2. **Stack Meta trực tiếp của Speaker Clean:** bỏ để gom về một phiên đấu giá AdMob, hay giữ để A/B trước?
3. **Đổi tên placement của Invoice** ngay trong lần chuyển về SDK chung, hay giữ tên cũ để báo cáo liền
   mạch rồi đổi sau?
4. **Nhóm quốc gia ở mục 2.1:** dùng như đề xuất, hay theo cách bạn đang đọc báo cáo?

---

## Nguồn

**Chính thức (Google / Firebase)**
- [AdMob — Disallowed interstitial implementations](https://support.google.com/admob/answer/6201362?hl=en)
- [AdMob — Recommended interstitial implementations](https://support.google.com/admob/answer/6201350?hl=en)
- [AdMob — Interstitial ad guidance](https://support.google.com/admob/answer/6066980?hl=en)
- [AdMob — About eCPM floors](https://support.google.com/admob/answer/3418058?hl=en)
- [AdMob Android — App open ads (best practices)](https://developers.google.com/admob/android/app-open)
- [AdMob Android — Native validator](https://developers.google.com/admob/android/native/validator)
- [Google Mobile Ads SDK forum — full-screen native ads](https://groups.google.com/g/google-admob-ads-sdk/c/bXvRdcndoQ8)
- [Firebase — Measure ad revenue (`ad_impression`)](https://firebase.google.com/docs/analytics/measure-ad-revenue)
- [Meta Audience Network — Native ads (Android)](https://developers.facebook.com/docs/audience-network/setting-up/ad-setup/android/native/)
- [AppLovin MAX — Native ads (Android)](https://support.applovin.com/en/max/android/ad-formats/native-ads)
- [Firebase — Remote Config templates and versioning](https://firebase.google.com/docs/remote-config/templates)

**Thứ cấp — chỉ để thấy độ lớn**
- [Bidlogic — eCPM growth in mobile apps Q1–Q2 2025](https://bidlogic.io/2025/07/25/ecpm-growth-in-mobile-apps-q1-q2-2025-analysis-and-insights/)
- [MonetizeMore — 2026 eCPM insights](https://www.monetizemore.com/blog/ecpm-insights/)
- [Maf.ad — Mobile ads eCPM: basics and latest data](https://maf.ad/en/blog/mobile-ads-ecpm/)

**Dữ liệu [M]:** ba repo app và repo `appplugin`, đọc ngày 2026-09-23; kế hoạch monetization của Invoice
tại InvoiceMakerAI/docs/superpowers/plans/2026-08-20-us-eu-market-monetization-plan.md (repo app, ngoài repo này).
