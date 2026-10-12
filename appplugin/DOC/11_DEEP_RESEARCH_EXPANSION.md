# 11 — Deep Research Expansion: Compliance, Experimentation, Tooling, Economy & MVP Blueprint

**Ngày mở rộng:** 2026-08-21  
**Phương pháp:** synthesis từ 5 sub-agent song song cộng thêm kiểm tra nguồn primary/current.  
**Phạm vi:** privacy/platform policy, experimentation/OPE, product tooling, MVP implementation, game economy/liveops.

## 1. Executive Summary

Sau bản `10_EXPANDED_RESEARCH_SYNTHESIS.md`, phần cần mở rộng tiếp không còn là "Monetization Brain là gì?", mà là:

```text
Làm sao để nó chạy production mà không:
  - vi phạm privacy/platform policy,
  - tối ưu sai causal metric,
  - phá economy,
  - làm team vận hành không kiểm soát được,
  - hoặc overbuild ML trước khi data foundation đủ chín.
```

Kết luận mới:

1. **Cần một Privacy/Policy Constraint Engine ngang hàng với Guardrail Engine.** Privacy không chỉ là field `consent_state`; nó quyết định feature nào được đọc, model nào được dùng, action nào được tạo, vendor nào được gọi, log nào được ghi và fallback nào hợp lệ.
2. **Experiment/OPE phải là product surface, không phải notebook phụ trợ.** Policy launch cần pre-registration, randomization unit, holdout strategy, MDE, CUPED/variance reduction, SRM checks, OPE estimators, shadow mode và launch gates.
3. **Monetization Brain cần UI vận hành như một control plane.** Tối thiểu phải có Decision-Point Catalog, Action Catalog, Policy Builder, Experiment Setup và Monitoring Dashboard.
4. **Game economy/liveops là constrained action space.** Offer, reward, free rescue, difficulty relief và event calendar đều phải chịu source/sink, payer protection, discount cannibalization, ad pressure và difficulty harm caps.
5. **90-day MVP nên build rất thực dụng.** Rule-based `DECIDE` + `FEEDBACK`, canonical tables, event envelope, decision log chuẩn OPE, Redis/warehouse feature path, 3 decision points và rollout checklist.

## 2. Privacy and Platform Policy

### 2.1 Privacy/Policy Constraint Engine

Kiến trúc hiện tại có:

```text
candidate generation -> guardrails -> policy scoring
```

Nên thêm một lớp trước đó:

```text
privacy/policy entitlements -> privacy feature filter -> candidate generation by entitlement
```

Lý do: với mobile monetization, privacy/platform rules là hard constraints. Nếu user là child/unknown age, ATT denied, CCPA/CPRA sale-share opt-out, TCF/GPP vendor denied hoặc app nằm trong Google Play Families scope, action space phải thay đổi trước khi ML/rules được chạy.

### 2.2 Constraint Groups

| Nhóm | Constraint cần thêm |
|---|---|
| iOS ATT | Nếu dùng IDFA hoặc join dữ liệu app với third-party data cho targeted ads/ad measurement/cross-app tracking thì cần ATT authorization. Nếu denied/restricted/notDetermined: không dùng IDFA, không cross-app tracking, không personalized ad action dựa trên third-party/ad network audience. |
| iOS Privacy Manifest | App/SDK module cần khai báo data collected, purpose, tracking domains, required reason APIs. Không dùng required reason APIs/fingerprinting signals ngoài lý do được Apple cho phép. |
| Apple Kids | Kids Category hoặc app intended for kids: chặn third-party advertising/analytics không phù hợp, cần parental gate cho IAP/link-out khi áp dụng, không gửi PII/device info trẻ em cho third party nếu không có parental consent hợp lệ. |
| Google Play Data Safety | Event SDK, Decision API, ad SDK, IAP SDK phải map sang Data Safety: collected/shared, purpose, encryption, deletion path. |
| Google Play Families | Nếu user là child hoặc unknown age trong mixed-audience app: không transmit AAID/device identifiers, chỉ dùng Families Self-Certified Ads SDK, không interest-based ads/remarketing, không aggressive/deceptive monetization. |
| Android Advertising ID | Android advertising ID có thể bị reset/delete; Android 13+ liên quan `AD_ID` permission. Policy cần fallback khi identifier không khả dụng. |
| GDPR-level | Purpose limitation, data minimization, storage limitation, transparency, lawful basis, erasure, objection/direct-marketing profiling, automated-decision safeguards khi liên quan. |
| CPRA/CCPA-level | Opt-out sale/share, sensitive PI limitation, delete/correct/know rights, privacy preference propagation. |
| IAB TCF/GPP | Consent strings cần được ingest/decode thành quyền theo purpose/vendor/jurisdiction. |
| Children/minors | `age_band`, `is_child`, `is_unknown_age`, `parental_consent_status`, `minor_sale_share_opt_in` phải là hard constraints. |
| Profiling | Không dùng inferred sensitive traits hoặc vulnerable segments để tăng ad pressure/discount manipulation. |
| Retention/deletion | Raw events, features, decision logs, OPE datasets và training snapshots cần TTL, purpose, legal basis và deletion propagation. |
| Non-personalized fallback | Nếu thiếu consent: `NO_ACTION`, contextual ad, standard IAP, generic offer hoặc client-local safe default. |

### 2.3 Consent-Aware Runtime

Production pipeline nên là:

```text
resolve identity / age / jurisdiction
  -> load consent + privacy signals
     ATT, TCF, GPP, CCPA opt-out, child status
  -> compute policy_entitlements
  -> privacy feature filter
  -> candidate generation by entitlement
  -> platform/legal guardrail filter
  -> score only with permitted features/models
  -> bind adapter payload with tracking requirements
  -> log privacy decision facts
  -> fallback if no lawful/policy-safe action remains
```

