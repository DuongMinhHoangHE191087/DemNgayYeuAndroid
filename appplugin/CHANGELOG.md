# Changelog

Mọi thay đổi đáng kể của `com.app.plugin:appplugin`. Định dạng theo
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), đánh số theo
[Semantic Versioning](https://semver.org/). Bản đã publish không bao giờ bị ghi đè: sửa lỗi là một
số hiệu mới.

## [2.5.1] - 2026-10-11

"2.5.1 — placement trên `ad_impression` của Firebase, event placement cho AdMob đã liên kết, chuẩn hoá `ads_cf_native_no_reload`" (**API công khai không đổi**: dump root và nextgen mỗi file chỉ `+` 9 dòng, không dòng `-`: khối internal mới `com.app.plugin.firbase.PlacementPaidEvent` (8 dòng) và một getter `getAD_PLACEMENT_IMPRESSION$appplugin` của `EventNames`; game 0. `BASELINE_ROOT` giữ `d03bdb3`. Plan `docs/superpowers/plans/2026-10-10-sdk-2.5.1-plan.md`.)

### Thêm

<!-- placement -->
- PLACEMENT: event Firebase `ad_impression` do SDK ghi (mọi mạng, trừ AdMob khi `ads_admob_firebase_linked` = 1) mang thêm param `ad_placement` = placement đang hiện (placement gọi show, lùi về slot khi rỗng; cắt ở 100 ký tự; không đặt khi cả hai rỗng) (`PaidEvent.kt:81,113`; nextgen `:104,136`). Cùng giá trị với `placement` của event server `ad_show`/`ad_revenue` (Firebase cắt ở 100 ký tự). Đường v1 `FirHelper.logAdRev*` uỷ quyền `PaidEvent` nên cũng có. Không đổi `value`, `currency`, `imp_id`.
<!-- plevt -->
- PLEVT: event Firebase `ad_placement_impression` cho app AdMob đã liên kết Firebase (`ads_admob_firebase_linked` = 1: SDK không ghi `ad_impression` cho AdMob; doanh thu AdMob trên GA4 chỉ còn ở `_ai` do GMA ghi, không có placement). Bật bằng resource `@integer/ads_cf_placement_event` = 1, **mặc định tắt (vắng, khác 1 hoặc đọc lỗi = 2.5.0), chỉ resource: Remote Config `ads_cf_overrides` không đặt được** (allowlist vẫn 4 tên, `AdsCfOverrides.kt:31-36`). Đọc một lần ở `initPlugin` (`AppPluginManager.kt:40`). Param: `ad_placement`, `ad_unit_name`, `ad_format`, `ad_source`, `ad_platform`, `imp_id`, `ad_value`, `currency`; **không có `value`** nên GA4 không cộng doanh thu và không đếm đôi với `_ai` (`PlacementPaidEvent.kt:35-44`). Chỉ ghi khi `ad_impression` của SDK bị chặn; MAX luôn ra `ad_impression`, không ra event này.

### Sửa

<!-- plmnorm -->
- PLMNORM: `ads_cf_native_no_reload` (2.5.0) chuẩn hoá mục thiếu tiền tố `nt_` thành `nt_<mục>` (như `showNative`) kèm một `logE` mỗi mục mỗi process; trước đó mục như `language` không khớp placement nào và không log. Lỗi đọc resource ⇒ một `logE` và dùng 3 placement cũ (trước đó nuốt lỗi im lặng) (`NativeController.kt:243-253`). Vắng vẫn = 3 placement cũ, có mặt kể cả rỗng vẫn = đúng danh sách đã khai.

### Nội bộ

<!-- b2flaky -->
- B2FLAKY: test `IapPlayRulesTest` B2 chờ hai bản bền (`iap_purchase`, `iap_result`) rời đĩa trước khi đóng server mỗi vòng, hết chập chờn; assertion giữ nguyên. Chỉ test.
<!-- parityx -->
- PARITYX: `tools/api_parity_check.sh` có bit thực thi (100644 → 100755), gọi thẳng `./tools/api_parity_check.sh` được; nội dung script không đổi.
- Ràng buộc H11: `AdsHelper`, `AdsFrequencyGate`, `AppPluginBase`, `AdsCfOverrides` không đổi so với 2.5.0.

### Phải làm khi nâng cấp

Không bắt buộc. (1) Muốn báo cáo eCPM theo placement: đăng ký custom dimension `ad_placement` (phạm vi event) trong GA4. (2) App đã liên kết AdMob ↔ Firebase, muốn placement cho doanh thu AdMob: khai `@integer/ads_admob_firebase_linked` = 1 **chỉ khi console xác nhận liên kết** (AdMob console hoặc GA4 Publisher ads có doanh thu AdMob; dòng log `origin=am,name=ad_impression(_ai)` chỉ chứng GMA ghi `_ai` trên máy, không chứng liên kết, và đặt 1 khi chưa liên kết làm doanh thu AdMob mất hẳn khỏi Firebase), khai thêm `@integer/ads_cf_placement_event` = 1 (chỉ resource), và đăng ký custom metric `ad_value` cùng custom dimension `ad_placement`. (3) App đang khai `ads_cf_native_no_reload` với mục thiếu `nt_`: mục đó nay có tác dụng (và `logE`); sửa thành tên đầy đủ nếu không muốn.

## [2.5.0] - 2026-10-09

"2.5.0 — ưu tiên lượt có show chờ, siết đặt chỗ theo lượt, công tắc lùi qua Remote Config, open ad không đè quảng cáo toàn màn hình" (**API công khai chỉ thêm một dòng**: `AdsHelper.setAutoShowOpenAdOnForeground(boolean)`; dump root và nextgen có 22 dòng `+` (một dòng public `AdsHelper`, ba dòng `…$appplugin` internal của `AdsHelper` và `NativeMediaRatio`, khối internal mới `com.app.plugin.ads.AdsCfOverrides`, thành viên internal của `UnitGate` và `NativeMediaRatio`) và đúng 1 dòng `-`: constructor internal `UnitGate$Waiter` (đổi chữ ký; `UnitGate` mới từ 2.4.4, ngoài baseline façade); game 0. Plan `docs/superpowers/plans/2026-10-09-sdk-2.5.0-plan.md`.) Mốc ABI của `tools/api_facade_check.sh` (`BASELINE_ROOT`) dời từ `8f9539c` (commit dump 2.4.2) sang `d03bdb3` (commit dump 2.5.0, như D27-210): API chỉ thêm một dòng đã được chủ dự án duyệt (D1-250); `BASELINE_GAME` giữ `82c22cd`.

### Đổi mặc định

<!-- prio -->
- PRIO: cổng một request mỗi ad unit (`core/UnitGate.kt`) xếp lượt theo **mức**: SHOW (có show đang chờ: view native chờ gắn, hoặc Full2 của quảng cáo cha sắp hiện) > APP (mặc định) > PRELOAD (placement `full_2_*` và lượt do `AdPreloader` mở). Hàng chờ mỗi unit nhả người chờ sống mức cao nhất, cùng mức theo thứ tự đến; mức đọc lúc quyết (`UnitGate.kt:71`). Một lượt mức cao gặp unit đang bị lượt slot khác mức thấp hơn giữ mà lượt đó **chưa gửi id** (đang chờ khởi tạo hoặc consent) thì đi ngay, lượt cũ vào lại cổng khi hẹn của nó chạy ("nhường"); lượt đã gửi id không bao giờ bị giành, và không chia một quảng cáo giữa hai slot. Waiter PRELOAD chạm trần 10 s **vẫn đi tiếp như 2.4.4** (`UnitGate.kt:178`). Log mới: `load yields unit to <placement>: <placement> slot=<originPl> unit=<id>` (`UnitGate.kt:127`). **Công tắc lùi:** `ads_cf_unit_gate_prio_off` = 1 (resource số nguyên hoặc RC `ads_cf_overrides`) ⇒ không mức, không nhường (FIFO như 2.4.4); đọc một lần mỗi process.
<!-- opengap -->
- LAM2: app open (placement khác `full_splash`, vẫn miễn) không hiện trong `openAdDeltaTime` (mặc định 15 s) sau **bất kỳ** quảng cáo toàn màn hình: interstitial, Full2, rewarded, rewarded interstitial; trước 2.5.0 chỉ tính từ open ad trước. Mốc = `max(openTimeShow, fullTimeShow, full2TimeShow, giftTimeShow)` (`AdsFrequencyGate.kt:114-122`). **Công tắc lùi:** `ads_cf_open_gap_own_only` = 1 (resource hoặc RC; đọc mỗi lần quyết) về hành vi 2.4.4. Cần biết: ca mở app 23 s sau Full2 vẫn qua; app có cooldown riêng (ví dụ 30 s) giữ nguyên hoặc nâng `openDeltatime` (giây, trường trong JSON RC `ads_region_android`).

### Thêm

<!-- lam1 -->
- LAM1: `AdsHelper.setAutoShowOpenAdOnForeground(enabled: Boolean)` (`AdsHelper.kt:598`, mặc định `true` = 2.4.4). `false` chỉ tắt lượt **SDK tự hẹn** `showOpenAd(full_resume)` 1 s sau mỗi lần vào foreground (trừ lần đầu; cờ `setFlag4ShowResume` tắt thì lượt này bị chặn `open_flag_resume`, `false` ở đây bỏ cả event show-fail đó; giá trị chỉ nằm trong RAM, gọi mỗi lần khởi động) (`AppPluginBase.kt:226-227`, kiểm cả lúc hẹn và lúc chạy: tắt trong 1 s cũng không hiện; bỏ lượt ghi `ads open ad auto-show on foreground skipped (app)`); app vẫn gọi `showOpenAd` được. Khác `setFlag4ShowResume(false)`, vốn chặn **mọi** open ad không phải splash kể cả lượt app tự gọi.
<!-- rcsw -->
- RCSW: khoá Remote Config `ads_cf_overrides` (đối tượng **trong** JSON `ads_policy_android`) đặt bốn công tắc lùi mà không phát bản app: `ads_cf_unit_gate_off` (0–1), `ads_cf_unit_gate_prio_off` (0–1), `ads_cf_native_media_ratio` (0–3), `ads_cf_open_gap_own_only` (0–1) (`AdsCfOverrides.kt:27-36`). Tên ngoài allowlist, giá trị không phải số nguyên hay ngoài khoảng bị bỏ riêng tên đó kèm `logE`; khoá trong một tier `by_tier` bị bỏ cả khối tier kèm `logE`. **Khoá RC có mặt thắng resource app; bỏ khoá để về resource; máy offline giữ bản lưu cuối.** RC chỉ lùi về hành vi cũ hoặc chọn trong enum có sẵn (`ads_cf_native_media_ratio` 0–3); JSON hỏng hoặc `schema_version` lạ ⇒ bỏ mọi override. Hiệu lực: `unit_gate_off` và `unit_gate_prio_off` ở lần mở sau (cổng chốt một lần mỗi process), `native_media_ratio` và `open_gap_own_only` ngay. API công khai không đổi (`AdsPolicy` không thêm trường). Một dòng `ads cf overrides: <tên>=<giá trị>[, …]` khi áp khác rỗng; bảng ở `INTEGRATION.md` mục 10.
<!-- plmn -->
- PLMN: resource chuỗi `ads_cf_native_no_reload` (CSV tên placement đầy đủ, ví dụ `nt_language`) thay danh sách cứng placement native không nạp lại khi `showNative` chưa có quảng cáo (`NativeController.kt:243-246`). **Vắng = 3 placement cũ** (`nt_language`, `nt_language_2`, `nt_intro`); **có mặt (kể cả rỗng) = đúng danh sách đã khai**. Đọc một lần mỗi process.

### Sửa

<!-- turnkey -->
- TURNKEY: siết hợp đồng đặt chỗ của cổng unit (**phòng ngừa**; đường cũ bị `isStaleLoadCallback` chặn): ngoài khớp `originPl`, đặt chỗ chỉ nhả khi lượt đang giữ đã **gửi** id đó và còn sống (`UnitGate.kt:156`), nên kết quả của lượt khác cùng slot không nhả nhầm. Không có đường quan sát được dẫn tới nhả nhầm trong 2.4.4 (callback muộn bị `isStaleLoadCallback` chặn trước telemetry ở 16/16 lớp mạng trong cổng); thay đổi cần vì PRIO tạo đặt chỗ chưa gửi thường xuyên. Lùi theo `ads_cf_unit_gate_off`.
<!-- plmm -->
- PLMM: layout native của app thiếu `ad_media` hoặc khai khác kiểu `MediaView` không còn crash (`as? MediaView`; Next-Gen `MediaView?`): không gắn media, một `logE` mỗi process nêu layout id và "GMA yêu cầu MediaView" (`NativeMediaRatio.kt:54`). Binder Full2 dùng layout của SDK nên ngoài phạm vi.
<!-- ngmap -->
- NGMAP: Next-Gen bỏ hai khối `when` lặp ở `AdsMobPlNt` và `AdsMobPlNtFull`, dùng chung `NativeMediaRatio.pick`; hành vi không đổi.

### Giới hạn còn lại

- NtCl và banner ngoài cổng **vẫn tính backoff** (`AdsTelemetry.kt:118`): GMA đếm "Too many recently failed requests" theo ad unit, không theo định dạng, nên chờ 2 s sau lỗi NtCl là đúng; giữ nguyên có chủ đích, không đổi mã.
- NATIVEPOOL (nhiều native song song cùng unit) **hoãn**: song song cùng unit chính là request chồng mà INFLIGHT loại; mở lại khi đo PRIO cho trung vị `released after` của `nt_*` lớn hơn 1 s trên ít nhất 3/5 lần cold start.

### Phải làm khi nâng cấp

Không bắt buộc. Hai mặc định mới có công tắc lùi: `ads_cf_unit_gate_prio_off` = 1 (PRIO) và `ads_cf_open_gap_own_only` = 1 (LAM2); cả hai, cùng `ads_cf_unit_gate_off` và `ads_cf_native_media_ratio`, đặt được qua RC `ads_cf_overrides`. Cần biết: (1) khoá RC có mặt **đè** resource app; nếu app đã đặt `ads_cf_unit_gate_off` = 1 thì đừng gửi khoá đó với giá trị 0 trong RC; (2) RC chỉ lùi về hành vi cũ hoặc chọn trong enum có sẵn, muốn bật tính năng mới vẫn phải nâng SDK; (3) app muốn SDK thôi tự hiện open ad lúc foreground gọi `AdsHelper.instance.setAutoShowOpenAdOnForeground(false)`; (4) `ads_cf_native_no_reload` không khai thì giữ 3 placement cũ, khai rỗng nghĩa là mọi native đều nạp lại. Tài liệu: `INTEGRATION.md` thêm Dagger/Hilt trước 2.60 + kapt (ép `kotlin-metadata-jvm` lên 2.3.0), ghi rõ SDK tự hẹn `full_resume` mỗi lần vào foreground, `full_resume` cần khoá riêng trong `admob_openad` (placement ngăn bằng `#`), hàng nâng cấp "từ bản < 1.2.0".

## [2.4.4] - 2026-10-09

"2.4.4 — một request mỗi ad unit, native xin tỉ lệ ANY, load AdMob chạy ngay trong callback khởi tạo" (**API công khai không đổi**: dump root và nextgen chỉ `+` ba lớp internal `com.app.plugin.ads.core.UnitGate` (kèm 4 lớp lồng private `$Fail`, `$Turn`, `$TurnCb`, `$Waiter`), `com.app.plugin.ads.NativeMediaRatio`, `com.app.plugin.ads.MainFront`, không dòng `-`, game 0; `BASELINE_ROOT` giữ `8f9539c`).

### Đổi mặc định

<!-- inflight -->
- INFLIGHT: SDK không gửi request thứ hai tới một ad unit mà slot khác đang có request bay (ngoại lệ ở "Giới hạn còn lại" bên dưới). Khoá của cổng là **id thật đang xin**, từng id độc lập (`core/UnitGate.kt:53-77` `admit`, `:90` `onRequest`): lượt bắt đầu ở id đầu của chuỗi `;` chưa vào backoff; id đó mà slot khác đang bay thì lượt xếp hàng FIFO (`load merged into in-flight request`) và tự gửi request riêng khi lượt kia có kết quả đầu tiên; quảng cáo vẫn về đúng slot của lượt, không chia quảng cáo giữa các slot. Đặt chỗ đi theo id thật đang bay của lượt qua `AdsTelemetry.onAdLoadStart` (`core/AdsTelemetry.kt:62`), nên thang sàn eCPM `id_cao;id_all` (chuỗi `;` trong `cf_ids_placement` hoặc `ads_ids`) vẫn chuyển sang id kế ngay khi id trước lỗi, như 2.4.3. Chỉ lỗi nạp thật (`onAdLoadFail`, `AdsTelemetry.kt:118`) tính vào backoff `min(2 000 × 2^(n−1), 16 000)` ms tính từ lúc lỗi (n là số lỗi liên tiếp của unit đó, nạp thành công `:98` đặt n = 0); **hết thời gian chờ (timeout) không tính** và unit đó được thử lại ngay như 2.4.3, vì request đã hết hạn có thể còn ở phía mạng. Khi **mọi** id của lượt đều đang backoff: lượt của cùng slot và cùng placement với lần lỗi (vòng retry của helper hoặc của app) bị bỏ qua ngay, báo `onLoadFailed(..., "unit_backoff")` cho `AdsLoadInnerCb` của slot với 0 request (qua AdsHelper, app nhận lỗi cuối của helper là `list empty`; chỉ rewarded interstitial chuyển nguyên `unit_backoff`); lượt của slot hoặc placement khác chờ hết backoff, tối đa 10 s (`UnitGate.kt:23` `WAIT_CAP_MS`), rồi bỏ qua đặt chỗ của slot khác và gửi, bắt đầu từ id đầu không backoff (`firstFree`, `:80`; lượt này vẫn đặt chỗ cho id của chính nó, `AdsPlBase.kt:240`, `:259`); với backoff 16 s (n ≥ 4) slot khác chỉ chờ 10 s, và dòng `load released after 10000 ms` là trần chờ, không phải lỗi. Chống kẹt: đặt chỗ chỉ còn hiệu lực khi slot chủ còn đang nạp và lượt chưa bị thay; sai thì bỏ chỗ và gửi ngay như 2.4.3. Phạm vi: interstitial, app open, rewarded, rewarded interstitial, native, native full (Full2) của AdMob Legacy, Next-Gen và Meta trực tiếp; banner, rect, NtCl và MAX ngoài cổng (không bị chặn và không đặt chỗ, nhưng lỗi nạp của chúng vẫn tính backoff cho unit đó: native trong cổng dùng chung unit với NtCl vừa lỗi sẽ chờ tối đa 10 s). Giới hạn còn lại (có thể chồng): chờ quá 10 s thì gửi luôn (`load released after 10000 ms`); sau timeout, unit được thử lại ngay; chuỗi `;` rơi xuống một unit mà slot khác đang có request bay không được cổng chặn; NtCl/banner cùng unit nằm ngoài cổng. **Công tắc lùi:** resource số nguyên app `ads_cf_unit_gate_off` = 1 trả hành vi 2.4.3 nguyên vẹn (vắng hoặc 0 = bật). Dòng log mới: `load merged into in-flight request: <placement> slot=<slot> unit=<id>`, `load waits unit backoff <n> ms: …`, `load skipped unit backoff <n> ms: …`, `load released after <n> ms: …`.
<!-- aspect -->
- ASPECT: request native (Nt, NtCl, Full2; cả GMA Legacy lẫn Next-Gen) xin tỉ lệ media ANY (`MediaAspectRatio.ANY`) thay cho PORTRAIT/LANDSCAPE theo hướng màn hình, để creative Meta 1.91:1 và 1:1 không bị loại. **Công tắc:** resource số nguyên app `ads_cf_native_media_ratio`: vắng, 0 hoặc ngoài 1–3 = ANY (mặc định mới); 1 = theo hướng màn hình như 2.4.3; 2 = LANDSCAPE; 3 = PORTRAIT. Một dòng log mỗi process: `ads native media ratio: <ANY|LANDSCAPE|PORTRAIT|BY_ORIENTATION> (ads_cf_native_media_ratio=<raw>)`. MediaView trong layout native của SDK (small, cl, language_2, Full2 nhúng `native_layout_full`/`full_pager`) dùng cách co giãn mặc định của GMA; chỉ màn Full2 dạng dialog đặt `CENTER_INSIDE` (`AdsMobNtFullView.kt:131`). Next-Gen (PREVIEW) đổi cùng mặc định.

### Nhanh hơn

<!-- initcb -->
- INITCB: hàng load AdMob đang giữ chạy ngay trong callback khởi tạo của GMA (đã ở luồng main), không còn `post` xuống cuối hàng đợi sau các khung hình đang chờ (trước: khoảng 59–95 ms từ `complete after` đến `tryLoad` đầu). Next-Gen: message bất đồng bộ đặt ở đầu hàng đợi main. Hẹn an toàn 8 s và `ads_cf_admob_init_wait_ms` giữ nguyên. Không có dòng log mới.

### Phải làm khi nâng cấp

Không bắt buộc. Hai mặc định mới có công tắc lùi: `ads_cf_unit_gate_off` = 1 (INFLIGHT) và `ads_cf_native_media_ratio` = 1 (ASPECT). Cần biết: (1) slot thứ hai nạp cùng ad unit chậm bằng thời gian request trước, cộng thời gian chờ hết backoff nếu request đó lỗi; tổng thời gian chờ tối đa 10 s, quá thì gửi luôn; (2) retry cùng placement khi mọi id đang backoff (thường là app retry ngay trong `onLoadFailed`) không gửi request nào và thất bại ngay. Qua AdsHelper, app nhận lỗi cuối của helper (`list empty`; riêng rewarded interstitial nhận nguyên `unit_backoff`); `unit_backoff` chỉ hiện ở `AdsLoadInnerCb` của slot và ở log `load skipped unit backoff`. App retry trong callback lỗi phải chờ ít nhất 2 s (backoff tăng tới 16 s), không retry ngay; (3) thang sàn eCPM `id_cao;id_all` không cần đổi gì; (4) native nay có thể về ảnh 1.91:1 hoặc 1:1: app dùng layout riêng muốn ảnh không bị cắt tự gọi `MediaView.setImageScaleType(FIT_CENTER)` hoặc `CENTER_INSIDE` trên MediaView của mình; app dùng layout native của SDK không gọi được `setImageScaleType`, nếu thấy ảnh bị cắt thì đặt `ads_cf_native_media_ratio` = 1 (như 2.4.3); (5) dòng log mới như trên.

## [2.4.3] - 2026-10-09

"2.4.3 — mở app nhanh hơn: cache giấy phép, chạy lại không xếp hàng main, preload nhường splash, native có hạn tuổi" (**API công khai không đổi**: dump root và nextgen chỉ `+` một khối internal `com.app.plugin.license.LicenseCache`, không dòng `-`, game 0; `BASELINE_ROOT` giữ `8f9539c`; hợp đồng SA1 §C giữ nguyên: giấy phép INVALID hoặc chưa VALID thì không khởi tạo mạng quảng cáo, verdict đầu thắng, fail-closed; plan `docs/superpowers/plans/2026-10-09-sdk-2.4.3-plan.md`).

### Nhanh hơn

<!-- lcache -->
- LCACHE: verdict giấy phép `VALID` được nhớ bền qua các lần mở (prefs riêng `appplugin_license`). Khoá cache là SHA-256 của token thô, `applicationId`, tập chứng chỉ ký, tập khoá công khai của SDK và bản SDK (`BuildConfig.SDK_VERSION`); trúng khoá và `exp` chưa tới (đồng hồ thật mỗi lần) thì bỏ qua parse và Ed25519, kết quả y như xác minh đầy đủ. Mọi lỗi (đọc prefs hay chứng chỉ ném, mục hỏng, khoá khác, hết hạn) thì xác minh đầy đủ như cũ; INVALID không bao giờ được cache. Không đổi cách cấp token. Dòng log mới mỗi process, ngay trước `license: VALID …` (dòng cũ giữ nguyên chữ): `license: source=cache|verify ms=<n> since_start=<n> cert=<n>`. Trần dòng của `license/LicenseGate.kt` trong `SdkFileSizeGateTest` nâng 210 → 214 (đo thật; trần plan 216); `license/LicenseCache.kt` là file mới.
<!-- faststart-a -->
- FASTSTART-A: việc khởi tạo mạng quảng cáo chạy lại sau verdict giấy phép được gửi lên main bằng message bất đồng bộ ở đầu hàng đợi (`sendMessageAtFrontOfQueue`), nên không xếp sau các khung hình đang chờ; vẫn chạy trên luồng main, thứ tự và luồng của `startAdNetworks` không đổi.
<!-- preorder -->
- PREORDER: lượt preload nhường `full_splash` và `nt_splash`: khi RC `ads_preload_android.delay_ms` không đặt (hoặc 0), preload chờ trong lúc hai placement đó đang nạp (ân hạn 1,5 s nếu chưa thấy splash nào nạp, trần 10 s rồi chạy như cũ, hỏi lại mỗi 250 ms). RC `delay_ms` > 0 thắng (hành vi 2.4.2 nguyên vẹn). Dòng log mới: `preload: wait splash` (một lần), `preload: splash done after <n> ms (settled|cap|none)`. Thời gian chờ được đếm bằng số nhịp hỏi 250 ms chứ không phải đồng hồ: khi main bận, `<n>` là cận dưới của thời gian thực.

### Thêm

<!-- initwait -->
- Resource số nguyên app **tuỳ chọn** `ads_cf_admob_init_wait_ms` (FASTSTART-C), **mặc định tắt**: vắng hoặc 0 = như cũ (load AdMob đang giữ chờ callback khởi tạo của GMA, hẹn an toàn 8 s); 1–8000 = thả các load đang giữ sau N ms kể từ khi `MobileAds.initialize` đã được gọi (sau `setRequestConfiguration`, nên test device luôn áp trước request); lớn hơn 8000 thì kẹp 8000; thả sớm ghi `ads admob initialize: early release after <n> ms (ads_cf_admob_init_wait_ms)`. Chỉ GMA Legacy (`appplugin`); `appplugin-nextgen` không đọc resource này (Next-Gen ném khi load trước khởi tạo). Nằm sẵn trong mẫu keep `@integer/ads_cf_*`. Đổi mặc định là quyết định của 2.5.0.

### Sửa

<!-- natage -->
- NATAGE: quảng cáo native đã nạp (AdMob, Facebook, MAX; cả Legacy lẫn Next-Gen) nay có hạn tuổi như fullscreen: quá `fullscreen_max_age_minutes` (RC `ads_policy_android`, mặc định **60 phút**, 1–240) thì bị bỏ, huỷ, báo `ad_show_fail reason=ad_expired` và lượt nạp kế tải mới (trước: native nạp sẵn hiện bất kể tuổi). Quảng cáo đang hiện không xét tuổi. Banner và rect không đổi.

### Phải làm khi nâng cấp

<!-- sc243-app -->
- Không bắt buộc, mọi app (Speaker Clean, Invoice Maker): không việc phải làm. Việc cần biết:
  - Native nạp sẵn quá 60 phút (hoặc `fullscreen_max_age_minutes`) bị thay bằng lượt nạp mới thay vì hiện lại; app đếm `ad_show_fail` có thể thấy lý do `ad_expired` ở native.
  - Preload nhường splash: app **không có** `full_splash`/`nt_splash` (ví dụ Invoice Maker) nay bắt đầu preload chậm khoảng 1,5 s (ân hạn); app có splash chờ tới khi splash nạp xong, tối đa 10 s. RC `ads_preload_android.delay_ms` > 0 (ví dụ 1) khôi phục thời điểm 2.4.2.
  - Lần mở đầu sau nâng cấp lên 2.4.3 và sau `MonetizationSdk.clearLocalData` xác minh giấy phép đầy đủ một lần (chậm như 2.4.2); từ lần sau dùng cache. Với `clearLocalData`, điều này chỉ đảm bảo theo khả năng tốt nhất: một lần ghi `apply()` đang chạy có thể ghi lại mục sau khi xoá.
  - Log: thêm dòng `license: source=…`, `preload: wait splash`, `preload: splash done after …`; ai lọc log theo `license:` phải chấp nhận thêm dòng `source=`.
  - Resource `ads_cf_admob_init_wait_ms` là tuỳ chọn và tắt mặc định; không đặt thì không đổi gì.

## [2.4.2] - 2026-10-07

"2.4.2 — ghép Speaker Clean (SC): thời điểm hiện cố định, công tắc placement, slot hiển thị, referrer sau consent, xoá dữ liệu cục bộ" (**API chỉ thêm**: dump `+` không có dòng `-`; mặc định cũ giữ cho Invoice Maker; nguồn `sc/ceo-decisions.md` D1–D6-SC, `sc/gap-sc-vs-241.md`; plan `docs/superpowers/plans/2026-10-07-sdk-2.4.2-plan.md`). Mốc ABI của `tools/api_facade_check.sh` (`BASELINE_ROOT`) dời từ `82c22cd` (v2.3.0) sang `8f9539c` (commit dump 2.4.2, như D27-210 của 2.1.0): API chỉ thêm đã được chủ dự án duyệt (D5-SC); `BASELINE_GAME` giữ `82c22cd` (dump game không đổi).

### Thêm

Bốn chữ ký công khai mới (dump root và nextgen +56 dòng, game 0, không dòng `-`; tính cả hàm tổng hợp `$default` của hai overload và các khối `internal` mới) cùng một resource:

- `AdsHelper.showInterstitial(placement: String, flagImage: Int, cbShow: AdsShowCb, isDeltaTime: Boolean = true, fixedMoment: Boolean): Boolean` và `AdsHelper.isInterstitialReadyToShow(placement: String, flagImage: Int = 0, isDeltaTime: Boolean = true, fixedMoment: Boolean): Boolean` (hai overload mới; `fixedMoment` không có mặc định nên lời gọi cũ 1–4 tham số vẫn chỉ khớp hàm cũ).
- `AdsHelper.isPlacementEnabled(placement: String): Boolean`: `true` khi entry đúng tên trong `cf_ads_placement` vắng hoặc `flagShow >= 0`; trước `initAdsHelper` trả `true`; không xét kill switch/gỡ quảng cáo.
- `MonetizationSdk.clearLocalData(context: Context): Unit`: xoá dữ liệu cục bộ của SDK, giữ quyền đã mua (xem mục "Sửa", `clear`).
- Resource số nguyên app `ads_cf_seg_cadence_guardrail_seconds` (mặc định cục bộ của guardrail segment; thứ tự RC `seg_cadence_guardrail_seconds` > resource > 0). Nằm sẵn trong mẫu keep `@integer/ads_cf_*` của `appplugin_keep.xml`.

### Sửa

<!-- fixed -->
- FIXED (D2-SC, gộp INTRO3): `fixedMoment = true` ở `showInterstitial` bỏ qua guardrail segment (`SegmentHooks.blocksFullscreen`), luật phiên/khoảng cách (`interstitialRefusal()`), cooldown N màn và brain; vẫn chặn khi có quảng cáo đang hiện, `isRemoveAds(4)`/`isDisableAds(4)` (kill, giấy phép, mask), `flagShow < 0`. `isInterstitialReadyToShow` nhận cùng tham số và trả đúng kết luận về các cổng mà `showInterstitial` ra (trừ brain), cộng điều kiện của riêng nó: placement đã nạp và host đã resume (chỉ hàm ready kiểm host resume). Lượt `fixedMoment` của `full_intro_3` được nối Full2 như fork; không có `fixedMoment` thì giữ loại trừ cũ; sau `full_intro_3` (và `full_splash`) hiện từ slot riêng thì không nạp lại `full_default`, hiện từ slot mặc định thì slot mặc định được nạp lại, như fork. Mọi lượt interstitial/rewarded nối Full2 dưới **placement được gọi** (trước: slot `originPl`).
<!-- switch -->
- SWITCH (D3-SC, D7-SC = D1-242): khoá `k` của `cf_ads_placement` áp cho `k` và bí danh (`full_x` ↔ `x`; JSON có cả hai thì khoá đúng tên thắng). **Theo từng nguồn:** RC và Firestore mỗi bên nhớ những khoá chính nó đã tắt (`mem_cf_ads_placement_rc`, `mem_cf_ads_placement_fs`; `mem_cf_ads_placement` giữ trạng thái hợp nhất cho cold start); khoá một nguồn từng tắt mà nay nguồn đó bỏ đi thì placement **bật lại** (`flagShow = 1`), kể cả khi giá trị RC rỗng/`{}` (chỉ khi nguồn đó có bộ nhớ riêng không rỗng) và khi tài liệu Firestore rỗng hoặc vắng; một nguồn không bao giờ bật lại khoá mà nguồn kia đang tắt. JSON hỏng giữ nguyên trạng thái và ghi `logE` (fork bật hết). Full2 sau placement X tắt khi `full_2_X` hoặc `full_2_default` có `flag < 0`: không dựng lớp che, không nạp. Thêm `AdsHelper.isPlacementEnabled` (`AdsHelper.kt:416`).
<!-- slot -->
- SLOT: quy tắc slot Full2 **3 bậc** cho cả nạp lẫn hiện (kể cả preload): tên `full_2_<placement>` chọn slot riêng `full_2_<placement>` → nếu mạng có slot `full_2_default` thì dùng nó → còn không là mặc định mạng `full_default`. Hiện/kiểm-đã-nạp (`showAd`, `hasLoadedAd`, `holdsLoadedAd`, `getAdForShow`) đọc slot riêng nếu nó đang có quảng cáo, không thì slot mặc định nếu nó có, không thì `riêng ?: full_2_default ?: mặc định` (bậc giữa chỉ cho tên `full_2_*`); `loadAd`/`isLoading` giữ `getAdsForPl`. Telemetry và tên RC của Full2 chuyển sang `full_2_<placement được gọi>` (xem "Phải làm khi nâng cấp").
<!-- splash -->
- SPLASH (D4-SC): `showOpenAd("full_splash")` được **miễn** guardrail segment; placement khác vẫn bị chặn `seg_guardrail`. **Đính chính [2.2.0]:** câu "mọi placement, kể cả `full_splash`, như fork" ở mục seg-hanh-vi của [2.2.0] không còn đúng; [2.2.0] giữ nguyên bản (bất biến), đính chính ở đây. `isInterstitialReadyToShow(placement, flagImage)` (không `fixedMoment`) nay tính cả guardrail segment để khớp cổng của `showInterstitial` (Invoice Maker guardrail = 0 nên không đổi).
<!-- referrer -->
- REFERRER (D5-SC): SDK tự đọc Install Referrer **sau consent** (khi cổng analytics mở và mỗi lần tầng consent đổi), một lần mỗi lượt cài (thử tối đa một lần mỗi process tới khi đọc xong, cờ `install_referrer_captured`), ngoài luồng main và ngoài khoá `ConsentState`; chỉ đọc khi Consent Mode cho `analytics_storage`; `gclid` và `raw_referrer` chỉ gửi khi `ad_user_data` được cho; `DENIED`/`UNKNOWN` trong GDPR không bao giờ đọc. Khác fork (gọi trước consent). Giá trị referrer thô không còn được lưu (khoá `install_referrer_url` của 2.4.1 không còn được ghi; `clearLocalData` xoá giá trị cũ). **Ghi chú Data safety (Play):** khai báo dữ liệu attribution cài đặt (tham số UTM; `gclid` khi người dùng cho `ad_user_data`) được thu thập và chia sẻ với Google Analytics for Firebase, mục đích Analytics (và Advertising nếu dùng `gclid` cho Google Ads); SDK chỉ thu sau consent.
<!-- clear -->
- CLEAR (D5-SC, D8-SC, D9-SC): `MonetizationSdk.clearLocalData(context)` (`MonetizationSdk.kt:195`) trả về ngay, I/O trên luồng nền; xoá mọi khoá của `App_prefer_file` và `XgameHCM-BaseAndroid` **trừ** (`LocalDataReset.KEEP_KEYS`/`KEEP_PREFIXES`): quyền đã mua (`iap_active_products`, `iap_remove_ads_products`, `entitlements_remove_ads_masks`, `iap_entitlements_v1`, `ads_hp_remove_ad_mask`, `ads_hp_remove_ad`), cờ do consent (`analytics_collection_disabled_by_sdk`, `mem_fcm_delete_pending`, `consent_tier`), **trạng thái công tắc placement** (`mem_cf_ads_placement`, `_rc`, `_fs`: ba khoá cùng giữ), bản lưu cấu hình từ xa/giấy phép/kill/policy (`AdsPolicy`, `SdkKillSwitch`, `LogEventControl`, `BiddingPartners`, `BrainPolicyStore`; tiền tố `cfcurr_`, `plmem_`) và cờ chống thu lại `install_referrer_captured`. **Sổ mua `mem_iap_ledger` bị xoá** (chứa order id): lần khôi phục mua sau đó phát lại `iap_purchase`, server khử trùng theo purchase token. Làm rỗng hàng đợi `files/appplugin_events`, bỏ cache trong RAM (sổ mua, player id do SDK sinh), nạp lại `UserSegmentEngine`. Không chạm `appplugin_policy`, `<pkg>_preferences` (IABTCF/GPP: đổi qua `showPrivacyOptions`), file của UMP/Firebase/AppsFlyer/AppLovin (app tự gọi `resetAnalyticsData`). Sau khi gọi máy như cài mới với SDK: player id do SDK sinh được thay mới (app truyền `playerId` qua `configure`/`AdsBrain.onSessionStart` thì `user_id` vẫn là id app truyền: app tự đổi hoặc ngừng truyền), `first_open` gửi lại (`TIME_OPEN` bị xoá), số phiên về 0 nên luật phiên đầu của `ads_policy_android` áp lại, mốc guardrail toàn màn hình và cửa sổ ẩn banner reset, `sub_lifecycle_*`/`sub_server_*` dựng lại từ Play/server. **Còn sót tới khi khởi động lại process:** thống kê bandit và belief của brain đang trong RAM (gồm belief dưới player id do app cấp) và cửa sổ eCPM của segment (`seg_ecpm_window`) có thể được ghi lại ở lần cập nhật kế tiếp; app muốn xoá sạch nên khởi động lại process sau khi gọi (backlog 2.5.0). Chỉ xoá dữ liệu **trên máy**: dữ liệu đã gửi (telemetry backend theo `user_id`, Firebase Analytics, AppsFlyer) không bị xoá; yêu cầu xoá dữ liệu/tài khoản phía server theo chính sách Play là việc riêng của app.

### Phải làm khi nâng cấp

<!-- sc242-app -->
- Invoice Maker (và app dùng 2.4.1 không đổi hành vi): không việc bắt buộc. Việc cần kiểm:
  - Full2 nay mang tên **`full_2_<placement được gọi>`** (trước: `full_2_<slot nguồn>`, vd. `full_2_default` cho placement không có slot riêng) trong telemetry, RC `cf_ads_placement` và dashboard; nạp/hiện chọn slot theo 3 bậc `full_2_<pl>` → `full_2_default` → `full_default`. Cập nhật bộ lọc dashboard và khoá RC nếu dựa vào tên cũ.
  - Placement có slot riêng mà slot đó đang rỗng nay hiện quảng cáo đã nạp ở slot mặc định của mạng (mọi định dạng: interstitial, app open, rewarded, native, rect); callback có thể nhận `originPl` là tên slot mặc định (`full_default`, `nt_default`…), nên app đừng dùng `originPl` để chọn placement nạp lại: dùng `placement`. Tỷ lệ hiện của placement có slot riêng sẽ tăng.
  - App có guardrail segment > 0 (RC `seg_cadence_guardrail_seconds` hoặc resource): `showOpenAd("full_splash")` nay hiện trong cửa sổ guardrail (trước bị chặn `seg_guardrail`).
  - `isInterstitialReadyToShow(placement, flagImage)` (không `fixedMoment`) nay tính cả guardrail segment như cổng của `showInterstitial`: app có guardrail > 0 sẽ thấy `false` ở khoảng chặn (Invoice Maker guardrail = 0: không đổi).
  - Gỡ một khoá khỏi `cf_ads_placement` (đang `flag:-1`) nay **bật lại** placement đó (theo từng nguồn RC/Firestore); tài liệu Firestore rỗng hoặc vắng cũng bật lại khoá Firestore từng tắt (trước: bỏ qua). Nâng từ 2.4.1: chưa có bộ nhớ theo nguồn nên bản đã lưu `mem_cf_ads_placement` được coi là của RC; khoá đã bỏ khỏi RC khi còn ở 2.4.1 được bật lại ở lần fetch RC đầu tiên của 2.4.2, và với app nhận `cf_ads_placement` qua Firestore thì khoá Firestore có thể bật lại trong khoảng ngắn tới khi Firestore trả về và tắt lại (một lần mỗi lần nâng cấp); tới lần fetch đầu đó, `isPlacementEnabled` với tên không tiền tố có thể trả `true` cho khoá `full_x` đã tắt (bản gộp cũ chưa mở bí danh).
  - `AdsHelper.parserAdsConfig` (public) gọi trực tiếp không phải là nguồn RC/Firestore: công tắc đặt bằng đường đó **không bền**, lần fetch RC kế tiếp dựng lại trạng thái từ bản ghi theo nguồn và bật lại nó; app chỉ nên bật/tắt placement qua RC (`cf_ads_placement`) hoặc Firestore.
  - Install Referrer nay SDK tự ghi sau consent (mục 14 của INTEGRATION): app tự đọc Install Referrer thì bỏ để khỏi đọc hai lần; cập nhật khai báo Data safety (dữ liệu attribution cài đặt, xem mục REFERRER).
  - Speaker Clean: đổi `bypassFrequencyCaps` thành `fixedMoment` (4 chỗ ở `MainActivity`; truyền theo tên, xem INTEGRATION mục 14 bước 8), đặt integer `ads_cf_seg_cadence_guardrail_seconds` = 60, thay `PreferenceHelper.clearAll()` bằng `MonetizationSdk.clearLocalData(context)`, chuyển `ads_placement_android` sang `cf_ads_placement` (bảng ở INTEGRATION mục 14); `screen_switch_interval` ở lại khoá RC riêng của app.

## [2.4.1] - 2026-10-07

"2.4.1 — sửa nhận Activity chủ cho app có `applicationIdSuffix`/flavor `applicationId`" (bản vá, không đổi API công khai; nguồn `v240/ceo-decisions.md` D36-240, phân tích `im/full2-redmi-analysis.md`).

### Sửa

<!-- hostfix -->
- HOSTFIX: `AppPluginBase.onActivityResumed` nhận Activity chủ (host) khi là Activity không thuộc SDK quảng cáo/mediation (11 tiền tố: `com.google.android.gms.ads.`, `com.facebook.ads.`, `com.applovin.`, `com.unity3d.`, `com.google.android.ump.`, `com.bytedance.`, `com.pangle.`, `com.mbridge.`, `com.vungle.`, `com.ironsource.`, `com.inmobi.`) và (tên lớp bắt đầu bằng `packageName` (luật cũ, giữ) hoặc là Activity launcher của `packageName` hoặc cùng package với lớp `Application`); luật `startsWith(packageName)` cũ vẫn được giữ, thêm hai điều kiện sau. Trước đó (mọi bản từ 8fcc3c1 (2026-09-11), gồm 2.3.0 và 2.4.0) app có `applicationIdSuffix` hoặc flavor `applicationId` (vd. `com.x.y.debug` với lớp `com.x.y.MainActivity`) không bao giờ có host: Full2 nối sau interstitial bị huỷ sau 3 s (`discarding unused prepared dialog`), `isInterstitialReadyToShow` luôn `false`, và lớp phủ Full2 hiện thành màn đen. Bản không suffix không rơi vào luật cũ (chưa đo trên máy thật).

### Phải làm khi nâng cấp

<!-- hostfix-app -->
- Không bắt buộc. App có `applicationIdSuffix` hoặc flavor `applicationId` trên mọi bản từ 8fcc3c1 (2026-09-11), gồm 2.3.0 và 2.4.0, cần gọi `AppPluginManager.instance.setMainActivity(this)` trong `onCreate` của Activity chính (trước lần resume đầu); nâng lên 2.4.1 thì bỏ được (giữ lại vẫn vô hại).

## [2.4.0] - 2026-10-06

"2.4.0 — GMA Next-Gen làm artifact tách `appplugin-nextgen` + đóng nợ 2.3.0" (đã chốt Task R; plan docs/superpowers/plans/2026-10-05-sdk-2.4.0-plan.md). Mỗi dấu dưới đây do task tương ứng điền nội dung khi land.

### Thêm

<!-- ng0 -->
- Artifact mới `com.app.plugin:appplugin-nextgen` (module `:appplugin-nextgen`, NG0): dùng chung nguồn `src/main` + `src/test` của `appplugin` (chép vào `build/generated/appplugin-shared/`), file cùng đường dẫn trong `appplugin-nextgen/src/` thay bản chung (`OVERRIDES.txt`, `NgOverridesTest`: khai đủ, ≤ legacy + 40, ≤ 800); cùng `namespace`/`sdkVersion`, ghim `moduleName = "appplugin"` → ở NG0 (0 override) `api/appplugin-nextgen.api` giống hệt `api/appplugin.api`. POM khai GMA Next-Gen `ads-mobile-sdk:1.5.0` + adapter Meta `6.22.0.1` (exclusion `play-services-ads`); chuyển tiếp: Next-Gen ở scope runtime, mã chung còn biên dịch trên Legacy `compileOnly` tới khi NG1–NG5b thay (`legacy-remaining.txt`, `NgLegacyCensusTest`). `appplugin` không đổi: dump Δ 0 (chỉ bỏ một import không dùng ở `AdsHelper.kt`). CI chạy unit/lint/AAR/dump/parity của module mới.
<!-- ng1 -->
- `appplugin-nextgen` khởi tạo GMA Next-Gen (NG1): `MobileAds.initialize(ctx, InitializationConfig.Builder(<meta-data APPLICATION_ID>).setRequestConfiguration(MA + test device), …)` trên luồng mới `appplugin-gma-init`, sau consent như `appplugin` (`AdSdkStartup`). Load trước khi init xong được giữ (tối đa 16, tràn thì từ chối lời gọi mới để giữ preload/splash đã xếp), quá 8 s vẫn giữ (Next-Gen ném nếu load trước init), init xong chạy đúng một lần trên main, init lỗi thì bỏ; dòng `Log.i`: `gma: init timeout, giữ hàng đợi`, `gma: init queue full, drop <n>`, `gma: init done, run <n>`, `gma: ready <ms>ms` (một lần mỗi process), `gma: init failed (<mã>)`. Manifest nextgen bỏ hai cờ `OPTIMIZE_*` của Legacy. `MainPost` là cửa duy nhất đưa callback nền của Next-Gen về main (`load`, sự kiện từng định dạng gồm `onAdPaid`, banner tự làm mới, `nativeLoader`, `video`, `earned`); `AdRequestFactory` dựng `AdRequest`/`BannerAdRequest`/`NativeAdRequest` với `npa=1` qua extras Google. Phụ thuộc Next-Gen nay ở scope compile; 6 file native là stub giữ chữ ký (D14-240) tới NG4/NG5a/NG5b, nên native/Full2 chưa chạy ở nextgen.
<!-- ng2 -->
- `appplugin-nextgen` (NG2): banner, rect và banner collapsible chạy trên GMA Next-Gen — `BannerAd.load(BannerAdRequest, …)` không nhận Context; banner dùng `AdSize.getLargeAnchoredAdaptiveBannerAdSize` (bản current-orientation đã deprecated; chiều cao có thể khác `appplugin`), rect `AdSize.MEDIUM_RECTANGLE`; slot trong `ads_collapsible_placements` gửi `collapsible=bottom` qua extras Google cùng `npa` tới lần nạp thành công đầu như cũ. Response và auto-refresh (`BannerAdRefreshCallback`) về main qua `MainPost`, view chỉ lấy trên main; response muộn của lượt đã bỏ giữ/huỷ như cũ, lỗi muộn bị bỏ; doanh thu từ `onAdPaid` (precision 0–3 theo enum). Next-Gen 1.5.0 đánh dấu `BannerAd.load`/`getView(Activity)` deprecated (khuyên `AdView.loadAd`/`registerBannerAd`) — vẫn chạy; chuyển sang `AdView` để bản sau. Banner cần Activity lúc nạp xong (không có thì báo lỗi `activity unavailable`).
<!-- ng3 -->
- `appplugin-nextgen` (NG3): interstitial, app open, rewarded và rewarded interstitial chạy trên GMA Next-Gen — `X.load(AdRequest, AdLoadCallback)` không nhận Context (cờ `gma_load_app_context` không có tác dụng ở nextgen), mọi callback load/sự kiện về main qua `MainPost`, app open giữ hạn 4 giờ, thưởng trao một lần lúc đóng như cũ. Doanh thu từ `onAdPaid`: `precision_type` 0–3 theo enum (UNKNOWN, ESTIMATED, PUBLISHER_PROVIDED, PRECISE), `value_micros` Long nguyên bản, currency; `ad_source` từ `ResponseInfo.getAdapterClassName()` qua cùng bộ ánh xạ; lý do show-fail mang mã lỗi của adapter mediation khi có (`ad_show_fail` reason `fail_to_show_full_content_<mã>` (app open: `open_fail_to_show_full_content_<mã>`) thay `_<message>` của Legacy: so A/B gộp theo tiền tố). Thưởng rewarded/rwi vẫn trao đúng một lần cả khi Next-Gen báo earned sau dismiss. Banner/rect/native của nextgen vẫn ở NG2/NG4–NG5b.
<!-- ng4 -->
- `appplugin-nextgen` (NG4): native `nt_` và native collapsible (NtCl) chạy trên GMA Next-Gen — `NativeAdLoader.load(NativeAdRequest(NATIVE) + VideoOptions + tỉ lệ media)` không nhận Context, callback load/sự kiện (gồm `onAdPaid`) về main qua `MainPost`, `NativeAdView.registerNativeAd(ad, mediaView)` chỉ chạy trên main (adapter Meta cần UiThread). Bốn layout `appplugin_admob_native_cl`, `appplugin_admob_native_layout_small`, `appplugin_admob_native_layout_language_2`, `appplugin_layout_native_full_pager` của nextgen dùng lớp gốc `com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView`/`MediaView`, cùng id; app nextgen tự đặt layout native (`setLayoutCustome4Native`) phải đổi lớp gốc tương tự. Full2 vẫn ở NG5a/NG5b.
<!-- ng5a -->
- `appplugin-nextgen` (NG5a): view và binder của Full2 (native full) chạy trên GMA Next-Gen — ba layout `appplugin_admob_native_full` (dọc + `layout-land`) và `appplugin_admob_native_layout_full` dùng lớp gốc `com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView`/`MediaView`, cùng id; `registerNativeAd(ad, mediaView)` chỉ chạy trên main. Video Full2 yêu cầu điều khiển riêng (`VideoOptions.setCustomControlsRequested(true)`, có tiếng như cũ): khi `VideoController.isCustomControlsEnabled()` là true dialog hiện nút play/pause/mute của SDK (id `appplugin_ntfull_*`, góc trên trái), không thì ẩn; trạng thái nút theo `VideoLifecycleCallbacks` về main qua `MainPost.video`. Nạp Full2 (`AdsMobPlNtFull`) vẫn ở NG5b.
<!-- ng5b -->
- `appplugin-nextgen` (NG5b): Full2 (native full) AdMob nạp bằng `NativeAdLoader` của GMA Next-Gen (điều khiển video riêng, có tiếng), callback load/sự kiện/`onAdPaid` về main qua `MainPost`; dialog Full2 dựng sẵn sau quảng cáo cha được xếp cuối hàng main nên chạy sau mọi callback nền đã tới trước (vd. dismiss của quảng cáo cha) và không dựng khi host đã huỷ hoặc chuỗi đã bỏ. Nextgen hết GMA Legacy: 0 file `com.google.android.gms.ads`, bỏ `compileOnly`/`testImplementation` `play-services-ads`, loại `play-services-ads` và `-lite` trên `api`/`implementation`/`runtimeOnly` (POM ghi exclusion); `releaseRuntimeClasspath` 0 `play-services-ads(-lite)`. Banner/rect ghi doanh thu qua entry Next-Gen của `PaidEvent`; mô tả nút video Full2 thành chuỗi `appplugin_ntfull_cd_*` (en, vi); POM ghim sàn `kotlinx-coroutines-android` 1.9.0 (constraint) cho app biên dịch trên nextgen. Khác `appplugin`: Full2 nạp xong khi host đã huỷ thì báo nạp lỗi và huỷ quảng cáo (Legacy đánh dấu đã nạp mà không giữ quảng cáo); hai callback chỉ ghi log `onAdOpened`/`onAdClosed` của Full2 không còn (Next-Gen không có tương ứng). Sau quảng cáo cha (interstitial/rewarded) trên nextgen, Full2 không gửi thêm request Full2 (`full_2_<placement cha>`, vd. `full_2_default`) khi Full2 đó đang hiện (override `Full2Chain` của nextgen, D26; nạp lại khi Full2 đóng; OOM của `ads-mobile-sdk` không đổi, xem `ng-gioi-han`); `appplugin` (Legacy) không đổi.

### Sửa

<!-- carry-ads -->
- CARRY-ADS (minors-230 #8, #17, #21, #27, #34): `AdPreloadPlan.blockReason` cho rwi dùng đúng cờ của `RewardedInterstitialController` (gỡ = `FLAG_FULLSCREEN`, tắt = `FLAG_REWARDED`); lượt nạp sẵn (`AdPreloader`) gắn callback riêng để bỏ dấu `trigger=preload` ngay khi lượt kết thúc không fill (trước: dấu tồn tới 60 s và gắn nhầm vào `ad_request` của app). API công khai/dump: Δ 0 (chỉ thân hàm và thành viên `private`).
<!-- carry-pol -->
- CARRY-POL (minors-230 #11, #12, #22, #24, #28, #31): `PolicyClient` chỉ nhận `com.app.plugin.BRAIN_POLICY_URL` dạng `https://<host>/…` hoặc đúng `http://127.0.0.1[:<port>]/…` (so host đã parse bằng `java.net.URI`, không `startsWith`; có user-info/query/fragment → `policy: off (com.app.plugin.BRAIN_POLICY_URL)`); version server trong dòng `policy: applied|rc_skipped version=<v>` lọc `[A-Za-z0-9._-]{1,64}`, khác → `?`; OkHttp read timeout 8 s (> deadline giải mã backend 5 s; connect 5 s, cả lời gọi ≤ 20 s giữ nguyên); mọi trường ghi được của `GuardrailEngine` là `@Volatile` (policy server áp trên `appplugin-policy`, quyết định đọc trên main); `FcmConsent.holdAutoInitUntilConsent` khi FirebaseApp vắng hẹn hỏi lại trên luồng `appplugin-fcm` (0,5 s, 1 s… 8 lần, ~2 phút) và chạy lại đúng một lần khi app tự khởi tạo Firebase muộn — cờ auto-init `true` đã lưu (≤ 1.3.x) không chạy trước consent. Test PC14 kiểm mức log qua seam `logSink` (không đọc text nguồn). API công khai/dump: Δ 0.

### Thay đổi hành vi

<!-- carry-ads-hanh-vi -->
- CARRY-ADS: (1) rewarded sau dismiss/hiện lỗi nạp lại **placement vừa hiện** (vd. `gift_premium`) thay vì luôn `gift_default` — như rewarded interstitial; app không cần tự nạp lại placement riêng nữa. (2) `loadInterstitialForThisTurn(…, cb)` / `loadOpenAdForThisTurn(…, cb)` khi slot đang nạp (của SDK hay của app) **gắn** `cb` vào lượt đang chạy: mọi callback nhận `onLoaded`/`onLoadFailed` thật đúng một lần, theo thứ tự gắn (trước: callback thứ hai nhận `onLoadFailed("…loading")` ngay); tầng mạng (`AdsPlBase.load` lúc slot đang load) cũng gắn callback thay vì báo `"isloading"`. Rewarded giữ nguyên cách gắn đã có. (3) Gỡ riêng quảng cáo rewarded (mask `FLAG_REWARDED`) không còn bỏ preload rwi (rwi chỉ bị gỡ theo `FLAG_FULLSCREEN`, như controller). (4) Dòng `Log.i` bỏ qua của preload mang placement: `preload: skip <format> <placement> (<lý do>)` (app open chưa cấu hình: placement `-`).
<!-- carry-pol-hanh-vi -->
- CARRY-POL (D3-240): `/policy` (Play Integrity) chỉ chạy khi `MonetizationSdk.configure { brainEnabled = true }` VÀ player trong rollout theo cấu hình phía client (`brainRolloutFraction` của `configure`, D15-240 — `rollout_fraction` của artifact đã áp không tắt `/policy`, nên server không tự khoá client; artifact vẫn điều khiển tính năng brain qua `rolloutOverride`: áp artifact không còn ghi đè `AdsBrain.rolloutFraction` của app, nên artifact KHÔNG có `rollout_fraction` áp sau một artifact có trường đó nay quay về rollout phía client thay vì giữ giá trị cũ, D17-240); khác → một dòng `Log.i` `policy: off (brain)` mỗi process, 0 đọc meta-data, 0 mạng (trước: chạy hễ đủ 3 meta-data). Tối đa một lượt Integrity / 24 h / cài đặt: mốc prefs `policy_last_attempt_ms` (file `appplugin_policy`) ghi `commit` trên `appplugin-policy` TRƯỚC lượt mạng, lỗi giữa chừng vẫn tính, 0 thử lại trong 24 h → `policy: skip (backoff <giờ còn lại>h)`; harness `policy_fake_token` không gọi Integrity nên không xét/ghi mốc. Cold start vẫn khôi phục policy server đã lưu như 2.3.0. Dòng `policy: off (com.app.plugin.INTEGRITY_CLOUD_PROJECT)` (demo brain bật, không khai cloud project) không đổi. FCM: FirebaseApp vắng lúc `initPlugin` → SDK hỏi lại trong cửa sổ ≈ 2 phút (0,5 s, 1 s… 8 lần) rồi thôi; Firebase khởi tạo muộn hơn cửa sổ đó thì vẫn dựa vào lần `follow` đầu (khi UMP trả lời) như 2.3.0. Việc giữ auto-init đọc lại tầng lúc chạy trên luồng FCM: consent đã cho phép trước đó thì không ghi `false`.

### Phải làm khi nâng cấp

<!-- ng-app -->
- `appplugin-nextgen` (tuỳ chọn, không bắt buộc; app ở `appplugin` Legacy không việc): muốn GMA Next-Gen thì (1) đổi toạ độ `com.app.plugin:appplugin` thành `com.app.plugin:appplugin-nextgen:<phiên bản>`; (2) exclude toàn cục `play-services-ads` và `play-services-ads-lite` (`configurations.all { exclude(group = "com.google.android.gms", module = "play-services-ads") … }`); (3) app dùng `appplugin-game` thêm `resolutionStrategy.dependencySubstitution { substitute(module("com.app.plugin:appplugin")).using(module("com.app.plugin:appplugin-nextgen:<phiên bản>")) }` (không có artifact game + Next-Gen riêng ở 2.4.0, D5-240); (4) bỏ `appplugin-max` (không hỗ trợ cùng Next-Gen); (5) layout của app ghi đè **cùng tên** một trong 7 file layout (6 tên) native của SDK (`appplugin_admob_native_cl`, `appplugin_admob_native_layout_small`, `appplugin_admob_native_layout_language_2`, `appplugin_layout_native_full_pager`, `appplugin_admob_native_full` (dọc và `layout-land`), `appplugin_admob_native_layout_full`; cả layout đặt qua `setLayoutCustome4Native`) phải đổi lớp gốc `com.google.android.gms.ads.nativead.NativeAdView`/`MediaView` thành `com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView`/`MediaView` (giữ lớp Legacy thì inflate hỏng vì `play-services-ads` bị loại); (6) app biên dịch trên nextgen nhận `kotlinx-coroutines-android` ≥ 1.9.0 qua `constraints { api(…) }` của `appplugin-nextgen/build.gradle.kts` được publish cùng artifact (POM `<dependencyManagement>` / metadata Gradle; không cần ghim); banner và rect cần Activity đã đăng ký lúc nạp xong (không thì `activity unavailable`, lần `showBanner` kế nạp lại); (7) mã GMA của chính app (import `com.google.android.gms.ads`) phải tự di trú sang Next-Gen. Hướng dẫn chọn bản và nâng cấp: `docs/integration/INTEGRATION.md` mục 3, 13, 16, 17.
<!-- carry-pol-app -->
- CARRY-POL: app bật `/policy` phải (1) `configure { brainEnabled = true }` với `brainRolloutFraction > 0` (player ngoài rollout phía client không gọi `/policy`; `rollout_fraction` trong policy server/RC không ảnh hưởng điều kiện này), (2) khai `BRAIN_POLICY_URL` bằng `https://` — `http://` chỉ còn cho `127.0.0.1` (dev/harness qua `adb reverse`), máy giả lập `http://10.0.2.2/` hay `http://localhost/` nay là `policy: off (com.app.plugin.BRAIN_POLICY_URL)`; (3) chờ tới 24 h giữa hai lượt Integrity trên cùng cài đặt khi thử tay (xoá dữ liệu app hoặc cài lại để thử ngay).
<!-- rollup -->
- Gộp nâng cấp 2.3.0 → 2.4.0: không việc bắt buộc (app ở `appplugin` Legacy giữ nguyên API công khai, dump Δ 0). Việc nên xem: muốn GMA Next-Gen thì làm đủ 7 bước ở `ng-app` (toạ độ `appplugin-nextgen`, exclude `play-services-ads(-lite)`, `-game` qua `dependencySubstitution`, bỏ `appplugin-max`, đổi lớp gốc layout native ghi đè); bật `/policy` cần `brainEnabled = true` với `brainRolloutFraction > 0`, `BRAIN_POLICY_URL` https (http chỉ `127.0.0.1`) và chờ 24 h giữa hai lượt Integrity (`carry-pol-app`); biết rewarded nạp lại placement vừa hiện, callback load được gắn khi slot đang nạp, dòng skip của preload mang placement (`carry-ads-hanh-vi`). Bảng 1.3.0 → 2.3.0 ở các mục `[2.3.0]` trở xuống (lịch sử không sửa).

### Công cụ

<!-- t0b -->
- Công cụ (t0b): `tools/api_dump.sh --module appplugin-nextgen`, `tools/api_parity_check.sh` (façade riêng của nextgen, chuẩn hoá `$appplugin_nextgen`), `pom_check.sh` Q6/Q7 và `module_check.sh` (namespace + phụ thuộc gốc) chỉ khi có module nextgen, `tools/release_local.sh` publish 4 artifact; test `tools/tests/dg_nextgen_test.sh` (11 kiểm, runner 365).
<!-- ng6 -->
- Công cụ (ng6): app demo có flavor `nextgen` (`:appplugin-nextgen`, 0 `play-services-ads`/`-lite` ở mọi cấu hình `nextgen*`; manifest chép từ `utility`), `release_gate.sh`/`bundle_size.sh --variant nextgen` và `gate_args.sh` nhận `--variant nextgen` như utility (khoá harness, sink 8089); `tools/ab_compare.py --a <sink…> --b <sink…>` so telemetry Legacy với Next-Gen (T1–T7, `RESULT: PASS|FAIL`, fail-closed). Build `:app:assembleNextgenRelease` đầy đủ chờ NG5b (NG6b); utility/game không đổi. Sau R0 (FIX-GATE): cổng đo chỉ chạm phần nút nằm trong vùng hiển thị trên thanh điều hướng, và `--banners --variant nextgen` đếm view banner Next-Gen.
<!-- ngpub -->
- NGPUB: `tools/consumer_check.sh` dựng dự án tiêu thụ tối thiểu (`tools/tests/fixtures/consumer`, AGP 9.2.1, Gradle `--offline`) trên `build/repo` và kiểm `releaseRuntimeClasspath` ba tổ hợp: (1) `appplugin` + `-game` + `-max` như 2.3.0, (2) `appplugin-nextgen` + `-game` với `dependencySubstitution` + exclude `play-services-ads(-lite)` (0 `com.app.plugin:appplugin:`, 0 `play-services-ads(-lite)`), (3) như (2) không luật thay thế, kỳ vọng `FAIL: trùng appplugin + appplugin-nextgen`; thiếu repo, thiếu artifact hoặc Gradle lỗi là FAIL; test `dg_nextgen_test.sh` CC1–CC5 chạy trên cây đóng hộp, không Gradle. Chữ hướng dẫn app ở `ng-app`.
<!-- carry-t -->
- Công cụ (carry-t): cổng thiết bị phân loại vi phạm StrictMode có khung `com.miui.server.*` trên khung SDK đầu tiên là môi trường (`by-sdk: <n> (+<m> env MIUI)`, không tính vào by-sdk); `net_restore` chỉ bật dữ liệu di động trên serial `emulator-*`; sink +0 ngoài utility in `INFO: sink +0 (không hàng mới)`; `perf_report.py` kiểm `min_n` (số nguyên >= 1); test `dg_carry_test.sh` (SM1, SM2, NR1, NR2, SK1), PF1, PL1 (log policy thật), `EventNameTrimTest`, R7b' thân block Meta.
<!-- demo-res -->
- Công cụ (demo-res): demo có nút `App open resume: OFF|ON` (`AdsHelper.setFlag4ShowResume`, mặc định OFF, không lưu); `device_gate.sh --resume` (APK utility) bật nút, HOME, mở lại và đo `resume_open ON: shows=<n> (cần 1)` / `resume_open OFF: shows=<n> (cần 0)`; `--measure` chạy thêm, SAU mọi chặng cũ, driver `show_full2` cho ba nút Full2 (`Interstitial → Full2`, `Rewarded → Full2`, `Show NtFull Full Screen (Full2)`): `show_full2 '<nhãn>': parent=<p> full2=<f> (cần 1/1)`. Sau R0 (FIX-GATE): `show_full2` và mọi chặng dùng `mg_first_visible` chỉ chạm trong vùng hiển thị trên thanh điều hướng (đọc inset `tappableElement`), không trúng nút HOME. Không đổi API SDK. FIX-GATE 2: nút cuối danh sách chỉ hở dưới ngưỡng 48 px trên thanh điều hướng 3 nút vẫn được chạm (dải tối thiểu 16 px, thấy cùng một dải hai lần qua một lần cuộn = đã tới đáy); `ui.xml` bị xoá trước mỗi lần `uiautomator dump` (dump lỗi không còn dùng file cũ của màn hình khác), dump lại ≤ 2 lần nếu rỗng; INVALID(ui) lưu XML cuối + focus vào `$MG_LOG.ui-fail.txt`. FIX-GATE 3: hàm thư viện của cổng đo gọi `mg_first_visible "${2:-}"` (chạy dưới `set -u` của `device_gate.sh`; trước đó tra cứu một đối số báo `unbound variable` và INVALID(ui)); test nguồn thư viện dưới `set -u`. FIX-GATE 4: demo có drive `show_rwi` (`--es drive show_rwi`) và chặng gap rwi của `--measure` gọi quảng cáo qua intent đó thay vì tìm nút bằng UI (tìm UI mất khoảng 46 s trên Samsung, quá 30 s `policy_min_gap`); trên máy gesture-nav không chạm trong 48 px cuối của vùng cử chỉ (inset `mandatorySystemGestures`, dự phòng `systemGestures`/`navigationBars`); đóng shade thông báo trước kiểm tra focus và trước mỗi lần thử lại; `ui_find` dừng khi demo mất focus. **Giới hạn công cụ:** trên máy gesture-nav, nút nằm trọn trong dải cử chỉ chỉ chạm được bằng drive intent (`show_interstitial`, `show_rewarded`, `show_rwi`); nhãn khác báo INVALID(ui) kèm focus (2.4.1: thêm drive action hoặc cuộn lên trước khi chạm). `--iap` kiểm tra `grace` cần mạng nền cho app demo: máy chặn mạng nền (Samsung A25 từ khoảng 06:48 ngày 2026-10-06, ~5,5 s sau HOME) cho 11/12 và phải đọc là INVALID(env), không phải lỗi SDK (D29). FIX-GATE 5 (D32): drive intent `show_rwi` (chặng gap và `--drive-intent` của `ui_find`) chạy `am start` với `--activity-clear-top --activity-single-top` (trước đó không cờ nên chỉ đưa task lên trước, `onNewIntent`/`runDrive` không chạy); công cụ chờ dòng `drive=intent action=<a>` (thiếu = `invalid (drive)` rc 4), rồi chờ `show refused: policy_min_gap` (thiếu = FAIL), lưu logcat chặng gap ở mọi nhánh; bỏ câu FAIL sai "APK không có cờ test". Bỏ GitHub Actions (D33): kiểm tra và release chạy local (`tools/release_local.sh`: tag = sdkVersion, notes từ CHANGELOG, unit + lint, POM check, 4 AAR, GitHub Release; publish GitHub Packages khi có PAT).
<!-- ig3 -->
- Công cụ (ig3): `docs/integration/INTEGRATION.md` mô tả SDK 2.4.0: §3 "Bốn artifact", mục 16 "Chọn GMA Legacy hay Next-Gen", mục 17 `-game` + `appplugin-nextgen`, hàng §13 2.4.0 (`ng-app`, `carry-pol-app`, `carry-ads-hanh-vi`), §15 policy https-only + back-off 24 giờ, bản tiếng Anh đồng bộ; `tools/integration_doc_check.sh` DC19 (§3 có hàng bảng đủ 4 artifactId) và DC20 (§3 nêu `appplugin-max` + `appplugin-nextgen`: không hỗ trợ), kiểm toạ độ Next-Gen ở `appplugin-nextgen/build.gradle.kts`; test `dg_doc_test.sh` (dc19, dc20).

### Giới hạn đã biết

<!-- gma-gioi-han -->
- `appplugin-max` chỉ dùng với `appplugin` (Legacy): chưa có adapter AppLovin MAX cho GMA Next-Gen trên Google Maven/Maven Central, và Next-Gen buộc exclude `play-services-ads` (phá `google-adapter` của MAX). App chọn một: `appplugin` (+`-game` +`-max`) hoặc `appplugin-nextgen` (+`-game`). Bigo `com.google.ads.mediation:bigo:6.1.0.0` không tồn tại (mới nhất 6.0.0.1) nên không khai.
<!-- ng-gioi-han -->
- Hai bộ nguồn GMA (Legacy chung + override Next-Gen ở `appplugin-nextgen/src`) song song tới khi bỏ Legacy (Google: ≤ 2027-06-30). `ads-mobile-sdk` 1.5.0 mang 92 lớp shim trùng tên `com.google.android.gms.ads.*`, nên trong lúc chuyển tiếp không thể biên dịch mã Legacy cùng Next-Gen trên một classpath (D14-240). Test chung đọc file repo theo đường dẫn tương đối hoặc phụ thuộc thứ tự không chạy ở nextgen (`appplugin-nextgen/shared-test-exclude.txt`, mỗi dòng có lý do); chúng vẫn chạy ở `appplugin`. GMA Next-Gen báo mọi callback trên luồng nền, nên lỗi hiện interstitial/app open/rewarded/rwi không bao giờ tới đồng bộ trong `show()`: `show()` trả `true`, lỗi tới app sau qua `onAdShowFail` và vòng waterfall không rơi sang mạng kế trong cùng lượt (Legacy rơi được khi GMA báo lỗi đồng bộ; NG3). **`appplugin-nextgen` 2.4.0 = preview, chưa dùng production** (D26-240): `ads-mobile-sdk` 1.5.0 chuyển media của creative toàn màn hình qua client HTTP riêng (`ads_mobile_sdk.r60`) và đệm cả body trong heap, không có trần; một creative video progressive dài (itag 22, khoảng 34 phút, không `range=`) gây `OutOfMemoryError` 134–268 MB trên Samsung A25 ở process chỉ nạp sẵn (Legacy phát qua WebView nên không bị); không có công tắc công khai, SDK ta không bắt được lỗi này; chờ Google sửa (theo dõi `ads-mobile-sdk` > 1.5.0); tuỳ chọn 2.5.0: nạp sẵn nextgen tuần tự (chỉ giảm đỉnh ở ca trung bình). `appplugin` (Legacy) vẫn là mặc định, không đổi. **Giới hạn API < 31:** `ads-mobile-sdk` 1.5.0 ghi `NoClassDefFoundError` cho `PackageManager$OnChecksumsReadyListener` (kiểu API 31) ở lớp `am3`; ART từ chối khởi tạo lại lớp, quảng cáo vẫn chạy. Đo được: API 26 không có, API 28 có (8 dòng, không crash), API 33/37 không; API 29–32 chưa đo. Chưa có sàn minSdk được chứng minh; app thử nextgen trên API < 31 có thể thấy dòng này (lỗi trong SDK Google). Trạng thái preview không đổi.
<!-- preload-gioi-han -->
- PRELOAD-GIOI-HAN (còn lại ở 2.4.0): RC `ads_preload_android` chỉ giữ trong RAM, RC đến muộn hơn lượt nạp đầu thì process đó dùng mặc định (bật). Interstitial trong cửa sổ `fullTimeStart`/`fullSessionStart` được hoãn bằng một runnable trên main looper, thời gian chờ tính từ `tAppStart` của lần cài đầu và không có trần (RC lớn → chờ hàng giờ rồi nạp nền; dòng `preload: defer <format> <placement> <ms> ms (fullStartTime)`); khởi động lại process thì bỏ lượt hoãn đang chờ. Kế hoạch nạp sẵn có app open `full_splash` nhưng splash không được nạp lại hay hiện lại trong process, nên nếu splash của app đã nạp xong trước preload thì lượt này thừa. Nạp sẵn chạy một lần mỗi process, không thử lại sau no-fill. Các giới hạn cũ của 2.3.0 về rewarded chỉ nạp lại `gift_default`, dòng skip thiếu placement và dấu `trigger=preload` tồn tới 60 s sau no-fill không còn (xem `carry-ads-hanh-vi`).

## [2.3.0] - 2026-10-05

"2.3.0 — đóng nợ tích hợp: tài liệu khớp mã, brain nói chuyện với `/policy` thật, preload mọi định dạng" (plan docs/superpowers/plans/2026-10-05-sdk-2.3.0-plan.md).

### Thêm

<!-- secbc -->
- SECBC: máy khách `/policy` có Play Integrity cho brain, **mặc định tắt**. Gói mới `com.app.plugin.brain.policy` (mọi kiểu `internal`: `PolicyClient`, `PolicyTransport` + bản OkHttp connect/read 5 s, cả lời gọi ≤ 20 s, không theo redirect, thân ≤ 64 KB, `IntegrityTokenSource` + `PlayIntegrityTokenSource` classic nonce + cloud project number). `AppPluginManager.initPlugin` gọi `PolicyClient.install` ngay sau `FirebaseApp.initializeApp`: chỉ đăng ký nghe verdict giấy phép (0 I/O trên main); verdict đầu `VALID` → luồng `appplugin-policy` đọc meta-data, `POST <url>policy/nonce` (`X-Api-Key`) → token Integrity (≤ 10 s) → `GET <url>policy` (`X-Api-Key` + `X-Integrity-Token`). Chỉ áp khi 200 + JSON chặt + `source == "app"` + `policy` là object, qua `DecisionEngine.applyPolicy` (được lưu như RC). Một dòng `Log.i` mỗi lượt: `policy: applied version=<v> source=app` | `policy: default (<nonce_<mã>|nonce_parse|no_token|http_<mã>|parse|<reason server>>)` | `policy: rejected (<dòng 1 báo cáo>)` | `policy: error (<Lớp>)` | `policy: off (<khoá meta-data>)`; không bao giờ log nonce, token, khoá API. Harness `BrainPolicyHarness` (3 khoá) nhận thêm extra `policy_fake_token` (token giả, bỏ yêu cầu cloud project, vẫn chờ `VALID`); demo `utility` khai `com.app.plugin.BRAIN_POLICY_URL = http://127.0.0.1:8089/` (không khai cloud project → đường sản phẩm `policy: off`).
<!-- preload -->
- PRELOAD (D15-230, D23-IM (2), D17-230): SDK tự nạp sẵn quảng cáo toàn màn hình. Cuối `AdSdkStartup.startAdNetworks` (chỉ tới đó khi consent cho phép, kill switch `ads` không chặn và giấy phép VALID), một lần mỗi process, khi MobileAds khởi tạo xong (hoặc quá 8 s) cộng `delay_ms`, SDK đăng một lời gọi load cho mỗi slot: interstitial `full_default` (kéo theo Full2 `full_2_default` khi `full2FlagShow` 1/3), mọi placement app open có cấu hình (`admob_openad`, MAX open; demo `full_splash` và `full_resume`), rewarded `gift_default` (Full2 khi `full2FlagShow` 2/3), rewarded interstitial `rwi_default` khi có cấu hình. Slot bị mask gỡ quảng cáo, kill switch, giấy phép hay `maskAdsStatus` chặn thì bỏ, một dòng `preload: skip <format> (<lý do>)`; interstitial trong cửa sổ `fullTimeStart`/`fullSessionStart` (≥ 120) được hoãn tới khi cửa sổ đóng thay vì bị cổng load bỏ im lặng; chưa có host Activity thì chờ `setMainActivity`. Lời gọi trùng (app hoặc SDK đã mở lượt nạp slot đó trước) bị bỏ, một dòng `preload: skip <format> (loading)` (D33-230: số dòng `load` + `skip (loading)` = số slot cấu hình; demo: `full_splash` và `rwi_default` do demo tự nạp ~1,2 s trước preloader). RC mới `ads_preload_android` `{"schema_version":1,"enabled":true,"formats":["interstitial","app_open","rewarded","rewarded_interstitial"],"delay_ms":0}` (vắng = bật; `delay_ms` kẹp 0..60 000; JSON hỏng, `schema_version` khác 1 hay trường sai = mặc định cả khối). Mỗi `ad_request` của lượt nạp sẵn mang thêm `trigger: "preload"` (param map, `LoadRequest` không đổi, không event mới). Log `Log.i`: `preload: <format> <placement> load` (chỉ khi lượt nạp thật sự mở), `preload: skip <format> (<lý do>)`, `preload: off (rc)`. Interstitial/app open: app gọi load kèm callback lúc lượt nạp sẵn đang chạy thì callback được gắn vào lượt đó (trước đây trả `…loading`). Nạp lại sau khi hiện: app open nạp lại placement vừa hiện sau dismiss/lỗi hiện (trừ `full_splash`); Full2 đóng thì nạp lại `full_2_<placement cha>` (trước chỉ `full_2_default`). Không nạp sẵn banner, native collapsible, native inline, rect, native full đứng riêng (gắn view). Lớp mới internal `ads.core.AdPreloader`, `ads.core.AdPreloadPlan`.

### Sửa

<!-- rob3 -->
- ROB3: `com.app.plugin.ads.RemoveAdsMask` thành `public` (2.0.0 C3b để `internal` dù INTEGRATION.md §9 bước 6 và §14 bước 2 bảo app dùng; dump API không đổi vì bytecode vốn public). `tools/integration_doc_check.sh` thêm kiểm visibility §9 (`tools/lib/doc_visibility.py`): mọi lớp ở bảng gói §9 và mọi `Lớp.thành_viên` trong §9 phải khai báo không `internal`/`private` trong nguồn Kotlin (enum entry tính là thành viên); `AdsBrain` giữ `internal` (D1-230), §9 bỏ khỏi bảng gói và bước 10 nói brain được hỏi tự động. Bảng nâng cấp §13 theo bản 2.3.0. Thông điệp `@Deprecated` của `com.app.plugin.ads.consent.ConsentManager`: gỡ ở 3.0 (không phải 2.0). ROB-2 nhẹ: process chưa có FirebaseApp mặc định (vd. bản debug không `google-services`) → 1 dòng W/process (`firebase: không có FirebaseApp mặc định — …`) và SDK bỏ Analytics/FCM của mình (`FirHelper.logEvent`, `ad_impression`, token và cờ auto-init FCM) thay cho 1 dòng E mỗi lời gọi; chỉ ghi nhớ "có", nên app khởi tạo Firebase sau `super.onCreate()` có lại Analytics/FCM của SDK từ lời gọi kế. Giới hạn: `SegmentHooks.kt:49` (user property) và `InstallReferrerHelper.kt:87` (referrer) vẫn log E khi thiếu FirebaseApp.
<!-- secbs -->
- SECBS: backend `/policy` hạn mức theo client (`(app_id, IP)` 1/10 + trần app 20/200, env `BRAIN_POLICY_APP_RATE_RPS`/`_BURST`, `BRAIN_TRUST_PROXY_HOPS`), lỗi back-off có lớp riêng `SessionBackoff` trong log, giải mã nhiều gói dùng một hạn 5 s chung và log gộp mọi lỗi (`decode_failed:<gói>=<mã>`); mã ra dây không đổi.
<!-- minr -->
- MINR: tên event Firebase `aad_lst_/aad_led_/aad_lfa_/aad_imp_` luôn <= 40 ký tự (trước đây placement dài như `gift_premium_template` cho 41 ký tự và Firebase Analytics bỏ event im lặng): cắt phần placement, giữ prefix và hậu tố platform/format (`AdsTelemetry.kt` 183 -> 193 dòng, hàm nội bộ, ABI không đổi); test census `isGetTurn()` của hai native full (R7b); `tools/perf_report.py` in hàng `info (không có dữ liệu)` cho luật không có số và luật hard có `min_n` báo `FAIL (n=<n> < min_n=<m>)` (samsung `fill_ms.banner` `min_n` 10).

### Thay đổi hành vi

<!-- secbc-hanh-vi -->
- SECBC: mặc định không đổi gì (thiếu meta-data = `policy: off`, 0 mạng). Bật đủ ba khoá: một lượt `/policy` mỗi process sau verdict giấy phép `VALID` đầu tiên, không chờ consent (Q4), 0 thử lại (Q5); giấy phép `INVALID`/`PENDING` mãi = 0 mạng. Fail-closed: mọi lỗi (nonce khác 200, không token, `source=default`, JSON hỏng, thân > 64 KB, artifact bị `BrainPolicy` từ chối) giữ nguyên policy đang chạy (RC/bản lưu/built-in), không bao giờ áp `{"version": 0}`. Sau khi `/policy` áp thành công, RC `brain_policy_android` bị bỏ qua tới hết process (Q3; một dòng `policy: rc_skipped version=<v> source=app`); cold start khôi phục bản cuối đã lưu như trước.
<!-- secbs-hanh-vi -->
- SECBS: một client hết hạn mức không còn chặn client khác của cùng app (429 theo client; bucket client kiểm trước, trần app sau — client đã hết hạn mức không tiêu token app, D21-230); chỉ dùng `X-Forwarded-For` khi `BRAIN_TRUST_PROXY_HOPS=N` > 0 (hop thứ N từ phải, IP hợp lệ, IPv6 theo /64).
<!-- preload-hanh-vi -->
- PRELOAD: SDK nạp sẵn mặc định → thêm `ad_request` ngay sau khi mạng quảng cáo khởi tạo (sau consent), dù app chưa gọi load (demo: 5 slot + Full2). Tắt bằng RC `ads_preload_android` `enabled:false` hoặc thu hẹp `formats`; RC `ads_preload_android` chỉ giữ trong RAM (không lưu prefs): RC đến muộn hơn lượt nạp đầu thì process đó dùng mặc định (bật) (D23-230 b). `loadFull2ForThisTurn` nay từ chối khi người dùng đã gỡ quảng cáo toàn màn hình (cờ 4): người dùng gói gỡ quảng cáo không còn phát request Full2 do rewarded kéo theo. App open tự nạp lại sau dismiss; Full2 nạp lại đúng placement vừa hiện; rewarded sau khi hiện chỉ nạp lại `gift_default`, không nạp lại placement vừa hiện (D23-230 a).

### Phải làm khi nâng cấp

<!-- rob3-app -->
- 2.2.0 → 2.3.0, ROB3: app dùng hằng cục bộ thay `RemoveAdsMask` (cổng IM: 63/23) → đổi về `RemoveAdsMask.ALL`/`RemoveAdsMask.INTERRUPTIVE`. App đang dùng `com.app.plugin.ads.consent.ConsentManager` (`@Deprecated`): vẫn chạy suốt 2.x, nên chuyển sang `com.app.plugin.consent.ConsentManager`.
<!-- secbc-app -->
- 2.2.0 → 2.3.0, SECBC: không bắt buộc. Muốn brain lấy policy từ server: khai meta-data `com.app.plugin.BRAIN_POLICY_URL` (http/https, kết thúc `/`), `DATABUCKETS_API_KEY` (khoá của app trên backend) và `com.app.plugin.INTEGRITY_CLOUD_PROJECT` (cloud project number, số > 0; **khai qua `@string/...`**, không dùng số trực tiếp: aapt2 có thể lưu số 12 chữ số dạng float và `policy: off`; khuyến nghị, chưa kiểm trên thiết bị); backend chạy `BRAIN_INTEGRITY_MODE=google` (sau O3) và có `BRAIN_POLICY_DIR/<app_id>.json`. Thiếu bất kỳ khoá = tắt, brain giữ hành vi cũ.
<!-- preload-app -->
- 2.2.0 → 2.3.0, PRELOAD: không bắt buộc đổi mã. App gọi `load*ForThisTurn(…, cb)` khi slot đó đang có một lượt nạp không callback (nạp sẵn của SDK, nạp lại sau dismiss của SDK, hay một lượt app tự mở mà không truyền callback): SDK gắn callback của app vào lượt đang chạy đó (app nhận `onLoaded`/`onLoadFailed` thật, một lần), các lời gọi load cho placement mặc định sau consent (vd. Invoice Maker IM-INT3) là vô hại nhưng vẫn nên giữ: nạp sẵn chạy một lần mỗi process, không thử lại sau no-fill, và nạp interstitial với `flagImg 0` (app hiện với `flagImage` ≠ 0 không được lợi). Khi app gọi `setFlag4ShowResume(true)`, app open resume (`full_resume`) được nạp sẵn và nạp lại sau mỗi dismiss nên có thể hiện ở mọi lần quay lại đủ điều kiện. Placement không mặc định (vd. `gift_premium_template`, interstitial có unit riêng) và việc nạp lại rewarded không mặc định sau khi hiện (SDK chỉ nạp lại `gift_default`) vẫn là việc của app (D23-230 a). Muốn tắt nạp sẵn: RC `{"schema_version":1,"enabled":false}` ở khoá `ads_preload_android`.
<!-- rollup -->
- Gộp nâng cấp 2.2.0 → 2.3.0: không việc bắt buộc. Việc nên xem: đổi `RemoveAdsMask.ALL/INTERRUPTIVE` thay hằng cục bộ và chuyển sang `com.app.plugin.consent.ConsentManager` (ROB3); giữ các lời gọi `load*ForThisTurn` (nạp sẵn một lần, không thử lại, `flagImg 0`) và biết `setFlag4ShowResume(true)` làm app open resume hiện ở mọi lần quay lại đủ điều kiện (PRELOAD); bật `/policy` cần ba meta-data (project qua `@string`) và kiểm hạn ngạch Play Integrity (SECBC). Mặc định tắt policy, 0 mạng; bảng 1.3.0 → 2.2.0 ở các mục `[2.2.0]` trở xuống (lịch sử không sửa).

### Công cụ

<!-- t0b -->
- Task T0b (2.3.0, bài học 15, plan-lint #59) — `tools/lib/free_port.sh` (12 dòng, chỉ `python3` bind cổng 0, bỏ 8097/18089/18090) trả một cổng TCP trống; `tools/tests/dg_tools_test.sh` (618) P3/M1/M5 dùng cổng trống thay 18089–18092 nên hai runner chạy song song không đụng nhau (test `FP1 free_port.sh`; `t230-t0b-parallel2.log` 2 × 79/79).
<!-- secbh -->
- SECBH (2.3.0) — `device_gate.sh --policy` (`tools/lib/policy_legs.sh`, chỉ APK utility) chạy client policy của SDK trên sink fake mode ở cổng trống (`tools/lib/free_port.sh`; `BRAIN_INTEGRITY_MODE=fake`, `adb reverse tcp:8089`): L1 `fake-ok` = đúng 1 `policy: applied version=policy_harness_230 source=app`, L2 `fake-bad-device` = `policy: default (not_accepted)`, L3 sink dừng = `policy: default (nonce_…)`/`error`, L4 không extra = 0 dòng `policy:` applied/default; mọi chặng StrictMode by-sdk 0, FATAL/ANR 0, token không lọt logcat. Sửa D18-230(b): cổng tắt airplane mode lúc thoát (`tools/lib/net_restore.sh`, cả khi FAIL) — emulator không còn bị bỏ ở airplane mode làm chặng kế INVALID. Test `tools/tests/dg_policy_test.sh` (GP1–GP7 + GP2b = 8). Smoke thật trên emulator 5570 (APK từ archive `20c95dc`): L1–L4 PASS, FATAL/ANR/native 0, StrictMode by-sdk 0, airplane_mode_on lúc thoát 0 (D31-230).
<!-- ig2 -->
- IG2 (2.3.0) — `docs/integration/INTEGRATION.md` sửa theo cổng Invoice Maker: đầu tài liệu "SDK 2.3.0"; §2 và phụ lục A mẫu `exclusiveContent { includeGroup("com.app.plugin") }` + property `appplugin.localRepo`; §5 token ở `license.properties` (gitignore) + `license.properties.sample` + `findProperty` rỗng, không vào file tracked; §6 xung đột backup-rules của AppsFlyer với mọi app, sink cục bộ debug (cleartext chỉ `127.0.0.1`, khoá `demo-utility-key`), Consent Mode v2 từ chối mặc định và FCM auto-init tắt, quy tắc tiền tố/tên placement; §8 `ConsentManager` cũ gỡ ở 3.0 và di trú `showPrivacyOptions`, thứ tự `startSdkInit`; §9 bước 3 `setFlag4ShowResume(true)`, bước 5 show-then-load, bước 7 `setPostAdScreenCooldown` gọi được trước consent, bước 10 `paywallFor`, bước 14 preload; §10 hàng `ads_preload_android`; §12 `device_gate.sh --policy`; §13 hàng 2.3.0 (policy brain, preload, tổng quát); §14 phụ thuộc bắc cầu và Consent Mode; §15 mới policy brain qua Play Integrity (tuỳ chọn, mặc định tắt, chưa kiểm với Play Integrity thật). `tools/integration_doc_check.sh` thêm luật: khối `exclusiveContent` (DC14), `license.properties` (DC15), mọi `"com.app.plugin.<KHOÁ>"` ở nguồn phải có trong tài liệu, `*_HARNESS` miễn (DC16), mục "Policy brain qua Play Integrity" bắt buộc; `tools/tests/dg_doc_test.sh` +3 ca (DC14b/DC15/DC16).

### Giới hạn đã biết

<!-- secbc-gioi-han -->
- SECBC: chưa chạy với Play Integrity thật (O3) — chỉ unit test với nguồn token giả và harness `policy_fake_token` (SECBH); `PlayIntegrityTokenSource` (classic) cần cloud project number và chưa đo hạn ngạch/độ trễ thật. Hạn ngạch: mỗi process của mọi người dùng tốn một lời gọi Integrity classic (cả khi brain tắt), mặc định khoảng 10 nghìn lời gọi/ngày [P]; chủ dự án kiểm tra/nâng hạn ngạch trước khi bật, 2.4.0 thêm back-off giữa các process. Một lượt mỗi process, 0 thử lại: lỗi mạng ở lượt đó = policy hiện có tới lần mở sau. Đọc pháp lý Play Integrity thuộc chủ dự án (OWNER-NOTES). `BRAIN_POLICY_URL` `http://` được chấp nhận với mọi host (https-only trừ `127.0.0.1` dời 2.4.0).
- PRELOAD (D23-230): RC `ads_preload_android` chỉ trong RAM — RC đến muộn thì process đó dùng mặc định (bật); JSON hỏng hay `schema_version` sai = mặc định cả khối; rewarded sau khi hiện chỉ nạp lại `gift_default`, placement rewarded không mặc định do app nạp lại; app open resume cần app gọi `setFlag4ShowResume(true)` (demo không gọi nên chưa đo); slot đang nạp bởi app/SDK = `skip (loading)`; chưa đo trên Play Integrity/mạng thật ngoài demo.
- Hiệu năng 2.3.0 (Task R, nguồn `metrics-230.md` #23–#26; bảng đủ ở DOC/65 "Hiệu năng (2.3.0)"): Redmi `hard` PASS theo luật ×1,5 — `initPlugin` 16 / 23,6 ms (2.2.0 14 / 31,4), `TotalTime` cold 337 / 376,6 ms, first-run `first_by_sdk` 0, show interstitial 143 ms; `fill_ms.opened` thô FAIL n = 2 (p90 2933,9 > 2879, trong ×1,5). Emulator chỉ thông tin: `initPlugin` med emu28 91, emu26 141 ms hợp lệ; emu37 (93) và emu33 (56) INVALID do tải host khi đo. Samsung chưa đo (vắng); API 24 chưa đo (không có image).
<!-- secbs-gioi-han -->
- SECBS: kho nonce và bucket nằm trong process (D7-230): google mode cần `--max-instances=1` hoặc session affinity; mặc định `BRAIN_TRUST_PROXY_HOPS=0` mọi client sau cùng một proxy chung bucket app (như 2.2.0), client sau NAT cũng vậy; N quá nhỏ = mọi client chung một bucket, N quá lớn = khoá client tự khai được; N chưa kiểm chứng offline, xác nhận bằng request thật trước khi bật.
<!-- lic-gioi-han -->
- LIC: hàng `experiment_exposure` giữ chờ verdict `VALID` được đóng dấu thời gian khi đưa ra hàng đợi gửi (lúc verdict `VALID` đầu tiên của process chạy `flushHeld`, `AdsBrain.kt:93-96`), không phải lúc bắt đầu phiên và không phải lúc HTTP gửi/gửi lại; `reconcile`/`calibrate` không dùng mốc đó (DOC/57 mục B.2).
<!-- gma-gioi-han -->
- GMA Next-Gen: SPIKE vòng 2 = go có điều kiện (D16-230, `v230/gma-nextgen-spike-2.md`); 2.3.0 không đổi mã GMA, di trú ở 2.4.0 thành artifact tách `appplugin-nextgen` theo plan riêng (D9-230). `appplugin-max` = legacy-only (adapter MAX Next-Gen của AppLovin chưa phát hành, PR #952/#927 còn mở); MAX + Next-Gen không hỗ trợ. Đường GMA Legacy: thông báo deprecation mốc 2027-06-30 (v25.x).

## [2.2.0] - 2026-10-05

"2.2.0 — hoàn thiện cuối: segment vào core, minSdk 24, hướng dẫn tích hợp kiểm chứng" (plan docs/superpowers/plans/2026-10-04-sdk-2.2.0-plan.md).

### Thêm

<!-- seg -->
- Segment người dùng vào core (SEG, D5/D15/D18/D25/D27-220): gói `com.app.plugin.segment` port từ fork `:adsplugin` của Invoice Maker (tập lớn hơn của IM ∪ SC, FQN giữ nguyên) — 10 kiểu public (`UserSegmentEngine`, `UserSegment`, `SegmentEvent`, `RevenueTier`, `RevenueTierEvaluator`, `RevenueTierConfig`, `ScorerState`, `SegmentCadenceConfig`, `AdCadenceGate`, `EcpmBucket`), 4 internal (`SegmentScorer`, `SegmentScoringConfig`, `GeoTierResolver`, `EcpmBucketResolver`) và seam internal `SegmentHooks` + `SegmentStartup` (0 dòng ở `AdsHelper`/`AppPluginBase`). SDK tự khởi động engine từ `initPlugin` — phần đọc Remote Config + prefs chạy trên luồng nền, event tới sớm xếp hàng trong RAM (≤ 100, bỏ cũ nhất) rồi phát lại đúng thứ tự — và ghi `SESSION_START` (mỗi lần foreground, như fork), `REWARDED_AD_COMPLETED` (rewarded và rewarded interstitial), `AD_IMPRESSION` (mọi impression interstitial/app open/rewarded/rewarded interstitial; có giá trị thì kèm nhóm eCPM như fork); user property `seg_segment`, `seg_score`, `seg_revenue_tier` đi qua cổng consent (DENIED/UNKNOWN → không ghi, R-P1) và được gửi lại một lần khi tầng chuyển thành GRANTED. Khoá RC `seg_*` y hệt fork (DOC/61 G.3). API dump +298 dòng, `-` 0; H9 211/298 = 70,8 % (cùng `B64Url` của MIN24: 212/299); façade nhận 10 kiểu qua `tools/facade-allow.txt` (D15-220). Test +105 (84 dời từ IM, 21 mới).
<!-- secb -->
- Backend `/policy` thật, nửa server (SECB): `POST /policy/nonce` cấp nonce 32 byte dùng một lần (TTL `BRAIN_INTEGRITY_NONCE_TTL_S`, gắn app_id); google mode (`BRAIN_INTEGRITY_MODE=google`, owner-gated) chỉ chấp nhận khi gói/cert đăng ký ở `BRAIN_PLAY_PACKAGES` khớp, nonce server khớp đúng một của `nonce`/`requestHash`, `timestampMillis` đủ mới (`BRAIN_INTEGRITY_MAX_AGE_S`), `appLicensingVerdict` = LICENSED (`BRAIN_INTEGRITY_REQUIRE_LICENSED`, mặc định bật); rate limit theo app_id (429 + `Retry-After`); credential Google tạo một lần; không khoá API = 503 `auth_off` (sửa nốt `/policy` phục vụ app `dev`); verifier giả lập đầy đủ theo payload `googleapis@144.0.0` cho test, 0 mạng.
<!-- be3 -->
- Backend: view `v_show_rate_views` (show rate theo phiên view cho banner/native_collapse/rect: mẫu số = khoá `(session_id, placement)` có attempt ready, sink emu37 banner 6/6 = 1,0 thay vì 6/34); `v_show_rate_reasons` gắn `ad_show_fail` nhãn `fullscreen` về định dạng của attempt cùng phiên/placement, thêm cột `blocked_kill` (`show_blocked_kill_switch`) và `harness` (`show_when_act_null`), `seg_guardrail` vào `blocked_policy`; `tools/stack_load.py` không nạp đôi cùng một file (BE3).
<!-- ig -->
- Hướng dẫn tích hợp một nguồn `docs/integration/INTEGRATION.md` (IG; tiếng Việt kèm bản tiếng Anh ngắn): từ app trống tới quảng cáo/consent/IAP/đo lường/brain: repository theo artifact (GitHub Packages, AppLovin cho `-max`), token giấy phép và cert Play App Signing, xung đột backup-rules của AppsFlyer (`tools:replace`), Firebase bắt buộc, thứ tự gọi API trích từ dump, bảng khoá Remote Config, "Lỗi thường gặp", checklist phát hành và bảng "Phải làm khi nâng cấp" 1.3.0 → 2.2.0. `tools/integration_doc_check.sh` (CI/tay) kiểm tên API với `api/appplugin.api`, toạ độ Gradle, khoá manifest/resource/Remote Config với `src/main`, và bảng nâng cấp phủ mọi bản có mục "Phải làm khi nâng cấp" (`--final`: không còn khối `TODO-*`); README mục 3 thành tóm tắt + liên kết. Test mới `DC1`–`DC3` trong `tools/tests/dg_tools_test.sh`.
<!-- perf2 -->
- Chặng banner của bộ đo hiệu năng (PERF2): `tools/perf_gate.sh` chạm "Show Banner Only" sau vòng show rồi chờ 20 s, nên sink nhận `ad_request`/`ad_fill` banner và `perf_report.py` in `fill_ms.banner`; ngưỡng `fill_ms.banner` là `info` ở `redmi` và `emu37/33/28/26` (hard chỉ sau lần đo A/B ghép cặp).

  Số đo Task R 2.2.0 (APK `2.2.0`; trung vị / p90, ms, cold n = 5; test ad; bảng đầy đủ ở DOC/65 "Hiệu năng (2.2.0)"):

  | Máy | `initPlugin` | `onCreate` | `TotalTime` cold | Fill rate · fill/request | PSS settle / sau show, KB |
  |---|---|---|---|---|---|
  | Redmi (API 34), thật, `hard` PASS | 14 / 31,4 (2.1.0 16 / 22,2) | 15 / 33,6 | 376 / 474 | 1,0 · 1,0 | 172491 / 234017 |
  | Samsung (API 36), thật | chưa đo | chưa đo | chưa đo | chưa đo | chưa đo |
  | emu37, thông tin | 55 / 81,4 | 55 / 85,8 | 700 / 898 | 1,0 · chưa đo | chưa đo |
  | emu33, thông tin | 89 / 52 (chỉ số gate) | chưa đo | chưa đo | chưa đo | chưa đo |
  | emu28, thông tin | 74 / 152 (chỉ số gate) | chưa đo | chưa đo | chưa đo | chưa đo |
  | emu26, thông tin | 142 / 212,2 (2.1.0 94 / 119) | 185 / 231 | 750 / 811 | 0,93 · 0,65 | 111167 / 133963 |

  A/B ghép cặp n = 10 trên Redmi: `initPlugin` 13,5 vs 13,5 (2.1.0 vs 2.2.0), `TotalTime` 350 vs 352,5, nên không chậm hơn. Redmi lượt đầu INVALID (tải host 4,7), chạy lại một lần lúc yên PASS; `first_by_sdk` 0, first-run 393 ms; show `interstitial` 164 ms (≤ 200); `fill_ms.banner` n = 1 là thông tin. Ô "chưa đo" và INVALID, theo nguyên nhân: Samsung (màn hình bảo mật tự khoá giữa lúc đo); API 24 (không có image arm64-v8a, O11); SQL parity 13 view và `stack_load --merge-runs 2` (Docker tắt); CI và `release.yml` (bước P); vòng show perf trên emu37 (host bận, INVALID ui; số cold chỉ là thông tin); game flavor trên emu37 (INVALID, billing unavailable: môi trường emulator, APK 2.1.0 cho kết quả giống hệt).

### Sửa

<!-- lic -->
- Brain chỉ quyết khi giấy phép `VALID` (LIC, D3/D16-220): `DecisionEngine.decide` (public, vẫn dùng được trực tiếp)
  trước đây vẫn chạy bandit và ghi `brain_decision` khi giấy phép `INVALID` (2.1.0 SMK-1: 24 bản ghi từ playground);
  `AdsBrain` chỉ chặn `INVALID`, còn `PENDING` vẫn quyết. Nay cả hai dùng chung một vị từ private
  (`LicenseGate.verdict is Valid`; dump ABI không đổi).
<!-- psh -->
- Đường hiển thị native full (PSH, N07, D17-220 + Q-A): click (`onAdClicked`) và impression (`onLoggingImpression`, kèm doanh thu `PaidEvent.metaDirect`) tới muộn của native full Meta (`AdsFbPlNtFull`), và impression tới muộn của native full AdMob (`AdsMobPlNtFull.onAdImpression`), đi tới callback của đường (show/get) của chính lượt quảng cáo đó, không theo `flagShow` hiện hành của slot — như click AdMob ở 2.1.0 P3 (X lấy ở đường get, Y hiện ở đường show → impression/click của X từng tới callback show của Y, click Meta còn `afterClick` dialog của Y). Placement (`turnPl`) vốn đã theo lượt từ 2.0.0; `full_splash` không có lượt show thiếu attempt (dòng duy nhất là `not_contain_placement` lúc parse cấu hình, `AdsBase.kt:62`, BE lớp `config`). `ShowTurnPlacementTest` R6 giữ 31 site; R7 mới: 0 `ecmpLoad.flagShow` trong 4 thân callback click/impression của hai file native full. Test mới 4 (`C4`, `C4b`, `C5`, `R7`). API public/protected không đổi.

### Thay đổi hành vi

<!-- lic-hanh-vi -->
- Giấy phép `INVALID` hoặc `PENDING`: `DecisionEngine.decide` trả ứng viên `NO_ACTION` đầu tiên,
  `chosenActionProbability = 1.0`, `guardrailsApplied = ["license_<nhãn>"]` (`license_INVALID:<lý do>` /
  `license_PENDING`), không bandit, không `brain_decision`, không vào `BrainInspector`; `feedback` của quyết định bị
  chặn (`d_<player>_unlicensed`) bị bỏ qua kể cả sau khi verdict thành `VALID` (không học, không `brain_feedback`);
  `observe`/`applyPolicy`/`resetPlayer` không đổi. `AdsBrain` không quyết (luật tĩnh) cho tới khi verdict `VALID`. Tín
  hiệu không phải quyết định — hàng `experiment_exposure` của phiên và `onFirstPurchase` tới CRM — được **giữ trong RAM
  khi `PENDING`** (tối đa 8, bỏ cũ nhất): verdict đầu `VALID` phát chúng theo thứ tự, `INVALID` bỏ hết. Cold start gọi
  `AdsBrain.onSessionStart()` trước khi khoá giấy phép bắt đầu, nên hàng membership của phiên cold start tới server ngay
  sau verdict `VALID` (DOC/57 §B.2). Hàng exposure đang giữ mà người chơi đã đổi (vd. `configure { playerId }` trước
  verdict) bị bỏ, vì `user_id` đọc lúc phát sẽ lệch bucket. Đếm lượt mua ngoài brain cập nhật ngay. `PENDING` mãi (app
  không đi qua `AppPluginBase`/`AdSdkStartup` nên khoá không bắt đầu): brain tắt, không phát gì. Demo
  `BrainPlaygroundActivity` hiện dòng `license: <nhãn>`. `iap/PlayIntegrityGuard` (mã chết, internal) ghi KDoc "xoá ở
  3.0" (D12-220, không annotation).
<!-- seg-hanh-vi -->
- Mặc định quảng cáo không đổi (D10-220): guardrail fullscreen chéo loại của segment **tắt** (`SegmentCadenceConfig.DEFAULT.globalGuardrailMillis = 0`; fork IM 180 s, SC 60 s). Khi RC `seg_cadence_guardrail_seconds` > 0: `showInterstitial`/`showOpenAd` (mọi placement, kể cả `full_splash`, như fork) gọi trong khoảng đó kể từ fullscreen gần nhất (interstitial, app open, Full2 hoặc rewarded interstitial hiện được; đồng hồ bền ở prefs khoá `ads_last_fullscreen_shown_millis` như fork, sống qua kill) trả `false` và gửi `ad_show_fail reason=seg_guardrail` (BE3: `blocked_policy`). Guardrail chỉ có hiệu lực sau khi segment nạp xong trên luồng nền (vài chục ms sau `initPlugin`); trước đó không chặn. Engine ghi prefs `seg_*` từ lần mở đầu.
<!-- min24-hanh-vi -->
- SDK `minSdk` 26 → **24** (MIN24, D2/D9-220) ở `appplugin`, `appplugin-game`, `appplugin-max` (`build.gradle.kts`,
  `gradle/module-defaults.gradle`) và demo (`app/build.gradle.kts`, mọi flavour); manifest merge và 3 AAR mang
  `minSdkVersion="24"`. Tách token giấy phép dùng codec base64url Kotlin thuần (`license/B64Url`, internal, RFC 4648 §5)
  thay `java.util.Base64` (API 26); ngày `exp=` của dòng `license: VALID` tính theo UTC bằng `SimpleDateFormat` thay
  `java.time.Instant` (cùng chuỗi `yyyy-MM-dd`). Luật token không đổi: vẫn từ chối đệm `=`, ký tự ngoài bảng url và bit
  thừa (`INVALID (malformed)`). Lint `NewApi` trên 3 module SDK: 6 lỗi → 0.

### Phải làm khi nâng cấp

<!-- seg-app -->
- App đang dùng fork `:adsplugin` (Invoice Maker, Speaker Clean): xoá thư mục `segment/` của fork (FQN giữ, 0 import đổi); bỏ các lời gọi SDK nay tự làm — `UserSegmentEngine.instance.init`, `EcpmBucketResolver.init`, `recordEvent(SESSION_START)` ở `onStart` (SDK ghi mỗi lần foreground qua `SessionTracker`; giữ thì đếm đôi), `REWARDED_AD_COMPLETED` trong `onAdRewarded` (SC `MainActivity.kt:1151`, `CleanerScreen.kt:346`; giữ thì đếm đôi), `AD_IMPRESSION` ở `FirHelper`. `UserSegment` có 6 giá trị (SC thiếu `POWER_USER`, `CHURN_RISK`): ba `when` exhaustive của SC `PaywallPlaybook.kt:44-81` cần 2 nhánh. Giữ guardrail cũ = đặt RC `seg_cadence_guardrail_seconds` (IM 180, SC 60); lý do chặn đổi tên `global_guardrail` → `seg_guardrail`. `recordEvent` và `setRevenueTierUserProperty` gọi lúc engine đang nạp (sau `initPlugin`, thường vài chục ms) được xếp hàng sau event hook, giữ thời điểm gọi, và phát lại (không mất, không ghi đè lịch sử đã lưu); tác động (StateFlow, snapshot, prefs) thấy được sau khi nạp; gọi trước `initPlugin` thì bỏ. Trước khi nạp xong (vài chục ms sau `initPlugin`) `segment.value` = `COLD_START` và `geoTier` = 4: app thu `segment` (StateFlow) hoặc đọc sau khung hình đầu, không đọc một lần lúc khởi động (SC `MainActivity.kt:954`, `:697`). Test `userSegmentHasExactlyTheFourSpecSegments` của SC lỗi thời (6 giá trị), không dời.
<!-- min24-app -->
- App `minSdk` 24/25 dùng SDK không cần `tools:overrideLibrary`; nếu đã thêm để ép merge với SDK 26 thì bỏ. App `minSdk`
  ≥ 26: không phải làm gì.
<!-- rollup -->
- Bảng gộp 2.1.0 → 2.2.0: chỗ giữ, nội dung chốt ở task tương ứng.

### Công cụ

<!-- t0b -->
- Khoá làn `tools/lanelock.sh` (T0b): dời vào `tools/` có test (`tools/tests/lanelock_test.sh`, chạy ở CI), hàng đợi vé FIFO cho `gradle`/`emugate`/`real` (vé của tiến trình đã chết tự bị dọn), kind mới `boot` (độc quyền theo cổng) và `cpu` (chung, tối đa 2, chờ khi có `boot`); thư mục trạng thái = `LANELOCK_DIR`, wrapper 3 dòng ở `.superpowers/sdd/locks/lanelock.sh` giữ lệnh cũ. Sửa CI Linux (T0f): `mtime` di động (ưu tiên GNU `stat -c %Y`, rồi BSD `stat -f %m`, giá trị không phải số coi là 0), vé rỗng < 2 s không còn bị dọn nhầm trên Ubuntu; thêm F6 mô phỏng GNU `stat`.
<!-- t0c -->
- Task T0c (2.2.0, D6-220, bài học 11) — `tools/lib/precheck_env.sh` chạy trước khi cài APK ở `device_gate.sh` và `perf_gate.sh`: máy thật phải có Wi-Fi `CONNECTED` (`dumpsys connectivity`), keyguard không hiện, pin ≥ 30 %, và host không ngủ được (`caffeinate` hoặc `pmset -g`); thiếu một điều kiện = `RESULT: INVALID (precheck)` (exit 4), dòng `precheck env net= lock= batt= host=` vào log; emulator chỉ kiểm host. `device_gate.sh --app <pkg>/<activity>` chạy cổng cơ bản (cài, hai lần cold start, StrictMode, FATAL/ANR/native) cho app bất kỳ: `--mapping` tuỳ chọn (không có thì dòng `by-sdk: không mapping`, không retrace), `--ez force_eea true` chỉ cho gói mặc định `test.sdk.zd`, kết hợp với `--measure/--iap/--banners/--consent/--drc` bị từ chối. Fixture thật chụp từ Redmi 65c9b673 + `pmset` host. T0e (IG-V): ở chế độ `--app` các kiểm chỉ của demo thành dòng info, không FAIL — canary StrictMode (`STRICT_ARMED_MIN`) và `af_app_id` (`AF_STARTED_MIN`) mặc định 0 (env vẫn ghi đè), sink 8089 + reverse + kiểm sink tăng dòng chỉ chạy khi có `--sink-port`/`--sink-dir` (không có thì dòng `sink: không dùng (--app)`); kiểm bắt buộc (FATAL/ANR/native, chuỗi R8, by-sdk khi `--mapping`, precheck, api=) giữ nguyên; chế độ gói mặc định không đổi (AP6–AP9).
<!-- t0d -->
- Task T0d (2.2.0, D15-220) — `api_facade_check.sh` đọc `tools/facade-allow.txt` (cột 1 = FQN kiểu public mới có chủ đích, rồi dấu task + lý do; in `new: <kiểu> (allow)`; SEG điền 10 dòng) và chỉ miễn `BuildSha` ở gói `com.app.plugin` (trần 78 → 88 dòng; lý do: hai nhánh mới của khối kiểm lớp mới, mỗi nhánh một điều kiện đọc được); `invalid_classes.sh` hết `printf | grep -q` (here-string; không còn `printf: write error: Broken pipe` khi SIGPIPE bị bỏ qua); `dg_harness.sh` dùng `ln -sfn` (cây thử dùng lại không tạo `backend/backend`); `absent_class_allowlist.txt` thêm `org.apache.http.conn.ConnectTimeoutException` (nguồn Pangle, D17 2.1.0); nhãn test precheck/`--app` của T0c đổi `PC1–PC5`/`AP1–AP5` (trùng `P1–P5` của sink-port).

### Giới hạn đã biết

<!-- secb-gioi-han -->
- `/policy` (SECB): kho nonce nằm trong process (nonce cấp ở instance này không dùng được ở instance khác; rate limit cũng theo instance); chưa có client SDK và chưa chạy Play Integrity thật (2.3.0, sau O3); google mode cần `--max-instances=1` hoặc session affinity tới khi có kho nonce chung; cờ giấy phép bật (mặc định) thì tài khoản UNEVALUATED (chưa đăng nhập Play) nhận policy mặc định; bucket hạn mức theo app_id có thể bị người giữ khoá API làm cạn (chỉ ảnh hưởng tính sẵn sàng, fail-closed) -> 2.3.0; `PlayIntegrityGuard` giữ nguyên (xoá ở 3.0).
<!-- seg-gioi-han -->
- Không port `segment.PlayIntegrityGuard` của Speaker Clean (D12-220: giữ ở app hoặc chờ client SEC-B 2.3.0). Ngưỡng `seg_ecpm_high_threshold_micros` mặc định 500 000 micros **mỗi impression** (giá trị fork, tương đương eCPM 500 USD) nên gần như luôn ra `LOW`; giữ nguyên để không đổi phân loại khi ghép app, chỉnh bằng RC. Khác fork có chủ ý (D27-220): impression Meta direct và impression giá trị 0 nay được đếm vào `AD_IMPRESSION` (không nhóm eCPM) — fork chỉ đếm paid event AdMob; `rewarded_interstitial` (1.9.0, fork chưa có) trong phạm vi. `AD_IMPRESSION` đi từ telemetry `AdEventLog.revenue`, nên `LogEventServer.logAdRevenue` (public, v1) của app cũng nạp segment. Hàng đợi khởi động giữ tối đa 100 event; tràn thì bỏ cái cũ nhất. Một ngoại lệ giữa chừng lúc engine init làm segment tắt cho process đó (ghi `logE`); lỗi đọc Remote Config được bắt và lùi về mặc định.
<!-- min24-gioi-han -->
- API 24–25 chưa đo trên thiết bị/emulator: host chỉ có image arm64-v8a API 26/28/33/37 (O11: cài image arm64-v8a
  API 24 để smoke). Cổng của bản = lint `NewApi` 0 + unit; đọc cert ký dưới API 28 đi nhánh `GET_SIGNATURES` có sẵn.

## [2.1.0] - 2026-10-03

"2.1.0 — dời mã game, driver bền, hiệu năng đo được, bảo mật bậc A" (plan docs/superpowers/plans/2026-10-03-sdk-2.1.0-plan.md).

### Thêm

<!-- perf -->
- Hiệu năng đo được (PERF, D5): công cụ `tools/perf_gate.sh` + `tools/perf_report.py` (mục Công cụ, `t0f`) đo mỗi máy ≥ 5 lượt cold start (`TotalTime`, `onCreate`, `initPlugin`, prefs, StrictMode), request → fill theo format, fill rate, show latency và PSS, rồi so `tools/perf-thresholds.json`; ngưỡng cứng chỉ trên máy thật (Redmi; Samsung còn tạm), emulator và PSS là thông tin. Số đo Task R (APK 2.1.0 `26609b7` so với APK 2.0.0 đóng băng; trung vị / p90, ms, cold n = 5; quảng cáo là test ad; bảng đầy đủ ở DOC/65 "Hiệu năng"):

  | Máy | `TotalTime` cold | `onCreate` | `initPlugin` | request → fill `interstitial` | fill rate · fill/request | show latency `interstitial` | PSS settle / sau show, KB |
  |---|---|---|---|---|---|---|---|
  | Redmi (API 34), thật, `hard` PASS | 364 / 452,4 → **379 / 418,8** | 12 / 25,8 → **17 / 25** | 11 / 23,4 → **16 / 22,2** | 2421 / 3407 → **2891 / 3116** (A/B gộp 2465,5 / 3517) | **1,0 · 0,95** | 144,5 → **133** | 189802 / 256439 → 188227 / 254155 |
  | Samsung (API 36), thật | chưa đo (máy vắng) | chưa đo (máy vắng) | chưa đo (máy vắng) | chưa đo (máy vắng) | chưa đo (máy vắng) | chưa đo (máy vắng) | chưa đo (máy vắng) |
  | emu37 (API 37), thông tin | 1037 / 1160,6 → 1066 / 1248,2 | 85 / 174,6 → 70 / 243,4 | 83 / 173,6 → 33 / 176,4 | 2013 / 3247,2 | 1,0 · 0,95 | 173 | 160720 / 184692 → 156132 / 180849 |
  | emu33, thông tin | 864 / 983,4 → 1555 / 1901,6 | 38 / 64,6 → 75 / 148,2 | 34 / 59,2 → 73 / 128,8 | 3790 / 4887 | 1,0 · 0,89 | 396 | 142248 / 156706 → 107557 / 152367 |
  | emu28, thông tin | 719 / 835,2 → 796 / 835,6 | 67 / 78,2 → 88 / 104,6 | 39 / 58 → 73 / 82,8 | 2291 / 2738 | 1,0 · 1,0 | 158 | 103751 / 146324 → 108062 / 156008 |
  | emu26, thông tin | 657 / 774 → 702 / 788,4 | 131 / 163,4 → 104 / 126,4 | 104 / 154 → 94 / 119 | 4194 / 5348,2 | 1,0 · 0,95 | 188 | 104786 / 117081 → 104668 / 112939 |

  Redmi: lượt đầu của 2.1.0 (máy tải ≈ 4) FAIL vì đuôi warm-up (`onCreate` p90 50,6; `TotalTime` p90 659), chạy lại một lần lúc yên PASS (cả hai log giữ); `prefs_waited_ms` 0, `by_sdk` 0, `first_by_sdk` 0 (ngưỡng ≤ 1, D16) ở mọi lượt. Fill 2.1.0 không hồi quy so với 2.0.0 trên A/B ghép cặp xen kẽ (n = 10 mỗi bên: `interstitial` +1,8 %, `rwi` +11,7 %, `opened` +10,2 %, nằm trong chênh lệch lượt-với-lượt của chính 2.0.0 là 7–32 %); mọi số fill đều dưới ngưỡng D24. Emulator không cho chiều thống nhất giữa các API và các lượt chạy lại (host tải 4–16), nên chỉ là thông tin. Samsung chưa đo vì máy vắng; PSS chưa có ngưỡng số (baseline cho 2.2.0).
<!-- sec-a -->
- Khoá giấy phép offline (SA1): token `ALP1.<payload>.<chữ ký>` trong meta-data `com.app.plugin.LICENSE`, chữ ký Ed25519 (RFC 8032, xác minh Kotlin thuần, 0 phụ thuộc mới) ràng buộc gói, SHA-256 cert ký, hạn và tính năng `ads`/`brain`. Xác minh một lần mỗi process trên luồng `appplugin-license` (0 IPC thêm trên main; đọc cert trên luồng đó); VALID in một dòng `license: VALID kid=<kid> exp=<yyyy-mm-dd>`; `sdk_health` mang `license` (`VALID`, `INVALID:<lý do>`, `PENDING`), INVALID gửi một lần với `networks=unlicensed`.
- Công cụ phát giấy phép (SA2): `tools/license_issue.py keygen` (khoá riêng chỉ ngoài repo, `chmod 600`, file mở đầu `ALPK1.`; đường dẫn trong repo bị từ chối) / `issue` (gói, cert, hạn, tính năng) / `verify`.
<!-- sec-b -->
- Bảo mật bậc B (SB): backend `GET /policy` — `X-Api-Key` kiểm trước hết (401), `X-Integrity-Token` tuỳ chọn; `BRAIN_INTEGRITY_MODE` = `off` (mặc định) | `fake` | `google` (import lười, owner-gated O3).
  Policy đã đăng ký chỉ trả khi verdict chấp nhận; còn lại fail-closed về artifact mặc định `{app_id, source:"default", reason, policy:{version:0}}`
  với `reason` ra dây thuộc `integrity_off`, `no_token`, `not_accepted`, `no_policy` (mã mịn chỉ ở log server); log không bao giờ chứa token.
<!-- sec-c -->
- Bảo mật bậc C-lite (SC1): dấu vết dựng `appplugin/<phiên bản>/<git short sha>` (`BuildInfo.ID`, sinh lúc build, còn trong dex sau R8 nhờ consumer rule; `sdk_health` thêm trường `build`) để truy nguồn APK (build sha từ APPPLUGIN_BUILD_SHA khi dựng từ archive). Rule bỏ log SDK là tuỳ chọn (`docs/integration/appplugin-strip-logs.pro`, `-assumenosideeffects` trên `LogUtil.logD/logW`), không nằm trong `consumer-rules.pro`.
<!-- be1 -->
- Backend: view `v_show_rate_reasons` tách lý do chặn của `ad_show_fail` (policy / brain / guard / entitlement / not_ready / config / other) và `organic_show_rate` = shows / (attempts_ready − chặn có chủ đích); `v_show_rate` giữ nguyên schema. `tools/stack_load.py` nạp kèm `g12-*/events.jsonl` để `v_paywall_funnel` có hàng. Báo cáo ngày: 12 bảng.

### Sửa

<!-- p1 -->
- Lần nạp prefs đầu chậm (P1, M47; Samsung sau `pm clear` ≈ 313 ms > hạn chờ warm-up 250 ms, StrictMode by-sdk 7): khi `PrefsWarmup.await()` hết hạn, 9 cờ đặt lại lúc mở app (`IS_FETCH_FIREBASE` + 8 `InitAds.*`) ghi trên luồng warm-up ngay sau khi nạp xong (`PrefsWarmup.onLoaded`), không còn trên main. `PreferenceHelper.init`, khôi phục tầng consent (R-P1), `TIME_OPEN` (đọc + ghi) và `EXPERIMENT_GROUP` vẫn đồng bộ trên main trước `initPlugin` (session_number, ab_test không đổi). Warm-up kịp: y như cũ (một edit 11 khoá trên main). Warm-up mở cả 4 file prefs gốc trước rồi mới chờ từng file (P1-2, D16): Android nạp 4 file song song, nên lần đọc `App_prefer_file` của `initPlugin` không còn chặn main ở lần đầu chậm.
<!-- p2 -->
- Comment, trần file và test từ minors 2.0.0 (P2), không đổi hành vi công khai: KDoc `DurableEvents` (chỉ 400/413 xoá bản đĩa, 401 ở lại), header `BiddingPartners` (lưu `""` khi RC bị từ chối; dòng presence cả tập đóng) và logE từ chối của nó giữ tên sai đầu tiên + số lượng trong 512 ký tự; log restore artifact brain bị từ chối nói đúng "giữ artifact đang chạy"; `AdsPolicyTiers.onCountryKnown` áp lại không parse lần hai nên logE cấp trường chỉ một lần (R-T4-2 a). `SdkFileSizeGateTest`: trần cho `AdsMobNtFullBinder` 104, `AdsMobNtFullBackground` 156, `AdsFbPlNtCl` 338, `AdsFbPlNtFull` 673, `license/*`, `RcSectionRegistry`, và cổng mới cho `appplugin-game` (382). Test mới 10 (+1 cổng `-game`): `NB1`, `MX1`–`MX3` (ba lời gọi core → MAX qua seam), `S4b`, `S7b`, `IO1`, `RP3`–`RP5` (RP2 chỉ nhận khoá app-contract cho tên đọc bằng chuỗi; bộ quét thêm `res/color`, `integer-array`, `item type=`, `declare-styleable`, `com.app.plugin.R.`). Demo: `MainActivity.runDrive` chỉ chạy khi `flagTest` bật. Tooling: `perf_test.sh` bỏ dấu backtick trong nhãn R10; `stack_load.with_g12` khử trùng theo đường dẫn đã resolve (`t210-p2-*.log`).
<!-- p3 -->
- Đường hiển thị (P3, M11/M15): native full AdMob (`AdsMobPlNtFull`) — click tới muộn của quảng cáo lượt trước đi tới callback của đường (show/get) của chính lượt đó, không theo `flagShow` hiện hành của slot (R-C1a m2: X lấy ở đường get, Y hiện ở đường show → click của X từng tới callback show của Y và `afterClick` dialog của Y). Rewarded interstitial AdMob (`AdsMobPlRwi`): `onAdDismissed`/`onAdRewarded` tới muộn mang placement lượt của chính quảng cáo (`turnPl`) thay vì placement hiện hành của slot — ngoại lệ rwi của c1b (2.0.0) hết hiệu lực từ 2.1.0. Rewarded AdMob/Meta (`AdsMobPlrewarded`, `AdsFbPlrewarded`): `onAdRewarded` tới muộn cũng mang placement lượt của quảng cáo. `ShowTurnPlacementTest` R6 quét 31 site, kể cả reward (27 của R5 + 2 rwi + 2 rewarded). Chưa đổi: Meta `AdsFbPlNtFull` và impression của native full vẫn chọn callback theo `flagShow` hiện hành; hai lượt cùng đường thì click muộn tới callback hiện hành của đường đó. Thứ tự cổng show không đổi; API public/protected không đổi.

### Thay đổi hành vi

<!-- sec-a-hanh-vi -->
- Thiếu hoặc sai token giấy phép (`missing`, `malformed`, `kid`, `sig`, `pkg`, `cert`, `expired`, `feat`; lỗi khi xác minh, hay chưa có verdict sau 2 s) → không quảng cáo, không brain: mạng quảng cáo không khởi tạo, `AdsHelper.isDisableAds` trả `true` (nhãn show bị chặn như kill switch), `AdsBrain` không quyết; đúng một `logE` `license: INVALID (<lý do>) — quảng cáo và brain tắt`, không ném. IAP, consent, logevent không đổi; giấy phép không mở thứ consent đóng. Chưa có verdict khi mạng sắp khởi tạo → chờ trên luồng riêng (≤ 2 s), main không chờ.
<!-- p1-hanh-vi -->
- Lần mở đầu chậm (warm-up prefs hết hạn): 9 cờ `Constants.Common.IS_FETCH_FIREBASE` và `Constants.InitAds.*` được đặt lại về `false` SAU `Application.onCreate` (trên luồng `appplugin-prefs-warmup`, ngay khi file prefs nạp xong) thay vì trong đó. SDK, app demo và app tiêu thụ hiện không đọc 9 khoá này; app host nào tự đọc chúng ngay lúc mở có thể thấy giá trị của phiên trước trong khoảng đó. Lần đầu chậm: by-sdk kỳ vọng ≤ 1 (đọc consent).
<!-- g1-hanh-vi -->
- **App chỉ dùng `appplugin` không còn chạy phần game** (G1, D7): 5 section Remote Config game (`purchase_routing_android`, `battle_pass_android`, `recurring_grants_android`, `offerwall_config_android`, `crm_bridge_android`) và tín hiệu brain → CRM chỉ chạy khi app có artifact `appplugin-game`. Vắng nó, khi bật log SDK có một dòng `game: không có com.app.plugin.game.GameModule — 0 section game` (0 lỗi, 0 I/O); có nó thì một dòng `game: GameModule installed — 5 section`. Trái với DOC/61 §G.3 bản 2.0.0 ("RC game áp ở mọi app"). APK utility không còn chứa lớp game nào (keep `GameModule` ra khỏi `consumer-rules.pro` gốc).

### Đổi API (phá vỡ có chủ ý)

<!-- g1 -->
- **Dời mã game sang `appplugin-game` (G1, commit phá vỡ ABI duy nhất của 2.1.0):** `PurchaseRouter`, `BattlePass`, `RecurringGrants`, `OfferwallManager`, `CrmBridge` và `GameModule` sang artifact `com.app.plugin:appplugin-game` (FQN giữ nguyên; `api/appplugin-game.api` mới). Dump gốc `api/appplugin.api` 5605 → 5224 dòng: 32 khối lớp game ra khỏi gốc + 5 thành viên đổi tên mangle vì `BrainSignalListener` và `RcSectionRegistry` thành public (SPI). SPI nội bộ mới, opt-in mức ERROR `@com.app.plugin.AppPluginInternalApi`: `RcSectionRegistry.register`, `BrainSignalListener`, `GameHost` (country, userId, subscriptionState). H9 (cộng hai module) 200/277 = 72,2 % (gốc riêng 199/271 = 73,4 %). Cấu hình Android chung của module con ở `gradle/module-defaults.gradle` (M32). Façade `BASELINE` mặc định của cả hai module (`BASELINE_ROOT`, `BASELINE_GAME`) là `e2ed528` (chính commit G1, dời bằng commit theo sau theo D27).

### Phải làm khi nâng cấp

<!-- g1-app -->
- **Mã game (G1):** app tiện ích không dùng RC game/CRM/BattlePass/PurchaseRouter/RecurringGrants/Offerwall: 0 việc. App dùng bất kỳ lớp nào trong số đó: thêm `implementation("com.app.plugin:appplugin-game:<cùng bản>")` (kéo `appplugin` cùng bản; import và FQN không đổi). Không dùng `@AppPluginInternalApi`.
<!-- sec-a-app -->
- Xin token giấy phép cho `applicationId` + SHA-256 cert ký phát hành (Play App Signing: cert của Play), rồi khai trong `AndroidManifest.xml`: `<meta-data android:name="com.app.plugin.LICENSE" android:value="ALP1.…" />` (demo: `manifestPlaceholders`, `-PlicenseToken=`). Không khai → không quảng cáo, không brain.
<!-- sec-c-app -->
- Muốn bỏ chuỗi log SDK khỏi dex (tuỳ chọn, không bắt buộc): thêm `docs/integration/appplugin-strip-logs.pro` vào `proguardFiles` của release của app (production vốn đã im lặng; không bật ở bản chạy cổng thiết bị của SDK).
<!-- rollup -->
- Gộp nâng cấp 2.0.0 → 2.1.0 (H10): bảng bốn hàng, gồm ba việc phải làm (token giấy phép; mã game khi app dùng; log SDK tuỳ chọn) và một hàng thay đổi hành vi không cần việc (ứng với ba mục ở "Thay đổi hành vi": giấy phép, mã game, lần mở đầu chậm; hàng 4 còn gộp đường hiển thị P3); bản cũ hơn đọc bảng 1.3.0 → 2.0.0 ở mục `[2.0.0]` (lịch sử không sửa, D15). Mỗi hàng là việc app cần xem; hàng "Không việc" = không cần làm gì (nguyên văn ở các mục cùng tên của `[2.1.0]`).

  | Từ bản | Việc phải làm | Ký hiệu |
  |---|---|---|
  | 2.0.0 (token giấy phép) | Xin token cho `applicationId` + SHA-256 cert ký phát hành, khai meta-data `com.app.plugin.LICENSE`; không khai thì không quảng cáo, không brain (README mục 9, `tools/license_issue.py`) | `sec-a-app`, `sec-a-hanh-vi` |
  | 2.0.0 (mã game) | App tiện ích không dùng RC game/CRM/BattlePass/PurchaseRouter/RecurringGrants/Offerwall: 0 việc. App dùng chúng: thêm `appplugin-game:<cùng bản>` (import và FQN không đổi); không có nó thì 5 khoá RC game và tín hiệu brain → CRM không chạy | `g1-app`, `g1-hanh-vi` |
  | 2.0.0 (log SDK, tuỳ chọn) | Muốn bỏ chuỗi log SDK khỏi dex: thêm `docs/integration/appplugin-strip-logs.pro` vào `proguardFiles` của release (không bật ở bản chạy cổng thiết bị) | `sec-c-app` |
  | 2.0.0 (hành vi) | Không việc: lần mở đầu chậm đặt 9 cờ `Constants.Common.IS_FETCH_FIREBASE`/`Constants.InitAds.*` sau `Application.onCreate` trên luồng nền; `onAdDismissed`/`onAdRewarded` của rwi và rewarded, click/impression của native full theo lượt của chính quảng cáo (ngoại lệ rwi của `c1b` 2.0.0 hết hiệu lực từ 2.1.0) | `p1-hanh-vi`, `p1`, `p3` |

  Lên 2.1.0 từ 2.0.0: ba hàng đầu (token là việc bắt buộc, hai hàng sau chỉ khi áp dụng). Từ bản cũ hơn: cộng các hàng của bảng 1.3.0 → 2.0.0 ở `[2.0.0]`.

### Công cụ

<!-- perf-nguong -->
- PERF-B (2.1.0): `tools/perf-thresholds.json` lấy từ số đo APK 2.0.0 đóng băng: khởi động Redmi (`onCreate`/`initPlugin`/`TotalTime`) `hard` từ cold n=5 ×1,2 với sàn nhiễu 20/40 ms (D22), `first_by_sdk` max 1; fill/show Redmi `hard` từ A/B ghép cặp (D24, ×1,5 vì phụ thuộc mạng; banner không được yêu cầu nên không có ngưỡng); Samsung vẫn tạm (vắng máy); 4 emulator `info`.
<!-- t0b -->
- Task 0b (2.1.0, D31, M45, M49) — `--consent` bền trên MIUI (`tools/lib/consent_miui.sh`, mới): chạm form UMP theo bounds của node trong dump (vắng node: toạ độ fixture 0,72 H, thang 72/71/73 %, không còn chạm mù 0,70 H); mọi lần chạm form được xác minh bằng dòng của app (`consent tier=DENIED` sau "Do not consent", `flushed N held after DENIED` sau "Consent") ≤ 10 s, thử lại ≤ 2 rồi FAIL; quảng cáo rwi còn che (focus AdActivity) thì gửi `back` (≤ 2) trước "Privacy options"; form vắng sau `pm clear` thì chờ lại 1 lần rồi `INVALID (cold guest)`. Dump `uiautomator` thật của Redmi 65c9b673 làm fixture (`tools/tests/fixtures/ui/`). Test shell dg_core 134 → 140 (F3a, F3b, U1–U4).
<!-- t0c -->
- Task 0c (2.1.0, M46) — tìm nút bền trên One UI: `tools/lib/ui_find.sh` (`mg_find_text` giờ gọi nó; cùng đường cho measure, iap, consent, rwi, drc, banners) thu rèm thông báo (`cmd statusbar collapse`) trước dump, một `KEYCODE_BACK` khi cửa sổ focus không phải demo, rồi kéo chậm ≤ 6 xuống / ≤ 12 lên; không thấy và có cờ `--drive-intent` thì `am start … --es drive <action>` (demo `MainActivity` gọi đúng hàm click của nút, log `drive=intent action=<a>`), không cờ thì `INVALID (ui)`. Fixture: dump thật của Samsung (focus NotificationShade, máy khoá PIN) + 3 khung danh sách demo dựng lại từ bố cục (thay bằng dump thật khi máy mở khoá). 5 test mới trong `dg_measure_test.sh`.
<!-- t0d -->
- Task 0d (2.1.0, D30, D32-2, D33, M06, M48, M51–M53) — lớp INVALID môi trường ở mọi cổng thiết bị qua MỘT bộ phân loại (`tools/lib/invalid_classes.sh`): `(package update)` = `killDueToPackageUpdate` của pid app, `(gms persistent)` = `Killing <pid>:<pkg> … dying proc com.google.android.gms.persistent`, `(billing unavailable)` = `IapHelper unavailable: 3`; chỉ khi lượt hỏng và không có FATAL/ANR/native của app (crash thắng mọi lớp), `RESULT: INVALID <lớp>`, rc 4, chạy lại ≤ 1; `--iap` đếm cửa sổ sau mua từ cú chạm show đầu (meta `first_tap`, `iap_check.py`) nên hàng khởi động xả muộn không còn làm `buy_sub` đỏ oan; `tools/emu_boot.sh` kiểm `EMU_BOOT_MAX`/`EMU_PROP_T` (rc 2) và chờ yên (`tools/lib/emu_quiet.sh`: `sys.boot_completed`, load1 ≤ `EMU_QUIET_LOAD`, `versionName` GMS ổn định hai lần cách 30 s, trần 240 s → rc 3 `INVALID (contention)`); không thăm `init.svc.bootanim`.
<!-- t0e -->
- Task 0e (2.1.0, M01, M19–M21, M27, M31, M35) — gia cố test công cụ từ minors 2.0.0: biên `--min-pct 70` của `public_types.sh` (7/10 qua, 6/10 rớt); phạm vi cửa sổ `remove_ad` của `--iap` và chặng gap của `--measure` (hàng trước cửa sổ không được tính); `--measure` khẳng định lượt rwi đầy đủ của pha A có đúng 1 intro / ad_show / reward / dismiss (`rwi_legs.sh`); `--drc` in độ trễ chạm → hàng `brain_decision`; nhãn "shown (ad focus)"; `pom_check.sh` chọn bản mới nhất trong `build/repo` và đòi scope `compile` của phụ thuộc `appplugin`; `module_check.sh` Q5 (`appplugin-max` khai đúng 13 group:artifact); B8 của `dg_banners_test.sh` chờ trên fifo thay vì phụ thuộc tốc độ máy; `perf_test.sh` chép `ui_find.sh` vào cây thử (10/10).
<!-- t0f -->
- Công cụ T0f: `tools/perf_gate.sh` đo hiệu năng một máy trên APK đóng băng (≥ 5 lượt cold `am start -W` có `onCreate`/`initPlugin`/`prefs`/StrictMode, một vòng show, `dumpsys meminfo` PSS, một lượt first-run; không phán ngưỡng, chỉ `PASS|INVALID`), `tools/perf_report.py` tính trung vị + p90 (lượt đo và sink: request→fill, fill rate, show latency, time-to-first-request) và so `tools/perf-thresholds.json` (tạm; `hard` máy thật, `info` emulator/mạng/PSS).
<!-- t0g -->
- Công cụ T0g: `tools/api_dump.sh`, `tools/api_facade_check.sh` và `tools/public_types.sh` hiểu ba module (`root`, `appplugin-game`, `appplugin-max`): `--module` chọn dump/baseline façade riêng (`BASELINE_ROOT`/`BASELINE_GAME`), module 0 lớp không tạo dump và `--check` chỉ qua khi cũng vắng file (có lớp mà vắng file = FAIL), H9 cộng mọi module; `tools/public-types.txt` đổi sang `<module>:<gói>.<Tên>` (cùng 73 kiểu `root:`); CI chạy thêm test/lint `-game`, dump `--check` và façade cho `-game`.
<!-- perf-first-run -->
- Công cụ PERF-fix: `tools/perf_report.py` đọc lượt first-run đúng dạng `perf_gate.sh` phát (`{"first_run":true,…}` không có khoá `run`), nên các chỉ số `first_*` (gồm `first_by_sdk`) không còn bị bỏ sót và ngưỡng `first_by_sdk` không còn bị coi là "thiếu dữ liệu"; test R11 ở `tools/tests/perf_test.sh`.
<!-- facade-uu-tien -->
- Công cụ FC1: `tools/api_facade_check.sh` đọc điều kiện BuildSha đúng ý (`A || { B && C; }`: lớp `BuildSha` sinh lúc build chỉ được nhận thêm khi `gradle/build-sha.gradle` khai báo nó, mọi lớp internal khác vẫn theo kiểm mẫu thường), file về trần 78 dòng; test FC1 ở `tools/tests/dg_tools_test.sh`.

### Giới hạn đã biết

<!-- g1-gioi-han -->
- **G1:** SPI `@AppPluginInternalApi` là API nội bộ giữa các artifact appplugin, có thể đổi giữa hai bản không báo; opt-in chỉ được ép với Kotlin — mã Java gọi được SPI mà không cần opt-in; `appplugin-game` luôn phải cùng bản với `appplugin`. Test của `appplugin-game` thấy `internal` của gốc qua friend-path chỉ trên `testDebugUnitTest` (không có `testReleaseUnitTest`). 3 helper test gốc (`ServerEventCapture`, `HeldCall`, `LogEventTestIsolation`) được chép sang `appplugin-game/src/test` (trùng lặp chấp nhận, sửa thì sửa cả hai).
<!-- sec-gioi-han -->
- Giới hạn bảo mật — phải đóng TRƯỚC khi chủ dự án bật `BRAIN_INTEGRITY_MODE=google` (O3): verdict chưa ràng `requestDetails` của token (package, nonce/request hash, độ mới timestamp) và `app_id` — hôm nay token bị bắt có thể mở policy cho bất kỳ ai giữ khoá API.
  `/policy` chưa có rate limit (quota Integrity 10k/ngày); `GoogleIntegrityVerifier` chưa tái dùng credential/HTTP session giữa các request, chưa có timeout refresh tường minh.
  `BRAIN_PLAY_PACKAGE` chỉ một app; chế độ dev không khoá trả lời như `/push` (app_id `dev`).
<!-- perf-gioi-han -->
- Giới hạn đo hiệu năng: **Samsung chưa đo** (máy vắng): khối Samsung của `perf-thresholds.json` còn tạm (`provisional`), nên mọi hàng hard của Samsung không phán ở 2.1.0; Redmi là máy thật duy nhất có số hard. Baseline v2.0.0 của Redmi: khởi động n = 5 (PERF-B, ngưỡng sàn nhiễu 20 / 40 ms với chỉ số dưới 20 ms, D22), fill/show từ A/B ghép cặp (n = 10 cold; `interstitial` n = 14, `rwi` n = 15, `opened` n = 4, ngưỡng ×1,5 vì phụ thuộc mạng, D24); `opened` và show latency n nhỏ (4 và 2). Fill, request → fill và show latency là **test ad** (`ca-app-pub-3940256099942544`), không dự báo production, và chỉ có `admob` (0 số theo mạng AppLovin/Meta/Pangle; `adapter pangle:` chưa đo vì cần nhóm mediation trên AdMob console, O4); `banner` không được `perf_gate` yêu cầu nên không có số fill (2.2.0: thêm chặng banner). Emulator chạy chung máy nên nhiễu (số chỉ là thông tin; emu37 một lượt vì boot yên INVALID ba lần); `TotalTime` gồm cả Activity demo, số của SDK là `onCreate`/`initPlugin`; PSS không có ngưỡng số. Show rate (Task R, oracle Python, không Docker, D20/D21): `interstitial` 11/11 và `rewarded` 4/4 (100 %); `rwi` 9/22 thô, mọi lượt sẵn sàng có tổ chức đều hiện (9/11, 11 `blocked_policy` + 4 kill switch + 1 harness); `banner`/`native_collapse`/`rect` đếm chu kỳ làm mới trên một lần hiện mỗi view (12/109) nên không phản ánh lỗi sản phẩm; `DecisionEngine` là lớp public và chưa bị chặn bởi verdict giấy phép (quyết định trực tiếp không tạo yêu cầu quảng cáo, D19; 2.2.0 chặn hoặc đóng gói `internal`).

## [2.0.0] - 2026-10-02

"2.0.0 — tách module tuỳ chọn, chốt API" (plan docs/superpowers/plans/2026-10-02-sdk-2.0.0-plan.md).

### Thêm

<!-- c1-seams -->
- Seam nội bộ core → MAX (2.0.0 C1c, D1): `AdsHelper`, `AdSdkStartup`, `AdsNetworkRegistry`, `PaidEvent` không còn tham chiếu
  gói `ads.adsmax`; chúng gọi MAX qua `MaxBridge` (internal), impl `ads.adsmax.MaxBridgeImpl` được dò bằng tên cố định đúng
  một lần mỗi process và chỉ khi có SDK AppLovin (vắng → `null`, y như nhánh F2-MAX của 1.6.4). Keep rule cho
  `MaxBridgeImpl` trong `consumer-rules.pro`. API public và hành vi không đổi; app không phải làm gì.
- Seam nội bộ core → game (2.0.0 C1d, D1/D16): `FirHelperConfig` và `AdsBrain` không còn gọi thẳng `PurchaseRouter`,
  `BattlePass`, `RecurringGrants`, `OfferwallManager`, `CrmBridge`. 5 section Remote Config của game đi qua `RcSectionRegistry`
  (internal; thứ tự áp và 5 dòng `… (RemoteConfig):` y hệt), hai tín hiệu CRM (lượt mua đầu, đổi segment) qua
  `BrainSignalListener` (internal). Mã game tự đăng ký trong `com.app.plugin.game.GameModule`, được dò bằng tên cố định đúng
  một lần mỗi process (sau init RC, hoặc ở tín hiệu brain đầu tiên nếu tới trước); đăng ký không I/O, không gửi gì (R-P1);
  vắng → 0 section, không lỗi. Keep rule cho `GameModule` trong `consumer-rules.pro`. Dòng log `SubscriptionLifecycle
  (RemoteConfig):` nay đứng trước `PurchaseRouter (RemoteConfig):` (hai cấu hình độc lập). Dump API −6 dòng
  (`FirHelperConfig$RemoteCfSnapshot` private, bỏ 5 trường game chết). API public không đổi; app không phải làm gì.

<!-- c2 -->
Artifact mới `com.app.plugin:appplugin-game:<cùng bản>` (module `:appplugin-game`): thư viện Android chỉ phụ thuộc, 0 mã nguồn,
phụ thuộc `api` vào `appplugin` cùng bản và mang keep rule phần game (`GameModule`) trong `consumer-rules.pro` riêng. Mã game
(`BattlePass`, `RecurringGrants`, `PurchaseRouter`, `OfferwallManager`, `CrmBridge`, `GameModule`) và 104 test vẫn ở module gốc;
`consumer-rules.pro` gốc không đổi. Dump API 0 dòng khác (5586), façade PASS.

Artifact thứ ba `com.app.plugin:appplugin-max:<cùng bản>` (module `:appplugin-max`, cũng 0 mã nguồn): phụ thuộc `api` vào `appplugin` cùng bản
cộng AppLovin SDK 13.6.2, 8 adapter `com.applovin.mediation:*`, `com.bigossp:admob-mediation`, GMA `chartboost`/`fyber` và `unity-ads`, đúng các
bản trước đây nằm trong POM `appplugin`. POM `appplugin` bỏ 13 phụ thuộc nhóm game đó (AppLovin SDK + 8 adapter MAX ở lại làm `compileOnly`, mã
`adsmax` và `isMaxSdkAvailable` không đổi); GMA, UMP, Meta, Firebase, billing, integrity, AppsFlyer, retrofit/gson vẫn ở POM `appplugin`.
Ba artifact publish cùng bản; `release.yml` đính ba AAR; `tools/pom_check.sh` kiểm POM trên repo cục bộ `build/repo`.

<!-- c3c -->
- Callback GMA vào main qua một poster (2.0.0 C3c, D47-2): `ads/MainPost` (internal) chạy ngay khi đã ở main, ngoài main thì post lên main looper, nên thứ tự callback trên main không đổi. Bọc ở chỗ đăng ký listener bằng một dòng mỗi chỗ (30 chỗ trong 11 file `ads/adsmob`: `adListener`, `fullScreenContentCallback`, các callback `load`, `onPaidEventListener`, `forNativeAd`, `OnUserEarnedRewardListener`, `VideoLifecycleCallbacks`), không bọc từng thân `onAd…`; `MainPostCensusTest` giữ số 30. API public không đổi (dump +19 dòng, khối internal mới); app không phải làm gì.

### Sửa

<!-- c1a -->
- Native full AdMob (`AdsMobPlNtFull`): paid và click tới muộn, sau khi slot đã sang lượt khác, mang placement của lượt hiện chính quảng cáo đó (`turnPl`, như 32 site còn lại từ 1.9.0 T6) thay vì placement hiện hành chọn theo `flagShow`; đóng phần paid/click của giới hạn `t6-gioi-han` 1.9.0 (impression/dismiss: C1b). `ShowTurnPlacementTest` R4 không còn chỗ miễn, quét cả thư mục con và đòi khoá `turnPl` là quảng cáo của chính callback. File tách `AdsMobNtFullBinder` + `AdsMobNtFullBackground` (internal): 791 → 579 dòng. API public/protected không đổi.

<!-- c1b -->
- Impression và dismiss gửi callback của app (`AdsShowCb.onAdImpression`/`onAdDismissed`, cả `adsGetCb` của đường get) ở 27 chỗ AdMob + Meta (`ads/adsmob`, `ads/adsfb`) mang placement của lượt hiện chính quảng cáo đó (`turnPl`) thay vì placement hiện hành của slot khi callback tới muộn, sau khi slot đã sang lượt khác. Cùng C1a (paid/click), giới hạn `t6-gioi-han` của 1.9.0 đã đóng hết cho AdMob + Meta: paid, click, impression, dismiss; MAX (đóng băng) giữ hành vi cũ. `ShowTurnPlacementTest` R5 quét đủ 27 chỗ (số cố định, cùng luật khoá R4c). Còn lại (minors): click muộn vẫn được chuyển tới callback show/get theo `flagShow` hiện hành (R-C1a m2). **Ngoại lệ ngoài 27 chỗ:** `onAdDismissed`/`onAdRewarded` của rewarded interstitial (`AdsMobPlRwi:162/164`, onAdRewarded/onAdDismissed trong `onAdDismissedFullScreenContent`) vẫn đọc placement hiện hành của slot (`adsPlacementEcmp`), chưa qua `turnPl`; chưa đóng ở 2.0.0. API public/protected không đổi.

<!-- s1 -->
- Các minor 1.9.0 về bidding, brain và policy: RC `ads_bidding_partners_android` bị từ chối lưu `""` thay vì chuỗi gốc (không logE lại mỗi lần mở app); logE từ chối RC và resource tối đa 512 ký tự (không lặp cả mảng `partners`); kiểm presence adapter qua hàm nhỏ `adapterPresent` có test (không khởi tạo lớp adapter); một dòng log `ads adapters on classpath: meta=present pangle=present …` cho cả tập đóng, kể cả partner không bật; artifact brain đã lưu mà lúc khôi phục bị từ chối nay logE đúng một lần rồi dùng mặc định; báo cáo `MonetizationSdk.configure` in rollout hiệu lực (artifact thắng rollout app đặt). Nhãn mạng của adapter ngoài 13 mẫu giữ chữ gốc (không còn hạ chữ như 1.8.0; không adapter nào SDK/demo cung cấp) và khoá `chartboost`/`fyber` bằng test.

<!-- b1 -->
- Billing (2.0.0 B1): đường listener (giao dịch tới ngoài luồng mua, vd PENDING vừa hoàn tất) với sản phẩm đã khai mà
  process chưa lấy được giá không còn ghi `iap_purchase` `price_micros 0` vĩnh viễn: token chưa vào sổ (logE `IapHelper
  listener: [<id>] chưa có giá, chưa ghi sổ`), lần khôi phục sau ghi với giá thật — cùng luật và cùng giới hạn với đường
  khôi phục của 1.9.0 T11 (token đã acknowledge thì lượt sau chỉ ghi `iap_purchase` lên server, không AppsFlyer/brain)
  (R-T11 m2). Consumable chưa có giá chỉ vào sổ ở 0 (kèm logE `ghi 0 (consumable)`) khi `consume` trả OK; `consume` lỗi →
  chưa ghi sổ, Play trả lại token, vòng sau ghi với giá (R-T11 m6). Dòng debug lấy giá thêm loại sản phẩm:
  `IapHelper products query code=<mã> n=<n> type=<INAPP|SUBS>` (tiền tố cũ giữ nguyên, R-T11 m5). Bước ghi sổ tách sang
  `iap/PurchaseRecorder` (internal; `IapHelper` 453 → 436 dòng). API public không đổi.

### Thay đổi hành vi

<!-- b1-hanh-vi -->
- HTTP 401 từ server event là lỗi **tạm thời** (2.0.0 B1, 1.9.0 R-T11 m3): tập bền `DurableEvents` giữ bản ghi trên đĩa
  và phát lại ở process sau (trước: bỏ hẳn như 400/413); sổ token IAP nhận 401 là DROPPED nên token chưa `reported`,
  process sau phát lại `iap_purchase` (server khử trùng theo token). 400 và 413 vẫn là từ chối vĩnh viễn; 4xx khác giữ như
  1.9.0. Bộ đếm `logevent stats`: 401 của event bền tính vào dropped thay vì rejected. Server trả 401 kéo dài (khoá sai,
  xoay khoá) → mỗi process có thể thêm một bản ghi bền cho mỗi token chờ (trần 2000 bản ghi, bỏ cũ nhất). Giới hạn: 401
  tới trước khi `DurableEvents` được gắn (trong `initPlugin`) vẫn là từ chối hẳn ở sổ. App không phải làm gì.

### Đổi API (phá vỡ có chủ ý)

<!-- c3b -->
**Chốt API 2.0.0 — 25 kiểu top-level và 3 thành viên chuyển `internal` (2.0.0 C3b, D3/D17/D28/D47-1; commit phá vỡ duy nhất của 2.0.0).**
Kiểu `internal` nay là 202/275 = 73,5 % (trước 177/275 = 64,4 %); cổng H9 `tools/public_types.sh --check --min-pct 70` chạy ở CI, baseline `tools/public-types.txt` còn 73 kiểu public.

| Nhóm | Kiểu → `internal` |
|---|---|
| Q7 (19) | `AdsConditionOb`, `ads.AdsBaseBannerCl`, `ads.AdsBaseRect`, `ads.AdsPlBaseBn`, `ads.AdsPlBaseFull`, `ads.AdsPlBaseNative`, `ads.AdsPlNtClBase`, `ads.consent.AdRequestFactory`, `appsflyer.AppsflyerHelper`, `brain.BanditPolicy`, `brain.DecisionLogQueue`, `brain.DecisionLogger`, `brain.HoldoutManager`, `brain.RewardModel`, `iap.FxRates`, `iap.PlayIntegrityGuard`, `logevent.EventRequest`, `logevent.LogService`, `until.PreUtil` |
| Margin (5) | `attribution.InstallReferrerHelper`, `brain.AdsBrain`, `brain.ComfortIndex`, `until.Constants`, `ads.RemoveAdsMask` |
| Sót public (D28, 1) | `logevent.UserProperty` |
| Thành viên (D47-1, D28) | `AdsMobMy.getAdnetName(ResponseInfo)`, `AdRequestFactory.build(Context)`, hàm top-level `logevent.createRequestBody(…, UserProperty)` (chữ ký duy nhất lộ `UserProperty`) |

Dump `api/appplugin.api` (javap không thấy visibility lớp — lớp `internal` vẫn public trong bytecode) đổi đúng 2 dòng `-`: `AdsMobMy.getAdnetName(com.google.android.gms.ads.ResponseInfo)` và `AdRequestFactory.build(android.content.Context)` (nay mangle `…$appplugin`); `createRequestBody` là hàm top-level nên `internal` không bị mangle và dòng dump của nó giữ nguyên; số dòng 5605 không đổi. Giữ public vì lộ qua chữ ký public: `AdsOptimizationPolicy`, `AdsPlacementConfigManager`, `ObjectForCustomeNativeAd`, `LagrangianBudget`, `BeliefState`, `GuardrailEngine`, `AdsConfigOb`; giữ public có chủ ý: `LogEventServer` (D12/D17). `tools/api_facade_check.sh` mặc định `BASELINE` = `a47d7dc` (chính commit C3b, dời bằng commit theo sau theo D27).

### Phải làm khi nâng cấp

<!-- c2-app -->
App game (dùng AppLovin MAX, mạng game): thêm `com.app.plugin:appplugin-max:<bản>` và `com.app.plugin:appplugin-game:<bản>` bên cạnh `appplugin`
(cùng bản; `-max` mang AppLovin SDK + adapter MAX + mạng game, `-game` mang keep rule `GameModule`). App tiện ích: `appplugin` không còn kéo AppLovin/Unity/Chartboost/Fyber/Bigo. App nào trước đây dựa vào các mạng
đó do POM gốc kéo bắc cầu — Bigo `com.bigossp:admob-mediation:5.7.0.0`, GMA Chartboost `com.google.ads.mediation:chartboost:9.12.1.1`, GMA DT Exchange
`com.google.ads.mediation:fyber:8.4.5.1`, Unity `com.unity3d.ads:unity-ads:4.19.0` (adapter `com.google.ads.mediation:unity` vốn `compileOnly`, app dùng Unity đã phải tự khai từ trước), AppLovin `com.applovin:applovin-sdk:13.6.2` +
các adapter `com.applovin.mediation:*` — phải tự khai đúng các toạ độ đó HOẶC thêm `appplugin-max`; nếu không, nhóm mediation AdMob của app lặng lẽ mất các mạng ấy. Partner đấu giá `unity` qua adapter GMA `com.google.ads.mediation:unity` nay cần app khai thêm
`com.unity3d.ads:unity-ads:4.19.0` (adapter không kéo SDK của nó; trước đây SDK mang sẵn).

<!-- c3a-app -->
**Resource của SDK đổi tên có tiền tố `appplugin_` (2.0.0 C3a).** SDK khai `resourcePrefix = "appplugin_"`: mọi resource nó tự khai (96 tên) nay là
`appplugin_<tên cũ>`; `R` của SDK không đổi lớp công khai nên `api/appplugin.api` không đổi. App KHÔNG đọc/ghi đè các tên này thì không phải làm gì. App nào
tham chiếu hoặc **ghi đè theo tên cũ** thì đổi sang tên mới (ghi đè một resource SDK = khai cùng tên trong app; tên cũ nay không còn khớp nên bản của app thôi có tác dụng
và SDK dùng bản mặc định của nó):
- **layout** (ghi đè được): `admob_native_cl`, `admob_native_full`, `admob_native_layout_full`, `admob_native_layout_language_2`, `admob_native_layout_small`, `fb_native_cl`, `fb_native_full`, `fb_native_layout_full`, `fb_native_layout_small`, `layout_native_full_pager`, `max_native_cl`, `max_native_cl_dialog`, `max_native_full`, `max_native_full_dialog`, `max_native_layout_small` → thêm `appplugin_` trước tên, kể cả bản `layout-land`. View id bên trong layout KHÔNG đổi (`ad_media`, `ad_headline`, `ntcl_*`, `fb_*`…), nên layout riêng giữ nguyên id là khớp.
- **style**: `AppTheme` → `appplugin_AppTheme`, `AppTheme.AdAttribution` → `appplugin_AppTheme.AdAttribution` (app tham chiếu `@style/AppTheme` của SDK, như app demo, phải đổi; app có `AppTheme` riêng thì không bị ảnh hưởng nữa vì hai tên không còn trùng).
- **`@xml/gma_ad_services_config` → `@xml/appplugin_gma_ad_services_config`** (app khai `android.adservices.AD_SERVICES_CONFIG` trỏ vào file này phải đổi; manifest mẫu ở app demo đã đổi).
- **string**: `default_notification_channel_id` → `appplugin_default_notification_channel_id` (app từng khai `default_notification_channel_id` để đổi kênh thông báo mà SDK dùng phải khai `appplugin_default_notification_channel_id`; tên cũ không còn ảnh hưởng SDK); `ad_attribution`, `ad_close_countdown`, `ad_price_placeholder`, `ad_sponsored` cũng đổi tương tự.
- **màu trùng tên thường gặp** (nếu app khai cùng tên để chỉnh màu quảng cáo thì bản của app thôi có tác dụng): `ad_body_text`, `ad_card_bg`, `ad_cta_blue_base`, `ad_cta_blue_mid`, `ad_cta_blue_top`, `ad_cta_fb`, `ad_headline_max`, `ad_overlay_bg`, `black`, `black_30`, `gnt_ad_green`, `gnt_black`, `gnt_blue`, `gnt_gray`, `gnt_green`, `gnt_outline`, `gnt_red`, `gnt_white`, `purple_200`, `purple_500`, `purple_700`, `teal_200`, `teal_700`, `transparent`, `white`.
- **dimen**: `activity_horizontal_margin`, `activity_vertical_margin`, `ad_badge_margin_bottom`, `ad_badge_margin_start`, `body_text_size`, `body_width`, `cta_button_margin_top`, `cta_button_text_size`, `cta_button_width`, `headline_margin_start`, `headline_text_size`, `headline_width`, `icon_layout_height`, `icon_layout_margin_end`, `icon_layout_margin_top`, `icon_layout_width`, `mediaview_height`, `mediaview_width`; **drawable**: `adsarea`, `app_icon_background`, `bg_ad_txt`, `bg_bound`, `bg_button_call_to_action_ad`, `bg_button_cta_full_blue`, `bg_card_ad_bottom`, `bg_circle`, `bg_native_ads_item`, `bg_round_rect_green`, `bg_skip_button`, `bgbt_close`, `bgc_cir_gray`, `button_bl`, `button_close`, `button_gr`, `grid_scr`, `ic_ad_choice`, `ic_ad_skip`, `ic_collapse`, `icon_down`, `icon_next`; **font**: `inter_bold`, `inter_extra_bold`, `inter_medium`, `inter_regular`, `inter_semi_bold`, `nunito_black`, `nuosu_sil_regular`, `purple_smile`.
- **KHÔNG đổi** (khoá app-contract, app khai, SDK đọc bằng tên): `admob_*`, `adfb_*`, `admax_*`, `ads_cf_*`, `ads_admob_firebase_linked`, `flagTest`, `admob_test_device`, `fb_test_device`, `max_test_device`, `facebook_app_id`, `af_app_id`, `admax_devKey`, `@array/ads_*`; cùng `raw/appplugin_keep` và 3 chuỗi `appplugin_rwi_*`.
- Cùng đợt SDK xoá 4 font không ai dùng (`inter_bold`, `inter_medium`, `nunito_black`, `nuosu_sil_regular`, khoảng 1,08 MB trong AAR): app nào tham chiếu font theo tên cũ từ SDK sẽ lỗi biên dịch; chưa thấy app nào làm vậy.
- Cùng đợt API khai báo tường minh (`explicitApi`): không đổi hành vi, không đổi dump API; chỉ thêm `public`/kiểu trả về vào mã nguồn SDK.

<!-- c3b-app -->
**Kiểu/thành viên chuyển `internal` ở 2.0.0 C3b.** App tham chiếu một trong 25 kiểu ở mục "Đổi API" (kế thừa `AdsPlBase*`/`AdsBase*Cl`/`AdsBaseRect`, gọi `AdsBrain`, `Constants`, `PreUtil`, `AppsflyerHelper`, `InstallReferrerHelper`, `RemoveAdsMask`, `HoldoutManager`, `DecisionLogger`, `FxRates`, `PlayIntegrityGuard`, `EventRequest`/`LogService`…) hoặc gọi `AdsMobMy.getAdnetName(ResponseInfo)` / `AdRequestFactory.build(Context)` / `createRequestBody(…)` (dựng body event thô — SDK tự gửi event qua `LogEventServer`, app không cần) sẽ lỗi biên dịch; thay bằng API public: quảng cáo qua `AdsHelper`, consent qua `ConsentManager`, brain qua `MonetizationSdk`/`BrainInspector`, log qua `LogEventServer`/`LogUtil`, cài đặt qua `PreferenceHelper`. Đọc trên fork SC/IM (∩ SDK) và app demo: 0 chỗ tham chiếu (F6), kể cả `UserProperty`/`createRequestBody` (0 nơi gọi trong SDK, demo và danh sách consumer) — không app nào hiện có phải sửa.

<!-- rollup -->

**Gộp "Phải làm khi nâng cấp" 1.3.0 → 2.0.0 (H10).** Đọc từ bản app đang dùng xuống 2.0.0; mỗi hàng là việc app phải làm (nguyên văn ở mục cùng bản), cột cuối là nơi tìm. Bốn hàng 2.0.0 là việc mới của lần nâng này.

| Từ bản | Việc phải làm | Ký hiệu |
|---|---|---|
| 1.3.0 | Gọi `AdsMobMy.instance.startSdkInit(context)` (`AppPluginBase.onCreate` không còn khởi tạo mạng); chạy luồng consent UMP (`gatherConsent`/`updateConsentInfo`) vì mạng chỉ khởi tạo sau câu trả lời; app đo Meta tự khai `facebook-core`; DataBucket mặc định tắt (khai `DATABUCKETS_BASE_URL` nếu cần); composite build cần AGP 9.x | mục "Phải làm khi nâng cấp" của [1.3.0] |
| 1.3.1 | Callback Full2 tới `AdsShowCb` cha (phân biệt bằng `placement?.startsWith("full_2_")`); `onAdShowFail` không còn tới giữa waterfall (dựa vào giá trị trả về); `onAdDismissed` có thể tới đồng bộ trong `onActivityDestroyed` (không commit Fragment/điều hướng); không đặt resource trùng `raw/appplugin_keep` | mục "Phải làm khi nâng cấp" của [1.3.1] |
| 1.4.0 | Kiểm lại nhánh `DENIED`/`PARTIAL`/`UNKNOWN` của consent tier; app không UMP tự `ConsentState.setTier` và gọi `updateConsentInfo`; app không kế thừa `AppPluginBase` gọi `ConsentState.installStore`+`restore`; bỏ `setAnalyticsCollectionEnabled(true)` vô điều kiện và `AppsflyerHelper.initHelper`; `tools:replace` cho `google_analytics_default_allow_*`; build lại vì `IapHelper.init` trả `Boolean`; `peekAll()` nullable; `onResult` của `LogEventServer.log…` chạy trên luồng bất kỳ và trễ; backend khử trùng event; `user_id` đổi từ ANDROID_ID sang player id (truyền `configure { playerId }`); lớp con `AdsPlBase`/`AdsBase` thêm `eventTag` (mục Đổi) | mục "Phải làm khi nâng cấp" của [1.4.0] |
| 1.5.0 | Chỉ khi có `admax_*` nhiều id (`pl,idA;idB`): xem lại thứ tự id (mỗi bước waterfall MAX request đúng unit); không để hai placement rewarded dùng chung một ad unit MAX; API không đổi | mục "Phải làm khi nâng cấp" của [1.5.0] |
| 1.6.0 | `cbLoad` của placement không đang load bị bỏ khi Activity chủ bị huỷ: đăng ký lại trong Activity mới (`showBanner`/`showRect`/`load…ForThisTurn`) | mục "Phải làm khi nâng cấp" của [1.6.0] |
| 1.6.4 | App tiện ích muốn bỏ MAX và adapter game: loại khỏi classpath như flavor `utility` (consumer rules đã `-dontwarn com.applovin.**`); app đang dùng đủ adapter: không việc (đã thay bằng artifact `-max` ở 2.0.0) | mục "Phải làm khi nâng cấp" của [1.6.4] |
| 1.7.0 | Event server schema v2 (`ad_request`/`ad_fill`/`ad_no_fill`/`ad_show_attempt`/`ad_show_fail` thay tên v1): đổi truy vấn `events_raw`; DataBucket vẫn tắt tới khi khai `DATABUCKETS_BASE_URL` | mục "Phải làm khi nâng cấp" của [1.7.0] |
| 1.8.0 | Sub `PENDING` không tắt quảng cáo, `IN_GRACE_PERIOD` chỉ từ server; `purchase()` chồng trả `Failed(-2, …)`; quyền lợi chỉ có hiệu lực sau lượt kiểm chữ ký nền; holdout paywall mặc định 0 (muốn holdout thì khai `holdout.fraction` trong `paywall_config_android` v2) | mục "Phải làm khi nâng cấp" của [1.8.0] |
| 1.9.0 | Năm adapter GMA tuỳ chọn (`pangle`, `mintegral`, `unity`, `vungle`, `applovin`) là `compileOnly`: app tự khai adapter cùng bản và liệt kê partner ở `ads_bidding_partners` | `t1-app` ([1.9.0]) |
| 2.0.0 (ba artifact) | App game thêm `appplugin-max` + `appplugin-game` cùng bản; app tiện ích chỉ `appplugin`. App từng dựa vào mạng bắc cầu cũ (Bigo, GMA Chartboost, GMA Fyber, `unity-ads` 4.19.0, `applovin-sdk` 13.6.2 + adapter) tự khai hoặc thêm `appplugin-max`; partner `unity` trên utility thêm `unity-ads:4.19.0` | `c2-app` |
| 2.0.0 (resource) | Tên resource của SDK có tiền tố `appplugin_` (96 tên: layout, style, `@xml/appplugin_gma_ad_services_config`, `appplugin_default_notification_channel_id`, màu, dimen, drawable, font); khoá app-contract và RC không đổi; 4 font không dùng bị xoá | `c3a-app` |
| 2.0.0 (API) | Tham chiếu một trong 25 kiểu/3 thành viên chuyển `internal` thì lỗi biên dịch: dùng API public; fork Speaker Clean và Invoice Maker (∩ SDK) và demo: 0 chỗ tham chiếu | `c3b-app` |
| 2.0.0 (hành vi) | Không việc: HTTP 401 từ server event là tạm thời (giữ và phát lại, trần 2000 bản ghi); billing listener không ghi giá 0; callback GMA vào main qua một poster; seam MAX/game nội bộ; `onAdDismissed`/`onAdRewarded` của rwi (`AdsMobPlRwi:162/164`) vẫn đọc placement hiện hành | `b1-hanh-vi`, `b1`, `c3c`, `c1-seams`, `c1b` |

Lên 2.0.0 từ 1.9.0: chỉ bốn hàng 2.0.0. Từ bản cũ hơn: cộng các hàng của các bản ở giữa (bản 1.6.1, 1.6.2, 1.6.3 không có mục riêng).

### Công cụ

<!-- t0b -->
- Cổng `--banners` (2.0.0 Task 0b, D8, D55): chờ có hạn (30 giây thật) dòng `ntcl attach children=1` thay cho ngủ cố định, hết
  hạn = FAIL; mẫu AdView lấy thêm sau NtCl x3 (`adview_ntcl=`, phải = 1); lấy mẫu sau 18 s kể từ lần chạm; khôi phục `accelerometer_rotation` gốc sau cổng (cả khi bị ngắt). 14 dump
  `dumpsys activity top` thật (37/33/28/26/Redmi) là fixture trong `tools/tests/fixtures/dumps/` cho test hồi quy B1–B5.

<!-- t0c -->
- Task 0c (2.0.0, D53, #43) — driver cho các số "chưa đo" của 1.9.0: `tools/lib/rwi_legs.sh` thêm vào `--measure` (sau G11/H6a) chặng **skip** (Show Rewarded Interstitial rồi chạm "Skip"/"Bỏ qua": `rwi_intro_skipped` 1, `ad_show` rwi 0) và chặng **gap** (Show Interstitial rồi rwi trong min-gap: `policy_min_gap` 1); `--iap` `buy_cheap` chạm thêm rwi và đòi đúng 1 hàng `ad_show_fail reason=remove_ad placement=rwi_default`; `device_gate.sh --drc` (`tools/lib/drc_legs.sh`) chạy 3 chặng harness brain (calibrated, kill, `rollout_fraction` 0 giữ qua cold start) kèm đối chứng (chặng 1 phải có `brain_decision` ≥ 1), 0 ghi Firebase. Demo: nút "Show Interstitial (brain)" (`isDeltaTime` mặc định) để `--drc` đo cổng brain trên đường sản phẩm; nút "Show Interstitial" cũ (`isDeltaTime = false`) giữ nguyên; 1.9.0 #17 đo engine qua Playground, không đo cổng sản phẩm. Test shell 254 → 262 (R1–R8).
<!-- t0d -->
- Task 0d (2.0.0) — `tools/device_gate.sh --consent` (`tools/lib/consent_gate.sh`) thay driver tạm của 1.8.0/1.9.0: `pm clear`, ≤ 3 lần chạy thử tới khi TotalTime < 5 s và form UMP hiện (không thì `RESULT: INVALID (cold guest)`), `force_eea`, "Do not consent", "Show Rewarded Interstitial", cửa sổ DENIED 60 s phải 0 hàng và 0 POST từ sink của lane, Privacy options → Consent (GRANTED), cửa sổ flush 45 s phải có hàng mới; dòng 1 của log mọi cổng có thêm `api=<ro.build.version.sdk>` (`getprop` lỗi: `INVALID (api)`, exit 4); phần phân tích đối số chuyển nguyên văn sang `tools/lib/gate_args.sh` (`device_gate.sh` 263 → 239 dòng). Process đổi pid trong cửa sổ DENIED: có crash → FAIL; có dòng `killDueToPackageUpdate` của chính pid đó → `INVALID (process restart: package update)`; còn lại → FAIL (process gone). Test shell 224 → 235 (C1–C6, C3a–c, A1, A2).

<!-- t0f -->
- Task 0f — bật `unity`/`applovin` qua `-PbiddingPartners` dựng được với R8 (D51): demo `app/build.gradle.kts` giữ cả nhóm phụ `com.unity3d.coherence` cùng `com.unity3d.ads` cho `unity`, và `consumer-rules.pro` thêm `-dontwarn com.amazon.privacypass.**` (OMID của adapter applovin). Demo khai resource `ads_bidding_partners` chỉ khi `-PbiddingPartners` khác rỗng (D50), qua task sinh `values/ads_bidding_partners.xml` (không dùng `resValue`).

<!-- t0g -->
- `tools/public_types.sh` đếm kiểu top-level theo mức hiển thị (cổng H9: internal ÷ tổng, `--min-pct 70` ở C3) và `tools/public-types.txt` là baseline kiểu public, `--check` chạy ở CI; sửa minor công cụ: `local_sink.sh stop --port` không cần `--dir`, `emu_boot.sh` chờ thiết bị ≤ 180 s và có hạn tổng khi `getprop` treo, `redact.sh` che dòng PEM không có dấu `:`, chữ help `--out` của `calibrate` là FILE.

### Giới hạn đã biết

<!-- c2-gioi-han -->
`appplugin-game` (và `appplugin-max` ở C2b) chỉ là phụ thuộc ở 2.0.0: AAR gần rỗng (manifest + rules, `classes.jar` 0 lớp), mã
game chưa tách khỏi `appplugin` (dời sang module riêng để 2.1.0). Ba artifact dùng chung một số phiên bản (`sdkVersion` ở module gốc).

<!-- c3-gioi-han -->
- `internal` của Kotlin không thấy được ở dump javap: lớp `internal` vẫn `public` trong bytecode, nên `api/appplugin.api` (cổng ABI) không đo H9; H9 đo ở mức nguồn bằng `tools/public_types.sh` (R-C3b m4). Gọi từ Java vẫn biên dịch được; đó không phải API được hỗ trợ.
- Khoá ngoài tiền tố `appplugin_`: khoá app-contract (`admob_*`, `adfb_*`, `admax_*`, `ads_cf_*`, `flagTest`, `*_test_device`, `facebook_app_id`, `af_app_id`, `admax_devKey`, `@array/ads_*`) giữ tên không tiền tố vì SDK đọc chúng bằng `getIdentifier`; kiểm `ResourcePrefixTest` RP2 chưa chặn một khoá app-contract mang tiền tố `appplugin_` (R-C3a m1).
- Fork Speaker Clean khai `@xml/gma_ad_services_config` trong manifest: phải đổi thành `@xml/appplugin_gma_ad_services_config` khi chuyển sang AAR (danh sách chuyển của app đầu tiên).

<!-- s1-gioi-han -->
- Khoá JSON trùng trong `partners` (RC bidding) và trong `by_tier` (policy quảng cáo): Gson lấy giá trị **cuối** (last-wins), không từ chối; đã ghi nhận, chưa sửa (R-T1 m2, R-T4-2 b).

## [1.9.0] - 2026-10-02

"1.9.0 — đòn bẩy eCPM và brain sẵn sàng" (plan docs/superpowers/plans/2026-10-01-sdk-1.9.0-plan.md). API public/protected không đổi.

### Thêm

<!-- t1 -->
- Bidding partner cấu hình được (Task 1, D1–D3, D19, D23): tập đóng `meta`, `pangle`, `mintegral`, `unity`, `vungle`,
  `applovin` (`meta` mặc định). App khai string-array `ads_bidding_partners` (tên lạ, trùng hay rỗng: bỏ cả resource,
  về `meta`, một logE); Remote Config `ads_bidding_partners_android` = `{"schema_version":1,"partners":[…]}` chỉ thu hẹp
  tập đó (sai dạng: bỏ cả giá trị, giữ resource, một logE), lưu ở `mem_ads_bidding_partners_android` cho lần mở kế.
  Lúc khởi tạo mạng (ngoài main, trước AdMob) SDK dò adapter của từng partner bật theo tên lớp: vắng thì đúng một dòng
  `ads bidding: <p> enabled but adapter absent, skipped`; `sdk_health.networks` thêm `,partners=<csv có adapter>`.
  Registry chỉ là hiện diện lúc build + nhãn: không loại được adapter đang có khỏi phiên đấu giá. Một mapper nhãn
  mạng: `AdsMobMy.getAdnetName` dùng `AppUtil.extractAdNetwork` (AdMob giờ ra `admob`, không còn tên lớp). keep.xml
  thêm `@array/ads_*`. Demo: `-PbiddingPartners=<csv>` (⊆ pangle, mintegral, unity, vungle, applovin) thêm adapter
  và SDK của partner đó vào flavor `utility`; `game` không đổi.
<!-- t2 -->
- Rewarded interstitial (hợp đồng C, D5): app gọi `loadRewardedForThisTurn("rwi_<x>")` / `showRewarded("rwi_<x>", AdsGiftShowCb)` — tiền tố `rwi_` được định tuyến **trước** tiền tố `gift_` sang `RewardedInterstitialController` (internal); không thêm API public. Thứ tự cổng: `ad_show_attempt` (`ad_format` `rewarded_interstitial`) → kill switch/định dạng rewarded tắt → remove-ads `FLAG_FULLSCREEN` (`remove_ad`; gói rẻ `INTERRUPTIVE` gỡ cả rwi) → luật phiên đầu + khoảng cách **dùng chung với interstitial** (`policy_first_session`/`policy_min_gap`, cùng mốc `fullTimeShow`: rwi đóng cũng chặn interstitial trong gap) → quảng cáo quá `fullscreen_max_age_minutes` (`ad_expired`, nạp lại) → màn intro SDK vẽ (đếm ngược 5 giây + nút "Bỏ qua"; bỏ qua/Back = `ad_show_fail reason=rwi_intro_skipped`, quảng cáo giữ cho lượt sau) → show. Một lượt giữ `flagAdsFullShowing` từ lúc intro mở tới khi đóng; `onAdRewarded` tối đa 1, `onAdDismissed` tối đa 1; không chuỗi Full2; brain không tham gia. Intro đóng khi host `clearActivity` hoặc `ON_DESTROY` (`host_destroyed`), tạm dừng đếm ngược khi host `ON_STOP`. Id từ resource `admob_rwi` (`rwi_default,<ad unit>`, slot mặc định `rwi_default`), `AdsIDDef.AdmobRwi = 30` internal; Firebase `show_ads_rwi`; `AdEventLog.formatOfPlacement("rwi_…")` = `rewarded_interstitial`. Chuỗi intro trong `appplugin_rwi_strings.xml`: mặc định tiếng Anh (`values/`), tiếng Việt ở `values-vi/` (D33; app ghi đè theo tên) (1.9.0 T2, `t190-t2-{red,green,full}.log`).
<!-- t3 -->
- Banner GMA collapsible theo cấu hình (hợp đồng D, D4/D23/D26): app khai string-array `ads_collapsible_placements` (tên slot `bn_` có trong `admob_banner`; một mục sai — không `bn_` hoặc không có trong `admob_banner` — thì bỏ **cả** danh sách, một logE). Mọi request của slot có tên mang extras AdMob `collapsible=bottom` cho tới lần nạp thành công đầu tiên của slot trong process (D37: nạp lỗi không dùng hết extras; theo tài liệu Google; không gửi `collapsible_request_id` vì tài liệu không có); sau đó load lại/refresh không mang extras (view tới muộn sau watchdog được giữ không tính là lần nạp thành công); `npa=1` của consent giữ nguyên trong cùng bundle. Log `tryLoad ok loading … collapsible=<isCollapsible()>` sau khi load. `CollapsibleBanner` internal, không đụng `AdRequestFactory`; banner adaptive không đổi (D4). API public không đổi (1.9.0 T3, `t190-t3-{red,green,full}.log`).
<!-- t4 -->
- `ads_policy_android` nhận khối tuỳ chọn `by_tier` (cùng `schema_version` 1): `{"T1":{…},"T2":{…},"VN":{…},"OTHER":{…}}`, mỗi tier chỉ 7 trường v1 (kẹp như top-level), trường vắng giữ giá trị top-level; nhóm theo country của thiết bị theo `backend/sql/seed/country_tier.csv` (13 T1, 23 T2, VN; còn lại và chưa biết = OTHER), resolve lại khi country biết sau `restorePolicy`; khối sai (tier lạ, trường lạ gồm `gma_load_app_context`, `by_tier`/`schema_version` lồng) bị bỏ cả khối, top-level giữ, 1 logE; SDK cũ bỏ qua khoá lạ; API public không đổi (`t190-t4-red.log`).
<!-- t9 -->
- Hiệu chỉnh artifact brain `python -m app.calibrate --jsonl <file> | --bq <project.dataset.table> --base <policy.json> --out <path> [--k 30] [--min-support 30]` (Task 9, D8): đầu ra = toàn bộ `--base` với `likelihood_profiles` thay (`rewarded_accept`, `payer_propensity`, `churn_tendency` từ proxy hàng v2 theo `segment` của quyết định mới nhất, co về prior k = 30, ô n < 30 giữ prior, kẹp [0.02, 0.98]; `fail_rate` = prior), `version = calib-<yyyymmdd>-<sha8>` tất định theo dữ liệu, `enabled`/`experiment` chép từ base, bảng hỗ trợ `<out>.support.md`; base thiếu `version` = lỗi; fixture `backend/tests/fixtures/brain_policy_calibrated.json` = đầu ra trên `ad_v2_fixture.jsonl`.

### Sửa

<!-- t3-ntcl -->
- Native collapsible (NtCl): mỗi host giữ tối đa một view. Trước đây `hideNativeCl` chỉ ẩn container nên mỗi lượt hiện cộng thêm một view vào host tới khi `clearActivity`; nay ẩn gỡ view của lượt vừa đóng, và lúc gắn `addToParentView` gỡ view cũ của host rồi gắn qua `BannerAttach` (view còn cha cũ được gỡ trước, không `child already has a parent`). Dòng debug `ntcl attach children=<n>` sau khi gắn; `clearActivity` vẫn gỡ hết (1.9.0 T3, N1–N3).
<!-- t5s -->
- Brain (Task 5s, D7, D28): kill switch `enabled:false` của `brain_policy_android` nay tắt thật `AdsBrain` (interstitial về luật tĩnh, không có hàng `brain_decision`, không `experiment_exposure`); artifact được **chấp nhận** cuối cùng lưu ở `mem_brain_policy_android` (`apply()`) và áp lại khi `DecisionEngine` dựng lúc mở app, trước quyết định đầu tiên (bản bị từ chối không bao giờ được lưu); `experiment.rollout_fraction` của artifact thắng `brainRolloutFraction` app đặt trong `configure`; đường Remote Config đi qua `DecisionEngine.applyPolicy` (`exploration_floor` tới bandit); mỗi lần chấp nhận log `BrainPolicy: đã nạp '<version>'`. Harness brain (internal, chỉ demo `utility`): đủ `flagTest` + meta-data `com.app.plugin.BRAIN_HARNESS` + extra `brain_policy` (JSON artifact) thì nạp qua đúng đường đó, log `brain harness: armed version=<v>`; thiếu khoá nào thì không đọc extra. API public không đổi (`t190-t5s-{red,green,full}.log`).
<!-- t6 -->
- Paid (`ad_show`/`ad_revenue`) và `ad_click` của AdMob/Meta mang placement của lượt show/attach của chính quảng cáo đó (1.9.0 T6, D14/D48; plan 1.8.0 D21): trước đây đọc placement slot đang giữ lúc callback tới, nên impression/click tới muộn sau khi slot đã hiện lại ở placement khác mang placement mới hơn. `AdsPlBase` giữ một map yếu quảng cáo → placement, ghi ở `setShowPlacement`/`setGetPlacement`; quảng cáo chưa vào lượt nào lùi về placement hiện hành như cũ. 32/35 chỗ paid/click của `ads/adsmob` + `ads/adsfb`; MAX không đổi. API public không đổi (`t190-t6-{red,green,full}.log`).
<!-- t11 -->
- Billing (Task 11, D13; tách `iap/PurchaseFlow.kt` internal khỏi `IapHelper` trước, 459 → 445 dòng): (R-2 m1) kết quả
  purchases-updated chỉ tiêu callback của luồng mua đang mở khi có giao dịch của đúng sản phẩm luồng đó (hay là mã lỗi/huỷ
  của lần mở luồng); giao dịch sản phẩm khác (PENDING hoàn tất ngoài luồng) chỉ về `purchaseListener`, luồng vẫn mở;
  (R-2 m4) khôi phục thấy token của sản phẩm đã khai mà lấy giá vẫn lỗi thì chưa ghi sổ (logE `IapHelper restore: […]
  chưa có giá, chưa ghi sổ`), lần khôi phục sau ghi với giá thật thay vì `price_micros` 0 mãi mãi — giới hạn (D43): token
  đã acknowledge ở lượt chưa có giá thì lượt sau chỉ gửi `iap_purchase` lên server, không AppsFlyer/brain (kill giữa lượt
  mua + lấy giá lỗi); riêng consumable đã khai (D44) bị consume ngay lượt đó và Play không trả lại, nên được ghi sổ luôn
  với giá đang biết (`price_micros` 0 khi chưa biết, logE `IapHelper restore: […] chưa có giá, ghi 0 (consumable)`);
  (F1a m2) `iap_purchase`
  bị server từ chối hẳn (`REJECTED` = 4xx trừ 408/429, hay body hỏng) được đánh dấu `reported`, không phát lại mỗi process;
  5xx/mạng vẫn phát lại. Dòng debug khi thành công (Task R #12): `IapHelper products query code=<mã> n=<số sản phẩm>` và
  `IapHelper restore OK owned=<n>`. API public không đổi (1.9.0 T11, `t190-t11-{red,full}.log`).

### Backend

<!-- t9-be -->
- View mới `v_brain_decisions` (quyết định theo điểm × chế độ × holdout × policy; `declined_app_day` = `ad_show_fail` `show_full_brain_declined` ở grain app × ngày, lặp trên mỗi hàng — không cộng dồn, D12/D38) và `v_ecpm_network_format` (eCPM theo mạng = chữ thường của `ad_network or ad_source` × định dạng, D11; `normalize.py` giữ nhãn như SDK gửi, D35); `v_paywall_funnel` thêm nhánh `variant_id = holdout` (phiên `paywall_holdout_skip`, lượt mua ngoài phiên của người bị giữ); báo cáo ngày 9 → 11 bảng; cột `slot` (add-only, từ `eventData`, trước chỉ trong `raw_event_data`) — dataset có sẵn: `bq_setup` dừng và in `ALTER TABLE … ADD COLUMN slot STRING` cho `events_staging`/`events_raw`; hàng cơ sở của view tách sang `app/ad_rows.py` (nội dung không đổi).

### Công cụ

<!-- t0c -->
- Task 0c — `tools/lib/redact.sh` (`redact_stream`, 6 mẫu cố định: JWT `eyJ…`, `AIza…`, `ya29.`, khối PEM, `Bearer`, mã băm tài khoản Play của Finsky) che bí mật ngay lúc chụp ở 11 chỗ ghi logcat/crash của `device_gate.sh`, `measure_gate.sh`, `iap_gate.sh` (log trong `DOC/testing` không còn token; số dòng và exit của adb không đổi); test X1–X8 (plan-lint #40).
<!-- t0d -->
- Task 0d — `tools/emu_boot.sh` gọi adb qua `adb_b` (`wait-for-device` có hạn 180 s, quá hạn FAIL + dòng `adb_b timeout`); tiền kiểm Docker của `device_gate.sh`/`daily_session.sh` neo `^/Applications/Docker.app/Contents/`; `mg_first_visible` bỏ node nhỏ hơn 8×8 px; `mg_screen_init` in thêm một dòng `screen size=<w>x<h> density=<d>`; test E1–E5.
<!-- t7 -->
- Task 7 — AVD `api33` (Google APIs arm64, 1080×2400@420, RAM 2048, data 6G, cổng 5590) boot qua `emu_boot.sh`; cổng smoke `device_gate.sh --variant utility` PASS trên API 33 với APK 1.8.0 đóng băng (R8 0, StrictMode by-sdk 0, FATAL/ANR/native 0). Allowlist: dòng ProviderInstaller của Play services nhận cả biến thể API 33 (`…ProviderInstallerImpl.reportRequestStats2`, tiền tố tuỳ chọn, vẫn 7 regex); test T7 (`FAKE_API=33`) và 2 ca regex thật.
<!-- t10 -->
- Task 10 — demo: nút Load/Show Rewarded Interstitial (`rwi_default`, unit test Google), banner `bn_collapsible` (khai ở `ads_collapsible_placements`) và NtCl show/hide ×3 (`DemoRwiBanner.kt`); `ads_ids.xml` thêm `admob_rwi` + slot `bn_collapsible` (ad unit trong `resources.arsc`: 9 → 11). Công cụ: pha A của `--measure` chạm "Show Rewarded Interstitial" (`showRwi call show`); `device_gate.sh --banners` (`tools/lib/banners_gate.sh`: collapsible ×3, xoay ×2, NtCl ×3, HOME/resume; `already has a parent` 0, `AdView` = 1, mọi `ntcl attach children=1`, thiếu bằng chứng = FAIL); `bundle_size.sh --partners <csv>` (⊆ năm adapter tuỳ chọn `pangle,mintegral,unity,vungle,applovin`; `meta` là partner mặc định, luôn có, nên bị từ chối như mọi từ lạ — FAIL trước Gradle, D46; hợp lệ thì chuyển `-PbiddingPartners`); `release_gate.sh` đếm meta-data `IAP_HARNESS`/`BRAIN_HARNESS` trong manifest (utility 1 / game 0, `tools/lib/harness_meta.sh`); test D1–D7; probe AdView đọc tên sau R8 từ mapping và nhận dòng `TASK` không có `uid:` trên API 28/26 (F3-t1/F3-t2).

### Thay đổi hành vi

<!-- t5s-hanh-vi -->
- Brain (Task 5s): `enabled:false` trong `brain_policy_android` trước đây chỉ ép kịch bản `BALANCED_HYBRID`, nay dừng mọi quyết định của brain (như `AdsBrain.enabled = false`); artifact (cả `enabled:false` và `rollout_fraction: 0`) giữ qua cold start tới khi Remote Config đổi giá trị, và `rollout_fraction` của artifact ghi đè `brainRolloutFraction` của `configure`. Grep app tiêu thụ dùng `brain_policy_android` = 0 file: không app nào cần di trú.

### Phải làm khi nâng cấp

<!-- t1-app -->
- Năm adapter GMA tuỳ chọn (`pangle`, `mintegral`, `unity`, `vungle`, `applovin`) nay là `compileOnly` trong SDK: SDK không còn
  kéo chúng cho app, và app trước đây nhận chúng bắc cầu từ SDK sẽ **mất** chúng khi nâng lên 1.9.0. App muốn partner nào vào
  đấu giá phải tự khai adapter đó (cùng bản) **và** liệt kê tên partner ở `ads_bidding_partners`:
  `com.google.ads.mediation:pangle:8.1.0.3.1`, `com.google.ads.mediation:mintegral:17.1.61.1`,
  `com.google.ads.mediation:unity:4.19.0.0`, `com.google.ads.mediation:vungle:7.7.4.2`,
  `com.google.ads.mediation:applovin:13.6.2.1`. Partner có trong `ads_bidding_partners` mà adapter vắng thì SDK log đúng một
  dòng (`ads bidding: <p> enabled but adapter absent, skipped`), không lỗi; partner không có trong resource thì SDK không xét
  (không có dòng nào). chartboost, fyber, bigo, facebook vẫn đi kèm SDK như cũ.

### Giới hạn đã biết

<!-- t1-gioi-han -->
- A/B bidding chỉ làm được qua AdMob console (mediation A/B) hoặc hai bản dựng: client không loại được adapter có
  trong bản dựng khỏi phiên đấu giá, Remote Config chỉ thu hẹp bước setup/nhãn của SDK.
- Opt-out bang Mỹ (GPP) không được chuyển tiếp cho các partner bidding (option A, D3; adapter tự đọc TCF); Meta vẫn
  theo `metaRefusal` như trước.
- MAX (`com.applovin:applovin-sdk` + adapter MAX) vẫn kéo SDK mbridge/unity/vungle theo bắc cầu, và SDK vẫn khai
  `com.unity3d.ads:unity-ads` trực tiếp (2.0.0 C2). Bật `applovin` ở demo `utility` đưa cả AppLovin SDK vào nên MAX
  cũng khởi tạo.
- Bật `unity` hoặc `applovin` qua `-PbiddingPartners` (demo) hay trong app thật cần thêm luật keep/dontwarn: R8 báo thiếu lớp
  (`com.unity3d.coherence.AttributesProvider`, `com.amazon.privacypass.PrivacyPass`), chưa đo được cỡ (2.0.0, D51).
- Dòng init `ads admob adapter <p>:` của GMA chỉ xuất hiện khi nhóm mediation của app ID trên AdMob console có partner đó (chủ
  dự án cấu hình; app ID thử không có pangle): classpath có adapter chưa đủ để thấy dòng đó. Dòng registry
  `sdk_health partners=meta,<p>` chỉ cần resource `ads_bidding_partners` (D50).
<!-- t2-gioi-han -->
- Rewarded interstitial (1.9.0 T2): chỉ AdMob (không MAX/Meta); brain không tham gia quyết định; màn intro (đếm ngược + "Bỏ qua") do SDK vẽ là **lựa chọn an toàn** của SDK, không phải trích dẫn chính sách AdMob; id chỉ từ resource `admob_rwi` (khoá Remote Config `cf_*` chưa có cho rwi); chuỗi intro chỉ có tiếng Anh (mặc định) và tiếng Việt, ngôn ngữ khác app tự thêm `appplugin_rwi_title`/`appplugin_rwi_countdown`/`appplugin_rwi_skip`; không có watchdog lượt show (như interstitial và rewarded): GMA không callback sau `show()` thì `flagAdsFullShowing` giữ, quảng cáo toàn màn hình khác bị chặn tới khi có callback; intro tạm dừng khi host `ON_STOP` (Activity khác che) vẫn giữ cờ đó tới khi người dùng quay lại hoặc host bị huỷ.
<!-- t9-gioi-han -->
- `calibrate.py` (Task 9) là chứng minh đường ống, **không tuyên bố**: không `mu` đã hiệu chỉnh, không lift, không tác động eCPM/ARPDAU, không ý nghĩa thống kê holdout, không tính hợp lệ phân khúc (D8; người dùng tổng hợp, ô dưới ngưỡng hỗ trợ — cổng app đầu); nhánh holdout của `v_paywall_funnel` tính lượt mua ngoài phiên của người bị giữ không theo thứ tự thời gian (kể cả trước lần bị giữ đầu) và ghi vào ngày mua, còn treatment ghi theo ngày phiên — `conversion_rate` hai nhánh không so trực tiếp được.
<!-- t6-gioi-han -->
- Native full AdMob (`AdsMobPlNtFull`, native hiện như full): impression/paid và click vẫn mang placement hiện hành của slot khi impression/click tới sau khi slot đã sang lượt khác (D48: file sát trần 800 dòng, không chạm ở 1.9.0); sửa ở 2.0.0 C1 cùng lúc tách file. MAX (đóng băng ở 1.9.0) giữ hành vi cũ như vậy.

**API public/protected không đổi** (`tools/api_dump.sh --check` → `API unchanged (5494 lines)`; so `2b9bd7e` (tag `v1.8.0` chưa có lúc đo) 0 dòng `-` ngoài `access$`, 178 dòng `+` (5316 + 178) đều thuộc khối T1–T11; façade PASS; D7 17/18 commit = 0, ngoại lệ `4a551f1` = 1 dòng `-` của `PurchaseFlow.take()`, lớp `internal` mới trong cùng dải, cả dải = 0).

**Đã kiểm (số đo Task R của `metrics-190` trên HEAD `99b7d5d` cho mã và `bcfe3c6` cho công cụ sau F3-t1 và F3-t2 (APK không đổi); APK utility sha256 `8ad41174047b9e90658dcc71ac3a90a20aa5ffc8e62354bb1c2129bc6bf407b6` (9 046 588 B), game `9bee22c9dd61ded6a2c99bffbb773921ef229cd4ca9fe8f21b374f631195d291` (27 483 403 B); sha và số byte lấy từ dòng `apk sha256=… bytes=…` của log release gate):**

- #1 unit ×3: 1831 test mỗi lượt / 0 skipped / 0 fail / 0 error, `@Ignore` 0 (1747 + 84; lượt 1 không có dòng tổng nên cộng từ XML; `t190-r-full-{1,2,3}.log`); #2 dump 5494 dòng, 0 dòng `-` ngoài `access$`, 178 `+` (`t190-r-apidump.log`); #3 façade PASS, D7 17/18 commit = 0 và `4a551f1` = 1 như trên (`t190-r-{facade,d7}.log`).
- #4 cỡ file: 0 > 800 (lớn nhất `AdsMobPlNtFull` 791); `AdsHelper` 696, `IapHelper` 453/455, `DecisionLogger` 570/580, `MainActivity` 367/375, `measure_gate.sh` 335/343, `device_gate.sh` 263/264, `release_gate.sh` 88/93 (`t190-r-sizes.log`). #5 shell: `device_gate_allowlist` 222/222, `gcp_guard` 128/128, `lane_pack` 10/10, FAIL 0 (plan ước 215 cho allowlist; số thật 222 sau F3-t1; `t190-r-shell.log`).
- #6 release gate `utility` PASS (`ad units in resources.arsc=11`, `absent-class sdk=0 allow=52`, mapping 399/403 = 99 %, `IAP_HARNESS` 1, `BRAIN_HARNESS` 1) và `game` PASS (`absent-class info=862 allow=30`, harness 0/0) (`t190-r-release-gate-{utility,game}.log`). #7 size: tải về `utility` min 7 642 770 / max **7 702 160** B ≤ 26 214 400; chênh so với 1.8.0 (7 685 835) = +16 325 B, trong ước ≤ +60 KB (thông tin); partner (thông tin): pangle +2 056 907 B, mintegral +2 242 047 B, vungle +368 310 B, **unity và applovin chưa đo** (R8 thiếu lớp, D51) (`t190-r-bundle-size*.log`); `--partners meta` bị từ chối trước Gradle (rc 1, D46).
- #8 gate `utility`: API 37, 33 (mới), 28, 26 đều PASS, FATAL/ANR/native 0, R8 0, `by sdk` 0 ×2, JSONL tăng, `precheck docker=0 sink=up`; `initPlugin total` cold1/cold2: 11/26 ms (37; 1.8.0 88/47), 41/48 (33; không có dải), 155/46 (28; 1.8.0 108/120), 158/61 (26; 1.8.0 187/113). Lượt đầu 28 = 274/77 và 26 = 56/268 vượt dải trong lúc host chạy bộ test shell; đo lại một lần (D54): 155/46 và 158/61, FATAL/ANR/native 0, host load1 5–15 từ ngoài lane: PASS trong dải ± nhiễu host (D56) (`t190-r-emu{37,33,28,26}-gate.log`, `t190-r-emu{28,26}-gate-2.log`; lượt 1 của API 37 `invalid (contention)` vì host load 14–24, lượt 2 PASS, `t190-r-emu37-gate.invalid-run0.log`).
- #9 rwi: chặng 1 (hiện, thưởng, đóng) **PASS** ở API 37 (intro 1, `ad_show` rwi 1, reward 1, dismiss 1) và Redmi; ở API 33/28/26 `ad_show` rwi 1 và `ad_revenue` rwi 1 (intro/reward/dismiss không tách); chặng 2 (`rwi_intro_skipped`), chặng 3 (`policy_min_gap`) và chặng 4 (`remove_ad`) **chưa đo (không có driver)**, logic ghim bằng unit T2 (D53); chặng 5 (N1) PASS (`t190-r-emu{37,33,28,26}-rwi.log`).
- #10 banner/NtCl: **PASS trên cả năm máy** với `banners: parent=0 show=6 ntcl_ok=1 ntcl_bad=0 adview=1/1`, `AdView name after R8: i4`, FATAL/ANR/native 0: API 37 và Redmi với công cụ `82d1176`; API 33 sau một lần đo lại (D55: vòng 2 `ntcl_ok=0` vì không fill trong cửa sổ, `cl_default show not loaded`, không phải lỗi SDK); API 28 và 26 với công cụ `bcfe3c6` (F3-t2: probe nhận dòng `TASK <pkg>` không có `uid:` của API 28/26). Lượt đầu (`99b7d5d`) FAIL ở `adview=0/0` do lỗi công cụ (R8 đổi tên `AdView`, probe `\.AdView\{` không khớp, D52; sửa ở F3-t1) và được các lượt trên thay thế. Chevron: chưa đo (không bấm vào creative, R10) (`t190-r-emu{37,33,28,26}-banners{,-2,-3}.log`, `t190-r-redmi-banners-2.log`).
- #11 G11 (i)–(v) PASS ở API 37, 33, 28, 26 và Redmi (API 37: accepted 52 = distinct 52, bền 2 mất 0, distinct 68 trùng 0, 9 `request_id` > 1 kết thúc 0; Redmi: 52 = 52, distinct 124 trùng 0); show rate (`v_show_rate`): interstitial 1/1, rewarded 1/1, rewarded_interstitial 1/1 = 100 % (n = 1, mẫu mỏng), NtCl 0/0 (thông tin), declined tách ở `v_brain_decisions` (`t190-r-emu*-g11.log`, `t190-r-redmi-g11.log`, `t190-r-be-showrate.log`).
- #12 G12 12 script: **12/12 PASS** ở API 37, 33, 28, 26 và Redmi (FAIL/`invalid` không; by-sdk 0 mọi script ở API 37 và Redmi); phần rwi `remove_ad` của `buy_cheap` **chưa đo (không có driver)**: `iap_gate.sh iap_show` chỉ chạm Interstitial và Rewarded; chưa có hàng rwi `remove_ad` trong dữ liệu (D53) (`t190-r-emu{37,33,28,26}-g12.log`, `t190-r-redmi-g12.log`). #13 consent N1 (API 37): DENIED 0 hàng, 0 POST, kể cả hàng rwi, brain và partner; sau GRANTED `flushed 16 held after DENIED`, +17 hàng (`t190-r-emu37-consent.log`).
- #14 chặng pangle (API 37, APK dựng riêng từ `aee3418`, trước T6 — không phải APK cuối): gate PASS, FATAL/ANR/native 0, by-sdk 0, StrictMode pangle 0; dòng `ads admob adapter pangle:` **0 = chưa đo** (cần nhóm mediation trên AdMob console, OWNER-NOTES #5, D50); không tuyên bố impression partner (`t190-r-emu37-pangle.log`).
- #15 backend: `pytest` 334 passed / 1 skipped / 0 failed (315 unit + 19 integration); parity 11/11 view Δ 0; MERGE G11+gate: staging +268 → raw 198 = distinct, lần 2 +0; G12 12 file: staging +346 → raw 542 = distinct, lần 2 +0, `iap_purchase` raw 6 = 6 token (`t190-r-be-{g13,parity,merge}.log`). #16 calibrate end-to-end: rc 0, cây khoá = mẫu (14 khoá, 0 lệch), `version = calib-20261001-f4bc6102`, 16/16 ô support n = 0 → prior (1 người dùng), **không tuyên bố `mu` nào** (`t190-r-be-calibrate.log`). #17 D-RC 3 chặng qua harness brain (API 37): (1) `BrainPolicy: đã nạp 'calib-20260930-821a919f'`; (2) `enabled:false` → 0 `brain_decision` sau dòng nạp (đối chứng yếu: chặng 1 cũng không thao tác quảng cáo); (3) `rollout_fraction:0` giữ qua cold start không extra; 0 ghi Firebase (`t190-r-emu37-drc.log`); meta-data `BRAIN_HARNESS` utility 1 / game 0. #18 báo cáo ngày 2026-10-01: 11 bảng, `v_ecpm_network_format` 4 hàng có `ad_network`, hàng `ad_format=rewarded_interstitial` 1 impression (doanh thu 0 = quảng cáo thử) (`t190-r-be-report.log`). #19 Docker: 0 (`pgrep` = 0; chỉ container `bq-e190` tạo/xoá; `precheck docker=0` mỗi gate; `t190-r-be-docker.log`).
- #20 che token: quét 6 mẫu #40 trên `DOC/testing/t190-r-*.log` = 0 (không cần che; `t190-r-redact.log`). #22 D-GMA: **no-go cho 2.0.0** (Meta bidding trên Next-Gen đạt nhưng không drop-in: MAX google-adapter không cùng tồn tại, callback nền, hook Full2 cỡ L); Next-Gen ở 2.1.0 sau C2.
- Máy thật Redmi 65c9b673 (API 34, sink 8101, APK `8ad41174` + công cụ `99b7d5d`): gate PASS (FATAL/ANR/native 0, R8 0, by-sdk 0 ở cả hai cold start, JSONL 0 → 152, `initPlugin total` 28/11 ms; 1.8.0 53/16); G11 (i)–(v) PASS, H6a PASS, case 'Show Rewarded Interstitial' tới app và hiện (`AdActivity`); rwi chặng 1 PASS, chặng 2–3 chưa đo (không có driver); G12 12/12 PASS (`t190-r-redmi-{gate,g11,g12,banners}.log`). Samsung R5CX10P9EKZ: chưa đo.

**Chưa kiểm:** rwi chặng 2, 3 và `remove_ad` (không có driver, D53; 2.0.0 tooling); dòng `ads admob adapter pangle:` (cần console, D50) và impression của mọi partner; `unity`/`applovin` ở bản dựng có partner (R8 thiếu lớp, D51); chevron của collapsible banner (không bấm được vào creative, R10); số `mu` hiệu chỉnh, lift, eCPM/ARPDAU thật (cần app thật; `calibrate` chạy trên 1 người dùng); Remote Config thật cho brain (D-RC dùng harness, OWNER-NOTES); Samsung R5CX10P9EKZ; mua/khôi phục Play thật (H7b, OWNER-NOTES #4); CI và release (#23, tại tag).

## [1.8.0] - 2026-10-02

"1.8.0 — subscription, IAP và paywall đạt chuẩn phát hành" (plan docs/superpowers/plans/2026-09-30-sdk-1.8.0-plan.md). API public/protected không đổi.

### Thêm

- Event IAP/paywall v2 (hợp đồng E): `IapEventLog` dựng `iap_purchase` (thêm `phase`, `offer_tag`, `paywall_session_id`, `placement`, `variant_id`; không `usd`, D3), `iap_result`, `paywall_impression`/`paywall_holdout_skip`/`paywall_skip`/`paywall_choice`, `subscription_state` (thêm `source`); `PaywallSession` giữ phiên paywall 30 phút. Sáu event mới vào `DurableEvents.DURABLE` và `LogEventControl.NEVER_SAMPLE` (D7): không lấy mẫu, sống qua kill. Builder chưa có chỗ gọi (Task 5/9/10/11 nối); API public không đổi (1.8.0 T7, `t180-t7-{red,green,full}.log`).
- Harness billing G12 (hợp đồng B, D8): `IapHarness` thay port store bằng `ScriptedBillingPort` (lớp duy nhất tạo nó là `BillingPorts.harness`) trên luồng "appplugin-billing", `end()` port Play, rồi chạy một trong **12** script của `IapScripts` (`buy_sub`, `buy_cheap`, `cancel`, `pending_then_paid`, `kill_mid_purchase` → mở lại `restore_unacked`, `restore_owned`, `reopen`, `restore_store_error`, `revoke`, `grace`, `denied`, `messages_off`) **qua state machine thật** của `IapHelper` (quyền lợi, ack, sổ token, `iap_result`, vòng đời). Arm khi đủ 3 khoá: `flagTest` bật, meta-data `com.app.plugin.IAP_HARNESS=true` (chỉ manifest `utility` của demo), extra `appplugin_iap_script <tên>` ∈ bảng; kill switch `iap` → không arm. Log `iap harness: armed script=<tên>`, `iap harness: <tên> step=<k> <kết quả>`, `iap harness: <tên> done`, `iap harness: in-app-messages call`; script 10/12 dùng token fixture RTDN `rtdn-fixture-sub-token`. `AppPluginBase.onActivityCreated` thêm 1 dòng; API public không đổi (1.8.0 T8, `t180-t8-{red,green,full}.log`).
- Paywall schema v2 (hợp đồng D, `paywall_config_android`): `schema_version` 2, khối `holdout.fraction` riêng mặc định **0** rút một lần trên `user_id` của SDK trong không gian băm `paywall_holdout_v2` (độc lập holdout brain `brain_holdout_v1`, không kế thừa tỉ lệ brain; D6/D14), `session_cap` cho placement `proactive` (mỗi process; placement người dùng mở không bị giới hạn), `paywall_skip` (`no_config`/`session_cap`/`brain`), `onPaywallShown` mở `PaywallSession` và gửi `paywall_impression` kèm `paywall_session_id` + `schema_version_cfg` (`paywall_choice`/`iap_purchase` của lượt mua kế tiếp mang cùng id). `schema_version` khác 2 → giữ cấu hình đang chạy + `logE`; JSON v1 (không `schema_version`) vẫn nhận. Parse tách sang `paywall/PaywallConfigV2.kt` (internal); API public không đổi. Giới hạn đã biết: phiên paywall gắn theo thời gian (30 phút, T7), không đóng khi người dùng tắt paywall — lượt mua trong 30 phút sau một paywall đã tắt vẫn mang `paywall_session_id` của paywall đó (1.8.0 T9, `t180-t9-{red,green,full}.log`).
- Tín hiệu vòng đời subscription từ server (hợp đồng C, D9): phản hồi 200 của `/push` mang `signals` (`v` 1, `type` `sub_state`, ≤ 5 phần tử); `LogEventSender` đọc tối đa 4 096 byte của body 2xx trước khi đóng (quá → bỏ cả khối, log một dòng), `ServerSignals` parse và áp trên một luồng nền (Retrofit gọi callback trên main). Ánh xạ nhãn: GRACE → `IN_GRACE_PERIOD`; ON_HOLD, PAUSED, REVOKED, EXPIRED → `LAPSED`; CANCELED → `CANCELLED`; RECOVERED, RENEWED, RESTARTED, PURCHASED → `ACTIVE`; trạng thái gốc giữ nội bộ (`SubscriptionServerState`, cùng `event_ms` áp cuối theo sản phẩm, lưu qua prefs). Tín hiệu sai dạng (`v` ≠ 1, `type`/`state` lạ, `event_ms` ≤ lần áp cuối) bị bỏ. Nhãn server còn trong cửa sổ 7 ngày và mới hơn lần đổi cuối của client thắng lượt restore thấy PURCHASED (Play báo sub trong grace như sub đang gia hạn: grace/hold được giữ); store không còn trả sub → `LAPSED` như cũ (D27). Chỉ đặt nhãn: không bao giờ cấp hay thu hồi quyền lợi; `RecurringGrants` chỉ tính sở hữu từ Entitlements/Play trên máy, không từ nhãn server (D28). `subscription_state` thêm `source` (`client` từ Play trên máy, `server` từ tín hiệu); API public không đổi (1.8.0 T10, `t180-t10-{red,green,full}.log`).
- SDK tự mở hộp thư Play (`BillingClient.showInAppMessages`, `TRANSACTIONAL`) ở lần vào foreground kế khi một subscription đang grace (`IN_GRACE_PERIOD`) hoặc bị hold theo server (`ON_HOLD`/`GRACE` còn trong cửa sổ 7 ngày, D27), để người dùng sửa phương thức thanh toán (D2). Tối đa một lần mỗi 24 giờ mỗi sản phẩm (mốc lưu prefs); quyết định chạy trên luồng `appplugin-billing`, chỉ lời gọi Play chạy trên main; Play báo `SUBSCRIPTION_STATUS_UPDATED` → SDK khôi phục giao dịch. Tắt bằng `subscription_lifecycle_android` `"in_app_messages":{"enabled":false}` (mặc định bật; `enabled=false` của cả khối cũng tắt; khối sai dạng, vd. `enabled` không phải boolean → tắt); im dưới harness G12 trừ script `grace`/`messages_off`. `BillingProxyCrashGuard` (port từ Speaker Clean, F9): chỉ nuốt `NullPointerException` có khung `com.android.billingclient.api.ProxyBillingActivity` trên main (Play dựng lại activity mua sau khi process bị kill giữa lượt mua), tối đa 5 lần mỗi process; mọi ngoại lệ khác chuyển nguyên cho handler trước (Crashlytics). Cài trên main lúc `AppPluginBase.onCreate` (idempotent, trước mọi `ProxyBillingActivity` dựng lại; `IapHelper.init` là dự phòng, D29). API public không đổi (1.8.0 T11, `t180-t11-{red,green,full}.log`).

### Sửa

- `IapHelper` chỉ còn state machine, lời gọi Play Billing chuyển sau cổng nội bộ `BillingPort` (1.8.0 Task 4, `iap/BillingPort.kt`, `PlayBillingPort.kt`, `BillingPorts.kt`): thân Play chép nguyên văn, cùng luồng, cùng chữ log, cùng mã và `debugMessage` trong `IapResult.Failed`, cùng thứ tự gán callback của lượt mua (D20); `IapHelper.kt` 583 → 449 dòng, 0 import `com.android.billingclient`. Khác biệt duy nhất: giao dịch `isSuspended()` (PBL 9) không cấp quyền lợi, như PENDING (D18).
- Luật Play trong `IapHelper` (1.8.0 Task 5): giao dịch `PENDING` (kể cả sub) và `isSuspended()` không bao giờ cấp quyền lợi, không còn thành `IN_GRACE_PERIOD` (D4/D18); `iap_purchase` đúng một lần theo purchase token qua sổ `iap/PurchaseLedger.kt` (prefs `mem_iap_ledger`, ≤ 200 token): dấu `granted` ghi đĩa trước, event đi qua tập bền với `ts` = giờ giao dịch, dấu `reported` khi server trả 2xx; token đã cấp mà chưa báo (kill giữa chừng) được phát lại ở lần khôi phục sau, cùng token, `ts` gốc (D13/D15); khôi phục thấy giao dịch trả tiền mà sổ chưa biết cũng báo một lần (AppsFlyer/brain chỉ khi chưa acknowledge); `price_micros` = pha thực thu (trial 0, intro, base) + `phase`, `offer_tag`, phiên paywall (F4/D5); `iap_result` cho mọi kết cục (thêm `ack_failed`) và `paywall_choice` khi mua trong phiên paywall; mọi lệnh store và xử lý kết quả chạy trên luồng `appplugin-billing`, callback cho app về main như trước (D11); `BILLING_UNAVAILABLE` (máy không có Play) → một dòng `IapHelper unavailable: 3 (không thử lại trong process)` và 0 lệnh store thêm (F7); query giá lỗi tạm thời thử lại sau 1-2-4-8-16 s rồi dừng; khôi phục lúc kết nối chạy sau khi có giá; một luồng mua tại một thời điểm — `purchase()` thứ hai trả `Failed(-2, "đang có luồng mua")` ngay (D22); store trả OK mà không có sản phẩm khi mua → `Failed(4)` (`ITEM_UNAVAILABLE`) thay `Failed(0)` (D20 C2).
- Quyền lợi lưu có chữ ký (1.8.0 Task 6, D10): `Entitlements` ghi bản ghi `v1|<ids>|<masks>|<hex mac>` (prefs `iap_entitlements_v1`) ký HMAC-SHA256 bằng khoá AndroidKeyStore `appplugin_ent_v1` (`iap/EntitlementIntegrity.kt`, 0 thư viện mới); kiểm lúc khởi tạo trên luồng `appplugin-billing`, một dòng `ent verify ms=<n> result=Verified|Legacy|Invalid`, áp mask quảng cáo khi xong; bản sửa tay hay khôi phục sang máy khác (khoá Keystore không theo backup) → rỗng + tự `restorePurchases()`, không thu hồi gì trên Play; giá trị chưa ký của bản cũ nhận một lần rồi ký lại. Keystore lỗi → khoá ngẫu nhiên theo process + một dòng cảnh báo (quyền lợi lấy lại từ Play mỗi lần mở). `active` bất biến (copy-on-write, hết race đọc không khoá); ký + ghi đĩa chạy trên luồng billing sau khi nhả monitor (#31) (1.8.0 T6, `t180-t6-{red,green,full}.log`).
- Carry SDK 1.7.0: `AdsTelemetry.close` chỉ phát một lần kể cả callback tới từ nhiều luồng (CAS của `ConcurrentHashMap`; response chưa từng mở vẫn phát `request_id` rỗng, `latency_ms` -1); `ad_request`/`ad_fill`/`ad_no_fill` mang `slot` (khoá slot mạng, tách khỏi `placement`; `LoadRequest` không đổi); test hợp đồng chặt hơn (`consent_tier` theo mọi tier, `schema_version` là số, phép quét literal thấy lời gọi nhiều dòng/có comment/có tên tham số). Chưa làm: placement của impression chụp lúc show (R-3b m2, cần 24 chỗ nghe trong `ads/adsmob|adsmax|adsfb`, ngoài Files của task) (1.8.0 T15, `t180-t15-{red,green,full}.log`).
- Nhật ký quyết định brain không còn I/O đĩa trên main (1.8.0 F-P1, D31): sink mặc định của `DecisionLogger` giao mọi truy cập `DecisionLogQueue` (ghi + cắt trần, đọc/xoá khi flush) cho MỘT luồng nền daemon `appplugin-brain-log` (đổi tên từ `appplugin-decision-log`, cùng luồng đếm/flush/xoá), FIFO; bản ghi vẫn xuống đĩa trước khi gửi sống (gửi chạy trên cùng luồng, sau lần ghi); `log()` chỉ chụp `user_id`/`ts` trên luồng gọi. Hết DiskRead/DiskWrite by-sdk của `PaywallManager.paywallFor` → `AdsBrain.shouldOfferIap` → `DecisionLogger.log` trên main (R-16 P1, G12 #1/#10/#11/#12). API public không đổi (`t180-p1-{red,green,full}.log`).

### Backend

- Doanh thu IAP vào ARPDAU, phễu paywall theo phiên, khử trùng IAP theo token (1.8.0 Task 12): `iap_purchase` khử trùng theo `iap_purchase:tok:<purchase_token>` (không token → `order_id` như cũ), nên bản phát lại sau restore/pending → paid/cold start (D15, `event_id` mới, có thể thiếu `order_id`) chỉ tính một lần; 7 cột v2 mới ở cuối `events_raw`/`events_staging` (`phase`, `offer_tag`, `paywall_session_id`, `outcome`, `response_code`, `schema_version_cfg`, `source`) và 3 event đã biết mới `iap_result`, `paywall_skip`, `paywall_choice`; view `v_arpdau_country` (ARPDAU = quảng cáo + IAP theo nước, USD quy đổi ở server theo `fx_daily` (D3), `phase = trial` = 0, không tỉ giá → `iap_unconverted`) và `v_paywall_funnel` (hiện → chọn → mua theo `paywall_session_id`, `closes` = phiên hiện mà không chọn), oracle `ad_metrics.py` + parity trên bigquery-emulator; `metrics.paywall_funnel` nhân `1/sample_rate` (D7, phòng thủ); báo cáo ngày `tools/report_daily.sh` 9 bảng. Bảng đã tạo trước bản này cần thêm 7 cột (bảng chỉ được tạo-nếu-chưa-có).
- Nhận RTDN cục bộ, trả tín hiệu vòng đời trong `/push` (1.8.0 Task 13): `POST /rtdn` nhận envelope Pub/Sub push (`message.data` base64 của `DeveloperNotification`), xác thực bằng bearer tĩnh `BRAIN_RTDN_TOKEN` (≥ 16 ký tự, `hmac.compare_digest`; không đặt → 503, production dùng OIDC của Pub/Sub = H7b), 2xx chỉ sau khi ghi bền; `notificationType` 1, 2, 3, 4, 5, 6, 7, 10, 12, 13, 20 → trạng thái (6 = `GRACE`), `voidedPurchaseNotification` → hàng hoàn tiền âm, `testNotification` → 2xx không ghi; bảng mới `subscription_events` (khử trùng `rtdn:<messageId>`, token → `user_id`/`app_id` từ `iap_purchase` đã lưu, cả JSONL và BigQuery); phản hồi 200 của `/push` thêm `signals` (§C: ≤ 5, nhận trong ≤ 7 ngày, trạng thái mới nhất mỗi sản phẩm, chỉ khi lô có đúng một `user_id`, cùng app của khoá; đọc hỏng → `[]`, không 5xx); `SubscriptionSource` (fixture; `subscriptionsv2` thật = H7b → 503); `bq_setup` dừng rõ ràng (rc 1, kèm câu `ALTER TABLE … ADD COLUMN`) khi bảng có trước thiếu cột, không tự sửa bảng (R-12 m3); test tiền/holdout của `metrics.paywall_funnel` theo `sample_rate` (R-12 m4). Fixture: `backend/tests/fixtures/rtdn/`.
- T13b — cache `signals` và hai test bảo mật (1.8.0 Task 13b, đóng R-13 m1/m4): `push_signals` dùng cache trong tiến trình theo `(app_id, user_id)` 60 s (`SIGNALS_CACHE_TTL_S`, ≤ 10 000 mục, `/rtdn` ghi thì xoá mục của user; lỗi đọc không được lưu) nên `/push` đơn user không tạo một truy vấn BigQuery mỗi event; test: hai `iap_purchase` cùng token thì lượt sớm nhất thắng (chống chiếm token) và `BRAIN_RTDN_TOKEN` < 16 ký tự bị từ chối lúc nạp cấu hình.
- Backend: `admob_import` từ chối báo cáo có nhiều phần tử `header`, `MATCHED_REQUESTS` lớn hơn `AD_REQUESTS`, và `header`/`footer` không phải object (lỗi miền `AdmobImportError`); `tools/gcp_guard.sh` chỉ cho `--set-env-vars` dạng `KEY=VALUE` với KEY thuộc danh sách khoá của `deploy_cloudrun.sh` (`BRAIN_BQ_PROJECT` phải là project cho phép). Carry R-11/R-12 của 1.7.0.

### Công cụ

- `tools/local_sink.sh start|stop|status --port <p> --dir <d>` và `device_gate.sh` / `daily_session.sh` `--sink-port <p> --sink-dir <d>` (`adb reverse tcp:8089 tcp:<p>`, pha C của phép đo reverse lại đúng cổng lane): mỗi lane một sink và một `events.jsonl` riêng nên máy thật và emulator chạy song song; không cờ thì vẫn 8089 và `DOC/testing/local-sink`. APK không đổi.
- Mọi lệnh tới máy trong `tools/` (`tools/lib/*.sh`, `device_gate.sh`, `daily_session.sh`) đi qua `adb_b <giây> <đối số adb>` (`tools/lib/adb_b.sh`): mỗi lệnh có hạn (60 s; `uiautomator dump` và `logcat -d` 30 s; install 120 s; monkey theo `EVENTS`), quá hạn thì kill đúng tiến trình con, in một dòng `FAIL: adb_b timeout` và trả 124, Ctrl-C cũng kill adb nền, không thử lại. `device_gate.sh` in `precheck docker=<n> sink=<up|down>` vào log, FAIL khi JSONL của sink không tăng dòng nào trên utility (R-8), và `mg_start` quá hạn vẫn in `TotalTime` đã đọc. adb giả của test có API (`FAKE_API`) và màn hình (`FAKE_WM`) và sink giả ghi dòng JSONL. Thêm 9 kiểm tra shell; hết hạn `logcat -d` là FAIL và ghi vào log (R-T2 I1). APK không đổi.
- Công cụ: `release_gate.sh` in `absent-class sdk=<n> allow=<k>` (`check_absent_classes`: kiểu được tham chiếu trong `classes*.dex` trừ kiểu định nghĩa, trừ `android.`/`java.`/`javax.`/`kotlin.`/`dalvik.`, phải nằm trong `tools/absent_class_allowlist.txt`; bài học MAX 1.6.4); `gcp_guard.sh` chạy `bq` với `BIGQUERYRC=/dev/null` và nhận `--set-env-vars` dạng `^|^KEY=VALUE|KEY=VALUE` (chỉ cờ này; `backend/deploy_cloudrun.sh` dùng dạng đó để `BRAIN_FX_RATES` mang dấu phẩy); workflow CI ghim `ubuntu-24.04`.
- Công cụ cổng `--iap` (1.8.0 TF-1): bỏ node uiautomator bounds `[0,0][0,0]`/diện tích 0 khi chọn nút (không chạm (0,0); chỉ còn node ẩn → `invalid (ui)`), `precheck docker=` chỉ đếm tiến trình `/Applications/Docker.app/Contents`, mỗi lượt G12 ghi JSONL ở thư mục sink riêng `<sink-dir>/g12-<serial>-<giờ>` (khử trùng của lượt trước không làm script 10 nhận `stored:false`) (`t180-tf1-{red,green,mutation-M1..M3}.log`).
- Công cụ cổng `--iap` (1.8.0 TF-2): API 28/26 form UMP dựng lại sau "Privacy options" báo mọi node bounds `[0,0][0,0]` → nút "Consent" không còn node hiển thị: chạm toạ độ tương đối (0.50×rộng, 0.70×cao của `wm size`, 1080×2400 → 540,1680), ghi `consent tap fallback x,y (no visible node)`, kiểm bằng logcat `consent tier=GRANTED` mới; không tới thì script 11 vẫn `invalid (ui)` (không PASS giả); có node hiển thị (API 37) giữ đường cũ (Z15).
- `tools/device_gate.sh --iap` chạy cổng G12: 12 script billing của harness (§Hợp đồng B) trên một máy — `pm clear` khi cần, `am start -W --es appplugin_iap_script <tên>`, chờ dòng `done`, chạm show, `am force-stop` (script 5), fixture `/rtdn` (script 10, 12), HOME + mở lại, luồng consent (script 11) — rồi `tools/iap_check.py` (chỉ thư viện chuẩn, fail-closed: thiếu JSONL/log/meta, JSONL 0 dòng hay tên script lạ = FAIL) khẳng định từng hàng: `iap_purchase` đúng một dòng theo token, ack `first=true`, `ad_show` 0 sau grant, 0 dòng tới sink giữa DENIED và GRANTED, `subscription_state IN_GRACE_PERIOD source=server`, `showInAppMessages` 1 lần ở foreground 1 và 0 ở foreground 2 (tắt bằng cấu hình: 0). Logic ở `tools/lib/iap_gate.sh` (mọi lệnh qua `adb_b`), bằng chứng từng script ở `<log>-iap/`; script không tới được giao diện là `invalid`, không tính. `tools/local_sink.sh start` xuất `BRAIN_RTDN_TOKEN` (mặc định token demo cục bộ) cho `/rtdn`. Thêm 10 kiểm tra shell (Z1–Z10, adb và curl giả cho 12 script).

### Phải làm khi nâng cấp

- Sub `PENDING` không còn tắt quảng cáo; `IN_GRACE_PERIOD` chỉ tới từ server (T10). `purchase()` gọi khi luồng mua trước chưa xong nhận `IapResult.Failed(-2, …)`; kết quả của lỗi "không có sản phẩm" lúc mua là mã 4 thay 0.
- Quyền lợi đọc từ đĩa chỉ có hiệu lực sau lượt kiểm chữ ký trên luồng nền (vài ms lúc mở app); trong lúc đó `Entitlements.isActive`/`activeProductIds` trả rỗng — `AdsHelper` giữ mask đã lưu (SDK không đẩy mask nào sang nó trước khi kiểm xong) nên người đã mua không thấy quảng cáo; `grant`/`sync`/`revoke` gọi trong lúc đó được phát lại lên tập đã kiểm, không làm mất quyền lợi đã ký. Sửa tay prefs hay khôi phục backup sang máy khác làm quyền lợi rỗng tới khi `restorePurchases()` (SDK tự gọi) trả lời; app không cần làm gì thêm, không cần loại prefs khỏi backup.
- Holdout paywall mặc định 0 (trước: 5 % theo `HoldoutManager.holdoutFraction`, chung nhóm với brain): muốn holdout thì khai `holdout.fraction` trong `paywall_config_android` v2 (`"schema_version":2`). Cấu hình v1 không còn giữ ai lại; `HoldoutManager.holdoutFraction` chỉ còn là holdout của brain (1.8.0 T9).

### Giới hạn đã biết
- Impression tới trễ (sau khi slot đó đã hiện/lấy quảng cáo tiếp theo) vẫn mang placement mới hơn như 1.7.0 (R-3b m2): việc chụp placement theo từng đối tượng quảng cáo dời sang 1.9.0 (quyết định D21); từ 1.8.0 nối load → show theo trường `slot` mới của `ad_request`/`ad_fill`/`ad_no_fill`.

- `iap_purchase` phía client của người chọn DENIED không được báo (D15; token nằm ở `!reported`, chỉ phát lại ở process sau có tầng GRANTED/PARTIAL); sự thật về tiền là RTDN/`subscriptionsv2` (H7b). Nếu thư viện Play không bao giờ gọi lại sau khi mở màn mua, `purchase()` tiếp theo trong process đó bị từ chối (D22).
- Harness billing chỉ bật khi đủ 3 khoá; app release với `flagTest` 0 không bao giờ nạp lớp harness (`ScriptedBillingPort`, `IapScripts`; cổng `IapHarness` chỉ đọc khoá 1 + 2 một lần mỗi process). Khi arm, harness thay catalog sản phẩm của app bằng catalog riêng (`g12_sub` mask ALL, `g12_lite` mask INTERRUPTIVE) và nối vào `purchaseListener` tới kết quả cuối; lượt khôi phục lúc vào foreground có thể chồng lượt khôi phục của script (ack thứ hai ghi `first=false`).
- Grace/hold chỉ biết khi backend nhận RTDN (H7b); không có backend → nhãn suy từ Play như cũ. Nhãn server chỉ thắng quan sát client trong 7 ngày kể từ `event_ms`; quá cửa sổ mà không có tín hiệu mới thì nhãn lại suy từ Play (1.8.0 T10, D27).
- Bằng chứng billing của 1.8.0 là harness G12 (logic SDK, qua state machine thật) và backend `/rtdn` trên fixture; chưa có mua thật trên Play internal testing, grace/hold/renewal/refund thật, RTDN thật (Pub/Sub, OIDC) hay credential `subscriptionsv2` (backend trả 503 cho bản thật), và NPE `ProxyBillingActivity` chưa tái hiện được trên máy (chỉ có unit): tất cả là H7b, OWNER-NOTES #4 (DOC/67 §4). Hướng dẫn: README mục 8.
- Trigger `suspended` của hộp thư Play chưa hoạt động ở 1.8.0: Play không trả sub bị treo cho `queryPurchasesAsync` nếu không bật `includeSuspendedSubscriptions`, và port Play chưa bật (ứng viên 1.9.0); grace/hold vẫn được nhắc qua nhãn server (D27, D29).

**API public/protected không đổi** (`tools/api_dump.sh --check` → `API unchanged (5316 lines)`; so `v1.7.0` 0 dòng `-` ngoài `access$`, 603 dòng `+` đều ánh xạ T4–T11/T15, trong đó 6 overload `AdEventLog` của T15 là `internal object`; façade PASS, D7 25/25 commit).

**Đã kiểm (số đo Task R của `metrics-180` trên HEAD `12e1aa1` cho mã và `963a334` cho công cụ sau TF-1; APK utility sha256 `99fe92414b54…`, game `29b9e7dd86c6…`):**

- #1 unit ×3: 1747 test / 0 skipped / 0 fail / 0 error mỗi lượt, `@Ignore` 0 (1653 + 95 − 1; `t180-r-full-{1,2,3}.log`); #2 dump 5316 dòng, 0 dòng `-` ngoài `access$`, 603 `+` ánh xạ T4–T11/T15 (`t180-r-apidump.log`); #3 façade PASS, D7 25/25 (24/24 lúc E1, 25/25 sau TF-1; `t180-r-{facade,d7}.log`, `t180-tf1-ig-*.log`).
- #4 cỡ file: 0 > 800 (lớn nhất `AdsMobPlNtFull` 791); `IapHelper` 459/459, `Entitlements` 248/250, `PaywallManager` 289/321, `SubscriptionLifecycle` 360/360, `AppPluginBase` 288/295, `LogEventSender` 205/215, `DecisionLogger` 570/580 (D32) (`t180-r-sizes.log`). #5 shell trên head cuối: `device_gate_allowlist` 194/194, `gcp_guard` 128/128, `lane_pack` 10/10 trên `410c855` (`t180-tf2-ig-*.log`; công thức đếm của plan cho 217 là sai cách đếm, không phải thiếu test).
- #6 release gate `utility` PASS (`absent-class sdk=0 allow=52`) và `game` PASS (`info=862 allow=30`); `IAP_HARNESS` trong manifest utility 1 / game 0 (`t180-r-release-gate-{utility,game}.log`). #7 size: aab 13 954 157 B, tải về `utility` min 7 630 564 / max **7 685 835** B ≤ 26 214 400; chênh so với 1.7.0 (7 652 009) = +33 826 B, cao hơn ước +20 KB (thông tin, chấp nhận) (`t180-r-bundle-size.log`).
- #8 gate `utility` emulator: API 37 / 28 / 26 đều PASS, FATAL/ANR/native 0, R8 0, `by sdk` 0, JSONL tăng, `precheck docker=0`; `initPlugin total` 88/47 ms (37), 108/120 ms (28), 187/113 ms (26) (`t180-r-emu{37,28,26}-gate.log`). #9 F7/D11 trên API 28/26: `Billing service unavailable` 1 và `IapHelper unavailable` 1 mỗi cold start, ngoài main; `setup failed` trên main 0 (1.7.0: ×7). #10 G11 hồi quy (i)–(v) PASS ở cả ba emulator (`t180-r-emu{37,28,26}-g11.log`).
- #11 G12 12 script: **API 37 12/12 PASS** (FAIL không, `invalid` không; `by sdk` 0 sau F-P1, trước đó 9; `t180-r-emu37-g12.log`). API 28/26 lượt đầu **11/12**: chỉ script 11 `denied` FAIL do lỗi công cụ (`iap_form_xy` chọn node "Consent" có bounds `[0,0]` → chạm (0,0), không GRANTED, 0 dòng JSONL; chạm tay đúng toạ độ → GRANTED, `flushed 9 held`, sản phẩm đúng); sửa ở TF-1 (`963a334`, chỉ công cụ); log lượt 11/12 giữ nguyên (`t180-r-emu{28,26}-g12.log`). Đo lại 28/26 sau TF-1: INVALID 11/12 (UMP WebView trên 28/26 báo mọi node bounds `[0,0][0,0]` → không node nhìn thấy; sửa tiếp ở TF-2, chỉ công cụ: chạm toạ độ tương đối). Với công cụ TF-2 (head công cụ `410c855`, code `12e1aa1`): **API 28 12/12 và API 26 12/12 PASS** (`consent tap fallback 540,1680`, `consent tier=GRANTED` ở script 11; FATAL/ANR 0, `by sdk` 0, `docker=0`; API 26 lượt 1 invalid vì công cụ bị sửa trong worktree chung lúc đo, lượt 2 từ bản `git archive c4803aa`) (`t180-r-emu28-g12-tf2.log`, `t180-r-emu26-g12-tf2-rerun.log`).
- #12 Play thật API 37: PASS kèm khoảng trống thông tin — `IapHelper connected` 1, không có dòng lỗi query/restore, `ent verify ms=0 result=Verified`, by-sdk 0, FATAL/ANR 0; mã không in dòng thành công cho mã query sản phẩm hay `restore OK owned=N`, nên chỉ có bằng chứng "không có dòng lỗi" (1.9.0: thêm một dòng log thành công) (`t180-r-emu37-realplay.log`). #13 consent R-P1 (API 37): DENIED 0 hàng, 0 POST, 0 `consent_*`/`iap_*`/`paywall_*`; sau GRANTED flush: `consent_form_shown` 1, `consent_choice` 1, `flushed 9 held after DENIED` (`t180-r-emu37-consent.log`).
- Máy thật Redmi 65c9b673 (API 34; E-real 08:30–08:45 01/10 trên bản `git archive 6ad8ede`, sink 8090): gate PASS (FATAL/ANR/native 0, R8 0, by-sdk 0 ở cả hai cold start, `initPlugin total` 53/16 ms, `TotalTime` 733/374 ms); G11 (i)–(v) PASS, H6a 3/3/3; G12 **12/12** PASS lượt đầu (by-sdk 0 mọi script); Play thật PASS (`IapHelper connected` 1, không dòng lỗi query/restore — cùng khoảng trống thông tin như API 37); #14 E-real ∥ E-emu: N/A (không emulator chạy song song). Samsung R5CX10P9EKZ: chưa đo (không cần thêm cho 1.8.0) (`t180-r-redmi-{gate,g11,g12,realplay}.log`).
- #15 backend: `pytest` 312 passed / 1 skipped / 0 failed (295 unit + 17 integration); parity 9 view 11/11; MERGE IAP staging +53 → raw 49 = distinct, lần 2 +0, `iap_purchase` raw 6 = 6 token; trên JSONL G12 thật (API 37): 329 hàng, staging +329 → raw 327 = distinct, lần 2 +0, `iap_purchase` raw 6 = 6 token (`t180-r-be-{g13,parity,merge,merge-dev}.log`). #16 báo cáo ngày 9 bảng (327 hàng): `v_arpdau_country` US IAP 25,94 USD / 6 lượt, DAU 11; `v_paywall_funnel` impression 4 ≥ choice 4 ≥ purchase 3 (`t180-r-be-report{,-dev}.log`). #17 `/rtdn` fixture: 11/11 lưu, `subscription_events` 11 hàng; `/push` của user fixture trả đúng 1 signal `sub_state pro_sub EXPIRED` (§C), user khác `[]`, không/sai bearer → 401 (`t180-r-be-rtdn.log`). #18 Docker: 0 — chỉ container `bq-e180` (và volume) được tạo/xoá, Docker.app đã thoát, không đụng `anlyjsc-*`; `precheck docker=0` ở mọi lượt gate (`t180-r-be-docker.log`).
- Review cuối: F1a (code) 0 Critical / 0 Important mở (I1 → D32, trần `DecisionLogger` 580); Minor còn lại nằm ở `minors-180` (carry 1.9.0).

**Chưa kiểm:** Samsung R5CX10P9EKZ (khoá; Redmi 65c9b673 đã đo đủ 4 chặng); mua/khôi phục với Play thật, grace/hold/renewal/refund thật, RTDN Pub/Sub thật và credential `subscriptionsv2` (H7b, OWNER-NOTES #4); NPE `ProxyBillingActivity` trên máy (chỉ unit); CI và release (#20–#21, tại tag).

## [1.7.0] - 2026-10-02

"1.7.0 — đo được hiệu quả của ads và của SDK" (plan docs/superpowers/plans/2026-09-30-sdk-1.7.0-plan.md). API public/protected không đổi; event server đổi sang schema v2.

### Thêm

- Chuỗi event quảng cáo v2 lên server của SDK và envelope v2 (1.7.0 Task 1): mọi body có `event_id` (UUID sinh lúc dựng, giữ nguyên khi gửi lại), `schema_version` = 2, `sdk_version`, `package_name`, `consent_tier`; chuỗi load `ad_request` → `ad_fill` | `ad_no_fill` cùng `request_id`, `latency_ms`, `mediation` (`admob`/`max`/`meta_direct`), `ad_source`, `error_code` (mã mạng, hoặc `timeout` khi watchdog slot 60 s bỏ request — trước đây không có event); `ad_show_attempt`, `ad_show_fail`, `ad_revenue` (`value_micros`) dựng qua `AdEventLog`. Hàm public `LogEventServer.logAd*` giữ chữ ký, thân uỷ quyền; dòng log `logevent: session_id=<uuid>` lúc init.
- Mọi lượt gọi show đo được (1.7.0 Task 2): mỗi lượt gọi hàm show của interstitial, rewarded, app open, native, native full (`getNativeFullForShow`), native collapse, rect và banner phát đúng một `ad_show_attempt` (`ready` = 1 nếu placement đã load lúc gọi), kể cả lượt bị cổng chặn; 8 đường trước đây trả `false` im lặng (interstitial `cf_not`/`ad_showing`/`not_config`; rewarded `ad_full_showing`/`not_allow_show`/`cf_not`/`ad_showing`/`not_config`) nay gửi `ad_show_fail` với lý do có sẵn; `ad_click` phát ở mọi callback click của `ads/core` (interstitial, rewarded, app open, native, native full, native collapse, rect, Full2). Giới hạn: banner không có callback click đi qua `ads/core` nên chưa có `ad_click` cho banner; Full2 (SDK tự nối sau quảng cáo cha) không phát `ad_show_attempt` — lọc placement `full_2_*` khi tính show rate; lượt gọi lại nội bộ (banner thu gọn gọi native collapse, native collapse hiện lại sau khi load, banner hiện lại sau thời gian ẩn) mỗi lượt cũng có một `ad_show_attempt`.
- Doanh thu v2 và khoá đối soát (1.7.0 Task 3): mỗi paid callback (AdMob `OnPaidEventListener`, MAX revenue listener, impression Meta direct) đi qua `PaidEvent` và sinh **một** `imp_id` dùng chung cho Firebase `ad_impression` (param `imp_id`), AppsFlyer (logcat `rev af: imp=<id> started=0|1`), server `ad_show` và `ad_revenue`. `ad_revenue` mang `value_micros` nguyên bản của mạng (Long, AdMob không qua double), `precision_type` của `AdValue` (0–3; MAX, Meta direct và `FirHelper.logAdRev*` gọi tay = -1). Server `ad_show` giờ chỉ phát từ paid event (không còn từ `AdsHelper.onAdImpression`; Firebase `aad_imp_*`/`show_ads_*` giữ nguyên). `FirHelper.logAdRevAdmob/Max/Fb` giữ chữ ký, uỷ quyền `PaidEvent`. Hàm public v1 `LogEventServer.logAdShow`/`logAdRevenue` (SDK không còn gọi) vẫn gửi `imp_id=""` khi app gọi thẳng: event đó không đối soát được với AppsFlyer/Firebase; muốn có `imp_id` thì để doanh thu đi qua paid event của SDK. `placement` của `ad_show`/`ad_revenue` là placement gọi show (như `ad_show_attempt`; `full_home`, `full_2_*` dù dùng chung slot `full_default`), lùi về slot khi paid event tới trước lượt show (T3b); `ad_click.placement` theo cùng quy tắc (trước đó là slot). Phía hiển thị (`ad_show_attempt`, `ad_show_fail`, `ad_show`, `ad_revenue`, `ad_click`) nối theo placement gọi show; `ad_request`/`ad_fill`/`ad_no_fill` vẫn mang slot mạng, nên fill rate tính theo slot tới khi có trường `slot` riêng (1.8.0).
- Phễu consent lên server (1.7.0 Task 4, `logevent/ConsentEvents.kt`): `consent_form_shown` (`gdpr_applies` 1/0/-1, `form_kind` `initial` khi UMP báo `REQUIRED` | `privacy_options` khi form tuỳ chọn bắt buộc) ngay trước khi form hiện; `consent_choice` (`tier`, `gdpr_applies`, `gpp_sections` nối `_`, `latency_ms` từ lúc hiện form, `form_kind`) khi form có câu trả lời, phát **sau** khi tầng consent cập nhật. Hai event đi qua cổng consent của telemetry như mọi event (không đường riêng) và được ghim trong bộ đệm trước consent (trần 50 không đẩy chúng ra). Mỗi lần hiện form nhiều nhất một `consent_choice`; form đóng khi UMP vẫn `REQUIRED` không tính. Với `form_kind=privacy_options`, đóng form mà không đổi gì vẫn là một `consent_choice` (UMP không phân biệt).
- `session_end` và dòng thống kê telemetry (1.7.0 Task 5): `SessionTracker` (internal) đếm mỗi lần foreground của process (`ProcessLifecycle` ON_START/ON_STOP, `onActivityStarted`, `AdEventLog.show`) và khi vào nền gửi `session_end` (`duration_ms`, `ads_shown_json` đếm theo định dạng, `screens`, `fg_index`) rồi reset bộ đếm, trước `AdsBrain.onSessionEnd`. Event đi qua cổng consent thường, sau DENIED không gửi gì. Mỗi lần in `Log.i("AppPlugin", "logevent stats session=… built=… accepted=… rejected=… dropped=… durable_stored=… durable_replayed=…")`. Không đổi API public. Giới hạn: `session_end` chỉ phát lúc ON_STOP, nên process chết khi còn ở foreground (crash, force-stop, vuốt bỏ trong 700 ms debounce) không có `session_end`; sau ON_STOP nó nằm trong tập bền và được replay khi cổng consent đã cho qua. Dòng `logevent stats` là ảnh chụp lúc ON_STOP, trước khi `session_end` đó được nhận. Có thêm `sampled=<n>`: số event bị bỏ vì lấy mẫu `logevent_control`; các event này cũng được tính trong `dropped` (`sampled` ⊆ `dropped`), nên `dropped - sampled` là phần mất thật.
- Điều khiển gửi event từ xa (1.7.0 Task 7): khoá Remote Config `logevent_control` (`{"version":1,"enabled":true,"default_sample":1.0,"sample":{"<event>":0.1}}`) tắt hẳn hoặc lấy mẫu event, tất định theo user (cả phễu của một user cùng giữ hoặc cùng bỏ) qua FNV-1a của `HoldoutManager`, độ mịn 1/10 000. Tiền, phễu bền (`ad_revenue`, `iap_purchase`, phiên, consent), `brain_decision`, `brain_feedback` và `iap_purchase` không bao giờ bị lấy mẫu. Event được giữ ở tỉ lệ < 1 mang `sample_rate` trong `eventData`; `enabled=false` là DROPPED như kill switch `logevent` (decision log giữ bản ghi). Cấu hình hỏng hoặc `version` ≠ 1 → giữ hết + `logE`. Lưu cho lần mở kế (`mem_logevent_control`). Chỉ bớt event, không làm event nào lọt qua cổng consent.
- Demo `utility` gửi telemetry tới sink cục bộ `127.0.0.1:8089` (release và debug): `app/src/utility/AndroidManifest.xml` khai `DATABUCKETS_BASE_URL`/`DATABUCKETS_API_KEY=demo-utility-key` và `networkSecurityConfig` chỉ cho cleartext tới `127.0.0.1`; `app/src/debug` (10.0.2.2) bị xoá — flavor `game` không còn DataBucket ở bản debug. Mới: `tools/local_sink.sh start|stop|status` (backend `python -m uvicorn` native, không Docker, `BRAIN_API_KEY=demo-utility-key`), `tools/emu_boot.sh` + `tools/lib/quiet_host.sh` (host yên trước mỗi lần boot emulator, exit 3 = `invalid (contention)`; sampler bộ nhớ vào `<LOG>.host`). `tools/device_gate.sh` bắt buộc `--apk`/`--mapping` (thiếu là FAIL trước mọi lệnh adb, không đọc `app/build/outputs`), FAIL khi Docker chạy hoặc không có sink, dựng `adb reverse tcp:8089 tcp:8089` (dỡ lúc thoát), dòng 1 của log in `apk=… sha256=… bytes=…; mapping sha256=…`; `release_gate.sh` và `bundle_size.sh` in sha256 của thứ vừa dựng. `Constants.DataBucket.BASE_URL` ghi chú không dùng bằng comment (không gắn `@Deprecated`: annotation thêm `getBASE_URL$annotations` vào dump, façade đỏ).
- Cổng đo G11 + đối soát H6a + phiên đo hằng ngày: `tools/device_gate.sh --measure` nạp `tools/lib/measure_gate.sh` và chạy kịch bản đo THAY cold start/monkey (cùng tiền kiểm Docker/sink/`--apk`/`--mapping`): pha A (`pm clear`, FA verbose, cold start không `force_eea`, Show Interstitial, Show Rewarded, HOME; mỗi lần chạm kiểm trong logcat ≤ 3 lần, chờ `onAdLoaded` mới trước lần chạm hiện, xác nhận `AdActivity`, về app bằng `am start --activity-clear-top --activity-single-top` (P10: cả ba nhánh đạt, nhánh đầu được chọn), focus kiểm sau mỗi lượt; hợp lệ = ≥ 1 FA `origin=app,name=ad_impression` và ≥ 1 `ad_revenue` của session, không thì `invalid`, chạy lại ≤ 2), pha B (sink không tới được, Show Interstitial, kill), pha C (mở lại, replay). `tools/measure_check.py` (fail-closed; khử trùng bằng đúng `dedup_key` của backend) chấm G11 (i)–(v) + (vi) ghi ((i) lấy `accepted` từ dòng stats pha B; (ii) event bền phải tới sink từ pha C, pha B kiểm `adb reverse --list` sau khi gỡ; (iv) ts replay trong `[t_b − 5 s, t_c)`; cột `dedup_key` của sink phải trùng khoá tính lại), `--local-days` (`tools/measure_days.py`) đếm ngày UTC+7 liên tiếp, một phiên = ngày UTC+7 của `client_ts_ms` đầu tiên; `tools/reconcile_session.sh <applog> <jsonl> <session_id>` đếm FA / `rev af` / JSONL `ad_revenue` theo `imp_id`; `tools/daily_session.sh [--serial <s>] [--apk <apk>]` chạy pha A vào `DOC/testing/daily/<ngày UTC+7>/<serial>-<ts>/`.
- Sửa công cụ đo (TF4): `mg_find_text` trong `tools/lib/measure_gate.sh` cuộn bằng kéo CHẬM 600 ms (70 %→40 % chiều cao, đọc từ `wm size`, ưu tiên `Override size`, không đọc được → 1080x2400 có dòng báo) thay cho swipe nhanh gây fling bỏ qua "Show Interstitial" trên Redmi 1080x2400 (pha A `invalid (ui)` 3/3); tìm tối đa 6 bước xuống rồi 12 bước lên. Test: +5 ca trong `tools/tests/device_gate_allowlist_test.sh` (141 → 146).
- Sửa công cụ đo (TF5): `mg_return_to_app` (`tools/lib/measure_gate.sh`) không còn `am start -W` khi về app sau quảng cáo (activity đã có nhận intent qua onNewIntent nên API 26 không bao giờ báo "Displayed" và `-W` treo cổng > 17 phút); kết luận bằng thăm focus MainActivity mỗi 1 s tối đa 10 s (`mg_back_in_app`), `mg_start` (cold start `-W`) có watchdog `MG_AMSTART_MAX` (mặc định 60 s) kill đúng tiến trình adb con rồi `FAIL: am start -W quá …s`. Test: +5 ca (146 → 151).

### Sửa

- Ngày/giờ và quốc gia của event không còn phụ thuộc locale máy (`Locale.ROOT`): máy tiếng Thổ Nhĩ Kỳ với SIM Ấn Độ gửi `İN` thay vì `IN` (rơi tier OTHER).
- Event tiền và phễu sống qua kill (1.7.0 Task 6, `DurableEvents`): `ad_revenue`, `iap_purchase`, `first_open`, `session_start`, `session_end`, `consent_form_shown`, `consent_choice` được ghi đĩa (`files/appplugin_events`, trần 2 000, bỏ cũ nhất) chỉ khi cổng consent cho gửi, trên luồng đĩa riêng, không bao giờ dưới khoá của cổng; lần mở sau replay đúng body đã lưu (`ts`, `sequenceId`, `event_id` giữ nguyên) khi tầng qua cổng, mỗi lượt không quá chỗ trống của hàng đợi; xoá khi server nhận hoặc trả 400/401/413; 429 (theo `Retry-After`), 5xx, 4xx khác và lỗi mạng giữ bản đĩa; `DENIED` xoá sạch file. Event đang giữ trước consent hay sau `DENIED` không chạm đĩa (Q2). Dòng log `logevent durable stored <name> <event_id>` / `logevent durable replayed <n>`.
- Replay `brain_decision` mang `ts` lúc ghi (F2): dòng decision log thêm trường 14 `ts_ms`, event sống và replay cùng `ts`; dòng 13 trường của bản trước replay như cũ (`ts` lúc dựng).
- Bộ gửi event tôn trọng `Retry-After` (giây, trần 600 s) của phản hồi thử lại được (429, 5xx): không gửi lại sớm hơn server yêu cầu.
- Event đã nằm trong hàng đợi gửi được kiểm lại **lúc gửi** (mỗi lần gửi và mỗi lượt gửi lại sau backoff), không chỉ lúc vào hàng (1.7.0 P-1): kill switch `logevent`, `logevent_control.enabled=false` hay consent chuyển `DENIED` giữa phiên → 0 POST thêm, event bị bỏ và đếm; bản bền (`DurableEvents`) nằm lại trên đĩa và replay ngay khi gỡ kill/bật lại (DENIED thì xoá, N1). Trước đây backlog (tới 500 event, mỗi cái tới 6 lần gửi) vẫn gửi tiếp trong phiên bật kill, và event xếp hàng trước DENIED vẫn đi sau DENIED.
- Kiểm lúc gửi (P-1), hai điều đã chốt khi review: yêu cầu đã giao OkHttp lúc tầng chuyển DENIED (tối đa 4 đang bay) không thu hồi vì được quyết định gửi lúc còn GRANTED; và app không cài cổng consent (không dùng `AppPluginBase`) mà `ConsentManager` tới DENIED thì event phát lúc đó bị bỏ thay vì gửi như trước (trước P-1 đây là lỗ hổng N1 có từ trước, không ai chấp thuận).

### Backend

- Backend production-ready (T9): khoá theo app `BRAIN_API_KEYS="app_id:key,…"` (chỉ `BRAIN_API_KEY` = app `default`; có cả hai thì `BRAIN_API_KEYS` thắng), so khớp `hmac.compare_digest` qua mọi khoá, cột `app_id` do server gán từ khoá; 429 + `Retry-After` (token bucket theo app 200 rps/400 và theo user 20 rps/60, env `BRAIN_RATE_*`); 413 khi body > 256 KB hoặc > 500 event; lỗi `{"ok":false,"error":{"code","message"},"request_id"}` + header `X-Request-ID`; log JSON một dòng mỗi request, không in khoá; `/health` = sẵn sàng thật (JSONL ghi được / BigQuery `get_dataset`, 503 khi không) kèm `version` (`BRAIN_GIT_SHA`); `dedup_key` trên mọi hàng (khoá tự nhiên → `ev:<event_id>` → `seq:<user>:<seq>:<ts>`); 27 cột v2 NULLABLE (add-only), `raw_event_data` là chuỗi JSON; sink BigQuery ghi `events_staging`, `BRAIN_BQ_ENDPOINT` (emulator, credential ẩn danh) chỉ với project `local-test`; `create_app(settings)`; requirements ghim, Dockerfile `"google-cloud-bigquery==3.45.2"`.
- Chỉ số DOC/62 §2.1 bằng SQL, một định nghĩa (T10): `backend/app/bq_tables.py` đặc tả 5 bảng tạo qua Python client (`events_staging` phân vùng `client_ts_date` hết hạn 2 ngày, `events_raw` 400 ngày + cluster `event_name,user_id`, `country_tier` seed `sql/seed/country_tier.csv` T1/T2/VN — còn lại kể cả `DEFAULT` = OTHER, `fx_daily`, `admob_report` của T12); `app/bq_setup.py` thay `setup_bq.sh` (tạo-nếu-chưa-có, kiểm hạn giữ từ metadata, không bao giờ xoá; từ chối production, project ngoài danh sách cho phép và BigQuery thật thiếu `GCP_GUARD_OWNER_GO`; `tools/gcp_guard.sh` bỏ `bq mk` khỏi danh sách); `sql/merge_events.sql` = TEMP TABLE + `MERGE … INSERT ROW`, khử trùng theo `(IFNULL(app_id,''), dedup_key)` (R-9), chạy lại thêm 0 hàng; 7 view (`v_ecpm_format_tier`, `v_imp_per_dau_format`, `v_arpdau_ads_country`, `v_fill_rate` có `orphans`, `v_show_rate`, `v_consent_funnel_nondenied` không có cột tỉ lệ, `v_retention_d1_d7_holdout`) trên view cơ sở `v_events_v2` (chỉ `schema_version = 2`, ngày = ngày UTC+7 của `client_ts_ms`, nhân `1/sample_rate`, tiền qua `fx_daily`, không tỉ giá = `unconverted`), oracle Python `app/ad_metrics.py` + test parity (integration); `docker-compose.yml` (API 8089 + bigquery-emulator ghim digest, chỉ `127.0.0.1`), `tools/stack_load.py` (JSONL → staging → MERGE, chỉ emulator), `tools/report_daily.sh --date D` (7 bảng → `DOC/reports/D.md`), `backend/tools/loadtest.py` (p50/p95/p99); `backend/pytest.ini` (`pythonpath`, marker `integration`) để `pytest` của job CI `backend` import được `app`. Giới hạn: hàng v1 có `user_id` rỗng mang khoá `seq::<seq>:<ts>` và có thể gộp giữa hai máy (v2 không bị); MERGE trên BigQuery thật chưa có lịch (scheduled query của chủ dự án).
- Sửa backend (F-BE, R-10 m2 + R-2 m4): mọi định danh ghép vào SQL (`--dataset`, `BRAIN_BQ_DATASET`, project, tên bảng/view) phải là định danh BigQuery hợp lệ (`^[A-Za-z_][A-Za-z0-9_]{0,1023}$`; project theo luật project id GCP), kiểm trước khi dựng client hay gửi SQL ở `app/bq_setup.py`, `app/daily_report.py`, `tools/stack_load.py`, `tools/report_daily.sh` — sai thì từ chối (rc 3 / 2), không làm sạch; `v_show_rate` và oracle `ad_metrics.show_rate` loại placement `full_2_*` (Full2 không phát `ad_show_attempt`), Full2 vẫn tính ở imp/DAU, eCPM, ARPDAU và fill.
- Sửa backend (F2-be, final review I1 + m1): `events_staging`/`events_raw` phân vùng theo cột mới `received_date` (DATE REQUIRED, ngày UTC của `received_ts_ms`, đặt ở `ingest_row`; `staging_rows` bù cho hàng JSONL cũ) thay cho `client_ts_date`, cùng hạn giữ 2/400 ngày — BigQuery xoá ngay hàng chèn vào phân vùng đã hết hạn, nên replay tới muộn ≥ 3 ngày, máy chậm giờ hay `ts = 0` từng được trả 200 rồi mất; ngày báo cáo vẫn là ngày UTC+7 của `client_ts_ms`, `client_ts_date` là cột thường; `bq_setup` báo lỗi bảng còn phân vùng `client_ts_date`; `ad_revenue` giữ eventData gốc trong `raw_event_data` (không mất `screen_name`). Không cần migration: chưa có bảng production (H5).
- Sửa backend (F2-be2, E-BE #16): lời gọi BigQuery trên đường request có tổng thời gian bị chặn thay cho `DEFAULT_RETRY` (~600 s) của thư viện — `/health` gọi `get_dataset` một lần (`retry=None`, 5 s: emulator tắt từng làm 503 tới sau ~575 s), `/push` (`insert_rows_json`) dùng retry hạn 10 s + timeout 10 s mỗi lần gọi (`app/bq_bounds.py`), nên BigQuery sập thì `/push` trả 5xx trong ≤ ~20 s, trước readTimeout 30 s của SDK; reader `/metrics/*` có `job_retry=None`, `query` ≤ ~20 s, chờ job ≈ ≤ 70 s (`result` timeout 30 s mỗi lần gọi + retry 10 s, ngân sách chờ 30 s), đọc trang hàng thêm theo cỡ kết quả (đường hiếm); công cụ batch (`bq_setup.py`, `daily_report.py`, `tools/stack_load.py`) giữ mặc định.
- **`tools/gcp_guard.sh`, runbook, CI backend** (1.7.0 Task 11): mọi lệnh `gcloud`/`bq` của repo đi qua guard, khớp dạng cố định
  `<tool> --project=<id>` + tiền tố lệnh trong danh sách + cờ `--tên=giá-trị` (không `--`); project chỉ `test-d-56f09`
  (`tools/gcp_allowed_projects.txt`), production và mọi tham chiếu sang project khác bị từ chối; `deploy`/`update-traffic`/`secrets versions`
  cần `GCP_GUARD_OWNER_GO`. `bq query` và `bq mk` không nằm trong danh sách; dựng bảng BigQuery bằng `backend/app/bq_setup.py`
  qua Python client (danh sách project cho phép + `GCP_GUARD_OWNER_GO` cho BigQuery thật; `setup_bq.sh` đã xoá ở Task 10).
  `deploy_cloudrun.sh` gọi qua guard (`--max-instances=3`, `--service-account=`, khoá API từ Secret Manager). `backend/RUNBOOK.md`
  mới, §0 = điều kiện IAM (thông tin đăng nhập dùng qua guard không có vai trò nào trên production; IAM mới là ranh giới). CI: job
  `backend` (`pytest -m "not integration"`) và bước `tools/tests/gcp_guard_test.sh` trong `verify`.
- **Nhập báo cáo AdMob mediation**: `backend/app/admob_import.py` đọc luồng JSON `mediationReport:generate` (dimension
  `APP, FORMAT, COUNTRY, AD_SOURCE, DATE`; metric tiền tính bằng micros); CSV chỉ qua `HEADER_MAP`, cột lạ là lỗi.
  `match_summary` cho match rate và eCPM quan sát theo `format x tier`, và lệch so với `ad_revenue` của ta theo ngày x tier
  (kèm lưu ý AdMob theo IP, ta theo SIM). Đặc tả bảng `admob_report` để Task 10 nạp; chưa ghi vào BigQuery.

### Tài liệu

- Tài liệu 1.7.0 (Task 14): DOC/46 §B.0 (một đường ống `/push`, envelope v2, bảng tên event; bỏ ý "SDK trỏ pipeline log cũ" ở mục "Không xác minh được"), DOC/62 §2.1 (cột nguồn của từng chỉ số: view SQL / báo cáo AdMob / AdMob Privacy & messaging, bảng event v2), `backend/README.md` (`BRAIN_API_KEYS`, `/health`, số test thật, stack cục bộ, G13 trên sink JSONL), DOC/67 (H6 tách H6a/H6b, tên cổng tần suất interstitial thật, backend theo Q4).
- Tài liệu 1.7.0 (E5): DOC/65 §3 (cột G6, dòng G11 và G13), §4 (cột "1.7.0" cho 11 hàng: đo được trên demo hay cần app thật), §5 (khối 1.7.0 và carry sang 1.8.0); DOC/67 §4 (1.7.0 xong, H5 và H6b "program gate mở"); DOC/46 và DOC/62 bỏ các chỗ trống `after T…` và câu "còn phát dạng v1"; `backend/README.md` số test (267 unit + 1 skip, 13 integration) và hình lỗi có `detail`.

### Phải làm khi nâng cấp

- Event server đổi sang schema v2 (tên + param, bảng DOC/62 §2): `ad_start_load`/`ad_load`/`ad_start_action`/`ad_show_fail_reason` không còn phát (thay bằng `ad_request`/`ad_fill`/`ad_no_fill`/`ad_show_attempt`/`ad_show_fail`); ai đọc bảng `events_raw` theo tên v1 phải đổi truy vấn.
- App thật không đổi: DataBucket vẫn tắt tới khi app khai meta-data `DATABUCKETS_BASE_URL`.

### Giới hạn đã biết

- Không event consent nào rời máy sau `DENIED` (Q2): `consent_form_shown` đang giữ (chưa trả lời) bị bỏ, `consent_choice` chỉ nằm trong vòng RAM (gửi nếu người đó đồng ý lại trong cùng process). `consent_form_shown` phát khi người dùng còn `GRANTED` (form tuỳ chọn, UMP hỏi lại) đã gửi trước khi họ rút lại: trong view phễu, `form_shown` không có `choice` có thể là rút lại, không chỉ là bỏ dở. Tỉ lệ đồng ý lấy từ báo cáo AdMob Privacy & messaging (chủ dự án xuất); backend không có cột "consent rate".
- Event chuỗi quảng cáo (`ad_request` … `ad_click`) không trong tập bền: process bị kill khi chưa gửi thì mất; số mất in ở dòng `logevent stats`. Bản bền bị server từ chối bằng 4xx khác 400/401/413 nằm lại trên đĩa và replay ở các lần mở sau tới khi trần 2 000 đẩy ra.

- Điều khiển gửi event từ xa (`logevent_control`): giá trị lưu ở `mem_logevent_control` chỉ giữ tới lần fetch Remote Config kế tiếp. Khoá RC vắng hoặc rỗng nghĩa là gỡ ghi đè (`applyRemote("")` = xoá, giống kill switch `logevent` 1.4.0: RC là nguồn chính, prefs chỉ là bộ nhớ đệm tới lần fetch), nên app không đặt khoá này trên RC sẽ tự bật lại telemetry vài giây sau cold start dù prefs đã ghi `enabled=false`. Muốn tắt bền phải đặt khoá trên RC. Dòng log thật khi tắt là `logevent_control: enabled=false — không gửi event (từ <event>)`.
- Yêu cầu gửi đã giao OkHttp lúc tầng chuyển `DENIED` (tối đa 4 đang bay, `maxInFlight`) không thu hồi được (P-1): chúng được quyết định gửi lúc còn `GRANTED`; app không cài cổng consent (không dùng `AppPluginBase`) nhưng `ConsentManager` tới `DENIED` thì event phát lúc đó bị bỏ thay vì gửi như trước (app chưa từng chạy `ConsentManager` ở `UNKNOWN` không đổi).
- `ad_request`/`ad_fill`/`ad_no_fill` mang slot mạng, các event hiển thị mang placement gọi show: fill rate tính theo slot cho tới khi có trường `slot` riêng (1.8.0). Impression muộn tới sau lượt show hoặc get kế tiếp trên cùng slot nhận placement của lượt mới (callback đọc placement lúc bắn).
- Cổng đo trên máy dùng một sink cố định `127.0.0.1:8089` nên các máy đo chạy tuần tự, không song song (1.8.0: `local_sink.sh --port`). `/health` của backend trên BigQuery thật chưa đo (chỉ emulator); hàng `ad_revenue` tới muộn > 2 ngày sống sót ở BigQuery thật chưa kiểm (emulator không có hạn giữ) — cả hai chờ H5.
- Chuẩn bị 1.8.0 ghi ở DOC/65 §5 "Carry sang 1.8.0" (gồm trường `slot`, mapping `precision_type` MAX −1 → 0–3, `gcp_guard` với `~/.bigqueryrc`, `device_gate.sh` FAIL khi JSONL +0 và in dòng tiền kiểm `docker=0 sink=up`).

**API public/protected không đổi** (`tools/api_dump.sh --check` → `API unchanged (4713 lines)`, `t170-r-apidump.log`; so `v1.6.4` 0 dòng `-` ngoài `access$` và 203 dòng `+`: khối mới `PaidEvent` 8, `AdEventLog` 31 (+`LoadRequest` 21), `ConsentEvents` 8 (+`Pending` 4), `DurableEvents` 16 (+`Tracked` 10), `EventEnvelope` 5, `LogEventControl` 17 (+`Config` 15), `SessionTracker` 9 (+`Snapshot` 6); trong khối cũ: 9 hằng `EventNames`, `LogEventTransport.sendEventAt$appplugin` (+`$default`), `AppsflyerHelper.logAdRevenue$appplugin`, 3 `access$`. Các dòng `+` ngoài danh sách tên của plan đều nằm trong khai báo `internal`/`private` của Kotlin hoặc là lớp tổng hợp (`AdsTelemetry$OpenLoad`, `LogEventTransport$WhenMappings`, `LogEventSender$RejectionCodeSink`, các getter `$appplugin`); chỉ `LogEventSender$Companion.retryAfterMs` là thành viên không mangle, của companion một lớp `internal`; quyết định của controller 2026-09-30, `metrics-170` #2. Façade so `v1.5.0` PASS, `core internal check` PASS, 23 dòng `new:` đều có "(cho phép", `t170-r-facade.log`).

**Đã kiểm (số đo Task R #1–#20 của `metrics-170` trên HEAD `336525a` cho mã; APK utility sha256 `7ad240d2af249a359d58e7b59b2ccd20435fae15aa8766fd7d68d7e08fe8cc67`, dựng ở `222a34f`; số byte và sha lấy từ dòng `apk sha256=… bytes=…` của log gate, byte chỉ là thông tin):**

- RC-1 (3 ngày UTC+7 liên tiếp, mỗi ngày ghi sha APK và task đã vào base): ngày 1 = **2026-09-30**, phiên hợp lệ `DOC/testing/daily/2026-09-30/emulator-5570-20260930T1120+07` (emulator API 37; APK `app-utility-release` sha256 `48eaa270b45bf28c547e9617d124681cb7af2b69a5073248e39a2cca70e8d3ad`, base `2d499da` với T1 + T8; ad_show ≥ 1, `ad_revenue` = 2, FATAL 0, ANR 0; lượt 10:50 vô hiệu vì lần chạm thứ hai không tới nơi, thư mục giữ dấu `INVALID.txt`). Ngày 2 = **2026-10-01**, phiên hợp lệ `DOC/testing/daily/2026-10-01/emulator-5570-20261001T0037+07` (APK `7ad240d2…cc67`, base `9fbadcf` với mọi task; ad_show 2, `ad_revenue` 2, FA `ad_impression` 2, 45 hàng v2, FATAL 0, ANR 0; `t170-r-daily-d2-session.log`). Ngày 3 = **2026-10-02**, phiên hợp lệ `DOC/testing/daily/2026-10-02/emulator-5570-20261002T0057+07` (cùng APK, cùng base `9fbadcf`; ad_show 1, `ad_revenue` 1, FA `ad_impression` 1, 47 hàng v2, FATAL 0, ANR 0; `t170-r-daily-d3-session.log`).
- #1 unit ×3: 1653 test / 0 skipped / 0 fail / 0 error mỗi lượt, `@Ignore` 0 (`t170-r-full-{1,2,3}.log`, dòng `SUM t s f e: 1653 0 0 0`); kỳ vọng suy ra 1584 + 73 − 4 = 1653 (`git log -p v1.6.4..HEAD -- src/test | command grep -c '^+.*@Test'` = 73, `'^-.*@Test'` = 4); ước lượng 1632 của plan chỉ để tham chiếu. Sau đó không commit nào đổi `src/test`.
- #2 dump 4713 dòng, #3 façade + core PASS, D7 24/24 commit của `v1.6.4..222a34f` (`t170-r-{apidump,facade,d7}.log`); #4 cỡ file: 0 file > 800, `LogEventServer.kt` 721 (trần 731), `AdsHelper.kt` 696, `TelemetryConsentGate.kt` 266, `LogEventTransport.kt` 267 (trần 270), `DecisionLogger.kt` 552 (trần 555), 7 file mới đều ≤ 232 dòng (`t170-r-sizes.log`); #5 shell: allowlist **151/151** (= 119 + 32 ca thêm từ `v1.6.4`, gồm TF4 +5 và TF5 +5) và `gcp_guard_test.sh` **111/111** (`t170-r-shell-final.log` trên `336525a`; lượt E1 trên `222a34f` là 141/111, `t170-r-shell.log`).
- #6 release gate `utility` PASS (9 ad unit, 317/321 = 98% lớp SDK đổi tên, zipalign 16 KB, `ClassValue` 0; `apk sha256=7ad240d2…cc67 bytes=8994908`, `mapping sha256=8033f0b2…ff70`) và `game` PASS (`apk sha256=fc15ab4d…a1b2 bytes=27480959`, 304/308 = 98%); `127.0.0.1:8089` trong manifest: `utility` 1, `game` 0 (`t170-r-release-gate-{utility,game}.log`, `t170-r-manifest-8089.log`). #7 Size: `bundletool get-size total` `utility` min 7 592 458, **max 7 652 009** B ≤ 26 214 400 (1.6.4: 7 641 195) (`t170-r-bundle-size.log`); `com.facebook` 1 artifact (`audience-network-sdk:6.21.0`), 723 dòng classpath (`t170-r-deps.log`); bản debug `utility` dựng được (`t170-r-debug-build.log`).
- #8 cổng thiết bị `utility` (`EVENTS=1500`, sink native, telemetry bật, `--apk`/`--mapping` đã đóng băng, dòng 1 sha = sha E2, FATAL/native/`am_anr` 0 ×6, chuỗi R8 0 ×3, StrictMode `by sdk` 0 (emulator: frame `com.app.plugin` 0) ×2, JSONL tăng): API 37 PASS (JSONL 387 → 534; cold1 TotalTime 1783 ms, main-thread 16 ms, `initPlugin total` 15 ms; cold2 522/34/34), API 28 PASS (898 → 1000; 1198/165/164; 1329/52/40), API 26 PASS (1149 → 1277; 890/101/88; 785/127/126), Redmi 65c9b673 API 34 PASS (149 → 315; 565/21/21; 399/20/19), `prefs warmup waited=0 ms, timeouts=0` ở cả 8 lần cold start (`t170-r-emu{37,28,26}-gate.log`, `t170-r-65c9b673-gate.log`; file `.host` cùng tên có ≥ 1 mẫu mỗi 10 s, không commit). Các số này là mốc TotalTime / main-thread mới khi telemetry bật (ghi nhận, chưa phải ngưỡng; emulator chịu tải host lúc đo).
- #10 G11 (cổng đo `--measure`, pha A hợp lệ ở lượt đầu, 0 lượt chạy lại; pha B sink không tới được, pha C replay): API 37, API 28, API 26 và Redmi đều PASS: (i) accepted = distinct = 45, (ii) 2 event bền, mất 0, (iii) trùng 0, (iv) 2 dòng replay giữ phiên pha B và ts trong cửa sổ, (v) 6 `request_id`, không cái nào có hơn một kết thúc, (vi) ghi: dựng 9, bền 2, mất best-effort 7 (`t170-r-{emu37,emu28,emu26,65c9b673}-g11.log`). Lượt Redmi đầu (trước TF4) `invalid (ui)` 3/3 do `mg_find_text` vuốt nhanh nhảy qua nút "Show Interstitial" trên màn 1080x2400, lượt API 26 đầu (trước TF5) treo `am start -W` > 17 phút: cả hai là lỗi công cụ, đã sửa (TF4, TF5, mục Thêm), lượt cũ giữ nguyên (`t170-r-65c9b673-g11-pre-tf4.log`, `t170-r-emu26-g11-pre-tf5.log`, không tính vào ngân sách chạy lại), rồi chạy lại 4/4 PASS lượt đầu trên công cụ mới.
- #12 H6a (3 phiên hợp lệ mỗi máy = pha A của G11 + 2 `daily_session.sh`): **12/12 phiên**, mỗi phiên 3 số bằng nhau (Firebase `ad_impression` theo `imp_id` = AppsFlyer `rev af … started` = JSONL `ad_revenue`; 3/3/3 ở pha A, 2/2/2 ở hai phiên hằng ngày) và hiệu tập rỗng, không có `imp_id` ngoài tập; `origin=am` ghi 3/2/2 (`t170-r-{emu37,emu28,emu26,65c9b673}-h6a.log`).
- #13 consent (API 37, khởi động ấm, `TotalTime` 578 ms, form UMP hiện): sau DENIED 60 s JSONL không tăng, 0 POST, 0 `consent_*`; chỉ khi GRANTED mới xả 9 event giữ (`flushed 9 held after DENIED`), `consent_form_shown` = 1, `consent_choice tier=GRANTED` = 1, POST 10 (`t170-r-emu37-consent.log`). #15 `logevent_control` (API 37, APK debug): tắt từ cold start tới lần fetch RC (15:47:48,8 → 15:47:51,285) 0 POST, dòng `logevent_control: enabled=false — không gửi event (từ session_start)` = 1, `session_start` phát lúc tắt bị bỏ; fetch RC không có khoá gỡ ghi đè và 3 POST sau đó là event phát sau lần fetch; bật lại (`enabled:true`, cold start) POST 10. Ngưỡng dùng là "POST = 0 cho tới lần fetch RC" (giải thích ở "Giới hạn đã biết"); C1–C6 ở unit ghim phần còn lại (`t170-r-emu37-control.log`).
- #11, #14, #16, #17, #19 (backend, Docker dưới khoá, `pgrep -f Docker.app | wc -l` = 0 sau khi xong) đo lần 1 trên `444372e`; #11, #16, #17 đo lại trên `9fbadcf` (mã cuối `336525a`, sau F2-be2): #11 MERGE thật: staging +59, raw 59 = distinct 59, lần 2 +0, bằng (iii) của `measure_check` (59) (`t170-r-be-merge.log`); #14 `ad_show_fail.reason` rỗng 0 trong 392 dòng, `ad_show_attempt` ở 7 định dạng, orphans 0 (`t170-r-be-views.log`); #16 `pytest` đủ **280 passed + 1 skipped** (= 268 không-integration + 13 integration, dẫn xuất; lần 1 trên `444372e`: 275 + 1), mã 200/401/401/413/429, `/health` 503 khi BigQuery emulator dừng sau **0,083 s** (lần 1 trên `444372e`: 575 s, F2-be2 sửa), `gcp_guard_test.sh` 111/111, D7 0; **G13**: `loadtest.py --rps 50 --seconds 60` trên sink JSONL native: 3000 request, lỗi 0, **p95 8,0 ms** < 200 ms (lần 1 trên `444372e`: 6,7 ms); lớp emulator BigQuery chỉ là thông tin (lượt 1 lỗi 2558, container mới p95 6,9 s; emulator tuần tự hoá insert) (`t170-r-be-g13.log`); #17 parity SQL = `ad_metrics.py`: 8 passed, Δ = 0 trên `9fbadcf` (`t170-r-be-parity.log`); #19 mọi bước `RUNBOOK` trên stack cục bộ OK, `deploy_cloudrun.sh` không có `GCP_GUARD_OWNER_GO` bị `REFUSED`, có token chỉ in lệnh qua shim `PATH` (không exec `gcloud` thật; `GCP_GUARD_DRY` không tồn tại trong guard, xem "Chưa kiểm") (`t170-r-runbook.log`); #18 báo cáo ngày 1 (2026-09-30): 7 bảng đủ, fill 8 / show 7 / imp-DAU 5 hàng > 0, eCPM và ARPDAU giá trị 0 (test ads), D1/D7 0 (cohort cùng ngày) (`t170-r-be-reports-day1.log`); ngày 2 và 3 + toàn bộ #18 (`measure_check.py --local-days`: 2026-09-30, 2026-10-01, 2026-10-02 liên tiếp = 3, lượt 1050 INVALID bị bỏ; `report_daily.sh` ×3 OK, mỗi ngày 7 bảng, fill/show/imp-DAU > 0, eCPM và ARPDAU giá trị 0 (test ads), consent 0 hàng cả 3 ngày vì không có event `consent_*` trong các phiên (consent đã trả lời trước phiên), D1/D7 cohort cùng ngày; ngày 1 nạp đúng phiên RC-1 1120, rows_total 40) (`t170-r-be-reports.log`, `DOC/reports/2026-09-30.md`, `2026-10-01.md`, `2026-10-02.md`). Sau E-BE: F2-be (`received_date`) và F2-be2 (chặn thời gian BigQuery) đổi `backend/app`; kiểm sau F2-be2: unit **267 passed + 1 skipped**, integration **13 passed** (gồm `/health` 503 trong < 15 s khi BigQuery dừng; F2-be2 tự dừng và bật lại `bq`); 4 đột biến F2-be2 đỏ (log giữ trong cây làm việc, không commit: `t170-f2be2-pytest.log`, `t170-f2be2-integration.log`).
- #20 Docker không chạy cùng thiết bị: đạt theo cấu trúc — tiền kiểm `pgrep -f Docker.app` = 0 của `device_gate.sh` (dòng 45–46) FAIL trước mọi lệnh adb và cả 8 log gate (4 thường, 4 `--measure`) đều tới bước cài; khoá `lanelock` loại `docker` với `emugate`/`real`; cửa sổ E-BE 17:11–17:35 nằm giữa hai cửa sổ thiết bị; cuối E-BE `pgrep -f Docker.app | wc -l` = 0. Log gate chưa in dòng `precheck docker=0` (1.8.0).
- Thiết bị: các dòng AppPlugin `E` trên API 28/26 chỉ gồm `IapHelper setup failed: 3 Billing service unavailable` (emulator không có Play Billing) và `appsflyer launch request failed: code=50 … 404`; dòng thô R8 khớp allowlist (`AppsFlyerLVL$resultListener`, `GetAppsReferrerStateListener`, `reportRequestStats2`).
- Review: F1a (Opus, mã đến `15dd58c`): 0 Critical, 1 Important (I1: phân vùng theo `client_ts_date` có hạn giữ làm mất replay tới muộn — đã sửa ở F2-be, review R-F2be), Minor đã xử lý hoặc chuyển 1.8.0 (DOC/65 §5 "Carry sang 1.8.0"). F1b (Fable, số) và F3 (luật APK/backend) chạy sau phần 2.

**Chưa kiểm:** Samsung R5CX10P9EKZ: chưa đo (máy không cắm) — không có cổng #9, G11, H6a trên máy này ở 1.7.0; API 30/33; Crash/ANR user-perceived, match rate, eCPM, cảnh báo chính sách, D1/D7 vs holdout: cần app thật (DOC/65 §4); tỉ lệ đồng ý consent (AdMob Privacy & messaging, chủ dự án xuất); H5 (pipeline chạy thật trên Cloud Run + BigQuery, chờ billing) và H6b (đối soát giá trị, cần ad unit thật ở internal testing, Q3) = program gate mở; BigQuery thật (không phải emulator): `/health` 503 có giới hạn, replay > 2 ngày còn trong bảng, chi phí truy vấn view theo phân vùng (R-10 m1); `GCP_GUARD_DRY` (plan #19 nêu, không có trong guard — dùng shim `PATH`, tương đương, chưa thêm chế độ dry vào công cụ bảo mật); MAX trên máy (demo không có `admax_devKey`); mua/khôi phục với tài khoản Play thật; 8 định dạng quảng cáo sau consent; CI và release (#21–#22: nhánh chưa push, chưa PR, chưa tag).


## [1.6.4] - 2026-09-30

"1.6.4 — trả nợ 1.6.3, cấu hình app tiện ích" (plan `docs/superpowers/plans/2026-09-30-sdk-1.6.4-plan.md`). API public/protected không đổi.

### Sửa

- `IapHelper.init`: dựng `BillingClient` và `startConnection` trên một luồng nền riêng ("appplugin-billing"), không còn trên luồng gọi (main lúc `Application.onCreate`); binder của Play Store/`PackageManager` không còn chặn main lúc cold start (gốc #11 Samsung 1.6.3, `FreecessController`). `init` trả `true` khi đã lên lịch; lỗi dựng `BillingClient` nay chỉ còn ở log (`IapHelper init failed`) (`t164-t1-{red,green}.log`).
- `IapHelper.restorePurchases`: store trả lỗi ở một trong hai lượt hỏi (mua một lần, thuê bao) thì giữ nguyên quyền lợi đang có thay vì coi là "không sở hữu gì" (người đã mua bỏ quảng cáo từng thấy quảng cáo lại tới lần đồng bộ sau; nhận từ `a059e61`, dạng `private`).
- `IapHelper.isReady()` (và cổng của `purchase`/`restorePurchases`) nay đúng nghĩa: `true` khi đã `startConnection` và billing không đang kết nối; `purchase()` gọi trong lúc kết nối đầu hoặc đang kết nối lại nay trả `false` ngay với `IapResult.Failed(-1, …)` thay vì đi vào thư viện và lỗi sau ~3 s. Trước đây nó luôn `true` vì Billing 9 với `enableAutoServiceReconnection()` trả `BillingClient.isReady() = true` ở mọi trạng thái, nên lượt khôi phục lúc vào foreground (`AppPluginBase.onStart`) chạy giữa lúc kết nối đầu còn chạy; kết nối chậm (>~3 s) thì lượt đó lỗi sau "Max retries" và log `E IapHelper setup failed: 5` giả xuất hiện sau `connected` (có từ 1.3.0; Samsung A25 1.6.4). Sau khi đã kết nối, mất kết nối vẫn là "sẵn sàng" (lời gọi kế tiếp tự kết nối lại như cũ). Kết quả "already connecting" tới trễ sau khi đã kết nối không còn log là lỗi (`t164-f2b-{red,green}.log`).
- Test: thứ tự ranh giới bước của `initPlugin ms` được ghim (đột biến đặt sai `t0` từng sống sót, 1.6.3 T1-R1); `dataBucketConfig` có test đọc từng khoá và về mặc định khi rỗng/vắng/lỗi (1.6.3 T1-R2; đột biến gốc "API key rỗng ghi đè mặc định" tương đương vì mặc định cũng rỗng). T6-m2 đóng: điều kiện `gpp == null` thừa nhưng tương đương, giữ làm tài liệu.
- Telemetry `logevent` (DataBucket): sau khi người dùng từ chối (tầng `DENIED`) SDK không gửi event nào. Event phát sau đó nằm trong bộ nhớ (tối đa 500, không ghi đĩa), chỉ gửi khi tầng đổi sang `GRANTED`/`PARTIAL` trong cùng process, mất khi process kết thúc; `brain_decision` báo DROPPED và giữ bản trên đĩa (N1, quyết định Q2 2026-09-29; trước 1.6.4 các event này vẫn gửi). Event giữ trước câu trả lời vẫn bị bỏ khi `DENIED` như 1.6.0.
- App không kèm AppLovin MAX (flavor `utility`) không còn khởi tạo lớp MAX của SDK lúc mở app: `AdsNetworkRegistry.build()` đọc `AdsMaxMy.instance` khi chưa kiểm `isMaxSdkAvailable` (inline vào `AdsHelper.initAdsHelper`), nên lúc chạy ART kiểm định lại `AdsMaxMy` và không nạp được lambda cài interface AppLovin vắng mặt; Android 8 log `Rejecting re-init on previously-failed class … NoClassDefFoundError AppLovinSdk$SdkInitializationListener` mỗi lần mở app (Android 9+ không log dòng này; suy từ lịch sử ART, không đo). Không crash. Nay `build()` chỉ đọc `AdsMaxMy` khi `isMaxSdkAvailable`, ngược lại bỏ qua mạng MAX kèm một log debug ("không đăng ký mạng MAX"); placement config trỏ tới MAX bị bỏ qua như trước. Có AppLovin (flavor `game`): thứ tự 24 lần đăng ký giữ nguyên. Vòng 1 trên `8a1e74f` API 26: `r8 strings=4` ở cả ba chặng, cổng FAIL (`t164-r-emu26-gate.log`); vòng 2 trên `51c553a`: 0/0/0, PASS (`t164-r-emu26-gate-2.log`). Test: `AdsMaxAbsentRegistryTest` (ClassLoader từ chối `com.applovin.*`; đỏ ở mã cũ, `t164-f2m-{red,green}.log`).

### Thêm

- `sdk_health` (Firebase) thêm `init_ms` = thời gian main thread của `AppPluginBase.onCreate` (-1 khi chưa đo). Phiên bản SDK trên Crashlytics đã có từ 1.4.0 dưới khoá `appplugin_version` (không thêm khoá `sdk_version` trùng).
- App demo có hai flavor: `utility` (AdMob + Meta, không adapter game: 162/227 artifact; APK release cuối 8 985 364 B, sha256 `287f8b52…19e6`; tải về `bundletool get-size total` 7 584 849–7 641 195 B, `t164-r-release-gate-utility-2.log`, `t164-r-crashkey-abc-2.log` cho số byte, `t164-r-bundle-size-2.log`, `t164-r-deps-2.log`) và `game` (như trước). `tools/device_gate.sh`, `tools/release_gate.sh` nhận `--variant utility|game` (mặc định `utility`); `tools/bundle_size.sh` đo `bundletool get-size total`. Từ 1.6.4 mọi cổng thiết bị chạy `utility`; `game` chỉ build + release gate. CI chạy release gate cho cả hai.
- `README.md` gốc (v0): dựng demo `utility`, tích hợp tối thiểu, resource và khoá Remote Config trỏ tới nguồn.

### Nội bộ

- Nội bộ: `LogEventServer.kt` (970 dòng, file duy nhất vượt 800) tách phần gửi/hàng đợi sang `LogEventTransport`; bộ đọc bit GPP sang `GppBits`; bộ lọc partner GPP của AppsFlyer sang `AppsflyerGppFilter`. Không đổi hành vi, không đổi API; trần G8 mới: LES 786, GppConsent 182, AppsflyerHelper 416 (số đo + 10).
- Nội bộ: `endpoint` của telemetry là `@Volatile` và đọc một lần mỗi event (1.6.3 T1b-m1; chỉ lý thuyết JMM, kết quả DROPPED không đổi).
- Test: `LagrangianBudgetTest` (luồng thật) không còn đỏ ngẫu nhiên (1/17 lượt full suite): tiêu đúng ngân sách (2 interstitial / 20 quyết định với B/T = 0,1) đưa λ về đúng điểm đầu — cân bằng BwK, không phải lỗi sản phẩm; test cũ `assertNotEquals(start, end)` sai theo thiết kế. Test khẳng định chiều và độ lớn từng bước (`λ·exp(0,15·(k − 20·B/T))` trong 1e-12), thêm test ghim k = 2/3/1; đột biến đổi dấu, không cập nhật, sai hệ số 2× và sai B/T đều đỏ; vòng lặp 3000 lượt của lane và 800 lượt của reviewer đều 0 lỗi (1.6.4 T8, `t164-t8-{red,green,mutation,loop}.log`). `src/main` không đổi.
- Test (F2, chỉ `src/test` và `tools/tests`): S3 kill switch tới sau khi `IapHelper.init` đã lên lịch vẫn trả `true` (đột biến bỏ nhánh `connectScheduled` đỏ); `restorePurchases` khi store lỗi khẳng định `onDone` mang quyền lợi đang có; thứ tự xả DENIED → UNKNOWN → GRANTED (vòng DENIED trước `held`, đột biến đảo thứ tự đỏ); V4 shell: `--bogus` bị từ chối, `RESULT: FAIL`, 0 lệnh adb (đột biến `*) shift;;` đỏ). Unit +2, shell 118 → 119 (`t164-f2c-*.log`). Billing: `BillingSetupOutcome` chuyển sang file riêng `iap/BillingSetupOutcome.kt` (30 dòng, `IapHelper.kt` 594 → 583, dump không đổi) và có 7 test mới (`IapHelperReadyTest` 6, `BillingSetupOutcome` 1; 5 đỏ ở mã cũ, `t164-f2b-{red,green}.log`) cùng test ghim thứ tự `connectionStarted` sau `startConnection` (`t164-f2b-mut-RA-*.log`).

### Phải làm khi nâng cấp

- App tiện ích muốn bỏ AppLovin MAX và adapter game: loại khỏi classpath như `app/build.gradle.kts` (`utility`); consumer rules của SDK đã có `-dontwarn com.applovin.**` (R8 không còn báo thiếu lớp). App đang dùng đủ adapter: không phải làm gì.

### Giới hạn đã biết

- Người `DENIED` quay lại đồng ý ở **lần mở sau** không gửi bù event của phiên từ chối (đã mất khi process kết thúc).
- Mục đã đưa vào hàng đợi gửi trước khi tầng chuyển GRANTED → DENIED **trong cùng process** vẫn được gửi lại như cũ (chưa hủy/đỗ hàng đợi gửi khi thu hồi; ngoài phạm vi N1; quyết định chủ dự án cho 1.6.5/1.7.0).
- Emulator API 37 khi host bị tranh chấp tài nguyên (yếu tố cụ thể lúc vòng 2 chưa xác định; RAM/swap là nghi vấn chính) có thể chậm 4–8× và `am_anr` ở monkey (vòng 2 của cổng 1.6.4, đã phân loại contention bằng A/B 6/6 sạch — xem "Đã kiểm"); không phải lỗi SDK.

**API public/protected không đổi** (`tools/api_dump.sh --check` → `API unchanged (4513 lines)`; so `v1.6.3` 0 dòng `-` ngoài `access$` và 28 dòng `+`: `SdkHealth` 3, `AppsflyerGppFilter` 4, `GppBits` 6, `LogEventTransport` 15; façade so `v1.5.0` PASS, khối mới đều `internal`).

**Đã kiểm (DOC/65 §5 "Trạng thái 1.6.4", số đo #1–#27 của `metrics-164` vòng 2 trên HEAD `51c553a`, APK utility sha256 `287f8b52c095…19e6`):**

- Vòng 1 trên `8a1e74f` (log giữ nguyên, số bị vòng 2 thay) tìm ra hai vấn đề, đã sửa ở F2 — vì thế đầu tag là `51c553a`: (1) `IapHelper.isReady()` luôn `true` dưới Billing 9 auto-reconnect (Samsung lượt 3: `Client is already in the process of connecting` → `Max retries reached` → `setup failed: 5` sau `connected`, `t164-r-R5CX10P9EKZ-gate-3.log`; sửa F2B, xem "Sửa"); (2) app không có AppLovin vẫn khởi tạo `AdsMaxMy` (API 26 `r8 strings=4`, FAIL, `t164-r-emu26-gate.log`; sửa F2-MAX). Vòng 1 API 28 cũng có `am_anr=1` ở 2/3 lượt (`t164-r-emu28-gate.log`, `…-rerun2.log`; `…-rerun1.log` PASS), chưa phân loại; vòng 2 API 28 PASS ngay lượt đầu.
- #1 unit ×3: 1584 test / 0 skipped / 0 fail / 0 error mỗi lượt, `@Ignore` 0 (`t164-r-full-2-{1,2,3}.log`; 1555 + 17 tác vụ + 7 F2B + 1 thứ tự + 2 F2C + 2 F2-MAX); #2 dump 4513 (`t164-r-apidump-2.log`); #3 façade + core PASS (`t164-r-facade-2.log`); #4 D7 19/19 (`t164-r-d7-2.log`); #6 `:lintDebug` lỗi 0, `v1.6.3` 0, chênh 0 (`t164-r-lint-{head,base}-2.log`); #7 shell 119/119 (`t164-r-shell-2.log`).
- #5 cỡ file: `LogEventServer.kt` 776 (từ 970; trần 786), `LogEventTransport.kt` 226, `GppConsent.kt` 172 (trần 182), `GppBits.kt` 86, `AppsflyerHelper.kt` 406 (trần 416), `AppsflyerGppFilter.kt` 55, `IapHelper.kt` 583, `BillingSetupOutcome.kt` 30, `AppPluginBase.kt` 282, `SdkHealth.kt` 62, `TelemetryConsentGate.kt` 266, `AdsHelper.kt` 696, `AdsNetworkRegistry.kt` 183; lớn nhất `AdsMobPlNtFull.kt` 793; file > 800 = 0 (`t164-r-sizes-2.log`); #23 `tier=$tier` = 0 (`t164-r-full-2-1.log`).
- #8 release gate `utility` PASS (`variant: utility`, 9 ad unit, 297/301 = 98% lớp SDK đổi tên, zipalign 16 KB, `ClassValue lines in mapping=0`; dex có `initPlugin ms: manager=` 1, `appplugin-billing` 1, `appplugin_version` 2, `hold-denied` 1, `init_ms` 1, "không đăng ký mạng MAX" 1); #9 `game` PASS, sha256 `e2dd7849…2fa4`; #21 tải về `utility` 7 584 849–7 641 195 B (aab 13 844 848 B, ≤ 25 MB, chưa chặn ở 1.6.4); #22 `utilityReleaseRuntimeClasspath` 162 artifact, nhóm game 0, `mediation:facebook` 1 (`t164-r-release-gate-{utility,game}-2.log`, `t164-r-bundle-size-2.log`, `t164-r-deps-2.log`).
- #12 Galaxy A25 R5CX10P9EKZ, **3/3 PASS** cùng APK `287f8b52…19e6`: `by sdk` 0/0 ở cả ba lượt (vi phạm 4/5, frame SDK 2/2 nhưng không do SDK), `strictmode armed` n=1/1, Freecess 0/0 (#11 của 1.6.3 không tái diễn), UMP `NOT_REQUIRED`, FATAL/native/`am_anr` 0; đếm billing mỗi chặng ở cả 6 log thô: `setup failed` 0, `already connecting` 0, `Max retries` 0, `IapHelper connected` 1, `restore: store lỗi` 0 (vòng 1 lượt 3 có đủ chuỗi lỗi; F2B xác nhận trên máy); `onCreate main-thread ms` 16/20, 36/21, 15/29; `initPlugin total` 15/19, 35/20, 15/26; TotalTime 1391/602, 763/606, 592/590 ms (1.6.3: 1310/640); xoay trước = sau (`accelerometer_rotation=1`, `user_rotation=0`); gói gỡ sạch (`t164-r-R5CX10P9EKZ-gate-2-{1,2,3}.log`).
- #11 emulator API 28 PASS lượt đầu (`am_anr` 0 ×6, R8 0, `by sdk` 0 + armed 1 ×2, `appsflyer started` 1; `t164-r-emu28-gate-2.log`); API 26 PASS (R8 0/0/0, raw 1/1/15 đều allowlisted, dòng `Rejecting re-init … y5` biến mất; `t164-r-emu26-gate-2.log`). API 37: 3/4 lượt đỏ `am_anr` vì contention host, A/B 6/6 sạch (dòng ngay dưới).
- API 37 utility vòng 2 (APK `287f8b52…19e6`, `EVENTS=1500`): 4 lượt (`t164-r-emu37-gate-2{,-rerun1,-rerun2,-rerun3}.log`), 1 PASS (rerun1) và 3 FAIL `am_anr=1` ở seed 1 — giữ nguyên trong bảng; mọi mục khác PASS ở cả 4. Các lượt đó cả guest bị bỏ đói (bind→app 1,3–24 s, cold1 16–56 s, monkey 93–291 s/seed, system/systemui/launcher ANR cùng cửa sổ, app chỉ 22–34% CPU). **Phân loại §4.3 = CONTENTION**: A/B trên cùng AVD, host yên (2026-09-30 05:55–06:17; Docker 0, không emulator khác, 0 Gradle ngoài 2 daemon nhàn rỗi của IDE 0–5 % CPU có mặt từ B1 trở đi; load1 sau khi boot 5,5–10,2 là của chính emulator), mỗi APK 2 lượt: 1.6.3 `842bbf93` 2/2, 1.6.4 utility `287f8b52` 2/2, 1.6.4 game `e2dd7849` 2/2 — cả 6 lượt `am_anr` 0, cold1 952–1138 ms, monkey 28–41 s/seed, không khác biệt giữa các APK (2/2 lượt sạch yên của cùng APK); yếu tố host cụ thể lúc vòng 2 chưa xác định (`t164-r-emu37-ab-{1..6}.log`, 1,3 = 1.6.3; 2,4 = utility; 5,6 = game; `t164-r-anr37-report.log`).
- #14 `prefs warmup waited=0 ms`, `timeouts=0` ở cả 6 lượt emulator và 3 lượt Samsung (`optional` Samsung 1/1, 2/3, 2/3 ms). #15 `initPlugin total ≤ main-thread` và `≠ none` mọi lượt; `onCreate main-thread ms` cold/cold2: API 28 22/258 (bước lớn nhất `manager` 224 ở cold2), API 26 44/767 (`manager` 682), API 37 bốn lượt 303/1008, 891/179, 992/421, 510/249 (`manager`/`helper+gate` lớn nhất); mục tiêu ≤ 80 ms emulator chỉ đạt ở cold1 API 28 và API 26 (không chặn). #16 TotalTime cold/cold2: API 28 486/8005, API 26 320/4665, API 37 26017/20156, 15912/8825, 56292/9190, 23535/11481 (1.6.3: 2309/1620, 1006/993, 2834/718). Emulator ngày 2026-09-30 chậm 4–8× ngay từ trước khi mã app chạy (process-create → `bindApplication` 1,3–24 s so với 55–159 ms ngày 09-29) — giả thuyết tranh chấp tài nguyên host, chưa chứng minh (`anr37-analysis`).
- #17 allowlist (6 log cuối): `AFf1lSDK$2` 10, `AppsFlyerLVL$resultListener` 10, `reportRequestStats2` 6, `AFj1sSDK$2` 3, `GetAppsReferrerStateListener` 3; `Long monitor contention` cold1+cold2: API 37 0, API 28 4, API 26 7, Samsung 0+0 / 2+0 / 1+1; không frame `com.app.plugin` (`t164-r-e1p2-2.log`). #19 `sdk_health`: `sdk_version=1.6.4`, `init_ms` = `main-thread ms` cùng log (API 37 179, API 28 258, API 26 767, Samsung 20/21/29), `networks=admob,meta,` (không `max`) ×6. #19b Crashlytics khoá `appplugin_version`: test 2/0, mã 1, dex 2, và trên máy khoá `{"appplugin_version":"1.6.4"}` trong file khoá của Crashlytics (`t164-r-crashkey-{abc,emu37}-2.log`). #20 `telemetry off` = 1, `not initialized` = 0 ở cold1/cold2 của cả 6 cổng.
- #24 N1 trên máy (APK debug `utility` `d5c52b47…`, API 37, lần thử 4): POST trước T1 0, T1→T2 (DENIED ~93 s) POST **0**, `hold-denied` 7, `flushed 7 held after DENIED` 1, POST 45 s sau GRANTED 8, FATAL của app 0, `am_anr` 0; lần 1–3 vô hiệu (máy ảo khởi động nguội quá chậm, form consent không tới được; ghi lại trong log) (`t164-r-n1-emu37-2.log`).
- #25 README: agent mới chỉ đọc `README.md` dựng được APK `utility` trong 137 s (Gradle 122 s, 84 task, `local.properties` là chỗ duy nhất phải đoán), 7 chỗ thiếu đã bổ sung vào README (`t164-r-readme.log`).
- Review cuối: F1a (code) 0 Critical / 0 Important; mọi Minor còn lại nằm ở DOC/65 §5 "Carry sang 1.6.5".

**Chưa kiểm:** Redmi 65c9b673 không cắm (`adb -s 65c9b673 get-state` → `device not found`; không có số 1.6.4 trên máy này); API 30/33; MAX trên máy (demo không có `admax_devKey`; chỉ unit và kiểm dex — O10); mua/khôi phục với tài khoản Play thật (chỉ unit và log kết nối trên Samsung); đường `cleared` của AppsFlyer trên máy (chỉ unit), 8 định dạng quảng cáo sau consent (không chạm ở 1.6.4); README sau khi bổ sung 7 chỗ chưa qua lượt agent-mới thứ hai; CI và release (#26–#27: nhánh chưa push, chưa PR, chưa tag).

## [1.6.3] - 2026-09-29

"1.6.3 — main-thread quy được về bước, allowlist neo hết lớp chủ, baseline façade = tag" (plan `docs/superpowers/plans/2026-09-29-sdk-1.6.3-plan.md`). API public/protected không đổi.

### Sửa

- `AppPluginBase.initPlugin` in một dòng `initPlugin ms: manager=… meta=… helper+gate=… logevent=… total=…` (log thẳng, tag `AppPlugin`) để `onCreate main-thread ms` quy được về bước (review cuối 1.6.2 §4.2: API 26 release 63/89 và 124/193 ms chưa rõ ở đâu); đọc meta-data `DATABUCKETS_*` tách thành hàm riêng, hành vi giữ nguyên (`t163-t1-{red,green}.log`).

- `LogEventServer`: app không cấu hình `DATABUCKETS_BASE_URL` (mặc định rỗng, telemetry tắt) không còn thấy dòng lỗi `LogEventServer not initialized: dropping events until init` mỗi lần mở app; thay bằng một dòng `Log.i` "telemetry off: DATABUCKETS_BASE_URL rỗng" mỗi process (log thẳng, không qua cờ log SDK); event vẫn bị bỏ như trước; dòng lỗi chỉ còn khi thật sự chưa `init` (18/24 log thô cổng 1.6.2 in dòng giả; `t163-t1b-{red,green}.log`).

- `tools/device_gate_allowlist.txt`: dòng LVL của AppsFlyer neo lớp chủ `AFf1lSDK$2` và đúng lớp thiếu `AppsFlyerLVL$resultListener` (dạng `Rejecting re-init` + `Caused by`), không còn regex nào thiếu neo lớp chủ; lớp chủ khác (kể cả `com.app.plugin.*`) thiếu LVL vẫn bị đếm (review cuối 1.6.2 M3; 106/106 dòng trong log 1.6.1–1.6.2 cùng dạng; `t163-t3-{red,green}.log`).

- Test: nhãn `consent tier=<tầng> outside GDPR scope` được ghim cho cả `not started` (cả hai đường gọi: `startAfterConsent` và `followTier`), `stopped`, `resumed` (trước 1.6.3 chỉ `started` và `consent updated`; review 1.6.2 R-C, I1 của review Task 4); bốn test mới, mỗi đột biến bỏ nhãn (`scopeLabel(tier, null)` ở `startAfterConsent`, `stopped`, `resumed`; `logNotStarted(tier, null)` chỉ khi `gdprApplies == false` ở lời gọi trong `followTier`) bị giết (`t163-t4-red.log`, `t163-t4-fix-r5-red.log`).

- `tools/api_facade_check.sh`: `BASELINE` mặc định `v1.5.0` (= `44e72d8`, tag publish 2026-09-29) thay `a76f77e`; dump hai commit y hệt (0 dòng khác) nên kết quả không đổi; `ci.yml` bước "API facade unchanged vs v1.5.0" + `fetch-tags: true` để runner thấy tag (N7 1.6.1/1.6.2 — hết).

- AppsFlyer: sau khi SDK đặt bộ lọc partner `"all"` (GPP opt-out), lần start kế trong cùng process gỡ bộ lọc **chỉ khi** `IABGPP_HDR_GppString` được UMP viết lại không còn section US 7–12 **và** tầng consent là `GRANTED` (`cleared (gpp=none, header không còn section US, tier=GRANTED)`); `GppSID=-1` một mình, chuỗi hỏng hay chuỗi vắng không gỡ (quyết định chủ dự án 2026-09-29 "C + GRANTED", review cuối 1.6.2 §4.1; `t163-t6-{red,green}.log`).

### Thêm

- `tools/device_gate.sh`: dòng `metrics <chặng>: …` ghi thêm `initPlugin ms=manager=… meta=… helper+gate=… logevent=… total=…` (dòng log mới của `AppPluginBase.initPlugin`, 1.6.3 Task 1) và `TotalTime ms=…` của `am start -W` (trước 1.6.3 chỉ có trong stdout cổng, docs phải grep tay — review 1.6.2 minors); `key[…]>` chép thêm dòng `initPlugin ms:` và `telemetry off`.

### Giới hạn đã biết

- AppsFlyer: `GppSID=-1` một mình (form EEA cùng process, ca Task 5 1.6.2) vẫn giữ bộ lọc `"all"` tới khi process khởi động lại — quyết định chủ dự án (C) 2026-09-29; giới hạn 1.6.2 còn cho ca này (chuỗi GPP trên đĩa vẫn ghi opt-out).

- Samsung A25 (One UI, API 36): cold start 2 có thể báo `DiskReadViolation` từ `system_server` (Freecess unfreeze) qua binder khi `IapHelper.connect` bind BillingClient lúc khởi động — không phải I/O của SDK, phụ thuộc trạng thái đóng băng gói; có từ `f710795`; 1.6.4 chuyển bind ra khỏi đường khởi động (DOC/67 #1).

**API public/protected không đổi** (`tools/api_dump.sh --check` → `API unchanged (4488 lines)`; so `7806644` chỉ thêm 2 dòng `internal` `headerHasUsSection$appplugin`, 0 dòng xoá; `tools/api_facade_check.sh` PASS so `v1.5.0`).

**Đã kiểm (DOC/65 §5 "Trạng thái 1.6.3", số đo #1–#18 của `metrics-163`, HEAD `4f2125a`, 17 commit `7806644..4f2125a`):**

- #1 unit ×3: 1555 test / 0 skipped / 0 fail / 0 error mỗi lượt, `@Ignore` 0 (`t163-r-full-{1,2,3}.log`); #2 dump API 4488 dòng (`t163-r-apidump.log`); #3 façade + core PASS, 9 khối `new:` đều được phép, không được phép 0 (`t163-r-facade.log`).
- #4 D7 17/17 PASS trên `7806644..4f2125a` (`t163-r-d7.log`); #6 `:lintDebug` 0 lỗi, `7806644` 0 lỗi, chênh 0 (`t163-r-lint-{head,base}.log`); #7 test shell cổng 115/115, FAIL 0 (`t163-r-shell.log`).
- #5 cỡ file: `LogEventServer.kt` 970 (≤ 970), `AdsHelper.kt` 696, `PrefsWarmup.kt` 160, `AppsflyerHelper.kt` 444, `AppPluginBase.kt` 280, `GppConsent.kt` 249; `AdsHelperReflectedFieldsTest` 2/2 (`t163-r-sizes.log`); #17 `tier=$tier` = 0, `AppsflyerHelperConsentTest` 57 test 0 fail (`t163-r-full-1.log`).
- #8 release gate PASS trên APK duy nhất sha256 `842bbf9373f76883700193bca8e91f6a9746ed89e5819d64077ad90ca221a42e`: 9 ad unit, 98% lớp SDK đổi tên, zipalign 16 KB, `ClassValue lines in mapping=0`, `af_app_id` 1, chuỗi `initPlugin ms` 1, chuỗi `telemetry off` 1 (`t163-r-release-gate.log`); sha khớp ở mọi lượt thiết bị.
- #9/#10 cổng thiết bị `EVENTS=1500`, một lượt mỗi máy, không đỏ: emulator API 37 **PASS**, API 28 **PASS** (raw R8 1/7/7 đều allowlisted), API 26 **PASS** (raw 1/15/15 đều allowlisted); R8 0/0/0, `strictmode armed` n=1/1, `appsflyer started` n=1, FATAL/native/`am_anr` 0 ×6, `pm clear` Success (`t163-r-emu{37,28,26}-gate.log`); nhãn `consent tier=UNKNOWN outside GDPR scope` có trên API 37.
- #11 Galaxy A25 R5CX10P9EKZ (API 36), **hai lượt cùng APK `842bbf93…a42e`, không ghi đè**: lượt 1 **FAIL** — dòng FAIL duy nhất là StrictMode cold start 2 `by sdk=3` (`cold start 1` 0; R8 0/0; FATAL/native/`am_anr` 0; monkey 3 seed exit 0; xoay không đổi; gói gỡ sạch) (`t163-r-R5CX10P9EKZ-gate.log`); chạy lại 14 phút sau **PASS**, `by sdk` 0/0 (`t163-r-R5CX10P9EKZ-gate-rerun1.log`). Phân loại: trạng thái máy / gốc từ OS, không phải hồi quy 1.6.3, xem "Giới hạn đã biết"; lượt 1 vẫn ghi FAIL.
- #12 `PrefsWarmup`: `waited` 0 ms, `timeouts` 0 ở mọi máy (max `min(waited, optional)` = 0, Task 1b không cần); `optional prefs ms` cold/cold2: API 37 0/2, API 28 18/35, API 26 41/21, Samsung 3/5.
- #13 `onCreate main-thread ms` (cold/cold2) và `initPlugin ms` (manager/meta/helper+gate/logevent/total): Samsung 31/40 (16/1/11/0/28 và 26/0/14/0/40); API 37 115/12 (12/34/66/2/114 và 8/0/4/0/12); API 28 135/40 (103/0/19/0/122 và 35/1/4/0/40); API 26 263/209 (190/15/53/4/262 và 179/0/29/0/208); `total ≤ main-thread` và `initPlugin ≠ none` mọi lượt; bước lớn nhất thường là `manager` (API 37 cold: `helper+gate` 66). Vượt mục tiêu (không chặn): emulator ≤ 80 ms ở API 37 cold, API 28 cold, API 26 cả hai chặng; máy tầm trung ≤ 30 ms ở Samsung cold2 (40).
- #14 `TotalTime` cold/cold2: Samsung 1310/640 ms (1.6.2: 761/496), API 37 2834/718, API 28 2309/1620, API 26 1006/993 ms (không chặn).
- #15 allowlist/contention: `Long monitor contention` thô tổng 6 (API 28 1, API 26 4, Samsung lượt chạy lại 1; API 37 0, Samsung lượt 1 0), thời gian main bị chặn (pid = tid) 0, contention gắn `ClassNotFoundException` 0, không frame `com.app.plugin` → PASS theo luật 1.6.2 (tổng thô chỉ để tham khảo); LVL neo mới khớp API 28 (6) và API 26 (8).
- #16 ANR: `am_anr` 0 ở mọi chặng của mọi lượt 1.6.3 (3 emulator, Samsung 2 lượt); không lượt đỏ về ANR (FAIL của #11 là StrictMode).
- #18 `telemetry off` = 1, `not initialized` = 0 ở 8/8 log app cold1/cold2 (API 37, 28, 26, Samsung).
- Review cuối: F1a + F1b, Critical 0 / Important 0; F1b chạy lại unit 1555/0/0/0, dump 4488, façade PASS, shell 115/115 và đối chiếu 20/20 số của CHANGELOG với log thô.

**Chưa kiểm:** Redmi 65c9b673 không cắm (không có số 1.6.3 trên máy này); ngưỡng chặn `onCreate main-thread ms` trên máy thật API 26–28 bỏ khỏi 1.6.3 theo quyết định phạm vi 2026-09-29 (không có máy); API 30/33; MAX + GPP trên máy (demo không có `admax_devKey`, chỉ unit — O10); đường `cleared` trên máy (không đạt được theo cấu trúc: form EEA ghi `GppSID=-1`, kế hoạch mục "Không lập task"; chỉ unit); CI (nhánh chưa push).

## [1.6.2] - 2026-09-29

"1.6.2 — cổng thiết bị giữ bằng chứng, allowlist hết dòng mồ côi, warm-up ưu tiên file main cần" (plan `docs/superpowers/plans/2026-09-29-sdk-1.6.2-plan.md`, DOC/65 §5 "Trạng thái 1.6.2"). **API public/protected không đổi** (`tools/api_dump.sh --check` → `API unchanged (4486 lines)`; diff so `b96fab3`: 0 dòng; `tools/api_facade_check.sh` PASS; `t162-r-apidump.log`, `t162-r-facade.log`).

**Đã kiểm (DOC/65 §5 "Trạng thái 1.6.2"):** unit **1540/0/0/0** ×3, `@Ignore` 0 (`t162-r-full-{1,2,3}.log`, `40fe798`; `87b51c5` chỉ đổi allowlist + test shell + CHANGELOG); D7 10/10 trên `b96fab3..40fe798` (`t162-r-d7.log`); `:lintDebug` 0 lỗi, `b96fab3` 0 lỗi, chênh 0 (`t162-r-lint-{head,base}.log`); cỡ file: `LogEventServer.kt` 966 (≤ 970), `AdsHelper.kt` 696 (≤ 700), `PrefsWarmup.kt` 160 (≤ 160), `AppsflyerHelper.kt` 435 (≤ 440), file lớn nhất `AdsMobPlNtFull` 793 (≤ 800), `AdsHelperReflectedFieldsTest` 2/2 (`t162-r-sizes.log`); test shell cổng 87/87 tại `40fe798` (`t162-r-shell.log`) và **101/101** tại HEAD (G3 thêm 12; `t162-g3-green.log`; F2 thêm 2 ca âm dấu `;`, `t162-f2-mutations.log`); test `AppsflyerHelper` 48 chạy 0 fail, `grep -c 'tier=$tier'` = 0 (`t162-r-full-1.log`).

Cổng release trên APK duy nhất (dựng ở `40fe798`, sha256 `b2824c5466a7012ebbfd737201578773bc6c155f3ee3269ab176992c544bc940`; sha khớp trước/sau mọi gate): 9 ad unit, 98% lớp SDK đổi tên (284/288), zipalign 16 KB, **`ClassValue lines in mapping=0`**, `af_app_id` 1 → RESULT PASS (`t162-r-release-gate.log`). Commit `87b51c5` (G3) sau APK chỉ đụng tooling, nên số thiết bị áp cho HEAD.

Cổng thiết bị `EVENTS=1500` (R8 = số dòng chuỗi R8 sau cold start / monkey / cold start 2; mọi lượt: `by sdk` 0 cả hai chặng, `strictmode armed` n=1/1, `appsflyer started` n=1, FATAL/native/`am_anr` 0 ở cả 6 lần đếm, `pm clear` Success, `prefs warmup waited=0 ms timeouts=0`):
- emulator API 37: **PASS**, R8 0/0/0, TotalTime 794/1349 ms (cold start / cold start 2, `t162-r-emu37-gate.log`).
- emulator API 28: **PASS**, R8 0/0/0, TotalTime 845/1348 ms (cold start / cold start 2, `t162-r-emu28-gate.log`).
- Redmi 65c9b673 (API 34): **PASS**, R8 0/0/0, UMP `NOT_REQUIRED`, TotalTime 802/454 ms, xoay khôi phục (`accelerometer_rotation` 0 → 1 do monkey → 0), gói gỡ sạch (`t162-r-65c9b673-gate.log`).
- emulator API 26, **hai lượt, cùng APK, không ghi đè**: (1) Task R **FAIL**, chỉ vì R8 0/6/6 — 3 cặp dòng AppsFlyer `AFe1hSDK$AFa1vSDK` dò lớp Samsung cloud game không có trong AAR (`Rejecting re-init … NoClassDefFoundError … CloudDevCallback` + `Caused by ClassNotFoundException`), FATAL/native/`am_anr` 0, không crash (`t162-r-emu26-gate.log`); G3 xác nhận lành tính (lớp Samsung vắng ở AAR 6.16.2, `releaseRuntimeClasspath` và mapping; AppsFlyer dò `Class.forName` trong try/catch `Throwable`), thêm allowlist neo đúng lớp chủ; đếm lại offline log thô lượt (1) với allowlist mới: 0/0/0 (`t162-g3-offline-recount.log`); (2) G3 chạy lại cùng APK: **PASS**, R8 0/0/0 (raw 1/15/15 đều allowlisted), dòng Samsung **không xuất hiện lại** (chập chờn; regex mới chưa được thiết bị kích hoạt, chỉ đối chiếu offline và fixture) (`t162-g3-emu26-gate.log`, `t162-g3-red.log` → `t162-g3-green.log`).
- Galaxy A25 R5CX10P9EKZ (API 36, cắm lại sau Task R, cùng APK `b2824c54…`): **PASS** — `by sdk` 0/0, `strictmode armed` 1/1, `appsflyer started` n=1, R8 0/0/0, FATAL/native/`am_anr` 0 mọi lần đếm, `waited` 0/0 ms (`optional` 14/3 ms, `timeouts` 0), `onCreate main-thread ms` 30/22, TotalTime 761/496 ms, xoay không đổi, gói gỡ sạch (`t162-r-R5CX10P9EKZ-gate.log`).

`am_anr` (đủ số): 0 ở mọi lượt 1.6.2 (3 emulator × 6 lần đếm + Redmi + lượt chạy lại API 26); không lượt đỏ về ANR, không phải chạy lại vì ANR.

Task 1b (`PrefsWarmup` chờ thêm file tuỳ chọn): **không cần** — `waited=0 ms`, `timeouts=0` ở mọi lượt; `optional prefs ms` (cold start / cold start 2): API 37 3/4, API 28 94/3, API 26 21/13 (lượt chạy lại 15/23), Redmi 32/1 → max `min(waited, optional)` = 0 ms. `AppPluginBase.onCreate main-thread ms` bản release (cold start / cold start 2): API 37 32/26, API 28 85/29, API 26 63/89 (lượt chạy lại 124/193), Redmi 33/11 — mục tiêu ≤ 80 ms emulator, ≤ 30 ms máy tầm trung, không chặn: **vượt** ở API 28 cold start (85), API 26 cold start 2 (89) và cả hai chặng của lượt chạy lại API 26 (124/193), Redmi cold start (33); chưa xác định nguyên nhân (log cổng ghi tải host ở mỗi chặng, lượt chạy lại API 26 chạy khi host yên, load1 2,90–7,77).

Allowlist theo từng regex trên 4 log gate: emulator API 37 và Redmi 0 dòng allowlisted; API 28: `appsflyer[./]lvl` 12, `reportRequestStats2` 3; API 26 (Task R): `AFj1sSDK$2` 6, `appsflyer[./]lvl` 16, `com.miui.referrer` 6, `reportRequestStats2` 3. Dòng "Long monitor contention … ClassNotFoundException" 0 ở mọi log thô, thời gian main bị chặn (tid == pid) 0 lần; tổng contention thô 0/3/2/0 (API 37/28/26/Redmi) chỉ để tham khảo — ngưỡng "contention thô = 0" của plan là lỗi thiết kế (R-B), điều kiện thực là contention gắn `ClassNotFoundException` = 0 (`t162-r-*-gate.log`, số đo #14 `metrics-162.md`). Nhãn AppsFlyer: `key[cold start 2]` có `consent tier=UNKNOWN outside GDPR scope` 1 lần trên API 37 (`t162-r-emu37-gate.log`).

Kiểm tay Task 5 (AppsFlyer gỡ bộ lọc partner qua US-GPP opt-out → allow → cùng process): **N/A** — dừng ở bước S4, form US không mở được sau khi rút consent bằng form EEA trong cùng process (UMP EEA ghi lại `GppSID=-1`), chưa có GPP allow nào được ghi; đếm ở pid 6160: `all partners` 1, `stopped` 1, `resumed` 1, `cleared` 0, FATAL 0 (`t162-t5-emu37-filter.log`). Đường unit (GPP allow → `cleared`) vẫn chưa được thiết bị chứng minh.

**Chưa kiểm:** API 30/33; MAX + GPP trên máy (demo không có `admax_devKey`, chỉ unit — O10); regex allowlist Samsung cloud game chưa từng khớp trên thiết bị ở lượt PASS (chỉ offline); đường gỡ bộ lọc partner GPP allow → `cleared` trên máy (Task 5 N/A); CI (nhánh chưa push). **Chưa tag.**

### Sửa

- `PrefsWarmup` nạp 4 file prefs main đọc trong `Application.onCreate` trước, rồi mới dò `af_app_id`/AppLovin và nạp file của chúng: dò chậm hay treo không còn đẩy lùi 4 file đó; main vẫn chờ một latch (≤ 250 ms) như 1.6.1, `af.init` vẫn thấy file AppsFlyer đã nạp (review cuối 1.6.1 m1; `t162-t1-{red,green}.log`).

- `tools/device_gate_allowlist.txt`: gỡ dòng cảnh báo ART "Long monitor contention … ClassNotFoundException.<init>" (1.6.1 G1) — 0 dòng khớp trong mọi log cổng sau fix `ClassValue` (G2), bằng chứng duy nhất của nó là triệu chứng lỗi đó; dòng MIUI referrer của AppsFlyer neo lớp chủ `AFj1sSDK$2` và dạng `Caused by` (lớp khác thiếu listener MIUI vẫn bị đếm); header thêm luật "không allowlist dòng chỉ mang từ khoá R8 mà không phải lỗi nạp lớp" (review cuối 1.6.1 m2, G1-m1/m2/m3).

- Log AppsFlyer `not started`, `stopped`, `resumed`, `consent updated` ghi `consent tier=<tầng> in|outside GDPR scope` như dòng `started` (review 1.6.1 Task 5); `ConsumerRulesTest` chỉ ghim `static boolean` của `CachingKt.useClassValue` (đúng điều rule `-assumevalues` cần; review G2-n1/n2).

### Thêm

- `tools/device_gate.sh` giữ bằng chứng: log thô của app mỗi chặng và buffer crash ở lại `DOC/testing/device-gate-app-<serial>-<ts>-{cold1,post,cold2,crash}.log`, kể cả khi cổng dừng giữa chừng (trước 1.6.2 EXIT trap xoá, review 1.6.1 T3 n1); dòng `prefs warmup`, `main-thread ms`, `appsflyer started`, `strictmode armed` chép vào log cổng kèm dòng `metrics <chặng>: …` (có `optional prefs ms`); canary `strictmode armed` (demo in khi bật StrictMode) cần ≥ 1 mỗi chặng cold start, thiếu thì FAIL — APK không có canary (host tự dùng cổng, nhánh cũ): đặt `STRICT_ARMED_MIN=0` (T3 m9); kết quả `pm clear` vào log, không `Success` thì FAIL (T3 m1); đếm crash/ANR ngay trước chặng 2 thay vì xoá buffer (T3 m8); ghi tải host (`uptime`) và máy (`/proc/loadavg`) trước mỗi chặng để phân loại ANR dưới tải.

- `tools/device_gate_allowlist.txt`: allowlist dòng AppsFlyer dò SDK Samsung cloud game tuỳ chọn trên API 26 (`AFe1hSDK$AFa1vSDK` implements `com.samsung.android.game.cloudgame.dev.sdk.CloudDevCallback`) — lớp Samsung không có trong AAR 6.16.2, `releaseRuntimeClasspath` lẫn mapping (không phải R8 strip), AppsFlyer dò `Class.forName` trong try/catch `Throwable` trước khi dùng; neo lớp chủ `AFe1hSDK$AFa1vSDK` và dạng `Caused by` đúng tên lớp (lớp chủ khác, lớp anh em cùng gói vẫn bị đếm) (1.6.2 G3; `t162-g3-{red,green}.log`). Review cuối (F2): thêm hai ca âm ghim dấu `;` cuối regex Samsung/MIUI (`…CloudDevCallbackImpl;`, `…GetAppsReferrerStateListenerImpl;` vẫn bị đếm; bỏ `;` thì đỏ, `t162-f2-mutations.log`); header allowlist: dòng `không định kỳ` chỉ gỡ khi ≥ 3 lượt sạch liên tiếp trên đúng API/máy; `.gitignore` bỏ qua log thô `device-gate-app-*` và `device-gate-monkey-*`.

### Giới hạn đã biết

- AppsFlyer trong cùng process: sau khi người dùng Mỹ opt-out GPP (SDK đặt bộ lọc partner `"all"`) rồi trả lời form EEA cấp consent, UMP ghi lại `GppSID=-1`, SDK đọc GPP là vắng và `applyGppSharingFilter` chỉ gỡ khi GPP có mặt (`AppsflyerHelper.kt:200-204`) nên bộ lọc `"all"` ở lại đến khi process khởi động lại, dù chuỗi GPP vẫn ghi opt-out. Thiên về an toàn quyền riêng tư (chia sẻ partner ít hơn trong process đó), không crash; chưa quyết giữ hay sửa (cần rà soát pháp lý consent) — quan sát ở Task 5 trên máy (`t162-t5-emu37-filter.log`).
- Kill switch `logevent` bắn trong lúc event đang giữ chờ consent: `session_start` mang `pre_consent_lost` bị bỏ lúc xả và số đó không được báo lại ở lần mở sau (bắn lúc phát thì số được giữ). Chỉ là bộ đếm chẩn đoán dưới cần khẩn cấp; khi kill switch còn bật, lần mở sau cũng không gửi (review 1.6.1 T4 m4).

## [1.6.1] - 2026-09-29

"1.6.1 — AppsFlyer/AppLovin hết đọc đĩa trên main, G2 đo được đường AppsFlyer, kill switch áp cho event đang giữ" (plan
`docs/superpowers/plans/2026-09-28-sdk-1.6.1-plan.md`, DOC/65 §5 "Trạng thái 1.6.1"). **API public/protected không đổi**
(`tools/api_dump.sh --check` → `API unchanged (4486 lines)`; diff so `56cfd1d`: 0 dòng bớt, đúng 2 dòng thêm —
`PrefsWarmup.names$appplugin(Context, boolean, boolean)` (`api/appplugin.api:4481`) và `AdsMaxMy.resetForTest$appplugin()`
(`:1416`), cả hai `internal`; `tools/api_facade_check.sh` PASS).

**Đã kiểm (DOC/65 §5 "Trạng thái 1.6.1"):** unit **1535/0/0/0** tại HEAD (cây giống hệt `74f27db`, nơi đã chạy;
`g2-report.md`) — vòng 1, trước fix G2, `af4161b`: 1533/0/0/0 ×3 (`t161-r-full-{1,2,3}.log`); `tools/api_dump.sh --check`
→ API unchanged (4486 dòng), diff so `56cfd1d` 0 bớt/2 thêm như trên (`t161-r-apidump.log`, `t161-r-apidiff.log`; nhãn
"AdsMaxMy.names$appplugin" ở vòng 1 là sai, đúng phải là `PrefsWarmup.names$appplugin` — sửa theo `final-161-review.md`
I1); `tools/api_facade_check.sh` PASS (`t161-r-facade.log`; không đổi giữa `af4161b` và `46af047`, G2 chỉ chạm
`consumer-rules.pro`/test); D7 13/13 trên `56cfd1d..af4161b` (`t161-r-d7.log`); `:lintDebug` 0 lỗi so `56cfd1d`
(`t161-r-lint-head.log`, `t161-r-lint-56cfd1d.log`); cỡ file không đổi: `LogEventServer.kt` 966 (≤ 970), `AdsHelper.kt`
696, `PrefsWarmup.kt` 142 (≤ 150), `AppsflyerHelper.kt` 435 (≤ 440), không file SDK nào khác > 800 dòng.

Cổng release trên APK cuối (R2, HEAD `46af047`, sha256
`629fb5c7af13f2e46e267eef77ea26d386baa778996c53d7b2eb670e449c2d86`): 9 ad unit, 98% lớp SDK đổi tên (284/288), zipalign
16 KB, **`ClassValue lines in mapping=0`** (cần 0) → RESULT PASS (`t161-r2-release-gate.log`, `r2-report.md`).

Cổng thiết bị `EVENTS=1500` trên APK R2 (`46af047`): emulator API 37 — `by sdk` 0/0 cả hai chặng, `appsflyer started`
n=1, R8 0/0/0, `ClassValue` 0 dòng log, FATAL/native/`am_anr` 0 ở cả 5 chặng, TotalTime 1174/519 ms, PASS
(`t161-r2-emu37-gate.log`); emulator API 28 — cùng kết quả (R8 raw 1/7/7, đều allowlisted), TotalTime 843/1196 ms, PASS
(`t161-r2-emu28-gate.log`); Galaxy A25 (R5CX10P9EKZ, API 36) — `by sdk` 0/0 (prefs first load 0, other 0), `appsflyer
started` n=1, R8 0/0/0, `ClassValue` 0, FATAL/native/`am_anr` 0, TotalTime 889/683 ms, xoay không đổi, gói gỡ sạch, PASS
(`t161-r2-R5CX10P9EKZ-gate.log`). API 26 chưa chạy lại trên APK R2 (không có emulator API 26 trong vòng đo này); cùng
luật R8 với G2, cổng đo trên APK build tại `e3e51a1` (trên nhánh `d8de500`; log commit `b164702`, worktree G2) vẫn PASS: `by sdk` 0/0, `appsflyer started` n=1, R8 0/0/0
(raw 1/15/15 allowlisted), `ClassValue` 0, FATAL/native/`am_anr` 0, TotalTime 986/736 ms (`t161-g2-api26-gate.log`); API
28 cùng luật cũng PASS ở G2 (TotalTime 2255/1618 ms, `t161-g2-api28-gate.log`) — số API 28 chính thức của bản release là
số R2 ở trên. StrictMode debug đối chứng đường AppsFlyer (vòng 1, `af4161b`, tham khảo — không chặn bản release): `by
sdk` 0/0/0 ×3 (1.6.0: 1/1/1), main-thread ms 86/102/141 (trung vị 102, mục tiêu ≤ 80 ms chỉ áp bản release)
(`t161-r-emu37-strictmode-debug.log`). `prefs warmup waited=` trên API 28/26 (đo bổ sung SHOULD-FIX, T1 m4, bản debug
`46af047`, ×3 mỗi API): **0/0/0 ms cả hai API** (cần ≤ 250), 0 dòng warning/error mỗi lượt, `appsflyer started` 1/1/1 mỗi
API, FATAL 0/0/0 (`t161-w-emu28-warmup.log`, `t161-w-emu26-warmup.log`).
Redmi 65c9b673: chưa đo (máy không cắm vòng này), chủ repo chạy.

Đỏ → xanh P1 trên release (hàng 16 kế hoạch `docs/superpowers/plans/2026-09-28-sdk-1.6.1-plan.md`): trước fix `by
sdk=1` (`t161-t3-gate-red-api28.log`), sau fix API 28 `by sdk=0`, cả hai `appsflyer started … n>=1`
(`t161-r2-emu28-gate.log`).

`am_anr` (đủ số, không giấu lượt đỏ): monkey seed 1 đầu trên emulator API 37 tại `af4161b` (APK `3de1ace…`) am_anr=1
(`Input dispatching timed out (Application does not have a focused window)`, tải máy ảo 49,77, CPU 99%, nhiều lane
chạy song song; `DOC/testing/device-gate-monkey-emulator-5570-20260928-223435-seed1.log`); 2 lần chạy lại cùng APK trên
host yên tĩnh hơn (load 7,3/9,5) PASS am_anr=0 (`t161-r-emu37-rerun{1,2}-gate.log`); APK release cuối (R2, `46af047`)
API 37 PASS am_anr=0 (`t161-r2-emu37-gate.log`). Phân loại: tranh chấp host, xảy ra 1 lần, không lặp lại 3/3.

`ClassValue`: tồn tại tiềm ẩn từ 1.6.0 (mapping `56cfd1d` đã có outline trong lớp Unity dùng chung
`AndroidAttribution$$ExternalSyntheticApiModelOutline0`, `g2-report.md` mục 0), sửa ở G2 bằng rule `-assumevalues` cho
`CachingKt.useClassValue`; `tools/release_gate.sh` nay FAIL nếu mapping còn chữ `ClassValue` (đỏ 51 dòng trên mapping
trước sửa `t161-g2-fix-gate-red.log`, xanh 0 dòng `t161-g2-fix-gate-green.log`).

**Chưa kiểm:** API 30/33; MAX + GPP trên máy (demo không có `admax_devKey` — chỉ unit, O10); Redmi 65c9b673 (máy không
cắm vòng này, chủ repo chạy); kịch bản tay ngoài checklist R-12 trên máy thật; CI (nhánh chưa push). **Chưa tag.**

### Sửa

- `PrefsWarmup` nạp sẵn file prefs của AppsFlyer (`appsflyer-data`, khi host có `af_app_id`) và AppLovin (`com.applovin.sdk.shared`, khi có SDK AppLovin) trên luồng nền: `af.init` và `AppLovinPrivacySettings.setDoNotSell` trên main không còn đọc đĩa — StrictMode `by sdk` bản debug tối đa 1 → 0 mỗi cold start (mã cũ đua với luồng nền của AppsFlyer: 3/5 lượt đỏ trên emulator API 37; mã mới 0/6). Số StrictMode chỉ đến từ AppsFlyer: AppLovin tự bọc lần đọc trong `allowThreadDiskReads`, nên nạp sẵn file của nó bỏ một lần đọc chặn main nhưng không đổi G2 (hết giới hạn 1.6.0 "AppsFlyer af.init gây StrictMode DiskRead", "MAX + GPP setDoNotSell trên main"; review Task 8a P1/m3; `t161-t1-emu37-strictmode-{red,green}.log`). App đang dùng (Speaker Clean, Invoice Maker) chỉ nhận hiệu ứng khi dùng SDK này và khai báo `af_app_id`: hôm nay cả hai dựng `:adsplugin` chép riêng, không có AppsFlyer (review Task 1 i2).
- AppsFlyer start lại trong cùng process (rút rồi cấp lại consent) với GPP không opt-out: gỡ bộ lọc partner do SDK
  đặt trước đó (`setSharingFilterForPartners()` rỗng, log `appsflyer sharing filter: cleared`); GPP vắng thì không gọi
  gì (hết giới hạn 1.6.0 "bộ lọc đã đặt trong process không được gỡ"; review Task 8a m8). Dòng `appsflyer started
  after consent` ghi `consent tier=<tầng> in|outside GDPR scope` thay `tier=<tầng>` (ngoài GDPR lần mở đầu tầng còn
  UNKNOWN là đúng thiết kế `outsideGdprGrantsAll`, review Task 8b).
- AppsFlyer GPP opt-out gọi `setSharingFilterForPartners("all")` thay `setSharingFilterForAllPartners()` (deprecated từ
  AppsFlyer 6.4; javap 6.16.2: hàm cũ gọi đúng hàm mới với "all") — hành vi không đổi, hết cảnh báo deprecation.
- Kill switch `logevent` bật trong lúc event server đang giữ chờ consent (1.6.0 `TelemetryConsentGate`): lúc xả các event đó bị bỏ (`onResult(false)`), không gửi — trước đây gửi hết (review cuối 1.6.0 m4). Event phát sau kill switch không đổi (bỏ ngay).
- `consumer-rules.pro` thêm `-assumevalues class kotlinx.serialization.internal.CachingKt { static boolean useClassValue return false; }` (1.6.1 G2): R8 9.2 outline lời gọi `ClassValue.get` (API 34) của `ClassValueCache`/`ClassValueParametrizedCache` ra lớp tổng hợp dùng chung `AndroidAttribution$$ExternalSyntheticApiModelOutline0` của Unity Ads (đã có từ 1.6.0, keep của 109926d không chặn được đường này), nên API 28/26 in `ClassNotFoundException java.lang.ClassValue`, API 28 thêm tranh khoá 147 ms; nay nhánh ClassValue là mã chết, mapping không còn chữ `ClassValue` và dex không còn tham chiếu kiểu `java.lang.ClassValue` (chỉ còn chuỗi `"java.lang.ClassValue"` của `Class.forName` trong `CachingKt.<clinit>`, lỗi được thư viện tự bắt). Từ API 34 kotlinx.serialization dùng `ConcurrentHashMapCache` sẵn có của nó thay `ClassValue`. Ba keep ClassValue* cũ giữ lại làm lớp phòng hờ; test soi trường `useClassValue` (phải còn `private static final boolean`) và `tools/release_gate.sh` FAIL khi mapping release còn chữ `ClassValue`. App host tự kiểm trên mapping release của mình: `grep -c ClassValue mapping.txt` phải là 0 (rule chỉ khớp khi kotlinx.serialization của host còn trường đó — đúng cho 1.4.1–1.8.1 — và host không keep cả `kotlinx.serialization.**`). Sửa chú thích cũ: kotlinx.serialization do chartboost-sdk và vungle-ads kéo theo, không phải firebase-sessions.

### Thêm

- `tools/device_gate.sh` thêm chặng "cold start 2 (non-EEA)" sau monkey: `pm clear` rồi mở app không `force_eea` để cổng analytics mở ngay và AppsFlyer `init` trong cửa sổ đo; đếm `appsflyer started` (cần ≥ 1, `AF_STARTED_MIN`) và StrictMode `by sdk` (cần 0) — G2 đo được đường `af.init` (trước 1.6.1 chặng EEA duy nhất không bao giờ tới `af.init`; review Task 8a P1). `tools/device_gate_allowlist.txt` thêm 1 dòng cho `com.appsflyer.lvl.AppsFlyerLVL` (module LVL tuỳ chọn của AppsFlyer, không có trong AAR 6.16.2, AppsFlyer tự bắt — không phải R8 strip; review Task 2+3).
- `tools/device_gate_allowlist.txt` thêm 2 dòng cho API ≤ 28 (1.6.1 G1): `com.miui.referrer.api.GetAppsReferrerStateListener` (Xiaomi GetApps referrer tuỳ chọn của AppsFlyer, không có trong AAR 6.16.2 lẫn `releaseRuntimeClasspath`, AppsFlyer dò bằng `Class.forName` trong try/catch — không phải R8 strip) và cảnh báo ART `Long monitor contention … at void java.lang.ClassNotFoundException.<init>` (chỉ là tranh chấp khoá, neo đúng dạng ART). `NoClassDefFoundError: java.lang.ClassValue` KHÔNG được allowlist: R8 gộp outline API `ClassValue.get` của kotlinx.serialization vào lớp outline dùng chung (cùng họ lỗi đã sửa ở `consumer-rules.pro`); đã sửa ở G2 (mục Sửa, rule `-assumevalues` cho `CachingKt.useClassValue`), cổng API 28/26 PASS.

### Giới hạn đã biết

- Đổi GPP opt-out giữa phiên khi AppsFlyer không start lại vẫn chỉ áp từ lần start kế (tài liệu AppsFlyer).
- Host tự gọi `setSharingFilterForPartners(...)` của AppsFlyer: bộ lọc đó bị "all" của SDK thay khi người dùng Mỹ
  opt-out (từ 1.6.0) và không được khôi phục khi SDK gỡ lúc start lại trong cùng process; host cần đặt lại sau khi
  AppsFlyer start.
- App đang dùng (Speaker Clean, Invoice Maker) hôm nay dựng `:adsplugin` chép riêng, không có AppsFlyer: lợi ích P1
  (`PrefsWarmup` nạp sẵn `appsflyer-data`, `af.init` hết đọc đĩa trên main) chỉ có hiệu lực sau khi hai app này chuyển
  sang dùng SDK `com.app.plugin:appplugin` và khai báo `af_app_id` (review Task 1 i2).

## [1.6.0] - 2026-09-28

"Đóng O1/O3/O7/O9 của plan 1.5.0, GPP opt-out bang Mỹ, sửa G9 bước rect" (plan
`docs/superpowers/plans/2026-09-28-sdk-1.6.0-plan.md`, DOC/65 §5 "Trạng thái 1.6.0"): Brain reward không còn thừa hưởng
lượt hiện cũ (O9); bốn hàm load (`loadRewardedForThisTurn`, `loadNativeFullForThisTurn`, `loadNtClForThisTurn`,
`loadRect4Show`) hết im lặng khi gọi trùng lúc đang load hoặc bị `cf not` (O7 (a)); telemetry server giữ event tới khi
có tầng consent, đo `pre_consent_lost` (O3 (a)); GPP (`IABGPP_*`, opt-out bang Mỹ) chuyển cho Firebase Consent Mode,
AppsFlyer, MAX, Meta AN + facebook-core (O2); `clearActivity` nhả `cbLoad` của placement rảnh và bọc callback của lượt
đang chạy khi host bị huỷ, rồi vá thêm đường giữ Activity qua Full2 native full dựng sẵn (G9 bước rect, 1.6.0 Task
3/4/5/10). **API public/protected không đổi** (`tools/api_dump.sh --check`, `tools/api_facade_check.sh` so với
`a76f77e`; lớp mới đều `internal`: `CfNotDispatcher`, `FanOutLoadCb`, `LoadCallbacks`, `OneShotLoadCb`, `GppConsent`,
`GppSignals`, `TelemetryConsentGate`; khối lớp sẵn có chỉ thêm 15 dòng `…$appplugin(…)` của thành viên `internal` mới
(vd `releaseNativeAdView$appplugin()` trên `AdsMobNtFullView` — lớp `internal`, javap thấy public) — không phải bề mặt API mới).

**Đã kiểm (DOC/65 §5 "Trạng thái 1.6.0"):** unit 1498/1498 ×3 ở Task 9 (`t160-t9-full-{1,2,3}.log`) → 1505 sau Task 10
(+7 `NtFullPreparedViewReleaseTest`, `t160-t10-full.log`) → **1509** sau review fix (+4 `NtFullPreparedViewOrderTest`,
`t160-t10fix-full.log`), 0 fail, 0 skip; `tools/api_dump.sh --check` → `API unchanged (4484 lines)`;
`tools/api_facade_check.sh` PASS (9 khối `internal` mới cho phép); D7 mọi commit `a76f77e..f183dcb` (32 commit) dump
PASS=32 facade PASS=32 (`t160-t9-d7.log`); cổng release PASS (APK sha256 `f7f182adbd4bb6867e5…`, 9 ad unit, 98% lớp SDK
đổi tên, zipalign 16 KB) và PASS lại tại `ffe0505` sau Task 10 (`t160-t10-release-gate.log`, cùng số đo); cổng thiết bị
`EVENTS=1500` PASS trên emulator API 37 (TotalTime trung vị 1003 ms, SDK main-thread trung vị 31 ms), API 28, API 26
(StrictMode `by sdk=0`, FATAL/`am_anr`/native = 0), và trên cả ba API sau consent cả 8 định dạng `tryLoad ok`; sau Task
10, `EVENTS=600 tools/device_gate.sh` PASS lại tại `ffe0505` trên emulator API 37 (`t160-t10-device-gate.log`:
TotalTime 1181 ms, StrictMode 0, FATAL/`am_anr`/native 0); `:lintDebug` 0 lỗi (không đổi so với `a76f77e`). Telemetry
(emulator, bản debug + sink cục bộ): POST trước consent 0; Consent → 17 event giữ được xả; "Do not consent" → 0 trong
17. GPP 4 consumer (emulator, debug + release, `--ez force_us true`): opt-out chuyển đúng cho Consent Mode, AppsFlyer,
Meta AN (MAX bỏ qua vì demo không có devKey — chỉ unit, O10); tầng không đổi (`GRANTED` trước/sau); banner `tryLoad
ok`. O7 trên máy (release, Load Rect ×2 cách 296 ms): 2 `loaded`, 0 `load timeout`. G9 bước rect, đo lại sau Task 10
(bản debug, emulator API 37, xoay `settings put system user_rotation`; `t160-t10-emu37-g9.log`): Activities sau rect
**3** (≤ 3, không đổi so với Task 9); sau cả luồng Full2 **5** (≤ 5, từ 6 ở Task 9); đường class A (qua
`com.app.plugin`) = **0/0/0** ở ba lần dump a2/b2/c2 (từ 1 ở Task 9); lượt Full2 tiếp theo hiện lại quảng cáo đã giữ mà
không nạp lại (`tryLoad has loaded`), impression 1, dismissed 1; FATAL/WindowLeaked/BadToken/`am_anr` = 0. Máy thật:
Redmi 65c9b673 API 34 PASS (`t160-t9-real-65c9b673*.log`: R8=0, `by sdk=0`, FATAL/native/`am_anr`=0, xoay khôi phục
đúng, gói gỡ sạch); Galaxy A25 (R5CX10P9EKZ) API 36 PASS trên APK `ffe0505` (`t160-t9-real-R5CX10P9EKZ-gate.log`:
TotalTime 1129 ms, StrictMode `by sdk=0`, FATAL/native/`am_anr`=0 ×4 giai đoạn, xoay không đổi so với trước, gói gỡ
sạch, sha256 APK trước/sau khớp).

**Chưa kiểm:** API 30/33; MAX + GPP trên máy (demo không có `admax_devKey`; chỉ unit test — O10); kịch bản tay khác
trên máy thật ngoài checklist R-12 (bốn nút inline); CI (nhánh chưa push). **Chưa tag.**

### Phải làm khi nâng cấp

- API public/protected không đổi: app không phải sửa code.
- Callback load (`cbLoad`) của placement không đang load bị bỏ khi Activity chủ bị huỷ (`clearActivity`), như `cbShow` từ
  1.4.0: `onLoaded` tới sau đó (banner sau `load timeout`, GMA refresh view đã gỡ) không còn tới callback đăng ký từ
  Activity đã huỷ — đăng ký lại trong Activity mới bằng `showBanner`/`showRect`/`load…ForThisTurn` (vốn phải gọi để gắn
  view hoặc nạp lại). Placement đang load giữ callback: request đang chờ vẫn nhận đúng một callback kết thúc.
- App gọi `loadRewardedForThisTurn`, `loadNativeFullForThisTurn`, `loadNtClForThisTurn`, `loadRect4Show` có callback: từ
  1.6.0 callback **luôn** kết thúc (kể cả `cf not` và khi gọi trùng lúc đang load). App gọi lại load ngay trong
  `onLoadFailed` phải bỏ qua khi `err == "cf not"` hoặc hẹn giờ (lời gọi lồng được post sang vòng kế, không đệ quy đồng
  bộ, nhưng lặp mãi nếu app cứ gọi lại). Hai app đang dùng gọi `loadRewardedForThisTurn(pl)` không callback: không ảnh
  hưởng. Khử trùng theo object: cùng một object callback truyền nhiều lần trong một lượt đang chạy nhận **một** kết thúc
  cho cả lượt (như 1.5.0); mỗi object **khác** gọi trong lúc đang load nhận kết quả riêng — app tạo callback mới mỗi lần
  bấm nút (vd lambda hiện quảng cáo trong `onLoaded`) nay nhận N `onLoaded` thay vì 1, `showRewarded` từ lần thứ hai trả
  `false`: chặn nút trong lúc chờ hoặc dùng một object. Chỉ cổng `cf not` post lời gọi lồng; cổng tắt quảng cáo
  (`… isremoveads or disable`) và 'chưa có host' vẫn báo đồng bộ như 1.4.0 — load lại ngay trong `onLoadFailed` ở hai
  cổng đó vẫn đệ quy.
- `AdsPlacementConfig.cbLoad` có thể là wrapper nội bộ (fan-out khi gọi load trùng lúc đang load, `OneShotLoadCb` sau
  `clearActivity`), kể cả sau khi lượt đã kết thúc, chứ không phải object app đã truyền — không đọc, so sánh hay gán
  `cbLoad`.
- App bật DataBucket (`DATABUCKETS_BASE_URL`): `first_open`/`session_start` chỉ tới server khi người dùng đồng ý (hoặc ngoài
  GDPR): người thoát trước khi trả lời mà không quay lại và người từ chối không còn hai event này.
  `session_start.pre_consent_lost` = **số event** (mọi loại, không phải số người) các process trước giữ mà không kịp gửi,
  chỉ người quay lại và đồng ý mới báo; không cộng thẳng vào số `first_open`. Event có `ts` lúc phát nhưng tới server sau
  câu trả lời (vài giây tới vài phút). App bật DataBucket phải chạy luồng consent của SDK (`ConsentManager.gatherConsent`/`request`): chỉ luồng
  này đổi tầng consent của SDK. Không chạy thì tầng mãi `UNKNOWN`: event server bị giữ (trần 50) và không bao giờ gửi,
  kể cả khi CMP khác đã ghi `IABTCF_*` với người đồng ý. Ngoại lệ duy nhất: `IABTCF_gdprApplies = 0` (ngoài GDPR) cho
  event đi; event đang giữ đi cùng event kế tiếp. Hai app đang dùng không bật DataBucket: không ảnh hưởng.
- **GPP (opt-out theo luật bang Mỹ, 1.6.0 Task 8a):** (1) Bật thông điệp **US states** trên AdMob console (Privacy &
  messaging) cho từng app — không bật thì UMP không ghi `IABGPP_*` và 1.6.0 không có gì để áp (hành vi như 1.5.0). (2) Với
  người Mỹ opt-out: Google Ads remarketing/conversion modelling giảm (Consent Mode denied), AppsFlyer ngừng chia sẻ
  postback với mọi partner (attribution cho nhóm này thiếu), Meta LDU giới hạn xử lý dữ liệu, MAX không bán dữ liệu — số
  liệu theo bang có thể đổi. (3) App tự gọi `setDoNotSell`/`setDataProcessingOptions`/`setSharingFilter*` phải bỏ để không
  xung đột với SDK. (4) Bộ lọc AppsFlyer chỉ áp từ lần start kế nếu người dùng đổi opt-out giữa phiên.
- **GPP: phải có mục "Privacy options" (1.6.0 Task 8b, emulator API 37):** UMP không hiện form US-state lúc mở app
  (`NOT_REQUIRED`, tầng `GRANTED`, chỉ ghi `IABGPP_*` "không áp dụng"; đo trên emulator với debug geography US,
  `t160-t8-emu-us.log`); người Mỹ chỉ chọn "Don't sell or share my data" qua
  form "My data preferences" mở bằng `ConsentManager.showPrivacyOptions`, và `ConsentManager.isPrivacyOptionsRequired`
  trả `true` cho vùng này. App không có mục Privacy options khi `isPrivacyOptionsRequired` = `true` thì người Mỹ không có
  đường opt-out và 1.6.0 không có gì để áp.

### Đổi

- `clearActivity` (host bị huỷ, kể cả xoay màn hình) nhả `AdsPlacementConfig.cbLoad` của mọi placement không đang load
  (`isLoading = false`), sau hai lượt nhả/clear mạng, cạnh `cbShow` rảnh; log `ads helper clearActivity: released <s>
  idle show callbacks, <l> idle load callbacks` (G9 bước rect, 1.6.0 Task 4). Heap emulator API 37 (bản debug, cờ
  `gma_load_app_context` tắt): trước đây `rect_default.cbLoad → MainActivity$DemoLoadCb.this$0` giữ Activity đã huỷ; ở
  màn nội dung không nạp quảng cáo toàn màn hình (host "sạch": Load Rect → Show Rect → xoay 2 lần) đó là người giữ duy
  nhất — nay Activity đó được GC (`Activities` 4 → 3). Interstitial/rewarded/app open/native/native collapse/native full
  không đổi gì app thấy (callback chỉ được gọi trong lượt của nó); banner/rect mất `onLoaded` muộn (rect: cả
  `onLoadFailed(…, "loadRectCircle onLoadFailed refresh")` do GMA tự refresh view đã gỡ) cho callback của host đã huỷ (mục
  "Phải làm khi nâng cấp"). Hai app đang dùng truyền `cbLoad = null` cho `showBanner`, gọi `loadRewardedForThisTurn(pl)`
  không callback, không gọi API rect; Speaker Clean truyền callback cho `loadOpenAdForThisTurn(PLFullSplash, …)` — app open
  chỉ gọi callback trong lượt của nó: không đổi gì.
- `loadRewardedForThisTurn`, `loadNativeFullForThisTurn`, `loadNtClForThisTurn`, `loadRect4Show` (và `showRect` khi
  placement đang load): gọi khi placement **đang load** thì callback được gắn vào lượt đang chạy và nhận đúng kết quả của
  lượt đó (`onLoaded`/`onLoadFailed`/`load timeout`, mỗi callback đúng một lần); placement bị tắt từ xa
  (`flagShow < 0`, `cf not`) báo `onLoadFailed(…, "cf not")` ngay trên luồng gọi, như interstitial/app open. Trước đây cả hai cổng im lặng
  (O7 (a) của plan 1.5.0; CHANGELOG 1.4.0/1.5.0 "Giới hạn đã biết"). `showRect` khi đang load không còn ghi đè callback của
  `loadRect4Show` trước đó (kể cả khi container đã gắn view); có rect trong cache thì callback của lần gọi này nhận
  `onLoaded` ngay, callback của lượt đang chạy nhận kết thúc của lượt (1.6.0 Task 5).
  Riêng rect: GMA tự refresh quảng cáo của cùng lượt vẫn gọi thêm `onLoaded` tới callback của lượt như trước — nay tới mọi
  callback đã gắn vào lượt đó.
- `clearActivity` bọc callback load của placement **đang load** (`OneShotLoadCb`, internal): lượt đó vẫn chuyển đúng một
  callback kết thúc, rồi SDK bỏ tham chiếu tới callback (thường giữ Activity đã huỷ); lời gọi muộn sau đó (banner, GMA
  refresh rect) không tới callback đó, như callback rảnh từ Task 4. Log `ads helper clearActivity: <n> in-flight load
  callbacks released after terminal` khi n > 0 (1.6.0 Task 5, review Task 4 I1).
- Telemetry server (app bật DataBucket): event phát khi chưa có tầng consent dùng được (`UNKNOWN` trong GDPR, hoặc
  `IABTCF_gdprApplies` vắng) được **giữ** (trần 50, bỏ cũ nhất trừ `first_open`/`session_start`) và chỉ gửi khi tầng thành `GRANTED`/`PARTIAL` hoặc ngoài
  GDPR; tầng `DENIED` thì bỏ. `first_open`/`session_start` của người EEA nay đi **sau** câu trả lời consent; `session_start`
  mang `pre_consent_lost` = số event process trước giữ mà không kịp gửi (số event mất trước consent trên mỗi cài đặt đã đồng
  ý: `sum(pre_consent_lost) / count(first_open)` — không phải tỷ lệ người mất). Event phát sau khi đã có tầng không đổi (O3
  (a), DOC/65 §7 #17). Thoát nhiều lần liên tiếp mà chưa trả lời thì số đếm cộng dồn: `session_start` mang số cũ bị giữ rồi
  mất thì số đó vẫn nằm trong khoá prefs `telemetry_pre_consent_pending` và được báo ở lần mở kế (emulator: process 1 mất 17
  → `session_start` process 2 mang `pre_consent_lost=17`). Log tag `AppPlugin`:
  `telemetry consent gate: hold <event> (held=N, consent tier=… GDPR scope)`, `flushed N`, `dropped N (tier=DENIED)`,
  `evicted 1 (cap 50)`. Emulator API 37 (bản debug demo, sink cục bộ): POST trước consent 0; Consent → 17 event giữ được
  gửi; "Do not consent" → 0 trong 17 (1.6.0 Task 6). Tràn trần không đẩy `first_open`/`session_start`; event bị đẩy được
  cộng vào `pre_consent_lost` của lần mở kế. Tầng hạ từ `GRANTED` về `UNKNOWN` giữa phiên (`refreshTierFromCache` khi không
  còn câu trả lời trên máy) thì event lại được giữ tới khi có tầng.
- Opt-out theo luật bang Mỹ (GPP, thông điệp US-state của UMP): người dùng chọn "Do not sell/share" → Firebase Consent
  Mode `ad_user_data`/`ad_personalization` denied (`analytics_storage`/`ad_storage` theo tầng như cũ), AppsFlyer
  `setSharingFilterForAllPartners()` trước start, MAX `AppLovinPrivacySettings.setDoNotSell(true)` trước khởi tạo, Meta
  Audience Network và facebook-core `setDataProcessingOptions(["LDU"], 0, 0)` trước khởi tạo. Tầng consent (`ConsentTier`)
  và luật `tierAllows` không đổi; quảng cáo vẫn hiện (GMA tự đọc GPP). Không có GPP (thông điệp US chưa bật, hoặc người
  dùng ngoài Mỹ) → không gọi API nào (O2, DOC/65 §7 #14). Có GPP mà không opt-out → MAX `setDoNotSell(false)`, Meta
  `setDataProcessingOptions([])`, Consent Mode theo tầng như cũ. Đổi lựa chọn qua Privacy options mà tầng không đổi (vẫn
  `GRANTED`, `ConsentState.onChange` không bắn): listener UMP của SDK đọc lại `IABGPP_*` và áp lại Consent Mode, MAX, Meta
  AN, facebook-core đã khởi tạo. `IABGPP_*` đọc ở đường consent (listener tầng/UMP, cổng analytics, khởi tạo mạng) như
  `IABTCF_*`; facebook-core đọc trên luồng nền của nó. Log tag `AppPlugin`: `analytics consent applied: … gpp=optOut|ok|none`,
  `appsflyer sharing filter: all partners`, `ads max: doNotSell=…`, `ads fb: LDU on|off`, `meta sdk: LDU on|off`,
  `consent updated, tier unchanged: gpp=…`. Báo cáo `MonetizationSdk.configure` có thêm cảnh báo `GPP: US opt-out đang áp
  (…)` khi SDK đang áp opt-out (`warnings`, `Report` không đổi) (1.6.0 Task 8a).

### Sửa

- Brain: mỗi quyết định mới bắt đầu với `pendingHadValue = false`; quyết định NO_ACTION kế sau một `onInterstitialShown()`
  không có quyết định treo (người ngoài rollout, hoặc app tự gọi) nay chốt `brain_feedback.reward_value` 0,6 thay vì 1,0
  (O9 của plan 1.5.0). Brain mặc định tắt; hai app đang dùng không bật.
- `brain_decision`: event sống mang `user_id` lúc ghi (cùng giá trị với trường 13 của dòng decision log và với replay),
  không còn id hiện hành lúc dựng body — `configure { playerId }` chạy giữa hai thời điểm không làm hai bản lệch id (N3 của
  DOC/65 §5; O1 (b)).
- Native full AdMob: dialog Full2 dựng sẵn sau quảng cáo cha (`prepareChainedShow`) gắn `NativeAd` vào `NativeAdView` của Activity đang hiện;
  khi dialog bị bỏ mà quảng cáo được giữ (host huỷ lúc cha đang hiện, chuỗi không nối, đổi host lúc show, hoặc dựng dialog lỗi sau khi đã gắn quảng cáo) GMA vẫn giữ view từ `NativeAd` →
  Activity cũ sống tới khi quảng cáo được hiện/huỷ (heap 1.6.0 Task 9 hàng 13: đường A `AdsEcmp.adObject → NativeAd → NativeAdView.mContext`).
  Nay `discardPreparedDialog` gọi `NativeAdView.destroy()` (đối xứng `unregisterView()` bên Meta); quảng cáo vẫn giữ, hiện lại trên host mới
  không nạp lại. Lỗi có từ 1.5.0 (`cfb9726`).

### Thêm

- `tools/api_facade_check.sh`: baseline `a76f77e` (1.5.0); khối lớp mới ở gói bất kỳ chỉ được phép khi khai báo nguồn là
  `internal`/`private` (Task 0). `SdkFileSizeGateTest`: mọi file SDK ≤ 800 dòng (`LogEventServer.kt` ≤ 970, `AdsHelper.kt` ≤ 700).
- App demo, chỉ bản debug: meta-data `DATABUCKETS_BASE_URL` = `http://10.0.2.2:8089/` (sink HTTP cục bộ trên máy dev, nhìn
  từ emulator) và cleartext chỉ cho `10.0.2.2` (`app/src/debug`), để đo telemetry trước consent. Bản release không đổi.
- `GppConsent` (`internal`): đọc `IABGPP_HDR_GppString`/`IABGPP_GppSID` do UMP ghi và giải mã opt-out bán/chia sẻ/quảng
  cáo nhắm mục tiêu của US National (7) và CA/VA/CO/UT/CT (8–12) (Task 7); nối vào Firebase Consent Mode, AppsFlyer, MAX,
  Meta ở Task 8 (xem `### Đổi`).
- `ConsentManager`: system property `appplugin.debug_geography` (`"us"`/`"other"`) ép geography UMP debug (US-state /
  khu vực khác) khi `testIds` không rỗng, áp dụng sau `debugForceEea` (thắng nếu cả hai cùng đặt); demo nhận
  `--ez force_us true` để thăm dò sớm form US-state (1.6.0 Task 8-0).

### Giới hạn đã biết

- **Activity cũ còn bị giữ sau Full2 và bước rect** (G9 "1 Activity sau GC" chưa đạt): emulator API 37, bản
  debug, cờ `gma_load_app_context` tắt. 1.6.0 Task 4 nhả `cbLoad` của placement rảnh trong `clearActivity` (heap trước
  đó: 1 → 3 → 5, mọi Activity chết khi đó `[gma]=false`, `t160-t4-emu-g9-flow.log`). Đo lại ở Task 9 (bước rect: Load/
  Show Rect → xoay 2 lần → luồng Full2, `t160-t9-emu37-g9.log`): Activities sau rect 3 (≤ 3), sau cả luồng **6** (> 5
  **FAIL**); một đường class A (qua `com.app.plugin`) qua Full2 native full dựng sẵn giữ Activity:
  `AdsMobPlNtFull.adsEcmpShow → AdsEcmp.adObject → NativeAd → NativeAdView.mContext` (có từ 1.5.0, `cfb9726`). Vá ở
  1.6.0 Task 10 (`discardPreparedDialog` gọi `NativeAdView.destroy()`, xem "Sửa"); đo lại (`t160-t10-emu37-g9.log`):
  Activities sau rect vẫn **3** (≤ 3 PASS), sau cả luồng **5** (≤ 5 PASS, từ 6); đường class A = **0/0/0** ở ba lần
  dump (a2/b2/c2); lượt Full2 kế tiếp hiện lại quảng cáo đã giữ mà không nạp lại; FATAL/WindowLeaked/BadToken/`am_anr`
  = 0. **Còn lại toàn bộ là class C** (GMA nạp quảng cáo bằng Activity, #18) — sửa gốc là nạp GMA bằng
  `applicationContext` (cờ `gma_load_app_context`, DOC/65 §7 #18), chưa bật mặc định, chưa đo trên máy với cờ bật.
- `showRect` gọi `onLoaded` đồng bộ trên luồng gọi khi có rect trong cache (có từ trước). Lượt đang chạy — do
  `loadRect4Show` hoặc `showRect` với container khác mở — không gắn view vào container của `showRect` gọi trong lúc lượt
  đó chạy: callback nhận `onLoaded`, container trống. App gọi lại `showRect(container, …)` **sau** `onLoaded`, post sang
  vòng kế (vd `container.post { … }`); không gọi lồng ngay trong `onLoaded` (lời gọi lồng lấy view từ cache rồi vòng đang
  chạy có thể chuyển view đó sang container của lượt).
- Đường load còn chỗ không callback (có từ trước, ngoài bốn hàm của O7 (a); đo ở 1.6.0 Task 5): `showRect` trên container
  **đã gắn** view khi placement không đang load; `showBanner` ở cổng `cf not`, khi banner bị gác (ẩn sau rewarded,
  cooldown màn hình), khi chưa đủ `bannerDeltaTime` mà không có vòng nào đang chạy, và khi gọi lại lúc banner đang load
  (callback mới thay callback cũ; callback cũ không nhận kết thúc của lượt).
- Event server phát **sau** `DENIED` vẫn gửi (id bí danh) như các bản trước — chờ chủ dự án (N1). Cổng telemetry chỉ bỏ
  event phát **trước** câu trả lời; mở lại app với tầng `DENIED` đã lưu vẫn gửi `session_start` (emulator: 1.6.0 Task 6).
  Bản ghi decision log (`brain_decision`) bị bỏ vì `DENIED` hay tràn trần vẫn nằm trên đĩa và được replay — gửi — ở lần mở
  sau khi tầng `DENIED` đã lưu.
- Người ngoài EEA cài mới mà lần cập nhật UMP đầu tiên lỗi mạng: event của process đó bị giữ tới khi process chết (mất
  `first_open`), được đếm vào `pre_consent_lost` của lần mở kế.
- GPC (Global Privacy Control) trong section GPP chỉ ghi log, không quyết định (N3).
- MAX và facebook-core với GPP chỉ có unit test (demo không có `admax_devKey`, không khai facebook-core — O10); Meta
  Audience Network có bằng chứng emulator (`ads fb: LDU on|off`, 1.6.0 Task 8b).
- GPP opt-out đổi giữa phiên: AppsFlyer chỉ áp bộ lọc từ lần start kế (lần mở app sau). Bộ lọc đã đặt trong process không
  được gỡ khi người dùng thôi opt-out, kể cả khi AppsFlyer start lại sau khi rút rồi cấp lại consent (gỡ ở lần mở app
  sau). GPP biến mất sau khi đã áp (UMP
  reset, người dùng rời Mỹ) thì SDK không gọi gì: cờ MAX `doNotSell` và LDU của facebook-core (lưu trên máy) giữ tới khi
  GPP có lại (luật "không có GPP → không gọi"); LDU của Meta Audience Network chỉ nằm trong bộ nhớ, hết ở lần mở kế.
- Một section US 7–12 hỏng làm cả chuỗi GPP coi như vắng (không opt-out), kể cả khi section khác hợp lệ có opt-out
  (review Task 7 m5; `GppConsent`: chuỗi hỏng → không có GPP, một dòng logE).
- MAX + GPP: `AppLovinPrivacySettings.setDoNotSell` chạy trên main ngay trước `AppLovinSdk.initialize` (như
  `getInstance`/`initialize` sẵn có) và đọc/ghi prefs `com.applovin.sdk.shared` của AppLovin (không nạp sẵn); cổng G2
  (StrictMode) không đo được đường MAX vì demo không có `admax_devKey` (review Task 8a m3).
- `AppLovinPrivacySettings.setDoNotSell` (AppLovin 13.6.2) và `AppsFlyerLib.setSharingFilterForAllPartners` (AppsFlyer
  6.16.2) là API `@Deprecated` nhưng vẫn là lời gọi 1.6.0 dùng cho opt-out bang Mỹ; nâng hai SDK này thì kiểm lại API thay
  thế trước (review Task 8a m4).
- **AppsFlyer `af.init` gây StrictMode DiskRead mà G2 không thấy** (không phải do GPP, có từ khi `af_app_id` giới hạn ở
  bản debug, `aa4d58f`): bản debug demo (có `af_app_id`), mọi cold start có `[prefs first load]` của AppsFlyer
  (`AppsflyerHelper.startAfterConsent → af.init`) tính là 1 vi phạm do SDK (`by sdk=1`, `t160-t8b-strictmode.log`); bản
  release dùng cho `device_gate` không có `af_app_id` nên AppsFlyer không chạy `init` và G2 báo `violations=0` — không đo
  được đường này. Hai app đang dùng đều có AppsFlyer nên gặp vi phạm này trên máy thật (review Task 8a P1). Theo sau:
  thêm file prefs của AppsFlyer vào `PrefsWarmup.names`, hoặc gắn `af_app_id` giả cho demo release để G2 đo được đường
  này.

## [1.5.0] - 2026-09-28

"Tách AdsHelper, đóng nốt follow-up 1.4.0" (plan `docs/superpowers/plans/2026-09-27-sdk-1.5.0-plan.md`, DOC/65 G8):
`AdsHelper.kt` 4.018 → 692 dòng — façade giữ nguyên mọi hàm public/protected, thân chuyển sang 12 controller `internal`
trong `com.app.plugin.ads.core` (13 file, lớn nhất 512 dòng), hành vi giữ nguyên; **API public/protected không đổi**
(`tools/api_dump.sh --check`, `tools/api_facade_check.sh` so với `6d8f454`). Đóng final review 1.4.0 Minor 6 (rect) và
Minor 7 (MAX nhiều id), vòng banner có `loadCycle`, bỏ hai getter-null của callback load. **Đã kiểm (DOC/65 §5 "Trạng thái
1.5.0"):** unit 1360 ×3 (0 fail, 0 skip); cổng release PASS (9 ad unit, 98% lớp SDK đổi tên, zipalign 16 KB); cổng thiết bị
`EVENTS=1500` PASS trên emulator API 37, 28, 26 (StrictMode 0, chuỗi R8 0, FATAL/`am_anr`/native 0), và trên cả ba API sau
consent mọi định dạng (banner, NtCl, rect, native, interstitial, native full, app open, rewarded) nạp được sau cold start;
kịch bản tay trên emulator API 37: resume app open, rect Home → mở lại, banner mất mạng 2 phút rồi có lại, G9 (3 Activity
sau GC, chuẩn 4); `:lintDebug` 0 lỗi. Máy thật (APK `ee4991a`): `tools/device_gate.sh` PASS trên Redmi 22021211RC
(API 34, cold start 956 ms) và Galaxy A25 (API 36, 766 ms) — R8 0, StrictMode do SDK 0, monkey 3 × 1.500 FATAL/`am_anr`/
native 0; rect, banner, native, native full nạp được trên cả hai (thêm interstitial trên Redmi, rewarded và Full2 trên
A25), `load_fail_but_ads_not_null` 0. **Chưa kiểm:** NtCl, show rewarded/app open và kịch bản tay trên máy thật, MAX trên máy (demo không có key), nhánh `load timeout` của vòng banner (chỉ unit test). **Chưa tag.**

### Phải làm khi nâng cấp

- API public/protected không đổi: app không phải sửa code (dump `api/appplugin.api` chỉ thêm khối lớp
  `com.app.plugin.ads.core.*`, lớp `internal` mà javap thấy là public — app không được gọi các lớp này).
- **Chỉ khi app có `admax_*` với placement nhiều id (`pl,idA;idB`):** từ 1.5.0 mỗi bước waterfall MAX request đúng unit của
  mục đó (trước đây mọi bước request unit đầu, mục sau kẹt 'đang load') — xem lại thứ tự id trước khi nâng cấp. Không để
  hai placement rewarded dùng chung một ad unit MAX ở bất kỳ vị trí nào trong danh sách (xem "Giới hạn đã biết"). App không
  dùng MAX hoặc mỗi placement một id MAX: không cần làm gì.

### Đổi

- `AdsHelper`: vùng telemetry load/impression (`onAdLoadStart`/`onAdLoaded`/`onAdLoadFail`/`onAdImpression`, map đo thời gian load, rút gọn tên event) chuyển sang `ads.core.AdsTelemetry` (Task 1); móc Full2 `native_full` của `onAdLoaded` ở lại façade, chạy trước telemetry; API không đổi.
- `AdsHelper`: luật tần suất (`adsPolicy`, `applyPolicy`, cooldown N màn hình, `recordScreenChange`) và 7 cổng thời gian (open/interstitial load + show/ntcl/banner/Full2/rewarded) chuyển sang `ads.core.AdsFrequencyGate` (Task 2); `postAdCooldown`, `fullScreenAdPolicy` và các mốc `*TimeShow` ở lại façade; API không đổi.
- `AdsHelper`: vùng rect (`showRect`, `hideRect`, `loadRect4Show`, `rectTimeRefresh`, vòng load rect, map view rect theo container) chuyển sang `ads.core.RectController` (Task 3); `clearActivity` gỡ view rect qua `RectController.onHostCleared()` đúng vị trí cũ; thân chết của `refreshRect` (sau `return` vô điều kiện) bị xoá, rect vẫn không tự refresh; API không đổi.
- `AdsHelper`: vùng native collapse (`createAndAddNtClParentView`, `bringLayoutCl`, `showNtCl`, `loadNtClForThisTurn`, `hideNativeCl`, vòng show/load NtCl, cờ `ntClIsShow`, container `ntclParentView`) chuyển sang `ads.core.NativeCollapseController` (Task 4); mốc `clTimeShow` ở lại façade; `clearActivity` gỡ và bỏ container NtCl qua `NativeCollapseController.onHostCleared()` đúng vị trí cũ; API không đổi.
- `AdsHelper`: vùng native (`showNative`, `loadNative4Show`, `hideNative`, layout/màu tuỳ biến `setLayoutCustome4Native`/`getLayoutCustome4Native`/`setCustomeView4Native`/`removeCustomeView4Native`/`getNativeCustom`, vòng show/load native kể cả dùng lại quảng cáo nguồn, map view native theo container) chuyển sang `ads.core.NativeController` (Task 5); `clearActivity` huỷ quảng cáo native từng view và xoá map qua `NativeController.onHostCleared()` đúng vị trí cũ; API không đổi.
- `AdsHelper`: vùng banner (`showBanner`, `hideBanner`, `isBannerHiddenByReward`, `grantBannerHideReward`, `retryBannerNow`, `bannerTimeRefresh`, vòng waterfall + watchdog kẹt 30 s + thử lại 5/15/45 s + refresh, hẹn hiện lại sau phần thưởng "tắt banner", chỗ đặt/view đang hiện) chuyển sang `ads.core.BannerController` (Task 6); `bnIsShow`, `bnFlagRefresh`, mốc `bnTimeShow` và `DEFAULT_BANNER_HIDE_MILLIS` ở lại façade; `clearActivity` ẩn banner, gỡ view và bỏ tham chiếu container qua `BannerController.onHostCleared()` đúng vị trí cũ; thêm 8 test đặc tả vòng banner (`BannerControllerTest`); API không đổi.
- `AdsHelper`: vùng native full (`loadNativeFullForThisTurn`, `getNativeFullForShow`, `destroyNativeFull`, vòng load native full, danh sách mạng theo `flagShow`, map view native full → quảng cáo `ntFullMapView`) chuyển sang `ads.core.NativeFullController` (Task 7); mạng đọc qua `fullNetwork(type)` của façade, hạ cờ "đã load" dùng chung `dropStaleLoadedFlag` (đổi `private` → `internal` tại chỗ); `clearActivity` xoá map native full qua `NativeFullController.onHostCleared()` đúng vị trí cũ; thêm 3 test đặc tả (`NativeFullControllerTest`); API không đổi.
- `AdsHelper`: vùng app open (`loadOpenAdForThisTurn`, `setAfterFirstOpenShow`, `isFirstOpenReady`, `showFirstOpen`, `showOpenAd`, vòng load/show app open kể cả giao thức `flagAdsFull4ShowFaild`/`flagAdsFull4Dissmiss` và `WaterfallShowFailHold`, danh sách mạng theo `openFlagFirst`, cờ `openFlagFirst`/`isWaitReleadOpenAd`) chuyển sang `ads.core.AppOpenController` (Task 8); `openFlag4Resume`, `openTimeShow`, `suppressOpenAdUntilMillis`, cờ click `flagAdClick` ở lại façade (`setFlag4ShowResume`/`getFlag4ShowResume`/`isFullShowing`/`isClickAds` giữ thân tại façade); `finishFull2Transition` đổi `private` → `internal` tại chỗ; `setMainActivity` gọi `AppOpenController.onHostSet()` đúng vị trí khối cũ; thân chết của `reloadSplash` (sau `return` vô điều kiện) bị xoá, splash vẫn không tự nạp lại; thêm 13 test đặc tả (`AppOpenControllerTest`); API không đổi.
- `AdsHelper`: vùng rewarded (`loadRewardedForThisTurn`, `showRewarded`, vòng load/show rewarded kể cả phần thưởng `AdsGiftShowCb.onAdRewarded`, giao thức `flagAdsFull4ShowFaild`/`flagAdsFull4Dissmiss` và `WaterfallShowFailHold`, nối Full2 sau rewarded khi `full2FlagShow` 2/3, danh sách mạng theo `giftIdxShow`) chuyển sang `ads.core.RewardedController` (Task 9); `giftTimeShow`, `giftIdxShow`, `giftCount4Circle`, `listAdsGift` ở lại façade; `skipLoadWithoutHost`, `beginFull2Transition`, `loadFull2ForThisTurn` đổi `private` → `internal` tại chỗ; thêm 17 test đặc tả (`RewardedControllerTest`); API không đổi.
- `AdsHelper`: phần load/ready của interstitial (`loadInterstitialForThisTurn`, `isInterstitialLoaded`, `isInterstitialReadyToShow`, vòng load interstitial kể cả guard `loadCycle`, hạ cờ "đã load" cũ `dropStaleLoadedFlag` + nạp lại, danh sách mạng theo `flagImage`/`fullIdxShow`, mạng theo type `fullNetwork`, cảnh báo gọi ngoài main) chuyển sang `ads.core.InterstitialController` (Task 10; phần show ở Task 11); `fullNetwork` và `dropStaleLoadedFlag` của façade uỷ quyền cho controller; KDoc public của `isInterstitialReadyToShow` ở lại façade; thêm 9 test đặc tả (`InterstitialControllerTest`: callback của cổng `cf not`/đang load/chưa đủ thời gian, ba guard `loadCycle`, `isLoadFull2`, cổng host resume và cooldown của `isInterstitialReadyToShow`); `AdsHelper.kt` còn 1.442 dòng (G8 mức 1.5 ≤ 1.500 đạt); API không đổi.
- `AdsHelper`: phần show của interstitial (`showInterstitial` kể cả cổng brain `AdsBrain.shouldShowInterstitial`/`onInterstitialShown`/`onAdSkipped`, vòng show `showInterstitialCircle` với giao thức `flagAdsFull4ShowFaild`/`flagAdsFull4Dissmiss` và `WaterfallShowFailHold`, dismiss → `showFull2`/`finishFull2Transition`, `beginFull2Transition` khi hiện, nạp lại `full_default`) chuyển sang `ads.core.InterstitialController` (Task 11); `fullIdxShow`, `fullCount4Circle`, `fullTimeShow`, `flagAdsFull*` ở lại façade; `getListIntersLoadShow` trả lại `private`; thêm 9 test đặc tả vòng show (`InterstitialControllerTest`, xanh trên mã cũ): brain từ chối → `false` + `show_full_brain_declined`, không gọi mạng, `isDeltaTime = false` không hỏi brain; brain đồng ý và hiện được → đếm lượt hiện; click → `onAdClicked` (chặn quảng cáo tự động khi quay lại); chuyển cảnh Full2 kết thúc ngay ở dismiss khi không nối Full2 và ở `onAdShowFail` cuối vòng; giá trị giao thức `flagAdsFull4*` = -1 và `fullIdxShow` xoay sau khi hiện; cổng "đang hiện" (`show_full_when_flag_showing`) và kẹp `flagAdsFullShowing` âm; lỗi đồng bộ → một `onAdShowFail`, nạp `full_default` rồi placement; `AdsHelper.kt` còn 1.217 dòng; API không đổi.
- `AdsHelper`: chuỗi Full2 — chuyển cảnh/cover (`setFull2TransitionListener`, `beginFull2Transition`/`finishFull2Transition`, dựng sẵn/huỷ dialog nối tiếp, hẹn giờ 3 s/1,5 s, cover mặc định `Full2DefaultCover`) và show/load Full2 (`showFull2`, vòng show `showFull2Circle` dùng chung `flagAdsFull4ShowFaild`/`flagAdsFull4Dissmiss` với quảng cáo cha — chỉ tăng và so, không đặt lại, `loadFull2ForThisTurn`, vòng load Full2, danh sách mạng theo `full2IdxShow`) chuyển sang `ads.core.Full2Chain` (Task 12); `full2MainHandler`, `pendingFull2` (`private data class PendingFull2`), `full2TransitionActive` ở lại façade (field test đọc bằng reflection) và `Full2Chain` đọc/ghi qua accessor `internal` chỉ nhận/trả thành phần; `full2TimeShow`/`full2IdxShow`/`full2Count4Circle`, `suppressOpenAdUntilMillis`, `suppressNextOpenAd`, `onAdClicked` ở lại façade; `onMainActivityResumed`/`onMainActivityPaused`/`clearActivity`/móc `native_full` của `onAdLoaded` gọi hook của `Full2Chain` đúng vị trí cũ; thêm 5 test đặc tả trình tự cover (`Full2ChainTest`, xanh trên mã cũ): `onTransitionChanged` true/false một lần và `finishFull2Transition` khi đã tắt vẫn báo false, `showFull2` trong cửa sổ `clearActivity` → `host_destroyed` không xếp và không báo false lần hai, host chưa resume → xếp rồi hiện đúng một lần khi resume, `suppressNextOpenAd` chặn không load, không có gì đã load → hạ cover và load ngay; `AdsHelper.kt` còn 817 dòng; API không đổi.
- `AdsHelper`: vòng dựng mạng của `initAdsHelper` (24 lần `AdsMobMy/AdsMaxMy/AdsFbMy.instance.getAds*` → `listAds*`/`listAdsAll.add`, giữ nguyên thứ tự — thứ tự này quyết định thứ tự `releaseActivity`/`clearActivity`/`checkLoadAfterGetCf`) chuyển sang `ads.core.AdsNetworkRegistry` (Task 13); `initAdsHelper` gọi `AdsNetworkRegistry.build()` (tham số không dùng `maxAvailable` bỏ ở Task 14 bước A; `isMaxSdkAvailable` vẫn chỉ gate `AdsMaxMy.initAd`) rồi gán 7 field `listAds*` (field R1 ở lại façade); thêm test `AdsNetworkRegistryTest`; `AdsHelper.kt` còn 692 dòng (G8 mức 2.0 ≤ 800 đạt); API không đổi.
- Callback load: vòng banner chụp `loadCycle` của placement như 8 vòng khác và chỉ gọi callback của app khi lượt còn hiện hành hoặc khi `cbLoad` được gán lại sau `load timeout` (kể cả app gán thẳng `cbLoad`, như getter cũ); slot bỏ callback ngay lúc watchdog báo `load timeout`. Bỏ hai getter-null (`AdsPlacementConfig.cbLoad`, `AdsPlacementEcmpBase.adsLoadCb`). Hành vi với app không đổi: đúng một callback kết thúc; banner vẫn tự thử lại và gắn quảng cáo tới muộn.

### Sửa

- Rect: lấy view ra hiện (`getAdForShow`) khi view đang hiện refresh chiếm mục không còn phát `LOAD_FAIL_BUT_ADS_NOT_NULL` giả (final review Minor 6; đường `load` đã hết từ 1.4.0).
- MAX interstitial/rewarded/native/NtCl/native full, placement có nhiều id MAX: mỗi bước waterfall nay request đúng unit của mục đó và ghi kết quả vào đúng mục (trước đây đối tượng dựng một lần cho unit đầu nên mọi bước request unit đầu, mục sau kẹt 'đang load'; từ lượt thứ hai unit đầu no-fill báo `isloading` rồi watchdog 60 s báo thêm `load timeout`). Đổi mục: interstitial destroy đối tượng cũ; native giữ một loader mỗi unit, không destroy loader cũ (quảng cáo nó đã render vẫn nhận click và doanh thu); rewarded lấy instance theo unit, không gỡ/destroy instance cũ (singleton theo unit). Callback load đã xếp hàng của đối tượng cũ bị bỏ; nhánh callback muộn không đổi. Danh sách MAX chỉ đọc từ `admax_*` lúc khởi tạo (`cf_ids_placement` chỉ áp cho AdMob). Thêm test `AdsMaxCreateOnceRebindTest` (final review I–M Minor 7; Task 14).

### Thêm

- `AdsNetworkRegistryTest`: test thứ tự — 24 mock có tên trong map của `AdsMobMy`/`AdsMaxMy`/`AdsFbMy` (khôi phục trong `finally`), `build()` trả `all` đúng thứ tự 24 lần `add` của `initAdsHelper` cũ và 6 map đúng khoá (review Task 13 m2: 4 đột biến sống nay đỏ; Task 14 bước A, chỉ thêm test).
- `tools/api_facade_check.sh`: cổng phụ G8 — mặt ngoài public/protected của mọi lớp có từ `6d8f454` không đổi; chỉ cho thêm lớp trong `com.app.plugin.ads.core` (Task 0).
- `tools/core_internal_check.sh` (gọi từ `api_facade_check.sh`): mọi khai báo top-level trong `ads/core/*.kt` phải `internal`/`private` — javap không phân biệt được `internal` với `public` (Task 0, review I1).
- `tools/core_internal_check.sh` chặn thêm controller đọc façade lúc dựng: field-level `h.` (trừ `get() =`), `init {`, `by lazy` trong `ads/core/*.kt` (plan R-2, review Task 2 m1); chỉ xét phần trước `{` đầu tiên của dòng field sau khi bỏ chuỗi và chú thích, `init {` tính ở cấp thành viên (review Task 3 m1).
- `NativeControllerTest`: test đặc tả chặn crash 1.4.0 `e67b3da` — `showNative` với quảng cáo không có view để gắn báo đúng một `onAdShowFail(view_attach_null_in_callback)`, không `addView`, lần sau lấy quảng cáo lại (review Task 5 m1; chỉ thêm test).
- `NativeFullControllerTest`: test đặc tả callback load native full của app — đúng một callback kết thúc mỗi lượt (`loaded` / `fail:list empty`), callback muộn của lượt đã bỏ (`loadCycle` tăng) không tới app và không đổi `isLoaded` (review Task 7 m1, plan Task 15 bước 0 (e); commit `d8d430b`, chỉ thêm test).
- `AppOpenControllerTest`: lưới cho ba lời gọi chéo vùng của vòng show app open — cooldown N màn sau quảng cáo (`markAutoAdShown` bật cooldown và ẩn banner, cổng `POST_AD_SCREEN_COOLDOWN` chặn lượt sau không gọi mạng, đổi màn thì hiện lại) và chuyển cảnh Full2 đang bật kết thúc ngay ở `onAdDismissed`/`onAdShowFail` cuối vòng (review Task 8 m3/m4; commit `2868ba3`, chỉ thêm test).
- `RewardedControllerTest`, `LoadCycleGuardTest`: mạng báo `onLoaded` đồng bộ trong `loadAd` (lối tắt "has loaded") — callback của chính lần gọi load nhận đúng một `onLoaded`, lần gọi trước không nhận thêm (khoá thứ tự gán `cbLoad` trước vòng load của rewarded và interstitial); placement rewarded tắt từ xa (`flagShow = -1`) → `showRewarded` `false` không gọi mạng, load im lặng (hành vi hiện tại, O7) (review Task 9 m2; commit `3b9fa0b`, chỉ thêm test).
- `InterstitialControllerTest` 9 → 25 test: đuôi vòng load interstitial (mọi mạng thất bại → đúng một `fail:list empty`, `isLoading` hạ, lượt kế gọi lại mạng đầu; nhánh `isLoading=false on fail`; type không có mạng → bước kế qua `Handler.post`), thứ tự mạng (`fullIdxShow`, `fullStepRe`, `flagImage` 4), nạp kèm Full2 với `full2FlagShow` 3, cổng remove-ads của load, `isInterstitialLoaded` có/không tiền tố `full_`, sáu cổng còn lại của `isInterstitialReadyToShow` và `flagImage` của lần nạp lại khi cờ đã load là cũ (review Task 10 I1: 17 đột biến sống nay đỏ; commit `815d799`, chỉ thêm test).
- `InterstitialControllerTest` 34 → 43 test: giao thức một-dismiss và chuỗi interstitial → Full2 — app nhận `showed` cha, `dismissed` cha, `showed` Full2 theo thứ tự, Full2 đóng không tới app lần hai (`flagAdsFull4Dissmiss` -9 rồi -8), chuyển cảnh còn bật tới impression của Full2; dismiss/`onAdShowFail` bất đồng bộ lặp 3 lần → một callback, placement không kẹt `isShowing`; `full2FlagShow` 0 không bật chuyển cảnh; cooldown N màn không chặn `isDeltaTime = false`; brain không được hỏi khi cooldown hoặc `flagAdsFullShowing` chặn; nhánh brain từ chối không gọi `onAdSkipped` (reward của quyết định từ chối giữ `pendingHadValue` cũ) (review Task 11 I1–I3: 10 đột biến sống nay đỏ; commit `9168896`, chỉ thêm test).

### Giới hạn đã biết

- **Máy thật chỉ chạy cổng tự động ở 1.5.0** (R-12): Redmi 22021211RC (API 34) và Galaxy A25 (API 36) cổng thiết bị PASS,
  các định dạng hiện trong màn hình nạp được (`DOC/testing/t150-realdevice-{redmi-65c9b673,a25-R5CX10P9EKZ}.log`); NtCl,
  show rewarded/app open và kịch bản tay (G9, resume, xoay, rect, banner mất mạng) chỉ chạy trên emulator API 37/28/26. CI chưa chạy trên
  nhánh (chưa push): job `verify`, kể cả bước mới `tools/api_facade_check.sh` (cần `fetch-depth: 0`), chạy lần đầu khi push.
- **MAX: chưa kiểm trên máy ở 1.5.0** (demo không có `admax_devKey`/`admax_*`); thay đổi MAX của 1.5.0 chỉ có unit test.
- **MAX rewarded:** hai placement rewarded dùng chung một ad unit MAX dùng chung một đối tượng `MaxRewardedAd` (singleton
  theo unit của AppLovin) — listener của placement nạp sau thắng, placement kia mất callback hiện/đóng/thưởng. Có từ bản
  đầu khi hai danh sách cùng bắt đầu bằng unit đó; từ 1.5.0 (mỗi bước waterfall request đúng unit của mục) xảy ra cả khi
  unit chung nằm ở vị trí sau trong danh sách và waterfall đi tới nó. Cấu hình mỗi placement rewarded MAX một ad unit riêng.
- **Rewarded:** mạng đầu của waterfall rewarded thôi xoay sau `giftStepCicle.size` lần thử đầu của process (`showRewarded`
  đặt lại `fullCount4Circle` thay vì `giftCount4Circle`; có từ bản đầu; chỉ có tác dụng khi `step_show_gift` có ≥ 2 mạng
  trong `cir:`).
- **Brain (mặc định tắt; hai app đang dùng không bật):** `AdsBrain.onInterstitialShown()` đặt `pendingHadValue = true` cả
  khi không có quyết định đang treo (người ngoài rollout, hoặc app tự gọi); quyết định NO_ACTION kế tiếp (nhánh từ chối
  của `showInterstitial` không gọi `onAdSkipped`) được chốt `brain_feedback.reward_value` 1,0 thay vì 0,6 — bandit lệch về
  phía "không hiện". Có từ trước 1.5.0; `InterstitialControllerTest` khoá hành vi hiện tại; sửa ở 2.0 (O9 của plan 1.5.0).
- **Cổng "chưa có cấu hình placement" (`cf not`) và "đang load" của `loadRect4Show`, `loadNtClForThisTurn`,
  `loadNativeFullForThisTurn`, `loadRewardedForThisTurn` vẫn im lặng** (không `onLoadFailed`), khác các cổng
  remove-ads/tắt định dạng/kill switch (mục "Load bị chặn …" ở "Phải làm" của 1.4.0).
- **Kích thước file (G8):** `AdsHelper` còn 692 dòng (✓ mức 1.5 và 2.0); `LogEventServer` 955 > 800 (ngưỡng 2.0; DOC/65 ghi
  942 là lần đếm cũ, file không đổi ở 1.5.0).
- **Activity cũ còn bị giữ sau Full2** (G9 "1 Activity sau GC" chưa đạt): emulator API 37, bản debug, luồng Full2 có xoay →
  3 Activity sau GC (1.4.0 đo 4); thêm bước rect (Load → Show Rect → xoay 2 lần) trước luồng → 3 ngay sau bước rect, 5 sau cả luồng. Đường giữ đã
  phân tích ở 1.4.0 là GMA (WebView, quảng cáo toàn màn hình/native nạp bằng Activity, DOC/65 §7 #18; cờ
  `gma_load_app_context` mặc định tắt). Bước rect cũng vậy (phân tích heap sau khi chốt bản, cùng quy trình và số đo): cả
  4 Activity cũ bị quảng cáo toàn màn hình của GMA nạp bằng Activity giữ (#18) — hai lần xoay của bước rect làm luồng Full2
  chạy trên host mới thay vì trên Activity đầu (đã bị AppOpen nạp lúc khởi động giữ), và lần xoay đầu làm demo nạp lại
  `full_splash` trong `onCreate` trên host mới. AdView rect (dựng bằng context ứng dụng, chỉ gỡ khỏi container) không giữ
  Activity nào. SDK giữ thêm Activity đầu qua `AdsPlacementConfig.cbLoad` (callback load của app; `clearActivity` nhả
  `cbShow` rảnh từ 1.4.0 nhưng không nhả `cbLoad`, có từ trước 1.5.0), song song với GMA nên không đổi số đo khi cờ tắt.
  Hai app đang dùng không gọi API rect.
- **Opt-out theo luật bang Mỹ (GPP) chưa được chuyển cho mạng bên thứ ba** (§7 #14): như 1.4.0 — SDK chưa đọc
  `IABGPP_*`; người dùng Mỹ opt-out qua thông điệp US-state của UMP vẫn được coi là cấp hết trừ `DENIED`. Không làm ở 1.5.0
  (chờ chủ dự án, O2 của plan 1.5.0).
- **Meta Audience Network qua adapter mediation chưa bị chặn** (§7 #16): adapter Meta trong AdMob
  (`com.google.ads.mediation:facebook`) và MAX (`facebook-adapter`) do MobileAds/AppLovin khởi tạo; SDK chỉ chặn đường
  khởi tạo trực tiếp (`meta an: not started …`). Chưa kiểm trên máy adapter có khởi tạo Meta cho người `DENIED` không.
- **Meta SDK (`facebook-core`) chưa dừng hẳn khi từ chối/rút consent** (mục "Meta SDK" ở "Đổi" của 1.4.0): lần mở đầu sau
  nâng cấp, và lần mở đầu sau khi rút, vẫn có thể có một lượt auto-init và app event tự động; rút consent giữa phiên không
  dừng facebook-core đang chạy; cài đặt phía server của Meta có thể đè auto-log. Chưa kiểm trên Events Manager.
- **FCM:** rút consent giữa hai lần mở app (tầng đã lưu cho phép, lần đầu của process là từ chối) không xoá token;
  token cũ vẫn nhận push tới khi `deleteToken()` thành công.
- **Kill switch tới trong phiên đầu, trước câu trả lời consent:** load đang chờ mạng khởi tạo không chạy trong phiên đó;
  app chỉ nhận `onLoadFailed` sau 60–90 giây (`load timeout` hoặc `list empty`), không có nhãn kill.
- **Telemetry trước consent** (§7 #17, P2, chưa chốt): app bật DataBucket (`DATABUCKETS_BASE_URL`) thì `first_open`,
  `session_start` vẫn gửi trước câu trả lời consent, cả cho người EEA sau này từ chối — mang id bí danh, không ANDROID_ID.
- **`user_id` chưa ổn định trước lần đầu app truyền id** và **id app đã lưu không có đường xoá**: như 1.4.0 (mục `user_id`
  ở "Phải làm" của 1.4.0); backend chưa có quy tắc ghép theo `session_id`; app có đăng xuất truyền một id khách của chính
  app lúc đăng xuất.
- **Rút về `UNKNOWN` rồi đồng ý lại làm AppsFlyer start lần hai trong process** (`stop(true)` rồi `stop(false)` + start —
  AppsFlyer nhận thêm một launch/phiên).
- **Offerwall chỉ chặn `DENIED`**: `OfferwallManager` vẫn chạy khi tầng là `UNKNOWN`, khác Điều 13 mà các consumer khác đã
  theo.

## [1.4.0] - 2026-09-25

"Đúng luật, đo đúng, không hỏng câm" (DOC/65 Phase B): tầng consent từ TCF, Firebase/FCM/AppsFlyer/Meta (Audience
Network và app events) theo consent, một policy tần suất, app open hết hạn 4 giờ, remove-ads theo định dạng, hàng đợi
event, hợp đồng tên event, kill switch, `sdk_health`, ABI dump khoá trong CI. **Đã kiểm (doc 64 mục G):** cây cuối
`c4749ca` qua cổng release và cổng thiết bị (StrictMode sau retrace: by sdk 0; chuỗi R8, FATAL, `am_anr` 0) trên
emulator API 37, 28, 26. Kịch bản tay chỉ chạy trên emulator API 37, ở cây `05ca0bf`/`6529164` — trước các quyết định
DOC/66 (Task A–G): a1–a3, b–e, g, h, j1, j2, k như kỳ vọng; i (G9 "1 Activity sau GC") chưa đạt; l ghi hành vi
AppsFlyer cũ (start cho `DENIED`); m chỉ có unit test. Máy thật Redmi 22021211RC: kịch bản a1, a2, b, c, d, h, g trên
cây `b9ec778` (trước DOC/66; cổng thiết bị lúc đó FAIL G2: 11 vi phạm StrictMode do SDK), rồi cổng thiết bị PASS
(by sdk 0, `prefs warmup waited=0 ms`) trên APK `c216628` (sau Task B, trước Task C–G). **APK cuối chưa chạy trên máy
thật nào**; Galaxy A25 chưa chạy 1.4.0; CI chưa chạy trên nhánh này. Mặc định tần suất vẫn là của 1.3.1 (A/B ở 1.5.0,
§7 #1, #2). **2026-09-25: chủ dự án chấp nhận toàn bộ đề xuất DOC/66** — mọi quyết định DOC/65 §7 (#1–#16) nay **đã
chốt**, trừ #4 (chờ chủ dự án xem Crashlytics trước khi quyết định backport sang Speaker Clean) và #17 (P2, telemetry
trước consent, mục mới, chưa chốt). G8: 66 lớp thành `internal` (50,3%, Task G); ngưỡng `AdsHelper` ≤ 1.500 dòng dời
sang mức 1.5 (T3). **Chưa tag.** Review toàn nhánh và đợt sửa cuối đã xong; còn chờ: cổng thiết bị và kịch bản tay
(a2, d, rút/cấp lại consent, interstitial sau 61 phút) của APK cuối trên máy thật, một lượt CI trên nhánh (PR), và chủ
dự án xác nhận hai điểm của đợt sửa cuối — AppsFlyer dừng cả khi tầng về `UNKNOWN` trong vùng GDPR (rộng hơn chữ
"chuyển sang DENIED" của DOC/66 #12) và id app được lưu qua các lần mở (mục `user_id` ở "Phải làm khi nâng cấp").

Đọc theo thứ tự: **Phải làm khi nâng cấp** là việc app phải kiểm hoặc sửa (đổi hợp đồng, đổi nhị phân, đổi
manifest, việc cho backend); **Đổi** là hành vi khác đi mà app không phải sửa code; **Thêm** là API và khoá cấu
hình mới; **Xoá** và **Sửa** là API bị bỏ và lỗi được sửa.

### Phải làm khi nâng cấp

- **Tầng consent giờ đọc từ TCF, không chỉ từ trạng thái UMP.** Người dùng EEA bấm "Do not consent"
  giờ là `DENIED` (trước là `GRANTED`); đồng ý quảng cáo nhưng không đồng ý purpose 3 hoặc 4 là
  `PARTIAL`; UMP `OBTAINED` mà không đọc được `IABTCF_*` là `UNKNOWN`, không còn là `GRANTED`. App rẽ
  nhánh theo tầng (`ConsentManager.request { tier -> }`, `showPrivacyOptions`, `ConsentState.tier()`)
  thì kiểm lại nhánh `DENIED` và `PARTIAL`.
- **`ConsentState.allowsProfiling()` và `allowsPersonalization()` trả `false` với tầng `UNKNOWN`** (trước là
  `true`; quyết định #7, DOC/66). Brain (`DecisionEngine.observe` không tích luỹ hồ sơ và trả `false`, kịch bản về
  `BALANCED_HYBRID`) và CRM (`CrmBridge.emit` không phát) im lặng tới câu trả lời đầu của UMP ở người chưa có tầng
  (cài mới, hoặc tầng chưa được lưu). Người ngoài EEA gần như không mất gì: UMP trả `NOT_REQUIRED` → `GRANTED` sau
  vài trăm ms ở lần mở đầu, và tầng được lưu qua phiên. App không chạy UMP phải tự `ConsentState.setTier(…)`,
  không thì brain và CRM tắt hẳn. Overload mới `allowsProfiling(gdprApplies: Boolean?)` /
  `allowsPersonalization(gdprApplies: Boolean?)` cho `UNKNOWN` chỉ khi `gdprApplies == false`
  (`IABTCF_gdprApplies = 0` đọc được rõ ràng); `null` không bao giờ là lý do cấp. `DENIED` luôn chặn; `PARTIAL`
  như cũ (không tích luỹ hồ sơ, vẫn cá nhân hoá). Ba nơi gọi trong SDK không có `Context` nên dùng bản không tham
  số. Offerwall không đổi: `OfferwallManager` chỉ chặn `DENIED`, không dùng hai hàm này.
- **App không kế thừa `AppPluginBase`** thì tầng không được lưu qua phiên: trong
  `Application.onCreate` gọi `ConsentState.installStore(…)` rồi `ConsentState.restore()`, trước
  `AppPluginManager.initPlugin` (initPlugin đọc tầng để giữ FCM và đặt Consent Mode).
- **AppsFlyer chỉ start sau khi UMP trả lời** (`canRequestAds()`, cùng cổng với mạng quảng cáo) **và khi tầng
  cho phép** (không bao giờ với `DENIED`; `UNKNOWN` chỉ ngoài GDPR; xem "Đổi"). **Consent
  Mode của Firebase Analytics và FCM (auto-init và token) theo tầng consent từ `AppPluginManager.initPlugin`**:
  chạy lại mỗi lần `ConsentState.setTier` đổi tầng — do luồng UMP của SDK hay do app tự gọi — kể cả trước
  khi cổng mở, và thêm một lần khi cổng mở; chúng chỉ cấp khi tầng cho phép. Tới lần đầu đó, manifest SDK
  giữ Consent Mode v2 denied cả bốn loại (`google_analytics_default_allow_*` = `false`) và
  `firebase_messaging_auto_init_enabled` = `false`, còn AppsFlyer chưa start. Hệ quả:
  - App không chạy UMP và không tự `ConsentState.setTier` thì Consent Mode giữ giá trị Firebase đã lưu (cài
    mới: denied), FCM không auto-init, AppsFlyer không start. Đặt `consentHandledExternally = true` không đủ
    (nó chỉ tắt cảnh báo): phải gọi `ConsentManager.updateConsentInfo(activity)` (hoặc
    `request`/`gatherConsent`). App dùng CMP khác mà tự gọi `ConsentState.setTier` thì Consent Mode và FCM
    theo tầng đó, nhưng AppsFlyer vẫn chờ cổng UMP. App không dùng UMP mà cần push thì tự bật
    `FirebaseMessaging.getInstance().isAutoInitEnabled = true` sau `AppPluginManager.initPlugin`, mỗi lần
    mở app.
  - **Push chỉ khi tầng cho phép**: FCM (auto-init và token) chạy khi tầng là `PARTIAL`/`GRANTED` hoặc
    ngoài GDPR (`IABTCF_gdprApplies = 0`) mà tầng không phải `DENIED`. Người dùng EEA không nhận push
    trước khi trả lời form; người từ chối (`DENIED`) không có token nên không nhận push, tới khi đổi ý
    qua privacy options. Rút lại consent tắt auto-init và xoá token đã có (`deleteToken()`, xem "Đổi").
  - Trong vùng GDPR, `DENIED` và `UNKNOWN` tắt hẳn thu thập Firebase Analytics
    (`setAnalyticsCollectionEnabled(false)`): mất `ad_impression`, `sdk_health` và mọi sự kiện của
    nhóm này. SDK ghi cờ `analytics_collection_disabled_by_sdk` (`PreferenceHelper`) khi tắt, và chỉ bật lại
    (`setAnalyticsCollectionEnabled(true)`) khi cờ đó còn, lúc tầng cho phép (tầng đổi hoặc cổng mở). App tự
    tắt thu thập (công tắc "chia sẻ dữ liệu sử dụng") thì SDK không bật lại ở các lần mở app sau. Riêng khi
    tầng đổi từ từ chối sang cho phép, SDK bật lại thu thập nó đã tắt: app có công tắc riêng đặt lại nó trong
    `ConsentState.onChange` (lần này `onChange` có bắn vì tầng đổi; `setTier` với tầng không đổi thì không
    bắn), và phải **đăng ký listener đó sau `AppPluginManager.initPlugin`**: listener chạy theo thứ tự đăng ký,
    SDK đăng ký của nó trong `initPlugin`, nên listener đăng ký trước sẽ chạy trước và bị lần bật lại của SDK
    đè lên. App không kế thừa `AppPluginBase` phải `PreferenceHelper.getInstance().init(context)` trước
    `initPlugin`, không thì cờ chỉ sống trong process (log `PreferenceHelper not initialized`) và thu thập SDK
    đã tắt ở phiên trước không được bật lại.
  - **App đang tự gọi `setAnalyticsCollectionEnabled(true)` sau câu trả lời consent** (ví dụ Speaker Clean
    `MainActivity.kt:218`, trong `onDone` của `gatherConsent`) phải bỏ dòng đó, hoặc chỉ gọi khi tầng cho phép
    (`PARTIAL`/`GRANTED`, hoặc ngoài GDPR): gọi vô điều kiện sẽ bật lại thu thập cho người EEA vừa từ chối,
    ngay sau khi SDK đã tắt.
  - Trước câu trả lời đầu tiên trên máy cài mới ở EEA, thu thập Firebase Analytics **vẫn bật** với
    Consent Mode denied cả bốn loại: Firebase gửi ping không định danh (như `first_open`), không cookie,
    không advertising id. SDK chỉ tắt thu thập khi đã có tầng. Đây là hệ quả của hằng
    `AnalyticsConsent.DISABLE_COLLECTION_WHEN_DENIED` (quyết định B#6 trong addendum của Task 9, chủ dự án
    CHƯA chốt, theo dõi ở DOC/65 §7 #10); app muốn tắt hẳn tới khi có câu trả lời thì khai meta-data
    `firebase_analytics_collection_enabled` = `false` và tự bật lại khi tầng cho phép — SDK chỉ bật lại
    thu thập do chính nó tắt, nên không bật thay app.
  - `AppsflyerHelper.initHelper(context)` bị xoá. App gọi thẳng hàm này thì bỏ dòng đó: SDK tự start
    AppsFlyer sau consent, từ `AppPluginManager.initPlugin`.
  - Manifest của SDK đặt bốn `google_analytics_default_allow_*` về `false`; app nào đang khai `true`
    phải dùng `tools:replace="android:value"`, không thì lỗi manifest merge. Giá trị đó chỉ có hiệu lực
    tới khi SDK đặt Consent Mode theo tầng. `firebase_messaging_auto_init_enabled`: `tools:replace` không
    có tác dụng, vì SDK ghi cờ auto-init lúc chạy (lúc mở app khi tầng chưa cho phép FCM, rồi mỗi lần
    tầng đổi và khi cổng mở), và cờ đã ghi thắng manifest; app khai giá trị khác `false` mà không có
    `tools:replace` thì lỗi manifest merge.
  - SDK tắt FCM auto-init lúc mở app tới khi có consent (`AppPluginManager.initPlugin`, khi tầng khôi
    phục chưa cho phép FCM): bản cũ lưu cờ auto-init = `true` mỗi lần mở app, và cờ đã lưu thắng cờ
    manifest. Riêng lần mở đầu tiên sau khi nâng từ bản cũ, FCM (khởi tạo trước `Application.onCreate`)
    có thể còn đồng bộ token một lần theo cờ cũ; từ lần mở sau thì không.
- **`IapHelper.init(context)` trả `Boolean`** (trước là `Unit`; ý nghĩa ở mục "Đổi"): đổi nhị phân, app phải
  build lại với bản này; mã Kotlin bỏ qua giá trị trả về và mã Java thì không phải sửa.
- **Hợp đồng `onAdDismissed` của 1.3.1 vẫn áp**: khi Activity chủ bị huỷ lúc native full đang hiện, SDK gửi
  `onAdDismissed` đồng bộ trong `onActivityDestroyed` (sau `onSaveInstanceState`): không commit Fragment,
  không điều hướng trong callback đó khi Activity đang huỷ. App nâng thẳng từ 1.3.0 đọc mục "Phải làm khi nâng
  cấp" của [1.3.1] trước.
- **`DecisionLogQueue.peekAll()` trả `List<String>?`** (trước là `List<String>`): `null` khi file hàng
  đợi có mà không đọc được. Code Kotlin gọi thẳng hàm này phải xử lý `null` (lỗi biên dịch, không đổi nhị
  phân); SDK không có nơi gọi nào ngoài chính nó.
- **`onResult` của các hàm `LogEventServer.log…` chạy trên luồng bất kỳ và có thể tới rất trễ**: luồng gọi
  (event bị bỏ ngay), luồng IO của SDK, hoặc main thread (Retrofit trả lời); khi mạng lỗi có thể trễ vài
  phút, tới vài giờ khi mất mạng lâu (tới khi event gửi được hoặc bị bỏ). Code đang chạm UI hay làm việc
  nặng trong `onResult` phải chuyển sang luồng phù hợp và không được chờ callback này để đi tiếp.
- **Backend phải khử trùng event của SDK**: request hết giờ sau khi server đã nhận được gửi lại, nên event
  quảng cáo có thể tới hai lần, cùng `sequenceId` và `ts` — khử trùng theo (người dùng, `sequenceId`).
  Decision log replay (`DecisionLogger.flushQueue`) có thể gửi lại bản ghi server đã nhận — khử trùng theo
  `decision_id`. Cần ticket backend trước khi phát hành.
- **`user_id` trong telemetry server của SDK đổi từ ANDROID_ID sang player id của brain** (#13, DOC/66 mục 4): id
  app truyền qua `MonetizationSdk.configure { playerId }` nếu có, nếu không thì UUID SDK sinh một lần mỗi lần cài
  (`BrainStorage.playerId()`, id brain dùng chia nhóm). SDK không còn đọc ANDROID_ID. Việc này chỉ chạm app bật
  DataBucket (meta-data `DATABUCKETS_BASE_URL`) và app dùng web shop (`{user_id}` trong URL checkout của
  `PurchaseRouter`).
  - **`user_id` gãy liên tục một lần khi nâng lên 1.4.0**: mọi người dùng mang id mới. Dashboard và truy vấn
    nhóm theo `user_id` (DAU, retention, cluster BigQuery `event_name,user_id`) phải coi mốc nâng cấp là ranh
    giới; retention theo ngày vẫn tính được từ `install_day`/`retention_day` gửi kèm mỗi event. Cài lại app ra id
    mới (trừ khi Auto Backup khôi phục prefs của app), khác ANDROID_ID vốn giữ nguyên qua cài lại.
  - **Web shop muốn giữ đơn qua cài lại phải truyền id tài khoản qua `configure { playerId }`**: không truyền thì
    `{user_id}` là UUID của lần cài, cài lại hay đổi máy thì server của studio không map được đơn và quyền lợi cũ.
  - **Id app được lưu và dùng lại từ lần mở sau** (review cuối I2): `first_open`/`session_start` gửi trong
    `super.onCreate()` (`AppPluginBase.initPlugin`), trước khi app kịp `configure`, và body dựng trên luồng nền; nếu
    chỉ giữ id app trong bộ nhớ thì mọi lần mở lạnh của app truyền id đều có `session_start` mang UUID cài đặt hay id
    app tuỳ race (hai `user_id` mỗi máy). SDK lưu id hợp lệ cuối cùng app truyền (`configure { playerId }` hoặc
    `AdsBrain.onSessionStart(id)`; khoá prefs `brain_app_player_id`, `apply()`), và `user_id` = id app của process
    này → id app đã lưu → UUID của SDK. Id mới ghi đè (đăng nhập tài khoản khác); id rỗng/không hợp lệ không được
    lưu. **Lần mở trước khi id app được lưu** (lần mở đầu tiên sau khi nâng lên 1.4.0 hoặc sau khi cài, hoặc mọi
    lần mở trước lần đầu app truyền id nếu app chỉ truyền id sau đăng nhập) `first_open`/`session_start` và
    `experiment_exposure` tự động của `initPlugin` (app bật brain trước `super.onCreate()`) vẫn có thể mang UUID;
    từ đó là id app ổn định. **Quy tắc ghép cho backend:** một cài đặt có thể có hàng mang UUID của SDK
    (`BrainStorage.playerId()`) ở các lần mở trước khi id app được lưu, rồi id app từ đó. Hàng mang UUID của SDK
    và hàng mang id app có cùng `userProperty.session_id` (một UUID mỗi process) là một người: ghép hàng UUID vào
    id app đó, hoặc bỏ hàng UUID của phiên đó khi đối soát theo id app. Hai id **app** khác nhau trong cùng
    `session_id` không phải một người: app đổi tài khoản giữa phiên, hoặc hàng `brain_decision` replay (mang
    `user_id` lúc ghi của process trước với `session_id` của process gửi lại) — không ghép. App tự dùng UUID làm
    id thì backend phải phân biệt hai loại theo id app đã biết của cài đặt, không theo dạng chuỗi. Đối soát bucket
    của backend (`reconcile.py` lớp 1) và nhóm holdout
    (`metrics.py`) giả định `user_id` = id brain phân nhóm (DOC/57 mục E.5); ba đường vào phiên giờ giữ điều đó:
    (1) quay lại sau ≥ 30 phút ở nền (`AdsBrain.onSessionStart()` trong `AppPluginBase.onStart`) giữ id app đã
    truyền, không còn về UUID của SDK (lỗi có từ trước, đổi bucket holdout giữa các phiên); (2) app gọi thẳng
    `AdsBrain.onSessionStart(id)` (DOC/38 §B bước 2) thay vì `configure` thì id đó cũng là `user_id`; (3) bản ghi
    decision log chờ gửi lưu `user_id` lúc ghi, nên replay ở lần mở sau (trước `configure`) không mang UUID của SDK.
    `AdsBrain.onSessionStart` với id rỗng giờ log lỗi và dùng id đang có hiệu lực (trước: phân nhóm theo chuỗi rỗng).
    Id chứa U+001F, `\n` hoặc `\r` cũng vậy, ở cả `configure { playerId }` (log lỗi, bỏ qua) và
    `AdsBrain.onSessionStart(id)`: nó làm hỏng dòng hàng đợi decision log (review N2).
  - **`AdsBrain.onSessionStart()` không tham số không còn quay về id của SDK trong cùng process** (review N5): sau khi
    app truyền id (qua `configure` hay `onSessionStart(id)`), id đó giữ tới hết process. App từng gọi
    `onSessionStart()` lúc đăng xuất để về id thiết bị giờ vẫn phân nhóm và gửi `user_id` theo id tài khoản cũ; muốn
    đổi thì truyền id mới qua `onSessionStart(id)`. Id app đã lưu cũng sống qua các lần mở sau (mục trên), tới khi app
    truyền id mới; SDK chưa có đường xoá id app.
  - **Truyền id nội bộ mờ, không email hay số điện thoại** (qua `configure { playerId }` hay
    `AdsBrain.onSessionStart(id)`): id đó tới server telemetry của SDK trên mọi event.
- **Load bị chặn giờ báo `onLoadFailed` ngay, trên luồng gọi** (`loadRect4Show`, `loadNtClForThisTurn`,
  `loadNativeFullForThisTurn`, `loadRewardedForThisTurn`; `showBanner` báo `onAdShowFail`): app đang gọi lại
  load ngay trong `onLoadFailed` sẽ lặp đệ quy khi người dùng đã mua gỡ quảng cáo, định dạng bị tắt hoặc kill
  switch bật. Thử lại thì hẹn giờ (hoặc bỏ qua khi `err` là `show_blocked_kill_switch` hay
  `… isremoveads or disable`), không gọi lại đồng bộ.
- **Ba event chẩn đoán trên Firebase đổi tên** (Điều 9.2: snake_case; DOC/65 §7 #5, chốt ở DOC/66: giữ đổi
  tên): `reloadSplash_acnull` → `reload_splash_ac_null`, `setAc_waitreloadsplash` → `set_ac_wait_reload_splash`,
  và event placement được load khi chưa có Activity `ads_<tên lớp><adsType>`
  (`ads_AdsMobPlInters0`; ở bản release của app là tên lớp đã bị R8 đổi, kiểu `ads_a0`) →
  `ads_pl_<mạng>_<định dạng>_<adsType>` (`ads_pl_mob_full_0`). Báo cáo Firebase của các event này gãy liên
  tục từ 1.4.0: dashboard, audience và truy vấn BigQuery theo tên cũ phải gộp tên cũ với tên mới. Event doanh
  thu và conversion không đổi tên (`AdLTV_OneDay_Top*Percent`, `daily_adrevenue`, `show_ads_*`, `aad_*`); 5 event
  `AdLTV_OneDay_Top*Percent` được **đóng băng** làm hợp đồng (#5): Google Ads nhập chúng làm conversion theo tên.
- **`Constants.LogEvent` chỉ còn `BASE_URL` và `SEQUENCE_ID`**: các hằng tên event chuyển vào
  `com.app.plugin.logevent.EventNames` (internal). Code app tham chiếu `Constants.LogEvent.<TÊN_EVENT>` lỗi
  biên dịch (không đổi nhị phân, hằng đã được inline); Speaker Clean và Invoice Maker không dùng.
- **Nhãn `err` của `showRect` và `loadOpenAdForThisTurn` khi bị remove-ads hay định dạng tắt đổi** cho cùng mẫu
  với các hàm khác: `"remove ads"` → `"showRect isremoveads or disable"`, `"isremoveads"` →
  `"loadOpenAdForThisTurn isremoveads or disable"`. App so khớp đúng chuỗi cũ thì đổi sang
  `endsWith("isremoveads or disable")`.
- **Bốn lớp thành `internal`**: `AdsOb4Native`, `ObjectForShowAdWithViewParent`, `LogEventServerHelper`,
  `NtFullPortraitHelper` (chỉ SDK dùng). Code Kotlin tham chiếu chúng lỗi biên dịch; nhị phân không đổi (lớp vẫn
  public trong bytecode) và Java gọi được như cũ. Speaker Clean, Invoice Maker AI và app-template: 0 tham chiếu.
- **`AdsPlBase` có thêm `protected abstract val eventTag: String`**: lớp con của `AdsPlBase` viết ngoài SDK (Speaker
  Clean và Invoice Maker không có) phải khai nó bằng getter hằng chữ snake_case, ví dụ
  `override val eventTag: String get() = "mob_full"`.
- **`AdsBase` có thêm `protected abstract val eventTag: String`, và nhãn `not_plm_cf` đổi dạng** (D3, DOC/66): lớp
  mạng viết ngoài SDK kế thừa `AdsBase` (Speaker Clean và Invoice Maker không có) phải khai getter hằng chữ, ví dụ
  `override val eventTag: String get() = "mob_full"`. `ad_show_fail_reason` khi mạng không có cấu hình cho
  placement đổi từ `not_plm_cf_<tên lớp><adsType>` (ở bản release của app là tên lớp đã bị R8 đổi, kiểu
  `not_plm_cf_z71`) thành `not_plm_cf_<mạng>_<định dạng>_<adsType>` (`not_plm_cf_mob_full_100`,
  `not_plm_cf_fb_reward_1`). Dashboard lọc theo tiền tố `not_plm_cf` không đổi; truy vấn theo giá trị đầy đủ phải
  gộp giá trị cũ với mới. Log debug `ads <TênLớp><adsType> … not pl config` thành `ads <eventTag>_<adsType> …`.
- **`RemoveAdsMask.INTERRUPTIVE` gỡ thêm native collapsible** (quyết định #15, DOC/66): giá trị đổi từ 21
  (`0b010101`) thành 23 (`0b010111`). Người mua gói rẻ khai mask này (Invoice Maker) không còn thấy NtCl, lớp phủ
  đáy che ~45 % màn hình: `loadNtClForThisTurn` báo `onLoadFailed` với `loadNtClForThisTurn isremoveads or
  disable`, như banner và rect. Native inline và rewarded vẫn còn; `ALL`, `NONE` không đổi. Mô tả gói rẻ trên store
  đổi theo: "gỡ banner và quảng cáo toàn màn hình; quảng cáo gốc trong nội dung và quảng cáo có thưởng vẫn còn".
  `INTERRUPTIVE` là `const val`, được inline vào app lúc biên dịch: app phải build lại với 1.4.0 mới nhận giá trị
  mới (mask đã lưu được thay ở lần `configure` kế tiếp).
- **66 lớp adapter/nội bộ thành `internal`** (G8, research-g8.md Phụ lục A; nối tiếp "Bốn lớp thành `internal`" ở
  trên). Code Kotlin ngoài SDK tham chiếu trực tiếp một trong các lớp này lỗi biên dịch; nhị phân không đổi (lớp
  vẫn public trong bytecode, `tools/api_dump.sh --check` không đổi dump) nên Java gọi được như cũ. Speaker Clean,
  Invoice Maker AI và app-template: 0 tham chiếu tới bất kỳ lớp nào dưới đây.
  - `com.app.plugin.ads`: `AdsIDDef`, `AdsUtil`, `FullScreenAdPolicy`, `ResIdDefault`.
  - `com.app.plugin.ads.adsmob` (16): `AdsMobBanner`, `AdsMobFull`, `AdsMobGift`, `AdsMobNative`, `AdsMobNtCl`,
    `AdsMobNtClView`, `AdsMobNtFullView`, `AdsMobPlBanner`, `AdsMobPlInters`, `AdsMobPlNt`, `AdsMobPlNtCl`,
    `AdsMobPlNtFull`, `AdsMobPlOpen`, `AdsMobPlRect`, `AdsMobPlrewarded`, `AdsMobRect`.
  - `com.app.plugin.ads.adsmax` (17): `AdsMaxBanner`, `AdsMaxFull`, `AdsMaxGift`, `AdsMaxMy`, `AdsMaxNative`,
    `AdsMaxNtCl`, `AdsMaxNtClView`, `AdsMaxNtFullView`, `AdsMaxPlBanner`, `AdsMaxPlInters`, `AdsMaxPlNt`,
    `AdsMaxPlNtCl`, `AdsMaxPlNtFull`, `AdsMaxPlOpen`, `AdsMaxPlRect`, `AdsMaxPlrewarded`, `AdsMaxRect`.
  - `com.app.plugin.ads.adsfb` (16): `AdsFbBanner`, `AdsFbFull`, `AdsFbGift`, `AdsFbMy`, `AdsFbNative`,
    `AdsFbNtCl`, `AdsFbNtClView`, `AdsFbNtFullView`, `AdsFbPlBanner`, `AdsFbPlInters`, `AdsFbPlNt`, `AdsFbPlNtCl`,
    `AdsFbPlNtFull`, `AdsFbPlRect`, `AdsFbPlrewarded`, `AdsFbRect`.
  - `com.app.plugin.brain` (3): `BrainStorage`, `ClickFraudGuard`, `LikelihoodEstimator`.
  - `com.app.plugin.firbase`: `FirHelperWorker`.
  - `com.app.plugin.logevent` (5): `AdState`, `AdStatus`, `AdTracker`, `AdmobSDKInitState`, `Logs`.
  - `com.app.plugin.until` (4): `AdFormat`, `AdNetwork`, `AdPlatform`, `AdState`; typealias `AdCallBack` trong
    cùng file (`ADSEnums.kt`, không dùng ở đâu khác trong SDK) cũng thành `internal` vì nó lộ `AdState` ra chữ ký
    public.
  - **Giữ public** (đã thử, trình biên dịch báo lỗi hoặc bảng nghiên cứu sai): không có — cả 66 lớp trong
    research-g8.md Phụ lục A đều đổi được đúng như đề xuất.

### Đổi

- **Full2 không còn hiện trên màn hình tái tạo khi quảng cáo cha bị đóng vì Activity host bị huỷ** (xoay, đổi ngôn
  ngữ, dark mode; #6, DOC/66 mục 4). Cha là native full (dialog của SDK) thì bị đóng giữa `clearActivity()`, và
  1.3.1 xếp Full2 đã load (`pendingFull2`, giữ `cbShow` của app tức Activity cũ, xem "Hành vi cần biết" của
  1.3.1) rồi hiện nó trên Activity mới khi resume — một quảng cáo toàn màn hình hiện "bất ngờ" theo chính sách Play.
  Nay SDK bỏ lượt Full2 đó: log `showFull2 skipped: parent closed by host destroy`, gửi `ad_show_fail_reason` =
  `host_destroyed` (hằng mới `Constants.AdShowFailReason.HOST_DESTROYED`), không xếp hàng, không giữ callback.
  App vẫn nhận đúng một `onAdDismissed` của cha và đúng một `onTransitionChanged(false)`. Cha là interstitial GMA
  (`AdActivity` sống qua xoay, người dùng đóng sau đó) không đổi: Full2 vẫn xếp và hiện khi host mới resume.
  Dashboard: `host_destroyed` là lượt Full2 bỏ có chủ ý, không phải lỗi hiện.
- Tầng consent đọc `IABTCF_gdprApplies`, `IABTCF_PurposeConsents`,
  `IABTCF_PurposeLegitimateInterests`, `IABTCF_VendorConsents` (TCF v2.2, UMP ghi) theo luật CMP của
  Google cho AdMob: purpose 1 + vendor Google (755) + purpose 2, 7, 9, 10 (consent hoặc legitimate
  interest) là `PARTIAL`, thêm consent purpose 3 và 4 là `GRANTED`, thiếu một điều kiện là `DENIED`.
  "Do not consent" giờ là `DENIED`, nên brain (không tích luỹ hồ sơ), CRM, cá nhân hoá và offerwall
  tắt đúng. Ngoài EEA (`NOT_REQUIRED`) vẫn `GRANTED`; form chưa trả lời (`REQUIRED`) vẫn `DENIED`.
- Tầng consent được lưu mỗi lần đổi (`PreferenceHelper`, khoá `consent_tier`) và khôi phục trong
  `AppPluginBase.onCreate`, trước khi UMP trả lời. Trước đây mỗi process mở ở `UNKNOWN` (profiling
  được phép) tới khi luồng consent chạy xong.
- Consent Mode v2 của Firebase Analytics theo tầng, đặt lại mỗi lần tầng đổi: ngoài GDPR
  (`IABTCF_gdprApplies = 0`, trừ `DENIED`) và `GRANTED` cấp cả bốn loại; `PARTIAL` cấp
  `analytics_storage`, `ad_storage`, từ chối `ad_user_data`, `ad_personalization`; `DENIED` và
  `UNKNOWN` từ chối cả bốn.
  Thiếu `IABTCF_gdprApplies` được xử lý như có GDPR, không bao giờ như ngoài GDPR.
- AppsFlyer nhận `AppsFlyerConsent` trước `init`/`start` và mỗi lần tầng đổi (rút lại qua privacy
  options tới được AppsFlyer): ngoài GDPR (trừ `DENIED`) là `isUserSubjectToGDPR = false`; trong GDPR
  (hoặc không biết) `hasConsentForDataUsage` và `hasConsentForAdStorage` đúng với `GRANTED`/`PARTIAL`,
  `hasConsentForAdsPersonalization` chỉ đúng với `GRANTED`.
- `IABTCF_gdprApplies = 0` không còn thắng tầng `DENIED`, ở cả Consent Mode, FCM và AppsFlyer: `DENIED`
  luôn từ chối (Consent Mode denied cả bốn và tắt thu thập; FCM không auto-init, không token; AppsFlyer
  không start, xem mục kế), vì `0` có thể là giá trị cũ còn lại khi người dùng đã vào EEA.
  Chỉ thu hẹp: không trường hợp nào được cấp nhiều hơn trước.
- **Người dùng `DENIED` không còn launch AppsFlyer** (#12, DOC/66; trước là start với consent toàn `false`, mà
  tài liệu AppsFlyer không nói consent `false` ngăn thu thập). SDK không `setConsentData`, không `init`, không
  `start` khi tầng là `DENIED` (kể cả khi `IABTCF_gdprApplies = 0`), và cả khi tầng là `UNKNOWN` trong vùng GDPR
  hoặc chưa biết GDPR (cổng chỉ xét `canRequestAds()` nên có thể mở khi tầng còn `UNKNOWN`); log
  `appsflyer not started: consent tier=<tầng> gdprApplies=<…>` một lần cho mỗi tầng bị từ chối trong process (cổng
  mở lúc `UNKNOWN` rồi tầng thành `DENIED` → hai dòng; dòng `tier=DENIED` luôn có). Doanh thu quảng cáo và IAP
  (`AppsflyerHelper.logAdRevenue`, `logIapPurchase`) chỉ tới AppsFlyer khi nó đang chạy; chưa start hoặc đã stop thì
  bỏ, log `appsflyer event dropped: not started (tier=…)` một lần mỗi process. AppsFlyer đang chạy mà tầng không còn
  cho phép (`DENIED` khi người dùng rút consent, hoặc `UNKNOWN` trong vùng GDPR/chưa biết GDPR — `ConsentManager.reset()`,
  trả lời lại mà `IABTCF_*` không đọc được) thì SDK gọi `AppsFlyerLib.stop(true, context)` đúng một lần, log
  `appsflyer stopped: consent withdrawn (tier=<tầng>)`: AppsFlyer ngừng gửi mọi dữ liệu. Cổng dừng trùng cổng start
  (`ConsentRules.tierAllows`), như FCM và Meta; trước đó chỉ `DENIED` mới dừng, nên GRANTED → UNKNOWN trong process để
  AppsFlyer chạy tiếp với consent toàn `false` (review cuối I1). Tầng thành cho phép sau đó trong cùng process (đồng ý lại
  qua privacy options, hoặc UMP cập nhật tầng) thì SDK `stop(false)` (nếu chính nó đã stop) rồi start lại, trên
  Activity của luồng consent. Hệ quả: AppsFlyer không thấy lượt cài, launch và doanh thu của nhóm EEA từ chối.
- Consent Mode và FCM theo tầng ngay từ `AppPluginManager.initPlugin`, không chờ UMP cho phép quảng cáo:
  tầng thu hẹp khi cổng chưa mở (UMP báo `REQUIRED` lại vì hết hạn hay chính sách đổi, người dùng từng
  `GRANTED`) giờ tới Firebase và FCM ngay (Consent Mode denied, tắt thu thập, tắt auto-init), thay vì
  giữ giá trị của phiên trước tới khi người dùng trả lời lại. Dòng `analytics consent applied` có thể
  xuất hiện hai lần khi cổng mở do chính câu trả lời (một lần khi tầng đổi, một lần khi cổng mở).
- **Meta Audience Network không khởi tạo cho người `DENIED` trong vùng GDPR** (P1, DOC/66; trước là khởi tạo cho
  mọi tầng). Meta không có trong GVL mà dựa vào Additional Consent (`IABTCF_AddtlConsent`), SDK chưa đọc chuỗi đó:
  tầng `DENIED` với `IABTCF_gdprApplies` khác `0` (hoặc vắng) thì SDK không gọi khởi tạo Meta AN, log lỗi
  `meta an: not started, consent tier=DENIED in GDPR scope`, và `sdk_health` báo `networks=admob,max` (hoặc
  `admob`). Load của placement Meta (`fb_*`) đang giữ chờ mạng khởi tạo, và load đến sau đó, báo
  `onLoadFailed(…, "network_not_started")` ngay, đúng một lần, trên luồng gọi, để waterfall đi tiếp; không chờ
  mãi. Tầng thành cho phép sau đó trong cùng process (đồng ý lại qua privacy options) thì SDK khởi tạo Meta lúc
  đó, một lần. Ngoài GDPR (`IABTCF_gdprApplies = 0`) khởi tạo, trừ tầng `DENIED` (FX, mục dưới).
  Chưa phủ: adapter Meta trong mediation của AdMob
  (`com.google.ads.mediation:facebook`) và MAX (`facebook-adapter`) do chính MobileAds/AppLovin khởi tạo; SDK
  không chặn chúng (DOC/65 §7 #16). Khởi tạo muộn chỉ khi tầng thật sự cho phép (`GRANTED`/`PARTIAL`, hoặc ngoài
  GDPR): `DENIED` → `UNKNOWN` (reset, IABTCF không đọc được) không khởi tạo Meta.
- **Meta SDK (app events, `facebook-core`): người `DENIED` trong vùng GDPR không được SDK khởi tạo, và cờ auto-init đã
  lưu trên máy bị tắt** (P1 mở rộng, review N1; trước là khởi tạo sau cổng UMP cho mọi tầng, chỉ tắt advertising id).
  App gọi `AppPluginBase.initMetaSdkAfterConsent()`: tầng `DENIED` với `IABTCF_gdprApplies` khác `0` (hoặc vắng) thì
  SDK không `fullyInitialize`, không `AppEventsLogger.activateApp`, log lỗi `meta sdk: not started, consent
  tier=DENIED in GDPR scope`, và trên luồng nền ghi `false` cho `FacebookSdk.setAutoInitEnabled`,
  `setAutoLogAppEventsEnabled`, `setAdvertiserIDCollectionEnabled` (log `meta sdk: auto-init disabled (tier=DENIED)`).
  Lý do: facebook-core **lưu** ba cờ này trên máy và giá trị đã lưu thắng manifest; `FacebookInitProvider` đọc nó lúc
  mở app, trước `initPlugin`, rồi tự khởi tạo và log app events. Mọi máy từng được SDK bật (mọi máy nâng cấp từ
  ≤ 1.3.x) giữ `true`, nên chỉ "không khởi tạo" thì Meta vẫn chạy trước cổng consent. Meta đã chạy trong process mà
  tầng chuyển sang từ chối (rút consent): cũng ghi `false` (một lần mỗi lần chuyển), nhưng **facebook-core đã chạy
  trong process thì chạy tiếp tới hết process** — SDK không dừng được nó; cờ `false` chỉ có hiệu lực từ lần mở app
  kế. Cài đặt phía server của Meta (app settings) có thể đè auto-log app events dù cờ cục bộ là `false`: cần kiểm
  trên máy (Events Manager không nhận `fb_mobile_activate_app` ở lần mở sau khi rút). Tầng thành cho phép sau đó (`GRANTED`/`PARTIAL`,
  hoặc ngoài GDPR; không phải `UNKNOWN`): khởi tạo lúc đó, một lần (`meta sdk: consent now allows it, starting`), ghi
  `true` lại như cũ. Ngoài GDPR khởi tạo, trừ tầng `DENIED` (FX, mục dưới).
  Hệ quả: chiến dịch Meta không thấy install/app event của nhóm EEA từ chối.
  **Chưa phủ:** lần mở app mà SDK ghi `false` (lần mở **đầu** sau nâng cấp, hoặc lần đầu sau khi rút consent) vẫn có
  thể có một lượt auto-init và app event tự động của facebook-core, vì provider đã chạy trước khi cờ `false` được ghi;
  từ lần mở kế thì không. Nếu `FacebookSdk` chưa khởi tạo (app tắt provider), cờ chỉ nằm trong bộ nhớ và SDK log lỗi.
- **Meta AN và Meta SDK (app events) dùng cùng cổng consent với AppsFlyer (`ConsentRules.tierAllows`)** (FX,
  Điều 13: `UNKNOWN` không bao giờ là đồng ý). Ngoài `DENIED` trong vùng GDPR (hai mục trên), SDK cũng không khởi tạo
  Meta khi tầng là `UNKNOWN` trong vùng GDPR hoặc `IABTCF_gdprApplies` vắng, và khi tầng là `DENIED` với
  `IABTCF_gdprApplies = 0` (`DENIED` thắng `0`, như AppsFlyer và FCM).
  Log `meta an: not started, consent tier=<tầng> in|outside GDPR scope` (và `meta sdk: not started, …`); load
  `fb_*` báo `network_not_started` như trên; tầng cho phép sau đó trong process thì khởi tạo một lần. `UNKNOWN` ngoài
  GDPR (`IABTCF_gdprApplies = 0`) vẫn khởi tạo. Hệ quả: nếu cổng UMP mở lúc tầng còn `UNKNOWN` (tầng phiên trước chưa
  lưu, hoặc `IABTCF_*` không đọc được), Meta khởi tạo muộn hơn, khi tầng thật về, và load `fb_*` trong khoảng đó đi
  sang mạng sau.
- AppsFlyer start trên Activity của luồng consent (`gatherConsent`/`updateConsentInfo`) khi còn sống,
  theo hướng dẫn start trễ của AppsFlyer, để sự kiện launch đi ngay sau câu trả lời thay vì có thể chờ
  lần background→foreground kế. Không có Activity (start trong `Application.onCreate` với câu trả lời
  cũ) thì như cũ, dùng app context, kèm log lỗi `appsflyer start without activity`. Kết quả request
  launch được log: `appsflyer launch request sent` hoặc `appsflyer launch request failed: code=…`.
- `gatherConsent` gặp lỗi mạng giờ dịch lại tầng từ bộ nhớ đệm trên máy như `updateConsentInfo`: tầng
  khôi phục `GRANTED` mà UMP đã lưu `REQUIRED` thành `DENIED`.
- Lỗi mạng mà bộ nhớ đệm không có câu trả lời: vẫn giữ `DENIED`/`PARTIAL`, nhưng `GRANTED` khôi phục từ
  phiên trước hạ về `UNKNOWN`.
- `ConsentManager.isConsentResolved(context)` và `isPrivacyOptionsRequired(context)` ghi log lỗi khi
  đọc UMP lỗi (vẫn trả `false`).
- Token FCM không còn được lấy lúc khởi tạo `FirHelper` (`getToken()` tạo token dù auto-init tắt); SDK
  lấy nó một lần, lần đầu tầng cho phép FCM. Lỗi lấy token giờ là log lỗi, không còn là log debug.
- **Rút consent giờ xoá token FCM** (#8, DOC/66): khi tầng chuyển từ cho phép FCM sang không cho phép trong
  process (ví dụ `GRANTED` → `DENIED` qua privacy options; lần theo tầng đầu tiên của process không tính), SDK
  tắt auto-init rồi gọi `FirebaseMessaging.deleteToken()` trên luồng nền `appplugin-fcm`, log
  `fcm token deleted after consent withdrawn` hoặc log lỗi. SDK xoá token và thử lại ở lần mở kế nếu lỗi: xoá lỗi
  (không mạng, `SERVICE_NOT_AVAILABLE`…) thì lưu cờ `mem_fcm_delete_pending`, lần mở app sau mà tầng vẫn không cho
  phép thì gọi `deleteToken()` lại một lần trên luồng nền (log `fcm token delete retried at launch…`); thành công
  hoặc tầng cho phép lại thì bỏ cờ. Trong khoảng tới khi xoá thành công, token cũ vẫn có thể nhận push (trước đây
  token cũ luôn nhận push). Cấp lại sau đó lấy token mới, một lần. Rút consent giữa hai lần mở app (tầng đã lưu cho
  phép, lần đầu của process là từ chối) chưa xoá token.
- Mở app bớt việc đĩa trên main thread: `AppPluginBase.onCreate` ghi 11 cờ prefs trong một lần edit
  (`PreferenceHelper.setValues`) thay vì 11 lần; `DecisionLogger.attachQueue` không còn đọc file hàng
  đợi trên luồng gọi — đếm, flush và xoá bản ghi đã gửi chạy trên luồng nền `appplugin-decision-log`.
  `DecisionLogger.queuedCount()` có thể trả `-1` ngay sau khi attach, tới khi đếm xong (báo cáo
  `MonetizationSdk` in "đang đếm"); `flushQueue()` trả về ngay. Log mới
  `AppPlugin: AppPluginBase.onCreate main-thread ms=<n>`.
- `PreferenceHelper.setValue`/`setValues` với giá trị `null` giờ xoá khoá (log cảnh báo); trước đây
  không ghi gì và log "Not yet implemented". `DecisionLogQueue.removeAll` trả về số bản ghi còn lại
  (trước là `Unit`).
- Event gửi lên server của SDK (`LogEventServer`) lỗi mạng, 5xx, 408 hay 429 giờ được gửi lại với backoff
  thay vì mất, nên có thể tới server trễ (có thể tới hai lần, xem "Phải làm khi nâng cấp"). Các hàm
  `LogEventServer.log…` có tham số `onResult` giờ gọi nó một lần khi có kết quả cuối (server nhận, hoặc
  event bị bỏ) thay vì báo `false` ngay ở lần lỗi đầu (luồng và độ trễ: xem "Phải làm khi nâng cấp").
  Nhánh bỏ event log `logE` `event dropped: …` / `event queue full …`.
  Hàng đợi chỉ ở bộ nhớ: process chết thì event chưa gửi mất (decision log vẫn còn bản trên đĩa).
- `DecisionLogger.flushQueue()` mỗi lượt chỉ replay tối đa 100 bản ghi cũ nhất, và không quá chỗ trống của
  hàng đợi event (hàng đầy thì hoãn), phần còn lại nằm trên đĩa; lượt kế chạy ngay khi lượt này xong và đĩa
  thật sự bớt (có bản được xoá, không bản nào xoá hỏng — xoá hỏng thì log một lần và chờ lần flush sau),
  tối đa 10 lượt mỗi lần flush. Flush khi lượt trước chưa xong thì bỏ qua (không gửi trùng). Bản bị server
  từ chối (4xx) xếp sau mọi bản khác trong process và không bao giờ bị xoá vì bị từ chối: 4xx toàn cục
  (khoá API sai, WAF, deploy hỏng) không làm mất backlog, trần 5000 bản giới hạn đĩa. Bản không dựng được
  body (ví dụ xác suất `NaN`, hỏng lại mỗi lần gửi) cũng xếp cuối như bản bị từ chối và nằm lại trên đĩa, để nó
  không chặn đầu mọi lượt replay.
  Trước đây mỗi lần flush gửi cả backlog (tới 5000 bản).
- `IapHelper.init(context)` trả `Boolean`: `true` khi đang (hoặc đã) kết nối Play Billing — kể cả khi kill
  switch `iap` tới sau lúc đã kết nối (kill áp từ lần mở kế) — `false` khi kill switch `iap` bật trước khi
  kết nối hoặc dựng `BillingClient` lỗi (trước là `Unit`; app Kotlin bỏ qua giá trị trả về thì
  không phải sửa, nhưng phải build lại với bản này).
- `showRewarded` bị chặn vì remove-ads hay kill switch giờ gửi `ad_show_fail_reason` (`remove_ad` hoặc
  `show_blocked_kill_switch`) như interstitial, app open và native; trước đây im lặng.
- `loadRect4Show`, `loadNtClForThisTurn`, `loadNativeFullForThisTurn`, `loadRewardedForThisTurn` bị cổng
  chặn (đã mua gỡ định dạng, định dạng tắt trong cấu hình, kill switch `ads`) giờ gọi
  `onLoadFailed(placement, realPl, "", err)` đúng một lần rồi dừng, như `loadInterstitialForThisTurn` và
  `showRect`; trước đây `return` im lặng nên app chờ callback (spinner của nút rewarded) chờ mãi. `err` là
  `show_blocked_kill_switch` khi kill, không thì `<tên hàm> isremoveads or disable`. `showBanner` bị cổng
  chặn gọi `onAdShowFail(placement, realPl, "", err)` của `cbShow` (nhãn tương tự, `showBanner isremoveads
  or disable`); `cbLoad` không được gọi. Cổng "chưa có cấu hình placement" (`cf not`) và "đang load" của bốn
  hàm này vẫn `return` im lặng như trước (chưa sửa ở bản này).
- App open AdMob đã load quá 4 giờ không còn được hiện (Google: app open hết hạn 4 giờ sau khi
  load) và được tải mới ở lần load kế: quảng cáo đó được coi là chưa load ở `showOpenAd` (mạng đó trả
  `false`, không callback, như lượt "show not loaded"; vòng đi tiếp sang mạng sau), ở lần load kế (tải mới
  thay vì báo đã load) và ở `hasLoadedAd` (nên `hasLoadedAd()` không còn là phép đọc thuần: chỉ gọi trên main
  thread). **MAX app open không áp hạn này** (Task I1, cùng lập luận với MAX interstitial/rewarded ở mục kế):
  `MaxAppOpenAd` tự hết hạn và nạp lại trong cùng đối tượng, nên hạn của SDK chỉ từ chối nhầm quảng cáo MAX còn
  tươi. Nó bị bỏ, gửi `ad_show_fail_reason` = `app_open_expired` (kể cả khi phát hiện ở
  `load()` hay `hasLoadedAd()`: trên dashboard đây là "quảng cáo cũ bị bỏ", không phải lượt show hỏng), log
  `app_open_expired after <n> min`. Giờ hệ thống bị lùi sau khi load cũng làm quảng cáo bị coi là hết
  hạn; quảng cáo đang hiện thì không bị bỏ. Ngưỡng mặc định 4 giờ, rút ngắn được bằng
  `app_open_max_age_minutes` của `ads_policy_android` (xem "Thêm"). Interstitial, rewarded, native full: mục kế.
- Interstitial, rewarded và native full (AdMob, Meta; với MAX chỉ native full) đã load quá 60 phút không còn được
  hiện và được tải mới ở lần load kế (D5, DOC/66; GMA: *"ads expire after an hour"*), cùng cơ chế với app open ở mục trên: coi là
  chưa load ở `show` (trả `false`, không callback, vòng đi tiếp sang mạng sau), ở `load` (tải mới thay vì báo đã
  load), ở `hasLoadedAd`/`isInterstitialReadyToShow`, ở `getAdForShow` của native full (trả `null`) và khi dựng
  sẵn dialog native full cho Full2. Quảng cáo bị bỏ gửi `ad_show_fail_reason` = `ad_expired` (kể cả khi phát hiện
  ở `load()` hay `hasLoadedAd()`; lượt show AdMob interstitial và AdMob native full gửi thêm lý do "không có quảng
  cáo" vốn có của nhánh đó, `show_not_loaded` và `show_not_loaded_on_show`), log `ad_expired after <n> min`; native full, Meta interstitial và Meta rewarded được `destroy()`. **MAX interstitial
  và rewarded không áp hạn này** (review E I1): MAX tự hết hạn và nạp lại quảng cáo trong cùng đối tượng
  `MaxInterstitialAd`/`MaxRewardedAd` (`onAdExpired`), nên mốc load của SDK không bao giờ đặt lại và hạn 60 phút chỉ
  từ chối nhầm quảng cáo MAX còn tươi (mất lượt hiện, `ad_expired` giả); MAX không bao giờ hiện quảng cáo nó coi là
  hết hạn. MAX native full vẫn áp hạn (MAX chỉ báo `onNativeAdExpired`, không tự nạp lại). Ngưỡng đổi được bằng
  `fullscreen_max_age_minutes` (xem "Thêm").
  Dashboard: `ad_expired` tăng ở người để quảng cáo đã load quá một giờ mới tới lượt hiện (AdMob interstitial và
  native full kéo theo `show_not_loaded`/`show_not_loaded_on_show` cùng lượt).
- `showInterstitial` (với `isDeltaTime`) bị chặn vì `fullTimeStart` hay chưa đủ khoảng cách giờ gửi
  `ad_show_fail_reason` (`not_allow_show` và `policy_min_gap`), trước đây không gửi gì; log
  `showInterstitial not allow show` đổi thành `showInterstitial refused: <lý do>`. Số event `ad_show_fail_reason`
  trên dashboard tăng theo (mỗi lượt gọi bị chặn một event), không có impression nào đổi.
  `isInterstitialReadyToShow` giờ xét đúng cùng luật thời gian/phiên với `showInterstitial`.
- `setPostAdScreenCooldown(n)`: khi `ads_policy_android` có `screens_before_interstitial` thì khoá đó thắng
  (log debug `ads cooldown: app đặt …`); gỡ khoá thì về giá trị app đặt gần nhất. Không có khoá (mặc định)
  thì như cũ.
- Tên event: `reloadSplash_acnull` → `reload_splash_ac_null`, `setAc_waitreloadsplash` →
  `set_ac_wait_reload_splash`, `ads_<tên lớp><adsType>` → `ads_pl_<eventTag>_<adsType>` (xem "Phải làm khi
  nâng cấp"). Tên event viết thẳng ở chỗ gọi `logEvent`/`logEventToServer` nằm trong `EventNames` và được khoá
  bằng `EventNameContractTest` (≤ 40 ký tự, `^[a-z][a-z0-9_]{0,39}$`, không tiền tố `firebase_`/`google_`/`ga_`;
  chỗ gọi không viết literal); ngoại lệ đóng, **đóng băng** (#5, DOC/66): năm mốc tROAS `AdLTV_OneDay_Top*Percent`
  (Google Ads nhập làm conversion theo tên), khoá từng ký tự, đúng mốc `ThresholdTopAdRev`, không literal ngoài
  `EventNames` (`EventNameContractTest`, `FirHelperAdLtvEventTest`). Tám event thời gian fetch Remote Config `fir_rm_fet_{1,2,3,4,5,6,8,9}s` (`FirHelperConfig`,
  trước đi qua một biến) nay cũng nằm trong `EventNames` (D4), **tên không đổi** (không có `7s`, `9s` là trên 8 s),
  mốc giữ nguyên (`EventNames.remoteConfigFetchBucket`). Chưa phải mọi tên: tên ghép lúc chạy `aad_*`, `show_ads_*`
  nằm ngoài tầm kiểm tĩnh (DOC/65 G6, follow-up).
  Mỗi lớp placement khai `eventTag` hằng chữ (`mob_full`, `fb_bn`, `max_open`, …) thay cho tên lớp; log debug
  của placement đổi từ `ads <TênLớp><adsType> …` sang `ads <eventTag>_<adsType> …`. Param `kill` của
  `sdk_health` được khoá dạng: `none`, `version`, hoặc các module bị tắt nối bằng dấu phẩy theo thứ tự
  `ads,iap,logevent`.
- **`AdsPlacementConfig.cbLoad` đọc ra `null` sau khi placement báo `load timeout`** (Task I2, đợt sửa review I-1) tới
  request kế của app: lượt load kế bật `isLoading`, hoặc gán `cbLoad` mới (`showRect` lấy rect từ cache báo `onLoaded`
  ngay). Chữ ký không đổi; app đọc trực tiếp `cbLoad` (hiếm) thấy `null` trong cửa sổ đó. Mục đích: callback muộn của
  lượt đã báo `load timeout` (mạng callback sau 90 giây, vòng banner) không tới app lần hai.
- **`AdsBase.hasLoadedAd(placement)` (và `AdsPlBase.hasLoadedAd()`) trả `false` khi MAX đang tự nạp lại** (Task J4):
  MAX interstitial, rewarded, app open đang nạp lại trong cùng đối tượng sau hết hạn (`isReady() == false`) không còn
  là "đã load" — trước là `true` rồi `show` trả `false`. Chữ ký không đổi; quảng cáo không bị bỏ; nạp lại xong thì
  `true` trở lại. Chi tiết ở "Sửa" (mục `hasLoadedAd` nhất quán với `isReady()`).

### Thêm

- `ConsentState.TierStore`, `ConsentState.installStore(store)`, `ConsentState.restore()`.
- `ConsentState.allowsProfiling(gdprApplies: Boolean?)`, `ConsentState.allowsPersonalization(gdprApplies: Boolean?)`
  (xem "Phải làm khi nâng cấp").
- `removeAdsMasks`: mask gỡ quảng cáo theo từng sản phẩm (Điều 13.4) — `MonetizationSdk.Config.removeAdsMasks`,
  `Entitlements.setRemoveAdsMasks(masks)`, `Entitlements.removeAdsMask()`. Gói rẻ khai
  `RemoveAdsMask.INTERRUPTIVE` chỉ gỡ banner, native collapsible, rect và toàn màn hình; rewarded và native
  inline vẫn còn.
  Sản phẩm trong `removeAdsProducts` không có mask riêng vẫn gỡ hết như cũ. `hasRemoveAds()` giờ đúng
  với mọi mask khác `NONE`. Mask được lưu (`PreferenceHelper`, khoá `entitlements_remove_ads_masks`);
  mục đã lưu bị hỏng được log và sản phẩm đó không gỡ gì cho tới lần `configure` kế tiếp, không bao giờ
  rơi về `ALL`. `configure` cảnh báo id trong `removeAdsMasks` không có trong danh sách sản phẩm.
  `AdsHelper.isRemoveAds()` không tham số chỉ đúng khi mask là `ALL`; app có gói rẻ ẩn chỗ quảng cáo theo từng
  định dạng bằng `AdsHelper.isRemoveAds(flag)` hoặc `RemoveAdsMask.isRemoved(Entitlements.removeAdsMask(), flag)`
  (không dùng `hasRemoveAds()` để ẩn mọi chỗ: nó ẩn cả nút rewarded của người mua gói rẻ; doc 45).
- Integer resource `ads_admob_firebase_linked` (doc 62 mục 2.3): app đã liên kết AdMob với Firebase
  trên console thì khai `<integer name="ads_admob_firebase_linked">1</integer>`, SDK thôi gửi
  `ad_impression` của AdMob lên Firebase — Firebase đã tự ghi, gửi thêm là đếm đôi doanh thu.
  AppsFlyer, sự kiện hiển thị và log server vẫn gửi như cũ; AppLovin MAX không đổi. Không khai, khai
  giá trị khác 1 hay đọc lỗi thì SDK vẫn gửi như trước. Tên đã có trong `appplugin_keep.xml`.
  **Mediation:** khai `1` thì SDK bỏ mọi `ad_impression` đi qua AdMob, kể cả impression của mạng bên thứ
  ba qua mediation AdMob; chưa kiểm Firebase có tự ghi đủ doanh thu của các impression đó không. App có
  waterfall bên thứ ba qua mediation AdMob kiểm DebugView trước khi khai `1` (doc 62 mục 2.3).
- Kill switch của ops qua Remote Config `sdk_kill_android` (doc 60 G), ví dụ
  `{"ads":true,"iap":false,"logevent":false,"below_version":"1.4.1"}`. `ads`: không khởi tạo mạng quảng
  cáo, không load, không show; lượt bị chặn mang nhãn `show_blocked_kill_switch` (telemetry
  `ad_show_fail_reason`, `err` của `onAdShowFail` của native và `showBanner`, và của `onLoadFailed`), trừ
  khi người dùng đã mua gỡ định dạng đó (giữ nhãn remove-ads). Mọi hàm load có `AdsLoadCb` nhận
  `onLoadFailed` đúng một lần ngay ở cổng (xem mục "Đổi"). Show interstitial, app open, rewarded vẫn chỉ
  trả `false`, không callback (vẫn gửi telemetry). `iap`: không kết nối Play Billing (mua trả lỗi "chưa sẵn sàng", quyền lợi
  đã lưu giữ nguyên; báo cáo của `MonetizationSdk.configure` có cảnh báo "billing bị kill switch").
  `logevent`: không gửi event lên server của SDK (decision log giữ bản ghi trên đĩa). `below_version`:
  bản SDK nhỏ hơn ngưỡng tắt cả ba; ngưỡng phải là số chấm số (`1.4.1`), sai dạng thì bị bỏ (có log);
  bản pre-release (`1.4.1-rc1`) tính là nhỏ hơn `1.4.1`. Mỗi trường đọc riêng: trường sai kiểu (`"ads":1`,
  `"ads":"yes"`, `"below_version":null` hay object) chỉ bỏ trường đó, log `kill switch: '<trường>' = …`, các
  trường khác vẫn áp; `ads`/`iap`/`logevent` nhận `true`/`false` (cả chuỗi `"true"`/`"false"`). Chỉ tắt,
  không cấp gì: JSON hỏng hay khoá vắng là không tắt gì.
  **Độ trễ:** giá trị áp ngay khi Remote Config về (chặn load/show từ lúc đó) và được lưu (`PreUtil`,
  khoá `mem_sdk_kill_android`); tắt khởi tạo mạng và billing có hiệu lực từ lần mở app kế tiếp. Gỡ khoá
  cũng vậy: phiên đã bỏ khởi tạo mạng không khởi tạo lại giữa chừng, và quảng cáo của phiên đó vẫn tắt
  (nhãn kill; mọi hàm load có `AdsLoadCb` nhận `onLoadFailed` ngay, `showBanner` nhận `onAdShowFail`) tới
  lần mở kế. Kill tới trong phiên đầu tiên, trước câu trả
  lời consent: load app gọi trước consent đang chờ mạng khởi tạo thì không chạy; từ Task I2 app nhận `onLoadFailed` sau
  60–90 giây (`load timeout` từ watchdog placement, hoặc `list empty` khi slot cuối hết hạn) thay vì kẹt "đang load" tới
  lần mở kế. Log: `kill switch: …`.
- Crashlytics custom key `appplugin_version` (phiên bản SDK), đặt trong `AppPluginManager.initPlugin`
  ngay sau `FirebaseApp.initializeApp`.
- Event Firebase `sdk_health`, một lần mỗi process khi mạng quảng cáo khởi tạo (hoặc bị kill switch bỏ
  qua): `sdk_version`, `consent_tier`, `consent_gdpr` (`true`/`false`/`unknown`), `networks` (mạng được
  yêu cầu khởi tạo, `admob,meta[,max]`, không phải mạng đã khởi tạo xong; hoặc `killed`),
  `since_request_ms` (từ lần đầu app gọi `startSdkInit` tới lúc **bắt đầu** khởi tạo mạng, tức phần lớn là
  thời gian chờ consent, không gồm thời gian khởi tạo), `kill` (`none`, `version`, hoặc module bị tắt nối
  bằng dấu phẩy). Như mọi sự kiện Firebase Analytics, không gửi khi thu thập bị tắt theo consent.
- Hàng đợi event của `LogEventServer` (gate G6, doc 65): tối đa 500 event chờ, đầy thì bỏ event cũ nhất
  (log lần đầu và mỗi 100 lần bỏ); tối đa 4 request đang bay; lỗi thử lại được thì backoff 1 s, 2 s, 4 s…
  tới 60 s, cả hàng chờ theo (event mới không làm gửi lại sớm), mở lại ngay khi có request thành công; mỗi
  event thử lại tối đa 5 lần; 4xx (trừ 408, 429) bỏ ngay, không thử lại. Body của response luôn được đóng.
  Backoff đo bằng đồng hồ đơn điệu, nên người dùng chỉnh giờ máy không làm hàng đợi đứng.
- Luật tần suất một chỗ (doc 62 F): khoá Remote Config `ads_policy_android`, JSON `schema_version` 1.
  **Mặc định giữ nguyên hành vi 1.3.1** (DOC/65 §7 #1, #2 chưa chốt, áp tạm), nên bản này không có mục "Phải
  làm khi nâng cấp" nào cho nó: không đặt khoá thì nhịp quảng cáo như cũ. Các trường, mặc định và khoảng kẹp:
  `no_interstitial_first_session` (`false`; `true` = lần mở app đầu không interstitial),
  `app_open_from_session` (1 = mọi phiên; 1–100; `full_splash` được miễn), `interstitial_min_gap_seconds`
  (vắng = `fullDeltatime` của `ads_region_android`, 30 s khi chưa đặt; 0–3600; chặn khi chưa qua **hơn** N giây,
  như `fullDeltatime`), `screens_before_interstitial` (vắng = giá trị app đặt bằng `setPostAdScreenCooldown`,
  mặc định 0 = tắt; 0–50), `post_click_cooldown_seconds` (vắng = 5 s; 0–60; Full2 tự động sau click),
  `app_open_max_age_minutes` (240; 1–240, dài hơn 4 giờ chỉ hiện quảng cáo Google không tính),
  `fullscreen_max_age_minutes` (60; 1–240; tuổi tối đa của interstitial, rewarded, native full, trừ MAX interstitial
  và rewarded; 61–240 là núm lùi có chủ ý, đã có thể hiện quảng cáo GMA coi là hết hạn; mặc định này khác
  1.3.1, vốn không cho chúng hết hạn, xem "Đổi"). Luật doc 62 F bật bằng: `{"schema_version":1,"no_interstitial_first_session":true,"app_open_from_session":2,"interstitial_min_gap_seconds":240}`
  (thêm `"screens_before_interstitial":4` chỉ cho app có gọi `AdsHelper.recordScreenChange()`).
  **Phiên** ở đây là một lần process chính khởi động (`AppPluginManager.initPlugin` tăng bộ đếm
  `mem_app_session_count`), kể cả process khởi động nền (push, WorkManager) mà người dùng không mở màn hình
  nào; không phải phiên 30 phút của `LogEventServer`. Nên `app_open_from_session` và
  `no_interstitial_first_session` có thể "hết" sớm hơn số lần người dùng thật sự mở app.
  Luật phiên và khoảng cách chỉ áp cho `showInterstitial` với `isDeltaTime` (mặc định; cả
  `isInterstitialReadyToShow`) và `showOpenAd`; `isDeltaTime = false` (splash/intro), rewarded và chuỗi Full2
  không bị chúng chặn. Lượt bị chặn trả `false`, không callback, gửi một `ad_show_fail_reason`:
  `policy_first_session`, `policy_min_gap`, `policy_app_open_session`.
  Parse từng trường như `sdk_kill_android`: trường sai kiểu bị bỏ, số ngoài khoảng bị kẹp, khoá lạ bị bỏ, mỗi
  lần một log lỗi `ads policy: …`; JSON hỏng hay `schema_version` khác 1 thì dùng mặc định (hành vi cũ). Policy
  `screens_before_interstitial` > 0 mà app chưa gọi `recordScreenChange()` lần nào trong process thì cooldown
  màn hình không bật (log lỗi một lần), để app không báo màn không bị chặn quảng cáo vĩnh viễn.
  **Độ trễ:** giá trị áp ngay khi Remote Config về và được lưu (`PreUtil`, khoá `mem_ads_policy_android`),
  lần mở kế áp bản đã lưu từ `initAdsHelper`, trước khi Remote Config về; gỡ khoá là về mặc định. Log
  `ads policy schema=…` mỗi lần áp. API: `AdsPolicy` (`parse`, `DEFAULT`, `current`), `AdsHelper.adsPolicy`,
  `AdsHelper.applyPolicy(policy)` (app tự gọi thì lần Remote Config về sau ghi đè).
- `Constants.AdShowFailReason.AD_EXPIRED` (`ad_expired`): nhãn chung khi quảng cáo quá tuổi tối đa mà placement
  không khai nhãn riêng (interstitial, rewarded, native full); app open giữ `app_open_expired`.
- `AdsPolicy.fullscreenMaxAgeMinutes`, `AdsPolicy.fullscreenMaxAgeMillis()`,
  `AdsPolicy.DEFAULT_FULLSCREEN_MAX_AGE_MINUTES` (60): khoá `fullscreen_max_age_minutes` của `ads_policy_android`.
- Khoá `gma_load_app_context` (boolean, **mặc định `false`**) trong JSON `ads_policy_android` (DOC/65 §7 #18 phương án
  (b)): `true` thì interstitial, app open, rewarded và native (native thường, native collapsible, native full) của AdMob
  **nạp** bằng `applicationContext` thay cho Activity; hiện quảng cáo vẫn bằng Activity, banner/rect không đổi, placement
  chưa có Activity vẫn trả `activity null` như cũ. Lý do: G9 (DOC/65), GMA giữ Activity đã truyền lúc nạp (WebView, registry
  OMID), nên Activity cũ tích dần qua mỗi lần xoay màn hình; SDK không nhả được từ phía mình. **Rủi ro:** vài adapter
  mediation cần Activity lúc nạp — adapter Meta (`com.google.ads.mediation:facebook`, Speaker Clean và Invoice Maker đang
  dùng) tạo quảng cáo từ context lúc nạp nên quảng cáo Meta qua mediation sẽ mở từ Application; Teads, Equativ không nạp được
  với app context. Khuyên bật thử theo nhóm người dùng (Remote Config condition) rồi so fill và eCPM với nhóm tắt trước khi
  bật rộng. Chỉ JSON boolean `true` bật; thiếu khoá, sai kiểu (kể cả chuỗi `"true"`, có log `ads policy: 'gma_load_app_context' …`),
  JSON hỏng hay `schema_version` khác 1 là `false`. Đi cùng đường áp của `ads_policy_android` (áp khi Remote Config về, lưu
  `mem_ads_policy_android`, lần mở kế áp bản đã lưu) và đọc lại ở mỗi lần nạp, nên đổi giữa phiên thì lần nạp sau theo giá
  trị mới; `AdsHelper.applyPolicy(policy)` do app gọi không đổi cờ này. Log `ads policy gma_load_app_context=…` mỗi lần áp,
  và `ads gma load context=application` mỗi lần nạp khi cờ bật. Không thêm API public (`AdsPolicy` không đổi).

### Xoá

- `AppsflyerHelper.initHelper(context)`: SDK tự start AppsFlyer sau consent (xem "Phải làm khi nâng cấp").

### Sửa

- Manifest của SDK khai `android.permission.WAKE_LOCK` (`FirebaseAnalytics.getInstance` cần nó; `:lintDebug`
  của CI báo lỗi `MissingPermission`). App vốn đã có quyền này qua manifest của play-services-measurement, nên
  manifest đã merge của app không đổi.
- Event gửi trước `LogEventServer.init` vẫn bị bỏ như trước, nhưng giờ có `logE` một lần mỗi process
  (`LogEventServer not initialized: dropping events until init (first: …)`); trước đây chỉ có log debug,
  câm ở bản release.
- Replay decision log lúc mở app (`attachQueue`, chạy trước `LogEventServer.init` trong `AppPluginBase`) chờ
  `LogEventServer.init`, và `init` tự gọi `DecisionLogger.flushQueue()`: backlog được gửi ngay trong lần mở
  đó, không còn bị bỏ (kèm `logE` "not initialized" giả) rồi chờ tới phiên sau.
- `AppPluginManager.initPlugin` không còn gọi `context.filesDir` trên main thread để gắn hàng đợi decision log:
  lần gọi đầu của hàm đó kiểm/tạo thư mục (một vi phạm StrictMode DiskRead, `File.exists` trong
  `ContextImpl.ensurePrivateDirExists`, emulator API 37 cài mới). Hàng đợi vẫn gắn ngay trên luồng gọi, vào
  cùng thư mục `files`, với đường dẫn dựng từ `applicationInfo.dataDir` (không đụng đĩa); thư mục chưa có thì
  lần ghi bản ghi đầu tạo. Hành vi khác giữ nguyên: bản ghi xuống đĩa trước khi gửi, `queuedCount()` là `-1`
  tới khi đếm xong.
- `DecisionLogQueue` không còn mất cả hàng đợi decision log mà không log khi đọc file lỗi: trước đây
  `peekAll` nuốt lỗi đọc thành danh sách rỗng và `removeAll` ghi lại file rỗng. Giờ `peekAll()` trả
  `List<String>?` — `null` khi file có mà không đọc được (log `decision log read failed`); `removeAll` khi
  đọc lỗi không ghi gì, log `decision log remove skipped: queue unreadable` và trả `-1` (ghi lỗi mà đọc lại
  cũng lỗi cũng trả `-1`, không còn trả `0` như thể file đã ngắn đi);
  `DecisionLogger` giữ nguyên `queuedCount()` khi gặp `-1`, và flush bỏ lượt (log) khi không đọc được.
- Lúc cold start, 4 file SharedPreferences (SDK, IABTCF, UMP) nạp trên luồng nền từ `attachBaseContext` thay vì trên
  main (trước: 5 vi phạm StrictMode API 37, 8 API 28/26); `onCreate` chờ ≤ 250 ms (`prefs warmup waited=N ms`), hết
  hạn thì đọc như trước.
- Interstitial hết hạn (D5) bị bỏ ở `isInterstitialReadyToShow` không còn làm placement dừng hẳn (review E I2): cờ
  đã load của placement hạ ngay (`isInterstitialLoaded` trả `false`) và SDK tự nạp lại placement đó đúng một lần
  (`loadInterstitialForThisTurn`, không nạp thêm khi đang load). Trước đây cờ kẹt ở `true` khi không còn quảng cáo
  nào, nên mẫu "ready → show, else if not loaded → load" (Speaker Clean `MainActivity`) không bao giờ load lại.
  Cùng cách ở `getNativeFullForShow` (native full hết hạn → `null`, hạ cờ, `loadNativeFullForThisTurn` một lần) và
  khi `showInterstitial`/`showRewarded` không mạng nào hiện được (hạ cờ trước lần nạp lại vốn có). Cờ đã load bật mà
  không mạng nào còn quảng cáo vì lý do khác (quảng cáo đã dùng) cũng được hạ và nạp lại như vậy.
  `isInterstitialReadyToShow` không phải phép đọc thuần (bỏ quảng cáo quá tuổi, `destroy()` native full, gửi
  `ad_expired`): **gọi trên main thread**; gọi ngoài main thì `logE` một lần mỗi process rồi vẫn xử lý.
  Tham số `flagImage` của `isInterstitialReadyToShow` phải trùng giá trị đã dùng khi load
  (`loadInterstitialForThisTurn`) và khi show: khác nhau thì hàm trả `false` dù `isInterstitialLoaded` `true`, và
  không nạp lại (placement vẫn có mạng giữ quảng cáo), tới khi quảng cáo quá tuổi (60 phút; interstitial MAX không
  có tuổi) hoặc được show.
- `showNtCl` có cổng remove-ads/tắt định dạng như `loadNtClForThisTurn` (`isRemoveAds(1) || isDisableAds(1)`): NtCl
  đã load trước khi gói gỡ quảng cáo (kể cả gói rẻ `INTERRUPTIVE`, #15) được cấp trong cùng phiên không còn hiện;
  gửi `ad_show_fail_reason` = `remove_ad` (kill switch: `show_blocked_kill_switch`), không callback (review E M3).
- Log `ads policy schema=…` ghi thêm `fullscreenMaxAge=<n>min` (giá trị đang áp); log bỏ quảng cáo quá tuổi dùng
  nhãn `<eventTag>_<adsType>` thay tên lớp (R8 đổi tên lớp ở bản release).
- FCM auto-init (cả nhánh hold lúc khởi tạo và nhánh follow sau khi consent đổi) chạy trên luồng nền
  `appplugin-fcm` thay vì trên main (D1, DOC/66): Firebase tạo `Store` (`getNoBackupFilesDir`, `File.exists`,
  `createNewFile`) đồng bộ ngay khi cờ auto-init được đặt, gây 3 vi phạm StrictMode DiskRead/DiskWrite trên máy
  thật mà emulator không lộ (thư mục đã có sẵn). Token vẫn được lấy sau, trên cùng luồng nền.
- `LogEventServerHelper.init` chạy trước `LogEventServer.init` trong `AppPluginBase.initPlugin` (D2, DOC/66):
  replay decision log lúc mở app không còn có thể mang các trường (`app_name`, `country`, `session_id`) rỗng
  do thứ tự khởi tạo ngược trước đây.
- App open MAX không còn bị SDK coi là hết hạn sau 4 giờ (Task I1): MAX tự hết hạn và nạp lại trong cùng
  `MaxAppOpenAd`, nên quảng cáo MAX còn tươi không bị từ chối nhầm, không `app_open_expired` giả, không `destroy()`.
- Slot mạng không bao giờ callback không còn kẹt "đang load" mãi (Task I2): watchdog 60 giây của slot (ngắn hơn 90 giây
  của placement) báo `onLoadFailed(..., "load timeout")` đúng một lần (waterfall đi tiếp sang mạng sau), log
  `ads <eventTag>_<adsType> … load timeout after 60000 ms`, slot nhận load mới, và lượt giữ chờ mạng khởi tạo đã bị bỏ
  thì không chạy nữa khi mạng sẵn sàng. Trước đây cờ đã load của placement bị giữ mãi (`isInterstitialLoaded` `true` mà
  không quảng cáo nào sẵn sàng). Watchdog 90 giây của placement cũng chặn callback muộn của lượt nó đã báo `load timeout`
  (trước đây app có thể nhận `onLoaded`/`onLoadFailed` thứ hai), và log `ads placement … load timeout after 90000 ms`.
  **Callback muộn** (đợt sửa review Task I): listener load của 17 lớp mạng (interstitial, rewarded, app open, native,
  native collapse, native full của AdMob, MAX, Meta) chụp mã lượt của slot lúc gửi request và bỏ callback tới sau khi
  lượt đó bị bỏ hay slot đang rảnh — kể cả khi slot đang chạy lượt mới trên cùng ad unit (trước đây lượt mới nhận kết
  quả của request cũ: `onLoaded` hai lần, hoặc bỏ qua một ad unit), và kể cả app open MAX hết hạn khi app ở nền báo
  `onAdLoadFailed(MISSING_ACTIVITY)` không có request nào chờ (trước đây gửi request tự phát). Quảng cáo tới muộn: mục
  waterfall đang rảnh thì giữ làm quảng cáo đã load (lần load sau trả ngay); mục đang có request mới thì `destroy`
  (Meta, native) hoặc bỏ. Log `… late load callback dropped …`, `… late ad kept/dropped …`. Vòng waterfall của
  `AdsHelper` (rect, native, native collapse, app open, native full, interstitial, Full2, rewarded) gắn với lượt load của
  placement (`AdsPlacementConfig.loadCycle`, cùng cách `BannerLoadGuard`): vòng cũ tỉnh dậy (slot chờ khởi tạo hết hạn
  sau khi placement đã báo `load timeout` và app đã load lại) không hạ `isLoading`, không gọi `cbLoad` của lượt mới —
  trước đây app retry sau `load timeout` nhận `onLoadFailed` hai lần cho request thứ hai (deterministic khi load trước
  consent với hai mạng cùng giữ). `showRect` lấy rect từ cache sau một lần placement hết hạn vẫn báo `onLoaded`
  (trước đây mất, xem `cbLoad` ở "Đổi"). Test: `LoadCycleGuardTest`, `LateLoadCallbackTest`, `SlotLoadWatchdogTest`.
- Interstitial và rewarded Meta được `destroy()` khi đóng (Task I3), như nhánh hết tuổi; trước đây chỉ bỏ tham chiếu.
  Không destroy lúc đang hiện. Native full Meta vốn đã destroy khi đóng.
- MAX interstitial, rewarded và app open kiểm `isReady()` trước `showAd` (Task I4): MAX đang tự nạp lại sau khi hết
  hạn (hoặc nạp lại lỗi) thì mạng đó trả `false` như lượt "show not loaded" (không callback, vòng show đi tiếp sang
  mạng sau), gửi `ad_show_fail_reason` = `show_not_loaded`, log `… show not ready`; trước đây `showAd` được gọi và MAX
  báo display fail.
- Host bị huỷ không còn giữ callback show của app (thường giữ Activity cũ) ở placement không đang hiện (Task I5, G9):
  `clearActivity` bỏ `cbShow` của mọi `AdsPlacementConfig` không đang hiện sau lượt 2 (log `released <n> idle show
  callbacks`); trước đây `cbShow` của quảng cáo cha còn giữ tới lượt show kế. Quảng cáo đang hiện (interstitial GMA sống
  qua xoay) giữ callback, dismiss vẫn tới app đúng một lần.
- FCM: xoá token và lấy token không còn chạy đua (Task I6, review C Minor 3). Cả hai là Task bất đồng bộ: cấp lại consent
  khi `deleteToken()` chưa xong thì chỉ lấy token sau khi xoá xong (thành công hay lỗi); rút consent khi đang lấy token
  thì chỉ xoá sau khi lần lấy xong; rút rồi cấp lại (hay ngược lại) trước khi việc đang chạy xong thì hai việc chờ huỷ
  nhau. Cờ auto-init vẫn theo tầng ngay. Log `fcm token … deferred until … completes`. Trước đây trong ~1 giây quanh một
  lượt rút/cấp lại có thể còn token cũ, hoặc token vừa xoá được cấp lại.
- Banner và rect (6 lớp: `AdsMobPlBanner`, `AdsMobPlRect`, `AdsMaxPlBanner`, `AdsMaxPlRect`, `AdsFbPlBanner`,
  `AdsFbPlRect`) có guard mã lượt trong listener (Task J1, đóng giới hạn còn lại của đợt sửa review Task I): callback
  đầu tiên của mỗi listener là response của request đang chờ và chụp `loadGeneration` lúc gửi; lượt đó đã bị watchdog
  slot bỏ (hoặc lượt mới đã bắt đầu) thì response không tới `adsLoadCb` của lượt mới — view muộn giữ làm quảng cáo đã
  load nếu mục rảnh và trống (lần load sau trả ngay, không request mới), không thì `destroy()`; view không được giữ
  thì mọi callback sau của nó (refresh/retry) bị bỏ. Auto-refresh của view đang hiện vẫn `onLoaded` mỗi lần như cũ.
  Trước đây response muộn (> 60 giây) của request đã bỏ tới `adsLoadCb` của lượt mới như một lần refresh (`onLoaded`
  thừa, hoặc `onLoadFailed` thừa khi lượt mới đang chờ). Getter-null của `AdsPlacementEcmpBase.adsLoadCb` và
  `AdsPlacementConfig.cbLoad` giữ lại: chúng chặn đường refresh (đa callback theo thiết kế) trong cửa sổ từ lúc
  watchdog báo `load timeout` tới request kế, đường mà guard J1 cố ý không đụng. Test: `LateBannerLoadCallbackTest`.
- FCM (Task J2): lần xoá lại token lúc mở app (cờ `mem_fcm_delete_pending`, `holdAutoInitUntilConsent`) đi cùng làn
  token với `FcmConsent.Follower`, nên lần lấy token sau consent trong cùng phiên chờ lần xoá đó xong (trước đây hai
  việc chạy đua: token có thể được lấy rồi bị xoá ngay sau). Việc token đang bay (lấy hoặc xoá) có hạn 30 giây: Task
  của Firebase không bao giờ hoàn tất thì sau hạn việc đó coi là xong-thất-bại (`logE fcm token … did not complete
  within 30000 ms; treated as failed`), việc đang chờ chạy tiếp; completion tới muộn sau hạn bị bỏ. Trước đây làn kẹt
  mãi (review Task I M2). Hẹn giờ chạy trên chính luồng `appplugin-fcm`.
- App open MAX được `destroy()` khi đóng (Task J3, như Meta I3): mỗi lần load tạo `MaxAppOpenAd` mới, đối tượng đã hiện
  xong không dùng lại; `onAdHidden` gọi `destroy()` sau khi bỏ tham chiếu và hạ `isShowing`, trước callback dismiss của
  app; không destroy lúc đang hiện. Quảng cáo app open MAX tới muộn bị bỏ (`keepLateAd`) cũng được `destroy()`.
- `hasLoadedAd` (`AdsPlBase`, `AdsBase`) nhất quán với `isReady()` của MAX (Task J4, review Task I concern 4): MAX
  interstitial, rewarded, app open đang tự nạp lại trong cùng đối tượng (`isReady() == false`) không còn được coi là đã
  load, nên `isInterstitialReadyToShow` trả `false` thay vì `true` rồi `show` trả `false` (Task I4). Quảng cáo không bị
  bỏ và không có request thừa: cờ đã load của placement giữ nguyên (`isInterstitialLoaded` vẫn `true` — mạng còn giữ
  đối tượng đang nạp lại được coi là bận trong `dropStaleLoadedFlag`), app theo mẫu ready → show / !loaded → load / chờ
  sẽ chờ tới khi MAX nạp lại xong. Hook internal `AdsPlBase.isLoadedAdReady`, chữ ký `hasLoadedAd` không đổi. Test:
  `AdsHelperMaxReadyTest`.
- MAX banner và rect không còn `destroy()` chính view đang hiện khi auto-refresh (review Task J IMP-1, lỗi có từ
  trước): MAX nạp lại trong cùng `MaxAdView` và gọi `onAdLoaded` trên cùng listener mỗi lần refresh; nhánh refresh của
  `AdsMaxPlBanner`/`AdsMaxPlRect` trước đây `destroy()` `adObject` — chính view đó — rồi gán lại, nên banner MAX đang
  hiện trống (nền đen) và ngừng refresh ngay lần refresh đầu (rect: lần thứ hai, vì `getAdForShow` bỏ `adObject`).
  Nay chỉ destroy view khác đang giữ. Test: `LateBannerLoadCallbackTest` (MAX banner, MAX rect).
- MAX interstitial, rewarded, app open tự nạp lại lỗi không còn làm placement kẹt "đã load mà không sẵn sàng" cả phiên
  (review Task J IMP-2): MAX hết hạn → tự nạp lại trong cùng đối tượng → lỗi (no fill, offline, app open thiếu
  Activity) → `onAdLoadFailed` tới khi không có request nào chờ; MAX không tự thử lại. Trước đây callback đó bị bỏ hẳn
  (đợt sửa review Task I, M1) và đối tượng ở lại với `isReady() == false`: `isInterstitialReadyToShow` `false` mà
  `isInterstitialLoaded` `true` tới hết process, không lần `loadAd()` nào nữa (trước Task I: waterfall vô tình nạp lại).
  Nay đối tượng hỏng bị bỏ khỏi mục (app open: kèm `destroy()`), không callback, không đẩy waterfall; lần
  `isInterstitialReadyToShow`/`load` kế gửi `loadAd()` thật (interstitial/rewarded: trên cùng đối tượng; app open: đối
  tượng mới). Log `held ad dropped: self-reload failed`. Test: `AdsHelperMaxReadyTest`.
- Banner/rect: response muộn của request đã bỏ (và callback của view không được giữ) không còn được đếm là load/fail
  trong `ad_load` (review Task J Minor 1: analytics gọi sau guard, như 17 listener toàn màn hình/native). FCM: lần xoá
  lại token lúc mở app nếu xếp sau lần lấy token của `Follower` (tầng cho phép lại trong lúc xếp hàng) kiểm lại tầng
  khi tới lượt, đã cho phép thì không xoá token vừa lấy (review Task J Minor 2; log `fcm token delete retry at launch
  skipped`).
- MAX banner và rect: auto-refresh của view đã lấy ra hiện, tới sau khi mục đã nạp view mới của lượt sau (nhánh "not
  turn"; gặp ở rect: `getAdForShow` bỏ `adObject`, lần load sau nạp view mới vào cùng mục), không còn `destroy()` view
  mới đó rồi giữ lại view cũ (Task K mục 1, re-review Task J Minor 1): như GMA, chỉ gán khi mục trống. Trước đây fill
  mới (đã trả tiền) bị vứt, "quảng cáo đã load" là view cũ đang hiện. Test: `LateBannerLoadCallbackTest`.
- Quảng cáo tới muộn chỉ được giữ làm quảng cáo đã load khi sẵn sàng hiện (`AdsPlBase.keepLateAd` kiểm
  `isLoadedAdReady`; Task K mục 2, re-review Task J Minor 2): đối tượng MAX `isReady() == false` (app open đã
  `destroy()`, interstitial vừa tự nạp lại lỗi) bị bỏ thay vì giữ lại; trước đây chỉ được chặn nhờ MAX gỡ listener khi
  `destroy()`. GMA/Meta không đổi. Test: `LateLoadCallbackTest`.
- FCM: lần xoá lại token lúc mở app đọc lại `IABTCF_gdprApplies` khi tới lượt (trên `appplugin-fcm`), không dùng giá
  trị chụp lúc gọi `holdAutoInitUntilConsent` (Task K mục 3, re-review Task J Minor 3): tầng UNKNOWN mà UMP ghi
  `gdprApplies = 0` (ngoài GDPR) trong lúc việc xoá xếp sau lần lấy token thì không còn xoá token vừa lấy. Test:
  `FcmConsentTest`.
- Banner/rect (GMA, MAX, Meta): mã lượt load (`AdsEcmp.turnLoadId`) là bộ đếm của mục, tăng 1 mỗi request, thay cho
  `System.currentTimeMillis()` (Task L mục 1, review Task K Minor 4): `showRect(); loadRect4Show()` trong cùng một
  mili giây không còn cho hai request chung mã lượt. Test: `LateBannerLoadCallbackTest`.
- MAX banner và rect: nhánh refresh cùng lượt không còn `destroy()` view khác đang giữ rồi gán view đang refresh (Task L
  mục 2, review Task K Minor 1): như GMA và nhánh "not turn", chỉ gán khi mục trống. Test: `LateBannerLoadCallbackTest`.
- Banner/rect (GMA, MAX, Meta): auto-refresh (thành công hoặc lỗi) của view đang hiện không còn gọi `adsLoadCb` khi
  slot đang có request chờ (Task L mục 3, review Task K Minor 2): trước đây helper coi refresh đó là kết quả load của
  request mới (hạ loading, huỷ watchdog) và app có thể thấy `onLoaded` rồi `onLoadFailed` cho một lần load. Callback kết
  thúc của chính request là callback duy nhất; slot rảnh thì refresh vẫn `onLoaded` như cũ. Test:
  `LateBannerLoadCallbackTest`.
- FCM: việc xoá lại token lúc mở app (có thể chờ trong làn token tới 30 s+) chỉ giữ `context.applicationContext`, không
  giữ context truyền vào `holdAutoInitUntilConsent` (Task L mục 4, review Task K Minor 3). Test: `FcmConsentTest`.
- Banner/rect (GMA, MAX, Meta): auto-refresh của view đang hiện không còn gán view đó vào mục trống khi slot có request
  đang chờ (Task M mục 2, review Task L Minor 2): không còn `LOAD_FAIL_BUT_ADS_NOT_NULL` giả trong lúc chờ, và view của
  request tới muộn sau watchdog được giữ làm quảng cáo đã load thay vì bị destroy. Test: `LateBannerLoadCallbackTest`.
- GMA (interstitial, app open, rewarded, native, NtCl, native full): quảng cáo tới muộn sau watchdog slot mà
  `keepLateAd` giữ làm quảng cáo đã load nay có `OnPaidEventListener` như quảng cáo thường, nên khi hiện doanh thu được
  ghi (`logAdRevAdmob`: Firebase `ad_impression`, tROAS, AppsFlyer) đúng một lần (final review IMP-1). Test:
  `LateLoadCallbackTest`.
- NtCl (GMA, MAX, Meta): native ad tới muộn mà `keepLateAd` không giữ (mục đang có request mới hoặc đã giữ quảng cáo
  khác) nay bị destroy thay vì bị bỏ rơi (final review Minor 1). Test: `LateLoadCallbackTest`.
- Banner/rect (GMA, MAX, Meta), thứ tự ngược của Task M mục 2: view đang hiện refresh lúc slot rảnh (gán vào mục trống,
  `isLoaded = false`) rồi app load — `load` bỏ tham chiếu đó khỏi mục trước khi xét quảng cáo đã load (không destroy
  view đang hiện), nên đường load không còn `LOAD_FAIL_BUT_ADS_NOT_NULL` giả, và view của request mới tới muộn sau
  watchdog được giữ thay vì bị destroy (final review Minor 2). Test: `LateBannerLoadCallbackTest`.
- FCM: việc xoá token sau khi rút consent mà quá hạn 30 s (Task của `deleteToken()` không hoàn tất) nay cũng đặt cờ
  xoá lại lúc mở app (`mem_fcm_delete_pending`) như khi xoá lỗi; completion thật tới muộn vẫn sửa cờ (final review
  Minor 5). Test: `FcmConsentTest`.

### Giới hạn đã biết

- **Opt-out theo luật bang Mỹ (GPP) chưa được chuyển cho mạng bên thứ ba** (§7 #14, DOC/66 — chỉ tài liệu,
  không đổi mã ở 1.4.0). SDK chỉ đọc TCF (EEA); ngoài GDPR (`IABTCF_gdprApplies = 0`, trừ `DENIED`) coi là cấp
  hết. Người dùng Mỹ chọn opt-out ("do not sell or share") qua thông điệp US-state của UMP **chưa** được
  chuyển cho Meta, MAX, AppsFlyer, Firebase hay CRM: luật hiện tại `gdprApplies == false && tier != DENIED`
  **không tôn trọng opt-out US-state** — người đã opt-out qua UMP vẫn được SDK coi là cấp hết trừ `DENIED`, vì
  SDK chưa đọc `IABGPP_*`. Chủ dự án tự bật thông điệp US-state trên AdMob console cho từng app trong lúc
  chờ. **1.5.0**: đọc `IABGPP_*` và chuyển cho các consumer trên (`setDoNotSell`, Meta LDU, Consent Mode
  denied, AppsFlyer). Chưa kiểm lại bằng debug geography US.
- **Meta Audience Network qua adapter mediation chưa bị chặn** (§7 #16): adapter Meta trong AdMob
  (`com.google.ads.mediation:facebook`) và MAX (`facebook-adapter`) do MobileAds/AppLovin khởi tạo; SDK chỉ chặn đường
  khởi tạo trực tiếp (`meta an: not started …`). Chưa kiểm trên máy adapter có khởi tạo Meta cho người `DENIED` không.
- **Meta SDK (`facebook-core`) chưa dừng hẳn khi từ chối/rút consent** (mục "Meta SDK" ở "Đổi"): lần mở đầu sau nâng
  cấp, và lần mở đầu sau khi rút, vẫn có thể có một lượt auto-init và app event tự động (provider chạy trước khi cờ
  `false` được ghi); rút consent giữa phiên không dừng facebook-core đang chạy; cài đặt phía server của Meta có thể đè
  auto-log. Chưa kiểm trên Events Manager.
- **FCM:** rút consent giữa hai lần mở app (tầng đã lưu cho phép, lần đầu của process là từ chối) không xoá token;
  token cũ vẫn nhận push tới khi `deleteToken()` thành công.
- **Cổng "chưa có cấu hình placement" (`cf not`) và "đang load" của `loadRect4Show`, `loadNtClForThisTurn`,
  `loadNativeFullForThisTurn`, `loadRewardedForThisTurn` vẫn im lặng** (không `onLoadFailed`), khác các cổng
  remove-ads/tắt định dạng/kill switch (mục "Load bị chặn …" ở "Phải làm").
- **Kill switch tới trong phiên đầu, trước câu trả lời consent:** load đang chờ mạng khởi tạo không chạy trong phiên đó;
  app chỉ nhận `onLoadFailed` sau 60–90 giây (`load timeout` từ watchdog placement, hoặc `list empty` khi slot cuối hết
  hạn; Task I2), không có nhãn kill (mục kill switch ở "Thêm").
- **Banner/rect: `LOAD_FAIL_BUT_ADS_NOT_NULL` còn có thể xuất hiện ngoài đường load** (final review Minor 2/6): view
  đang hiện refresh lúc slot rảnh vẫn gán vào mục trống (`isLoaded = false`); `load` bỏ tham chiếu đó trước khi xét, nhưng
  lần lấy view ra hiện lúc mục đang bị chiếm (`getAdForShow` → `getAdLoaded`) vẫn báo event đó. Chỉ là event chẩn đoán
  thừa, không ảnh hưởng hiển thị hay doanh thu.
- **Activity cũ còn bị giữ sau Full2** (G9 "1 Activity sau GC" chưa đạt trên máy): phép đo trước Task I5 thấy một
  Activity cũ giữ mạnh qua WebView của GMA; `plcf.cbShow` nay được nhả khi host bị huỷ (Task I5) nhưng chưa đo lại trên
  máy.
- **Telemetry trước consent** (§7 #17, P2, chưa chốt): app bật DataBucket (`DATABUCKETS_BASE_URL`) thì `first_open`,
  `session_start` vẫn gửi trước câu trả lời consent, cả cho người EEA sau này từ chối — nay mang id bí danh
  (`user_id` = player id), không còn ANDROID_ID.
- **`user_id` chưa ổn định trước lần đầu app truyền id** (mục `user_id` ở "Phải làm"): các lần mở trước khi id app được
  lưu (lần mở đầu sau nâng cấp/cài, hoặc mọi lần mở trước lần đăng nhập đầu nếu app chỉ truyền id sau đăng nhập) có
  `session_start`/`first_open`/`experiment_exposure` tự động mang UUID của SDK hay id app tuỳ race; lần mở mà app đổi
  tài khoản thì các hàng đó mang id cũ. Backend (`reconcile.py`, `metrics.py`) chưa có quy tắc ghép theo `session_id`.
- **Id app đã lưu không có đường xoá** (mục `user_id` ở "Phải làm"): sau khi app thôi truyền id (đăng xuất), `user_id`,
  bucket brain/holdout và `{user_id}` của web shop (`PurchaseRouter`) vẫn là id tài khoản cuối cùng, qua mọi lần mở
  sau, tới khi app truyền id khác; Auto Backup khôi phục nó khi cài lại. App có đăng xuất: truyền một id khách của
  chính app (vd `guest-<uuid app tự sinh>`) lúc đăng xuất; không mở web shop khi chưa đăng nhập.
- **Rút về `UNKNOWN` rồi đồng ý lại làm AppsFlyer start lần hai trong process** (`ConsentManager.reset()` rồi hỏi lại,
  hoặc UMP cập nhật lỗi mạng mà không còn câu trả lời lưu → `GRANTED` hạ về `UNKNOWN`): `stop(true)` rồi
  `stop(false)` + start — AppsFlyer nhận thêm một launch/phiên.
- **Offerwall chỉ chặn `DENIED`** (mục `allowsProfiling` ở "Phải làm"): `OfferwallManager` vẫn chạy khi tầng là
  `UNKNOWN`, khác Điều 13 mà các consumer khác đã theo.
- **Bản dựng cuối (`729638f`) chưa chạy trên máy thật và CI chưa từng chạy trên nhánh; cổng thiết bị cuối chỉ chạy
  trên emulator API 37/28/26 (DOC/64 "Cổng cuối").**

## [1.3.1] - 2026-09-24

Các lỗi ghi ở doc 64 mục D, tìm ra khi chạy thử 1.3.0 trên emulator, và các đường crash của bản
release R8 (doc 65, Phase A). Kiểm lại trên emulator API 26, 28, 37 (tại `9081988`, trước đợt sửa
cuối) và máy thật Galaxy A25 (API 36) — máy thật chạy lại trên `a2428cd`, sau khi đổi đường show
(`e19e36f`): cổng thiết bị và kịch bản (d) PASS; các kịch bản tay khác chưa chạy lại sau `e19e36f`
(doc 64 mục F). Bản này đổi hợp đồng callback và vài hành vi mặc định: đọc hết mục dưới trước khi nâng.

### Phải làm khi nâng cấp

- **Callback của Full2 tới app**, như bản Speaker Clean: `onAdShowed`, `onAdImpression`,
  `onAdClick` của Full2 được chuyển tới `AdsShowCb` của quảng cáo cha, với placement `full_2_*`.
  App đếm hay ghi log trong các callback này thì phân biệt bằng
  `placement?.startsWith("full_2_")`. `onAdDismissed` không đổi: app vẫn nhận đúng một lần, của
  quảng cáo cha, kể cả khi Full2 không hiện.
- **`onAdShowFail` không còn tới giữa waterfall.** Show trả `false` là không hiện. Trong vòng app
  open, interstitial, rewarded, mạng có quảng cáo mà không hiện được ngay lúc show (Activity đang huỷ,
  `Dialog.show()` ném lỗi, quảng cáo hỏng) không báo gì cho app, và vòng thử mạng sau. App nhận
  `onAdShowFail` đúng một lần, ở cuối vòng, khi không mạng nào hiện mà ít nhất một mạng báo lỗi lúc show;
  hoặc, như trước, khi mạng đã nhận hiện (show trả `true`) rồi mới báo lỗi. Không mạng nào có quảng cáo
  thì chỉ có `false`, không callback. Gọi thẳng một mạng (`AdsBase.showAd`), ngoài vòng, thì mạng đó
  vẫn báo đúng một `onAdShowFail`. App nào đang dựa vào `onAdShowFail("not pl config")` để biết "không có quảng cáo"
  thì chuyển sang giá trị trả về.
- **`onAdDismissed` có thể tới trong lúc Activity đang huỷ.** Activity chủ bị huỷ (xoay màn hình, đổi
  dark mode hay ngôn ngữ, hệ thống thu hồi) khi native full đang hiện (Full2, app open `openFlagImg`
  2/3, interstitial native): SDK đóng dialog và gửi đúng một `onAdDismissed`, **đồng bộ trong
  `onActivityDestroyed`, tức sau `onSaveInstanceState`**. Trong callback này, khi Activity đang huỷ,
  không commit Fragment và không điều hướng: kiểm `isFinishing`/`isDestroyed` trước, hoặc dùng
  `commitAllowingStateLoss`; không thì `IllegalStateException`. Trước đây callback này không bao giờ tới
  và mọi quảng cáo toàn màn hình bị chặn tới khi process chết. `loadInterstitialForThisTurn` hay
  `loadRewardedForThisTurn` gọi trong callback này nhận ngay `onLoadFailed(…, "host destroyed, load
  skipped")`: load lại sau khi Activity mới lên.
- **Hành vi mặc định khác** (app không phải sửa code, nhưng cần biết):
  - R8: `consumer-rules.pro` giữ constructor không tham số (`<init>`) của mọi lớp con `RoomDatabase`,
    `InputMerger`, `ListenableWorker` và `ComponentRegistrar` trong app, không chỉ lớp của SDK.
  - SDK mang `res/raw/appplugin_keep.xml`, nên app có thêm resource `raw/appplugin_keep`. `admob_*`,
    `adfb_*`, `admax_*`, `ads_cf_*`, `flagTest`, ba id test device, `facebook_app_id`, `af_app_id`,
    `admax_devKey` không còn bị `shrinkResources` bỏ. App không được đặt resource trùng tên
    `raw/appplugin_keep`: resource của app đè file của SDK.
  - Banner và rect gắn lại vào container mới (Activity tạo lại) thì gỡ view khỏi container cũ trước;
    gỡ không được thì không gắn và ghi sự kiện `ad_show_fail_reason` lý do
    `view_attach_null_in_callback`. Trước đây crash "The specified child already has a parent".
  - `showNative` khi chưa có view quảng cáo gọi `onAdShowFail(…, "view_attach_null_in_callback")` của
    app. Trước đây crash ở `addView(null)`.
  - Activity chủ bị huỷ: SDK gỡ view banner, rect, native collapsible của mình khỏi container của
    Activity đó.
  - Native full từ chối host `isFinishing`/`isDestroyed`: không hiện, quảng cáo native đã load bị huỷ,
    show trả `false`. `Dialog.show()` ném (`BadTokenException`) thì báo lỗi `act_destroy` thay vì làm
    app sập. Lý do đi qua sự kiện `ad_show_fail_reason`; callback theo mục `onAdShowFail` ở trên.
  - Overlay vùng an toàn của native full được gỡ khỏi Activity khi dismiss hoặc khi Activity bị huỷ,
    dựng lại ở lần show kế. `hideBlackSafeAreaView()` (public) nay cũng gỡ overlay và bỏ tham chiếu,
    không chỉ ẩn.

### Sửa

- **App nhận `onAdShowFail` rồi quảng cáo vẫn hiện.** Mạng chưa cấu hình placement gọi
  `onAdShowFail("not pl config")` ngay giữa waterfall, và mạng phía sau vẫn hiện được. App nào điều
  hướng ở cả hai callback thì điều hướng hai lần. Nay mạng đó im lặng như khi chưa có quảng cáo. Lỗi
  mạng báo ngay trong lúc show (native full trên Activity đang huỷ, rewarded AdMob khi chưa có
  Activity, quảng cáo hỏng) được giữ tới cuối vòng và chỉ tới app khi không mạng nào hiện. Mạng báo lỗi
  ngay trong lúc show mà vẫn trả `true` không còn để cờ full-screen kẹt ở 1.
- **Full2 hiện nút đóng ngay, không đếm ngược, và dựng lại dialog lúc hiện.** SDK gọi Full2 với
  `flagShow = 1`, trong khi dialog dựng sẵn phía sau quảng cáo cha có `isDelay = false`, nên dialog
  đó luôn bị bỏ. Nay dùng 0 như Speaker Clean: nút đóng đếm ngược từ `onStart` (doc 62), dialog dựng
  sẵn được dùng lại.
- **Click vào Full2 không chặn quảng cáo tự động** lúc người dùng quay lại app. Nay như click vào
  mọi quảng cáo toàn màn hình khác.
- **`IapHelper setup failed: 5` ở mỗi lần mở app.** Billing 9 với `enableAutoServiceReconnection()`
  báo cả kết quả kết nối lại về listener của `startConnection`. Một lời gọi API trong lúc lần kết nối
  đầu chưa xong cho ra `DEVELOPER_ERROR` ("already in the process of connecting"), rồi mới tới OK.
  Nay kết quả đó khi client vẫn đang `CONNECTING` chỉ ghi log debug.
- **`NoClassDefFoundError: java.lang.ClassValue` lúc Crashlytics khởi tạo, bản R8, API < 34.** R8 gộp
  lớp cache ClassValue của kotlinx.serialization (kéo theo bởi firebase-sessions) chung với lớp
  Crashlytics dùng lúc khởi động, và ART xác minh lớp gộp đó thì không resolve được `ClassValue`.
  Không sập, nhưng mỗi lần mở app in 13–16 dòng lỗi (emulator API 28). Nâng kotlinx.serialization
  lên 1.8.1 không hết. Nay `consumer-rules.pro` không cho R8 gộp ba lớp đó; app không phải làm gì.
- **Bản R8 full mode sập lúc khởi động ở `WorkDatabase_Impl` và mất registrar Firebase.** R8 bỏ
  constructor không tham số mà Room, WorkManager và Firebase gọi bằng reflection. Nay
  `consumer-rules.pro` giữ `<init>` của `RoomDatabase`, `InputMerger`, `ListenableWorker` và
  `ComponentRegistrar`.
- **App bật `shrinkResources` mất ad unit và resource SDK đọc bằng tên.** SDK đọc id quảng cáo và vài
  resource qua `getIdentifier`, nên bộ thu gọn resource coi chúng là không dùng và bỏ đi (app demo:
  0 ad unit trong `resources.arsc`). Nay SDK mang `res/raw/appplugin_keep.xml` (`tools:keep`), và
  `KeepXmlTest` bắt mọi tên đọc bằng chuỗi phải có trong đó.
- **Banner, rect, native gắn vào container sau khi Activity tạo lại sập "The specified child already
  has a parent".** View quảng cáo còn nằm trong container của Activity cũ. Nay banner và rect đi qua
  `BannerAttach`: gỡ khỏi cha cũ rồi mới gắn, gỡ không được thì không gắn. Native không còn gọi
  `addView(null)` khi chưa có view.
- **Native full (Full2) `BadTokenException` khi hiện trên Activity đã chết.** Dialog dựng sẵn cho
  Activity cũ bị dùng lại sau khi Activity tạo lại. Nay dialog dựng sẵn chỉ dùng lại khi cùng Activity
  còn sống, và `show()` bắt `BadTokenException` rồi báo lỗi `act_destroy` (callback theo mục
  `onAdShowFail` ở trên).
- **Xoay màn hình lúc Full2 đang hiện làm SDK kẹt ở "đang hiện quảng cáo toàn màn hình".** Dialog mất
  cùng Activity mà không phát `onAdDismissed`, nên cờ full-screen không về 0 và mọi interstitial,
  rewarded, app open sau đó bị từ chối (`isAdsFullShowing`) tới hết tiến trình. Nay khi host bị huỷ,
  native full dismiss dialog và phát đúng một `onAdDismissed` (lần dismiss muộn từ listener bị bỏ),
  cờ về 0, overlay vùng an toàn được gỡ, và Full2 không load lại trên Activity đang huỷ.
- **Load do app gọi từ callback dismiss khi host chết** nay nhận `onLoadFailed(…, "host destroyed, load
  skipped")` (interstitial, rewarded; Full2 bỏ qua) thay vì dựng ad trên Activity cũ;
  `AdsHelper.clearActivity` nhả Activity mọi mạng trước khi dismiss.
  Trong cửa sổ đó SDK cũng bỏ các lần tự reload sau dismiss (`full_default`, Full2 mặc định,
  `gift_default`); lần preload kế đến ở lượt show kế hoặc khi app gọi load.

### Thêm

- `tools/release_gate.sh`: dựng app demo bản release (R8 full, `shrinkResources`) và kiểm ad unit
  trong `resources.arsc`, tỉ lệ lớp SDK đổi tên, zipalign 16 KB.
- `tools/device_gate.sh`: cài bản release lên một máy, cold start, đếm 4 chuỗi lỗi R8 theo pid (có
  allowlist fail-closed `tools/device_gate_allowlist.txt`), monkey, crash Java/native, ANR.
- CI: job `release-gate` mỗi PR và workflow `device-gate` chạy tay/hằng đêm.
- App demo dựng như app thật: `targetSdk` 36, R8 full, `shrinkResources`, StrictMode đọc/ghi đĩa khi
  `flagTest`.
- API (chỉ thêm, app cũ không phải sửa): `AdsBase.releaseActivity()` và `AdsPlBase.releaseActivity()`
  nhả Activity mà không dismiss, không callback; `AdsPlBase.clearActivity()` nay `open`.

### Hành vi cần biết

- Sau khi xoay làm đóng interstitial cha (đóng không do người dùng), Full2 đã load vẫn xếp hàng
  (`pendingFull2`) và hiện trên màn hình tái tạo khi resume; `pendingFull2` giữ `cbShow` tới lần
  resume kế. Chưa đổi — chờ chủ dự án quyết.

## [1.3.0] - 2026-09-23

Mạng quảng cáo chỉ khởi tạo sau consent. Thêm các bản sửa và API mà Invoice Maker cần để bỏ
fork (doc 63 mục B, C). Build lên AGP 9.

### Phải làm khi nâng cấp

- **Gọi `AdsMobMy.instance.startSdkInit(context)`.** `AppPluginBase.onCreate` không còn khởi tạo
  mạng nào. Không gọi hàm này thì không mạng nào khởi tạo và không quảng cáo nào được tải. Gọi
  lúc nào cũng được, kể cả trước consent: SDK tự chờ UMP. Speaker Clean và Invoice Maker đang gọi
  đúng hàm này trên bản fork.
- **Chạy luồng consent UMP** (`ConsentManager.gatherConsent` hoặc `updateConsentInfo`). Mạng chỉ
  khởi tạo khi `ConsentInformation.canRequestAds()` là `true`. App không chạy UMP thì câu trả lời
  đó không bao giờ có, nên app không có quảng cáo nào.
- **App đo chiến dịch Meta (`AppEventsLogger`)** tự khai `com.facebook.android:facebook-core`
  (hay `facebook-android-sdk`): SDK chỉ `compileOnly` nó. Rồi gọi
  `AppPluginBase.initMetaSdkAfterConsent()`. Manifest của SDK đặt
  `com.facebook.sdk.AutoInitEnabled`, `AutoLogAppEventsEnabled`, `AdvertiserIDCollectionEnabled`
  về `false`; app nào đang khai `true` phải dùng `tools:replace="android:value"`.
- **Log DataBucket mặc định tắt.** App cần thì khai meta-data `DATABUCKETS_BASE_URL` và
  `DATABUCKETS_API_KEY`.
- **Sửa SDK cùng lúc với app bằng composite build:** app phải dùng AGP 9.x.

### Thêm

- `AdsMobMy.startSdkInit(context)`: yêu cầu khởi tạo MobileAds (kéo theo adapter mediation), Meta
  Audience Network và AppLovin MAX. Gọi lại là no-op.
- `AppPluginBase.initMetaSdkAfterConsent()`: Meta SDK sau consent. Không làm gì khi app không có
  `facebook-core` hoặc `facebook_app_id` trống hay còn placeholder `FB_APP_ID…`. Ở EEA mà purpose 1
  của TCF không được đồng ý thì không thu advertising id.
- `ConsentManager.updateConsentInfo(activity) {}`: cập nhật thông tin consent, không hiện form,
  cùng tham số với `gatherConsent`, callback đúng một lần.
- `ConsentManager.canStartAdSdks(context)`: câu trả lời UMP đã lưu. Khác `canRequestAds()` (trả
  `true` khi UMP chưa khởi tạo), hàm này là `false` khi UMP chưa có câu trả lời.
- `admob_test_device`, `fb_test_device`, `max_test_device` nhận nhiều id cách nhau bằng dấu phẩy.
- Log chẩn đoán khởi tạo (`flagTest`, tag `AppPlugin`): `ad networks: waiting for the consent answer
  before init (UMP status …)`, `ads admob initialize: complete after N ms` hoặc `not complete after
  8000 ms, releasing held loads`, trạng thái từng adapter mediation.

### Đổi

- **Mạng quảng cáo khởi tạo sau consent** (`AdSdkStartGate`): khi app đã gọi `startSdkInit` và UMP
  cho phép. Người dùng đã trả lời ở phiên trước thì khởi tạo ngay lúc mở app. Nguồn, trang Get
  Started của Google Mobile Ads Android: "If you need to obtain consent from users in the European
  Economic Area (EEA), the United Kingdom (UK), or Switzerland, set any request-specific flags …
  or otherwise take action before loading ads, ensure you do so before initializing Google Mobile
  Ads SDK (Legacy)." và "Ads may be preloaded by Google Mobile Ads SDK (Legacy) or mediation partner
  SDKs upon initialization." ("Legacy" là tên Google đặt cho `play-services-ads`, bản SDK này dùng.)
  UMP đọc câu trả lời đã lưu trên luồng nền, nên ngay lúc mở app nó còn báo UNKNOWN; SDK hỏi lại
  mỗi 100 ms trong 3 s đầu, nhờ vậy người dùng đã trả lời khởi tạo sau ~0,4 s thay vì chờ lần cập
  nhật UMP qua mạng (1–2,5 s, đo trên emulator API 37).
- **AppLovin MAX không khởi tạo khi `admax_devKey` trống** (app chỉ chạy AdMob + Meta). Trước đây
  vẫn mở phiên AppLovin với key rỗng, ghi lỗi kèm stack trace và gửi postback.
- **Load của Meta và MAX chờ mạng khởi tạo xong**, như AdMob đã làm (`SdkReadyQueue`). Trước đây
  load của hai mạng này chạy ngay, và SDK của mạng tự khởi tạo. Hạn chờ 8 s tính từ lúc bắt đầu
  khởi tạo, không từ `initAdsHelper()`.
- `Constants.DataBucket.BASE_URL` rỗng, như cả hai app fork đang chạy.
- AGP 9.2.1, Gradle 9.4.1, Kotlin tích hợp sẵn của AGP (vẫn 2.2.10). Bytecode vẫn 1.8.

### Sửa

- **Banner chết sau một vòng lỗi** (Invoice BUG-07): cả waterfall lỗi thì SDK dừng hẳn, và mốc
  `bnTimeShow` vừa đóng chặn luôn `showBanner()` kế tiếp. Nay thử lại sau 5 s, 15 s, 45 s khi chưa
  có banner nào trên màn hình.
- **Refresh banner dừng hẳn sau một vòng refresh lỗi**: banner cũ đứng yên tới lần khởi động sau.
  Nay giữ nhịp refresh.
- **Hai vòng banner chạy song song**: lần gọi `loadBanner()` thứ hai hạ `isLoading` của vòng đang
  chạy. Nay gọi chồng là no-op; mỗi vòng mang số thế hệ theo chỗ đặt, callback của vòng cũ bị bỏ,
  và một bước im lặng 30 s thì vòng bị bỏ rồi thử lại. Chờ consent không tính là kẹt.
- **Form consent mất khi Activity bị tạo lại** (xoay màn hình, đổi dark mode, đổi ngôn ngữ) lúc
  form đang hiện hoặc đang tải: UMP trả lỗi "Activity is destroyed", cổng mở mà người dùng chưa trả
  lời, và ở EEA cả phiên không có quảng cáo. Nay form hiện lại trên Activity mới (Activity mới gọi
  lại `gatherConsent` trong `onCreate`, như mọi app đang làm); không có Activity mới thì cổng mở ở
  lần kiểm tra kế như cũ. Kiểm trên emulator: xoay 4 lần lúc form đang hiện, form vẫn trên màn hình.
- `AdsConfigOb.loadFromMem()`/`saveCurr()` crash khi `mContext` null.
- `AdSettings.INTEGRATION_ERROR_CRASH_DEBUG_MODE` của Meta chỉ bật ở bản test (`flagTest`), không
  còn ở bản phát hành.

## [1.2.0] - 2026-09-23

Bản hợp nhất (doc 61, mục E bước 1). Nguồn duy nhất cho LAM 2026, Speaker Clean và Invoice
Maker; đưa về SDK những gì hai bản fork đã sửa và kiểm trên máy thật.

### Phải làm khi nâng cấp

- **App dùng Dagger/Hilt < 2.60 với kapt** (LAM): Meta adapter 6.21.0.4 kéo `kotlin-stdlib`
  2.3.0 và `kaptDebugKotlin` vỡ. Ghim adapter về 6.21.0.3 cho tới khi lên AGP 9 + Hilt 2.60:

  ```kotlin
  dependencies {
      constraints {
          implementation("com.google.ads.mediation:facebook") {
              version { strictly("6.21.0.3") }
              because("Hilt 2.57.2 (kapt) cannot read kotlin-stdlib 2.3 metadata pulled by 6.21.0.4")
          }
      }
  }
  ```

- **Build release có R8:** consumer rules không còn keep-all (xem mục Bảo mật). App nào tự
  gọi SDK qua reflection phải tự thêm rule; gọi thẳng bằng code thì không cần gì.
- **`InstallReferrerHelper`** không tự chạy: gọi `InstallReferrerHelper.captureOnce(context)`
  trong `onDone` của consent.
- **`ConsentManager.request()`** giờ chạy một lần cho mỗi process. Gọi lại (ví dụ Activity
  được tạo lại) không mở form mới mà trả ngay tầng consent hiện tại.

### Thêm

- **Chuẩn chuyển cảnh interstitial/rewarded → full2** theo Speaker Clean, cho AdMob, Meta và
  MAX (doc 62 mục 3.M): dựng sẵn dialog native full phía sau quảng cáo cha, lớp che mặc định
  `Full2DefaultCover` khi app không đăng ký `Full2TransitionListener`, giữ lớp che tới impression
  (dự phòng 1,5 s, timeout 3 s), cooldown 5 s sau khi bấm quảng cáo.
- **Consent hợp nhất:** `ConsentManager.gatherConsent(activity) {}`, `isConsentResolved(context)`,
  `isPrivacyOptionsRequired(context)`, `addConsentUpdatedListener {}`. Một `ConsentGate` cho mỗi
  process. Cổng không bao giờ mở khi form còn trên màn hình (Speaker Clean), và vẫn mở khi form
  hỏng im lặng, kèm một lần hiện lại (Invoice Maker, BUG-07). Xem `ConsentFormWatchdog`.
- **`com.app.plugin.ads.consent.ConsentManager.instance`**: lớp chuyển tiếp cho code đang gọi bản
  fork, `@Deprecated`, giữ trong 1.x. Khác bản fork: `flagTest` không còn tự ép EEA, muốn giữ
  thì đặt `ConsentManager.debugForceEea = BuildConfig.DEBUG`.
- **`AdRequestFactory`**: mọi `AdRequest` AdMob (cả 8 định dạng) gắn `npa=1` khi GDPR áp dụng mà
  purpose 1 của TCF chưa được đồng ý.
- **Gỡ quảng cáo theo định dạng:** `AdsHelper.setRemoveAdsMask(mask)`, `getRemoveAdsMask()`,
  `isRemoveAds(flag)`, hằng `RemoveAdsMask.ALL` / `INTERRUPTIVE`. `setRemoveAds(0|1)` vẫn chạy như
  cũ. Entitlement về trước `initAdsHelper` giờ được giữ lại thay vì bị bỏ.
- **Cooldown "N màn hình" sau quảng cáo toàn màn hình**, mặc định **tắt**:
  `AdsHelper.setPostAdScreenCooldown(2)` rồi gọi `AdsHelper.recordScreenChange()` mỗi lần đổi
  màn hình. Chặn interstitial (khi `isDeltaTime`), app open (trừ splash) và banner; không chặn
  rewarded và full2.
- **Ẩn banner làm phần thưởng:** `AdsHelper.grantBannerHideReward(durationMillis)`,
  `isBannerHiddenByReward()`. Mốc hết hạn được lưu qua các lần khởi động.
- **`AdsHelper.isInterstitialLoaded(pl)`, `isInterstitialReadyToShow(pl)`**: hàm sau chỉ `true`
  khi lệnh show kế tiếp sẽ qua mọi cổng, để app không chạy màn "ad break" rồi không có quảng cáo.
- **`AdsHelper.suppressNextOpenAd()`, `onAdClicked()`, `retryBannerNow(reason)`.** Banner tự tải
  lại khi form consent đóng.
- **`PlayIntegrityGuard.requestToken(context, nonce, cloudProjectNumber)`** (gói `iap`): token
  Play Integrity kiểu classic gắn với giao dịch mua, fail-open. Dependency
  `com.google.android.play:integrity:1.6.0`.
- **`InstallReferrerHelper.captureOnce(context)`** (gói `attribution`): UTM của Play Install
  Referrer vào Firebase Analytics.
- Có trong mã từ sau commit đặt số 1.1.0 (`f710795`): IAP (router mua hàng, vòng đời subscription,
  battle pass, recurring grants), paywall cấu hình từ xa, offerwall, cầu nối CRM, lớp consent
  CMP/UMP, quyết định cross-modal và bản ghi phơi nhiễm holdout của brain, banner auto-refresh.

### Sửa

- **AAR thiếu package:** build release của thư viện minify với danh sách keep viết tay, nên mọi
  package không có trong danh sách đều mất khỏi AAR. Build thử trên `main` trước bản này thiếu hẳn
  `crm/`, `offerwall/`, `appsflyer/` và `AdsConditionOb`. Thư viện không còn minify; R8 của app lo
  phần này.
- **Test device ghi đè cấu hình AdMob:** hai lần `setRequestConfiguration()` trên hai luồng, lần
  sau xoá cờ trẻ em, cờ tuổi đồng ý và rating của lần trước (doc 61 B.1). Giờ là một cấu hình.
- **Rewarded có thể trao thưởng sai lần:** cờ "đã nhận thưởng" nằm trên đối tượng waterfall dùng
  chung giữa các lần show. Giờ là `RewardDeliveryGate` theo từng lần show, cho AdMob, Meta, MAX.
- **Meta native không render** ("Method is not found") trên GMA 24.9 với adapter 6.21.0.1.
- FCM token không còn được ghi nguyên văn ra logcat (`SensitiveLogSanitizer`).

### Thay đổi

- GMA `play-services-ads` 25.4.0 cùng bộ adapter AdMob build trên 25.4.0; Meta adapter
  6.21.0.4; AppLovin 13.6.2 cùng adapter MAX tương ứng.
- Countdown nút đóng của native full bắt đầu ở `onStart`, để dialog dựng sẵn vẫn đủ thời gian đếm.
- `MonetizationSdk.VERSION` đọc từ `BuildConfig.SDK_VERSION`, cùng nguồn với bản publish.

### Bảo mật

- Consumer rules chỉ còn giữ thứ R8 không tự thấy: Gson (`TypeToken`, field của
  `EventRequest`/`UserProperty`), Retrofit 2.9 cho R8 full mode, tên hằng enum. Trước đây SDK
  keep nguyên mọi package `com.app.plugin.*` (và cả Firebase, AppsFlyer, Meta, AppLovin, vốn tự
  mang rule), nên APK phát hành lộ đủ tên lớp và hàm nội bộ. Kiểm trên bản release có R8 của
  LAM build từ AAR 1.2.0: 198 lớp SDK bị đổi tên; `LogService` còn chữ ký `Call<ResponseBody>` và
  `@POST`/`@Body`, các lớp con `TypeToken` còn chữ ký generic, field của `EventRequest` và
  `UserProperty` giữ tên.

### Chưa làm trong bản này

- `segment/*` và `GridForExcludeTouch` của Speaker Clean: chờ quyết định (doc 61 mục F).
- SDK lên AGP 9: LAM còn build SDK từ source bằng AGP 8.10.1; làm sau khi LAM chuyển sang AAR.

## [1.1.0] - 2026-08-24

Monetization Brain, lớp IAP và điểm vào duy nhất `MonetizationSdk.configure()`.
