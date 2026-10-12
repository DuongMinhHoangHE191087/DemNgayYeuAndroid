# 04 — Player Intelligence & Feature Model

> **Lưu ý:** Feature dictionary dưới đây là **design synthesis** từ King, Firebase, Metica, Spotify-style causal targeting và mobile game analytics. Không phải schema nguyên bản của một vendor.

## 1. Player state model

```text
S_t =
[
  StaticProfile,
  Acquisition,
  Lifecycle,
  Engagement,
  Skill,
  Progression,
  Economy,
  IAP,
  IAA,
  Intent,
  TemporalSequence,
  CurrentContext
]
```

Nên maintain cả snapshot hiện tại, rolling windows, trend/velocity, sequence và baseline-vs-current deviation.

## 2. Acquisition features

```text
acq_channel
acq_network
acq_campaign
acq_adset
acq_creative
acq_country
acq_platform
organic_flag
install_timestamp
first_open_timestamp
campaign_objective
campaign_predicted_quality
campaign_iap_bias
campaign_iaa_bias
historical_cohort_ltv
historical_cohort_iap_ltv
historical_cohort_iaa_ltv
historical_cohort_retention_d7
```

FOMO public evidence rằng acquisition channel đủ khác nhau về behavior để segment monetization. [FOMO_FIREBASE]  
Metica cũng nêu acquisition channel là context hữu ích. [METICA_BLENDED_ROAS]

## 3. Lifecycle

```text
account_age_days
active_days_lifetime
active_days_7d
active_days_30d
days_since_last_session
sessions_lifetime
sessions_1d
sessions_7d
sessions_30d
session_frequency_7d
session_frequency_30d
return_interval_mean
return_interval_std
lifecycle_stage
new_user_flag
resurrected_user_flag
slipping_user_flag
```

## 4. Engagement

```text
session_duration_current
session_duration_avg_1d
session_duration_avg_7d
session_duration_avg_30d
session_depth_current
events_per_session
levels_per_session
sessions_per_day
playtime_1d
playtime_7d
playtime_30d
engagement_velocity_24h
engagement_vs_personal_baseline
content_completion_rate
event_participation_rate
social_action_rate
```

## 5. Skill / difficulty response

```text
win_rate_10
win_rate_50
fail_rate_10
fail_rate_50
retry_rate
retry_latency
fail_streak_current
max_fail_streak_7d
time_to_complete_level
moves_remaining_on_win
booster_usage_per_level
skill_score
difficulty_sensitivity
frustration_after_fail
rage_quit_rate
post_fail_session_exit_rate
```

## 6. Progression

```text
current_level
levels_completed_lifetime
levels_completed_1d
levels_completed_7d
progression_velocity_1d
progression_velocity_7d
progression_velocity_30d
progression_acceleration
time_since_last_level_complete
blocked_level_age
attempts_current_level
checkpoint_distance
event_progress
battlepass_progress
progression_vs_cohort
progression_vs_personal_baseline
```

## 7. Economy

```text
soft_currency_balance
hard_currency_balance
energy_balance
inventory_value
booster_count_total
booster_type_counts
currency_inflow_1d
currency_outflow_1d
currency_inflow_7d
currency_outflow_7d
net_currency_flow_1d
net_currency_flow_7d
burn_rate_1h
burn_rate_24h
source_rate
sink_rate
balance_delta_1h
balance_delta_24h
scarcity_score
stockpile_score
economy_pressure
expected_time_to_zero_balance
reward_dependency
free_reward_consumption_rate
```

## 8. IAP behavior

```text
payer_flag
purchase_count_lifetime
purchase_count_1d
purchase_count_7d
purchase_count_30d
purchase_value_lifetime
purchase_value_1d
purchase_value_7d
purchase_value_30d
days_since_last_purchase
hours_since_last_purchase
first_purchase_age
avg_days_between_purchase
std_days_between_purchase
current_purchase_gap_ratio
purchase_frequency_7d
purchase_frequency_30d
purchase_acceleration
avg_order_value
max_order_value
price_band_mode
sku_affinity
bundle_affinity
subscription_history
refund_count
payment_failure_count
checkout_start_count
checkout_cancel_count
store_visit_to_purchase_rate
offer_view_to_purchase_rate
discount_purchase_share
full_price_purchase_share
```

King explicitly nhấn mạnh time windows, ratios, transformations và >200 candidate features. [KING_PAPER]

## 9. IAA behavior

```text
ad_impressions_1h
ad_impressions_session
ad_impressions_1d
ad_impressions_7d
rewarded_opportunities
rewarded_accept_count
rewarded_accept_rate
rewarded_completion_rate
reward_claim_rate
interstitial_impressions
interstitial_dismiss_latency
post_interstitial_exit_rate
post_rewarded_exit_rate
minutes_since_last_ad
minutes_since_last_rewarded
minutes_since_last_interstitial
ad_revenue_1d
ad_revenue_7d
ad_revenue_lifetime
ecpm_user_recent
ecpm_country_recent
ad_fill_rate_user_context
ad_tolerance_score
ad_fatigue_score
ad_revenue_share_of_ltv
```