Suggested objects:

```text
ConsentState:
  analytics_allowed
  personalization_allowed
  personalized_ads_allowed
  sale_share_opt_out
  att_status
  tcf_string
  gpp_string
  child_directed_treatment
  age_band
  parental_consent_status
  jurisdiction
  updated_at
  source
```

```text
PolicyEntitlements:
  can_use_idfa
  can_use_aaid
  can_send_to_ad_network
  can_personalize_ads
  can_personalize_iap_offer
  can_profile_for_ltv
  can_train_model
  can_join_cross_app_data
  can_store_raw_event
  max_retention_days
  allowed_ad_mode
```

### 2.4 Decision Log Privacy Fields

Decision log v2 nên thêm:

```text
consent_snapshot_hash
privacy_policy_version
platform_policy_version
jurisdiction
age_band
att_status
gpp_section_ids
tcf_vendor_allowed
features_suppressed_for_privacy
actions_suppressed_for_privacy
model_allowed
fallback_privacy_reason
retention_class
deletion_trace_id
```

### 2.5 Data and Model Governance Checklist

| Area | Checklist |
|---|---|
| Data inventory | Mỗi event/feature có `privacy_class`, purpose, legal basis/consent dependency, owner, TTL, sharing destination, Data Safety/App Privacy mapping. |
| SDK/vendor registry | Ad/IAP/analytics SDK registry: collected data, privacy manifest, tracking domains, ATT dependency, Families-safe status, GPP/TCF support. |
| Feature store | Enforce consent at read time and training time. Không để model train bằng feature mà runtime không được dùng. |
| Training snapshots | Mỗi snapshot có `snapshot_id`, cohort date, consent eligibility, deletion policy, feature schema hash, model purpose, retention expiry. |
| Deletion | User deletion request cần tombstone identity graph, raw events, derived features, decision logs where required, training snapshots if feasible/required, vendor deletion propagation. |
| Model lineage | Model card: training window, allowed jurisdictions, excluded minors, feature list, consent assumptions, evaluation/guardrails, rollback version. |
| Profiling review | Flag high-risk policies: dynamic discount, payer suppression, churn targeting, high-ad-tolerance targeting, minors, vulnerable segments. |
| Retention | Raw events TTL ngắn hơn; aggregated anonymized metrics dài hơn; decision/OPE logs theo experiment need. |
| Fallback | Non-personalized fallback vẫn phải log để tránh OPE bias. |
| Auditability | Version `consent_schema_version`, `retention_policy_version`, `vendor_policy_version` giống `policy_version`. |

### 2.6 Privacy Risk Table

| Risk | Severity | Signal | Mitigation |
|---|---:|---|---|
| Personalized ads khi ATT denied hoặc child/unknown age | High | IDFA/AAID/ad audience used despite denied status | Entitlement gate trước candidate generation; adapter payload chặn personalized ads. |
| Data Safety/App Privacy mismatch | High | Store disclosure khác event/vendor thực tế | Data inventory auto-map từ event schema + SDK registry; release gate. |
| Children data leakage qua SDK | Critical | AAID/device ID sent before age screen | Default child-safe mode until adult known; remove unsafe SDK paths; neutral age screen. |
| Model train trên user đã opt-out/delete | High | Training snapshot chứa deleted IDs | Snapshot eligibility ledger + deletion propagation + retraining policy. |
| Profiling vulnerable/minor users | High | Discount/ad pressure based on inferred weakness | Protected segment suppression; human review for high-impact monetization policies. |
| Consent drift | Medium | Consent updated but cached features still used | Consent version attached to feature snapshot; invalidate stale features. |
| Retention overrun | Medium | Raw events kept beyond purpose TTL | Automated TTL by table/feature class; retention dashboard. |
| OPE bias from privacy fallback | Medium | Consent-denied traffic omitted from logs | Log fallback decisions and suppression reasons. |
| Vendor onward sharing | High | Ad/analytics SDK repurposes data | Vendor review, TCF/GPP vendor checks, ATT tracking-domain audit. |

## 3. Experimentation and OPE Playbook

### 3.1 Design Principles

1. **Estimand trước, metric sau.** Mỗi experiment cần định nghĩa đang đo `Delta IAP`, `Delta IAA`, `Delta Retention`, `Delta LTV` hay reward tổng hợp.
2. **`NO_ACTION` là arm thật.** Không có baseline/no-action/static policy thì không đo được cannibalization, payer harm, ad fatigue hoặc retention harm.
3. **Default randomization unit là `canonical_player_id`.** Analysis có thể ở decision-level, nhưng variance nên cluster theo player.
4. **Power tính trên eligible triggered population.** Không dùng toàn DAU làm denominator nếu chỉ 8% user trigger decision point.
5. **Pre-register.** Primary metric, MDE, alpha, power, allocation, duration, one/two-sided, stopping rule, multiple testing correction.
6. **CUPED/variance reduction chỉ dùng pre-treatment covariates.** Ví dụ `pre_7d_iap`, `pre_7d_iaa`, `sessions_pre_7d`, `payer_status_pre`.
7. **Sequential monitoring phải có rule.** Guardrail kill-switch được phép real-time, nhưng efficacy decision cần fixed-horizon hoặc always-valid/SPRT pre-spec.
8. **Check interference.** Economy, ad auction, guild/social, shared marketplace, event calendar và multi-game publisher context đều có thể phá SUTVA.
9. **Long-term holdout là bắt buộc trước bandit/RL broad rollout.** D1/D7 là leading indicators; D30/D60 payer retention, repeat purchase, refund và economy inflation mới là source of truth.
10. **Bandit không thay thế experimentation.** Personalization stack serve decisions; experimentation stack evaluate the full policy.

