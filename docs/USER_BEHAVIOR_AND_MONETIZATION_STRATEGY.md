# Chiến Lược Hành Vi Người Dùng & Kiếm Tiền Win-Win (InLove)

> Tài liệu tổng hợp định hướng sản phẩm và cơ chế kiếm tiền bền vững cho **InLove (Đếm Ngày Yêu)** trên Android & iOS, kết hợp nghiên cứu thị trường (`docs/PRODUCT_RESEARCH.md`, `docs/research/2026-10-27-gift-occasion-market-research.md`) và rà soát `:appplugin` (`docs/APPPLUGIN_SDK_OVERVIEW.md`).

---

## 1. Chân Dung Người Dùng & Vòng Lặp Hành Vi Cốt Lõi

| Trục hành vi | Thực tế người dùng (Gen Z & Millennials VN/SEA) | Cơ chế sản phẩm InLove đáp ứng |
|---|---|---|
| **Mở app hằng ngày (0-tap / 1-tap)** | Muốn nhìn thấy số ngày yêu tăng lên và cảm xúc gắn kết mỗi sáng | `CoupleWidgetSnapshot` (Glance / WidgetKit), bộ đếm `Day 1 = startDate` đồng nhất (`AnniversaryBehaviorEngine`) |
| **Không bỏ lỡ cột mốc cảm xúc** | Đếm ngày (7, 14, 22, 30, 50, 52, 99, 100, 200, 300, 365, 500, 520, 730, 1000, 1095, 1314, 1825, 2000, 3650), kỷ niệm tháng (Monthiversary) & lễ Âm lịch VN | Bảng âm lịch xác minh 2025–2040 (`LunarCalendar2025To2040`, sửa Rằm Giêng 2027 = `2027-02-20`, bổ sung Thất Tịch 7/7 ÂL) + `NextOccasionReadiness` |
| **Nỗi đau khi tặng quà** | Hay quên size áo/giày/nhẫn, dị ứng thực phẩm/mùi hương, tặng trùng năm ngoái, hoặc đợi sát ngày mới đặt dẫn đến hết chỗ | **Hồ Sơ Người Thương (`PartnerDossier`)**: Love Languages, 14 sở thích chuẩn, size, món ăn/dị ứng/kiêng kỵ, Ghi chú bí mật, lịch sử quà đã tặng |
| **Chuẩn bị theo giai đoạn** | Valentine, Sinh nhật, 8/3, 20/10, Giáng Sinh cần đặt bàn/quà trước 14–21 ngày; một số lễ (Vu Lan, Giỗ Tổ, Rằm Giêng) KHÔNG phải dịp tặng quà cặp đôi | **Mô hình 3 trục (`OccasionProfile`)**: tách riêng `CoupleRelevance`, `GiftExpectation`, `defaultReminderOn` + 5 `PreparationStage` |
| **Bí mật & tin cậy** | Kỷ niệm riêng tư và ghi chú bất ngờ không được lộ ra màn hình khóa hay gửi thô lên LLM | Redaction trên thông báo/widget khi khóa PIN; `AiPromptBuilder` ẩn danh hoàn toàn PII (không gửi tên thật, email, SĐT, URL bí mật) |

---

## 2. Mô Hình Dịp Lễ 3 Trục & Giai Đoạn Chuẩn Bị

Thay vì dùng một cờ boolean duy nhất hay nhắc đồng loạt mọi ngày lễ trước 3 ngày, InLove phân tách hành vi theo **3 trục độc lập** trong `AnniversaryBehaviorEngine`:

| Dịp tiêu biểu | `CoupleRelevance` | `GiftExpectation` | `defaultReminderOn` | `bookingAdvised` | Nhắc sớm khuyến nghị |
|---|---|---|---|---|---|
| Kỷ niệm ngày yêu (`couple_anniversary`) | `CORE` | `MAJOR` | `true` | `true` | 30, 14, 7, 3, 1, 0 ngày |
| Sinh nhật người thương (`partner_birthday`) | `CORE` | `MAJOR` | `true` | `true` | 21, 14, 7, 3, 1, 0 ngày |
| Lễ Tình Nhân Valentine 14/2 (`valentine`) | `CORE` | `EXPECTED` | `true` (tắt ở ID/MY/SA/KW) | `true` | 21, 14, 7, 3, 1, 0 ngày |
| 8/3 & 20/10 (`women_day_0803`, `vn_women_day_2010`) | `CORE` (VN) | `EXPECTED` | `true` (VN) | `true` | 14, 7, 3, 1, 0 ngày |
| Lễ Thất Tịch 7/7 ÂL (`that_tich`) | `CORE` (VN) | `SMALL` | `true` (VN) | `false` | 7, 3, 1, 0 ngày |
| Tết Trung Thu 15/8 ÂL (`trung_thu`) | `RELEVANT` | `SMALL` | `true` (VN) | `false` | 14, 7, 3, 1, 0 ngày |
| Rằm Tháng Giêng, Giỗ Tổ Hùng Vương | `NONE` | `NONE` | `false` | `false` | 3, 1 ngày (chỉ nếu bật tay) |
| Lễ Vu Lan 15/7 ÂL (`vu_lan`) | `OPTIONAL` | `NONE` | `false` | `false` | 7, 3 ngày (gia đình) |

