# 15 — Thị trường Việt Nam & SEA: Bối cảnh, khoảng trống và MVP wedge

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Vì team phát triển repo này là Android SDK shop tại Việt Nam, đây là chương thị trường: hiện trạng monetization của studio VN/SEA, ai đang dùng công cụ gì, và điểm vào (wedge) thực tế nhất cho brain.

> **Cập nhật định hướng (2026-08-21):** Thị trường mục tiêu của sản phẩm là **US & EU** — các thị trường sẵn sàng chi trả cao (xem doc 17). Chương này giữ nguyên vai trò tham chiếu cho **điểm xuất phát phát triển**: team đặt tại VN, chi phí vận hành thấp, rào cản thử nghiệm thấp — tức VN/SEA là sandbox/dev base hợp lý để build và kiểm thử trước khi đánh US/EU, nhưng thiết kế tính năng, pricing, compliance và benchmark phải lấy US/EU làm chuẩn ngay từ đầu. Wedge IAA-side bên dưới vẫn đúng về mặt "điểm vào kỹ thuật", nhưng giá trị kinh tế lớn nhất nằm ở payer US/EU (IAP/subscription), không phải ở impression SEA giá rẻ.

## Evidence labels

- **[Market report]**: số liệu từ AppMagic/Sensor Tower/data.ai hoặc tương đương (thường là ước tính).
- **[Case study — self-reported]**: số do studio/vendor tự công bố.
- **[Primary law]**: văn bản pháp luật VN.
- **[Secondary]**: báo chí ngành game.

---

## A. Quy mô và cấu trúc thị trường

- **Việt Nam:** doanh thu mobile game ~$825M (2025), toàn ngành game ~$1.66B. [Market report]
- **Xuất khẩu:** ~6.7 tỷ lượt tải do studio Việt phát hành ra nước ngoài — Việt Nam nhiều năm liền là **quốc gia xuất khẩu game mobile top đầu thế giới**; ~95% user của game Việt là quốc tế. [Market report / Secondary]
- **Platform:** ~84% Android — khác hẳn US/EU (iOS-leaning). **Mọi kiến trúc phải Android-first.**
- **Monetization mix:** ~89% game Việt dùng quảng cáo; ~60% có IAP — tức phần lớn là **hybrid IAA+IAP**, đúng loại game mà cross-modal brain (doc 01) phát huy giá trị nhất.
- **Thể loại chuyển dịch:** hypercasual từ ~70% danh mục xuất khẩu tụt xuống ~50%; **hybrid-casual và midcore đang lên**. Đây là inflection point: hypercasual chỉ cần ad frequency thô; hybrid-casual bắt đầu cần decisioning per-user vì có cả ad, IAP, session depth.

## B. Bức tranh publisher VN

| Studio | Thể loại/thế mạnh | Monetization hiện tại | Evidence |
|---|---|---|---|
| **VNG** | ~55% thị phần nội địa; MMORPG, game phát hành nội địa | IAP-heavy, liveops thủ công | [Secondary] |
| **Amanotes** | Music game; 3.5B+ downloads | IAA-heavy; stack Pangle + Mintegral + Anzu | [Case study — self-reported] |
| **OneSoft (1SOFT)** | Game casual xuất khẩu; doanh thu xuất khẩu top 1 (~$70M/năm theo thống kê ngành) | IAA + IAP; tự báo IAP +22% trong 5 tháng sau tối ưu | [Case study — self-reported] |
| **Funtap / SohaGame / GOSU** | Phát hành nội địa, mobile MMO/card | IAP + kênh nạp nội địa | [Secondary] |
| **Bravestars Games** | Casual/puzzle xuất khẩu | Moloco UA; báo tăng trưởng 2.8x QoQ | [Case study — self-reported] |
| **ABI Games, iKame (Zego), XGame** | Casual/hypercasual xuất khẩu | IAA là chính; XGame báo AdMob revenue +40% sau tối ưu | [Case study — self-reported] |

## C. Khoảng trống chiến lược ở VN/SEA

