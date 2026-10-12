# 10 — Expanded Research Synthesis: Market, Causal Policy, Production & MVP

**Ngày mở rộng:** 2026-08-21  
**Phương pháp:** synthesis từ 4 sub-agent song song: vendor/market, ML-causal policy, production architecture và product roadmap.  
**Mục tiêu:** biến knowledge base hiện có thành một research memo có thể dùng cho quyết định build, product positioning, architecture và experiment planning.

## 1. Executive thesis mở rộng

Luận điểm ban đầu vẫn đúng:

> Monetization Brain không phải ad SDK, IAP SDK, paywall renderer hay dashboard segmentation. Nó là một decision platform chọn next best monetization action theo trạng thái từng player và tối ưu incremental long-term value.

Phần nghiên cứu mở rộng củng cố thêm bốn điểm:

1. **Khoảng trống thị trường nằm ở cross-monetization causal decisioning.** Vendor hiện tại mạnh ở từng lớp: mediation, paywall, messaging, remote config, experimentation. Rất ít bên public full loop từ player memory -> action candidates gồm IAA/IAP/no-action/free/content/difficulty -> causal uplift/bandit -> long-term guardrails -> unified feedback.
2. **MVP không nên là RL.** MVP nên là `DECIDE` / `FEEDBACK`, decision log đủ cho A/B/OPE, rule/segment policy và 3-5 experiment đầu tiên.
3. **Decision log là tài sản ML quan trọng nhất.** Không có `candidate_actions`, `chosen_action_probability`, `policy_version`, point-in-time context và outcome windows thì uplift, bandit, OPE và RL đều yếu.
4. **Vendor tốt nhất nên được dùng như actuator/control layer.** Brain proprietary nên quyết định khi nào dùng MAX/AdMob/ADMOST, RevenueCat/Adapty/Superwall, Storyly/CRM hoặc `NO_ACTION`, thay vì để từng vendor tối ưu cục bộ.

## 2. Sub-agent coverage

| Nhánh | Câu hỏi nghiên cứu | Output chính |
|---|---|---|
| Vendor/market | Ai đang làm các mảnh của Monetization Brain? | Vendor matrix, Turkey signal, buy-vs-build gaps |
| ML/causal | Chính sách quyết định nên học incremental value thế nào? | Method table, OPE checklist, delayed reward, RL gate |
| Production architecture | Hệ thống cần contract nào để chạy thật? | Event envelope, identity graph, feature store, decision log v2, fallback |
| Product roadmap | Build từ đâu để có value sớm? | ICP, MVP wedge, KPI tree, risk matrix, 30/60/90-day plan |

## 3. Market and vendor landscape

### 3.1 New or strengthened insights

