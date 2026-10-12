# 25 — Technical Implementation Blueprint: từ nghiên cứu tới code chạy được

**Ngày viết:** 2026-08-21
**Phạm vi:** Đây là lớp "thực hiện hóa" (implementation) đứng trên doc 23 (roadmap sản phẩm) và doc 24 (POMDP/belief-state). Doc 23 trả lời "xây gì, theo thứ tự nào"; doc này trả lời **"xây bằng thuật toán gì, schema gì, code gì"** — với code Kotlin thật đã được thêm vào repo, không chỉ pseudocode.

> **Trạng thái build (2026-08-22): ĐÃ XÁC MINH.** Toàn bộ code đã compile sạch và **74/74 unit test PASS** với JDK 17 (`/opt/homebrew/opt/openjdk@17`). Lệnh: `./gradlew testDebugUnitTest`.
>
> Hai lỗi thật đã được phát hiện nhờ lần build đầu tiên, không thể thấy bằng rà soát thủ công:
>
> 1. **Bẫy nested comment của Kotlin** — chuỗi `logevent/*` viết trong KDoc mở một comment **lồng nhau** (Kotlin cho phép nest, khác Java), khiến `*/` kế tiếp đóng nhầm comment con và cả file `DecisionModels.kt` không parse được. Một dòng chú thích làm hỏng 5 class và sinh ra 30 lỗi "Unresolved reference" ở các file khác.
> 2. **`internal` không vượt ranh giới module** — app demo không truy cập được `DecisionEngine.bandit`/`.guardrails`. Đã sửa đúng hướng: thêm API công khai có chủ đích (`applyPolicy()`, `observationCount()`, `effectiveInterstitialCap()`) thay vì nới `internal` thành `public` và để lộ cả nội bộ.

---

## A. Code đã thêm vào repo

Package mới **`com.app.plugin.brain`**, đặt cạnh `ads`, `app`, `firbase`, `logevent` theo đúng convention hiện có của repo. Toàn bộ **additive** — không sửa bất kỳ file production nào đang chạy (`AdsPlacementConfig.kt`, `AdsHelper.kt`, `FirHelper.kt`... giữ nguyên).

| File | Vai trò | Ánh xạ tới doc nghiên cứu |
|---|---|---|
| [PlayerState.kt](../src/main/java/com/app/plugin/brain/PlayerState.kt) | `S_t` — observable state + placeholder latent | doc 04, doc 24 mục I |
| [BeliefState.kt](../src/main/java/com/app/plugin/brain/BeliefState.kt) | Posterior rời rạc trên latent player type (POMDP tối giản) | doc 24 mục B, L |
| [DecisionModels.kt](../src/main/java/com/app/plugin/brain/DecisionModels.kt) | `DecisionPoint`, `ActionMode`, `ActionCandidate`, `DecisionRequest/Response` | doc 06 mục 2, 8–10 |
| [GuardrailEngine.kt](../src/main/java/com/app/plugin/brain/GuardrailEngine.kt) | Lọc candidate vi phạm hard constraint | doc 06 mục 13, doc 14 ETH_* |
| [BanditPolicy.kt](../src/main/java/com/app/plugin/brain/BanditPolicy.kt) | Thompson Sampling Beta-Bernoulli, segment-keyed | doc 05 mục 4–6, doc 24 mục G |
| [DecisionLogger.kt](../src/main/java/com/app/plugin/brain/DecisionLogger.kt) | Decision log, `chosen_action_probability` | doc 05 mục 10, doc 06 mục 8, doc 24 mục K |
| [DecisionEngine.kt](../src/main/java/com/app/plugin/brain/DecisionEngine.kt) | Facade `decide()`/`feedback()` — entrypoint duy nhất | doc 06 mục 9–10 |

## B. Vì sao chọn đúng những lựa chọn kỹ thuật này (không phải lựa chọn khác)

### B.1 Thompson Sampling Beta-Bernoulli, không phải UCB hay epsilon-greedy

Ba lý do, đối chiếu trực tiếp với nghiên cứu:

1. **Tự nhiên hóa exploration/exploitation mà không cần tham số ε thủ công** — khác epsilon-greedy (doc 05 mục 5, Firebase codelab dùng ε=30% cố định — không tối ưu, không tự thích nghi).
2. **Posterior Beta cho phép đọc trực tiếp `chosen_action_probability`** qua Monte Carlo (đếm tần suất arm thắng trong N lần vẽ mẫu) — đúng field bắt buộc cho OPE (doc 24 mục K). UCB không có diễn giải xác suất tự nhiên để log field này.
3. **Khớp với "model complexity router" (doc 24 mục G)**: Beta-Bernoulli là điểm khởi đầu đúng độ phức tạp cho "title mới/traffic thấp → title đang tăng trưởng". Nâng cấp lên linear contextual bandit (Vowpal Wabbit-style, doc 05 mục 4) chỉ nên làm khi mỗi segment đã có đủ traffic để ước lượng ổn định — không nhảy thẳng lên vì "nghe cao cấp hơn".

### B.2 Segment-keyed, không phải full contextual regression ngay từ đầu

`segmentKey = "${decisionPoint}_${beliefState.mostLikelyType()}"` — đây là contextual ở mức **rời rạc hóa context thành nhóm** (discretized context), không phải bandit học trực tiếp trên vector feature liên tục. Đây là quyết định có chủ đích: bài học starter-pack bandit (doc 24 mục H) cho thấy context nông (country/device) không đủ tín hiệu, nhưng đồng thời full contextual regression cần nhiều dữ liệu hơn segment-keyed MAB mới hội tụ ổn định. `BeliefState.mostLikelyType()` là điểm cân bằng: giàu tín hiệu hơn metadata tĩnh, nhưng vẫn đủ rời rạc để MAB đơn giản hoạt động tốt với ít dữ liệu.

### B.3 Belief state rời rạc 4 loại, không phải particle filter liên tục

POMDP đầy đủ (doc 24 mục B, paper Tencent/HKU) dùng belief liên tục qua Monte Carlo planning offline phức tạp. `BeliefState` ở đây cố tình đơn giản hóa thành **4 loại latent rời rạc** với cập nhật Bayes rời rạc (`update()`), vì:
- Đủ để phân biệt 4 chiến lược monetization khác nhau về chất (doc 24 mục B, bảng ví dụ 5 user A-E ở doc gốc rút gọn còn 4 nhóm hành động khác biệt rõ).
- Cập nhật O(1) theo số loại, chạy được on-device không cần Monte Carlo planning nặng.
- Đường nâng cấp rõ ràng: khi có đủ dữ liệu, thay `LatentPlayerType` bằng embedding liên tục và `update()` bằng particle filter — **không cần đổi API của `DecisionEngine`**, chỉ đổi implementation nội bộ của `BeliefState`.

### B.4 Guardrail tách khỏi bandit — nhưng phải phân biệt HAI LOẠI

`GuardrailEngine.evaluate()` trả `null` hoặc `GuardrailViolation` — không trả về một số trừ vào `rawScore`. Với **ràng buộc đạo đức/chính sách**, đây là quyết định đúng và không thương lượng: nếu guardrail chỉ "trừ điểm", một action vi phạm nghiêm trọng (ví dụ `ETH_MANUFACTURED_FAIL`) vẫn có thể thắng nếu điểm bandit đủ cao — penalty luôn có thể bị "mua" bằng đủ doanh thu.

> **Đính chính (2026-08-21, sau doc 28 mục E):** bản đầu của mục này khẳng định hard-filter là đúng cho **mọi** guardrail. Điều đó **sai** với nhóm ràng buộc tài nguyên. Lý thuyết Bandits-with-Knapsacks (Badanidiyuru–Kleinberg–Slivkins) chỉ ra: khi ràng buộc là *ngân sách* (ad-load/session, fatigue/ngày, rủi ro churn), lời giải tối ưu là một **phân phối trộn** trên các arm — ví dụ "70% session rewarded nhẹ, 20% interstitial, 10% im lặng" — chứ không phải "luôn chọn arm tốt nhất rồi chặn khi chạm trần". Lọc cứng nhóm này tạo **bias hệ thống**: bandit học từ log mà arm tốt nhất bị chặn không đều.

