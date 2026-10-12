# 13 — Privacy & Compliance: Trạng thái luật 2026 và kiến trúc consent-aware

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Bức tranh pháp lý hiện hành (giữa 2026) ràng buộc một monetization brain, và cách thiết kế hệ thống để compliance là thuộc tính kiến trúc chứ không phải vá sau.

## Evidence labels

- **[Primary law/docs]**: văn bản luật, quy định, docs nền tảng chính thức.
- **[Primary enforcement]**: quyết định xử phạt/công bố của cơ quan quản lý.
- **[Secondary]**: báo chí pháp lý/analyst.

---

## A. Thay đổi lớn nhất: Android Privacy Sandbox đã bị khai tử

- **Tháng 10/2025, Google tuyên bố dừng Google Privacy Sandbox trên Android**: Topics, Protected Audience (PAAPI), SDK Runtime và Attribution Reporting đều không được triển khai rộng. [Primary docs — Android Developers blog]
- **Hệ quả trực tiếp:**
  1. **GAID (Advertising ID) vẫn còn** trên Android — môi trường Android đỡ khắc nghiệt hơn iOS về identifier. Nhưng Google Play vẫn có thể siết dần theo chính sách riêng.
  2. **Data Safety form của Google Play là hợp đồng ràng buộc chính**: mọi loại data thu thập/chia sẻ phải khai đúng; khai sai là rủi ro gỡ app.
  3. Không thể chờ đợi API attribution chuẩn của Google — measurement phải xây trên **first-party server-side events**.
- **Với kiến trúc brain:** không cần thiết kế quanh SDK Runtime hay Topics; ngược lại, phải thiết kế quanh Data Safety form + consent + first-party data.

## B. iOS: SKAN 4.0 + AdAttributionKit

- **SKAN 4.0**: 6-bit conversion value, 3 postback windows (0–2d, 3–7d, 8–35d), aggregate-only, lockout delay.
- **AdAttributionKit (iOS 18.4+)**: chồng thêm re-engagement windows và conversion tags, nhưng **vẫn không có user-level attribution**. [Primary docs — Apple]
- **Hệ quả:** decisioning on-device hoạt động được (brain biết user), nhưng **đo lường hiệu quả trên iOS phải cohort-based**. Conversion-value schema 64 bucket là bài toán thiết kế thông tin — nên dành bucket cho pLTV tier.
- **Apple Guidelines 3.1.x**: personalization chỉ được thực hiện qua **loại offer hợp lệ** (introductory offer, promotional offer, win-back offer) và SKU riêng — **không được hiển thị giá khác nhau cho cùng một SKU theo user**. Đây là lý do "personalized pricing" trên iOS phải thể hiện bằng coupon/offer type, không phải đổi giá SKU. [Primary docs — Apple Review Guidelines 3.1.2/3.1.3]

## C. Trẻ em và người chưa thành niên — ràng buộc cứng

- **COPPA (Mỹ) amended rule, hiệu lực 23/6/2025; tuân thủ đầy đủ 22/4/2026:** targeted ads cho trẻ <13 cần opt-in của phụ huynh; thu thập dữ liệu sinh trắc bị hạn chế. [Primary law — FTC]
- **Trung Quốc:** cap chi tiêu của minor theo tuổi — dưới 8 tuổi: không được nạp; 8–16 tuổi: 200 RMB/tháng; 16–18 tuổi: 400 RMB/tháng. [Primary law]
- **Hàn Quốc:** cap cho minor 70.000 KRW/tháng (đã hạ từ mức cũ). [Primary law — Game Industry Act]
- **Hệ quả kiến trúc:** brain cần **age gate + spending cap enforcement** như hard constraint trước policy, không phải sau. Với game có trẻ em: personalized monetization gần như bị loại hoàn toàn → fallback sang non-personalized.

## D. Loot box và dark pattern — đường ranh đang cứng lại

- **PEGI (6/2026):** game có loot box tự động nhận nhãn PEGI 16. [Primary — PEGI]
- **Bỉ:** loot box bị cấm như cờ bạc. **Hà Lan** từng phạt, pháp lý đang dao động. [Primary enforcement]
- **EU Digital Fairness Act (đề xuất):** thảo luận cấm loot box cho <16, cấm pay-to-win nhắm vào trẻ em, minh bạch dynamic/personalized pricing, drip pricing. Chưa thành luật — nhưng là hướng đi phải theo dõi 2026–2027. [Secondary + primary proposal]
- **UK ASA (2/2026):** quảng cáo có loot box phải disclose. [Primary — ASA ruling]
- **Epic Games settlement $245M (2022, Fortnite)** là tiền lệ lớn nhất về dark pattern + billing. [Primary enforcement — FTC]
- **FTC Click-to-Cancel rule:** hủy subscription phải dễ bằng mua — áp dụng nếu brain đề xuất subscription. [Primary law]
- **Hệ quả:** guardrail engine (doc 06) cần có rule category riêng cho: disclosure của loot box, minh bạch countdown/urgency, khả năng thoát khỏi flow mua trong ≤2 bước.

## E. GDPR / CCPA — personalization là profiling