1. **Turkey là case về monetization efficiency, không chỉ production volume.** AppMagic/PocketGamer.biz ghi nhận revenue mobile game Turkey tăng mạnh trong khi downloads giảm; Dream Games + Peak chiếm hơn 80% revenue, puzzle gần 97% revenue của developer Turkey. Đây là tín hiệu rằng lợi thế nằm ở liveops, progression, offer design và monetization optimization.
2. **FOMO là case sát nhất về IAA vs IAP policy.** Firebase case study cho thấy studio không chỉ đổi UI offer, mà thay đổi policy: remove rewarded ad ở một context và show IAP offer giá trị cao hơn; reported result là +20% overall LTV. Đây là evidence rằng IAA và IAP nên là competing actions.
3. **Metica là vendor gần Monetization Brain nhất.** Public docs của Metica mô tả Smart Config, eligibility rules, contextual bandits, success metric, attribution window, assignment duration, holdout và dynamic bundles dùng constraint programming + contextual bandits.
4. **Firebase Remote Config Personalization là bridge tốt nhưng chưa đủ.** Firebase dùng contextual multi-armed bandit và baseline group, nhưng mỗi personalization học độc lập. Brain cần phối hợp nhiều decision points và nhiều action classes.
5. **Mediation vendors tối ưu sau khi đã quyết định show ad.** MAX, AdMob, Unity LevelPlay và ADMOST mạnh ở bidding/waterfall/yield. Câu hỏi upstream vẫn là: show ad, show offer, give free value, adjust difficulty hay no action?
6. **Paywall vendors đã chiếm presentation + targeting layer.** RevenueCat Targeting/Experiments, Adapty audiences/placements và Superwall campaigns/rules đều rất hữu ích để thực thi paywall/offer, nhưng chưa phải policy engine tối ưu incremental LTV toàn game.
7. **CRM/engagement vendors có feature primitives hữu ích.** Insider, Braze, CleverTap, OneSignal có purchase/churn prediction, lifecycle, affinity, journeys, push/in-app messaging. Nên xem chúng là channel actuators hoặc feature sources.
8. **Experimentation platforms đang lấn sang decisioning.** Statsig, Amplitude Experiment, GrowthBook/Unleash có gates, variants, JSON payload, experimentation và một số bandit/autotune capability. Dùng được cho control plane, nhưng thiếu game economy, ad pressure, IAP cannibalization và player trajectory semantics.
9. **Presentation personalization là một dimension riêng.** Storyly/Superwall/Adapty cho thấy cùng một offer có thể được render ở story, popup, paywall, banner, card, feed hoặc full-screen. Action schema nên chứa `Placement` và `Presentation`, không chỉ `Product` và `Price`.
10. **Studio lớn thường tiến tới internal operating system.** Playtika-style filings và các paper/case study của King/Wildlife/Meta gợi ý end-state không phải một model đơn lẻ, mà là OS nội bộ cho decisioning, experimentation, rollout, tooling và policy governance.

### 3.2 Vendor/capability matrix

| Vendor/Product | Layer | Capability đáng lấy | Giới hạn so với Monetization Brain |
|---|---|---|---|
| Metica | Brain-like game personalization | Smart Config, contextual bandit, holdout, dynamic bundles | Closed platform; vẫn cần own feature, reward, economy policy |
| Firebase Remote Config | Remote config + personalization | Contextual bandit, Analytics objective, baseline group | Personalizations độc lập, không phải cross-action policy |
| Firebase A/B/Analytics | Measurement/control | Experimentation, objectives, app events | Không có unified IAA/IAP/no-action brain |
| AppLovin MAX | IAA execution | Bidding, waterfall, ad network routing | Downstream ad yield, không quyết định ad vs IAP |
| Google AdMob | IAA execution | Mediation, bidding, mobile ads SDK | Chủ yếu fill/yield/ad serving |
| ADMOST | IAA execution + analytics | Personalized waterfall, ad + IAP analytics | Không phải full long-term player decision system |
| RevenueCat | IAP/paywall infrastructure | Offerings, Targeting, Experiments, Placements | IAP/subscription centric |
| Adapty | Paywall/onboarding monetization | Audiences, placements, flows, A/B tests | Presentation + paywall targeting |
| Superwall | Paywall campaigns | Placements, audiences, filters, holdout, experiments | Paywall-specific actuator |
| Statsig | Experiment/control plane | Gates, experiments, contextual/non-contextual bandits | Generic decisioning, cần game-specific reward/contracts |
| Amplitude Experiment | Analytics + flags | Variants, user context, JSON payloads | Generic config/experiment |
| GrowthBook | Experiment/control plane | Feature flags, experiments | Không native IAA/IAP/economy semantics |
| Unleash | Feature flags | Rollout, strategy constraints | Không phải optimization brain |
| Braze/CleverTap/Insider | Engagement intelligence | Purchase/churn likelihood, lifecycle, journeys | Messaging/channel centric |
| Storyly/OneSignal | Presentation/messaging | Stories, in-app/push, segmentation | Actuator layer |
| Countly | Event/control plane | Open-source analytics, segmentation, remote config candidate | Nền events/control, không có causal policy đầy đủ |

### 3.3 Build-vs-buy implication

