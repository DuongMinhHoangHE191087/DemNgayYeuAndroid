# 24 — POMDP & Belief-State Player Model: từ propensity đến trạng thái ẩn

**Ngày nghiên cứu:** 2026-08-21
**Nguồn gốc:** Tích hợp từ `Monetization_Brain_Player_Decision_Platform_v2.docx` (phần 10 — "Research Expansion v2"), một dossier riêng xuất hiện trong quá trình làm việc, chưa từng nằm trong docs 01–23. Đây là nội dung **nâng cấp trực tiếp tư duy causal/bandit của doc 05** lên một tầng biểu diễn sâu hơn: player không chỉ có state quan sát được, mà có **trạng thái ẩn (latent/belief state)** cần suy luận.

## Evidence labels

- **[Primary research — production RCT]**: kết quả từ thử nghiệm ngẫu nhiên hóa đã deploy thật.
- **[Primary research — peer-reviewed]**: paper học thuật đã review.
- **[Primary docs — patent]**: bằng sáng chế, là bằng chứng về kiến trúc/ý định, không phải bằng chứng capability đã triển khai đúng y hệt.
- **[Primary docs — vendor]**: tài liệu kỹ thuật vendor.

---

## A. Định nghĩa hệ thống nâng cấp

Thay vì chỉ "Monetization Optimizer", định nghĩa rộng hơn:

> **Adaptive Player Decision System** — một hệ quyết định tuần tự, có ràng buộc, ước lượng trạng thái ẩn của player và chọn can thiệp (gameplay, IAA, IAP, free, hoặc không hành động) để tối đa incremental long-term value, chịu ràng buộc retention/churn/economy/UX/policy.

```text
PLAYER HISTORY → OBSERVABLE STATE → LATENT/BELIEF STATE → OPPORTUNITY DETECTOR
    → CANDIDATE ACTIONS → CAUSAL/SEQUENTIAL POLICY → SAFE EXPERIENCE CONFIG
    → RESPONSE → BELIEF + POLICY UPDATE
```

Thay đổi tư duy cốt lõi: không chỉ hỏi "treatment nào convert?", mà hỏi **"chuỗi can thiệp nào thay đổi hành vi tương lai theo hướng có giá trị?"**

## B. Bằng chứng production mạnh nhất trong toàn bộ KB: Tencent/HKU POMDP

AAMAS 2026 có extended abstract *"The Monetization Agent: A Deployed POMDP for Maximizing Lifetime In-App Advertising Revenue"* — mô hình hóa **dynamic difficulty adjustment** như một **POMDP** (Partially Observable Markov Decision Process). [Primary research — production RCT]

Ý tưởng production quan trọng:
- **Hidden player type** nắm bắt skill và ad-tolerance không đồng nhất giữa các user.
- **Observable state** gồm tiến trình game (level hiện tại, số lần thử).
- Agent quan sát thắng/thua, quyết định xem rewarded ad, và chuyển trạng thái churn.
- **Immediate reward** là chỉ báo có xem ad hay không, nhưng **objective thật** là tổng lifetime rewarded-ad impression.
- Một mô hình hành vi kinh tế lượng cấu trúc (structural econometric) liên kết difficulty với xác suất thắng, xem ad, và churn.
- **Planning offline bằng Monte Carlo**; runtime chỉ cần belief update nhanh + tra bảng policy nén — đủ nhỏ để **deploy on-device**.

**Kết quả production đã randomize** (không phải self-reported case study thông thường — đây là RCT thật):

| Experiment | Lift lifetime ad-impression | Kết quả retention |
|---|---|---|
| Game A | **+23.6%** | Retention giữa-cuối vòng đời cải thiện; tỷ lệ sống sót đến level 300 báo cáo tăng tới +204% |
| Game B | **+7.0%** | Tương tự; sống sót đến level 300 tăng tới +297% |

Đây là bằng chứng mạnh nhất trong toàn bộ knowledge base tính đến thời điểm này, vì là kết quả RCT production thật, không phải case study tự công bố thông thường.

### Vì sao cần belief state

Player không thể chỉ biểu diễn bằng quan sát trực tiếp (`fail_count`, `ads_watched`). Các biến có giá trị nhất thường **ẩn**:

```text
true_skill  ad_tolerance  friction_tolerance  churn_vulnerability
purchase_intent  willingness_to_pay
```

Cần một belief state:

```text
b_t(z) = P(z | H_t)
```