**Đã sửa trong code:** `GuardrailKind` phân loại `HARD` (tuyệt đối — click-fraud, đạo đức) và `RESOURCE` (ngân sách — ad pressure, payer protection, churn budget). Cơ chế đúng cho nhóm `RESOURCE` là Lagrangian shadow price:

```text
r_tilde(a) = r(a) - Σ_j lambda_j * c_j(a)
lambda_j ← lambda_j * exp( eta * (c_j_observed - B_j/T) )
```

`lambda_j` chính là "giá bóng" của guardrail j — khi ad-load vượt ngân sách, mọi arm quảng cáo bị phạt nặng dần một cách trơn tru thay vì giật cục, và dashboard `lambda_j` theo thời gian là công cụ chẩn đoán rất tốt cho Control Plane (doc 26).

**Nợ kỹ thuật đã biết, chấp nhận có chủ đích:** nhóm `RESOURCE` hiện *vẫn* chạy như hard filter. Điều này an toàn ở Phase 0–1 vì policy còn là rule-based — chưa học từ log thì bias chưa phát sinh. **Phải chuyển sang Lagrangian TRƯỚC khi bật bandit learning trên traffic thật.**

### B.5 Decision log phải ghi tập action đủ điều kiện

Hệ quả trực tiếp của B.4: `chosen_action_probability` có điều kiện trên tập candidate **sau** guardrail, không phải tập ban đầu. Nếu log không ghi tập này, mọi importance weight trong OPE đều sai. `DecisionResponse` vì vậy có thêm `eligibleActionIds` — không có nó thì log không tái lập được quyết định.

`GuardrailEngine` cũng đếm số lần mỗi guardrail **ngăn chặn thành công** (`preventedCount`) — đây chính là đầu vào cho thành phần `guardrail_violation_rate_prevented` của `ComfortIndex` (doc 26 mục C.4), trước đây mới chỉ có trong công thức mà chưa có nguồn dữ liệu.

## C. Reward signal: chưa implement, đây là quyết định có chủ đích

`DecisionEngine.feedback()` hiện nhận thẳng `outcomeReward01: Double` — một số đã được tính sẵn bởi caller. **Chưa implement công thức reward đầy đủ của doc 11**:

```text
R(decision) = r_immediate + γ·[LTV(s') − LTV(s)] − λ_churn·P(churn|action)
              − λ_fair·FairnessViolation + bonus_info·ΔUncertaintyReduction
```

Lý do không code cứng công thức này vào `DecisionEngine`: nó cần `LTV(s')`, `P(churn|action)` — các model dự đoán chưa tồn tại (cần feature store + training pipeline, mục E bên dưới). Đặt trách nhiệm tính `outcomeReward01` ở lớp caller là đúng ranh giới — `DecisionEngine` không nên tự ý giả định cách tính reward khi các thành phần đầu vào chưa sẵn sàng. Khi có LTV model, viết một `RewardModel` riêng implement công thức trên và gọi trước khi truyền vào `feedback()`.

## D. Ví dụ minh họa tích hợp (KHÔNG phải patch trực tiếp)

Đây là ví dụ cách một call site trong tương lai sẽ trông như thế nào khi nối `DecisionEngine` vào luồng interstitial hiện có — viết ở đây để minh họa, **cố tình không apply trực tiếp vào `AdsPlacementConfigManager.kt` hay `AdsPlacementConfig.kt`** vì đó là code production đang chạy thật, cần review/test riêng trước khi đổi hành vi:

```kotlin
// Ví dụ minh họa — KHÔNG phải patch đã áp dụng
fun maybeShowInterstitial(playerState: PlayerState, plConfig: AdsPlacementConfig) {
    val request = DecisionRequest(
        playerId = playerState.playerId,
        decisionPoint = DecisionPoint.INTERSTITIAL_OPPORTUNITY
    )
    val candidates = listOf(
        ActionCandidate(ActionMode.NO_ACTION, "no_action"),
        ActionCandidate(ActionMode.INTERSTITIAL, "interstitial_default")
    )
    val decision = DecisionEngine.instance.decide(
        request, playerState, candidates,
        adsShownThisSession = plConfig.countshow,
        minutesSinceLastInterstitial = minutesSince(plConfig.timeShow)
    )
    if (decision.chosenAction.mode == ActionMode.INTERSTITIAL) {
        // gọi luồng show ad hiện có của AdsHelper — không đổi cách show, chỉ đổi "có nên show không"
    }
}
```

