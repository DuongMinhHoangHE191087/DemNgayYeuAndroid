# 67 — Kế hoạch hoàn thiện SDK Android để ghép vào app

**Ngày viết:** 2026-09-29 (Fable 5.1, vai trò leader; số đo lấy từ cây `feat/1.6.3` @ `4f2125a`)
**Thay cho:** lộ trình DOC/23 (hết hiệu lực) và phần "1.7.0 / Phase C" của DOC/65 mục 5 (DOC/65 giữ tiêu chí
G1–G10 và bảng benchmark mục 4; tài liệu này là **kế hoạch chính** từ nay tới lúc SDK ghép vào app).
**Quyết định khung của chủ dự án (2026-09-29, nguyên văn rút gọn):**
1. Không sửa app hiện tại. **Hoàn thiện toàn bộ SDK trước, rồi mới ghép** vào app; dự án cũ để sau.
2. Chỉ Android. iOS chưa làm. Game chưa làm (MAX và adapter game chỉ cần build xanh).
3. Thu hẹp nhưng **đầy đủ tính năng**, chạy tốt, ổn định, không bug.
4. **Log event để đo hiệu quả của ads và của SDK còn chưa có** — phải có.
5. **eCPM và revenue phải cao hơn đối thủ.**

---

## 0. Kết luận điều hành

