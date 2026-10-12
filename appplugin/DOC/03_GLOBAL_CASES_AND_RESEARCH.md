# 03 — Global Cases, Papers và Production Patterns

## 1. Metica — gần nhất với “Monetization Brain as a product”

Metica định vị trực tiếp cho mobile games: near-real-time player data + personalization/optimization + experimentation.

Docs nêu:

- dynamic/personalized offers,
- eligibility rules theo player properties,
- contextual bandits,
- A/B logic,
- success metric,
- attribution window,
- assignment duration,
- holdout group,
- calculated attributes từ player events. [METICA_OVERVIEW] [METICA_GETTING_STARTED] [METICA_SMARTCONFIG]

Smart Config use cases được public gồm:

```text
offer timing
ad frequency
reward size
pricing experiments
game/app dynamic payloads
```

[METICA_SMARTCONFIG]

REST API cho phép fetch personalized configs theo user/device context. [METICA_API]

### Core pattern

```text
USER CONTEXT
    ↓
ELIGIBILITY
    ↓
VARIANTS
    ↓
A/B or CONTEXTUAL BANDIT
    ↓
SUCCESS METRIC / REWARD
    ↓
ASSIGNMENT + ATTRIBUTION
```

### Adaptive Bundles

Metica mô tả dùng constraint programming để sinh bundle hợp lệ rồi contextual multi-armed bandit tối ưu performance. [METICA_BUNDLES]

## 2. Firebase — bridge từ segmentation tới personalized decisioning

### FOMO
IAA vs IAP tailored policy, reported +20% LTV. [FOMO_FIREBASE]

### Halfbrick
Firebase case nói Halfbrick dùng Remote Config Personalization để personalize interstitial frequency; reported ads/user tăng từ 3.4 lên 4.2 và ARPDAU +16% mà không thấy retention/ratings degrade trong case. [HALFBRICK_FIREBASE]

### GameNexa
Dùng Analytics/Predictions để hiểu/segment propensity rồi Remote Config + A/B testing để thử ad placements/formats và IAP promotions. Reported IAP revenue 2.5x và customized IAP packs tăng conversion 6x. [GAMENEXA_FIREBASE]

### Pomelo
Testing interstitials qua Firebase; case reported +25% AdMob revenue, +35% IAP và gần như không giảm retention. [POMELO_FIREBASE]

### Remote Config Personalization
Firebase Personalization có baseline group khoảng 20% và đo lift so với baseline. [FIREBASE_PERSONALIZATION]

### IAP optimization codelab
Google public tutorial frame IAP optimization là contextual bandit:

\[
State = user + current session
\]

\[
Action = IAP offer
\]

\[
Reward = 0 \text{ if reject; IAP value if accept}
\]

Data row cần user + game state + offer + response. Codelab gợi ý features từ Firebase Analytics/Firestore như play duration, level, coins spent, purchases, balance, signup date. Có epsilon-greedy exploration ví dụ 30% và sample model reported 28% better than random agent trên test data. [FIREBASE_IAP]

Repo code: [FIREBASE_IAP_REPO]

## 3. King / Activision Blizzard — personalized offer timing

Paper của King mô tả personalized IAP system trong game như Candy Crush.

Ba câu hỏi của offer:

1. show ở đâu,
2. offer gồm item gì,
3. show khi nào.

Paper tập trung vào câu 3: dự đoán thời điểm next IAP. [KING_PAPER]

Họ ghi nhận purchase behavior phụ thuộc long-term behavior + short-term behavior.

Feature families gồm play, purchase, context; aggregations/time windows/ratios/custom transformations tạo **>200 candidate features**.

Concept quan trọng:

\[
P(p^{(i)} \text{ purchases on } d_n \mid X_i)
\]

(dạng đầy đủ trong paper; ý tưởng lõi là mô hình hóa số purchase theo ngày thay vì chỉ `P(purchase)` nhị phân).

## 4. Dream11 — Hierarchical Contextual Uplift Bandits

Paper 2026 kết hợp contextual bandits, hierarchical context, uplift modeling và policy transfer để giảm cold-start/sparse-context problem.