- **GDPR:** personalized offers dựa trên profiling cần **consent** (không phải legitimate interest) theo hướng TCF 2.2; profiling quy mô lớn cần **DPIA**; EDPB năm 2026 nhấn mạnh transparency obligation cho AI-driven personalization. [Primary — GDPR Art. 22, EDPB]
- **CCPA/CPRA (California):** tháng 11/2025 có án phạt gaming ~$1.4M — tín hiệu cơ quan quản lý đã nhắm vào game, không chỉ adtech. [Primary enforcement]
- **Việt Nam:** Nghị định **356/2025/NĐ-CP** (hiệu lực 1/1/2026, thay thế Nghị định 13/2023): profiling cần DPIA; chuyển dữ liệu xuyên biên giới cần Transfer Impact Assessment; phạt tới **5% doanh thu tại VN**; áp dụng cả với processor nước ngoài xử lý dữ liệu công dân VN. Nghị định **147/2024** cấm casino/card game, yêu cầu xác thực số điện thoại — áp dụng cho game phát hành nội địa. [Primary law]
- **IAB GPP** là chuẩn consent string đa khu vực nên dùng thay vì tự chế định dạng consent. [Primary spec — IAB Tech Lab]

## F. Kiến trúc consent-aware serving

```text
Decision request → Consent Resolver
  │
  ├─ FULL_CONSENT     → pipeline đầy đủ: profile + personalization + measurement
  ├─ PARTIAL_CONSENT  → personalization giới hạn: chỉ feature consented
  │                     (per-feature consent masking trên feature vector)
  └─ NO_CONSENT       → fallback: decision theo segment thô / rule static,
                        không dùng behavioral features; vẫn log decision
                        (log không chứa PII)

Yêu cầu bổ sung:
- Consent provenance trên training data: sample nào được thu khi user
  đồng ý profiling, sample nào không — model chỉ train trên phần được phép.
- Consent change là event hạng nhất: đổi consent → invalidate profile cache
  và đổi tier serving ngay.
- Data Safety form (Google Play) + App Privacy Nutrition Label (iOS) phải
  khớp 1:1 với data flow thực tế của SDK — audit tự động diff là nên có.
```

## G. Checklist tổng hợp (ID dùng xuyên suốt bộ tài liệu)

```text
D1–D8  Ingestion     : consent gate, schema khai báo, retention, minimization,
                       cross-border TIA (VN), Data Safety mapping, SKAN schema,
                       GPP integration
F1–F6  Features      : per-feature consent mask, age-gated features, DPIA cho
                       profiling features, feature provenance, nhạy cảm (health/
                       location) loại hẳn, retention theo tier
T1–T5  Training      : consented-only training set, DPIA trước train profiling
                       model, model card, no-child-data flag, cross-border
                       training data rules (VN 356)
S1–S12 Serving       : 3-tier consent degradation, age/spend cap hard check,
                       loot-box disclosure, price transparency (EU DFA),
                       click-to-cancel cho sub offer, no dark pattern check,
                       audit log, opt-out response time, VN Decree 147 check,
                       GDPR Art.22 human-review path, disclosure text versioning
G1–G7  Governance    : DPIA pipeline, incident response, regulator report path,
                       consent audit trail, policy versioning, trẻ em/đối tượng
                       yếu thế review board, red-team dark pattern định kỳ
```

## H. Tóm tắt theo platform

| Ràng buộc | Android | iOS |
|---|---|---|
| Identifier | GAID còn (chưa rõ tương lai) | Không có IDFA nếu ATT từ chối |
| Attribution | First-party server-side | SKAN 4.0 + AdAttributionKit, cohort-only |
| Personalized pricing | Tự do hơn về kỹ thuật, nhưng Data Safety + GDPR vẫn buộc consent | Chỉ qua offer type/SKU riêng (Guidelines 3.1.x) |
| On-device decisioning | Khả thi đầy đủ | Khả thi đầy đủ |
| Điểm rủi ro cao nhất | Data Safety form khai thiếu | ATT opt-in rate thấp → thiếu data train |

## Không xác minh được (ghi rõ)

1. Nội dung chi tiết cuối cùng của EU Digital Fairness Act (mới ở mức đề xuất, thay đổi liên tục).
2. Chi tiết án phạt CCPA gaming 11/2025 (tên công ty cụ thể trong hồ sơ không trích xuất đầy đủ).
3. Lộ trình chính sách tiếp theo của Google với GAID (chưa có công bố).

## Nguồn chính

- Android Privacy Sandbox winding down — https://android-developers.googleblog.com/ (announcement 10/2025) + https://privacysandbox.google.com/
- Apple SKAN / AdAttributionKit — https://developer.apple.com/documentation/storekit/sk_ad_network ; https://developer.apple.com/documentation/adattributionkit
- Apple Review Guidelines 3.1 — https://developer.apple.com/app-store/review/guidelines/
- FTC COPPA amended rule — https://www.ftc.gov/business-guidance/blog/2025/06/ftcs-updated-coppa-rule-goes-effect-june-23
- PEGI loot box — https://pegi.info/
- EU Digital Fairness Act — https://www.digital-fairness-act.com/
- Nghị định 356/2025/NĐ-CP & Nghị định 147/2024 — văn bản pháp luật VN
- IAB GPP — https://iabtechlab.com/gpp/
- IAB TCF 2.2 — https://iabeurope.eu/transparency-consent-framework/
- Epic settlement — FTC press release 2022
- GameAnalytics/state-of-mobile cho số liệu market: xem doc 09