ADMOST là reference về per-user ad behavior/waterfall và combined IAA+IAP LTV. [ADMOST_WATERFALL] [ADMOST_ANALYTICS]

## 10. Offer / shop intent

```text
shop_open_count_1d
shop_open_count_7d
shop_open_velocity
offer_eligible_count
offer_impression_count
offer_dwell_ms_avg
offer_dwell_ms_recent
offer_expand_count
offer_cta_click_count
offer_cta_rate
checkout_open_count
checkout_cancel_count
checkout_abandon_rate
time_to_close_offer_ms
offer_reopen_rate
price_view_count
bundle_detail_view_count
purchase_failed_count
days_since_last_offer_view
minutes_since_last_offer
same_offer_exposure_count
offer_fatigue_score
```

Intent hierarchy:

```text
impression → close 180ms
```

khác với:

```text
impression → dwell 8.4s → CTA → checkout → cancel
```

Cả hai đều `purchase=0`, nhưng intent khác rất xa.

## 11. Preference / affinity

\[
Affinity_u =
[Power, TimeSaving, Cosmetic, Collection, Competition, Social, Convenience]
\]

Input examples:

```text
power_item_views
power_item_purchases
cosmetic_views
cosmetic_purchases
revive_usage
booster_usage
time_saving_item_usage
collection_completion_behavior
competitive_event_participation
social_purchase_context
```

Insider `Attribute Affinity` và Segmentify/Visilabs recommendation là reference tư duy. [INSIDER_PRED] [SEGMENTIFY_FAQ] [VISILABS_IOS]

## 12. Temporal / sequence features

```text
last_10_session_vector
last_20_level_outcomes
last_10_offer_responses
last_10_ad_responses
last_10_economy_actions
time_since_event_X
event_sequence_pattern
behavior_change_point
short_term_embedding
long_term_embedding
```

Superwall public derived `minutesSince_X / hoursSince_X / daysSince_X`, là ví dụ đơn giản của temporal eligibility. [SUPERWALL_RULES]

## 13. Context tại decision point

```text
decision_point
current_screen
current_level
current_event
session_minute
session_number_today
fail_streak
currency_state
inventory_state
last_action
last_monetization_action
time_since_last_monetization
local_time
day_of_week
network_state
device_performance
```

## 14. Derived latent scores

### Purchase propensity

\[
P(Purchase \le 24h | X)
\]

### Purchase hazard

\[
P(T_{purchase}=d|X)
\]

### Organic purchase probability

\[
P(Purchase|do(NoAction),X)
\]

### Discount uplift

\[
E[Purchase|do(Discount),X]-E[Purchase|do(NoDiscount),X]
\]

### Monetization readiness

Conceptual:

\[
M_t =
w_1 Scarcity +
w_2 Intent +
w_3 Engagement +
w_4 PurchaseHazard +
w_5 FailurePressure -
w_6 ChurnRisk -
w_7 AdFatigue
\]

## 15. Feature quality rules

1. Point-in-time correct; không leakage future data.
2. Có window rõ: 1h/1d/7d/30d/lifetime.
3. Có personal baseline và cohort baseline.
4. Log missingness.
5. Tránh high-cardinality contexts thiếu data.
6. Schema/versioning bắt buộc.
7. Chỉ dùng attributes phù hợp privacy/policy và business need.

## Nguồn

- **[KING_PAPER] Varelas et al. — Development of an End-to-end ML System with Application to IAP** — Primary research paper.  
  https://arxiv.org/abs/2412.12390
- **[FOMO_FIREBASE] Firebase — FOMO Games case study** — Primary platform case study.  
  https://firebase.google.com/case-studies/fomo-games
- **[METICA_BLENDED_ROAS] Metica — Mastering Blended ROAS** — Primary vendor blog.  
  https://www.metica.com/blog/mastering-blended-roas
- **[ADMOST_WATERFALL] Admost — How does Waterfall work?** — Primary vendor docs.  
  https://resources.admost.com/knowledge-base/how-does-waterfall-work-2/
- **[ADMOST_ANALYTICS] Admost — Analytics Tool & Utilization** — Primary vendor docs.  
  https://resources.admost.com/knowledge-base/analytics-tool/
- **[INSIDER_PRED] Insider — Predictive Segments** — Primary vendor docs.  
  https://academy.useinsider.com/docs/audience-predictive-segments
- **[SEGMENTIFY_FAQ] Segmentify — FAQ / real-time personalized recommendations** — Primary vendor docs.  
  https://segmentify.com/faqs/
- **[VISILABS_IOS] Related Digital / Visilabs — iOS Targeting Actions** — Primary vendor docs.  
  https://relateddigital.atlassian.net/wiki/spaces/KB/pages/1029275656/iOS%2BTargeting%2BActions
- **[SUPERWALL_RULES] Superwall — Campaign Rules** — Primary vendor docs.  
  https://superwall.com/docs/dashboard/dashboard-campaigns/campaign-rules
- **[FIREBASE_IAP] Firebase Codelab — Realtime on-device IAP optimization** — Primary technical tutorial.  
  https://firebase.google.com/codelabs/iap-optimization
