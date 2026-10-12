# 01 — Core Thesis: Personalized Monetization Brain

## 1. Hệ thống đang tìm là gì?

Tên kỹ thuật hợp lý:

**Constrained Sequential Decision System for Player Lifetime Value Optimization**

Nó phải trả lời liên tục:

\[
a_t^* = \arg\max_a E[\Delta LTV \mid S_t, a]
\]

Trong đó:

- `S_t`: trạng thái player tại thời điểm t.
- `a`: một hành động có thể thực hiện.
- `ΔLTV`: giá trị tăng thêm do chính intervention đó tạo ra, không chỉ doanh thu quan sát sau intervention.

Action space tối thiểu:

```text
NO_ACTION
FREE_ACTION
REWARDED_AD
INTERSTITIAL
IAP_OFFER
DISCOUNT
BUNDLE
SUBSCRIPTION
LIVEOPS_ACTION
GAMEPLAY_CONFIG
```

## 2. Vì sao Whale / Dolphin / F2P là chưa đủ?

Static segmentation chỉ là abstraction phục vụ con người. Hai non-payer có thể hoàn toàn khác nhau:

| User | P(IAP) | Ad tolerance | Churn risk | Strategy |
|---|---:|---:|---:|---|
| A | 0.82 | 0.10 | 0.12 | Protect from ads, push IAP |
| B | 0.05 | 0.90 | 0.18 | IAA-heavy |
| C | 0.42 | 0.55 | 0.75 | Reduce pressure |
| D | 0.65 | 0.72 | 0.10 | Hybrid |
| E | 0.15 | 0.15 | 0.80 | Retention first |

Do đó player nên được biểu diễn bằng state vector động:

\[
S_t = [Static, LongTerm, Recent, Sequence, CurrentContext]
\]

và các latent variables:

```text
purchase_propensity
purchase_hazard
pLTV
churn_risk
ad_tolerance
rewarded_affinity
interstitial_exit_risk
price_sensitivity
discount_uplift
power_affinity
cosmetic_affinity
time_saving_affinity
frustration
scarcity
monetization_readiness
organic_purchase_probability
```

## 3. Maturity model

```text
LEVEL 0 — One policy for everybody
LEVEL 1 — Static segmentation
LEVEL 2 — Behavioral segmentation
LEVEL 3 — Propensity models
LEVEL 4 — Timing / survival / purchase hazard
LEVEL 5 — Contextual recommendation / bandit
LEVEL 6 — Causal uplift / incremental decisioning
LEVEL 7 — Sequential policy / reinforcement learning
```

### Level 0
Ví dụ: sau level 3 cứ mỗi 2 level show interstitial.

### Level 1
Country, platform, acquisition source, payer/non-payer.

### Level 2
RFM, engagement, economy state, ad behavior, progression behavior.

### Level 3
Dự đoán `P(purchase)`, `P(churn)`, `P(rewarded_accept)`.

### Level 4
Dự đoán *khi nào* purchase có khả năng xảy ra:

\[
P(T_{next purchase} = d \mid X)
\]

King/Activision Blizzard đã public production ML cho bài toán personalized offer timing, sử dụng long-term + short-term player behavior. [KING_PAPER]

### Level 5
Contextual bandit học action tốt nhất cho context hiện tại. Firebase IAP codelab và Metica Smart Config là các reference rõ. [FIREBASE_IAP] [METICA_SMARTCONFIG]

### Level 6
Không hỏi “ai dễ mua?”, mà hỏi “ai sẽ thay đổi hành vi **vì** intervention?”. Spotify dùng uplift/CATE cho in-app messaging; Dream11 triển khai hierarchical contextual uplift bandits. [SPOTIFY_UPLIFT] [DREAM11_PAPER]

### Level 7
Tối ưu chuỗi action thay vì từng action greedy:

\[
\pi^* = \arg\max_\pi E\left[\sum_{t=0}^{T} \gamma^t R_t\right]
\]

Wildlife Studios đã public use case dùng RL + Ray cho personalized IAP offer recommendation, với mục tiêu sequence of decisions tối đa long-term profit. [WILDLIFE_RL]

## 4. Next Best Monetization Action

Tại một decision point:

```text
PLAYER FAILS BOSS 4 TIMES
          ↓
Current state + trajectory
          ↓
Candidate generator
          ↓
NO_ACTION / FREE / REWARDED / INTERSTITIAL / IAP A / IAP B
          ↓
Expected incremental value per action
          ↓
Guardrails
          ↓
Chosen action
```

Ví dụ minh họa:

| Action | Immediate revenue | Future IAP Δ | Retention Δ | Expected incremental LTV |
|---|---:|---:|---:|---:|
| No action | 0 | baseline | baseline | 0 |
| Rewarded | +0.03 | -0.08 | +0.02 | -0.03 |
| Interstitial | +0.025 | -0.01 | -0.06 | -0.045 |
| $2.99 rescue | +0.11 EV | +0.07 | -0.01 | +0.17 |
| Free rescue | 0 | +0.04 | +0.17 | +0.21 |

Một Monetization Brain mature phải có quyền chọn **không monetize** hoặc **give free value**.

## 5. Objective function

Không nên:

\[
Reward = Revenue_t
\]

Nên gần hơn với:

\[
R_t =
\Delta IAP_t +
\Delta IAA_t +
\alpha \Delta RetentionValue_t +
\beta \Delta EngagementValue_t -
\gamma \Delta ChurnRisk_t -
\delta EconomyDamage_t -
\epsilon ExperienceCost_t
\]

Và toàn hệ thống chịu constraints:

\[
Retention \ge threshold
\]

\[
Churn \le threshold
\]

\[
AdPressure \le cap
\]

\[
EconomyDamage \le cap
\]