### 3.2 Randomization Unit

| Case | Unit khuyến nghị |
|---|---|
| Policy dài hạn, retention, payer protection | `canonical_player_id` |
| Session-only UI copy, low carryover | `session_id`, có repeated exposure guard |
| Atomic decision-point test | `decision_id`, chỉ khi carryover/interference thấp |
| Guild/clan/social/feed/marketplace/shared economy | Cluster/guild/market/cohort |
| Multi-game publisher policy | Stable account/player across titles |

### 3.3 Holdout Strategy

| Holdout | Mục đích |
|---|---|
| 50/50 balanced A/B | Experiment đầu tiên, cần sensitivity |
| 5-10% independent policy holdout | Đo Brain policy vs static baseline |
| 1-5% long-term holdout | D30/D60/D90 LTV, economy, payer retention |
| Per-action no-action control | Uplift/CATE label |
| Reverse holdback | Khi đã launch rộng nhưng cần đo long-term equilibrium |

### 3.4 OPE Launch Checklist

| Gate | Checklist production |
|---|---|
| Logging schema | `decision_id`, `request_id`, `player_id`, `session_id`, `timestamp`, `decision_point`, `randomization_unit`, `experiment_id`, `arm`, `policy_version`, `policy_hash`, `context_snapshot_ref`, `candidate_actions`, `suppressed_actions`, `guardrail_hits`, `chosen_action`, `chosen_action_probability`, `model_scores`, `fallback`, `latency_ms`. |
| Outcome joins | Immediate, session, D1, D7, D30; split `iap_revenue`, `iaa_revenue`, retention, churn, refund, economy, UX. |
| Propensity validity | `chosen_action_probability > 0`; probability đúng theo behavior policy tại thời điểm decision; không backfill giả. |
| Support/overlap | Candidate policy không chọn action/context chưa có support; min propensity, effective sample size, p95/max weight pass. |
| Replay sanity | Compare action distribution, reward distribution, segment coverage, fallback rate giữa logging policy và candidate policy. |
| Estimator suite | IPS, SNIPS, DR, clipped DR/IPS, direct model; bootstrap CI theo player cluster. |
| Shadow mode | Score 7-14 ngày nhưng không execute; log proposed action, proposed propensity, suppression, predicted reward, latency. |
| Launch gates | OPE lower CI không âm cho primary reward; guardrails pass; no SRM/data loss; rollback tested; kill switch live; exploration cap configured. |
| Rollout | 1% internal/low-risk -> 5% -> 10-25% -> 50% A/B -> full with long-term holdout. |
| Post-launch | Daily health, weekly primary readout, D30 frozen cohort readout, segment harm review. |

### 3.5 Estimator Table

| Estimator | Assumptions | Failure mode |
|---|---|---|
| Replay / rejection sampling | Logged action trùng candidate action; random exploration đủ rộng | Match rate thấp, CI rộng, không dùng được khi policy mới khác xa |
| IPS | Known behavior propensity, positivity, stable reward logging | Extreme weights, variance nổ khi propensity nhỏ |
| SNIPS | Như IPS, thêm self-normalization để giảm variance | Bias finite-sample; có thể che support yếu |
| Direct Method | Reward model đúng/đủ tốt cho mọi action-context | Model bias, extrapolation sang action ít thấy |
| Doubly Robust | Propensity hoặc reward model đúng; overlap đủ | Nếu cả hai sai hoặc support kém thì tự tin sai |
| Clipped IPS/DR | Threshold clipping pre-registered | Giảm variance bằng cách thêm bias; dễ approve policy quá aggressive |
| Longitudinal/delayed OPE | Trajectory log đầy đủ, censoring/attribution xử lý đúng | Delayed feedback, proxy hacking, survivorship bias |

### 3.6 Experiment Reporting Template

| Section | Nội dung |
|---|---|
| Experiment header | Hypothesis, owner, dates, policy versions, randomization unit, eligible population, estimand, MDE, stopping rule |
| Assignment health | Exposure by arm, SRM, eligibility drift, trigger rate, decision coverage, feedback coverage, duplicate/drop rate |
| Primary readout | Raw lift, CUPED lift, CI, p/q-value hoặc always-valid result, power/MDE remaining |
| Revenue decomposition | `Delta IAP`, `Delta IAA`, ARPDAU, ARPPU, payer conversion, repeat purchase, discount cannibalization |
| Guardrails | D1/D7/D30 retention, payer retention, post-ad exit, session continuation, refund, crash, latency, economy inflation |
| OPE panel | IPS/SNIPS/DR, bootstrap CI, ESS, propensity min/p50/p95/max, clipped-weight share, unsupported actions |
| Segment view | New users, payers, recent payers, high ad tolerance, ad-sensitive, checkout abandoners, country/platform/lifecycle |
| Shadow mode | Proposed vs executed action distribution, guardrail suppressions, fallback rate, DECIDE p95/p99 |
| Launch gates | Data quality, support, OPE, guardrail, ops, rollback, privacy/consent status |
| Long-term holdout | D30/D60/D90 LTV, retention, economy health, cumulative cannibalization, cohort freeze status |

## 4. Product Tooling and Control Plane

### 4.1 Personas

| Persona | Mục tiêu | Product surface cần |
|---|---|---|
| Monetization PM / LiveOps | Tăng incremental LTV, setup policy nhanh | Policy authoring UI, decision-point catalog, experiment setup, approval |
| Game Economy Designer | Bảo vệ economy/free value/discount/reward | Guardrail registry, simulator, blast-radius preview |
| Data Scientist / ML | Uplift, propensity, bandit, OPE | Decision log explorer, feature/context schema, OPE dashboard |
| Data Analyst | Đọc kết quả experiment, monitor metrics | Monitoring dashboard, cohort drilldown, metric lineage |
| Backend / Platform Engineer | Tích hợp `DECIDE`/`FEEDBACK`, SLO, rollback | Action catalog, adapter registry, runtime health |
| Finance / Leadership | Biết policy nào tạo incremental profit thật | Executive dashboard, audit log, approval history |
| QA / Release Manager | Đảm bảo config live an toàn | Preview, staging simulator, approval gates, diff/rollback |