trong đó `z` là latent player type, `H_t` là toàn bộ lịch sử tương tác đến thời điểm `t`. Hai user có snapshot giống hệt nhau vẫn có thể có posterior belief khác nhau vì trajectory khác nhau — đây là cơ sở cho một **Player Belief Engine** riêng trong kiến trúc.

## C. Gameplay personalization là một monetization action thật sự

Paper peer-reviewed 2025 *"Personalized content, engagement, and monetization in a mobile puzzle game"* dùng structural behavioral model, báo cáo: difficulty trung bình vốn đã gần mức tối đa doanh thu theo model của nhà phát triển, nhưng **personalize theo đặc điểm quan sát được của player có thể tăng doanh thu mô hình hóa 71%**. [Primary research — peer-reviewed]

**Lưu ý evidence quan trọng**: con số 71% là kết quả **structural counterfactual/model**, không phải uplift A/B sống đã đo trên production — phải xử lý khác với kết quả RCT ở mục B.

Difficulty tạo ra friction:

```text
TOO EASY → urgency thấp → nhu cầu resource/booster yếu
FRICTION TỐI ƯU → thử thách + engagement → giá trị rescue/booster có ý nghĩa
TOO HARD → frustration → thoát session → churn
```

Player model nên ước lượng thêm: `friction_tolerance`, `frustration_state`, `reward_sensitivity`, `skill`, `difficulty_response`. Một candidate action có thể là **cấu hình gameplay**, không chỉ ad hay paywall — đúng như action `GAMEPLAY_CONFIG` đã có trong action space gốc (doc 01).

## D. Path dependence: ads và offer cần trí nhớ

Một monetization action có hiệu ứng khác nhau tùy exposure trước đó. Ad thứ 5 trong session không tương đương ad đầu tiên.

```text
ads_session ads_24h ads_7d last_1_ad_outcome last_3_ad_outcomes
rewarded_accept_rate_recent rewarded_accept_rate_baseline
post_ad_exit_rate time_since_last_ad ad_fatigue_score

offers_session offers_24h offers_7d discounts_30d offer_dwell_recent
checkout_abandon_rate purchase_after_discount_ratio full_price_conversion
reference_price_state days_since_last_purchase
```

### Strategic user learning & reference-price effect

Chính sách monetization lặp lại có thể **dạy user cách hệ thống vận hành**:

```text
Offer full-price bị từ chối → 48 giờ sau → giảm giá 30%
```

Nếu lặp lại pattern này, user học được cách chờ giảm giá một cách hợp lý. Conversion ngắn hạn với discount có thể tăng trong khi expected value toàn phần giảm. **Hệ quả thiết kế**: theo dõi **incremental margin**, không chỉ conversion lift.

```text
Không giảm giá:  P(mua $9.99) = 0.35 → EV = $3.50
Giảm giá 20%:    P(mua $7.99) = 0.42 → EV = $3.36
```

Conversion tăng nhưng expected revenue tức thời giảm — chưa kể hiệu ứng reference-price dài hạn.

## E. Playtika — bằng sáng chế Experience Control Plane (mới, chưa có trong doc 03/12)

Hai đơn bằng sáng chế Playtika không nằm trong bất kỳ doc nào trước đây:

- **US20260131248** — *"Systems and Methods for Varying Gaming Experience Using Group Segmentation"*: nền tảng remote truy cập dữ liệu session lịch sử, phân nhóm player (reactivated/dormant/paying/non-paying/theo level), áp đặt trải nghiệm tạm thời khác nhau theo nhóm, hỗ trợ re-evaluate assignment từ lịch sử hành vi. [Primary docs — patent]
- **US20260131240** — *"Systems and Methods for Conflict Detection"*: phát hiện xung đột giữa các lớp patch trải nghiệm có giới hạn thời gian chạy đồng thời. [Primary docs — patent]

Tổng hợp kiến trúc gợi ý:

```text
BASE GAME + gameplay patch + monetization patch + retention patch
    + liveops patch + experiment patch
    → conflict resolver → final experience configuration
```

**Lưu ý evidence**: bằng sáng chế là bằng chứng về kiến trúc/ý định đã đăng ký, không phải bằng chứng mọi capability mô tả đã được triển khai đúng y hệt trong production.

## F. Braze/OfferFit — chia nhỏ action space khổng lồ ("community of bandits")

BrazeAI Decisioning Studio (tích hợp công nghệ OfferFit) mô tả cách tiếp cận **community of bandits**: thay vì một bandit khổng lồ trên tích Cartesian của mọi lựa chọn, action space được **phân tầng theo dimension**. [Primary docs — vendor]

