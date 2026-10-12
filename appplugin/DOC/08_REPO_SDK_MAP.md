# 08 — Repo / SDK / Platform Map

## Phân loại

- **Brain / Decision reference** — logic chọn action theo user context.
- **Data / Control plane** — events, cohort, remote config, experimentation.
- **Execution** — ads, paywall, billing, mediation.
- **ML primitive** — causal inference, bandit, optimization.
- **Game backend** — state/runtime.

## 1. Brain / Decision references

### Metica
Closed platform, nhưng docs rất sát target:
- offers,
- eligibility,
- contextual bandits,
- Smart Config,
- ad frequency,
- offer timing,
- pricing,
- reward size,
- dynamic bundles.

Docs:
- https://docs.metica.com/readme-1
- https://docs.metica.com/uiguide/smart-config-ui
- https://docs.metica.com/api/integration

### Firebase IAP Optimization sample
Repo:
- https://github.com/googlecodelabs/firebase-iap-optimization

Đọc state/action/reward framing, data prep, training notebook, TFLite deployment, exploration.

### Wildlife Studios RL
Conference deck:
- https://speakerdeck.com/anyscale/using-reinforcement-learning-to-optimize-iap-offer-recommendations-in-mobile-games-emiliano-castro-vinicius-alves-and-felipe-antunes-wildlife-studios

## 2. Data / control plane

### Countly
- https://github.com/Countly/countly-server

### GrowthBook
- https://github.com/growthbook/growthbook

### Unleash
- https://github.com/Unleash/unleash

## 3. ML primitives

### CausalML
- https://github.com/uber/causalml

### EconML
- https://github.com/py-why/EconML

### Vowpal Wabbit
- https://vowpalwabbit.org/tutorials/contextual_bandits.html

### Google OR-Tools
- https://developers.google.com/optimization/cp/

## 4. IAA execution layer

### Google Mobile Ads Unity
- https://github.com/googleads/googleads-mobile-unity

### Google Mobile Ads Unity Mediation
- https://github.com/googleads/googleads-mobile-unity-mediation

### AppLovin MAX Unity
- https://github.com/AppLovin/AppLovin-MAX-Unity-Plugin

### AppLovin MAX Android
- https://github.com/AppLovin/AppLovin-MAX-SDK-Android

### ADMOST
- https://resources.admost.com/knowledge-base/overview/
- https://resources.admost.com/knowledge-base/how-does-waterfall-work-2/

## 5. IAP / subscription execution

### RevenueCat
- https://github.com/RevenueCat
- https://github.com/RevenueCat/purchases-unity

### Adapty
- https://adapty.io/docs/segments

### Superwall
- https://superwall.com/docs/dashboard/dashboard-campaigns/campaigns-audience
- https://superwall.com/docs/dashboard/dashboard-campaigns/campaign-rules

## 6. Presentation

### Storyly
- https://github.com/Netvent/storyly-sdk
- https://docs.storyly.io/docs/introduction

## 7. Game backend/runtime

### Nakama
- https://github.com/heroiclabs/nakama

## 8. Recommended code reading order

```text
1. firebase-iap-optimization
2. Countly server
3. CausalML
4. EconML
5. Vowpal Wabbit contextual bandits
6. OR-Tools CP-SAT
7. GrowthBook / Unleash
8. RevenueCat + MAX as actuator integration
```

## 9. Phần nên proprietary

```text
player feature definitions
decision-point taxonomy
causal labels
monetization policy
action constraints
reward definition
long-term value model
decision log
experiment design
```