### 4.2 Core Workflow

```text
Author policy
  -> Validate
  -> Simulate
  -> Setup experiment
  -> Approval
  -> Shadow score
  -> Rollout
  -> Monitor
  -> Rollback if needed
  -> Analyze / Learn
```

Validation checks:

```text
missing NO_ACTION
missing holdout
action not approved
guardrail conflict
feature freshness below SLA
consent mismatch
unlogged candidate probability
unsupported adapter payload
policy missing owner
no rollback version
```

### 4.3 Module Inventory

| Module | Chức năng |
|---|---|
| Home / Command Center | North star, active policies, experiments, alerts |
| Decision-Point Catalog | Owner, trigger event, context schema, eligible action classes, SDK status |
| Action Catalog | Payload schema, adapter mapping, revenue/economy tags, approval state |
| Guardrail Registry | Hard/soft constraints, metric source, threshold, owner, blocking behavior |
| Policy Builder | Visual rule builder + YAML/JSON editor, ordered rules, version diff |
| Segment / Feature Browser | TTL, owner, privacy class, online/offline parity, sample distribution |
| Experiment Setup | A/B/n, holdout, randomization unit, ramp, assignment stickiness, windows |
| Simulator | Historical replay, synthetic player, cohort preview |
| Blast-Radius Preview | Affected users, revenue exposure, payer exposure, country/platform split |
| Monitoring Dashboard | Runtime + business metrics |
| Alert Center | Severity, impacted policy, recommended action, rollback shortcut |
| Approval Workflow | Required reviewers by risk class, policy diff, simulation evidence |
| Rollback Center | Version history, kill switches, staged rollback |
| Audit Log | Who changed what, diff, approval, publish, rollback, policy hash |
| Decision Log Explorer | Candidate actions, chosen probability, guardrail hits, fallback, feedback linkage |
| Adapter Registry | MAX/AdMob/RevenueCat/Adapty/Superwall/native payload contracts |

MVP modules:

```text
Decision-Point Catalog
Action Catalog
Policy Builder
Experiment Setup
Monitoring Dashboard
```

### 4.4 Policy DSL Sketch

```yaml
policy:
  id: monet_policy_fail_rescue_v1
  version: 17
  status: draft
  owner: monetization_pm
  decision_point: level_fail_streak
  randomization_unit: player
  default_action: NO_ACTION

  objective:
    primary_metric: incremental_ltv_d7
    secondary_metrics:
      - session_continuation
      - iap_revenue_d7
      - iaa_revenue_d7
    guardrail_metrics:
      - d1_retention
      - post_ad_exit_rate
      - currency_inflation

  candidates:
    - action_id: NO_ACTION
      mode: NO_ACTION
    - action_id: FREE_RESCUE_SMALL
      mode: FREE_ACTION
      economy_cost:
        soft_currency_equivalent: 50
    - action_id: REWARDED_REVIVE
      mode: REWARDED_AD
      adapter: applovin_max
      placement: fail_rescue
    - action_id: IAP_RESCUE_BUNDLE_299
      mode: IAP_OFFER
      adapter: revenuecat
      product_id: rescue_bundle_299

  eligibility:
    - name: consent_required
      when: consent.personalization == false
      suppress_all_except:
        - NO_ACTION
    - name: recent_payer_protection
      when: payer_status in ["recent_payer", "high_value_payer"]
      suppress:
        - INTERSTITIAL
        - REWARDED_REVIVE

  guardrails:
    hard:
      - guardrail_id: ad_pressure_session_cap
        condition: ad_impressions_session >= 3
        suppress:
          - REWARDED_REVIVE
          - INTERSTITIAL
      - guardrail_id: economy_free_value_daily_cap
        condition: free_value_issued_24h > 500
        suppress:
          - FREE_RESCUE_SMALL

  experiment:
    id: exp_fail_rescue_01
    enabled: true
    holdout:
      action_id: NO_ACTION
      allocation: 0.10
    arms:
      - id: rule_policy
        allocation: 0.45
      - id: rescue_iap_heavy
        allocation: 0.45
    outcome_windows:
      - session
      - d1
      - d7
      - d30

  logging:
    require_candidate_actions: true
    require_chosen_probability: true
    log_suppressed_actions: true
    log_guardrail_hits: true
```

### 4.5 Dashboard and Alerts

Dashboard tiers:

| Tier | Widgets |
|---|---|
| Executive / Monetization Health | Incremental LTV, revenue split, retention guardrails, risk panel, decision coverage |
| Policy Runtime | Decision volume, action distribution, guardrail hits, fallback health, latency/SLO, arm balance |
| Data / ML Readiness | Decision log completeness, feedback coverage, support/overlap, outcome maturity, OPE readiness |
| Economy / Experience | Source/sink ratio, ad pressure, discount pressure, frustration, payer protection |

Alert design:

```text
severity
metric
threshold
baseline
cohort
first_seen
policy_version
decision_point
experiment_id
action_id
diagnosis
recommended_action
rollback_link
```

Critical examples:

```text
D1 retention delta < -1.5%
DECIDE fallback > 20%
payer ads guardrail breached
feedback coverage < 80%
arm imbalance / SRM
post-ad exit spike
```

## 5. MVP Implementation Blueprint

### 5.1 Architecture

