# 41 — Prompt chuẩn: Phân tích (teardown) một dự án app/game về monetization

**Mục đích:** một prompt tái sử dụng được, đưa cho AI để phân tích BẤT KỲ dự án nào (APK đối thủ đã decompile, repo source của chính mình, hoặc app đang vận hành) và trả về báo cáo đầy đủ: có những gì, kịch bản IAA ra sao, kịch bản IAP ra sao, LiveOps thế nào, và toàn bộ các lớp kiến thức còn lại.

**Nguồn taxonomy:** doc 04 (player features), 23 mục C (6 lớp tính năng chuẩn thế giới), 30 mục 8–9 (lịch LiveOps, ad trong game hybrid), 29 (rubric đạo đức), 27 (pattern/anti-pattern từ teardown Solara), 13 (privacy), 21 (measurement).

---

## A. PROMPT CHÍNH (copy nguyên khối dưới đây)

```text
Bạn là chuyên gia monetization + reverse engineering mobile app/game (IAA, IAP,
subscription, LiveOps, player intelligence). Nhiệm vụ: phân tích dự án dưới đây
và trả về MỘT báo cáo teardown đầy đủ theo đúng cấu trúc 14 mục ở phần OUTPUT.

===================== THAM SỐ =====================
DỰ ÁN:        {{tên app/game + package name nếu có}}
LOẠI INPUT:   {{APK/XAPK đã decompile | repo source | cả hai | chỉ store listing + observation}}
ĐƯỜNG DẪN:    {{path tới jadx_base/, apktool_base/, hoặc root repo}}
THỂ LOẠI:     {{utility app | casual game | mid-core game | ...}}
THỊ TRƯỜNG:   {{US/EU | global | ...}}
ĐỘ SÂU:       {{quick (mục 0-5) | full (mục 0-13) | focused: <liệt kê mục>}}
===================================================

===================== NGUYÊN TẮC BẮT BUỘC =====================
1. EVIDENCE-FIRST. Mỗi khẳng định phải gắn nhãn:
   [V] Verified  — đọc được trực tiếp từ code/config, PHẢI kèm `file:line` hoặc key name
   [I] Inferred  — suy luận từ nhiều mảnh bằng chứng, phải ghi rõ suy luận từ gì
   [U] Unknown   — không xác minh được từ input hiện có
   Không có nhãn = không được viết ra.
2. KHÔNG BỊA SỐ. Mọi con số (giây, giá, ngưỡng, tần suất) phải trích được từ input.
   Nếu chỉ đoán → ghi [U] và nói rõ cần input gì để xác minh.
3. Phân biệt "code có khả năng làm X" với "X đang thực sự bật". Luôn truy: giá trị
   default đóng gói trong APK/repo → giá trị remote config override → giá trị đang chạy.
4. Mọi con số thời gian quy về giây; mọi giá quy về USD kèm ghi chú tier quốc gia.
5. Khi thấy hành vi khác nhau giữa các nhóm user, PHẢI nêu rõ chiều phân nhóm
   (subscriber, organic/non-organic, country tier, phiên đầu/phiên sau, phiên bản OS).
6. Kết thúc báo cáo BẮT BUỘC có mục "Không xác minh được" — liệt kê thẳng, không giấu.
================================================================

===================== CÁCH THU THẬP =====================
Nếu input là APK/XAPK đã decompile:
  - Manifest: đếm activity/service/receiver/provider; liệt kê meta-data (AdMob app id,
    MAX sdk key, Facebook app id, consent flags).
  - `unknown/*.properties`, `META-INF/`, `res/xml/` → bảng SDK + version.
  - `res/xml/remote_config_defaults.xml` (hoặc tương đương) → TOÀN BỘ key remote config,
    kèm giá trị default; đây là bản đồ LiveOps quan trọng nhất.
  - grep các chuỗi: "ca-app-pub-", "interstitial", "rewarded", "app_open", "native",
    "banner", "subs", "inapp", "sku", "paywall", "premium", "trial", "offer",
    "remote_config", "consent", "gdpr", "att", "purchase", "billing".
  - Tìm class facade quảng cáo và class quản lý IAP; đọc TỪ ĐIỂM GỌI ngược lên
    (Activity/Fragment → facade → loader) để dựng placement map thật, không đoán.
Nếu input là repo source:
  - Đọc file config/manager của ads, billing, remote config, analytics trước.
  - Dựng call graph từ màn hình → nơi gọi show ad / mở paywall.
  - Đọc test (nếu có) để biết hành vi nào được coi là đúng.
Nếu chỉ có observation (không có code):
  - Đánh dấu TOÀN BỘ báo cáo là [I] hoặc [U]; ghi rõ đây là black-box teardown.
=========================================================

===================== OUTPUT: 14 MỤC =====================

## 0. Danh tính & kiểm kê stack
Package, version, kích thước, số class/activity. Bảng SDK: mediation (AdMob/MAX/IronSource),
ad network adapter thực có trong bin (đối chiếu với network được KHAI trong config — lệch nhau
là một phát hiện), MMP (AppsFlyer/Adjust), analytics, remote config, billing lib, CMP.
Ghi rõ: SDK nào khai trong config nhưng KHÔNG có trong bin, và ngược lại.

## 1. Bản đồ bề mặt monetization (placement map)
Bảng: Màn hình/Trigger | Loại ad hoặc offer | Điều kiện hiển thị | Config key điều khiển |
Có tắt được từ xa không. Bao gồm cả splash/app-open, back press, chuyển màn, sau hành động
thành công, khi hết resource. Chỉ rõ chuỗi dày nhất (thường là splash → home).

## 2. KỊCH BẢN IAA (chi tiết nhất — đây là mục lõi)
2.1 Waterfall & tầng giá: có mấy tầng (high/medium/default)? Hàm nào chọn tầng? Điều kiện lên/xuống tầng?
2.2 Ad unit resolution: từ format → slot → unit id qua mấy lớp override (code default,
    XML default, remote config, country tier)? Vẽ thứ tự ưu tiên.
2.3 Cache & preload: preload bao nhiêu, cache key gồm những chiều nào, có share/reuse ad
    giữa các vị trí không (kiểu `is_can_reuse_ads`) — nêu rủi ro nếu có share.
2.4 Fail & retry: khi load fail thì hạ tầng thế nào, delay retry theo tầng, error code nào
    được retry, có disable timestamp per-ad-type không (ghi số giây thật).
2.5 Frequency cap & pacing: maxShow/session, delay giữa 2 ad (delTime), cap theo ngày,
    cap phiên đầu, cooldown sau khi user click.
2.6 Cổng chặn trước khi show: subscriber check, remote enable flag, click-fraud guard
    (ngưỡng click/cửa sổ/thời gian khóa), visibility check, thời điểm sớm nhất được show ad
    kể từ lúc mở app.
2.7 Phân tầng theo user: organic vs non-organic, country tier (bao nhiêu tier, bao nhiêu
    quốc gia), phiên đầu vs phiên sau, người đã từng trả tiền.
2.8 Rewarded: điểm mời xem, phần thưởng cụ thể, có "manufactured fail" (cố tình tạo thất bại
    để mời xem ad) không — đây là anti-pattern phải gọi tên nếu có.
2.9 Mật độ ad ước tính: số ad/phiên 10 phút theo config đang bật (ghi [I] và show cách tính).

2.10 Ma trận tệp người chơi × kịch bản (User Segment × Scenario Matrix):
   Định nghĩa: bao nhiêu tệp người chơi *thực sự* (real segment N) được code/config phân biệt
   để **quyết định ad khác nhau**? Mỗi tệp = một **bộ quy tắc ad riêng** (waterfall khác,
   frequency cap khác, tầng giá khác, hoặc có/không chiếu ad). Ghi rõ:
   - N = số tệp (nếu tất cả người chơi thấy ad giống nhau → N=1)
   - Bảng: tệp i | định nghĩa | số người chơi % | tham số ad riêng (cap, cooldown, unit id, …)
   - Bộ tín hiệu gán nhóm: ai gán (client/server), khi nào (lần đầu/mỗi phiên/real-time), có lock không
   
   4 câu kiểm tra bắt buộc:
   (a) Ngoài bộ tín hiệu gán tệp, có còn tín hiệu nào khác (country, campaign, os tier)
       ảnh hưởng ad decisioning không? → Nếu CÓ: tệp N đơn giản là ngụy trang.
   (b) Có tệp nào với zero ad hoặc all-premium không (cổng dạng white-list)? → Nếu YES: đó
       có phải là trong-app purchase / subscriber không, hay là experiment holdout?
   (c) Có xung đột giữa tệp không (một người chơi match nhiều tệp cùng lúc)? → Nếu YES: cơ
       chế ưu tiên là gì?
   (d) Khi user transition giữa tệp (ví dụ mua NoAds), ad config có apply lại ngay không?
       Có delay / race condition không?

## 3. KỊCH BẢN IAP / PAYWALL
3.1 Catalog: liệt kê SKU (id, loại consumable/non-consumable/subs, giá theo tier), thang giá
    (bao nhiêu bậc, tỷ lệ giữa các bậc, bậc nào là "neo" anchoring).
3.2 Cấu trúc tiền tệ: soft/hard currency, tỷ giá quy đổi, có currency decoupling không.
3.3 Bundle & offer: starter pack, bundle, giá trị khai báo ("tiết kiệm 60%"), có tính được
    break-even không, offer nào giới hạn thời gian.
3.4 Subscription: các tier, chu kỳ, free trial/intro offer, grace period/account hold,
    win-back, dunning (retry khi thanh toán lỗi).
3.5 Paywall: trigger nào mở paywall, mở bao nhiêu lần/phiên, delay nút đóng (giây!),
    có ép xem trước khi dùng app không, copy/anchoring, dark pattern check.
3.6 Regional pricing: có map country → tier giá không, tier ảnh hưởng gì ngoài giá.
3.7 Payment rails: chỉ Store IAP hay có web shop/D2C? Entitlement là client-side hay
    server-side? Có validate receipt server không? (nếu client-side → nêu rủi ro gian lận)
3.8 Sau khi mua: người đã trả tiền còn thấy ad không? Thấy loại nào? — nếu CÓ, gọi tên đây là
    anti-pattern hối hận sau mua và đánh giá tác động lên retention.

## 4. LIVE OPS & khả năng điều khiển từ xa
4.1 Bề mặt remote config: bao nhiêu key, nhóm thành mấy cụm chức năng, cái gì đổi được
    KHÔNG cần release app (giá, tần suất ad, ad unit, bật/tắt tính năng, copy, paywall).
    Đây là thước đo "độ trưởng thành LiveOps" quan trọng nhất.
4.2 Lịch sự kiện theo kiến trúc 5 lớp (nếu là game):
    Lớp 0 nền (daily reward/quest/energy) | Lớp 1 tuần (tournament/win-streak/team) |
    Lớp 2 mùa (battle pass/collection — không bao giờ chồng 2 cái) | Lớp 3 đỉnh
    (limited-time mode, lễ, banner giới hạn) | Lớp 4 offer gắn theo từng lớp.
    Điền: có gì, mật độ/tháng, thời lượng, có ma trận xung đột tài nguyên không
    (thời gian/energy/soft/hard currency/kỹ năng — hai event cùng ngốn một cột không được chồng).
4.3 Battle pass/season pass: giá, số tier, có catch-up mechanics không, ước tính % doanh thu.
4.4 Targeting & segmentation trong config: config có phân nhánh theo segment nào không.
4.5 Cơ chế thí nghiệm: có A/B test không, ai gán nhóm (client hay server), có holdout không,
    có kill switch/rollback không, policy/config có ký số hay verify không.
4.6 Nhiều lớp default: đếm số lớp fallback (hardcode trong code / XML đóng gói / remote).
    KIỂM TRA BẮT BUỘC: lớp fallback đáy có HIẾU CHIẾN HƠN lớp default chính không
    (giá mặc định nhảy lên gói đắt nhất, ad dày hơn)? Đây là lỗi thiết kế nguy hiểm nhất
    thường gặp — nguyên tắc đúng là "fail-safe, không fail-aggressive".

## 5. Player intelligence & personalization
5.1 Tín hiệu thu thập được (đối chiếu 13 nhóm feature: acquisition, lifecycle, engagement,
    skill/difficulty response, progression, economy, IAP behavior, IAA behavior, offer intent,
    preference, temporal/sequence, context tại decision point, latent score).
5.2 Mức độ cá nhân hóa: rule tĩnh → segment thủ công → propensity model → bandit → RL.
    Chấm mức đang ở đâu, kèm bằng chứng.
5.3 Có decision log không (ghi lại: state, action đã chọn, các action ứng viên, propensity,
    outcome)? Không có decision log = không thể học off-policy sau này.
5.4 Cold start: user mới được xử lý ra sao.

## 6. Measurement & attribution
MMP nào, event taxonomy (liệt kê event chính), có ad-revenue impression-level không,
SKAN/AAK conversion value schema, deep link/web-to-app, purchase event có gửi server không.

## 7. Kinh tế trong game (bỏ qua nếu là utility app)
Source/sink của từng loại currency, tốc độ lạm phát, đường cong progression/độ khó,
có DDA không, gacha/loot box (tỷ lệ công bố, pity, hard pity — tính kỳ vọng số lần quay
và chi phí kỳ vọng nếu đủ dữ liệu), soft gate ở đâu.

## 8. Privacy & compliance
CMP/consent flow (GDPR/CCPA), ATT prompt timing, COPPA/age gate, quyền xin trong manifest
(cái nào thừa), dữ liệu gửi đi đâu, luồng huỷ subscription có dễ như lúc mua không
(FTC click-to-cancel), loot box disclosure, tuân thủ quảng cáo cho trẻ em.

## 9. Audit đạo đức & Comfort (dùng rubric 5 câu, trả lời từng câu có/không + bằng chứng)
  1) Người chơi có hiểu rõ mình đang mua gì và với xác suất nào không?
  2) Thiết kế có khai thác riêng nhóm dễ tổn thương (chi tiêu bất thường) không?
  3) Áp lực có được tạo ra một cách nhân tạo (đếm ngược giả, khan hiếm giả) không?
  4) Người đã trả tiền có được đối xử tốt hơn không?
  5) Nếu công khai toàn bộ cơ chế cho người chơi, họ có thấy bị lừa không?
Kết luận vào 1 trong 3 vùng: chấp nhận được / vùng xám / vượt lằn ranh — kèm lý do.
Liệt kê riêng các anti-pattern kinh điển nếu phát hiện: near-miss, manufactured fail,
fallback hiếu chiến hơn default, share state giữa 2 loại action không liên quan,
subscriber vẫn thấy ad, nút đóng paywall delay dài, ad ngay giây đầu phiên đầu.

## 10. Bảng đối chiếu 6 lớp tính năng chuẩn thế giới
| Lớp | Có / Một phần / Không | Bằng chứng | Khoảng cách so với chuẩn |
C.1 Ad monetization (execution + decisioning per-user)
C.2 Payment routing đa kênh (Store IAP / web shop / D2C, entitlement server-side)
C.3 Paywall & subscription (remote paywall, A/B, dunning, win-back, trial)
C.4 Offerwall / loyalty / engagement (battle pass, streak, mission, CRM)
C.5 Measurement & attribution 2026 (AAK/SKAN, decision log, incrementality/holdout)
C.6 Decision brain (bandit/uplift, guardrail, cross-modal IAA↔IAP)

## 11. Chắt lọc DNA — phần có giá trị nhất
11.1 Bảng PATTERN TỐT ĐÁNG HỌC: mỗi dòng gồm — pattern | bằng chứng production | vì sao tốt |
     áp vào SDK của ta ở file nào | có copy được con số không (mặc định: KHÔNG copy số, chỉ
     dùng làm prior để A/B).
11.2 Bảng ANTI-PATTERN PHẢI TRÁNH: pattern | tác hại | ta đã tránh được chưa | nếu chưa thì
     sửa ở đâu | viết test gì để nó không tái diễn.
Tiêu chí chọn lọc: chỉ đưa vào nếu (a) chạy production thật ở quy mô thật, và (b) verify
được trực tiếp từ code/config, không phải suy đoán.

## 12. Ước tính hiệu quả kinh tế (ghi rõ [I], show cách tính)
Ước tính ARPDAU/ad density, tỷ trọng IAA vs IAP, chỗ rò rỉ doanh thu lớn nhất,
và 3 thay đổi có đòn bẩy cao nhất nếu đây là dự án của ta.

## 13. Việc tiếp theo (đánh số, xếp theo thứ tự thực tế)
Mỗi việc: làm gì | file nào | phụ thuộc gì | cách nghiệm thu.

## Không xác minh được (BẮT BUỘC — không được bỏ)
Liệt kê thẳng mọi thứ không truy được từ input, kèm: cần thêm input gì để xác minh.

===================== ĐỊNH DẠNG =====================
- Tiếng Việt, ưu tiên BẢNG và khối `text` sơ đồ hơn văn xuôi dài.
- Mỗi mục mở đầu bằng 1 câu kết luận, rồi mới đến bằng chứng.
- Không dùng tính từ marketing ("mạnh mẽ", "tối ưu") — chỉ mô tả cơ chế và số.
- Nếu một mục không áp dụng cho loại dự án này, ghi "N/A vì <lý do>" thay vì bỏ trống.
======================================================
```

---

## B. BẢN NGẮN (dùng khi chỉ cần quét nhanh)

```text
Phân tích dự án {{tên}} tại {{path}} và trả về báo cáo monetization teardown gồm:
(0) kiểm kê SDK + remote config key, (1) placement map màn hình → ad/offer,
(2) kịch bản IAA: waterfall/tầng giá, frequency cap, retry/fail, cổng chặn, phân tầng
    theo subscriber/organic/country, (3) kịch bản IAP: SKU + thang giá + paywall trigger
    + delay nút đóng + regional pricing + người đã mua còn thấy ad không,
(4) LiveOps: cái gì đổi được từ xa không cần release, có A/B/holdout/kill switch không,
    lớp fallback có hiếu chiến hơn default không,
(5) 3 pattern đáng học + 3 anti-pattern phải tránh, (6) không xác minh được.

Mọi khẳng định gắn nhãn [V] verified kèm file:line / [I] inferred / [U] unknown.
Không bịa số. Ưu tiên bảng. Tiếng Việt.
```

---

## C. Cách dùng

1. Điền khối THAM SỐ, dán bản A vào phiên làm việc có quyền đọc thư mục dự án.
2. Với APK: decompile trước (`jadx`/`apktool`) rồi trỏ ĐƯỜNG DẪN vào thư mục output.
3. Sản phẩm ra: một file `NN_<TÊN>_TEARDOWN.md` trong `DOC/`.
4. Sau đó chạy tiếp bước chắt lọc: lấy mục 11 của báo cáo, viết thành một doc DNA riêng
   theo đúng khuôn `27_SOLARA_TEARDOWN_DISTILLED_DNA.md` (pattern tốt đã code hóa /
   anti-pattern đã ngăn / cập nhật bảng DNA doc 26 / việc tiếp theo).

## Ghi chú giới hạn

- Prompt này chỉ mạnh bằng input. Với black-box (không có code), mục 2.1–2.6, 4.1, 4.5, 5.3
  gần như chắc chắn ra [U] — đừng ép AI trả lời, sẽ thành bịa.
- Con số quan sát ở một app KHÔNG phải số tối ưu cho app khác. Luôn dùng làm prior để A/B,
  không copy thẳng vào code (bài học doc 27 mục "Không xác minh được").