Large-scale A/B test reported +0.42% revenue cùng improvement về user satisfaction; sau deployment production reported thêm +0.51% revenue. [DREAM11_PAPER]

Key conceptual shift:

\[
\max E[Y|A,X]
\]

sang:

\[
\max (E[Y|do(A),X]-E[Y|do(Control),X])
\]

## 5. Spotify — causal eligibility bằng uplift

Spotify có holdout để đo effect của in-app messaging, nhận thấy treatment effect heterogeneous và train uplift/CATE model để quyết định user nào nên nhận message.

\[
CATE(x)=E[Y(1)-Y(0)|X=x]
\]

Họ mô tả S-/T-learner concepts, multiheaded architecture, weighted multiple outcomes, offline policy evaluation cho send-to-all, send-to-none và uplift policy.

Sau A/B test, Spotify báo significant retention improvement và rollout production. [SPOTIFY_UPLIFT]

## 6. Spotify — tách personalization khỏi experimentation

2026 Spotify mô tả contextual bandit như personalization system, không phải A/B testing replacement.

```text
ML stack → serve personalization
Experimentation stack → evaluate the whole system
```

Họ nhấn mạnh meaningful metrics có thể delayed nhiều tuần và bandits thường one-dimensional. [SPOTIFY_STACKS]

## 7. Meta Looper — general decision platform

Looper hỗ trợ classification, value estimation, sequence prediction, ranking, planning, supervised learning, reinforcement learning, real-time decisions và causal experiments/A-B evaluation. [META_LOOPER]

Pattern nên copy:

```text
Product code:
DECIDE(context)
FEEDBACK(decision_id, outcome)

Platform:
feature access
training
serving
policy
logging
evaluation
rollout
```

## 8. Pinterest — short-term downstream reward cho long-term retention

Paper 2026 mô tả retention signal là sparse/delayed/khó attribution. Họ dùng session-level behaviors xuất hiện sớm nhưng predictive của future retention làm downstream reward signals. [PINTEREST_REWARDS]

Mapping sang game:

```text
session_continue
progression_continue
D1_return
shop_revisit
reward_claim
```

## 9. Wildlife Studios — RL cho personalized IAP offers

Wildlife public Ray Summit deck với objective:

> Với một player tại một thời điểm, offer nào tối đa long-term profit?

Framing:

```text
Agent       = recommender
Environment = players
State       = player features
Action      = offer
Reward      = revenue
```

và tập trung vào best sequence of decisions thay vì short-sighted supervised recommendation. [WILDLIFE_RL]

## 10. Playtika — internal Monetization / LiveOps OS

Playtika filings mô tả các mảnh proprietary:

- Player Journey,
- Campaign Manager,
- Marketing Suite,
- AB Boost,
- offer automation/optimization,
- ML/AI,
- back-office tools,
- personalized player experience,
- real-time personalized game content. [PLAYTIKA_2025] [PLAYTIKA_SEC]

Đây là evidence mạnh rằng end-state của studio lớn thường là **internal operating system**, không chỉ một ML model.

## 11. Personalized difficulty là monetization-adjacent control

Peer-reviewed paper 2025 dùng player-level data + structural model để nghiên cứu personalized game difficulty trong mobile puzzle game và báo potential revenue uplift lớn. [SCIENCE_PERSONAL_DIFFICULTY]

Nghĩa là action space dài hạn không chỉ IAP/IAA; difficulty, progression, reward pacing và content cũng tác động LTV.

## Nguồn

- **[METICA_OVERVIEW] Metica — What is Metica?** — Primary vendor docs.  
  https://docs.metica.com/readme-1
- **[METICA_GETTING_STARTED] Metica — Getting Started / Offers & Eligibility Rules** — Primary vendor docs.  
  https://docs.metica.com/developer-guides/readme
- **[METICA_SMARTCONFIG] Metica — SmartConfigs UI** — Primary vendor docs.  
  https://docs.metica.com/uiguide/smart-config-ui
- **[METICA_API] Metica — REST API / Smart Config** — Primary vendor docs.  
  https://docs.metica.com/api/integration
