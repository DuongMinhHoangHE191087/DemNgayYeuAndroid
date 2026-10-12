# 26 — Product Output Definition: Sản phẩm này thực sự là gì, cho ai, đo bằng gì

**Ngày viết:** 2026-08-21
**Vai trò:** Đây là tài liệu chốt sản phẩm (product spec), không phải nghiên cứu — nó hợp nhất docs 01–25 thành **một định nghĩa đầu ra duy nhất**, dựa trên định hướng trực tiếp: sản phẩm phải vừa hữu ích cho đội vận hành, vừa là SDK mạnh cho game/app, chắt lọc DNA tinh túy từ các studio lớn thế giới, và mục tiêu cuối là tăng ARPDAU/doanh thu/subscription **đồng thời** khiến user thoải mái và sẵn sàng chi trả — không phải một sự đánh đổi.

> **Thu hẹp (chủ dự án, 2026-09-29).** Deliverable thứ hai — **Control Plane (dashboard 5 module) bị bỏ**: không có
> đội vận hành riêng, Firebase Console + Remote Config đang làm việc này. Deliverable còn lại là SDK Android nội bộ
> (doc 61); lộ trình và phạm vi ở DOC/65 mục 5. Bảng DNA và 4 North Star Metrics giữ làm tham chiếu, không còn là
> tiêu chí nhận task. "Game studios integrate it directly" đã bị thay từ 2026-09-23 (doc 61).

---

## A. Hai đầu ra, một hệ thống

Sản phẩm có **hai mặt hàng giao (deliverable) khác nhau, dùng chung một bộ não**:

```text
                    ┌─────────────────────────┐
                    │   DECISION BRAIN (lõi)   │
                    │  belief state, bandit,   │
                    │  guardrail, reward model  │
                    └───────────┬─────────────┘
                                │
              ┌─────────────────┴─────────────────┐
              ↓                                     ↓
   ┌───────────────────────┐          ┌──────────────────────────┐
   │   1. SDK (client-side) │          │  2. CONTROL PLANE (web)   │
   │   tích hợp vào app/game│          │  cho đội vận hành          │
   │   → studio dùng        │          │  → PM/Data/Monetization    │
   └───────────────────────┘          └──────────────────────────┘
```

### 1. SDK — sản phẩm cho studio game/app tích hợp

Ship dưới dạng **thư viện Android (AAR, qua Maven/GitHub Packages — đúng cơ chế `build.gradle.kts` đã có sẵn trong repo)**, về sau mở rộng iOS (Pod/SPM). Studio tích hợp một lần, gọi `DecisionEngine.decide()`/`feedback()` (đã có code thật ở `com.app.plugin.brain`), và SDK tự vận hành:

- Quyết định per-user cho ad frequency/timing, IAP offer, gameplay config, subscription nudge.
- Guardrail chạy on-device, không cần chờ server (đã implement trong `GuardrailEngine`).
- Đồng bộ policy artifact định kỳ qua kênh Remote Config sẵn có (`FirHelper`), không cần backend riêng để *bắt đầu* dùng ở mức rule-based (Phase 0–1 của doc 23).

**Cái studio thấy được**: tích hợp giống hệt cách họ đang tích hợp `AdsHelper`/`AppsflyerHelper` hiện tại — quen thuộc, không phải học framework mới.

### 2. Control Plane — sản phẩm cho đội vận hành

Đây là mảnh **hoàn toàn chưa build**, nhưng được xác định rõ vai trò (tương ứng "Product Tooling and Control Plane" đã nêu trong `11_DEEP_RESEARCH_EXPANSION.md` mục 4 của nhánh nghiên cứu song song — tôi xác nhận và tích hợp vào định nghĩa chính thức tại đây thay vì để rời rạc). Tối thiểu 5 module:

| Module | Chức năng | Người dùng |
|---|---|---|
| Decision-Point Catalog | Xem toàn bộ decision point đang chạy, tần suất gọi, action space mỗi điểm | PM, Data |
| Action/Offer Catalog | Định nghĩa offer/reward/bundle hợp lệ (business constraint trước ML — doc 06 mục 12) | Monetization, Game Economy |
| Policy Builder | Cấu hình guardrail (cap, threshold), bật/tắt bandit theo decision point, không cần code | Monetization, Data |
| Experiment Setup | Tạo A/B/holdout, xem kết quả tách biệt khỏi personalization (doc 05 mục 10) | Data, PM |
| Monitoring Dashboard | ARPDAU/revenue/subscription theo thời gian thực **+ chỉ số comfort (mục C)**, guardrail vi phạm, decision log audit | Toàn đội vận hành |

**Cái đội vận hành thấy được**: một dashboard trả lời "chính sách hiện tại đang làm gì, có an toàn không, có hiệu quả không" — không phải đọc log thô.

## B. DNA chắt lọc từ các studio lớn — bảng ánh xạ cụ thể

