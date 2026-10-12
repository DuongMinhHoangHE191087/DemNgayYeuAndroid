# 11 — Personalized Pricing & LTV Modeling (Mở rộng)

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Hai mảng mà knowledge base 00–10 còn mỏng: (A) price như một decision variable hạng nhất, (B) LTV modeling làm nền cho reward shaping và guardrail.

## Evidence labels

- **[Primary research]**: paper peer-reviewed / arXiv từ nhóm nghiên cứu thật.
- **[Primary docs]**: docs chính thức của vendor/platform.
- **[Case study — self-reported]**: số do vendor/studio tự công bố, không phải replication độc lập.
- **[Secondary]**: tổng hợp báo chí/analyst.

---

## A. Price là decision variable

### A.1 Luận điểm trung tâm

Docs 00–10 định nghĩa action tuple có `Price`, nhưng chưa có nền tảng phương pháp cho việc **tối ưu price bằng bandit/causal**. Nghiên cứu mở rộng cho thấy đây là một lĩnh vực nghiên cứu chín muồi:

1. Price không chỉ là tham số của offer — nó là **arm** của bài toán quyết định.
2. Elasticity là **heterogeneous** — mỗi segment phản ứng khác nhau với cùng một mức giá.
3. Có cả dòng nghiên cứu discrete-arm (MAB trên price ladder) lẫn continuous-arm (GP bandit học demand curve).

### A.2 Các paper nền tảng

#### Ban & Keskin (2021) — Personalized dynamic pricing với ML [Primary research]

- *Management Science* 67(9), 5549–5568.
- https://pubsonline.informs.org/doi/10.1287/mnsc.2020.3680
- Seller định giá theo từng customer với covariates giàu; elasticity không đồng nhất. Kết hợp demand estimation + quyết định giá trong setting high-dimensional; regret O(√(sT)).
- **Đây là framework chuẩn cho "price là contextual bandit arm với per-user features".**
- Dòng follow-up: contextual dynamic pricing với neural network (arXiv:2412.00920), biến thể privacy-preserving, primal-dual learning (arXiv:1812.09234).

#### Misra & Schwartz (2019) — MAB pricing trong F2P game [Primary research]

- *Marketing Science* 38(2), 226–252.
- https://pubsonline.informs.org/doi/10.1287/mksc.2018.1129
- **Paper sát use-case nhất**: áp MAB trực tiếp vào pricing "starter pack" của một game mobile F2P; price là discrete arms; mở rộng MAB bằng partial identification of demand.
- Không đọc được chi tiết thuật toán (paywall INFORMS) — xác nhận tồn tại và venue qua nhiều nguồn thứ cấp.

#### Nonparametric Pricing Bandits (2025) [Primary research]

- *Marketing Science*, DOI 10.1287/mksc.2022.0247.
- https://pubsonline.informs.org/doi/10.1287/mksc.2022.0247
- GP bandit đơn điệu (monotonic) dựa trên microeconomics để học demand curve; price liên tục; dùng informational externality (giá lân cận inform lẫn nhau).
- **Dùng khi muốn tối ưu price liên tục thay vì discrete ladder.**

#### Lundy et al. (AAAI 2024) — Pay to (Not) Play: Monetizing Impatience [Primary research]

- https://ojs.aaai.org/index.php/AAAI/article/view/28846
- Lý thuyết pricing cho "skip/timer" trong game mobile: **revenue cao, utility cao và purchase rate thấp có thể cùng tồn tại** tại đúng price point — giá skip tối ưu thường là mức rất ít người mua nhưng maximize revenue.
- **Hệ quả:** price tối ưu cho IAP không nhất thiết là giá maximize conversion.

#### Ascarza, Netzer, Runge (IJRM 2025) — Personalized Game Design [Primary research]

- Field experiment ngẫu nhiên quy mô lớn trong F2P game: cá nhân hóa cả **difficulty lẫn pricing**.
- https://www.columbia.edu/~on2110/Papers/Personalized_games.pdf
- Phát hiện chính: **giảm giá sinh lợi đáng kể cả ngắn hạn lẫn dài hạn** với player nhạy giá; game dễ hơn một chút cho struggling player tăng engagement/retention/LTV. Giảm difficulty có thể **thay thế** IAP (cho thêm resource → mua ít hơn nhưng retention tốt hơn).
- **Hệ quả kiến trúc:** brain phải đồng tối ưu price và gameplay experience, không chỉ price.