```text
Game Client / Local SDK
  -> Event Ingest API
  -> Raw Event Store + Stream Processor
  -> Online State Store
  -> Feature Compute Jobs
  -> Online Feature Store / Offline Feature Store
  -> Decision Service
       validate -> identity -> consent -> features -> candidates
       -> eligibility -> guardrails -> rule/rank -> experiment
       -> adapter payload -> decision log -> response
  -> Execution Adapters
       IAA: MAX / AdMob / ADMOST
       IAP: RevenueCat / native store / Adapty / Superwall
       UI: native offer modal / shop / rewarded placement
  -> Feedback API
  -> Outcome Joiner / Experiment Analysis / OPE Dataset
```

### 5.2 Service Boundaries

| Service | Scope | Không làm |
|---|---|---|
| SDK | Event capture, `DECIDE`, execute action, feedback | Không chứa brain logic |
| Event Ingest | Validate schema, idempotency, consent tags | Không compute policy |
| Identity/Session | Canonical player, install/session state | Không merge mù nếu thiếu consent |
| Feature Service | Online reads, batch/stream materialization | Không train model trong latency path |
| Decision Service | Candidates, rules, guardrails, experiment assignment | Không gọi vendor SDK trực tiếp |
| Adapter Gateway | Bind action thành IAA/IAP/UI payload | Không tự chọn monetization action |
| Experiment/OPE | Holdout, treatment-control, propensity logs | Không thay policy runtime |

### 5.3 90-Day Storage Choices

| Layer | 90-day choice | Lý do |
|---|---|---|
| Raw events | Postgres JSONB hoặc BigQuery/Snowflake nếu đã có | Audit, replay, schema evolution |
| Stream | Kafka/Redpanda nếu team có ops; otherwise managed Pub/Sub/Kinesis or 1-5 minute micro-batch | Tránh overbuild |
| Online state | Redis | Fail streak, session counters, cooldown, ad pressure |
| Decision log | Postgres partitioned table + warehouse export | Transactional, OPE-ready |
| Offline features | Warehouse tables | Point-in-time training/OPE |
| Online features | Redis/Feast online store | Low latency lookup |
| Feature registry | YAML/db table ban đầu; Feast khi feature count tăng | Versioned feature definitions |
| Observability | OpenTelemetry traces/log correlation | Trace `DECIDE -> feature -> policy -> adapter` |

### 5.4 Canonical Tables

```sql
players(
  player_id, anonymous_id, first_seen_at, country, platform,
  app_version_first, consent_analytics, consent_personalization,
  created_at, updated_at
)

sessions(
  session_id, player_id, install_id, started_at, ended_at,
  end_reason, session_seq, duration_sec, app_version, sdk_version
)

events_raw(
  event_id, event_name, event_version, schema_version,
  occurred_at, received_at, player_id, anonymous_id, session_id,
  decision_id, source_adapter, consent_state_json, properties_json
)

player_feature_snapshots(
  snapshot_id, player_id, entity_ts, feature_version,
  lifecycle_json, engagement_json, skill_json, economy_json,
  iap_json, iaa_json, intent_json, context_json, freshness_json
)

action_catalog(
  action_id, action_space_version, mode, product_id, price_micros,
  value_json, placement, presentation, reward_json,
  eligibility_json, economy_cost_json, active
)

guardrail_registry(
  guardrail_id, version, type, severity, rule_json,
  applies_to_action_modes, owner, active
)

decision_logs(
  decision_id, request_id, idempotency_key, trace_id,
  player_id, session_id, decided_at, decision_point,
  experiment_id, experiment_arm, randomization_unit,
  policy_version, policy_config_hash, action_space_version,
  feature_snapshot_ref, candidate_actions_json,
  suppressed_actions_json, guardrail_hits_json,
  chosen_action, chosen_action_probability,
  model_scores_json, fallback, fallback_reason, latency_ms
)

feedback_events(
  feedback_id, decision_id, event_id, event_name,
  occurred_at, adapter_name, revenue_micros, currency,
  validation_status, properties_json
)

outcome_windows(
  decision_id, player_id, window_name,
  iap_revenue_micros, iaa_revenue_micros,
  retained, churned, session_continued,
  post_ad_exit, purchase_success, reward_claimed,
  computed_at
)
```

### 5.5 API Examples

`POST /v1/decide`

```json
{
  "request_id": "req_123",
  "idempotency_key": "p123:s789:level_fail_streak:4",
  "player_id": "p_123",
  "session_id": "s_789",
  "source_event_id": "evt_123",
  "decision_point": "level_fail_streak",
  "occurred_at": "2026-08-21T06:00:00Z",
  "sdk_version": "0.3.0",
  "app_version": "1.4.2",
  "locale": "tr-TR",
  "timezone": "Europe/Istanbul",
  "network_state": "wifi",
  "consent_state": {
    "analytics": true,
    "personalization": true,
    "personalized_ads": false
  },
  "adapter_capabilities": {
    "rewarded_ad": true,
    "interstitial": true,
    "iap_checkout": true,
    "native_modal": true
  },
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
  "decision_id": "d_987",
  "policy_version": "monet_rules_v1",
  "experiment_id": "exp_fail_rescue_01",
  "experiment_arm": "rule_segmented",
  "fallback": false,
  "reason_codes": ["fail_streak_high", "low_hard_currency", "payer_protection_passed"],
  "action": {
    "action_id": "rewarded_revive_v1",
    "mode": "REWARDED_AD",
    "placement": "fail_modal",
    "presentation": "large_modal",
    "reward": {
      "type": "revive",
      "amount": 1
    },
    "expires_in_sec": 300
  },
  "execution_contract": {
    "impression_required": true,
    "feedback_required": ["ad_impression", "ad_complete", "reward_claim", "post_ad_action"],
    "timeout_ms": 2500
  },
  "adapter_payload": {
    "adapter": "admob",
    "ad_unit": "rewarded_revive",
    "server_side_verification": {
      "decision_id": "d_987"
    }
  },
  "cache_ttl_sec": 30
}
```

