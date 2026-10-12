# 27 — Chắt lọc DNA từ teardown "All Document Reader" (Solara)

**Ngày viết:** 2026-08-21
**Nguồn:** `TỔNG HỢP PHÂN TÍCH APP ALL DOCUMENT READER.md`, `ADS_REPORT.md`, `REPORT.md`, `CONFIG_DEFAULTS_LOCAL.md`, `remote_config_defaults_pretty.txt` — báo cáo reverse-engineering (jadx/apktool) một app đối thủ, đánh giá chất lượng ở lượt trao đổi trước. Tài liệu này KHÔNG lặp lại đánh giá chất lượng — chỉ làm một việc: **chắt lọc cái tốt thành của mình, ghi rõ cái xấu để tránh**, và code hóa ngay những gì code hóa được.

> Nguyên tắc chọn lọc: một pattern chỉ được đưa vào đây nếu (a) đã chạy production thật ở app có ~50.000 class, 172MB, phân phối chính thức qua Play Store — tức đã qua kiểm nghiệm quy mô thật, và (b) có thể verify trực tiếp từ code/config đã decompile, không phải suy đoán.

---

## A. Bốn pattern TỐT — đã áp dụng vào code

### A.1 Click-fraud tự động — **đã code: `ClickFraudGuard.kt`**

Solara: `config_prevent_ad_click` — quá 6 click ads trong 300 giây thì khóa toàn bộ ad 1800 giây. Bảo vệ tài khoản AdMob khỏi invalid traffic ngay tại client, không chờ network phát hiện rồi ban tài khoản.

**Đã thêm** `com.app.plugin.brain.ClickFraudGuard` — object in-memory (đúng pattern `AdTracker.kt` sẵn có trong repo), giữ nguyên 3 con số default đã quan sát chạy thật (6/300s/1800s) làm điểm khởi đầu. Nối vào `GuardrailEngine.evaluate()` như một hard constraint áp dụng cho mọi `ActionMode` trừ `NO_ACTION`/`GAMEPLAY_CONFIG`.

### A.2 Waterfall 3 tầng giá (H/M/D), per-ad-type disable timestamp — **đã có trong roadmap (doc 25), giờ có bằng chứng production**

Đây chính là pattern tôi đã thiết kế trừu tượng cho `AdsPlacementConfig`/`BanditPolicy` — Solara là **bằng chứng nó chạy thật, ở quy mô thật**, với con số cụ thể đáng tham khảo làm điểm khởi đầu calib:

| Loại | Khóa H (`time_disable_v3`) | Khóa M (`time_disable_v2`) |
|---|---|---|
| Native | 600s | 300s |
| Interstitial/Pip/Squeeze | 300s | 150s |
| App Open | 150s | 75s |

Không copy nguyên số này vào code (đây là số của một app cụ thể, chưa chắc đúng cho app khác), nhưng dùng làm **prior hợp lý** khi A/B tìm số riêng — tốt hơn nhiều so với đoán từ đầu.

### A.3 Phân biệt organic vs non-organic để định mật độ ad — validates doc 04

Solara tắt hẳn 6 vị trí ad cho user organic (`is_enable_ogn=false`): logic đúng đắn — user tự tìm đến có LTV kỳ vọng cao hơn, nên giữ trải nghiệm nhẹ hơn; user từ UA trả tiền cần thu hồi chi phí nhanh hơn nên chịu mật độ dày hơn. Đây là bằng chứng production cho đúng nguyên tắc "acquisition channel là context quan trọng" đã nêu ở doc 04 mục 2 (FOMO/Metica).

**Áp dụng**: `PlayerState` nên có thêm field `isOrganicAcquisition: Boolean` (chưa thêm ở bản hiện tại — ghi vào việc-tiếp-theo bên dưới), và `GuardrailEngine`/`BanditPolicy` dùng nó như một chiều segment bổ sung ngoài `LatentPlayerType`.

### A.4 Country-tier pricing — production evidence mạnh hơn case study self-reported ở doc 11

Solara map ~160 quốc gia thành 3 tier, override được **mọi** nhóm cấu hình (ad frequency, ad unit, giá IAP) theo tier — không chỉ giá, mà toàn bộ trải nghiệm monetization. Đây là bằng chứng production tốt hơn phần lớn nguồn trong doc 11 (đa số vendor case study tự công bố) — nên trích dẫn case này vào doc 11 như minh chứng "regional pricing tiering không chỉ là lý thuyết academic, mà là cấu trúc chuẩn ở app production thật, kể cả app tier dưới AAA."

## B. Ba anti-pattern — đã ngăn hoặc ghi rõ để tránh

### B.1 Fallback hiếu chiến hơn default chính — **đã code hóa thành nguyên tắc cứng**

