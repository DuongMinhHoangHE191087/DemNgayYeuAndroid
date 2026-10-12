# 06 — Reference Architecture, Event Taxonomy, Data Schema & API

## 1. End-to-end architecture

```text
PLAYER / APP
    ↓
EVENT SDK
    ↓
EVENT STREAM
    ↓
RAW STORE + REAL-TIME STATE
    ↓
FEATURE STORE
    ↓
PLAYER INTELLIGENCE
    ↓
OPPORTUNITY DETECTOR
    ↓
CANDIDATE GENERATOR
    ↓
SAFETY FILTER
    ↓
VALUE / UPLIFT ESTIMATORS
    ↓
POLICY ENGINE
    ↓
DECISION API
    ↓
IAA / IAP / FREE / NONE
    ↓
OUTCOME
    ↓
FEEDBACK API
    ↓
DECISION + OUTCOME LOG
    ↓
TRAIN / EVALUATE
```

## 2. Decision points

```text
session_start
tutorial_complete
level_start
level_fail
level_fail_streak
level_complete
boss_fail
currency_low
energy_empty
inventory_low
shop_open
shop_close
offer_expired
rewarded_opportunity
interstitial_opportunity
event_start
event_checkpoint
battlepass_checkpoint
session_end
return_after_inactivity
```

Mỗi decision point cần context schema, candidates, eligibility, objective, guardrails, feedback window.

## 3. Session events

```text
app_open
session_start
session_heartbeat
session_end
app_background
app_foreground
```

## 4. Progression events

```text
level_start
level_complete
level_fail
level_retry
checkpoint_reached
boss_start
boss_fail
boss_complete
tutorial_step
tutorial_complete
```

Properties:

```text
level_id
attempt
difficulty
duration
moves_remaining
boosters_used
currency_spent
reward
```

## 5. Economy events

```text
currency_source
currency_sink
inventory_add
inventory_remove
reward_granted
reward_claimed
booster_used
energy_spent
energy_refilled
```

Bắt buộc balance_before/balance_after và reason.

## 6. Offer/IAP events

```text
offer_eligible
offer_requested
offer_returned
offer_impression
offer_dwell
offer_expand
offer_cta_click
checkout_open
checkout_cancel
purchase_success
purchase_failure
offer_dismiss
offer_expire
```

## 7. Ad events

```text
ad_opportunity
ad_eligible
ad_suppressed
ad_request
ad_fill
ad_no_fill
ad_impression
ad_click
ad_dismiss
ad_complete
reward_claim
post_ad_action
```

## 8. Decision log

```json
{
  "decision_id": "d_987",
  "player_id": "u_123",
  "timestamp": "2026-08-21T06:00:00Z",
  "decision_point": "level_fail",
  "policy_version": "monet_v17",
  "experiment_id": "exp_42",
  "context_feature_version": "fs_9",
  "candidate_actions": [
    "NO_ACTION",
    "FREE_RESCUE",
    "REWARDED_REVIVE",
    "IAP_RESCUE_299"
  ],
  "chosen_action": "IAP_RESCUE_299",
  "chosen_action_probability": 0.62,
  "model_scores": {
    "NO_ACTION": 0.00,
    "FREE_RESCUE": 0.11,
    "REWARDED_REVIVE": 0.05,
    "IAP_RESCUE_299": 0.17
  }
}
```

`chosen_action_probability` là field quan trọng cho OPE.

## 9. DECIDE API

Request:

```json
{
  "player_id": "u123",
  "decision_point": "level_fail",
  "context": {
    "level": 241,
    "attempt": 4,
    "session_minutes": 17,
    "soft_currency": 130,
    "hard_currency": 3,
    "fail_streak": 4
  }
}
```

Response:

```json
{
  "decision_id": "d987",
  "action": {
    "mode": "IAP",
    "offer_id": "boss_rescue_04",
    "placement": "fail_modal",
    "presentation": "large",
    "expires_in_sec": 300
  },
  "policy_version": "monet_v17"
}
```

## 10. FEEDBACK API

```json
{
  "decision_id": "d987",
  "event": "checkout_open",
  "timestamp": "2026-08-21T06:00:07Z"
}
```

Delayed outcomes D1/D7/D30 backfill sau.

## 11. Action schema

\[
A =
(Mode, Product, Price, Value, Placement, Creative, Timing, Frequency, Reward)
\]

## 12. Candidate generation

Business constraints trước, ML ranking sau.

```text
approved SKUs only
discount <= cap
bundle value <= economy cap
max ads/session
minimum cooldown
payer protection
tutorial protection
```

Metica + OR-Tools là reference. [METICA_BUNDLES] [ORTOOLS]

## 13. Guardrail engine

### Ad pressure
```text
max_interstitial_session
min_minutes_between_interstitial
max_ads_24h
```

### Payer protection
```text
recent_payer
high_organic_purchase_prob
high_iap_ltv
subscription_active
```

### Churn protection
```text
high_churn_risk
post_ad_exit_spike
recent_negative_session
```

### Economy
```text
max_free_value
max_bundle_discount
currency_inflation_limit
```

### Experience
```text
no repeated offer within X minutes
respect user dismissal
no monetization during protected moments
```

## 14. Experimentation layer

```text
A = static baseline
B = rule segmentation
C = current ML policy
D = candidate ML policy
```

Spotify's separation principle là reference. [SPOTIFY_STACKS]

## 15. Execution adapters

```text
IAP:
  RevenueCat / Adapty / Superwall / native store
IAA:
  MAX / AdMob / ADMOST
Presentation:
  native UI / Storyly-like server-driven surface
Remote config:
  Firebase / Countly
```

Superwall/Adapty phù hợp execution targeting, không phải final brain. [SUPERWALL_AUDIENCE] [ADAPTY_SEGMENTS]

## Nguồn

- **[META_LOOPER] Meta AI — Looper optimization platform** — Primary engineering blog.  
  https://ai.meta.com/blog/looper-meta-ai-optimization-platform-for-engineers/
- **[METICA_API] Metica — REST API / Smart Config** — Primary vendor docs.  
  https://docs.metica.com/api/integration
- **[METICA_SMARTCONFIG] Metica — SmartConfigs UI** — Primary vendor docs.  
  https://docs.metica.com/uiguide/smart-config-ui
- **[METICA_BUNDLES] Metica — Dynamic Adaptive Bundles** — Primary vendor blog.  
  https://metica.com/blog/earn-more-with-dynamic-adaptive-bundles
- **[ORTOOLS] Google OR-Tools — Constraint Optimization** — Primary docs.  
  https://developers.google.com/optimization/cp/
- **[SPOTIFY_STACKS] Spotify Engineering — Separate Tech Stacks for Personalization and Experimentation** — Primary engineering blog.  
  https://engineering.atspotify.com/2026/1/why-we-use-separate-tech-stacks-for-personalization-and-experimentation
- **[SUPERWALL_AUDIENCE] Superwall — Audiences** — Primary vendor docs.  
  https://superwall.com/docs/dashboard/dashboard-campaigns/campaigns-audience
- **[SUPERWALL_RULES] Superwall — Campaign Rules** — Primary vendor docs.  
  https://superwall.com/docs/dashboard/dashboard-campaigns/campaign-rules
- **[ADAPTY_SEGMENTS] Adapty — Segments** — Primary vendor docs.  
  https://adapty.io/docs/segments
- **[ADMOST_WATERFALL] Admost — How does Waterfall work?** — Primary vendor docs.  
  https://resources.admost.com/knowledge-base/how-does-waterfall-work-2/