### 5.6 Policy Pseudocode

```python
def decide(req):
    trace = start_trace(req.request_id)
    assert_valid_schema(req)
    if is_duplicate(req.idempotency_key):
        return previous_response(req.idempotency_key)

    identity = resolve_identity(req.player_id, req.anonymous_id, req.session_id)
    consent = resolve_consent(req.consent_state, identity)
    entitlements = compute_policy_entitlements(consent, identity)

    if not entitlements.can_store_raw_event:
        return logged_fallback(req, "NO_ACTION", "privacy_storage_not_allowed")

    features = load_online_features(identity.player_id, entitlements)
    features = merge_request_context(features, req.context)

    candidates = generate_candidates(req.decision_point, features, req.adapter_capabilities, entitlements)
    candidates = filter_by_privacy_entitlements(candidates, entitlements)

    suppressed = []
    eligible = []
    for action in candidates:
        if not business_eligible(action, features):
            suppressed.append((action, "eligibility_failed"))
        elif violates_hard_guardrail(action, features):
            suppressed.append((action, guardrail_reason(action, features)))
        else:
            eligible.append(action)

    if not eligible:
        return logged_fallback(req, "NO_ACTION", "no_eligible_action")

    arm = assign_experiment(identity.player_id, req.decision_point)
    if arm == "static_baseline":
        chosen, prob = baseline_policy(eligible), 1.0
    elif arm == "rule_segmented":
        chosen, prob = rules_policy(eligible, features), 1.0
    else:
        chosen, prob = exploration_policy(eligible, features, budget="mvp_capped")

    payload = bind_adapter_payload(chosen, req.adapter_capabilities, entitlements)
    log_decision(req, features, candidates, suppressed, chosen, prob, arm, trace.id)
    return response(chosen, payload, tracking_requirements(chosen))
```

### 5.7 Rollout Checklist

0-30 days:

```text
event schema/versioning
SDK event envelope + decision_id propagation
DECIDE and FEEDBACK endpoints
decision_logs with candidates/probability/policy_version
3 decision points
fallback hierarchy
OpenTelemetry trace IDs
journey reconstruction gate
```

31-60 days:

```text
behavioral cohorts
guardrail registry
policy config versioning
first A/B tests
runtime dashboard
rollback by policy_version
feedback coverage gate
```

61-90 days:

```text
batch propensity features
D1/D7 outcome windows
first CATE readout
OPE dataset export
shadow-score candidate ML policy
clean holdouts + stable guardrails gate
```

## 6. Game Economy and LiveOps

### 6.1 Economy Guardrail Framework

Monetization Brain nên coi economy/liveops là constrained action space:

```text
Action = Mode + Product + Price + Value + Placement + Timing + Frequency + EconomyImpact + Guardrails
```

| Guardrail | Ý nghĩa | Chặn khi |
|---|---|---|
| `payer_protection` | Bảo vệ whale/dolphin/recent payer khỏi ads/discount rẻ tiền | high payer value + high post-ad harm hoặc high organic purchase probability |
| `discount_cannibalization_cap` | Không bán rẻ cho user vốn sẽ mua full-price | `P(purchase|no_discount)` cao, uplift thấp |
| `economy_inflation_cap` | Không bơm quá nhiều currency/booster/free rescue | source/sink ratio vượt ngưỡng, stockpile tăng |
| `free_value_budget` | Free rescue/reward là retention investment, không vô hạn | free value issued/user/day hoặc per fail streak vượt cap |
| `ad_pressure_cap` | IAA không được phá IAP/retention | ad impressions/session, post-ad exit, payer ad exposure vượt cap |
| `difficulty_harm_cap` | Difficulty tuning không được thành paywall trá hình | fail streak/rage quit tăng nhưng rescue monetization tăng bất thường |
| `event_fatigue_cap` | Calendar không được đốt cohort | participation decay, post-event retention drop |
| `bundle_margin_floor` | Bundle vẫn phải có unit economics | effective discount/value ratio vượt giới hạn |
| `sku_ladder_integrity` | Không phá price/value ladder | bundle rẻ hơn quá nhiều so với SKU cùng tier |
| `segment_fairness` | Tránh dynamic pricing gây cảm giác bất công | cùng context thấy chênh giá khó giải thích |

### 6.2 Guardrail Formulas

```text
Economy Source/Sink:
  source_rate <= sink_capacity * allowed_inflation_multiplier

Payer Harm:
  if payer_value_tier >= dolphin and post_ad_exit_risk high:
      suppress INTERSTITIAL
      prefer NO_ACTION / premium offer / event access

Discount:
  if organic_purchase_probability high and discount_uplift low:
      suppress DISCOUNT
      allow STANDARD / ACCESS / COSMETIC / NO_ACTION

Free Rescue:
  if fail_streak high and churn_risk high:
      allow FREE_RESCUE
  else if reward_dependency high:
      cooldown FREE_RESCUE

Event Calendar:
  same_event_type_repeat_decay <= threshold
  post_event_D7_retention >= baseline - tolerance
```

### 6.3 Action Design Patterns

