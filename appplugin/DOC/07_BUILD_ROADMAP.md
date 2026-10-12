# 07 — Build Roadmap: từ Rules tới Monetization Brain

## Nguyên tắc

Không build RL trước khi có event taxonomy đúng, decision log, independent experimentation, safe action space, reliable reward và propensity logging.

## Phase 0 — Measurement foundation

Deliverables:

```text
canonical player ID
event schema/version
offer/ad/purchase event taxonomy
IAA + IAP revenue joins
session/progression/economy events
decision log skeleton
```

Success: reconstruct được monetization journey của từng user.

## Phase 1 — Rule-based Decision Service

Unified API:

```text
DECIDE(player, decision_point, context)
FEEDBACK(decision_id, event)
```

Policy ban đầu = rules.

Mục tiêu: chuẩn hóa integration trước ML.

## Phase 2 — Behavioral segmentation

Cohorts:

```text
High IAP Intent
High Ad Tolerance
Ad-sensitive
Checkout Abandoner
Discount Sensitive
Premium Buyer
Slipping Payer
Mature Nonpayer
High Frustration
Economy Scarcity
```

Benchmark pattern: FOMO / Peak / Good Job. [FOMO_FIREBASE] [PEAK_VOLTD] [GOODJOB_ROLE]

## Phase 3 — Propensity models

```text
P_purchase_24h
P_purchase_7d
P_rewarded_accept
P_interstitial_exit
P_churn_7d
pLTV
```

Propensity là state feature, không phải final decision policy.

## Phase 4 — Timing / survival

\[
P(T_{purchase}=d|X)
\]

và purchase/churn/session-end hazards.

King là reference. [KING_PAPER]

## Phase 5 — Uplift eligibility

Binary treatments:

```text
SHOW_OFFER vs NO_ACTION
SHOW_INTERSTITIAL vs NO_ACTION
SHOW_DISCOUNT vs STANDARD
```

Train CATE từ randomized experiment data.

Tools: CausalML, EconML. [CAUSALML] [ECONML]

## Phase 6 — Contextual bandit

Candidates:

```text
NO_ACTION
REWARDED
OFFER_A
OFFER_B
FREE_RESCUE
```

Reference: Firebase IAP, Metica, Vowpal Wabbit. [FIREBASE_IAP] [METICA_SMARTCONFIG] [VW_CB]

Production requirements:

```text
exploration budget
propensity logging
holdout
minimum support
rollback
```

## Phase 7 — Contextual uplift bandit

\[
\max_a \tau_a(x)
\]

Dùng hierarchy/shrinkage cho sparse context. Dream11 là reference. [DREAM11_PAPER]

## Phase 8 — Sequential policy / RL

Chỉ khi state/reward/OPE/safety đủ mature. Wildlife Studios là reference. [WILDLIFE_RL]

## Metrics framework

Immediate:

```text
ad_revenue
iap_revenue
conversion
ARPDAU
```

Response:

```text
offer CTR
checkout start
ad completion
post-ad exit
session continuation
```

Long-term:

```text
D1/D7/D30 retention
payer retention
repeat purchase
LTV
churn
economy health
```

Incremental:

```text
ΔIAP
ΔIAA
ΔRetention
ΔLTV
```

## Build vs buy

| Layer | Recommendation |
|---|---|
| Ad mediation | Buy/use MAX, AdMob, ADMOST |
| Billing/IAP receipt | RevenueCat/native |
| Paywall renderer | Adapty/Superwall optional |
| Event pipeline | Existing analytics + own warehouse |
| Player feature store | Build |
| Decision API | Build |
| Causal/uplift | Build on CausalML/EconML |
| Contextual bandit | VW/custom |
| Experimentation | Existing or build carefully |
| Bundle constraints | OR-Tools/custom |
| Monetization policy | **Core proprietary — build** |

## First experiment backlog

1. High IAP intent: suppress forced ads vs baseline.
2. High ad tolerance: higher interstitial frequency with guardrails.
3. Fail streak: free rescue vs rewarded vs IAP rescue.
4. Checkout abandoner: standard offer vs value-adjusted offer.
5. Recent payer: ad suppression.
6. High organic purchase probability: no discount vs discount.
7. Rewarded-heavy user: reward size personalization.
8. Mature nonpayer: IAA-first vs hybrid.

## Nguồn

- **[FOMO_FIREBASE] Firebase — FOMO Games case study** — Primary platform case study.  
  https://firebase.google.com/case-studies/fomo-games
- **[PEAK_VOLTD] Peak Games / VoltDB — Real-time segmentation and personalization** — Primary/partner announcement.  
  https://www.prnewswire.com/news-releases/peak-games-selects-voltdb-for-real-time-segmentation-and-personalization-300306017.html
- **[GOODJOB_ROLE] Good Job Games — Data & Monetization Analyst** — Primary company job description.  
  https://job-boards.greenhouse.io/goodjobgames/jobs/7661366003
- **[KING_PAPER] Varelas et al. — Development of an End-to-end ML System with Application to IAP** — Primary research paper.  
  https://arxiv.org/abs/2412.12390
- **[CAUSALML] Uber — CausalML** — Primary source repository.  
  https://github.com/uber/causalml
- **[ECONML] PyWhy / Microsoft Research — EconML** — Primary source repository.  
  https://github.com/py-why/EconML
- **[FIREBASE_IAP] Firebase Codelab — Realtime on-device IAP optimization** — Primary technical tutorial.  
  https://firebase.google.com/codelabs/iap-optimization
- **[METICA_SMARTCONFIG] Metica — SmartConfigs UI** — Primary vendor docs.  
  https://docs.metica.com/uiguide/smart-config-ui
- **[VW_CB] Vowpal Wabbit — Contextual Bandits tutorial** — Primary docs.  
  https://vowpalwabbit.org/tutorials/contextual_bandits.html
- **[DREAM11_PAPER] Agrawal et al. — Hierarchical Contextual Uplift Bandits for Catalog Personalization** — Primary research paper.  
  https://arxiv.org/abs/2601.14333
- **[WILDLIFE_RL] Wildlife Studios / Ray Summit — RL for IAP offer recommendations** — Primary conference deck.  
  https://speakerdeck.com/anyscale/using-reinforcement-learning-to-optimize-iap-offer-recommendations-in-mobile-games-emiliano-castro-vinicius-alves-and-felipe-antunes-wildlife-studios
- **[SPOTIFY_STACKS] Spotify Engineering — Separate Tech Stacks for Personalization and Experimentation** — Primary engineering blog.  
  https://engineering.atspotify.com/2026/1/why-we-use-separate-tech-stacks-for-personalization-and-experimentation