Ví dụ lifecycle marketing: `CHANNEL → OFFER → TIME` — output của quyết định trước trở thành context cho dimension tiếp theo. Braze mô tả nhiều độ phức tạp model: multi-armed, elastic-net contextual, gradient-boosting bandit, cộng thêm meta-bandit chọn giữa chúng.

**Tương đương cho game monetization** — đây chính là factorized policy nên dùng thay vì bandit đơn khối:

```text
SHOULD_ACT? → MODE (FREE/IAA/IAP/GAMEPLAY) → FORMAT/OFFER FAMILY
    → VALUE/PRICE/REWARD → PLACEMENT → CREATIVE → TIMING/FREQUENCY
```

Cách này giảm sample complexity đáng kể so với coi mỗi tổ hợp là một arm độc lập — mấu chốt để tránh cold-start problem khi action space lớn.

## G. Độ phức tạp model nên khớp độ trưởng thành dữ liệu

| Giai đoạn sản phẩm/dữ liệu | Độ phức tạp policy khuyến nghị |
|---|---|
| Title mới / traffic thấp | Rules + Bayesian priors / MAB đơn giản |
| Title đang tăng trưởng | Contextual bandit linear/elastic-net |
| Title trưởng thành | Tree/GBM contextual policy + causal model |
| Title trưởng thành, volume cao | Uplift bandit / belief-state policy / POMDP |
| Scale rất lớn, experimentation vững | Sequential policy / constrained RL |

Nguyên tắc trung tâm: **khớp độ linh hoạt của model với khối lượng bằng chứng và rủi ro**. Model phức tạp hơn có thể overfit trên context player thưa dữ liệu.

## H. Bài học starter-pack bandit: metadata không đủ

Paper IEEE CoG 2024 *"Exploratory Bandit Experiments with Starter Packs in a Free-to-Play Mobile Game"* kiểm thử bandit personalization online cho offer starter-pack, xử lý cold-start bằng business-expert prior. [Primary research — peer-reviewed]

Bài học chính: **context nông (country, device) không cung cấp đủ tín hiệu cá nhân hóa**. Đây là bằng chứng ủng hộ ưu tiên trajectory hành vi + temporal signal (mục D ở trên) hơn segmentation theo metadata tĩnh (đúng luận điểm cốt lõi ở doc 01 mục 2).

## I. Mô hình player state 5 lớp (nâng cấp doc 04)

| Lớp | Câu hỏi | Ví dụ |
|---|---|---|
| Observable state | User đang ở đâu? | level, balance, session length |
| Temporal dynamics | User đang thay đổi thế nào? | fail acceleration, currency burn velocity |
| Latent traits | Loại ổn định/chậm thay đổi là gì? | skill, patience, ad tolerance |
| Intent state | Đang có ý định gì? | buy readiness, quit risk, rescue need |
| Causal response | Can thiệp sẽ thay đổi họ thế nào? | IAP uplift, ad uplift, churn effect |

Candidate latent trait cần ước lượng: `skill, patience, friction_tolerance, reward_sensitivity, ad_tolerance, ad_fatigue, price_sensitivity, discount_conditioning, purchase_intent, churn_vulnerability, power_affinity, convenience_affinity, cosmetic_affinity, novelty_seeking, strategic_waiting_behavior`.

## J. Slate optimization cho store nhiều offer

Nếu store hiển thị nhiều offer cùng lúc, action không phải một sản phẩm mà là **một slate có thứ tự**:

```text
a = [Offer_1, Offer_2, ..., Offer_K]
```

Cần mô hình hóa thêm: position bias, attention probability, substitution, complementarity, diversity, fatigue từ sản phẩm lặp lại.

```text
candidate generation → eligibility/guardrail filter → offer scoring
    → slate optimization → presentation decision
```

## K. OPE là yêu cầu dữ liệu ngày-một (day-one), không phải sau này

*Open Bandit Dataset and Pipeline* hình thức hóa off-policy evaluation: ước lượng hiệu năng của một policy giả định từ log do policy khác sinh ra. [Primary research — peer-reviewed]

Mọi quyết định production nên log tối thiểu:

```text
decision_id  player_id  timestamp  policy_version
context/feature_snapshot  candidate_actions  selected_action
propensity/probability_of_selected_action
guardrails_applied  exposure  immediate_reward  future_outcomes
```