| Pattern | Khi dùng | Thiết kế |
|---|---|---|
| Free Rescue as Retention Investment | Fail streak cao + churn risk cao + payer intent chưa rõ | Cứu miễn phí 1 lần, cooldown, log như action có cost |
| Rewarded Rescue Ladder | Rewarded affinity cao, ad fatigue thấp | Free hint -> rewarded revive -> small IAP rescue |
| No Discount for Sure Things | High purchase hazard + high organic purchase probability | Standard/premium offer, không giảm giá |
| First-Purchase Converter | Mature nonpayer, shop intent cao | SKU nhỏ, value rõ, one-time, không phá ladder |
| Dolphin Expansion Bundle | Payer vừa/đều, affinity rõ | Bundle theo power/time-saving/event progress affinity |
| Whale Access, Not Cheapness | High-value payer | Early access, exclusive progression, convenience, limited quantity |
| F2P IAA Participation Loop | Nonpayer + ad tolerance cao | Rewarded ads đổi event currency, cap để không inflate economy |
| Difficulty Relief Before Monetization | Frustration/rage quit cao | Giảm difficulty nhẹ hoặc free booster trước khi bán rescue |
| Scarcity-Aware Bundle | Currency thấp + sink khỏe | Bundle bù đúng resource thiếu |
| Stockpile Sink Offer | Dư currency/booster | Event sink, crafting, cosmetic, leaderboard entry |
| Calendar Rotation | LiveOps dài hạn | Macro season + mid-cycle event + micro offer |
| Cannibalization-Safe Flash Sale | Sale event | Exclude high organic buyers; target price-sensitive/lapsed/checkout abandoners |

### 6.4 Price/Value Ladder

```text
Free value:     rescue, hint, small booster, event sample
$0.99-$1.99:    first purchase / starter rescue
$2.99-$4.99:    progression bundle
$9.99-$14.99:   event completion / battle pass / mid-tier bundle
$19.99-$49.99:  high-value resource + access
$99+:           whale pack, scarce convenience, prestige, not deep discount default
```

Bundle constraints:

```text
min_margin
max_discount_depth
max_currency_issued
max_booster_by_type
required_affinity_match
inventory_stockpile_exclusion
event_relevance_required
price_ladder_position
payer_tier_allowed
cooldown_since_last_bundle
```

### 6.5 Economy Experiments

| Experiment | Arms | Primary | Guardrail |
|---|---|---|---|
| Fail Streak Rescue | `NO_ACTION`, `FREE_RESCUE`, `REWARDED_REVIVE`, `$2.99_RESCUE` | Incremental LTV, session continuation | Free value, economy inflation, D1/D7 |
| Discount Cannibalization | Standard vs 20/40% discount | Incremental gross revenue | ARPPU, repeat purchase |
| Recent Payer Protection | Normal ads vs forced-ad suppression | Payer retention, repeat purchase | IAA loss cap |
| Rewarded Reward Size | Low/medium/high reward | Completion + D1 return | Source/sink, ad fatigue |
| Difficulty Relief | Baseline vs mild relief for high-frustration users | Retention, later IAP/IAA | Rescue IAP cannibalization, progression speed |
| Event Calendar Cadence | Same event vs rotation vs cooldown week | 28-day LTV | Participation decay, post-event D7 |
| Whale Strategy | Discount bundle vs access/prestige bundle | Whale incremental LTV | Refund/support, future full-price purchase |
| Mature Nonpayer Hybrid | IAA-first vs hybrid vs first-purchase offer | Total incremental LTV | Churn, ad fatigue |

### 6.6 Metrics

Economy:

```text
source_rate
sink_rate
source_sink_ratio
net_currency_flow_1d/7d
balance_inflation_index
stockpile_rate
expected_time_to_zero_balance
free_value_issued_per_user
reward_dependency_score
booster_depletion_rate
event_currency_breakage
```

Cannibalization:

```text
organic_purchase_probability
discount_uplift
incremental_revenue_per_discount_dollar
full_price_purchase_displacement
ARPPU_after_discount
repeat_purchase_gap
sure_thing_targeting_rate
```

Payer protection:

```text
payer_ad_exposure
recent_payer_interstitial_rate
post_ad_exit_payer
payer_D7/D30_retention
repeat_purchase_rate
refund_rate
support_complaint_rate
```

LiveOps:

```text
event_participation_rate
repeat_run_participation_decay
event_revenue_lift
post_event_retention
event_completion_rate
battle_pass_progress_distribution
event_sink_utilization
calendar_overlap_pressure
```

Difficulty/free rescue:

```text
fail_streak_current
rage_quit_rate
post_fail_exit_rate
rescue_accept_rate
free_rescue_to_return_rate
difficulty_relief_to_purchase_lag
progression_velocity
level_completion_after_intervention
```

## 7. Integrated Production Model

The mature production loop should now be:

```text
PLAYER / APP
  -> EVENT SDK
  -> PRIVACY + CONSENT + AGE + JURISDICTION RESOLUTION
  -> EVENT STREAM / RAW STORE
  -> ONLINE STATE + FEATURE STORE
  -> DECISION POINT TRIGGER
  -> PRIVACY/POLICY CONSTRAINT ENGINE
  -> CANDIDATE GENERATOR
  -> ECONOMY + EXPERIENCE + PAYER GUARDRAILS
  -> EXPERIMENT / HOLDOUT ASSIGNMENT
  -> RULE / UPLIFT / BANDIT POLICY
  -> ADAPTER PAYLOAD
  -> DECISION LOG V2
  -> SDK EXECUTION
  -> FEEDBACK + OUTCOME WINDOWS
  -> EXPERIMENT/OPE REPORTING
  -> POLICY TOOLING / APPROVAL / ROLLBACK
```

## 8. Source Additions

### Privacy/platform

- **[APPLE_PRIVACY_MANIFEST] Apple — Privacy manifest files** — Primary platform docs.  
  https://developer.apple.com/documentation/bundleresources/privacy-manifest-files
- **[APPLE_USER_PRIVACY_DATA_USE] Apple — User privacy and data use** — Primary platform docs.  
  https://developer.apple.com/app-store/user-privacy-and-data-use/
- **[APPLE_REQUIRED_REASON_APIS] Apple — Describing use of required reason API** — Primary platform docs.  
  https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api