| Layer | Recommendation |
|---|---|
| Ad mediation | Buy/use MAX, AdMob, Unity LevelPlay, ADMOST |
| Billing/IAP receipt | Buy/use RevenueCat/native/Adapty |
| Paywall renderer | Buy/use Adapty/Superwall/RevenueCat/native UI |
| Messaging/presentation | Buy/use Storyly/Braze/OneSignal/Superwall or native |
| Analytics/event collection | Use existing analytics + own warehouse |
| Experiment control plane | Use existing if strong; otherwise build minimal |
| Player feature definitions | Build proprietary |
| Decision-point ontology | Build proprietary |
| Decision API | Build |
| Decision log | Build |
| Guardrails/policy engine | Build |
| Incremental reward/CATE labels | Build proprietary |
| Bandit/uplift policy | Build/customize |
| Game economy constraints | Build proprietary |

## 4. Proprietary gaps to own

These are the places where the business advantage should live:

```text
unified action taxonomy
decision-point ontology
point-in-time player state
candidate generation rules
incremental LTV label
organic purchase probability
discount cannibalization model
ad pressure / ad fatigue model
economy damage constraints
payer protection rules
negative responder detection
decision log and OPE dataset
reward contract
policy simulation and blast-radius preview
experiment design templates
rollout and rollback governance
```

The key proprietary abstraction:

\[
Action=(Mode,Product,Price,Value,Placement,Presentation,Timing,Frequency,Reward,Guardrails)
\]

Where:

```text
Mode = NO_ACTION | FREE_ACTION | REWARDED_AD | INTERSTITIAL | IAP_OFFER | DISCOUNT | BUNDLE | LIVEOPS_ACTION | GAMEPLAY_CONFIG
```

## 5. ML and causal decisioning additions

### 5.1 Principles to add

1. **Optimize incremental value, not observed response.**

\[
a^*(x)=\arg\max_a E[Y(a)-Y(NO\_ACTION)\mid X=x]
\]

2. **Propensity is a feature, not the final policy.** High `P(purchase)` may mean "sure thing", not "target with discount".
3. **No-action/baseline is a first-class arm.** Without it, the system cannot estimate cannibalization, retention harm or negative response.
4. **Bandit is personalization, not experiment replacement.** The whole policy still needs independent holdout/A-B evaluation.
5. **Uplift bandit is the natural step after CATE.** Static CATE says who has uplift; uplift bandit learns which action has incremental effect under live uncertainty.
6. **Delayed reward needs a two-layer reward contract.** Use short-term proxies for learning speed, but validate against D7/D30/LTV.
7. **Timing is survival/hazard, not only ranking.** Model purchase hazard, churn hazard, session-end hazard and ad-exit hazard.
8. **RL is a late-stage maturity level.** Offline RL should only start after policy logs, coverage, reward reliability, OPE and safety constraints are mature.
9. **Guardrails should be constraints, not dashboards.** Retention floor, churn cap, ad pressure cap, economy inflation cap and payer harm cap should block unsafe actions.

### 5.2 Method table

| Method | Khi dùng | Data cần | Rủi ro |
|---|---|---|---|
| Propensity model | Phase 3; model state | Labeled outcomes, point-in-time features | Cannibalization nếu dùng làm policy |
| Survival/hazard | Offer timing, churn timing, ad/session-exit risk | Time-to-event, censoring, event windows | Leakage, immortal-time bias, competing risk bị bỏ qua |
| Uplift/CATE | Eligibility cho offer/ad/discount | Randomized treatment/control, outcome windows | Bias nếu treatment non-random, variance cao |
| Meta-learners / causal forest / DML | Heterogeneous treatment effects | Treatment, outcome, covariates, propensity/confounders | CATE tốt offline nhưng policy kém nếu support yếu |
| Contextual bandit | Online action selection với reward tương đối nhanh | Context, action, reward, action probability | Explore gây hại, metric quá ngắn hạn |
| Uplift bandit | Dynamic policy tối ưu incremental effect | Baseline/control arm, uplift reward, hierarchy | Sparse leaf, non-stationarity, khó inference |
| OPE: IPS/SNIPS/DR | Chặn policy tệ trước rollout | Logged propensities, rewards, candidate set | IPS variance cao, DR phụ thuộc reward model |
| Delayed reward model | LTV/retention chậm | Short-term proxies + long-term labels | Proxy hacking, partial attribution |
| Sequential/offline RL | Chuỗi offer/ad/liveops dài hạn | Trajectories, state transitions, reward, behavior policy | Distribution shift, overestimated Q, unsafe exploration |
| Constrained optimization | High-risk monetization actions | Constraint metrics by segment/time/action | Hard cap có thể bó learning nếu quá thô |