Đây chính là Phase 3 của roadmap doc 23 (cắm vào `AdsPlacementConfig.flagShow/maxShow/delTime` sẵn có) — nhưng **chỉ nên làm sau Phase 0–1** (payment routing + decision log nền tảng), theo đúng thứ tự ưu tiên kinh tế US/EU đã lập luận ở doc 23 mục B–D.

## E. Phần chưa có trong repo Android này: backend cần thiết

`DecisionEngine` hiện chạy hoàn toàn **in-memory, on-device, không persistent** — `BanditPolicy.arms` và `beliefStates` mất khi app restart. Đây là giới hạn cố ý của bản skeleton, nhưng để thành hệ thống thật cần một **companion service** ngoài phạm vi repo Android này:

```text
ANDROID SDK (repo này)                    BACKEND (chưa tồn tại, cần build riêng)
─────────────────────                     ──────────────────────────────────────
DecisionEngine (on-device)      ←policy artifact── Training pipeline
  - BanditPolicy (posterior             (batch, định kỳ, học lại
    cục bộ, khởi tạo từ artifact)         từ decision log toàn cục)
  - GuardrailEngine (rule cục bộ) ←config── Guardrail config service
  - DecisionLogger              ──event──→ Feature store + decision log
                                            (server-side, tổng hợp mọi user)
```

**Nguyên tắc phân tầng (doc 16 mục D)**: on-device chỉ giữ posterior/rule **cục bộ, đã được huấn luyện sẵn**; backend chịu trách nhiệm tổng hợp log toàn cục, huấn luyện lại theo batch, và đẩy "policy artifact" mới xuống — đúng pattern Adobe Target on-device decisioning đã dẫn ở doc 16 mục D.1. `FirHelper.fetchRemoteCf()` đã có sẵn cơ chế fetch định kỳ — đây chính là kênh vận chuyển artifact tự nhiên, chỉ cần mở rộng schema JSON mà `AdsPlacementConfigManager.parserConfig()` đang đọc để chứa thêm tham số Beta (alpha/beta) theo segment.

Backend tối thiểu cần 3 thành phần (chưa build, liệt kê để rõ phạm vi còn lại):
1. **Decision log ingestion** — nhận batch từ `DecisionLogger` forwarder, lưu theo schema doc 06 mục 8.
2. **Feature store** — tổng hợp `PlayerState` đầy đủ từ event stream (doc 04), phục vụ cả training lẫn debug.
3. **Training/policy service** — định kỳ tính lại tham số Beta per-segment từ decision log, xuất policy artifact JSON, đẩy qua Remote Config/Firestore (kênh đã có sẵn trong `FirHelper`).

## F. Thuật toán tham chiếu cho các bước tiếp theo (chưa code, có công thức)

### F.1 CATE/Uplift (khi đủ dữ liệu experiment ngẫu nhiên hóa — doc 05 mục 3, doc 11)

```text
CATE(x) = E[Y(1) − Y(0) | X=x]
```

Ước lượng bằng T-learner (2 model riêng cho treatment/control) hoặc dùng trực tiếp CausalML/EconML (đã liệt kê ở doc 09/docx phần 08) — **không tự viết từ đầu**, hai thư viện này là open-source production-grade, tích hợp qua backend Python, không phải trong Android SDK.

### F.2 Belief update likelihood (mở rộng `BeliefState.update()`)

Hiện `update()` nhận `likelihood: Map<LatentPlayerType, Double>` do caller tính sẵn. Công thức gợi ý cho một quan sát nhị phân đơn giản (ví dụ "vừa accept rewarded ad"):

```text
P(accept_rewarded=1 | HIGH_SKILL_HIGH_TOLERANCE) = 0.7   (giả định prior từ domain knowledge)
P(accept_rewarded=1 | HIGH_SKILL_LOW_TOLERANCE)  = 0.2
P(accept_rewarded=1 | LOW_SKILL_HIGH_CHURN_RISK) = 0.4
P(accept_rewarded=1 | CASUAL_STEADY)             = 0.5
```