`propensity`/`chosen_action_probability` đặc biệt quan trọng cho các estimator như IPS và doubly robust. **Hệ quả**: decision logging phải được thiết kế trước khi có ML tinh vi — ngay cả khi policy đầu tiên chỉ là rule-based. (Điều này khớp hoàn toàn với `decision_log` schema đã có ở doc 06 mục 8 — không mâu thuẫn, chỉ củng cố thêm mức độ ưu tiên.)

## L. Kiến trúc tham chiếu cập nhật

```text
PLAYER
  ↓
EVENT STREAM
  ↓
PLAYER MEMORY (full behavioral trajectory)
  ↓
  ├─ LONG TERM ─┬─ SHORT TERM ─┬─ SEQUENCE
  ↓
STATE ENCODER
  ↓
BELIEF ENGINE (latent player-state posterior)
  ↓
  ├─ SKILL ─┬─ AD/IAP TOLERANCE ─┬─ CHURN/INTENT
  ↓
OPPORTUNITY DETECTOR
  ↓
SHOULD ACT? ── NO → NOTHING
  │
  YES
  ↓
ACTION ROUTER
  ↓
  ├─ GAMEPLAY (difficulty, reward/economy)
  ├─ IAA (rewarded, interstitial)
  ├─ IAP (offer/bundle, value/price)
  ↓
CAUSAL VALUE MODEL (incremental LTV effects)
  ↓
FACTORIZED POLICY
  ↓
GUARDRAILS
  ↓
EXPERIENCE CONFIG
  ↓
RESPONSE
  ↓
UPDATE BELIEF + POLICY
```

### Tách Experience Control Plane khỏi Player Decision Brain

| Player Decision Brain | Experience Control Plane |
|---|---|
| Suy luận state | Remote configuration |
| Propensity/timing | Policy versioning |
| Ước lượng causal response | Rollout & kill switch |
| Ranking action | Gán experiment |
| Sequential planning | Patch overlay, conflict detection |
| — | Shadow policy, audit log |

Tách biệt này giúp hệ thống an toàn hơn khi vận hành và cho phép con người override.

## M. Bảy lõi trí tuệ của platform (kết luận)

1. Player Memory
2. State Encoder và Belief Model
3. Causal Response Models
4. Opportunity Detection
5. Factorized Action Policy
6. Experience Control Plane
7. Experimentation và Off-Policy Evaluation

Nguyên tắc sản phẩm trung tâm không đổi: **`NO_ACTION` luôn phải là candidate hợp lệ.** Hệ thống phải học không chỉ trải nghiệm monetization nào nên hiện, mà cả **khi nào nên im lặng để bảo vệ giá trị tương lai**.

## Không xác minh được (ghi rõ)

1. Chi tiết đầy đủ của paper AAMAS 2026 Tencent/HKU (chỉ có extended abstract công khai tại thời điểm viết; bản full paper tham chiếu qua SSRN chưa được đọc trực tiếp).
2. Mức độ triển khai thực tế của 2 bằng sáng chế Playtika — patent chỉ xác nhận đã đăng ký, không xác nhận đã chạy production đúng y hệt mô tả.
3. Độ chính xác của con số 71% (paper puzzle difficulty) khi áp dụng ngoài bối cảnh structural model của chính nghiên cứu đó.

## Nguồn chính

- Tencent/HKU Monetization Agent (AAMAS 2026 extended abstract) — https://www.ifaamas.org/Proceedings/aamas2026/pdfs/WOQF1299.pdf
- Full paper reference (SSRN) — https://ssrn.com/abstract=6137709
- Personalized mobile puzzle difficulty (ScienceDirect) — https://www.sciencedirect.com/science/article/pii/S0167718724000833 (DOI: https://doi.org/10.1016/j.ijindorg.2024.103128)
- Playtika patent US20260131248 — https://patents.justia.com/patent/20260131248
- Playtika patent US20260131240 — https://patents.justia.com/patent/20260131240
- Playtika patent index — https://patents.justia.com/assignee/playtika-ltd
- Braze — A Community of Bandits — https://www.braze.com/resources/reports-and-guides/ai-decisioning-community-of-bandits
- Braze — What Is OfferFit? — https://www.braze.com/resources/articles/what-is-offerfit
- Starter-pack bandit research (IEEE Xplore) — https://ieeexplore.ieee.org/document/10645582
- Open Bandit Dataset and Pipeline — https://arxiv.org/abs/2008.07146