### 5.3 Experiment and OPE checklist

- Define estimand first: `Delta IAP`, `Delta IAA`, `Delta Retention`, `Delta LTV`, or weighted reward.
- Always include `NO_ACTION` or static baseline arm.
- Randomize at the right unit: user-level for long-term policy; decision-level only when interference is low.
- Log: `decision_id`, `player_id`, `timestamp`, `state_version`, `context`, `candidate_actions`, `chosen_action`, `chosen_probability`, `policy_version`, `eligibility_reason`.
- Store outcomes at immediate, session, D1, D7 and D30 windows.
- Split outcomes into IAP, IAA, retention, churn, economy and experience.
- Check overlap/support before policy replay.
- Run replay sanity check, IPS/SNIPS, Doubly Robust and bootstrap confidence intervals where possible.
- Report heterogeneity: positive uplift, negative uplift, sure thing, lost cause.
- Use shadow scoring before live rollout.
- Roll out in stages: offline OPE -> shadow scoring -> small holdout A/B -> capped rollout -> full experiment.
- Keep an independent long-term holdout even after bandit launch.
- Freeze delayed-reward cohorts long enough before drawing conclusions.
- Add kill switch, cooldown, max frequency and exploration budget for bandit/RL policies.

## 6. Production architecture additions

### 6.1 Decision service runtime

Production `DECIDE` should be a deterministic pipeline around ML, not a raw model call:

```text
validate request
  ↓
resolve identity/session
  ↓
load consent state
  ↓
load online features
  ↓
derive point-in-time context
  ↓
generate candidates
  ↓
apply eligibility
  ↓
apply hard guardrails
  ↓
score/rank remaining actions
  ↓
assign experiment/exploration
  ↓
bind adapter payload
  ↓
write decision log
  ↓
return action or fallback
```

### 6.2 Event envelope

Every event should carry:

```json
{
  "event_id": "evt_123",
  "event_name": "offer_impression",
  "event_version": 1,
  "schema_version": "2026-08-21",
  "occurred_at": "2026-08-21T06:00:00Z",
  "received_at": "2026-08-21T06:00:02Z",
  "player_id": "p_123",
  "anonymous_id": "anon_456",
  "session_id": "s_789",
  "install_id": "i_001",
  "platform": "ios",
  "app_version": "1.4.2",
  "sdk_version": "0.3.0",
  "country": "TR",
  "consent_state": {
    "analytics": true,
    "personalization": true,
    "personalized_ads": false
  },
  "decision_id": "d_987",
  "source_adapter": "native_offer_ui"
}
```

Event classes:

```text
client_event
server_event
adapter_event
decision_event
outcome_event
privacy_event
```

### 6.3 Identity and session

Add identity graph concepts:

```text
anonymous_id
account_id
player_id_canonical
install_id
device_id_hash
identity_link_event
identity_unlink_event
merge_timestamp
```

Session should be a state machine:

```text
open -> foreground -> active -> background -> ended
                                  ↘ crashed / timeout
```

Useful session fields:

```text
session_sequence_number
session_start_reason
heartbeat_seq
end_reason
last_foreground_at
last_background_at
```

### 6.4 Feature store contract