### A.3 Ai đang làm price personalization trong thực tế

| Vendor/Studio | Cách làm | Evidence |
|---|---|---|
| **Wappier** | ML + econometric model tự chỉnh IAP price theo quốc gia liên tục; có promotional pricing per-user | [Vendor — self-reported] https://wappier.com/global-pricing/ |
| **SuperScale** | Regional pricing: giảm 50% giá ở Tier-3 → conversion 0.79% → 1.37% (+73%) | [Case study — self-reported] https://superscale.com/resources/boosting-mobile-game-revenue-with-regional-pricing-a-case-study |
| **Adapty / RevenueCat / Superwall** | Paywall price experimentation (A/B trên price point, trial, cadence, anchor) | [Primary docs] https://adapty.io/paywall-ab-testing/ ; https://www.revenuecat.com/state-of-subscription-apps/ |
| **Phiture** | Westendorp Price Sensitivity Meter + Firebase Remote Config cho SKU | [Vendor docs] https://phiture.com/mobilegrowthstack/app-pricing-optimization-guide/ |
| **Qonversion** | Subscription infra + A/B | [Vendor docs] https://qonversion.io/ |
| **Crazy Panda / GameAnalytics** | Segmented personalized offers; ML dự đoán currency preference | [Case study — self-reported] https://www.gameanalytics.com/blog/personalizing-offers-for-in-app-monetization-crazy-panda |

**Không tìm thấy studio game nào công bố chi tiết hệ thống price personalization in-house** — phần lớn là bí mật thương mại. Đây vừa là white space vừa là lý do phải dựa vào research academic.

### A.4 PPP / localized pricing — lớp bắt buộc trước mọi personalization

- Định giá theo **purchasing power parity**, không chỉ FX. Không có PPP pricing có thể bỏ mất tới ~85% user tiềm năng ở vùng nhạy giá.
- Tier phổ biến: T1 (US/EU/JP/AU, full price), T2 (Đông Âu, LATAM: −40–60%), T3 (Ấn Độ, SEA, châu Phi: −60–80%).
- Tool: PricePush, Mirava, PPP Price Planner, Google Play "Localize Price".
- **Với thị trường Việt Nam/SEA (doc 15): PPP pricing là quick-win lớn nhất trước khi làm bất kỳ bandit nào.**

### A.5 Rủi ro pháp lý và fairness của personalized pricing

- **CMU Tepper (2025):** AI personalized pricing có thể làm giảm elasticity qua ranking → giảm động lực hạ giá → hại người tiêu dùng. https://www.cmu.edu/tepper/news/stories/2025/0602-ai-driven-personalized-pricing-may-not-help-consumers
- **EU Digital Fairness Act (đề xuất, dự kiến 2026–2027):** nhắm vào personalized pricing dựa trên tracking/profiling, drip pricing, dynamic pricing thiếu minh bạch. Hiện luật EU chưa cấm dynamic pricing nhưng sẽ có nghĩa vụ transparency. https://www.digital-fairness-act.com/
- **Fairness-constrained pricing:** paper 2026 (MDPI) về DRL có shield cho fairness constraint — fairness phải là **hard guardrail**, không phải soft penalty. https://www.mdpi.com/2227-7390/14/4/600
- Chi tiết hơn ở doc 13.

### A.6 Hệ quả cho action schema

```text
IAP_OFFER action cần thêm các dimension hạng nhất:
  price_tier        : nấc thang giá cụ thể ($0.99/$2.99/$4.99/$9.99/$19.99…)
  discount_depth    : 0/20/40/60/80%
  offer_structure   : trial_duration, billing_cadence, anchor_price, display_price
  region_pricing    : PPP tier override
  ladder_position   : first-purchase / escalation / whale
```

- First-purchase offer nên là **action type riêng** với discount sâu (50–80%) vì giá trị chuyển đổi "non-payer → payer" không tuyến tính.
- Skip/timer monetization là action type riêng với price arm (Lundy et al.).
- Feature cần thêm cho elasticity: lịch sử phản ứng discount, price point đã mua, purchasing-power proxy (geo, device, tuổi account), time-since-last-purchase, purchase velocity.

---

## B. LTV modeling cho decisioning

### B.1 Ba họ mô hình

#### Họ xác suất cổ điển (baseline)