Đây là yêu cầu cốt lõi: SDK không phát minh lại từ đầu, mà **chắt lọc pattern đã được validate production** từ các nguồn đã nghiên cứu (docs 02, 03, 24), biến thành tính năng cụ thể trong SDK:

| Nguồn (studio/vendor) | DNA/pattern đã validate | Thể hiện trong SDK |
|---|---|---|
| **King** (Candy Crush) | Dự đoán *khi nào* mua, không chỉ *có mua không*; >200 candidate feature từ trajectory | `PlayerState` có field trajectory (fail streak, burn rate) thay vì chỉ snapshot |
| **Firebase/FOMO** | IAA vs IAP là lựa chọn per-cohort, không song song mù quáng | `ActionMode` gồm cả REWARDED_AD lẫn IAP_OFFER cùng một candidate set, để bandit chọn |
| **Spotify** | Tách biệt personalization stack khỏi experimentation stack | Control Plane có Experiment Setup **riêng** khỏi Policy Builder |
| **Dream11** | Contextual uplift bandit, hierarchical context giảm cold-start | `segmentKey` ghép decision point + belief type — chuẩn bị cho nâng cấp hierarchical |
| **Wildlife Studios** | RL cho sequence quyết định, không greedy từng action | Reward contract (doc 11) có `γ·[LTV(s')−LTV(s)]` — chưa implement nhưng đã spec |
| **Metica** | Constraint programming trước, bandit ranking sau | `DecisionEngine.decide()` nhận `candidates` đã lọc business rule từ caller, không tự sinh offer |
| **Tencent/HKU (POMDP)** | Belief state ẩn, policy nén đủ nhỏ chạy on-device | `BeliefState` rời rạc — chạy on-device, không cần Monte Carlo planning nặng |
| **Playtika** | Experience Control Plane tách khỏi decision intelligence, conflict detection giữa các patch | Kiến trúc SDK/Control Plane tách biệt (mục A) chính là áp dụng nguyên tắc này |
| **Braze/OfferFit** | Factorized policy — chia nhỏ action space thay vì 1 bandit khổng lồ | `ActionMode` + `actionId` là 2 tầng; mở rộng thêm tầng value/placement/timing khi cần |
| **Adobe Target** | Policy artifact cache on-device, fallback server khi chưa có cache | Kênh `FirHelper.fetchRemoteCf()` sẵn có = policy artifact transport (doc 16 mục D.1) |
| **RevenueCat/Adapty** | Paywall builder remote-config, A/B price/copy | Chưa build (Phase 2, doc 23) — nhưng **phải có holdout-native** vì đây là white-space họ không có (doc 19) |
| **Epic/Scopely (Monopoly Go)** | Web-shop/D2P giữ margin cao hơn store 30% | Purchase router đa kênh (Phase 1, doc 23) |

Đây không phải danh sách đầy đủ — mọi doc 01–25 đều có thể tiếp tục đóng góp thêm dòng vào bảng này khi tính năng mới được thiết kế.

## C. North Star Metrics — bốn chỉ số, không đánh đổi lẫn nhau

Yêu cầu của bạn có 4 mục tiêu. Điều quan trọng nhất về mặt thiết kế: **3 mục tiêu đầu dễ tối ưu riêng lẻ và dễ đánh đổi mục tiêu thứ 4** nếu không có ràng buộc rõ — đây chính là lý do toàn bộ kiến trúc guardrail (doc 06, 14) tồn tại. Định nghĩa chính xác từng chỉ số:

### C.1 ARPDAU (Average Revenue Per Daily Active User)

```text
ARPDAU = (IAP_revenue_24h + IAA_revenue_24h) / DAU
```

Đã có sẵn hạ tầng đo: `FirHelper.logAdTroas()`/`logAdTopRev()` hiện đang track ad revenue tích lũy theo ngưỡng percentile — đây là tiền thân thô của ARPDAU tracking, nên **nâng cấp thành field chính thức** thay vì để rời rạc trong `FirHelper`.

### C.2 Revenue (tổng, và theo kênh)

Tách theo kênh (doc 18): Store IAP, web-shop D2P, subscription — vì margin khác nhau (~70% net qua store vs ~92% net qua web-shop). SDK nên report **revenue theo kênh**, không gộp một số, để đội vận hành thấy được hiệu quả thật của payment routing.

### C.3 Subscription count (lượt sub)

Số subscription active + conversion rate free→paid + retention subscription (D30, D90). Đây là chỉ số duy nhất trong 4 mục tiêu **tự nó đã có tín hiệu comfort tích hợp sẵn** — subscription hủy nhanh (`Day 0 cancellation 55.4%` theo doc 19) là dấu hiệu ép buộc, không phải thành công thật.

### C.4 Player Comfort & Willingness-to-Pay (chỉ số mới cần định nghĩa — đây là phần thiếu nhất trong KB)