Split features into:

```text
online feature store  -> DECIDE latency path
offline feature store -> training/OPE/backfill
```

Each feature should have:

```text
feature_name
entity
window
ttl
freshness_sla
owner
privacy_class
training_serving_contract
point_in_time_correctness_rule
```

Streaming/push features:

```text
fail_streak_current
session_minutes
currency_balance
ad_impressions_session
offer_dwell_recent
post_ad_exit_recent
```

Batch features:

```text
pLTV
cohort_ltv
purchase_frequency_30d
ad_revenue_30d
retention_history
acquisition_quality
```

### 6.5 Decision log v2

Current decision log should be extended:

```json
{
  "decision_id": "d_987",
  "request_id": "req_123",
  "idempotency_key": "idem_abc",
  "trace_id": "trace_xyz",
  "player_id": "p_123",
  "session_id": "s_789",
  "timestamp": "2026-08-21T06:00:00Z",
  "decision_point": "level_fail_streak",
  "randomization_unit": "player",
  "experiment_id": "exp_fail_rescue_01",
  "experiment_arm": "policy_c",
  "policy_version": "monet_v17",
  "policy_config_hash": "sha256:...",
  "action_space_version": "actions_v4",
  "context_feature_version": "fs_9",
  "feature_snapshot_ref": "ofs://snapshots/p_123/...",
  "candidate_actions": [
    "NO_ACTION",
    "FREE_RESCUE",
    "REWARDED_REVIVE",
    "IAP_RESCUE_299"
  ],
  "suppressed_actions": [
    {
      "action": "INTERSTITIAL",
      "reason": "high_post_ad_exit_risk"
    }
  ],
  "guardrail_hits": [
    "payer_protection",
    "ad_pressure_cap"
  ],
  "chosen_action": "FREE_RESCUE",
  "chosen_action_probability": 0.35,
  "model_scores": {
    "NO_ACTION": 0.00,
    "FREE_RESCUE": 0.21,
    "REWARDED_REVIVE": 0.05,
    "IAP_RESCUE_299": 0.17
  },
  "fallback": false,
  "fallback_reason": null,
  "latency_ms": 84
}
```

Log suppressed actions as well as chosen actions. Otherwise guardrails cannot be audited and OPE support is hard to diagnose.

### 6.6 API gaps

`DECIDE` request should add:

```text
request_id
idempotency_key
session_id
source_event_id
consent_state
sdk_version
app_version
locale
timezone
network_state
adapter_capabilities
```

`DECIDE` response should add:

```text
fallback
reason_codes
tracking_requirements
execution_contract
adapter_payload
cache_ttl_sec
expires_at
impression_required
```

`FEEDBACK` should add:

```text
idempotency_key
event envelope
adapter metadata
revenue normalization
validation status
outcome_window
```

Missing contracts:

```text
EVENT INGEST API
IDENTITY RESOLUTION API
FEATURE READ/WRITE API
POLICY CONFIG schema
ACTION CATALOG schema
GUARDRAIL REGISTRY schema
CONSENT / RETENTION schema
```

### 6.7 Execution adapter contract

SDK should execute commands, not own the brain:

```text
can_execute
prepare
show
confirm_impression
complete
fail
timeout
cancel
```

IAA adapters should normalize:

```text
ad_request
ad_fill
ad_no_fill
ad_impression
ad_revenue_paid
ad_click
ad_dismiss
ad_complete
reward_granted
```

IAP adapters should normalize:

```text
checkout_open
purchase_success
purchase_failure
refund
restore
receipt_validated
```

Presentation adapters should normalize:

```text
placement
surface
creative_version
dwell_ms
close_reason
cta_click
```

### 6.8 Fallback hierarchy

```text
fresh online features + current policy
  ↓
stale features within TTL
  ↓
cached rule policy
  ↓
NO_ACTION
  ↓
client-local safe default
```

Fallback must be logged as a decision. Otherwise outages create invisible bias in attribution and OPE.

