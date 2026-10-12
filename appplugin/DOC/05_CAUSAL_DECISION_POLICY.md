# 05 — Causal Decision Policy: Uplift, Bandits, OPE và RL

## 1. Ba câu hỏi khác nhau

### Prediction
\[
P(Y=1|X)
\]

### Recommendation
\[
\arg\max_a E[Y|X,A=a]
\]

### Causal decision
\[
\arg\max_a E[Y(a)-Y(0)|X]
\]

Monetization Brain nên tiến tới câu thứ ba.

## 2. Vì sao propensity có thể gây cannibalization?

Nếu high-propensity users vốn đã mua full price, targeting discount vào họ có thể làm conversion cao nhưng incremental revenue thấp hoặc âm.

Do đó cần baseline/no-action arm.

## 3. Uplift / CATE

\[
CATE(x)=E[Y(1)-Y(0)|X=x]
\]

Spotify productionized causal eligibility cho in-app messaging bằng uplift modeling và independent A/B validation. [SPOTIFY_UPLIFT]

Open-source:

- CausalML — uplift/CATE, multiple treatments, campaign targeting/personalized engagement. [CAUSALML]
- EconML — heterogeneous treatment effect estimation, policy learning. [ECONML]

## 4. Contextual bandit

```text
observe context X_t
choose action A_t
observe reward for chosen action
update policy
```

Vowpal Wabbit docs là reference open-source tốt. [VW_CB]

Firebase IAP codelab mapping:

```text
State  = user + current session
Action = IAP offer
Reward = offer value if accepted, else 0
```

[FIREBASE_IAP]

## 5. Exploration vs exploitation

Epsilon-greedy:

```text
with probability ε:
  random valid action
otherwise:
  best estimated action
```

Google codelab dùng 30% như ví dụ exploration. [FIREBASE_IAP]

Production cần safe exploration, action constraints, holdout và exploration caps cho high-risk actions.

## 6. Contextual uplift bandit

Dream11 framework kết hợp hierarchical context + contextual bandit + uplift để transfer policy và handle sparse/cold-start contexts. [DREAM11_PAPER]

\[
a^*(x)=\arg\max_a \tau_a(x)
\]

với:

\[
\tau_a(x)=E[Y(a)-Y(control)|X=x]
\]

## 7. Hierarchical context

```text
ALL USERS
  └─ Country
      └─ Platform
          └─ Lifecycle
              └─ Monet Type
                  └─ Behavioral State
```

Nếu leaf sparse, fallback/shrink toward parent.

## 8. Multi-objective reward

\[
R =
\lambda_1 \Delta Revenue +
\lambda_2 \Delta Retention +
\lambda_3 \Delta Engagement -
\lambda_4 \Delta Churn -
\lambda_5 EconomyDamage
\]

Dream11 đưa revenue + engagement + retention vào uplift reward framing. [DREAM11_PAPER]

Spotify cảnh báo bandits thường one-dimensional và business metrics delayed. [SPOTIFY_STACKS]

## 9. Delayed reward

Pinterest đề xuất học downstream proxy rewards: early behaviors predictive của future retention. [PINTEREST_REWARDS]

Game mapping:

```text
session_continue
progression_continue
D1_return
shop_revisit
offer_reopen
reward_use
```

\[
\hat{LTV}_{30} = f(short\_term\ outcomes)
\]

\[
R_t = ImmediateRevenue + \lambda\Delta\hat{LTV}_{30}
\]

## 10. Off-policy evaluation

Minimum decision log:

```text
decision_id
context
candidate_actions
chosen_action
chosen_action_probability
policy_version
outcomes
```

Methods:

- IPS,
- SNIPS,
- Doubly Robust,
- replay/rejection sampling tùy setting.

Spotify mô tả offline policy evaluation trước online rollout trong uplift messaging case. [SPOTIFY_UPLIFT]

## 11. Sequential policy / RL

Greedy:

\[
a_t=\arg\max E[R_t]
\]

Sequential:

\[
\pi^*=\arg\max_\pi E[\sum_t \gamma^t R_t]
\]

Wildlife Studios public RL framing cho IAP offer recommendation và long-term decision sequence. [WILDLIFE_RL]

## 12. Vì sao không bắt đầu bằng RL?

RL cần exploration, reliable state, stable reward, attribution, enough action coverage, OPE và safety constraints.

Production path:

```text
Rules
→ Segmentation
→ Propensity
→ Timing
→ Uplift
→ Contextual Bandit
→ Contextual Uplift Bandit
→ Sequential RL
```

## 13. Experimental architecture

```text
PERSONALIZATION POLICY
        ↓
       users
        ↓
 outcomes

INDEPENDENT EXPERIMENT:
Control static policy
vs
Current policy
vs
Candidate policy
```

Guardrails:

```text
D1/D7/D30 retention
payer conversion
IAP cannibalization
ad exits
crash/performance
economy inflation
user experience
```

## Nguồn

- **[CAUSALML] Uber — CausalML** — Primary source repository.  
  https://github.com/uber/causalml
- **[ECONML] PyWhy / Microsoft Research — EconML** — Primary source repository.  
  https://github.com/py-why/EconML
- **[VW_CB] Vowpal Wabbit — Contextual Bandits tutorial** — Primary docs.  
  https://vowpalwabbit.org/tutorials/contextual_bandits.html
- **[DREAM11_PAPER] Agrawal et al. — Hierarchical Contextual Uplift Bandits for Catalog Personalization** — Primary research paper.  
  https://arxiv.org/abs/2601.14333
- **[SPOTIFY_UPLIFT] Spotify Engineering — ML to Target In-App Messaging** — Primary engineering blog.  
  https://engineering.atspotify.com/2023/6/experimenting-with-machine-learning-to-target-in-app-messaging
- **[SPOTIFY_STACKS] Spotify Engineering — Separate Tech Stacks for Personalization and Experimentation** — Primary engineering blog.  
  https://engineering.atspotify.com/2026/1/why-we-use-separate-tech-stacks-for-personalization-and-experimentation
- **[PINTEREST_REWARDS] Wang et al. — Long-term User Engagement Optimization through Downstream Rewards** — Primary research paper.  
  https://arxiv.org/abs/2607.14192
- **[WILDLIFE_RL] Wildlife Studios / Ray Summit — RL for IAP offer recommendations** — Primary conference deck.  
  https://speakerdeck.com/anyscale/using-reinforcement-learning-to-optimize-iap-offer-recommendations-in-mobile-games-emiliano-castro-vinicius-alves-and-felipe-antunes-wildlife-studios
- **[FIREBASE_IAP] Firebase Codelab — Realtime on-device IAP optimization** — Primary technical tutorial.  
  https://firebase.google.com/codelabs/iap-optimization