### 5 Giai Đoạn Chuẩn Bị (`PreparationStage`)
1. `RELAXED` (> 21 ngày hoặc dịp không cần đặt sớm): nhắc nhẹ tích lũy **Ghi chú bí mật** (`SecretHintNote`) khi người ấy vô tình nhắc tới món mình thích.
2. `PLAN_AHEAD_BOOKING` (8–21 ngày cho dịp `MAJOR` / `bookingAdvised`): nhắc đặt nhà hàng, staycation, hoặc quà handmade cần thời gian chế tác.
3. `ACTION_NEEDED` (4–7 ngày): chốt quà theo ngân sách & đối chiếu bảng kiêng kỵ/dị ứng.
4. `URGENT_LAST_MINUTE` (1–3 ngày): tự động ưu tiên quà có `leadTimeDays <= 2` (vé xem phim ghế đôi, hoa giao trong ngày, bữa tối tự nấu, playlist + picnic).
5. `TODAY_CELEBRATION` (0 ngày): kích hoạt **Vùng Thiêng (`MILESTONE_CELEBRATION_DAY`)**, tắt hoàn toàn quảng cáo, tôn vinh lời chúc và lưu khoảnh khắc.

---

## 3. Chiến Lược Kiếm Tiền Win-Win (Tôn Trọng Cảm Xúc Cặp Đôi)

### 3.1 Quản Trị `:appplugin` (Loại Bỏ Rủi Ro Brain Thử Nghiệm)
Theo phân tích trong `docs/APPPLUGIN_SDK_OVERVIEW.md`, SDK `:appplugin` vốn tối ưu cho **game US/EU** và chưa có backend BigQuery huấn luyện cho `AdsBrain`. Vì vậy:
- **Kiến trúc chuẩn**: Bao bọc `:appplugin` sau `AppPluginMonetizationFacade` (`AdPolicyGate`, `MonetizationFeatureGate`) kết hợp luật thuần từ `:shared` (`AdPlacementPolicy`).
- **Tôn trọng cấu hình ứng dụng**: Không để `AdsBrain` tự quyết định chèn quảng cáo vào khoảnh khắc nhạy cảm; mọi yêu cầu hiển thị quảng cáo đều phải qua `AdPlacementPolicy.evaluate(...)`.
- **Single Source of Truth SKU**: Chỉ dùng duy nhất 3 mã sản phẩm `vip_monthly`, `vip_yearly` (dùng thử miễn phí 3 ngày), `vip_lifetime` đồng bộ giữa Google Play Billing (`VipProductIds`), `:shared` (`ProTierCatalog`), và iOS StoreKit 2 (`InLove.storekit`).

### 3.2 Quy Tắc Vùng Thiêng (`SacredZone`) & Tần Suất Quảng Cáo

| Quy tắc | Giá trị / Điều kiện | Lý do Win-Win |
|---|---|---|
| **VIP User** | `isVip = true` -> Chặn 100% quảng cáo | Giá trị cốt lõi của gói Pro, giải phóng cache ad ngay lập tức |
| **UMP Consent** | `hasConsent = false` -> Chặn 100% | Tuân thủ GDPR / UMP và Google Play Ads Policy |
| **Vùng Thiêng (`SacredZone`)** | `ONBOARDING`, `PAYWALL`, `PIN_LOCK`, `MILESTONE_CELEBRATION_DAY`, `MEMORY_CAPTURE_FLOW`, `PAIRING_ACCEPT_FLOW` | Không bao giờ ngắt mạch cảm xúc lúc mở khóa PIN, chụp ảnh kỷ niệm, nhận lời mời Set Love, hay ngày kỷ niệm chính |
| **Warmup đầu phiên** | `SESSION_WARMUP_MS = 60_000L` (60 giây) | Người dùng mở app xem số ngày yêu buổi sáng không bị quảng cáo chen ngang |
| **Cooldown Interstitial** | `INTERSTITIAL_COOLDOWN_MS = 120_000L` (120 giây) | Chống spam xen kẽ, bảo vệ retention D7/D30 |
| **Giới hạn phiên** | `MAX_INTERSTITIALS_PER_SESSION = 3` | Bảo đảm trải nghiệm nhẹ nhàng cho người dùng miễn phí |

### 3.3 Ma Trận Quyền Lợi Free vs InLove Pro (`ProFeature`)