### 6.9 Observability and SLOs

Operational metrics:

```text
DECIDE p95/p99 latency
feature freshness
fallback rate
suppression rate
adapter error rate
no-fill rate
duplicate event rate
consent-denied rate
policy rollback count
decision coverage by decision_point
feedback coverage by action
```

Structured logs should carry:

```text
trace_id
decision_id
player_id_hash
policy_version
experiment_id
adapter_name
fallback_reason
```

## 7. Product roadmap and MVP

### 7.1 ICP

Best initial customers/use cases:

| ICP | Why |
|---|---|
| Casual/hybrid-casual/mid-core mobile games with IAA + IAP | Many decision points and clear ad-vs-IAP tension |
| Studio with small data team but no personalization platform | Pain exists; build capability is missing |
| Publisher operating multiple games | Shared event taxonomy and decision layer compound value |
| Games with progression/economy loops | Rich context for fail, currency, shop, reward and churn decisions |

Not ideal as first wedge:

```text
subscription-only app with few behavioral events
very low DAU game without experiment volume
product with no server-side event warehouse
team unwilling to run holdouts
```

### 7.2 MVP wedge

MVP:

> Rule-based Decision API + experiment-ready decision log for 3 high-value decision points.

Initial decision points:

```text
level_fail_streak
rewarded_opportunity
shop_open / currency_low
```

MVP actions:

```text
NO_ACTION
FREE_RESCUE
REWARDED_REVIVE
INTERSTITIAL
STANDARD_IAP_OFFER
DISCOUNT_OR_BUNDLE
```

MVP promise:

```text
standardize monetization decisions
measure incremental impact
protect users with guardrails
prepare data for uplift/bandit later
```

Avoid promising:

```text
fully autonomous AI monetization
production RL in 90 days
guaranteed LTV uplift without holdouts
```

### 7.3 30/60/90-day plan

| Window | Focus | Deliverables |
|---|---|---|
| 0-30 days | Measurement + API skeleton | Event taxonomy v1, decision log v1, 3 decision points, rule policy, basic dashboard |
| 31-60 days | Segmentation + first experiments | Behavioral cohorts, guardrails, 3-5 A/B tests, recent payer protection, fail-streak rescue |
| 61-90 days | Propensity + uplift readiness | Churn/purchase/ad-tolerance models, randomized holdouts, first CATE readout, OPE-compatible logs |

Phase gates:

```text
Phase 0 -> 1:
  can reconstruct user monetization journey
  IAA + IAP joins reliable

Phase 1 -> 2:
  DECIDE covers target decision points
  FEEDBACK coverage acceptable
  rollback by policy_version works

Phase 2 -> 3:
  experiments have clean holdouts
  guardrail metrics are stable

Phase 3 -> 4:
  model calibration acceptable
  propensity is used as input, not policy

Phase 4 -> 5:
  at least 2 interventions show incremental effect
  negative responders can be detected and protected

Phase 5 -> 6:
  chosen_action_probability is logged
  OPE is trusted enough for candidate policies
  exploration budget and kill switch exist
```

### 7.4 First experiment backlog

| Experiment | Treatment | Primary metric | Guardrail |
|---|---|---|---|
| Recent payer protection | Suppress forced ads | Payer retention / repeat purchase | IAA loss cap |
| High ad tolerance | Increase interstitial frequency | Delta IAA | Post-ad exit, D1 retention |
| Fail streak rescue | Free vs rewarded vs IAP rescue | Delta LTV / session continuation | Economy inflation |
| Checkout abandoner | Standard vs value-adjusted offer | Checkout-to-purchase lift | Discount cannibalization |
| High organic purchase probability | No discount vs discount | Gross revenue / ARPPU | Conversion and retention |
| Mature nonpayer | IAA-first vs hybrid | IAA + retention | Ad fatigue |
| Rewarded-heavy user | Reward size personalization | Ad completion / reward claim | Economy balance |

### 7.5 KPI tree

North Star:

```text
incremental long-term LTV per eligible user
```

| Level | Metrics |
|---|---|
| Revenue | Delta IAP, Delta IAA, ARPDAU, ARPPU, payer conversion, repeat purchase |
| Response | Offer CTR, checkout start, ad completion, reward claim, session continuation |
| Retention | D1/D7/D30 retention, payer retention, return after fail streak |
| Experience | Post-ad exit, offer dismiss rate, session length drop, support complaints |
| Economy | Currency inflation, booster depletion, bundle margin, free value issued |
| Incrementality | Treatment-control Delta LTV, CATE by segment, cannibalized discount revenue |
| Platform health | DECIDE latency, decision coverage, feedback coverage, rollback time |

### 7.6 Risk register

| Risk | Severity | Signal | Mitigation |
|---|---:|---|---|
| Short-term revenue hurts retention | High | ARPDAU up, D7/D30 down | Long-term reward, retention guardrails, holdout |
| Discount cannibalization | High | Conversion up, ARPPU/LTV flat or down | Organic purchase probability, no-discount control |
| Ads damage payer value | High | Payer retention down after ads | Recent/high-value payer suppression |
| Bad taxonomy blocks ML | High | Missing joins, inconsistent outcomes | Phase 0 gate before ML |
| Bandit learns from biased logs | High | No action probability, no holdout | Propensity logging, randomization, OPE |
| Economy inflation | Medium | Balance inflation, sink/source imbalance | Economy caps, approval workflow |
| Sparse context/action data | Medium | Unstable segment lift | Hierarchy/shrinkage, smaller action set |
| Latency affects gameplay | Medium | DECIDE timeout | Default fallback rules, local cache |
| Privacy/platform gaps | Medium | Consent/data retention failures | Consent-aware features, minimization |
| ML replaces experimentation culturally | High | No independent A/B | Separate personalization and experimentation stacks |

## 8. Suggested additions to existing docs

| File | Additions |
|---|---|
| `01_CORE_THESIS.md` | Add market gap: cross-monetization causal decision OS; add "MVP is not RL" warning |
| `02_TURKEY_ECOSYSTEM.md` | Add 2026 Turkey revenue concentration and monetization efficiency signal |
| `03_GLOBAL_CASES_AND_RESEARCH.md` | Add Statsig/Amplitude as decisioning-adjacent experimentation platforms; RevenueCat/Superwall/Adapty as paywall actuators |
| `04_PLAYER_INTELLIGENCE_FEATURES.md` | Add feature TTL, owner, freshness SLA, privacy class, online/offline parity |
| `05_CAUSAL_DECISION_POLICY.md` | Add method table, OPE checklist, delayed reward contract, offline RL caveats |
| `06_ARCHITECTURE_DATA_API.md` | Add event envelope, identity/session, decision log v2, adapter contract, fallback, observability |
| `07_BUILD_ROADMAP.md` | Add ICP, MVP wedge, phase gates, 30/60/90-day plan, KPI tree, risk register |
| `08_REPO_SDK_MAP.md` | Add vendor matrix and clearer buy-vs-build boundary |
| `09_SOURCE_LIBRARY.md` | Add new sources listed below |

## 9. Source additions

### Market/vendor

- **[TURKEY_APPMAGIC_2026] PocketGamer.biz / AppMagic — Turkey mobile gaming revenue concentration** — Market report coverage.  
  https://www.pocketgamer.biz/dream-games-and-peak-games-responsible-for-over-80-of-trkiyes-games-revenue/
- **[STATSIG_BANDITS] Statsig — Advanced Bandit Usage** — Primary product docs.  
  https://docs.statsig.com/autotune/using-bandits
- **[REVENUECAT_TARGETING] RevenueCat — Targeting** — Primary product docs.  
  https://www.revenuecat.com/docs/tools/targeting
- **[REVENUECAT_EXPERIMENTS] RevenueCat — Experiments** — Primary product docs.  
  https://www.revenuecat.com/docs/tools/experiments-v1/experiments-overview-v1