- **[APPLE_REVIEW_GUIDELINES] Apple — App Review Guidelines** — Primary platform docs.  
  https://developer.apple.com/app-store/review/guidelines/
- **[GOOGLE_PLAY_FAMILIES] Google Play — Families policies** — Primary platform docs.  
  https://support.google.com/googleplay/android-developer/answer/9893335
- **[GOOGLE_PLAY_FAMILIES_DATA] Google Play — Families data practices** — Primary platform docs.  
  https://support.google.com/googleplay/android-developer/answer/11043825
- **[GOOGLE_PLAY_AD_ID] Google Play — Advertising ID policy** — Primary platform docs.  
  https://support.google.com/googleplay/android-developer/answer/6048248
- **[GDPR_TEXT] EU — GDPR regulation text** — Primary legal text.  
  https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32016R0679
- **[EDPB_GDPR_PRINCIPLES] EDPB — Basic GDPR principles** — Primary regulator guidance.  
  https://www.edpb.europa.eu/topics/key-gdpr-concepts/basic-principles_en
- **[CA_CCPA] California OAG — CCPA/CPRA rights** — Primary regulator guidance.  
  https://oag.ca.gov/privacy/ccpa
- **[CPPA_REGS] California Privacy Protection Agency — CCPA updates/regulations** — Primary regulator source.  
  https://cppa.ca.gov/regulations/ccpa_updates.html
- **[FTC_COPPA] FTC — COPPA compliance plan** — Primary regulator guidance.  
  https://www.ftc.gov/business-guidance/resources/childrens-online-privacy-protection-rule-six-step-compliance-plan-your-business
- **[IAB_DDRF] IAB Tech Lab — Data Deletion Request Framework** — Primary industry framework.  
  https://iabtechlab.com/standards/data-deletion-request-framework/

### Experimentation/OPE/tooling

- **[KOHAVI_CONTROLLED_EXPERIMENTS] Kohavi et al. — Controlled experiments on the web** — Primary research paper.  
  https://link.springer.com/article/10.1007/s10618-008-0114-1
- **[KOHAVI_TRUSTWORTHY_BOOK] Kohavi, Tang, Xu — Trustworthy Online Controlled Experiments** — Primary book/reference.  
  https://experimentguide.com/
- **[CUPED] Deng et al. — Improving sensitivity of online controlled experiments by utilizing pre-experiment data** — Primary research paper.  
  https://doi.org/10.1145/2433396.2433413
- **[ALWAYS_VALID_INFERENCE] Johari et al. — Always valid inference** — Primary research paper.  
  https://doi.org/10.1287/opre.2021.2135
- **[INTERFERENCE_TESTING] Ugander et al. — Graph cluster randomization / interference testing** — Primary research paper.  
  https://academic.oup.com/biomet/article/106/4/929/5578431
- **[STATSIG_POWER_ANALYSIS] Statsig — Power Analysis** — Primary product docs.  
  https://docs.statsig.com/experiments/power-analysis
- **[STATSIG_HOLDOUTS] Statsig — Holdouts** — Primary product docs.  
  https://docs.statsig.com/experiments/holdouts-introduction
- **[STATSIG_EVALUATION] Statsig — How Evaluation Works** — Primary product docs.  
  https://docs.statsig.com/sdks/how-evaluation-works
- **[OPENFEATURE_HOOKS] OpenFeature — Hooks** — Primary specification.  
  https://openfeature.dev/specification/sections/hooks/
- **[OPENFEATURE_TRACKING] OpenFeature — Tracking** — Primary specification.  
  https://openfeature.dev/specification/sections/tracking/
- **[LAUNCHDARKLY_APPROVALS] LaunchDarkly — Approvals** — Primary product docs.  
  https://launchdarkly.com/docs/home/releases/approvals
- **[GROWTHBOOK_DOCS] GrowthBook — Documentation** — Primary product docs.  
  https://docs.growthbook.io/
- **[KAFKA_DOCS] Apache Kafka — Documentation** — Primary docs.  
  https://kafka.apache.org/documentation/

### Economy/liveops

- **[METICA_CONTEXTUAL_BANDITS] Metica — How to design contextual bandits** — Primary vendor blog.  
  https://www.metica.com/blog/how-to-design-contextual-bandits
- **[DYNAMIC_DIFFICULTY_RETHINKING] Rethinking dynamic difficulty adjustment for video game design** — Primary research paper.  
  https://www.sciencedirect.com/science/article/pii/S1875952124000314
- **[PERSONALIZED_GAME_DESIGN_SSRN] Personalized Game Design for Improved User Retention and Monetization in Freemium Mobile Games** — Primary research working paper.  
  https://papers.ssrn.com/sol3/papers.cfm?abstract_id=4653319
- **[UNITY_LIVEOPS] Unity — LiveOps documentation** — Primary product docs.  
  https://docs.unity.com/en-us/liveops
- **[UNITY_ANALYTICS_EVENTS] Unity — Analytics standard events** — Primary product docs.  
  https://docs.unity3d.com/2021.1/Documentation/Manual/UnityAnalyticsStandardEvents.html
- **[GOOGLE_PLAY_IAP_PRODUCTS] Google Play Console — In-app products setup** — Primary platform docs.  
  https://play.google.com/console/about/in-appproductssetup/

## 9. Bottom Line

The next version of the Monetization Brain thesis should be:

> Build a guarded, consent-aware, experiment-ready decision control plane for hybrid monetization.

Not:

```text
Build an AI that maximizes revenue.
```

The real defensible system is the combination of:

```text
privacy/policy entitlements
  + point-in-time player state
  + economy-aware action catalog
  + guardrail registry
  + experiment/OPE discipline
  + decision log v2
  + policy tooling
  + safe rollout/rollback
```