Các hằng số này **phải học từ dữ liệu thật** (tần suất accept quan sát theo từng cụm hành vi đã biết nhãn/cluster), không nên giữ nguyên giá trị giả định minh họa này trong production.

### F.3 Reward contract đầy đủ (doc 11) — implement khi có LTV model

```text
R(decision) = r_immediate + γ·[LTV(s') − LTV(s)] − λ_churn·P(churn|action)
              − λ_fair·FairnessViolation + bonus_info·ΔUncertaintyReduction
```

`LTV(s)` nên bắt đầu bằng pLTV đơn giản (ZILN — doc 11) trước khi thử CALTV/survival ensemble phức tạp hơn — lại một lần nữa áp dụng nguyên tắc "khớp độ phức tạp với độ trưởng thành dữ liệu" (doc 24 mục G).

## G. Việc tiếp theo theo thứ tự thực tế

```text
[✓ ĐÃ XONG] Cơ chế segment + personalization chạy được:
   - LikelihoodEstimator (mắt xích làm belief chuyển động — trước đó BeliefState.update()
     không ai gọi nên posterior vĩnh viễn đứng ở prior 0.25, hệ thống suy biến thành
     một bandit toàn cục không cá nhân hoá)
   - PersonalizationScenario (4 kịch bản, trần chỉ được siết chặt hơn — min(), không ghi đè)
   - BrainStorage (persist belief + thống kê bandit qua PreferenceHelper)
   - BrainInspector + BrainDebugView + BrainPlaygroundActivity (quan sát trực tiếp khi test)

[✓ ĐÃ XONG] Unit test: 61 case trong src/test/java/com/app/plugin/brain/
   BeliefStateTest, BanditPolicyTest, GuardrailEngineTest, LikelihoodAndScenarioTest,
   DecisionEngineTest. Hạ tầng test: junit 4.13.2 + testOptions.unitTests.isReturnDefaultValues.

1. CHẠY `./gradlew test` và `./gradlew assembleDebug` — vẫn CHƯA làm được trong môi trường
   viết tài liệu (không có Java Runtime). Đây là rào cản số một còn lại.
2. Thiết kế schema JSON cho policy artifact (mở rộng AdsPlacementConfigManager.parserConfig
   để đọc thêm alpha/beta per-segment) — cần quyết định format cùng người maintain
   FirHelper/Remote Config hiện tại
3. Spec backend tối thiểu (mục E) — ngoài phạm vi Android repo, cần quyết định stack riêng
4. Calibrate hằng số hồ sơ trong LikelihoodEstimator bằng dữ liệu thật (hiện là giá trị giả
   định từ domain knowledge) — phương pháp ở doc 33; kiểm chứng OPE theo kế hoạch ở doc 34
5. Chỉ sau đó mới nối DecisionEngine vào luồng interstitial thật (mục D) — có A/B/holdout
   trước khi rollout 100%, đúng nguyên tắc tách experimentation khỏi personalization (doc 05 mục 10)
6. Chuyển guardrail nhóm RESOURCE sang Lagrangian TRƯỚC khi bật bandit learning trên
   traffic thật (nợ kỹ thuật đã ghi ở mục B.4)
```

## Không xác minh được (ghi rõ)

1. Code Kotlin trong package `brain/` chưa được compiler xác nhận (không có JRE trong môi trường viết tài liệu) — cần build thử trước khi tin cậy.
2. Hằng số likelihood minh họa ở mục F.2 là giá trị giả định cho mục đích minh họa công thức, không phải số đã học từ dữ liệu thật.
3. Hiệu năng thực tế của Marsaglia-Tsang Gamma sampler viết tay (thay vì dùng thư viện) chưa được benchmark trên thiết bị Android thật.

## Nguồn chính

Đây là tài liệu tổng hợp kỹ thuật, không có nguồn ngoài mới — toàn bộ thuật toán/kiến trúc tham chiếu trực tiếp docs 04–06, 11, 14, 16, 23, 24 đã có trong KB.