Đây là chỉ số duy nhất **chưa có công thức rõ** ở bất kỳ doc nào trước đây. Tôi định nghĩa nó ở đây bằng cách ghép các latent variable **đã có sẵn** trong doc 04/24, không phát minh field mới:

```text
ComfortIndex = w1·organic_purchase_probability     (mua không cần ép — dấu hiệu tốt nhất)
             − w2·ad_fatigue_score
             − w3·churn_risk
             − w4·refund_rate
             (đã bỏ: guardrail_violation_rate_prevented — xem đính chính bên dưới)
             + w6·post_purchase_retention_delta      (D7 sau khi mua so với trước khi mua)
```

- `organic_purchase_probability` = `P(Purchase | do(NoAction), X)` — đã định nghĩa ở doc 04 mục 14. Đây là **tín hiệu willingness-to-pay chân thực nhất**: nếu user mua mà không cần discount/pressure, đó là monetization lành mạnh.
- ~~`guardrail_violation_rate_prevented`~~ — **ĐÃ BỎ khỏi công thức (đính chính 2026-08-22).** Bản đầu cho nó dấu âm nhưng lại mô tả là "proxy cho hệ thống đang bảo vệ user" (tức tốt) — hai điều đó mâu thuẫn. Xử lý đúng: guardrail nổ nhiều nghĩa là *chính sách bên dưới đang muốn hung hăng và bị chặn* — đó là tín hiệu về **sức khoẻ chính sách**, không phải về trải nghiệm người chơi (người chơi đã được bảo vệ nên comfort của họ không đổi). Gộp hai thứ làm cả hai mất ý nghĩa. Nay báo cáo riêng qua `ComfortIndex.policyAggressiveness()`. Xem doc 40.
- `post_purchase_retention_delta` — nếu retention giảm sau khi mua, đó là dấu hiệu hối hận/ép buộc dù revenue tăng tức thời.

> **Trạng thái implement (2026-08-22):** `ComfortIndex.kt` đã có, nhưng **chỉ 2/5 thành phần tính được** — 3 thành phần còn lại cần causal model, theo dõi refund và theo dõi retention sau mua, đều chưa tồn tại. Vì vậy hàm luôn trả về kèm `coverage`, và `isTrustworthy()` hiện trả về `false`. Xem doc 40.

**Ràng buộc bắt buộc** (không phải gợi ý): mọi thay đổi policy trong Control Plane's Policy Builder phải hiển thị cả 4 chỉ số cùng lúc, không cho phép xem riêng ARPDAU/Revenue mà ẩn ComfortIndex — đây là cách kiến trúc ngăn đội vận hành vô tình tối ưu 3 chỉ số đầu bằng cách hy sinh chỉ số thứ 4, đúng tinh thần "hai lớp tách biệt" đã nêu ở doc 14 (engagement loop ≠ monetization loop).

## D. "Thành công" nhìn thấy ở đâu — cho từng phía

| Phía | Nhìn thấy thành công ở đâu |
|---|---|
| Studio tích hợp SDK | ARPDAU tăng trong dashboard analytics của chính họ (Firebase/AppsFlyer hiện có) — SDK không cần tự có UI riêng cho studio, chỉ cần đẩy đúng event vào pipeline họ đã dùng |
| Đội vận hành | Control Plane Monitoring Dashboard — 4 chỉ số mục C cùng lúc, theo decision point, theo experiment |
| Player (gián tiếp) | Không có UI riêng — thành công của player thể hiện qua ComfortIndex tăng, KHÔNG qua việc hỏi trực tiếp player (research UX riêng nếu cần validate thêm) |

## E. Việc cần làm tiếp theo để hiện thực hóa định nghĩa này

```text
1. Thêm field ARPDAU/ComfortIndex vào DecisionLogger — hiện log chỉ có
   chosen_action_probability, chưa có chỗ cho outcome tổng hợp theo ngày.
2. GuardrailEngine cần đếm violation đã prevented (hiện chỉ trả về/không trả về
   violation cho 1 lần gọi, chưa có counter tích lũy) — sửa nhỏ, không phải viết lại.
3. Control Plane là dự án riêng (web dashboard + backend), NGOÀI phạm vi repo
   Android này — cần quyết định stack riêng, chưa nên giả định ở đây.
4. Bảng DNA (mục B) nên là tài liệu sống — cập nhật mỗi khi thêm tính năng mới,
   không viết một lần rồi bỏ quên.
```

## Không xác minh được (ghi rõ)

1. Trọng số `w1..w6` trong `ComfortIndex` là placeholder cấu trúc, chưa được calibrate từ dữ liệu thật — cần A/B để tìm trọng số đúng, không dùng số ở đây làm giá trị production.
2. Chưa có bằng chứng thực nghiệm nào (trong toàn bộ KB) đo trực tiếp mối quan hệ giữa `organic_purchase_probability` và long-term satisfaction ngoài suy luận logic — đây là giả thuyết thiết kế, không phải kết quả đã kiểm chứng.