- **Pareto/NBD** (1987), **BG/NBD** (Fader/Hardie/Lee 2005), **Gamma-Gamma**: lý thuyết rõ, diễn giải được; BG/NBD có closed-form, phù hợp scoring.
- **Hạn chế với game:** (1) non-stationarity (event, season) phá giả định dừng; (2) zero-inflation cực lớn (95–98% không trả tiền); (3) heavy-tail power-law; (4) Gamma-Gamma giả định frequency ⊥ monetary — **sai với game** vì whale mua vừa nhiều lần vừa pack to; (5) đa luồng doanh thu (IAP + ad + sub).
- Implement Bayesian có uncertainty: **PyMC-Marketing** — https://www.pymc-marketing.io/en/stable/guide/clv/clv_intro.html — posterior samples dùng được cho Thompson Sampling trên LTV.

#### Họ deep probabilistic

- **ZILN (Google, arXiv:1912.07753):** mixture zero-mass + lognormal; 2 head (P(revenue>0), revenue|>0); hơn MSE loss 11.4%; đánh giá bằng normalized Gini. Code: https://github.com/google/lifetime_value — **đây là backbone mặc định nên bắt đầu.**
- **CALTV (arXiv 2510.08281, 2025):** dự đoán **số giao dịch theo từng price tier** rồi cộng lại thành LTV — tránh giả định lognormal; AULC +1.4%, bias top-80% giảm >40% so với ZILN; **online ROI +20%** khi deploy. Cách "phân rã theo price tier" khớp tự nhiên với brain cần đánh giá action ở nhiều mức giá.
- **CLV với uncertainty (arXiv 2411.15944):** uncertainty của LTV estimate nên điều khiển exploration — uncertainty cao → action thu thập thông tin.
- **Chen et al. 2018 (arXiv 1811.12799):** deep tốt hơn parametric về predictive nhưng parametric diễn giải được; hybrid là lựa chọn thực dụng.

#### Họ survival (liên kết doc 01 Phase 4)

- **Survival ensembles cho churn game** (arXiv 1710.02264): conditional inference survival ensemble hơn Cox regression (IBS 0.158 vs 0.169); dự đoán **khi nào** churn chứ không chỉ **có/không** → can thiệp đúng thời điểm.
- **Churn cho whale** (HAL hal-04632443): cần head/model riêng cho high-value tier vì churn whale đắt không cân xứng.
- **Google Play LTV series** (Ignacio Monereo): cách tính LTV cho 5 monetization model (IAP, sub, ad, hybrid, paid) — https://medium.com/googleplaydev/predicting-your-apps-monetization-future-27180e82ae34

### B.2 pLTV — early segmentation và bidding

- pLTV chuẩn: dự đoán revenue D180/D365 từ hành vi 24h đầu. Tín hiệu sớm: tutorial completion, payment attempt (kể cả fail), session frequency, progression speed.
- **Tenjin pLTV** [Primary docs]: 78 signals, kết hợp IAP + ad mediation LTV, dùng làm bid value. https://tenjin.com/docs/predicted-lifetime-value-pltv/
- pLTV bidding trên Meta/Google Value Optimization: ROAS report 30–50% tốt hơn install-only [Case study — self-reported].
- **AppLovin AXON** là vòng lặp kín gần nhất với full brain hiện có: UA bidding ↔ pLTV ↔ MAX monetization ↔ feedback. Điểm khác biệt cốt lõi: AXON tối ưu giá trị cho ad platform, không tối ưu LTV cho player/studio. [Company filing + phân tích] https://www.klover.ai/applovin-ai-strategy-analysis-of-dominance-in-advertising/
- **Cảnh báo (Churney):** pLTV phi-causal gãy dưới distribution shift (đổi privacy framework, đổi source). pLTV dùng cho quyết định nên hướng causal. https://churney.io/blogs/predictive-ltv-for-ua-optimization-go-causal-or-go-home

### B.3 LTV dưới ràng buộc đo lường (SKAN / Privacy Sandbox)

- SKAN 4.x: 6-bit conversion value, aggregate-only, lockout delay → pLTV phải chuyển sang **cohort-based**, thiết kế conversion-value schema là bài toán thông tin (64 bucket encode được gì).
- Android Privacy Sandbox đã bị **khai tử 10/2025** — GAID vẫn còn; chi tiết ở doc 13.
- Chiến thuật: first-party server-side events làm nguồn train chính; MMM bổ trợ cho aggregate measurement.

### B.4 LTV feed vào decision policy — reward contract

Reward contract mở rộng từ doc 05:

```text
R(decision) =
    r_immediate                     (revenue trực tiếp của action)
  + γ·[LTV(s') − LTV(s)]            (potential-based shaping, Φ = LTV model)
  − λ_churn·P(churn|action)         (penalty churn)
  − λ_fair·FairnessViolation        (hard guardrail, EU DFA)
  + bonus_info·ΔUncertaintyReduction (khuyến khích explore nơi elastic)
```

- Potential-based shaping F(s,s′) = γΦ(s′) − Φ(s) giữ optimal policy bất biến — an toàn về lý thuyết.
- Khi reward thưa (user chưa phát sinh revenue): mượn kỹ thuật **ROLeR** (kNN reward từ user tương tự, arXiv 2407.13163) và offline RL cho sparse reward (DPO loss, arXiv 2506.23090).
- **Gap xác nhận được:** không tìm thấy paper/blog nào mô tả production RL policy cho monetization in-game driven by LTV reward shaping — các ví dụ đều từ recommender/ad bidding lân cận. Đây là white space thật.

### B.5 Roadmap tích hợp pricing + LTV

```text
Bước 1 (ngay): PPP/regional pricing theo tier — uplift 10–50% theo case SuperScale,
               không cần ML.
Bước 2: ZILN (hoặc CALTV) làm LTV backbone; price_tier thành action parameter.
Bước 3: Thompson Sampling trên price arms theo segment (khung Misra & Schwartz);
        first-purchase discount depth làm bandit riêng.
Bước 4: Contextual bandit price với player features (khung Ban & Keskin);
        survival churn → retention pricing đúng thời điểm.
Bước 5: GP bandit cho price liên tục (paper 2025); đồng tối ưu price × difficulty
        (Ascarza et al.); fairness hard constraints.
Bước 6: Offline RL với LTV-delta reward shaping (ROLeR-style); causal pLTV.
```

## Không xác minh được (ghi rõ)

1. Chi tiết thuật toán MAB và số uplift cụ thể trong Misra & Schwartz (paywall).
2. Mức giảm giá cụ thể trong field experiment Ascarza et al. (PDF không fetch được).
3. Phương pháp ML nội bộ của Wappier (self-reported).
4. Kiến trúc AXON chi tiết (proprietary).
5. Studio game cụ thể chạy price personalization in-house ngoài Crazy Panda case.

## Nguồn chính

- Ban & Keskin 2021 — https://pubsonline.informs.org/doi/10.1287/mnsc.2020.3680
- Misra & Schwartz 2019 — https://pubsonline.informs.org/doi/10.1287/mksc.2018.1129
- Nonparametric Pricing Bandits 2025 — https://pubsonline.informs.org/doi/10.1287/mksc.2022.0247
- Lundy et al. AAAI 2024 — https://ojs.aaai.org/index.php/AAAI/article/view/28846
- Ascarza, Netzer, Runge IJRM 2025 — https://www.columbia.edu/~on2110/Papers/Personalized_games.pdf
- Google ZILN — https://arxiv.org/abs/1912.07753 / https://github.com/google/lifetime_value
- CALTV — https://arxiv.org/html/2510.08281v1
- CLV uncertainty — https://arxiv.org/html/2411.15944
- Chen et al. 2018 — https://arxiv.org/pdf/1811.12799
- PyMC-Marketing — https://www.pymc-marketing.io/en/stable/guide/clv/clv_intro.html
- Survival ensembles churn — https://ar5iv.labs.arxiv.org/html/1710.02264
- Tenjin pLTV — https://tenjin.com/docs/predicted-lifetime-value-pltv/
- AXON analysis — https://www.klover.ai/applovin-ai-strategy-analysis-of-dominance-in-advertising/
- Churney causal pLTV — https://churney.io/blogs/predictive-ltv-for-ua-optimization-go-causal-or-go-home
- ROLeR — https://arxiv.org/html/2407.13163v1
- SuperScale regional pricing — https://superscale.com/resources/boosting-mobile-game-revenue-with-regional-pricing-a-case-study
- CMU AI pricing — https://www.cmu.edu/tepper/news/stories/2025/0602-ai-driven-personalized-pricing-may-not-help-consumers
- EU Digital Fairness Act — https://www.digital-fairness-act.com/
- Fairness-constrained DRL pricing — https://www.mdpi.com/2227-7390/14/4/600
- AppMagic Steam pricing — https://appmagic.rocks/research/steam-pricing-research
- AppsFlyer hybrid monetization 2024 — https://www.appsflyer.com/resources/reports/app-marketing-monetization-report-2024/