1. **Không studio VN nào công bố hệ thống ML decisioning per-user tự xây** cho monetization. Toàn bộ trí thông minh monetization được thuê ngoài cho platform tool (AdMob, AppLovin, TopOn, TradPlus, Moloco). Đây là khoảng trống mà một SDK trung lập có thể chiếm.
2. **Mediation stack của SEA phân mảnh hơn US**: ngoài MAX/AppLovin còn TopOn, TradPlus (mạnh ở CN/SEA), Mintegral, Pangle. Brain trung lập với mediation stack (doc 12, white space #4) có giá trị đặc biệt cao ở đây.
3. **ARPU thấp, volume cao**: SEA (Indonesia volume lớn nhưng ARPU thấp; Thái Lan là thị trường IAP tốt nhất SEA) → mỗi decision sai trên impression rẻ ít đau, nhưng sai ở tần suất lớn; ngược lại gain nhỏ per-user nhân với hàng trăm triệu user thành số lớn. Đây là điều kiện lý tưởng cho **IAA-side optimization** (frequency/timing) hơn là IAP price bandit.
4. **Rào cản pháp lý thấp hơn EU/US** cho thử nghiệm (nhưng Nghị định 356/2025 sẽ nâng chuẩn từ 2026 — doc 13), nghĩa là VN là **sandbox thực tế tốt** trước khi mang sản phẩm ra thị trường khó hơn.

## D. MVP wedge đề xuất cho team này

```text
Phase 0 (weeks): Baseline & measurement
  - Decision log chuẩn (doc 06) cho 2 loại decision đầu tiên
  - Event taxonomy tối thiểu; server-side first-party events

Phase 1 (MVP): IAA-side decisioning — nơi pain lớn nhất và data sẵn nhất
  a) Per-user interstitial frequency:
     thay vì rule "mỗi N phiên", chọn frequency theo user state
     (session length trend, ad tolerance proxy, revenue-per-impression
     theo giờ). Thuật toán bắt đầu: contextual bandit Thompson Sampling
     trên 3–5 frequency arms theo segment.
  b) Rewarded video offer timing:
     quyết định CÓ hiện entry-point rewarded không và hiện lúc nào
     (sau fail thật — theo doc 14 chỉ khi offer không phải cách duy
     nhất để qua; KHÔNG engineered failure).
  c) Vị trí SDK: giữa game và mediation (AdMob/TopOn/TradPlus) —
     không thay mediation, chỉ quyết định "gọi mediation lúc nào,
     cho ai, bao nhiêu lần".

Phase 2: IAA ↔ IAP switching
  - Cùng một decision point, chọn REWARDED_AD vs IAP_OFFER vs NO_ACTION
    dựa trên propensity trả tiền + giá trị ad kỳ vọng. Đây là lúc
    brain thành cross-modal thật sự — và là lúc khác biệt với mọi
    vendor (doc 12).

Phase 3: Full action space của doc 01 (bundle, discount depth, liveops)
  - Chỉ khi có đủ decision log volume và LTV backbone (doc 11).
```

**Hạ tầng đề xuất:** inference on-device (TFLite/LiteRT — doc 16) cho decision latency thấp và giảm chi phí server; cloud fallback vùng VN/SEA cho model nặng và training. Điều này cũng khớp với thực tế hạ tầng mạng VN và chi phí vận hành của studio nhỏ.

## E. Rủi ro thị trường cần theo dõi

1. **AppLovin/Unity mở rộng self-serve AI xuống phân khúc nhỏ** (Axon self-serve 6/2026, doc 12) — có thể đóng một phần white space trước khi team kịp build. Tốc độ là lợi thế duy nhất.
2. **Google siết Data Safety / chính sách ad** bất kỳ lúc nào (Privacy Sandbox đã chết nhưng chính sách Play Store thì không — doc 13).
3. **Nghị định 356/2025/NĐ-CP** (hiệu lực 1/1/2026): DPIA cho profiling, Transfer Impact Assessment cho chuyển dữ liệu xuyên biên giới, phạt tới 5% doanh thu tại VN — áp dụng cả với game xuất khẩu có user VN. [Primary law]
4. **Nghị định 147/2024/NĐ-CP**: cấm casino/card game, xác thực số điện thoại — chỉ ảnh hưởng game phát hành nội địa. [Primary law]

## Không xác minh được (ghi rõ)

1. Các con số doanh thu studio (OneSoft ~$70M, Amanotes downloads, Bravestars 2.8x) đều là self-reported hoặc ước tính báo chí.
2. Thị phần nội địa VNG ~55% là ước tính phổ biến trong ngành, không có nguồn kiểm toán.
3. Không có dữ liệu công khai về việc studio VN nào đang thử nghiệm ML decisioning nội bộ (bí mật).

## Nguồn chính

- AppMagic / Sensor Tower / data.ai reports về VN 2024–2025 (qua coverage của PocketGamer.biz, GameHub, VnExpress)
- Case Moloco × Bravestars — https://www.moloco.com/ (case studies)
- Nghị định 356/2025/NĐ-CP, Nghị định 147/2024/NĐ-CP — văn bản pháp luật VN
- AdMob/MAX/TopOn/TradPlus docs — xem doc 09
- Bối cảnh medation/AXON — xem doc 12