| Tính năng (`ProFeature`) | Free (Khởi đầu giá trị thật) | InLove Pro (`vip_monthly` / `vip_yearly` / `vip_lifetime`) |
|---|---|---|
| Đếm ngày yêu, lịch lễ VN/Quốc tế, widget cơ bản | Miễn phí đầy đủ | Miễn phí đầy đủ |
| **Quảng cáo (`AD_FREE`)** | Banner nhẹ ngoài Vùng Thiêng; Interstitial có warmup 60s & cooldown 120s | **100% Không Quảng Cáo** |
| **Trợ Lý Quà & Lời Chúc AI (`AI_GIFT_CONCIERGE_UNLIMITED`)** | **2 lượt Cloud AI / tháng** + không giới hạn gợi ý cục bộ `SmartRuleGiftAdvisor` | **60 lượt Cloud AI / tháng** (server gate `vip`) + soạn lời chúc đa sắc thái |
| **Hồ Sơ Người Thương (`DEEP_PARTNER_DOSSIER_UNLIMITED`)** | Đầy đủ Love Languages, sở thích, size, dị ứng + tối đa **5 Ghi chú bí mật** | **Không giới hạn Ghi chú bí mật** & lịch sử quà nhiều năm |
| **Nhắc Hẹn Sớm (`EARLY_14D_30D_REMINDERS`)** | Nhắc chuẩn 0 / 1 / 3 / 7 ngày | Mở khóa nhắc sớm **14–30 ngày** cho dịp cần đặt chỗ trước |
| **Nhắc Tròn Tháng (`MONTHIVERSARY_CADENCE`)** | Cột mốc ngày lớn & kỷ niệm năm | Tự động nhắc **Monthiversary hằng tháng** + mốc 520, 1314 |
| **Giao Diện & Widget (`PREMIUM_WALLPAPERS_AND_WIDGETS`)** | Giao diện & widget chuẩn | Bộ hình nền cao cấp & kiểu widget đa dạng |

### 3.4 Paywall Mềm Theo Ngữ Cảnh (`UpsellTrigger`) — Không Dark Pattern
- Chỉ hiện `SoftPaywallPrompt` tại đúng thời điểm người dùng **tự phát sinh nhu cầu**:
  1. `AI_QUOTA_REACHED`: Đã dùng hết `2/2` lượt Cloud AI trong tháng -> gợi ý mở khóa `vip_yearly` (kèm fallback `SmartRuleGiftAdvisor` vẫn hoạt động ngay lập tức, không khóa màn hình!).
  2. `SECRET_NOTES_LIMIT`: Khi chạm mốc `5` Ghi chú bí mật -> giải thích nhẹ nhàng lợi ích lưu không giới hạn.
  3. `EARLY_REMINDER_14D_30D`: Khi bật nhắc 14–30 ngày trước sinh nhật/kỷ niệm lớn.
  4. `MONTHIVERSARY_CADENCE`: Khi bật nhắc tròn tháng tự động.
  5. `WIDGET_OR_WALLPAPER_LOCKED` & `POST_MILESTONE_CELEBRATION` (sau khi kết thúc ngày lễ, không chen vào giữa ngày lễ).
- **Quy tắc minh bạch Play/App Store & Pháp lý**:
  - Không dùng nhãn "AI" giả cho template tĩnh (mọi output `SmartRuleGiftAdvisor` ghi rõ `AdviceSource.SMART_LOCAL_ENGINE`, chỉ Gemini Cloud Function mới gắn `AdviceSource.CLOUD_AI`).
  - Không gắn affiliate ẩn trong danh mục quà cho tới khi hoàn thiện nhãn công bố và kiểm tra UMP/Play Data Safety/ATT.

---

## 4. Chiến Lược AI Lai (Hybrid Concierge) & An Toàn Chi Phí

| Lớp xử lý | Cơ chế | Chi phí & Giới hạn | An toàn & Quyền riêng tư |
|---|---|---|---|
| **Local First (`SmartRuleGiftAdvisor`)** | Chấm điểm 29 mẫu quà song ngữ + Ghi chú bí mật theo Love Languages, 14 sở thích, size/màu/mùi hương, `daysUntilOccasion`, trừ dị ứng & lịch sử đã tặng | **0 USD**, độ trễ < 5ms, chạy offline 100% trên Android & iOS | Toàn bộ dữ liệu ở trên máy |
| **Cloud AI (`generateGiftAdvice` - Gemini 2.5 Flash)** | Callable có `enforceAppCheck: true`, rate limit, kiểm tra `gatePolicy` (`tiered`: `vip` = 60/tháng, `free` = 2/tháng), fallback server engine nếu thiếu `GEMINI_API_KEY` | Kiểm soát chặt theo UID & tháng (`aiAdvisorQuota/{uid}_{YYYYMM}`) | `AiPromptBuilder` lọc sạch tên thật (`nickname`), email, SĐT, URL và không gửi `secretSurpriseNotes` lên LLM |

---

## 5. Lộ Trình Mở Rộng Thị Trường
1. **Giai đoạn 1 (Việt Nam)**: Hoàn thiện chiều sâu âm lịch 2025–2040, Thất Tịch 7/7, Hồ sơ người thương, Hybrid AI Concierge & Widget iOS/Android.
2. **Giai đoạn 2 (Philippines)**: Tận dụng giao diện EN sẵn có, Android chiếm tỷ trọng cao, văn hóa *monthsary* (`MONTHIVERSARY_CADENCE`).
3. **Giai đoạn 3 (Brazil & Thái Lan)**: Bổ sung BCP-47 localization, Dia dos Namorados 12/06 (BR), chuẩn hóa tiền tệ đa quốc gia và kiểm chứng lịch địa phương.