- **[ADAPTY_AUDIENCE_AB] Adapty — Audience paywall A/B test** — Primary product docs.  
  https://adapty.io/docs/add-audience-paywall-ab-test
- **[SUPERWALL_CAMPAIGNS] Superwall — Campaigns** — Primary product docs.  
  https://superwall.com/docs/dashboard/dashboard-campaigns/campaigns
- **[CLEVERTAP_PREDICTIONS] CleverTap — Prediction types and statuses** — Primary product docs.  
  https://docs.clevertap.com/docs/predictions-types-and-statuses

### Architecture/production

- **[FEAST_ARCHITECTURE] Feast — Architecture overview** — Primary docs.  
  https://docs.feast.dev/getting-started/architecture/overview
- **[FEAST_PUSH_SOURCES] Feast — Push sources** — Primary docs.  
  https://docs.feast.dev/reference/data-sources/push
- **[OPENTELEMETRY_DOCS] OpenTelemetry — Documentation** — Primary docs.  
  https://opentelemetry.io/docs/
- **[OPENFEATURE_CONTEXT] OpenFeature — Evaluation Context** — Primary specification.  
  https://openfeature.dev/specification/sections/evaluation-context/
- **[APPLE_ATT] Apple — App Tracking Transparency** — Primary platform docs.  
  https://developer.apple.com/documentation/apptrackingtransparency
- **[GOOGLE_PLAY_DATA_SAFETY] Google Play — Data safety form** — Primary platform docs.  
  https://support.google.com/googleplay/android-developer/answer/10787469
- **[IAB_TCF] IAB Europe — Transparency & Consent Framework** — Primary industry framework.  
  https://iabeurope.eu/transparency-consent-framework/
- **[IAB_GPP] IAB Tech Lab — Global Privacy Platform** — Primary industry framework.  
  https://iabtechlab.com/gpp/

### Causal/OPE/RL

- **[DR_POLICY_EVAL] Dudik et al. — Doubly Robust Policy Evaluation and Learning** — Primary research paper.  
  https://arxiv.org/abs/1103.4601
- **[CRM_SWAMINATHAN] Swaminathan & Joachims — Counterfactual Risk Minimization** — Primary research paper.  
  https://proceedings.mlr.press/v37/swaminathan15.html
- **[GRF] Athey et al. — Generalized Random Forests** — Primary research paper.  
  https://arxiv.org/abs/1610.01271
- **[CATE_METALEARNERS] Künzel et al. — Metalearners for treatment effects** — Primary research paper.  
  https://pmc.ncbi.nlm.nih.gov/articles/PMC6410831/
- **[DELAYED_FEEDBACK_ONLINE] Joulani et al. — Online learning under delayed feedback** — Primary research paper.  
  https://proceedings.mlr.press/v28/joulani13.html
- **[CONSTRAINED_POLICY_OPT] Achiam et al. — Constrained Policy Optimization** — Primary research paper.  
  https://arxiv.org/abs/1705.10528
- **[OFFLINE_RL_BCQ] Fujimoto et al. — Batch-Constrained deep Q-learning** — Primary research paper.  
  https://proceedings.mlr.press/v97/fujimoto19a.html
- **[OFFLINE_RL_CQL] Kumar et al. — Conservative Q-Learning** — Primary research paper.  
  https://papers.neurips.cc/paper_files/paper/2020/hash/0d2b2061826a5df3221116a5085a6052-Abstract.html

## 10. Bottom line

The best build path is:

```text
Measurement foundation
  -> Decision API + decision log
  -> rule/segment policies with guardrails
  -> randomized holdouts and uplift labels
  -> propensity as state features
  -> limited contextual bandit
  -> uplift bandit
  -> sequential/offline RL only after safety and OPE maturity
```

The product should be sold internally or externally as:

> A guarded, experiment-ready decision layer for hybrid monetization, not an autonomous black-box optimizer.