\[
PolicyCompliance = true
\]

## 6. Propensity không bằng incrementality

Ví dụ:

```text
P(purchase | no offer) = 0.82
P(purchase | 30% discount) = 0.87
```

Conversion khi discount rất cao, nhưng uplift chỉ:

\[
0.87 - 0.82 = 0.05
\]

Nếu user vốn đã mua full price, discount có thể cannibalize revenue.

Do đó phải ước lượng:

\[
CATE(x)=E[Y(1)-Y(0)\mid X=x]
\]

Spotify đã dùng chính logic này cho eligibility của in-app messages. [SPOTIFY_UPLIFT]

## 7. Bốn causal user types

Cho treatment = `show rescue offer`:

| Type | Nếu không show | Nếu show | Policy |
|---|---|---|---|
| Persuadable | không mua | mua | Target |
| Sure thing | mua | mua | Không cần discount |
| Lost cause | không mua | không mua | Không bother |
| Negative responder | tốt hơn | xấu đi/churn | Protect |

Nhóm `negative responder` đặc biệt quan trọng trong ads.

## 8. Timing là một dimension độc lập

Không chỉ:

```text
WHAT offer?
```

mà:

```text
WHO?
WHEN?
WHERE?
PRICE?
VALUE?
PRESENTATION?
FREQUENCY?
```

King public rõ ba câu hỏi của offer personalization: **where, which items, when**, và nghiên cứu tập trung vào “when”. [KING_PAPER]

Khái niệm nên dùng:

**Monetization Window** = khoảng thời gian có incremental effect cao nhất dựa trên trajectory của player.

Ví dụ signal combination:

```text
fail streak ↑
currency balance ↓
booster inventory ↓
shop visits ↑
session engagement high
days since last purchase ≈ historical purchase cycle
```

## 9. Snapshot không đủ — cần trajectory

Thay vì chỉ:

```text
gem_balance = 53
fail_count = 4
```

phải có:

```text
gem_balance_1h_ago = 340
gem_balance_now = 53
burn_rate sharply up

normal_fail_rate = 8%
fail_rate_last_30m = 57%

shop_visit_velocity_24h = +300%
```

User state thực sự phải đọc biến động, không chỉ snapshot. King cho thấy purchase behavior phụ thuộc cả long-term và short-term behavior. [KING_PAPER]

## 10. Personalization và experimentation phải tách

Spotify 2026 mô tả rõ:

```text
ML / personalization stack
→ serves personalized decisions

Experimentation stack
→ evaluates the personalization system
```

Contextual bandit là **product/personalization system**, không phải thay thế hoàn toàn cho independent A/B testing. [SPOTIFY_STACKS]

## 11. Candidate generation trước ranking

Không để ML search toàn bộ Cartesian product:

\[
A = Mode \times Product \times Price \times Value \times Placement \times Creative \times Timing \times Frequency
\]

Có thể tạo hàng chục nghìn action.

Pattern tốt:

```text
BUSINESS CONSTRAINTS
        ↓
CANDIDATE GENERATOR / CONSTRAINT SOLVER
        ↓
VALID SAFE ACTIONS
        ↓
BANDIT / CAUSAL RANKER
```

Metica public việc kết hợp constraint programming với contextual multi-armed bandits cho Adaptive Bundles; Google OR-Tools CP-SAT là reference kỹ thuật phù hợp. [METICA_BUNDLES] [ORTOOLS]

## 12. Kết luận

Monetization Brain không nên được xây như “một model dự đoán payer”. Nó là một **decision platform**:

```text
DECISION POINT
    ↓
STATE
    ↓
ELIGIBLE ACTIONS
    ↓
VALUE / UPLIFT ESTIMATION
    ↓
POLICY
    ↓
GUARDRAILS
    ↓
DECISION
    ↓
FEEDBACK
    ↓
EXPERIMENT
```

## Nguồn chính

- **[KING_PAPER] Varelas et al. — Development of an End-to-end ML System with Application to IAP** — Primary research paper.  
  https://arxiv.org/abs/2412.12390
- **[FIREBASE_IAP] Firebase Codelab — Realtime on-device IAP optimization** — Primary technical tutorial.  
  https://firebase.google.com/codelabs/iap-optimization
- **[METICA_SMARTCONFIG] Metica — SmartConfigs UI** — Primary vendor docs.  
  https://docs.metica.com/uiguide/smart-config-ui
- **[SPOTIFY_UPLIFT] Spotify Engineering — ML to Target In-App Messaging** — Primary engineering blog.  
  https://engineering.atspotify.com/2023/6/experimenting-with-machine-learning-to-target-in-app-messaging
- **[DREAM11_PAPER] Agrawal et al. — Hierarchical Contextual Uplift Bandits for Catalog Personalization** — Primary research paper.  
  https://arxiv.org/abs/2601.14333
- **[WILDLIFE_RL] Wildlife Studios / Ray Summit — RL for IAP offer recommendations** — Primary conference deck.  
  https://speakerdeck.com/anyscale/using-reinforcement-learning-to-optimize-iap-offer-recommendations-in-mobile-games-emiliano-castro-vinicius-alves-and-felipe-antunes-wildlife-studios
- **[SPOTIFY_STACKS] Spotify Engineering — Separate Tech Stacks for Personalization and Experimentation** — Primary engineering blog.  
  https://engineering.atspotify.com/2026/1/why-we-use-separate-tech-stacks-for-personalization-and-experimentation
- **[METICA_BUNDLES] Metica — Dynamic Adaptive Bundles** — Primary vendor blog.  
  https://metica.com/blog/earn-more-with-dynamic-adaptive-bundles
- **[ORTOOLS] Google OR-Tools — Constraint Optimization** — Primary docs.  
  https://developers.google.com/optimization/cp/