Bản có nội dung cuối cùng là **2.4.0** (GMA Next-Gen theo D16-230; Task R đã chốt; PR #18 sẵn sàng; tag + release local (D33), publish Packages cần PAT (OWNER 19); 2.3.0 đã phát hành); sau 2.4.0 SDK chỉ bảo trì + tối đa 1 bản vá sau cổng app trước khi ghép app; chương trình: 2.3.0 → 2.4.0 → bảo trì. Đã phát hành trên đường đó: `1.6.4` (nợ 1.6.3) → `1.7.0` (đo lường hiệu quả) → `1.8.0`
(subscription/paywall) → `1.9.0` (đòn bẩy eCPM + brain sẵn sàng calibrate) → `2.0.0` (tách module, chốt
API). Sau đó là **cổng cuối "app đầu tiên"** — bước duy nhất chứng minh được eCPM/revenue bằng số thật, và
là bước chủ dự án tự bấm nút.

Hai sự thật phải nói trước:

- **Lớp event phía client đã có** (52 tên event, `ad_start_load`/`ad_load`/`ad_show`/`ad_show_fail_reason`/
  `ad_revenue`, `onPaidEvent` ở 5 lớp AdMob ghi giá trị + network + placement, `brain_decision`,
  `iap_purchase`…) và **backend đã có** (`backend/`: FastAPI → BigQuery, schema `events_raw` 80 cột,
  `metrics.py` tính ARPDAU/holdout/paywall funnel). **Cái chưa có là đường ống chạy thật:** backend
  *"chưa deploy bao giờ"*, chống trùng *"nỗ lực tốt nhất"*, không retention, không rate limit; không có bảng
  eCPM × định dạng × nhóm quốc gia, fill/show rate, imp/DAU, consent rate như DOC/62 §2.1 đòi; 14 dòng
  benchmark trong DOC/65 mục 4 vẫn "chưa đo". Nên 1.7.0 là **làm cho số chảy tới bảng**, không phải thêm event.
- **eCPM do cầu quyết định** (AdMob bidding, quốc gia, định dạng, loại app). SDK không đặt được eCPM;
  SDK chỉ kiểm soát **bốn đòn bẩy** (DOC/65 mục 4): không mất impression (fill/show rate), mật độ đúng luật
  và đúng người (policy + brain), phiên đấu giá đông nhất có thể (bidding partners), và đo đúng để tối ưu.
  Mọi task "eCPM" dưới đây đều thuộc một trong bốn đòn bẩy này; con số so với đối thủ chỉ có sau app đầu tiên.

---

## 1. Hiện trạng đo được (2026-09-29)

| Mảng | Có | Thiếu / chưa chứng minh |
|---|---|---|
| Quảng cáo | 5 định dạng AdMob (banner, inter, rewarded, app open, native/native-full/native-collapse) + Full2, Meta bidding, MAX; collapsible banner (2 file), adaptive banner (1 chỗ); policy `ads_policy_android`; remove-ads mask | Rewarded interstitial (0); bộ adapter bidding cho app tiện ích cố định AdMob + Meta (DOC/62 B), không cấu hình/A/B được; `precisionType` mới 1 chỗ |
| Consent | UMP/TCF/GPP, Consent Mode v2, cổng cho Firebase/FCM/AppsFlyer/Meta | Consent rate EEA chưa có event; N1 (telemetry sau DENIED) chưa chốt |
| Đo lường | Event client đủ tên; Firebase `ad_impression` có chống đếm đôi (`AdRevenueRouting`); AppsFlyer ad revenue; backend + BigQuery schema + `metrics.py` | Backend chưa deploy; không dedup chắc, không retention/rate limit; không bảng DOC/62 §2.1; chưa đối soát Firebase = AppsFlyer = backend ±1%; AdMob report chưa nhập; Crashlytics **đã gắn** phiên bản SDK dưới khoá `appplugin_version` từ 1.4.0 (`AppPluginManager.kt:105`, test `AppPluginManagerCrashlyticsTest`; đọc theo khoá này, không có khoá `sdk_version`) |
| IAP/paywall | `IapHelper` 565, `Entitlements` 225, `SubscriptionLifecycle` 338, `FxRates`, `PlayIntegrityGuard`, `PaywallManager` 281 | 0 cổng thiết bị billing; `IapHelper.connect` bind lúc khởi động (#11 Samsung); chưa app nào dùng; fork Speaker Clean có `EntitlementCrypto`/`PaywallSessionMutex`/`NonPayerPlaybook` mà SDK thiếu |
| Brain | 21 file, cắm `InterstitialController` chỉ siết, `brain_policy_android`, holdout | `LikelihoodEstimator` **chưa calibrate bằng dữ liệu thật** (KDoc :27); chưa có đường calibrate từ decision log |
| Ổn định | G1–G10, 1.555 test, R8 gate, 3 máy, ABI dump | 3 file chạm trần G8; main-thread API 26 emulator 209–263 ms; #11 Samsung |
| Tích hợp | DOC/60–66, CHANGELOG "Phải làm khi nâng cấp" từng bản | **Không README gốc**; demo không có build "app tiện ích" (AdMob + Meta, không adapter game) nên mọi cổng đang đo trên cấu hình app sẽ không dùng; `build.gradle.kts` 44 dependency gồm Pangle/Vungle/Mintegral/Unity/Chartboost/AppLovin |

---

## 2. "Hoàn thiện để ghép app" nghĩa là gì — checklist đóng

SDK được gọi là hoàn thiện khi **tất cả** dòng dưới đây có bằng chứng (log/commit/số) trong ledger:

| # | Điều kiện | Bằng chứng |
|---|---|---|
| H1 | Một dependency `appplugin` (+ `appplugin-max` chỉ cho game) cho trọn 5 nhóm: ads, consent, đo lường, subscription/paywall, tối ưu tần suất có holdout | DOC/65 mục 5 định nghĩa "đầy đủ" — **✅ v2.1.0** (`a7606c0`; CHANGELOG `[2.1.0]`, DOC/65 §3 "Số đo 2.1.0") |
| H2 | Demo có build variant **`utility`** = AdMob + Meta, 0 adapter game; **mọi cổng thiết bị từ 1.7.0 chạy trên variant này** | `app/build.gradle.kts` flavor; log gate ghi variant — **✅ v2.1.0** (variant `utility`; mọi cổng thiết bị 2.1.0 chạy trên variant này) |
| H3 | Bản tải về của demo `utility` ≤ 25 MB (DOC/65 mục 4) | `bundletool get-size total` — **✅ v2.1.0** (APK utility ≤ 25 MB, `bundle_size.sh`; DOC/65 §3) |
| H4 | G1–G10 PASS trên 3 máy ở bản tag cuối (2.0.0) | DOC/65 mục 3 điền lại — **✅ v2.1.0** (G1–G10 PASS, DOC/65 §3 "Số đo 2.1.0") |
| H5 | Đường ống đo lường chạy thật: event từ demo → backend production → BigQuery → bảng DOC/62 §2.1 hằng ngày | URL Cloud Run + query + 1 bảng có số. **1.7.0: đã chạy trên stack cục bộ (JSONL → emulator BigQuery → 7 bảng); chạy thật trên GCP = program gate mở, chờ chủ dự án (billing, Q4)** — **chờ chủ dự án** (GCP thật, billing Q4; chạy thật = OWNER-NOTES) |
| H6a | Đối soát **số lượng** trên test ads: Firebase `ad_impression` = AppsFlyer = backend `ad_revenue`, 3 phiên × 4 thiết bị, chênh 0 — **điều kiện tag 1.7.0** | log của script đối soát phiên (Task 13, `tools/reconcile_session.sh`); **1.7.0: 12/12 phiên hợp lệ trên 4 máy (API 37/28/26 + Redmi), mỗi phiên 3 số bằng nhau, hiệu tập rỗng (`t170-r-*-h6a.log`)** |
| H6b | Đối soát **giá trị** doanh thu: ± 1 %/ngày, 7 ngày liên tiếp (trên demo với ad unit thật ở internal testing) — **cổng chương trình**, cần chủ dự án (Q3) | log đối soát 7 ngày. **1.7.0: program gate mở** (đường ống đối soát số lượng xong; giá trị cần ad unit thật ở internal testing, Q3) — **chờ chủ dự án** (ad unit thật, Q3) |
| H7 | Cổng thiết bị billing: mua, huỷ, grace, restore, mở lại app, StrictMode by-sdk = 0, `ad_show_fail reason=remove_ad` = 100 % sau `grant`. **Tách từ 1.8.0 (xem §4): H7a** = harness qua state machine thật trên 37/28/26 + Redmi (`tools/device_gate.sh --iap`, DOC/65 G12) — điều kiện tag 1.8.0; **H7b** = mua/grace/hold/RTDN thật trên Play internal testing — program gate, chờ chủ dự án | `tools/device_gate.sh --iap` (H7a); H7b: OWNER-NOTES #4 — **H7a ✅; H7b chờ chủ dự án** (OWNER-NOTES #4) |
| H8 | Brain: bật/tắt/rollout/holdout qua Remote Config, decision log về backend, script calibrate chạy được trên log thật của demo | báo cáo + script calibrate (1.9.0: `backend/app/calibrate.py`; **đường ống end-to-end chạy trên log gate** — rc 0, `calib-20261001-f4bc6102`, mọi ô support = prior; D-RC qua harness brain; RC thật = OWNER-NOTES) — **✅ trên harness; RC thật chờ chủ dự án** |
| H9 | Bề mặt API chốt: `explicitApi`, `internal` ≥ 70 %, ABI dump + façade trong CI, `apiCheck` chặn được thay đổi thử | log CI — **✅ v2.1.0** (205/282 = 72,7 %; façade ×2 PASS, CI) |
| H10 | Tài liệu ghép: README gốc + DOC/61 cập nhật: 1 trang các bước, bảng resource/khoá Remote Config có schema + version, CHANGELOG gộp "Phải làm khi nâng cấp" 1.3.0 → 2.1.0 (hai bảng, `[2.0.0]` + `[Unreleased]`) | file — **✅ v2.1.0** (README, DOC/61, CHANGELOG `[2.1.0]`) |
| H11 | Ba AAR 2.0.0 (`appplugin`, `appplugin-max`, `appplugin-game`) trên GitHub Packages, CI xanh. `device-gate.yml` **chỉ thông tin**, không phải cổng: cổng thiết bị chỉ chạy trên máy này (chủ dự án 2026-09-30, D14); không đề xuất cổng thiết bị trên GitHub runner | run id của CI và `release.yml` (cổng); run id `device-gate.yml` chỉ ghi (thông tin) — **✅ v2.1.0** (3 AAR, CI + `release.yml` xanh) |

---

## 3. Đòn bẩy eCPM / revenue mà SDK kiểm soát được

| Đòn bẩy | Việc SDK làm | Bản | Chứng minh khi nào |
|---|---|---|---|
| Không mất impression | Show rate ≥ 95 % (đã gate), fill/show rate theo placement lên bảng, `ad_show_fail reason` phủ 100 % (đã có; trước 1.7.0 thiếu: cổng tần suất interstitial `AdsFrequencyGate.interstitialRefusal()` và 8 đường `return false` im lặng của `InterstitialController`/`RewardedController`; `isIntersAllowShowAds` là tên cũ không còn trong mã — **đã đóng ở 1.7.0 T2**) | 1.7.0 | Trên demo (kỹ thuật), trên app (số) |
| Phiên đấu giá đông | Bộ adapter **bidding** cho stack tiện ích cấu hình được qua resource/Remote Config (Meta mặc định; Pangle/Mintegral/Unity/Vungle/AppLovin bidding qua AdMob là tuỳ chọn, mặc định tắt), có A/B; log `ad_network` mỗi impression (đã có) | 1.9.0 | Chỉ trên app, A/B ≥ 2 tuần |
| Định dạng có eCPM cao cho app tiện ích | Collapsible banner hoàn chỉnh + test; adaptive banner mặc định; **rewarded interstitial** (mới); app open on resume theo policy | 1.9.0 | Demo (kỹ thuật) |
| Mật độ đúng người | `ads_policy_android` theo nhóm quốc gia T1/T2/VN/Khác; brain siết cho người sắp churn, holdout đo D1/D7 | 1.9.0 | App |
| Floor | Không làm trong SDK: Google optimized floors + A/B trên console (DOC/62 C) | — | Console |
| Đo đúng | Toàn bộ 1.7.0 | 1.7.0 | Demo |

Điều SDK **không** làm được: chọn placement trong luồng người dùng của app, số màn hình giữa hai
interstitial hợp với app đó, giá subscription, ASO. Đó là việc lúc ghép app.

---

## 4. Lộ trình bản

Mỗi bản đi theo quy trình đang dùng: brief (Fable) → plan chi tiết (Opus, `docs/superpowers/plans/`) →
dry-run (Sonnet, plan-lint-checklist.md (trong .superpowers/sdd, ngoài repo)) → implement + review từng task → IG → Task R đo số → F1 review
toàn nhánh → publish. Cỡ: **S** ≤ 1 ngày agent, **M** 1–3 ngày, **L** > 3 ngày.

### 1.6.3 — đóng (đang chờ)

Chờ chủ dự án chốt **#11 Samsung** (đề xuất hướng A: phân loại "do hệ điều hành", dời bind sang 1.6.4) →
F1b → E5 docs (CHANGELOG:20 số dòng) → Task P publish. Không thêm gì.

### 1.6.4 — "Trả nợ 1.6.3, chuẩn bị cấu hình app tiện ích" (S–M) — **xong 2026-09-30** (đầu `51c553a`, chưa tag; số đo, F2 và carry 1.6.5: DOC/65 §5 "1.6.4")

| # | Task | Cỡ |
|---|---|---|
| 1 | Dời `IapHelper.connect` (bind `BillingClient`) khỏi đường khởi động: lazy tới lần gọi IAP đầu hoặc sau `onCreate` + 1 frame, trên luồng nền; gate Samsung by-sdk = 0 | S |
| 2 | Tách 3 file chạm trần G8: `LogEventServer.kt` 970, `GppConsent.kt` 249, `AppsflyerHelper.kt` 444 — tách trước, không nâng trần | M |
| 3 | Test còn thiếu: T1-R1 (thứ tự bước `initPlugin`), T1-R2 (`dataBucketConfig` rỗng), T6-m2, T1b-m1, T5-m1 | S |
| 4 | Crashlytics: khoá phiên bản SDK **đã có** dưới tên `appplugin_version` từ 1.4.0 (`AppPluginManager.kt:105`; không thêm khoá `sdk_version` trùng). `sdk_health` đã mang `sdk_version`, `consent_tier`; 1.6.4 chỉ thêm `init_ms` | S |
| 5 | **Demo flavor `utility`**: AdMob + Meta only (adapter game `compileOnly` hoặc flavor `game`); `tools/device_gate.sh` nhận `--variant`; từ 1.6.4 cổng chạy variant `utility` là chính, `game` chỉ build xanh | M |
| 6 | N1: thực thi quyết định chủ dự án về telemetry sau `DENIED` (đề xuất: giữ hàng đợi, không gửi cho tới khi tầng đổi) | S |

**Xong khi:** G1–G10 PASS trên variant `utility` ở 3 máy; Samsung by-sdk = 0 cả hai chặng; 3 file dưới trần;
`bundletool` size của `utility` ghi vào ledger (mốc cho H3).

**Kết quả 1.6.4 (DOC/65 §5):** #1–#6 xong (#1 billing nền + `isReady` đúng nghĩa, #2 ba file dưới trần, #3 test còn thiếu, #4 chỉ `init_ms` (khoá phiên bản đã có), #5 flavor `utility`/`game` + `--variant`, #6 N1 giữ trong bộ nhớ sau `DENIED`). Samsung by-sdk = 0 cả hai chặng ở 3/3 lượt, `bundletool` size `utility` 7 584 849–7 641 195 B. Chưa đủ "3 máy": Redmi không cắm. API 37: 3/4 lượt vòng 2 đỏ `am_anr`, đã phân loại contention host (A/B 6/6 sạch, DOC/65 §5).

### 1.7.0 — "Đo được hiệu quả của ads và của SDK" (L) — **xong 2026-10-02** (đầu = commit của tag `v1.7.0`; số đo: `CHANGELOG.md` "Đã kiểm", carry 1.8.0: DOC/65 §5 "1.7.0")

Mục tiêu: **bảng DOC/62 §2.1 có số mỗi ngày từ demo**, và mọi con số về sau (IAP, brain, eCPM) đọc từ cùng
một chỗ.

| # | Task | Cỡ |
|---|---|---|
| 1 | **Audit + đóng khe event quảng cáo** (schema `ad_events` v2, có `schema_version`): mỗi placement đủ chuỗi `ad_request → ad_fill / ad_no_fill(error_code, latency_ms) → ad_ready → ad_show / ad_show_fail(reason) → ad_click → ad_revenue(value_micros, currency, precision_type, ad_network, ad_unit, format, placement, country, tier)`; phủ cổng tần suất interstitial (`interstitialRefusal()`, trước đây gọi `isIntersAllowShowAds`) và 8 đường im lặng; test hợp đồng tên/param (bảng: DOC/62 §2.1 "Event v2") | M |
| 2 | Event consent funnel: `consent_form_shown`, `consent_choice(tier, gdpr_applies, gpp_sections)` → consent rate EEA | S |
| 3 | Event phiên: `session_end(duration_ms, ads_shown_by_format, screens)` để tính imp/DAU và mật độ | S |
| 4 | **Backend production-ready** (Q4: dựng và đo trên stack cục bộ Docker + bigquery-emulator; `deploy_cloudrun.sh` chỉ chủ dự án hoặc controller sau go bằng văn bản, qua `tools/gcp_guard.sh`): staging + `MERGE` theo `dedup_key`; partition expiration; rate limit + API key per app (`BRAIN_API_KEYS`); healthcheck `GET /health`; `backend/RUNBOOK.md` (§0 điều kiện IAM) | M |
| 5 | **Bảng chỉ số DOC/62 §2.1** (view SQL `backend/sql/views/` là bản chạy, oracle Python `backend/app/ad_metrics.py`, khoá bằng test parity; một định nghĩa): eCPM × định dạng × nhóm quốc gia, imp/DAU, ARPDAU ads, fill rate, show rate, consent rate, D1/D7 + holdout compare; script `tools/report_daily.sh` xuất Markdown vào `DOC/reports/` | M |
| 6 | **Nhập AdMob report** (luồng JSON `mediationReport:generate` trước, CSV qua `HEADER_MAP` sau; `App × Ad format × Country × Ad source`, 28 ngày; `backend/app/admob_import.py`) → match rate, observed eCPM; đối soát với `ad_revenue` client ở mức nhóm quốc gia | S |
| 7 | **Đối soát 3 nguồn**: số lượng trên test ads (H6a, script đối soát phiên của Task 13, `tools/reconcile_session.sh`); giá trị Firebase `ad_impression` = AppsFlyer = backend ± 1 %/ngày (H6b) cần demo `utility` ở **internal testing** với ad unit thật (test ads trả giá 0 nên không đối soát được giá trị) | M |
| 8 | Kill switch + sampling cho `logevent` theo Remote Config (bảo vệ chi phí BigQuery khi DAU tăng) | S |
| 9 | Cổng "đo lường" thêm vào `device_gate.sh`: 1 cold start → 1 phiên → backend nhận đủ chuỗi event, 0 mất, 0 trùng | S |

**Kết quả 1.7.0:** #1–#9 xong (T1–T3 #1, T4 #2, T5 #3, T9–T11 #4, T10 #5, T12 #6, T13 #7 H6a + #9 cổng G11, T7 #8, T8 demo `utility` → sink cục bộ). H6a: 12/12 phiên hợp lệ trên 4 máy; G11 4/4; G13 p95 trên sink JSONL; H5 và H6b = "program gate mở" (cần GCP thật / ad unit thật, chủ dự án). Samsung R5CX10P9EKZ không cắm nên chưa đo. Số liệu: `.superpowers/sdd/v170/metrics-170.md`, DOC/65 §3/§4/§5.

**Xong khi:** H5 và H6a có bằng chứng (H6a là điều kiện tag); H6b (7 ngày liên tiếp, giá trị) là cổng chương trình cần chủ dự án; 14 dòng "chưa đo" của DOC/65 mục 4
giảm còn những dòng cần app thật (match rate/eCPM theo quốc gia của app), và ghi rõ.

### 1.8.0 — "Subscription / IAP / paywall đạt chuẩn phát hành" (L) — **mã xong 2026-10-02** (đầu = commit của tag `v1.8.0`; số đo: `CHANGELOG.md` "Đã kiểm"; còn đo lại G12 28/26 sau TF-1 và máy thật)

| # | Task | Cỡ |
|---|---|---|
| 1 | Đối chiếu fork (chỉ **đọc** `~/AndroidStudioProjects/*`): liệt kê hành vi SDK thiếu — mutex phiên paywall, mã hoá entitlement lưu cục bộ, playbook non-payer; bổ sung vào SDK theo cách của SDK | M |
| 2 | `IapHelper`: acknowledge/consume đúng luật Play, restore khi mở app, pending purchase, `queryPurchasesAsync` sau `onResume`, retry backoff; không việc nào trên main | M |
| 3 | `SubscriptionLifecycle`: nhận cả tín hiệu server (webhook RTDN vào backend 1.7.0 → cờ trong Remote Config/`logevent` response) để bớt suy luận client; ghi rõ giới hạn còn lại | M |
| 4 | `PaywallManager`: schema có version, A/B + holdout, event `paywall_impression/choice/purchase` nối vào bảng 1.7.0 (`paywall_funnel` đã có trong `metrics.py`) | S |
| 5 | **Cổng thiết bị billing** `tools/device_gate.sh --iap`: Play test account, demo ở internal testing; mua, huỷ, grace, restore, mở lại, StrictMode by-sdk = 0, `ad_show_fail reason=remove_ad` = 100 % sau `grant` | M |
| 6 | Unit + đột biến đỏ cho `iap`/`paywall`; ABI dump chỉ thêm | S |

**Xong khi:** H7a; `iap_purchase` với `price_micros`/`currency_code`/`phase` lên bảng ARPDAU cả hai vế, USD tính ở server theo `fx_daily` (D3; client không gửi `usd`).

**Cách 1.8.0 thực hiện (plan `docs/superpowers/plans/2026-09-30-sdk-1.8.0-plan.md`; cập nhật 2026-10-01 khi viết Task 17; T11 và T16 đang giao song song, số đo: sau Task R):**

- **H7a (điều kiện tag 1.8.0) — xong ở mức mã (Task R #11/#12):** G12 API 37 12/12 PASS, Play thật API 37 PASS (không có dòng lỗi), backend `/rtdn` 11/11 (DOC/65 §3 G12); G12 28/26 sau TF-1 và máy thật: chưa đo. Nội dung: harness G12 qua `BillingPort` nội bộ — 12 script chạy qua state machine thật của `IapHelper` trên emulator API 37/28/26 + Redmi; `iap_purchase` đúng một lần theo token; PENDING/`isSuspended()` không cấp quyền lợi;
  quyền lợi lưu có HMAC + Keystore (D10); paywall schema v2 (holdout mặc định 0, `session_cap`); `/rtdn` + `signals` trên stack cục bộ (0 lời gọi GCP thật); `showInAppMessages` tự gọi khi grace/hold (T11); `BillingProxyCrashGuard`; ABI chỉ thêm khối `internal` (D1).
  Bằng chứng: DOC/65 §3 G12 (cột 1.8.0 điền ở Task R), README mục 8.
- **H7b (program gate mở; OWNER-NOTES #4):** những gì cần Play/GCP thật và không thể giả: demo `utility` trên Play **internal testing** + **license tester**, mua thật (sheet của Play), grace/hold/renewal/refund thật, tái hiện NPE `ProxyBillingActivity` trên máy,
  **RTDN**: topic Pub/Sub + subscription push tới `/rtdn` (xác thực OIDC thay bearer tĩnh), credential đọc `subscriptionsv2` (hiện `SubscriptionSource` chỉ có fixture, bản thật trả 503), đối soát doanh thu IAP với Play Console. Khi H7b mở, Q3 (DOC/67 §5) và Q4 (billing GCP) vẫn là điều kiện.
- **Giới hạn đã biết sau 1.8.0:** client không thấy grace trực tiếp (Play báo sub trong grace như đang gia hạn) — grace/hold chỉ biết khi backend nhận RTDN; nhãn server chỉ thắng quan sát client 7 ngày (D27); `iap_purchase` phía client của người `DENIED` không được báo (D15).


### 1.9.0 — "Đòn bẩy eCPM và brain sẵn sàng" (M–L) — **mã xong 2026-10-01** (đầu mã `99b7d5d`, công cụ `bcfe3c6`; số đo: `CHANGELOG.md` "Đã kiểm", DOC/65 §3 "Số đo 1.9.0")

| # | Task | Cỡ |
|---|---|---|
| 1 | Bộ adapter bidding cho stack tiện ích **cấu hình được** (resource `ads_bidding_partners` + Remote Config override, mặc định `meta`); init chỉ adapter được bật; test: bật `pangle` không đụng đường AdMob; log `ad_network` mỗi impression | M |
| 2 | Rewarded interstitial (AdMob) như định dạng mới, cùng policy/telemetry/remove-ads mask | M |
| 3 | Collapsible banner + adaptive banner: hoàn chỉnh, test, cổng thiết bị (không crash "child already has a parent" khi collapse/expand) | S |
| 4 | `ads_policy_android` theo nhóm quốc gia (T1/T2/VN/Khác) — cùng schema, thêm khối `by_tier` | S |
| 5 | Brain: backend/app/calibrate.py (chưa có, làm ở bản này) gom decision log → `mu` cho `LikelihoodEstimator`, xuất `brain_policy_android` có version; `rolloutFraction` từ Remote Config; log `brain declined`/`holdout` lên bảng 1.7.0; kill switch | M |
| 6 | Cổng: mọi định dạng mới qua G1–G3 trên `utility` 3 máy; show rate ≥ 95 % giữ | S |

**Xong khi:** H8; bảng 1.7.0 có cột `ad_network` và `format` cho định dạng mới; chưa có số eCPM so đối thủ
(cần app).

**Cách 1.9.0 thực hiện (plan `docs/superpowers/plans/2026-10-01-sdk-1.9.0-plan.md`; số đo Task R: `metrics-190`, `CHANGELOG.md` "Đã kiểm"):**

- **#1 bidding:** tập đóng `meta`, `pangle`, `mintegral`, `unity`, `vungle`, `applovin` (`meta` mặc định); resource `ads_bidding_partners`, Remote Config `ads_bidding_partners_android` chỉ thu hẹp; 5 adapter tuỳ chọn là `compileOnly` (app tự khai). Registry là hiện diện + nhãn, **không** loại được adapter khỏi phiên đấu giá: A/B làm ở AdMob console (D1). Chặng pangle trên API 37: gate PASS (FATAL/ANR/native 0, by-sdk 0), nhưng dòng `ads admob adapter pangle:` **chưa đo** (cần nhóm mediation trên console, OWNER-NOTES #5, D50); APK pangle dựng từ `aee3418` (trước T6), không phải APK cuối. Cỡ thêm cho partner (thông tin): pangle +2 056 907 B, mintegral +2 242 047 B, vungle +368 310 B; unity và applovin **chưa đo** (R8 thiếu lớp, cần thêm keep/dontwarn, 2.0.0; D51).
- **#2 rewarded interstitial:** `rwi_<x>` (không API mới), intro đếm ngược do SDK vẽ; chặng 1 (hiện, thưởng, đóng) PASS ở API 37 và Redmi; chặng 2 (bỏ qua), 3 (`policy_min_gap`) và `remove_ad` của rwi **chưa đo (không có driver)**, được ghim bằng unit T2 (D53).
- **#3 collapsible banner / NtCl:** extras `collapsible=bottom` tới lần nạp thành công đầu tiên (D37); NtCl 1 view/host.
- **#4 `by_tier`**, **#5 brain:** kill `enabled:false` thật, artifact persist qua cold start, `rollout_fraction` của artifact thắng; D-RC 3 chặng qua harness brain trên API 37 PASS (nạp `calib-…`, kill 0 `brain_decision`, rollout 0 giữ qua cold start), **không ghi Firebase Remote Config thật** (RC thật = OWNER-NOTES). `calibrate.py` chạy end-to-end trên log gate nhưng **không tuyên bố số `mu`** (1 người dùng, mọi ô prior; DOC/46 §D.2).
- **D-GMA (spike chỉ đọc; lịch sử, đã được D-GMA2/D16-230 thay thế — xem §4 2.3.0 và 2.4.0):** **no-go cho 2.0.0** — Meta bidding trên Next-Gen đạt nhưng không drop-in (MAX google-adapter không cùng tồn tại, callback nền, hook Full2 cỡ L); GMA Next-Gen dời sang **2.1.0** sau tách module (C2).
- **Giới hạn carry 2.0.0:** `AdsMobPlNtFull` còn 3 chỗ placement theo slot (D48, tách file ở C1); keep/dontwarn cho unity/applovin; registry log hiện diện cả tập đóng; tooling cho rwi chặng 2–3 và `remove_ad`; billing T11 m3 (HTTP 400/401).

### 2.0.0 — "Tách module tuỳ chọn, chốt API" (L) — **phát hành 2026-10-02** (`v2.0.0` = `3dc4d06`; số đo từng lane và Task R: DOC/65 §3 "Số đo 2.0.0", plan `docs/superpowers/plans/2026-10-02-sdk-2.0.0-plan.md`)

Theo DOC/65 Phase C (phạm vi chốt 2026-09-29) và các quyết định D1–D28-2 của plan. **Xong khi:** H1, H3, H4, H9, H10, H11.

| Nhóm | Việc | Thực hiện ở 2.0.0 |
|---|---|---|
| C1 | `AdsHelper` ≤ 800 dòng/file | **`AdsHelper` đã tách ở 1.5.0; 2.0.0 = seams** (D1): `AdsMobPlNtFull` 791 → 581; seam core → MAX (`MaxBridge`, dò tên) và core → game (`RcSectionRegistry`, `BrainSignalListener`, `GameModule`); paid/click của native full (3 site) và impression/dismiss (27 chỗ) mang placement của lượt (ngoại lệ: dismiss/rewarded của rwi, `AdsMobPlRwi:162/164`); cổng thiết bị IG-C1 trên 37 + 26 PASS trước C2 |
| C2 | Module tuỳ chọn | `appplugin-max` và `appplugin-game` là artifact **phụ thuộc** (0 mã ở 2.0.0; D16: dời mã game cần `-Xfriend-paths`, làm ở 2.1.0 theo hướng SPI); ba artifact cùng bản, POM `appplugin` bớt 13 phụ thuộc game; app tiện ích chỉ khai `appplugin` (H1) |
| C3 | Chốt API | `explicitApi()` (1094 sửa) + `resourcePrefix = "appplugin_"` (96 tên); callback GMA vào main qua một poster; **một commit phá vỡ**: 25 kiểu + 3 thành viên `internal`, H9 202/275 = 73,5 %, baseline façade + `public_types` mới; `apiCheck` chặn thay đổi thử là bằng chứng của controller sau CI xanh |
| (hoãn) | Adapter GMA Next-Gen | dời sang **2.1.0** (D-GMA, D47 1.9.0): Meta bidding trên Next-Gen không drop-in — *lịch sử, đã thay thế bằng D-GMA2 (D16-230): go có điều kiện, 2.4.0* |
| Docs (H10) | Tài liệu ghép | `README.md` mục 3 (các bước), `DOC/61` mục G (bảng resource + khoá RC có schema + version), bảng gộp "Phải làm khi nâng cấp" 1.3.0 → 2.0.0 trong `CHANGELOG.md` |

### 2.1.0 — "Dời mã game, driver bền, hiệu năng đo được, bảo mật bậc A" (L) — **phát hành 2026-10-03** (`v2.1.0` = `a7606c0`; plan `docs/superpowers/plans/2026-10-03-sdk-2.1.0-plan.md`, số đo từng lane: DOC/65 §3 "Số đo 2.1.0", "Hiệu năng")

Mở sau 2.0.0 theo chỉ đạo chủ dự án 2026-10-02 (hiệu năng đo trên mọi máy, chống tách SDK dùng lại) và carry list 2.0.0. **Xong khi:** H1/H3/H4 như 2.0.0, H9 ≥ 70 % (cộng module), **PERF** bảng 6 máy trong DOC/65 + release notes với mọi hàng `hard` PASS, **SEC-A** hợp lệ/sai token chứng minh trên 5570 + Redmi, SEC-C watermark + strip tuỳ chọn, 0 dòng minors-200 mở, F1 YES, F3 PASS, `v2.1.0` publish. Không nằm trong 2.1.0: `-max` option A và GMA Next-Gen (D3, owner-gated), AAR-level R8 (D4, chỉ đo), tích hợp Play Integrity thật (O3).

| Nhóm | Việc | Thực hiện ở 2.1.0 |
|---|---|---|
| G | Dời mã game (D1/D7/D8) | **một commit phá vỡ ABI** (`e2ed528`): `PurchaseRouter`, `BattlePass`, `RecurringGrants`, `OfferwallManager`, `CrmBridge`, `GameModule` sang `appplugin-game` (FQN giữ); SPI nội bộ `@AppPluginInternalApi` (3 điểm), không friend-path trong mã phát hành; dump gốc 5605 → 5224 (− 394, + 13), `api/appplugin-game.api` 389; façade BASELINE = chính commit G1 (G1-2); **hành vi: app tiện ích không còn chạy RC game/CRM nếu không thêm `appplugin-game`** (DOC/61 G.3) |
| SEC | Bậc A: khoá giấy phép (D2/D6/D14) | token `ALP1.…` Ed25519 trong meta-data `com.app.plugin.LICENSE` (gói + cert ký + hạn + tính năng), xác minh offline một lần mỗi process, RFC 8032 Kotlin thuần, 0 phụ thuộc mới; không token → không quảng cáo, không brain; công cụ `tools/license_issue.py` (khoá riêng chỉ ngoài repo); kid `dev1` là khoá phát hành thực tế tới khi chủ dự án tạo `k1` (O5) |
| SEC | Bậc B: `/policy` (BE2) | backend trả policy theo app, `X-Integrity-Token` tuỳ chọn, verdict giả trong test, fail-closed về mặc định; bậc Google thật chờ O3 và một task ràng buộc nonce/package/`app_id`, rate limit (`sec-gioi-han`) |
| SEC | Bậc C-lite (SC1; D4/D6) | dấu vết `appplugin/<phiên bản>/<sha>` trong dex + `sdk_health.build`; rule bỏ log SDK là tuỳ chọn của app (`docs/integration/appplugin-strip-logs.pro`) |
| PERF | Đo hiệu năng (T0f, PERF-B, Task R #23–#27) | `tools/perf_gate.sh` + `perf_report.py`: ≥ 5 lượt cold, onCreate/initPlugin, request → fill theo format, fill rate, show latency, PSS; ngưỡng cứng chỉ trên máy thật (baseline v2.0.0 ×1,2, D5); emulator và PSS là thông tin (baseline cho 2.2.0); bảng ở DOC/65 "Hiệu năng" |
| P | Sửa nhỏ | P1 prefs lần đầu chậm không chặn main ngoài consent; P2 KDoc/LIMITS/test (minors 2.0.0); P3 click và rwi muộn theo lượt (census 31 site `turnPl`) |
| T0 | Công cụ bền | driver consent MIUI (T0b), One UI + intent `drive` (T0c), lớp INVALID môi trường + cửa sổ iap + quiet-wait (T0d), dump/façade/H9 theo module (T0g), `lane_pack` id chữ (T0a); T0e gia cố test công cụ chạy song song |
| BE | Backend (BE1, BE2) | view `v_show_rate_reasons` (13 view, báo cáo ngày 12 bảng), nạp sink G12; `/policy` |
| Docs (H10) | Tài liệu ghép | `README.md` (token giấy phép mục 9, `appplugin-game` mang mã), `DOC/61` G.1/G.3, bảng gộp "Phải làm khi nâng cấp" 1.3.0 → 2.0.0 (`[2.0.0]`) và 2.0.0 → 2.1.0 (`[Unreleased]`) trong `CHANGELOG.md` |

### 2.2.0 — "Hoàn thiện cuối: segment vào core, minSdk 24, hướng dẫn tích hợp kiểm chứng" (L) — **phát hành 2026-10-05** (`v2.2.0` = `2ccd17e`; plan `docs/superpowers/plans/2026-10-04-sdk-2.2.0-plan.md`; nhánh `feat/2.2.0`)

Mở sau 2.1.0 (`v2.1.0` = `a7606c0`, BASELINE ABI add-only). Nội dung, "xong khi" và quyết định: plan trên (Task T0a–T0d, PERF2, BE3, SECB, MIN24, LIC, PSH, SEG, IG, IG-V, R); số đo: DOC/65 §3 "Hiệu năng (2.2.0)" (từ `metrics-220.md`). Tóm tắt Task R: gate emu37/33/28/26 + Redmi PASS (FATAL/ANR/native 0, by-sdk 0, license VALID 1/process, segment init 1), release_gate utility/game/licbad PASS, lint `NewApi` 0, AAR minSdk 24 ×3, H9 70,9 % (212/299), IG-V PASS. Chưa đo hoặc INVALID, theo nguyên nhân: Samsung (keyguard tự khoá), API 24 (không có image, O11), SQL parity 13 view + `stack_load` (Docker tắt), CI/`release.yml` (bước P). #32 game trên emu37 INVALID (billing unavailable) giống hệt APK 2.1.0: môi trường emulator; perf emu37 vòng show INVALID (ui) do host bận (số cold chỉ thông tin).

### 2.3.0 — "Đóng nợ tích hợp: tài liệu khớp mã, brain gọi `/policy` thật, preload mọi định dạng" (L) — **phát hành 2026-10-05** (`v2.3.0` = `82c22cd`, release 12:43Z; plan `docs/superpowers/plans/2026-10-05-sdk-2.3.0-plan.md`; nhánh `feat/2.3.0`; BASELINE ABI = `v2.2.0` = `2ccd17e`)

**Trạng thái: 2.3.0 phát hành 2026-10-05 (`v2.3.0` = `82c22cd`, release 12:43Z)** (số đo đủ 42 hàng ở `v230/metrics-230.md`; CHANGELOG `[2.3.0] - 2026-10-05`) (đầu `feat/2.3.0` = `75597c1` lúc đo E1). Đã land: ROB3 (`RemoveAdsMask` public, checker §9), MINR (ROB-4 tên event <= 40, R7b, perf min_n), SECBS (rate limit hai bucket, `BRAIN_TRUST_PROXY_HOPS`), SECBC (`/policy` client, mặc định tắt), SECBH (`device_gate.sh --policy`), PRELOAD (nạp sẵn mọi slot cấu hình sau consent + VALID), R0 (`sdkVersion` 2.3.0, demo 5 slot), IG2 (INTEGRATION.md + checker; đang land). Quyết định: `v230/ceo-decisions.md` (D1–D35-230).

Bằng chứng tới nay (số đo đầy đủ: `v230/metrics-230.md`; bảng PERF: DOC/65 khi P-docs): E2 APK READY `c1903247…` (release_gate utility/game/licbad PASS, AAR minSdk 24 ×3); E1 unit 2090 ×3 (root 1980 + game 110, 0 fail), dump root 5806 / game 389 (`-` ngoài `access$` = 0), façade + D7 PASS, H9 71,8 % (224/312); E-BE `pytest` 383 passed + 1 skipped (367 + 16); E-real Redmi `65c9b673` PASS (có ghi chú D33/D34/D35: #41 5/5 slot nạp, #13 StrictMode MIUI, #42 app open resume chưa đo vì cờ app); E-IM2 PASS (nhánh IM `ff40b65`, build + unit `:app` 34); SECBH smoke 5570 L1–L4 PASS. E-emu 37/33/28/26 PASS (#9 #10 #11 #13 #14 #15 #22 #29 #32 #41 #42; IAP by-sdk 0 trên 4 emulator xác nhận StrictMode MIUI là môi trường; preload 5/5 slot theo D33; perf emu37/33 INVALID do tải host, emu28/26 hợp lệ); lint `NewApi` 0 ×3; CI run 37272102199 trên 66e6e6e success; unit 2090 ×3, runner 354/354 sau IG2. Chưa đo: Samsung (vắng), parity 13 view + `stack_load` (Docker tắt), API 24 (O11), app open resume (cờ app, D35), CI HEAD cuối + `release.yml` (bước P).

**SPIKE GMA Next-Gen (vòng 2): GO có điều kiện (D16-230)** — `v230/gma-nextgen-spike-2.md`: (a) 9/9 định dạng có nguồn; (b) adapter MAX Next-Gen chưa phát hành (PR #952/#927 mở); (c) ước cỡ L. Di trú ở **2.4.0**, không có task NG trong 2.3.0 (D9-230).

### 2.4.0 — "GMA Next-Gen làm artifact tách `appplugin-nextgen`" (L) — **Task R đã chốt (R0 = `18fbaf9`); PR #18 sẵn sàng; tag + release local (D33), publish Packages cần PAT (OWNER 19)** (plan `docs/superpowers/plans/2026-10-05-sdk-2.4.0-plan.md`; brief `v230/ng-brief-2.4.0.md`; nhánh `feat/2.4.0`; BASELINE ABI = `v2.3.0` = `82c22cd`)

Artifact tách `com.app.plugin:appplugin-nextgen`; `appplugin` (Legacy 25.4.0), `appplugin-game`, `appplugin-max` không đổi hành vi/ABI (dump Δ 0). Một app chọn một: `appplugin` hoặc `appplugin-nextgen`. **`appplugin-max` = legacy-only** (không có adapter MAX Next-Gen phát hành; PR AppLovin #952/#927 còn mở); **MAX + Next-Gen không hỗ trợ**. Thông báo deprecation đường GMA Legacy: mốc **2027-06-30** (v25.x), sunset 2028-06-30. Cây việc NG0–NG6 và rủi ro: brief. **Đã giao (land ở `feat/2.4.0`, R0 `sdkVersion` 2.4.0):** bốn artifact (`appplugin`, `appplugin-game`, `appplugin-max`, `appplugin-nextgen`; AAR cả bốn minSdk 24); `appplugin-nextgen` chạy GMA Next-Gen `ads-mobile-sdk` 1.5.0 cho đủ định dạng (init + `MainPost`, banner/rect/collapsible, interstitial/app open/rewarded/rwi, native `nt_`/NtCl, Full2), cắt hết nhánh Legacy (AAR nextgen 0 tham chiếu `com/google/android/gms/ads/`), dump + parity riêng, `consumer_check.sh` kiểm toạ độ tiêu thụ; demo có flavor `nextgen` và `ab_compare.py` so telemetry Legacy với Next-Gen; carry: CARRY-ADS (rewarded nạp lại placement vừa hiện, nối callback khi slot đang nạp, dòng skip có placement), CARRY-POL (`/policy` chỉ khi `brainEnabled` + rollout phía client, https-only, back-off 24 h, FCM hỏi lại), CARRY-T/DEMO-RES (công cụ cổng, nút app open resume, driver Full2), IG3 (INTEGRATION.md 4 artifact, mục 16–17). Số đo (số liệu đã có: unit root 2010 + game 110 = 2120, dump root 5806 / game 389 / nextgen 6113, parity 431/456 khối y hệt): DOC/65 §3 "Số đo 2.4.0" (phần còn lại điền ở E5 pha B). **`appplugin-nextgen` 2.4.0 = preview, chưa dùng production (D26-240):** `ads-mobile-sdk` 1.5.0 đệm cả body media creative toàn màn hình trong heap không trần (`ads_mobile_sdk.r60`; creative video itag 22 khoảng 34 phút, không `range=`) → `OutOfMemoryError` 134–268 MB trên Samsung A25 ở process chỉ nạp sẵn; lỗi nằm ở SDK Google, không có đường sửa phía ta; chờ bản `ads-mobile-sdk` > 1.5.0; Legacy mặc định không đổi, tag không bị chặn. Hệ quả: 2.3.0 đã phát hành 2026-10-05; 2.4.0 là bản có nội dung duy nhất còn lại, sau đó chỉ bảo trì (chương trình không đổi).

### 2.4.1 — "HOSTFIX: nhận Activity chủ cho app có `applicationIdSuffix`/flavor `applicationId`" (S) — bản vá sau 2.4.0 (D36-240, D37-241; plan `docs/superpowers/plans/2026-10-06-sdk-2.4.1-plan.md`; nhánh `feat/2.4.1`)

Host Activity nhận khi không thuộc 11 tiền tố SDK quảng cáo/mediation (danh sách ở CHANGELOG [2.4.1]) và (tên lớp bắt đầu bằng `packageName` (luật cũ, giữ) hoặc là launcher Activity của `packageName` hoặc cùng package với lớp `Application`); trước đó app có suffix/flavor id (IM debug `.debug`) không có host nên Full2 nối bị huỷ sau 3 s. Phạm vi tối thiểu: HOSTFIX + tài liệu + R-light; minors công cụ (#29–#34) để sau; không đổi API công khai. Số đo: DOC/65 §3 "Số đo 2.4.1" (R-light 2026-10-07, PASS #1–#7).

### 2.4.2 — "Ghép Speaker Clean: thời điểm cố định, công tắc placement, slot hiển thị, referrer, xoá dữ liệu" (M) — **Task R đã chốt @3131631 (hàng 1–11, 13 PASS; hàng 12 SPLASH chưa đo, JVM SP1–SP3); chờ tag + release local (D33/D35)** (plan `docs/superpowers/plans/2026-10-07-sdk-2.4.2-plan.md`; nhánh `feat/2.4.2`; mốc so dump `git diff v2.4.1 -- api/` = `v2.4.1` = `ccdd471`; mốc façade `BASELINE_ROOT` = commit dump 2.4.2; **API chỉ thêm**)

Các sửa chung để Speaker Clean ghép SDK không hồi quy (D1–D6-SC: 2.4.2 trước, cổng SC sau khi RELEASED): FIXED (+INTRO3, Full2 theo placement được gọi, slot Full2 3 bậc), SWITCH (`cf_ads_placement` `flag:-1` tương đương `PlacementSwitches`; `screen_switch_interval` ở lại app), SLOT, SPLASH (`full_splash` miễn guardrail; mặc định cục bộ `ads_cf_seg_cadence_guardrail_seconds`, SC 60), REFERRER (tự ghi sau consent), CLEAR (`MonetizationSdk.clearLocalData`, giữ quyền đã mua). Ngoài phạm vi (→ 2.5.0): log adapter, unit mẫu debug, Meta probe, `whenConsentFlowDone` public. Số đo: DOC/65 §3 "Số đo 2.4.2".

### Cổng cuối — "App đầu tiên" (chủ dự án bấm nút, không thuộc 5 bản trên)

Ghép SDK (2.1.0 trở đi; cần token giấy phép cho `applicationId` + cert ký phát hành) vào một app (đề xuất Invoice Maker: eCPM cao nhất, phép thử thật nhất). Đây là nơi duy nhất
trả lời "eCPM/revenue cao hơn đối thủ": bảng 1.7.0 chạy trên người dùng thật ≥ 28 ngày, A/B bidding
partners (1.9.0 #1) và brain holdout (1.9.0 #5) có số, so với AdMob report và benchmark eCPM công khai theo
quốc gia/định dạng. Trước cổng này, mọi tuyên bố về eCPM đều là "sẵn sàng đo", không phải "đã hơn".

---

## 5. Quyết định chủ dự án cần chốt (theo thứ tự cần)

| # | Câu hỏi | Đề xuất | Chặn bản |
|---|---|---|---|
| Q1 | #11 Samsung 1.6.3: A (phân loại do OS, dời bind sang 1.6.4) hay B (sửa ngay, build lại, đo lại) | **A** | 1.6.3 |
| Q2 | N1: event `logevent` sau `DENIED` ở EEA — gửi tiếp (hiện tại) hay giữ/không gửi | **Không gửi** cho tới khi tầng đổi | 1.6.4 |
| Q3 | Demo `utility` lên **Play internal testing** với ad unit thật + Play test account (cần app trên Play Console, AdMob app id thật) — không có thì H6b, H7 không đo được | **Có** | 1.7.0, 1.8.0 |
| Q4 | Backend production: GCP project, Cloud Run + BigQuery, ngân sách (ước < 20 USD/tháng ở DAU demo; tăng theo DAU app) | **Có**, cùng project Firebase demo | 1.7.0 |
| Q5 | Mở rộng bidding partners cho app tiện ích ngoài AdMob + Meta (đổi DOC/62 B từ "cố định" sang "cấu hình được, mặc định Meta") | **Có**, mặc định tắt, chỉ A/B | 1.9.0 |
| Q6 | Thêm định dạng rewarded interstitial | **Có** | 1.9.0 |
| Q7 | 20 lớp public "chủ dự án xác nhận" chuyển `internal` ở 2.0.0 (cần cho `internal` ≥ 70 %) | **Có** trừ lớp app đang gọi | 2.0.0 |
| Q8 | Ngân sách quota: 1.7.0–1.9.0 là 3 bản L/M liên tiếp; giữ luật ≤ 2 Opus song song, Fable chỉ F1 + review kiến trúc | giữ | — |

## 6. Rủi ro của kế hoạch

| # | Rủi ro | Chặn |
|---|---|---|
| R1 | "Hoàn thiện rồi mới ghép" đẩy bằng chứng eCPM ra sau 5 bản; nếu ghép xong mới thấy lệch thì sửa muộn | Cổng cuối định nghĩa sẵn; 1.7.0 làm **trước** IAP để mọi bản sau có số; demo `utility` ở internal testing là "app giả" gần nhất |
| R2 | Test ads trả giá 0 → đối soát doanh thu trên demo không có ý nghĩa | Q3: ad unit thật ở internal testing, lưu lượng nhỏ |
| R3 | Chi phí BigQuery tăng theo DAU khi ghép app | 1.7.0 #8 sampling + kill switch; retention |
| R4 | 3 bản L/M liên tiếp làm phình file mới quá trần G8 | Mỗi plan có bảng cỡ file mục tiêu trước khi code (`SdkFileSizeGateTest`) |
| R5 | Bidding partner thêm vào làm tăng kích thước và bề mặt crash cho app tiện ích | Mặc định tắt; adapter `compileOnly`/flavor; cổng R8 + monkey trên `utility` với từng partner bật |
| R6 | Brain calibrate trên dữ liệu demo (ít, không đại diện) | Chỉ chứng minh **đường ống** calibrate; `mu` thật lấy sau app đầu tiên; brain vẫn "chỉ siết" |
| R7 | Backend là Python một người viết, chưa có test tải | 1.7.0 #4 có test tải nhỏ (k6/locust) + healthcheck trong CI |

## 7. Việc không làm (nhắc lại để không trôi)

Game (MAX cổng thiết bị, BattlePass, RecurringGrants, PurchaseRouter, offerwall, crm), iOS, Control Plane,
D2P/web shop, ký config Ed25519, DexGuard, sửa app hiện tại, tách `ads-gma`/`ads-meta`, `Config.Builder`
(xét lúc ghép app). **GMA Next-Gen** (D16-230, thay D-GMA no-go cũ): làm ở 2.4.0 như artifact tách; `appplugin-max` giữ legacy-only (không có adapter MAX Next-Gen phát hành), MAX + Next-Gen không hỗ trợ; artifact `appplugin-nextgen` ở 2.4.0 là **preview, chưa dùng production** (OOM `ads-mobile-sdk` 1.5.0, D26-240; chờ Google sửa; Invoice Maker/Speaker Clean chưa chuyển nextgen); deprecation đường Legacy 2027-06-30, sunset 2028-06-30 (v25.x, `v230/gma-nextgen-spike-2.md` §8 [S6]; `appplugin-max` legacy-only nên chịu cùng mốc). Danh sách đầy đủ và lý do: DOC/65 mục 5 "Quyết định phạm vi 2026-09-29".