- **[METICA_BUNDLES] Metica — Dynamic Adaptive Bundles** — Primary vendor blog.  
  https://metica.com/blog/earn-more-with-dynamic-adaptive-bundles
- **[FOMO_FIREBASE] Firebase — FOMO Games case study** — Primary platform case study.  
  https://firebase.google.com/case-studies/fomo-games
- **[HALFBRICK_FIREBASE] Firebase — Halfbrick personalization case study** — Primary platform case study.  
  https://firebase.google.com/case-studies/halfbrick-personalization
- **[GAMENEXA_FIREBASE] Firebase — GameNexa monetization case study** — Primary platform case study.  
  https://firebase.blog/posts/2020/12/gamenexa-studios-case-study
- **[POMELO_FIREBASE] Firebase — Pomelo Games case study** — Primary platform case study.  
  https://firebase.google.com/case-studies/pomelo-games
- **[FIREBASE_PERSONALIZATION] Firebase — Remote Config Personalization** — Primary product docs.  
  https://firebase.google.com/docs/remote-config/personalization/about
- **[FIREBASE_IAP] Firebase Codelab — Realtime on-device IAP optimization** — Primary technical tutorial.  
  https://firebase.google.com/codelabs/iap-optimization
- **[FIREBASE_IAP_REPO] Google Codelabs — firebase-iap-optimization** — Primary source repository.  
  https://github.com/googlecodelabs/firebase-iap-optimization
- **[KING_PAPER] Varelas et al. — Development of an End-to-end ML System with Application to IAP** — Primary research paper.  
  https://arxiv.org/abs/2412.12390
- **[DREAM11_PAPER] Agrawal et al. — Hierarchical Contextual Uplift Bandits for Catalog Personalization** — Primary research paper.  
  https://arxiv.org/abs/2601.14333
- **[SPOTIFY_UPLIFT] Spotify Engineering — ML to Target In-App Messaging** — Primary engineering blog.  
  https://engineering.atspotify.com/2023/6/experimenting-with-machine-learning-to-target-in-app-messaging
- **[SPOTIFY_STACKS] Spotify Engineering — Separate Tech Stacks for Personalization and Experimentation** — Primary engineering blog.  
  https://engineering.atspotify.com/2026/1/why-we-use-separate-tech-stacks-for-personalization-and-experimentation
- **[META_LOOPER] Meta AI — Looper optimization platform** — Primary engineering blog.  
  https://ai.meta.com/blog/looper-meta-ai-optimization-platform-for-engineers/
- **[PINTEREST_REWARDS] Wang et al. — Long-term User Engagement Optimization through Model-agnostic Downstream Rewards Learning (RecSys 2026)** — Primary research paper.  
  https://arxiv.org/abs/2607.14192
- **[WILDLIFE_RL] Wildlife Studios / Ray Summit — RL for IAP offer recommendations** — Primary conference deck.  
  https://speakerdeck.com/anyscale/using-reinforcement-learning-to-optimize-iap-offer-recommendations-in-mobile-games-emiliano-castro-vinicius-alves-and-felipe-antunes-wildlife-studios
- **[WILDLIFE_RAY] Anyscale — Wildlife Studios RL offer recommendation result** — Secondary event coverage.  
  https://www.01net.it/anyscale-hosts-second-annual-ray-summit-featuring-50-user-talks-on-building-scalable-machine-learning-applications/
- **[PLAYTIKA_2025] Playtika Holding Corp. — 2025 Annual Report** — Primary company filing.  
  https://investors.playtika.com/static-files/c43bf274-ddde-40b7-a959-086b74c69d91
- **[PLAYTIKA_SEC] Playtika Holding Corp. — SEC annual filing** — Primary regulatory filing.  
  https://www.sec.gov/Archives/edgar/data/1828016/000182801625000011/playtika-20241231.htm
- **[SCIENCE_PERSONAL_DIFFICULTY] Personalized content, engagement, and monetization in a mobile puzzle game** — Peer-reviewed research.  
  https://www.sciencedirect.com/science/article/pii/S0167718724000833