Đây là phát hiện **mới nhất, giá trị nhất** từ bản teardown — chưa từng có trong bất kỳ doc 01–26 nào trước đây. Solara có 2 lớp default (XML packaged + hardcode trong code), và khi rơi xuống lớp đáy, hệ thống **tự động hiếu chiến hơn**: gói IAP mặc định nhảy lên mức đắt nhất (30 thay vì 20), tần suất app-open ad dày gấp 12 lần (5 phút thay vì 1 giờ). Không phải bug — code chạy đúng logic — nhưng một lỗi cú pháp JSON khi publish config có thể âm thầm đổi giá bán và mật độ ad mà không ai nhận ra.

**Đã thêm vào `GuardrailEngine.kt`** như một nguyên tắc thiết kế ghi rõ trong doc comment: *"Fail-safe, không phải fail-aggressive"* — mọi giá trị default trong class này phải là **biên an toàn nhất** hệ thống, không phải biên trung bình. Khi Phase sau này thêm lớp fallback thật (mở rộng `AdsPlacementConfigManager.parserConfig()`), đây là ràng buộc bắt buộc phải test, không phải gợi ý.

### B.2 Share state khóa-tầng giữa 2 loại action không liên quan — **đã tránh được nhờ thiết kế sẵn có**

Solara: pip dùng chung ô nhớ khóa tầng với banner, squeeze-back dùng chung với native (8 loại ad nhưng chỉ 6 cặp slot) — hệ quả: 1 loại no-fill kéo loại không liên quan xuống tầng thấp hơn.

`BanditPolicy` của chúng ta **đã tránh lỗi này về mặt kiến trúc** — key theo `"$segmentKey|$armId"`, mỗi `armId` hoàn toàn độc lập, không có cơ chế share ẩn nào giữa 2 action khác nhau. Ghi lại ở đây như một ràng buộc phải giữ, không phải việc cần sửa — **nhưng phải cảnh giác khi tối ưu hiệu năng sau này** (ví dụ nếu ai đó sau này "gộp state cho gọn" để tiết kiệm bộ nhớ, đây chính là lỗi sẽ tái diễn).

### B.3 Người đã trả tiền vẫn thấy quảng cáo — case đối chứng cho `ComfortIndex` (doc 26)

Solara: `is_enable_ads_for_subscribers=true` — subscriber vẫn thấy ad (native đổi thành banner cho nhẹ hơn), nút đóng paywall delay 3 giây. Đây chính xác là loại quyết định mà `ComfortIndex` (doc 26 mục C.4) được thiết kế để phát hiện: `organic_purchase_probability` cao không có nghĩa gì nếu trải nghiệm sau khi mua vẫn giống hệt trước khi mua — `post_purchase_retention_delta` sẽ phản ánh đúng mức độ "hối hận" này nếu có.

**Không code hóa gì thêm** — đây là case dùng để minh họa cụ thể cho tài liệu, không phải bug cần fix (không phải code của chúng ta). Ghi vào doc 14 như một ví dụ đối chứng cụ thể cho phần ethics.

## C. Cập nhật bảng DNA (doc 26 mục B) — 3 dòng mới

| Nguồn | DNA/pattern đã validate | Thể hiện trong SDK |
|---|---|---|
| **Solara teardown (production evidence)** | Click-fraud tự động bảo vệ tài khoản mediation | `ClickFraudGuard.kt` — đã code |
| **Solara teardown (anti-pattern)** | Fallback không được hiếu chiến hơn default chính | Nguyên tắc cứng trong `GuardrailEngine.kt` doc comment |
| **Solara teardown (production evidence)** | Country-tier override toàn bộ monetization, không chỉ giá | Củng cố doc 11; chưa code — Phase 2 (payment/pricing) |

## D. Việc tiếp theo

```text
1. Thêm field isOrganicAcquisition vào PlayerState.kt (mục A.3) — chưa làm trong lượt này,
   cần quyết định cách xác định "organic" nhất quán với AppsflyerHelper hiện có trước khi thêm field
2. Khi xây lớp fallback thật cho policy artifact (doc 25 mục E), viết test xác nhận
   nguyên tắc B.1: mọi giá trị fallback <= giá trị default hiện tại về mức độ ad pressure,
   và <= giá trị default về mức giá IAP mặc định (không tự nhảy lên gói đắt nhất)
3. Trích dẫn case Solara vào doc 11 (regional pricing) và doc 14 (ethics — case đối chứng B.3)
   khi có dịp sửa 2 doc đó — không sửa ngay trong lượt này để tránh chỉnh nhiều file cùng lúc
```

## Không xác minh được (ghi rõ)

1. Ba con số default của `ClickFraudGuard` (6/300s/1800s) là giá trị quan sát được ở MỘT app cụ thể — chưa có cơ sở khẳng định đây là số tối ưu cho app/game khác; cần A/B khi có dữ liệu thật.
2. Chưa verify việc `BanditPolicy` "tránh được" lỗi B.2 bằng test thực tế — đây là suy luận từ đọc code, chưa có unit test xác nhận (xem việc-tiếp-theo ở doc 25 mục G, mục 2).
