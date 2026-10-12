# 33 — Player Representation & Segmentation: Toàn cảnh mô hình hoá người chơi cho Monetization Decision Engine

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Cách biểu diễn (represent) và phân nhóm (segment) người chơi để một decision engine ra quyết định monetization tốt hơn. Bao gồm clustering và các failure mode của nó, latent state models (mở rộng trực tiếp belief state 4 trạng thái ở doc 24), representation learning, sequence architectures, temporal point processes, cold start, concept drift, interpretability cho ops, mapping segment → policy, và cách chứng minh một segmentation là **hữu ích** chứ không chỉ **sạch về thống kê**. Doc này là tầng "modeling reference" nằm giữa doc 04 (feature dictionary) và doc 05/28 (causal policy, bandit/RL).

---

## 0. Tóm tắt điều hành

Ba luận điểm chi phối toàn bộ doc này:

1. **Segmentation không phải mục tiêu, nó là một compression của state space.** Mọi lựa chọn biểu diễn (k-means cluster id, HMM state posterior, embedding 64 chiều) đều chỉ là cách nén `H_t` (toàn bộ lịch sử tương tác) thành một thứ đủ nhỏ để policy học được với lượng dữ liệu hữu hạn. Tiêu chí đánh giá đúng là **policy improvement**, không phải silhouette score.

2. **Cấu trúc thời gian là tín hiệu bị bỏ phí nhiều nhất.** Đa số hệ thống game analytics dừng ở snapshot features (aggregate 7d/30d). Thứ thực sự trả lời câu hỏi "khi nào nên offer" là **temporal point process** — intensity function của session/purchase arrival, và self-excitation sau một purchase. Đây là mảng underused nhất và có ROI cao nhất cho SDK này.

3. **Ops team chỉ tin cái họ hiểu được.** Một embedding 128 chiều có AUC cao hơn nhưng không đặt tên được sẽ không bao giờ được bật lên production trong một Control Plane do product manager vận hành. Kiến trúc đúng là **dual representation**: một tầng liên tục cho policy, một tầng rời rạc có tên cho con người.

```text
TẦNG BIỂU DIỄN (3 lớp, cùng tồn tại, không thay thế nhau)

  L1  Discrete segment      →  ops-facing, có tên, có rule override
      (cluster id, LCA class, "Whale-at-risk")
      dùng cho: dashboard, guardrail, config targeting

  L2  Latent belief state   →  policy-facing, có ngữ nghĩa
      (HMM posterior b_t over K states — doc 24)
      dùng cho: POMDP policy, context vector cho bandit

  L3  Continuous embedding  →  model-facing, không diễn giải trực tiếp
      (sequence encoder output, 32–128d)
      dùng cho: LTV/churn head, retrieval, similarity, cold-start prior

  L1 nên được DERIVE từ L2/L3 (naming + profiling), không học độc lập.
```

---

## 1. Clustering: những gì hoạt động và những gì đánh lừa

### 1.1 k-means trên behavioral features — và vì sao Euclidean distance nói dối

k-means là mặc định của mọi game analytics team vì rẻ và có sẵn. Nó cũng là nguồn của nhiều segmentation vô nghĩa nhất. Các failure mode cụ thể trên dữ liệu game:

```text
FAILURE MODE 1 — Scale dominance
  revenue_30d ∈ [0, 5000]      variance ~10^5
  sessions_30d ∈ [0, 60]       variance ~10^2
  → distance bị revenue chi phối hoàn toàn
  → "cluster" thu được chỉ là revenue decile được đổi tên

FAILURE MODE 2 — Heavy tail
  Phân phối spend trong game là power-law/lognormal.
  Euclidean distance trên raw spend đặt whale 5000$ và whale 500$
  xa nhau hơn F2P 0$ và minnow 5$ — sai về mặt hành vi.
  → log1p transform, hoặc rank/quantile transform trước khi cluster.

FAILURE MODE 3 — Correlated features = implicit weighting
  sessions_7d, playtime_7d, levels_7d, events_7d gần như collinear.
  Đưa cả 4 vào = nhân trọng số "engagement" lên 4 lần so với
  "monetization" chỉ có 1 feature.
  → whiten (PCA/ZCA) hoặc chọn 1 đại diện mỗi nhóm tương quan.

FAILURE MODE 4 — Compositional data
  Tỷ lệ thời gian ở các game mode (p1..pk, sum=1) nằm trên simplex.
  Euclidean trên simplex sai; cần CLR/ILR transform (Aitchison geometry).

FAILURE MODE 5 — Tenure confound (nghiêm trọng nhất trong game)
  Hầu hết behavioral aggregate tương quan mạnh với account_age.
  k-means sẽ tự tin trả về "cluster" = lifecycle stage.
  → cluster theo RATE (per active day) hoặc cluster TRONG cohort tenure.

FAILURE MODE 6 — Spherical prior
  k-means giả định cluster đẳng hướng, cùng bán kính, cùng cỡ.
  Player base có 1 cluster khổng lồ (F2P dormant) + đuôi nhỏ.
  k-means sẽ CHẺ cluster lớn và GỘP các cluster nhỏ.
```

Kết luận thực hành: nếu vẫn dùng k-means, pipeline bắt buộc là `log1p/quantile transform → per-active-day normalization → correlation pruning → standardize → (optional) PCA whiten → k-means++ với nhiều restart`. Bỏ qua bất kỳ bước nào thì kết quả gần như chắc chắn là lifecycle stage hoặc revenue decile được đội lốt behavioral segment. [Design synthesis]

### 1.2 Các họ thuật toán và khi nào dùng

| Method | Giả định | Mạnh trên game data khi | Yếu điểm |
|---|---|---|---|
| **k-means** | cluster cầu, cùng cỡ | cần baseline nhanh, feature đã chuẩn hoá tốt | tất cả failure mode ở 1.1 |
| **GMM (EM)** | mixture Gaussian, covariance tự do | cần **soft assignment** (probability thuộc cluster) và cluster hình elip | dễ overfit với full covariance; cần regularize |
| **DBSCAN** | mật độ, có noise | tìm nhóm bất thường (bot, fraud, exploit) | 1 tham số eps toàn cục — hỏng khi mật độ không đều (đúng với game) |
| **HDBSCAN** | mật độ phân cấp | như DBSCAN nhưng mật độ biến thiên; trả về "noise" tường minh | không ép mọi user vào cluster (đôi khi là ưu điểm) |
| **Hierarchical (Ward)** | merge tham lam | cần dendrogram để ops chọn độ mịn (4 nhóm hay 12 nhóm) | O(n²) bộ nhớ; cần sample |
| **Archetypal Analysis** | mỗi user = convex combination của các archetype cực trị | **diễn giải cực tốt**: archetype là "người chơi thuần khiết" ở biên | tối ưu khó hơn, nhạy với outlier |
| **NMF** | phần tử không âm, additive parts | count data (số lần dùng feature, số lần xem ad) → "parts-based" topic của hành vi | không có xác suất; cần chọn rank |

**Soft assignment quan trọng hơn người ta nghĩ.** Với decision engine, việc biết "user này 60% Grinder / 40% Spender" là input tốt hơn hẳn nhãn cứng: nó cho phép policy blend, và nó chính là dạng belief vector tương thích với doc 24. GMM, NMF, và Archetypal Analysis đều cho soft membership; k-means và DBSCAN thì không.

**Archetypal Analysis đáng chú ý riêng cho game.** Thay vì tìm centroid (người chơi trung bình của nhóm), AA tìm các đỉnh của convex hull — tức là các **hành vi thuần** — rồi biểu diễn mọi người chơi là hỗn hợp của chúng. Ops team hiểu "70% hardcore raider + 30% casual collector" dễ hơn "cluster 4". Drachen và cộng sự đã áp dụng cụ thể hướng này (bao gồm Simplex Volume Maximization, một biến thể AA) trên telemetry của Tera, Battlefield 2: BC2 và sau đó là Destiny ở quy mô hàng triệu người chơi. [Primary research]

### 1.3 Cluster-stability validation: làm sao biết cluster là thật

Đây là phần bị bỏ qua nhiều nhất. Mọi thuật toán clustering **luôn** trả về cluster, kể cả trên nhiễu Gaussian thuần. Cần ba lớp kiểm chứng:

```text
LỚP 1 — Internal validity (rẻ, yếu)
  Silhouette s(i) = (b(i) - a(i)) / max(a(i), b(i))
    a(i) = khoảng cách TB trong cluster
    b(i) = khoảng cách TB tới cluster gần nhất
  Cảnh báo: silhouette thưởng cho cluster cầu → thiên vị k-means.
  Silhouette cao KHÔNG chứng minh cluster có ý nghĩa kinh doanh.

  Davies–Bouldin, Calinski–Harabasz: cùng hạng, cùng thiên vị.

LỚP 2 — So với null (đúng câu hỏi hơn)
  Gap statistic (Tibshirani, Walther & Hastie 2001):
    Gap(k) = E*[log W_k] - log W_k
      W_k    = within-cluster dispersion trên dữ liệu thật
      E*[·]  = kỳ vọng trên B mẫu từ reference null distribution
               (uniform trên bounding box, hoặc uniform trên PCA-aligned box)
    Chọn k nhỏ nhất sao cho Gap(k) >= Gap(k+1) - s_{k+1}

  Ý nghĩa: "cấu trúc này có vượt qua thứ tôi thu được từ dữ liệu
  KHÔNG có cụm nào không?" Đây mới là câu hỏi đúng.

LỚP 3 — Stability (mạnh nhất, bắt buộc cho production)
  Bootstrap / subsample stability:
    for b in 1..B:
      D_b  = subsample 80% users
      C_b  = cluster(D_b)
      đo agreement giữa C_b và C_full trên phần giao
      (Adjusted Rand Index, hoặc Jaccard theo từng cluster)
    Rule of thumb thực hành: Jaccard TB của một cluster
      > 0.75  → cluster ổn định, dùng được
      0.6-0.75 → mẫu hình có thật nhưng ranh giới mờ
      < 0.6   → không tái lập, KHÔNG đặt tên, KHÔNG đưa vào ops
    (Ngưỡng này là heuristic phổ biến trong literature về
     bootstrap cluster stability, không phải hằng số phổ quát.)

  Temporal stability (đặc thù game, quan trọng hơn cả bootstrap):
    cluster trên tuần W, gán lại trên tuần W+4 với CÙNG model
    → phân phối cluster size có drift không?
    → tỷ lệ user đổi cluster có vượt mức kỳ vọng hành vi không?
    Nếu 60% user nhảy cluster sau 4 tuần, "segment" của bạn
    thực chất là một state biến thiên nhanh → nên mô hình hoá
    bằng HMM (mục 2), không phải clustering tĩnh.
```

Ba câu hỏi mà mọi segmentation phải trả lời được trước khi đưa vào Control Plane:
1. Nó có vượt qua null reference không? (gap statistic)
2. Nó có tái lập trên subsample và trên thời gian không? (bootstrap + temporal ARI)
3. Nó có **dự báo được thứ ta quan tâm** mà feature đơn lẻ không dự báo được không? (mục 10)

Nếu câu 3 fail, hai câu đầu là vô nghĩa. [Design synthesis]

---

## 2. Latent state models — mở rộng trực tiếp belief state của doc 24

Doc 24 định nghĩa một belief state rời rạc 4 trạng thái với posterior `b_t(z) = P(z | H_t)`. Đây chính là một **Hidden Markov Model** ở dạng tối giản. Mục này mở rộng ra toàn bộ họ mô hình.

### 2.1 HMM cho chuỗi trạng thái người chơi

```text
Sinh mô hình:
  z_t ∈ {1..K}                     latent player state
  z_1  ~ π                          initial distribution
  z_t  ~ A[z_{t-1}, ·]              transition matrix K×K
  o_t  ~ B(· | z_t)                 emission (observation model)

Observation o_t trong monetization context (per-session vector):
  session_length, levels_attempted, fail_rate, currency_spent,
  store_open, offer_view, offer_purchase, ad_watch, ad_skip,
  social_action, time_since_prev_session

Emission thực tế nên là product of heterogeneous likelihoods:
  B(o|z) = Poisson(n_sessions | λ_z)
         × Bernoulli(purchase | p_z)
         × LogNormal(session_len | μ_z, σ_z)
         × Categorical(dominant_activity | θ_z)
  (naive-Bayes emission — sai về mặt độc lập nhưng ổn định và
   dễ fit hơn full multivariate emission rất nhiều)

Inference:
  forward–backward → γ_t(z) = P(z_t = z | o_{1:T})   [smoothing, offline]
  forward only     → α_t(z) ∝ P(z_t = z | o_{1:t})   [filtering, ONLINE]

Runtime SDK chỉ cần FORWARD recursion:
  α_t(z) ∝ B(o_t | z) · Σ_{z'} A[z', z] · α_{t-1}(z')
  → O(K²) mỗi update, K=4..8 → chi phí không đáng kể trên device.
  Đây là lý do belief-state engine của doc 24 deploy được on-device.
```

Điểm mạnh cho monetization: HMM cho ra **posterior**, không phải nhãn cứng — nghĩa là nó tự động biểu diễn sự không chắc chắn. Một user vừa cài đặt có `b_t` gần prior; một user có 30 phiên có `b_t` sắc nhọn. Policy có thể dùng chính entropy của `b_t` như một tín hiệu "tôi biết bao nhiêu về người này" (tie thẳng vào explore/exploit của doc 28).

### 2.2 Hidden Semi-Markov Model (HSMM) — sửa lỗi lớn nhất của HMM

HMM có một giả định sai nghiêm trọng với dữ liệu game: **duration của một state phân phối hình học (geometric)**, tức `P(ở lại thêm d bước) = a_ii^d`. Điều này ngụ ý "hazard rate không đổi" — xác suất rời trạng thái "engaged" hôm nay giống hệt ngày thứ 60. Thực tế hành vi người chơi không như vậy: có honeymoon period, có ngưỡng chán, có cliff sau khi hết content.

```text
HSMM: mỗi state z có phân phối duration riêng D_z(d)
  (Negative Binomial hoặc Weibull — cho phép hazard tăng/giảm)

  z_1 ~ π,  d_1 ~ D_{z_1},  phát ra d_1 observations,
  rồi chuyển z_2 ~ Ã[z_1, ·]  (đường chéo bằng 0)

Chi phí: inference O(K² · D_max · T) thay vì O(K² · T).
Với K=6, D_max=30 sessions → vẫn khả thi offline; online thì
xấp xỉ bằng cách đưa "elapsed duration in current state"
vào làm feature và giữ HMM (a duration-augmented state).
```

Với monetization, HSMM đáng giá vì "đã ở trạng thái Considering bao lâu" là tín hiệu timing trực tiếp — và HMM chuẩn về mặt cấu trúc **không thể** biểu diễn nó. [Design synthesis, dựa trên lý thuyết HSMM tiêu chuẩn]

### 2.3 Latent Class Analysis (LCA) và Latent Transition Analysis (LTA)

Truyền thống từ psychometrics/social science, ít gặp trong game industry nhưng rất phù hợp:

- **LCA**: mixture model trên biến categorical/binary. Bản chất là "GMM cho dữ liệu rời rạc". Cực hợp với feature dạng cờ (đã mua bao giờ chưa, có bật notification không, có tham gia guild không). Cho ra class-membership probability + item-response probability per class → **bảng profiling sẵn dùng cho ops**.
- **LTA**: LCA + ma trận chuyển đổi giữa các thời điểm đo. Nói cách khác là một HMM với số bước ít, dữ liệu categorical, và trọng tâm là **diễn giải ma trận chuyển tiếp** hơn là dự báo.

LTA trả lời trực tiếp câu hỏi mà ops hay hỏi: "sau LiveOps event, bao nhiêu % nhóm Casual chuyển thành nhóm Engaged, và có bền không?" Đó là một ô trong ma trận chuyển tiếp, có confidence interval. [Design synthesis]

### 2.4 State-space models (liên tục)

Khi state hợp lý hơn ở dạng liên tục (ví dụ `purchase_propensity ∈ R`, `frustration ∈ R`, `budget_remaining ∈ R⁺`):

```text
Linear-Gaussian SSM (Kalman):
  x_t = F x_{t-1} + w_t,   w ~ N(0, Q)
  y_t = H x_t + v_t,       v ~ N(0, R)
  → Kalman filter cho posterior chính xác, O(d³), d nhỏ.

Ứng dụng thực tế nhất trong SDK: theo dõi mức "engagement latent"
mượt hoá qua nhiễu ngày-sang-ngày, thay cho rolling average.
Kalman ưu việt hơn rolling mean vì:
  - tự động điều chỉnh độ tin cậy theo khoảng trống dữ liệu
  - trả về variance (biết mình không biết) — cần cho bandit

Phi tuyến / non-Gaussian:
  particle filter (SMC) — linh hoạt, đắt
  Ở on-device scale, tránh; ưu tiên discrete HMM hoặc Kalman.

Kết hợp: switching state-space (regime = discrete z, dynamics
= liên tục theo regime). Mạnh nhất về biểu đạt, đắt nhất về ops.
```

### 2.5 Chọn số state K và đánh đổi diễn giải

```text
Tiêu chí thống kê:
  BIC = -2 log L + p log N          → phạt mạnh, chọn K nhỏ, ổn định
  AIC = -2 log L + 2p               → phạt nhẹ, dễ chọn K lớn
  ICL = BIC - entropy of assignment → thưởng cho phân tách RÕ
        (ICL thường là lựa chọn tốt nhất khi mục tiêu là DIỄN GIẢI,
         vì nó phạt các mô hình có state chồng lấn mù mờ)
  Cross-validated held-out log-likelihood trên user MỚI
        → tiêu chí trung thực nhất; luôn tách theo user, không theo row

Tiêu chí vận hành (quan trọng ngang thống kê):
  - Mỗi state có đặt được tên bằng một cụm từ tiếng Việt/Anh không?
  - Mỗi state có một ACTION khác biệt tương ứng không?
    Nếu state 5 và state 6 dẫn tới cùng một quyết định → gộp lại.
  - State nhỏ nhất có >= ~2% population không? Nếu không,
    bandit sẽ không bao giờ đủ mẫu để học policy riêng cho nó.

QUY TẮC THỰC HÀNH: chọn K bằng min(K_BIC, K_actionable).
Số lượng state không nên vượt số lượng action khác biệt mà
decision engine thực sự có. K=4..8 là vùng lành mạnh.
Doc 24 chọn K=4 — hoàn toàn hợp lý làm điểm khởi đầu.
```

Đánh đổi cốt lõi: **likelihood tăng đơn điệu theo K, tính hữu dụng thì không.** Một HMM 20 state có perplexity tốt hơn nhưng ops team sẽ không dùng, guardrail không viết được, và bandit sẽ chết đói dữ liệu ở các state hiếm.

---

## 3. Representation learning: từ event stream ra vector

### 3.1 Embedding từ chuỗi sự kiện (word2vec / item2vec analogue)

Ý tưởng gốc: coi mỗi event type (hoặc item, offer, level) như một "từ", chuỗi phiên chơi như một "câu", rồi huấn luyện skip-gram với negative sampling. item2vec (Barkan & Koenigstein, Microsoft) đã chứng minh word2vec chuyển sang miền item hoạt động tốt; Airbnb đã đưa hướng này vào production với listing/user-type embeddings học từ chuỗi click trong session. [Primary research]

```text
Pipeline cho game:
  1. Token hoá event stream:
     LEVEL_START:42  LEVEL_FAIL:42  STORE_OPEN  OFFER_VIEW:starter
     OFFER_DISMISS  AD_REWARDED_COMPLETE  LEVEL_WIN:42

  2. Skip-gram với negative sampling trên cửa sổ ±5 event
     → vector cho MỖI EVENT TYPE (không phải cho user)

  3. User embedding = pooling (mean / TF-IDF-weighted mean /
     attention-weighted) các event vector trong window gần nhất
     → "player2vec" giá rẻ, không cần train model per-user

Vì sao đáng làm TRƯỚC các mô hình nặng:
  - train được trên CPU, vài phút, vài triệu event
  - embedding của event type là artifact tự thân có giá trị:
    nearest neighbour của OFFER_DISMISS:starter tiết lộ
    các event thường đi kèm việc từ chối offer
  - dùng làm feature đầu vào cho k-means/GMM → cluster trên
    embedding space tránh phần lớn failure mode ở mục 1.1
    (không còn scale dominance, không còn collinearity thô)
```

Cảnh báo: embedding pooling mất trật tự thời gian. Nó là bước đệm tốt, không phải đích đến.

### 3.2 Autoencoder trên chuỗi thời gian hành vi

```text
Đầu vào: ma trận [T ngày × F feature] cho mỗi user
         (F ~ 20-40 daily aggregate, T = 14 hoặc 28)

Sequence autoencoder:
  encoder: GRU/1D-CNN/Transformer → z ∈ R^d  (d = 16..64)
  decoder: tái tạo chuỗi gốc
  loss:    MSE (numeric) + BCE (binary) + Poisson NLL (count)
           — dùng likelihood phù hợp từng loại feature,
             KHÔNG dùng MSE cho tất cả

Biến thể hữu ích:
  - Masked/denoising AE: che ngẫu nhiên 15% ô → buộc model học
    tương quan chéo giữa feature, robust với missing data
    (missing data là chuẩn mực trong mobile telemetry)
  - VAE: cho ra posterior q(z|x) có VARIANCE → uncertainty-aware
    representation, khớp trực tiếp với Thompson Sampling (doc 28)
  - Anomaly detection miễn phí: reconstruction error cao
    → bot, cheat, hoặc hành vi thực sự mới lạ

Rủi ro: AE tối ưu cho tái tạo, không cho quyết định. Feature
có variance lớn (playtime) sẽ chiếm hết capacity, feature
hiếm-nhưng-quan trọng (purchase) bị bỏ qua.
→ Khắc phục: reweight loss theo giá trị kinh doanh, hoặc
  thêm một supervised head phụ (multi-task, mục 3.4).
```

### 3.3 Contrastive / self-supervised pretraining

Đây là hướng phù hợp nhất với thực tế của một monetization SDK: **event nhiều vô kể, nhãn (purchase) cực hiếm**. Self-supervised cho phép học biểu diễn từ 100% traffic rồi fine-tune trên 1-3% có nhãn.

```text
Contrastive objective (SimCLR-style, chuyển sang behavior):
  Với mỗi user u, tạo hai "view" từ cùng lịch sử:
    view A = window ngày 1-14, dropout event ngẫu nhiên
    view B = window ngày 8-21, permute nhẹ trong phiên
  Loss InfoNCE:
    L = -log [ exp(sim(z_A, z_B)/τ) / Σ_k exp(sim(z_A, z_k)/τ) ]
    z_k = embedding của user KHÁC trong batch (negatives)

Augmentation hợp lệ cho behavior sequence (giữ nguyên ngữ nghĩa):
  ✓ item/event masking      ✓ crop cửa sổ thời gian
  ✓ dropout phiên ngắn      ✓ jitter timestamp nhỏ
  ✗ shuffle toàn chuỗi (phá causal order — đây KHÔNG phải ảnh)
  ✗ đảo ngược chuỗi (progression trong game là một chiều)

Pretext task thay thế, rẻ và hiệu quả:
  - Next-event prediction (autoregressive)
  - Masked event modeling (BERT-style, xem mục 4)
  - Gap prediction: dự đoán khoảng thời gian tới event kế tiếp
    → task này ép model học chính cái ta cần cho TIMING (mục 5)
```

Nguyên tắc: chọn pretext task **gần với downstream task**. Nếu downstream là "khi nào offer", pretext nên là gap/next-event prediction, không phải reconstruction.

### 3.4 Two-tower architecture

```text
        USER TOWER                      OFFER/ACTION TOWER
   event sequence → encoder        offer features → MLP
   static profile  ↘                (price, type, currency,
   context (time)   → MLP            discount %, art variant)
                     ↓                        ↓
                   u ∈ R^d                  v ∈ R^d
                     └────── score = <u, v> / τ ──────┘

Huấn luyện: sampled softmax với in-batch negatives.
Vấn đề đã biết: in-batch negatives thiên vị theo popularity —
offer phổ biến bị phạt quá mức. Yi et al. (RecSys 2019, YouTube)
đưa ra logQ correction ước lượng tần suất item từ streaming data
để hiệu chỉnh: score' = score - log p̂(item). [Primary research]

Vì sao two-tower quan trọng cho SDK NÀY (không chỉ recommendation):
  1. User tower chạy ON-DEVICE, cache u, chỉ tính lại khi có
     event mới → chi phí runtime gần bằng 0.
  2. Offer tower chạy OFFLINE, precompute v cho toàn bộ catalog.
  3. Thêm offer MỚI không cần retrain user tower — chỉ cần
     feature của offer → giải quyết ITEM cold start (mục 6).
  4. u chính là player embedding dùng lại được cho LTV, churn,
     cluster — một representation, nhiều head (multi-task).

Giới hạn: dot product không mô hình hoá được tương tác bậc cao
giữa user và offer. Two-tower dùng cho RETRIEVAL/scoring thô;
tầng ranking cuối vẫn nên là mô hình cross-feature (hoặc bandit).
```

### 3.5 Graph embeddings cho tín hiệu xã hội

Khi game có guild/clan/friend/gifting, đồ thị xã hội mang thông tin mà feature cá nhân không có: một F2P nằm trong guild toàn whale có propensity khác hẳn một F2P đơn độc có cùng hành vi cá nhân.

```text
Các cạnh dùng được:
  co-guild, friend, gift sent/received, co-play trong trận,
  invite chain (referrer → referee), trade

Kỹ thuật theo mức độ phức tạp:
  1. Feature tổng hợp từ hàng xóm (rẻ nhất, hiệu quả bất ngờ):
     mean/max spend của guild-mates, tỷ lệ whale trong guild,
     degree, clustering coefficient
  2. node2vec / DeepWalk: random walk → skip-gram
     → embedding không giám sát, dùng làm feature
  3. GraphSAGE / GAT: inductive, xử lý được node mới
     → bắt buộc nếu muốn embed user mới mà không retrain

Cảnh báo nhân quả (rất quan trọng): tương quan trong mạng xã hội
là hỗn hợp của INFLUENCE (bạn bè khiến tôi mua) và HOMOPHILY
(người giống nhau kết bạn với nhau). Không tách được hai thứ này
bằng observational data. Đừng viết "guild ảnh hưởng chi tiêu"
trong dashboard trừ khi có randomized experiment.
[Primary research — vấn đề influence/homophily identification
 là kết quả đã được thiết lập trong literature về social networks]
```

---

## 4. Sequence architectures trên event stream

### 4.1 Dòng dõi

```text
GRU4Rec (Hidasi et al., 2016 — arXiv 1511.06939)
  RNN/GRU cho session-based recommendation, session-parallel
  mini-batch + ranking loss (BPR/TOP1). Mở màn hướng
  "session sequence là đủ, không cần user id".
        ↓
SASRec (Kang & McAuley, ICDM 2018 — arXiv 1808.09781)
  Self-attention một chiều (causal mask), next-item prediction.
  Nhanh hơn RNN/CNN một bậc độ lớn. Đặc điểm quan sát được:
  trên dataset DÀY nó chú ý xa, trên dataset THƯA nó chú ý
  vào hành vi gần đây. [Primary research]
        ↓
BERT4Rec (Sun et al., CIKM 2019 — arXiv 1904.06690)
  Transformer HAI CHIỀU + masked item prediction (Cloze).
  Học biểu diễn giàu ngữ cảnh hơn, tốt khi tương tác thưa.
  [Primary research]
```

**Lưu ý phương pháp luận quan trọng:** đã có công trình replication cho thấy kết quả so sánh giữa các mô hình sequential recommendation rất nhạy với chi tiết huấn luyện (số epoch, negative sampling, cách chia dữ liệu), và các baseline được tinh chỉnh kỹ thường thu hẹp hoặc xoá bỏ khoảng cách được báo cáo. Đừng chọn kiến trúc dựa trên bảng số trong paper; hãy benchmark trên chính dữ liệu của bạn với baseline mạnh (popularity, recency, gradient boosting trên aggregate feature). [Primary research]

### 4.2 Áp dụng vào monetization, không phải recommendation

Khác biệt cốt tử: recommendation dự đoán **item nào**, monetization engine cần dự đoán **có/không, khi nào, và ở mức giá nào**. Cùng backbone, khác head.

```text
Shared encoder (SASRec-style causal transformer over events)
        ↓ h_t
   ├─→ head A: P(purchase trong 24h tới)          [binary]
   ├─→ head B: P(churn trong 7 ngày tới)          [binary]
   ├─→ head C: E[revenue 30 ngày tới]             [regression, tweedie]
   ├─→ head D: intensity λ(t) cho event kế tiếp   [TPP, mục 5]
   └─→ head E: b_t — posterior trên K latent state [soft, mục 2]

Multi-task giúp: nhãn purchase quá hiếm để train encoder lớn
một mình; các head dày đặc hơn (next-event, session length)
cung cấp gradient signal để encoder học được biểu diễn tốt.

Head E đáng chú ý: nếu ép output là một simplex K chiều với
regularizer khuyến khích sparsity + temporal smoothness, ta thu
được một belief state HỌC ĐƯỢC thay vì HMM đặt tay — vẫn giữ
được tính diễn giải (K state có tên) nhưng dùng encoder mạnh hơn.
Đây là con đường nâng cấp tự nhiên cho engine ở doc 24.
[Design synthesis]
```

### 4.3 Chuỗi có thời gian bất quy tắc và độ dài biến thiên

Đây là chỗ mọi kiến trúc recommendation "vanilla" thất bại với game data. Sequence recommendation giả định các bước cách đều; event stream trong game thì không — hai event cách nhau 3 giây khác hoàn toàn hai event cách nhau 3 tuần.

```text
GIẢI PHÁP 1 — Time2Vec (Kazemi et al., 2019 — arXiv 1907.05321)
  t2v(τ)[i] = ω_i·τ + φ_i             nếu i = 0
            = F(ω_i·τ + φ_i)          nếu 1 <= i <= k   (F = sin)
  Thành phần tuyến tính bắt tiến trình thời gian không tuần hoàn;
  thành phần tuần hoàn bắt chu kỳ (giờ trong ngày, ngày trong tuần)
  với TẦN SỐ HỌC ĐƯỢC thay vì cố định như sinusoidal positional
  encoding. Model-agnostic: cắm vào RNN/Transformer đều được.
  [Primary research]

  Với game: mã hoá CẢ HAI
    - absolute time-of-day / day-of-week (bắt nhịp sinh hoạt)
    - Δt = log(1 + t_i - t_{i-1}) (bắt tempo và khoảng nghỉ)

GIẢI PHÁP 2 — Time-aware attention
  Sửa attention logit bằng bias phụ thuộc khoảng cách thời gian:
    e_ij = (q_i·k_j)/√d  +  g(Δt_ij)
  g có thể là: bucket embedding của log Δt, hoặc decay tham số
    g(Δt) = -γ · log(1 + Δt),  γ học được (per-head)
  Cho phép các head khác nhau chuyên biệt hoá:
    head "short-horizon" (γ lớn) vs head "lifetime" (γ ≈ 0).

GIẢI PHÁP 3 — Gap token tường minh
  Chèn token giả vào chuỗi khi Δt vượt ngưỡng:
    ... LEVEL_WIN  [GAP_1D]  SESSION_START ...
    ... STORE_OPEN [GAP_14D] SESSION_START ...
  Thô sơ nhưng cực hiệu quả và dễ debug. Nên làm baseline.

ĐỘ DÀI BIẾN THIÊN
  - Truncate về N event gần nhất (N=50..200) + LƯU một
    "summary state" nén phần lịch sử bị cắt (aggregate feature)
    → tránh mất hoàn toàn ký ức dài hạn của whale lâu năm
  - Left-padding + attention mask (không right-padding với causal)
  - Bucket theo độ dài khi tạo batch → giảm padding waste
  - Trên device: giữ chuỗi cuộn (ring buffer) N event,
    tái tính encoder incremental với KV-cache
```

---

## 5. Temporal Point Processes — mảnh ghép bị bỏ quên cho câu hỏi "khi nào"

Đây là mục có tỷ lệ giá trị/độ phổ biến cao nhất trong doc này. Hầu như mọi vendor monetization đều trả lời "ai" và "cái gì"; rất ít trả lời "khi nào" bằng một mô hình đúng. TPP là công cụ toán học chuẩn cho việc đó.

### 5.1 Nền tảng

```text
Một TPP mô tả chuỗi thời điểm {t_1, t_2, ...} qua CONDITIONAL
INTENSITY FUNCTION:

  λ*(t) = lim_{dt→0} P(có event trong [t, t+dt) | H_t) / dt

  ("*" nhắc rằng nó phụ thuộc lịch sử H_t)

Từ λ* suy ra mọi thứ:
  Survival:  S(t) = exp( -∫_{t_n}^{t} λ*(u) du )
  Density:   f(t) = λ*(t) · S(t)
  Log-lik:   log L = Σ_i log λ*(t_i)  -  ∫_0^T λ*(u) du
                     ↑ event xảy ra      ↑ event KHÔNG xảy ra

Số hạng tích phân là điểm mấu chốt: TPP học từ CẢ khoảng lặng,
không chỉ từ event. Đây là lợi thế cơ bản so với việc chia thời
gian thành bucket ngày rồi làm classification — cách làm bucket
vứt bỏ thông tin về vị trí event trong ngày và về độ dài chính xác
của khoảng lặng.
```

### 5.2 Hawkes process và self-excitation

```text
Hawkes (self-exciting):
  λ*(t) = μ(t)  +  Σ_{t_i < t} α · κ(t - t_i)

  μ(t)  baseline intensity (có thể tuần hoàn: giờ, ngày trong tuần)
  α     branching ratio — mỗi event "sinh" thêm bao nhiêu event
  κ(·)  kernel suy giảm; exponential κ(s)=β e^{-βs} là phổ biến
        (cho phép cập nhật đệ quy O(1), quan trọng cho on-device)
        power-law kernel bắt đuôi dài tốt hơn nhưng đắt hơn

Điều kiện ổn định: α · ∫κ = α < 1. Nếu ước lượng ra α >= 1,
mô hình sai (hoặc dữ liệu có burst do bug/bot).

Ý nghĩa cho monetization — CHÍNH LÀ trọng tâm:
  "Một purchase làm TĂNG cường độ purchase trong ngắn hạn."
  Đây là hiện tượng được quan sát rộng rãi trong mobile game
  (mua lần đầu là ngưỡng tâm lý và ngưỡng ma sát thanh toán;
   vượt qua rồi thì lần hai dễ hơn nhiều — xem doc 29).
  Hawkes CHÍNH XÁC là mô hình toán cho hiện tượng đó, và nó
  ước lượng ĐỊNH LƯỢNG:
     α  = mức độ tăng cường (mua 1 lần → kỳ vọng thêm α lần nữa)
     1/β = thời hằng của cửa sổ tăng cường (giờ? ngày? tuần?)
  → 1/β trả lời trực tiếp "cửa sổ vàng để offer tiếp là bao lâu".

MULTIVARIATE Hawkes — nơi giá trị thật nằm:
  λ*_m(t) = μ_m + Σ_{n} Σ_{t_i: type=n} α_{n→m} κ(t - t_i)
  Ma trận α là EXCITATION GRAPH giữa các loại event:
     session_start → store_open ?
     ad_reward_complete → purchase ?   (ad có ăn thịt IAP không?)
     offer_dismiss → churn ?
     friend_gift_received → session_start ?
  Ma trận này là một artifact CỰC KỲ dễ đọc cho ops team —
  một heatmap "cái gì kích hoạt cái gì, mạnh bao nhiêu, trong
  bao lâu". Đây là thứ nên đưa lên Control Plane.

  CẢNH BÁO NHÂN QUẢ: α_{n→m} là excitation THỐNG KÊ, không phải
  hiệu ứng nhân quả. Confounder chung (một đợt LiveOps làm tăng
  cả hai loại event) sẽ hiện lên như excitation. Dùng nó để
  SINH GIẢ THUYẾT, xác nhận bằng experiment (doc 05).
```

### 5.3 Neural TPP

```text
RMTPP (Du et al., KDD 2016) — Recurrent Marked TPP
  RNN nén lịch sử (thời gian + MARK/loại event) thành h_j, rồi
  λ*(t) = exp( v·h_j + w(t - t_j) + b )
  → dạng đóng cho likelihood; mark được mô hình hoá tường minh.
  [Primary research]

Neural Hawkes Process (Mei & Eisner, NeurIPS 2017)
  LSTM với continuous-time decay của cell state giữa các event
  → intensity "tự điều biến", cho phép ẢNH HƯỞNG ỨC CHẾ
    (inhibition), điều mà Hawkes cổ điển với α > 0 không làm được.
  Quan trọng với game: một offer bị từ chối có thể LÀM GIẢM
  intensity mua trong ngắn hạn (offer fatigue) — Hawkes chuẩn
  không biểu diễn được, neural Hawkes thì có. [Primary research]

Transformer Hawkes Process (Zuo et al., ICML 2020)
  Self-attention thay LSTM; phụ thuộc dài hạn tốt hơn, song song
  hoá được. [Primary research]

LỰA CHỌN THỰC DỤNG CHO SDK:
  Bậc 1  Parametric Hawkes exponential kernel, univariate,
         fit bằng MLE trên toàn population + per-user shrinkage.
         → chạy được on-device, cập nhật O(1), giải thích được.
  Bậc 2  Multivariate Hawkes trên 8-15 loại event chính (server-side).
         → cho ra excitation matrix làm ops artifact.
  Bậc 3  Neural TPP như một HEAD trên sequence encoder (mục 4.2).
         → dùng khi đã có encoder rồi; chi phí biên thấp.
  Đừng bắt đầu ở bậc 3.
```

### 5.4 Nối TPP vào quyết định

```text
Từ λ*(t) rút ra ba đại lượng dùng trực tiếp cho policy:

  1. P(event trong [t, t+h] | H_t) = 1 - exp(-∫_t^{t+h} λ*(u)du)
     → "propensity trong horizon h", chuẩn hoá theo thời gian
       (khác hẳn score không có đơn vị của một classifier)

  2. Expected time to next purchase = ∫ u · f(u) du
     → xếp hạng ai cần can thiệp TRƯỚC

  3. d/dt λ*(t) — intensity đang lên hay xuống
     → INTENT ĐANG TĂNG là tín hiệu timing tốt nhất.
       Offer khi intensity đang lên tự nhiên = tăng conversion
       Offer khi intensity đáy = tiêu tốn một lần hiển thị

  Guardrail suy ra được: nếu λ* đã cao, KHÔNG cần offer —
  người dùng sẽ mua dù sao. Đây là lập luận uplift bằng ngôn ngữ
  TPP: đừng tiêu ngân sách vào "sure things" (doc 05, mục 10).
```

---

## 6. Cold start và few-shot: 5 phút đầu tiên biết được gì

### 6.1 Thứ tự thông tin theo thời gian

```text
t = 0 (install)        THÔNG TIN: gần như chỉ có cohort prior
  device model/tier, OS version, locale, country, timezone,
  acquisition channel/campaign/creative (nếu có attribution),
  install hour, store referrer
  → Sức mạnh dự báo LTV: yếu-trung bình. Device tier + country
    + campaign giải thích được một phần đáng kể phương sai LTV
    ở mức COHORT, nhưng gần như vô dụng ở mức CÁ NHÂN.
  → Điều đúng đắn: KHÔNG dự đoán cá nhân, chỉ đặt PRIOR.

t = phiên 1 (5-20 phút)   THÔNG TIN: nhảy vọt
  session_length_1, tutorial completion & drop point,
  số level thử, fail rate sớm, có mở store không,
  phản ứng với ad đầu tiên, tốc độ thao tác, có bật
  notification không, có đăng nhập social không
  → Đây là điểm có ROI thông tin cao nhất trong toàn vòng đời.
    Session đầu tiên nói nhiều hơn toàn bộ install-time signal.

t = D1-D3               THÔNG TIN: return behavior
  có quay lại không, khoảng cách giữa hai phiên đầu,
  độ sâu progression, hành vi với soft currency
  → D1 retention là biến phân tách mạnh nhất cho hầu hết
    downstream outcome. Đây là kiến thức đã được thiết lập
    trong game analytics. [Design synthesis]

t = D7+                 chuyển sang chế độ mô hình đầy đủ
```

### 6.2 Hierarchical Bayesian shrinkage — công cụ trung tâm

```text
Bài toán: ước lượng θ_u (ví dụ purchase propensity) cho user u
có rất ít dữ liệu.

Mô hình phân cấp:
  θ_u | c(u)  ~ N(μ_{c(u)}, σ²_within)        c = cohort của u
  μ_c         ~ N(μ_0,      σ²_between)       μ_0 = population
  y_u | θ_u   ~ likelihood (Bernoulli/Poisson)

Posterior mean có dạng shrinkage tường minh (Gaussian conjugate):
  θ̂_u = w_u · ȳ_u  +  (1 - w_u) · μ̂_{c(u)}
  w_u = n_u / (n_u + σ²_within/σ²_ȳ)

  n_u nhỏ  → w_u → 0  → dùng cohort prior (đúng khi cold start)
  n_u lớn  → w_u → 1  → tin dữ liệu cá nhân
  Chuyển tiếp TỰ ĐỘNG và MƯỢT theo lượng bằng chứng.

Với Beta-Binomial (conversion rate) đơn giản hơn nữa:
  prior:     Beta(α_c, β_c)   ước lượng từ cohort (empirical Bayes)
  posterior: Beta(α_c + k_u, β_c + n_u - k_u)
  → cập nhật một dòng code, chạy on-device, không cần retrain.

ĐÂY LÀ CÁCH ĐÚNG ĐỂ NỐI VỚI DOC 24: belief state prior b_0
KHÔNG nên là uniform. Nó nên là posterior của cohort:
  b_0(z | device_tier, country, campaign) học từ lịch sử.
  Một user cài từ campaign incentivized trên máy low-end
  khởi động với b_0 nghiêng hẳn về state "ad-monetized",
  và cần ÍT bằng chứng hơn để xác nhận.

BA TẦNG PRIOR nên có:
  global → country×platform → country×platform×campaign
  (partial pooling: tầng sâu hơn tự động shrink về tầng nông
   khi campaign còn ít dữ liệu — tránh overfit campaign mới)
```

### 6.3 Meta-learning

Ý tưởng: coi mỗi user là một "task", học một khởi tạo tham số sao cho vài gradient step trên ít dữ liệu là đủ thích ứng. MeLU (Lee et al., KDD 2019) áp dụng ý tưởng MAML cho cold-start recommendation, ước lượng sở thích user mới từ một số nhỏ item đã tiêu thụ. Vartak et al. (NeurIPS 2017) đưa ra góc nhìn meta-learning cho item cold start với kiến trúc mạng thích ứng theo task. [Primary research]

Đánh giá thực dụng cho SDK này: meta-learning **đúng về khái niệm** nhưng chi phí vận hành cao (train phức tạp, khó debug, khó giải thích), và trong đa số trường hợp hierarchical Bayesian shrinkage đạt phần lớn lợi ích với một phần nhỏ độ phức tạp. Xếp meta-learning vào giai đoạn sau, sau khi tầng Bayes phân cấp đã bão hoà. [Design synthesis]

### 6.4 Progressive personalization

```text
Một policy duy nhất, độ cá nhân hoá tăng dần theo bằng chứng:

  Evidence tier 0 (install)      → cohort policy, tham số cố định
                                    exploration cao, offer an toàn
  Evidence tier 1 (session 1)    → prior cập nhật, bandit context
                                    = cohort + session-1 feature
  Evidence tier 2 (D1-D7)        → belief state b_t bắt đầu sắc nét
                                    per-user posterior có ý nghĩa
  Evidence tier 3 (D7+, >20 sess)→ full personalization,
                                    TPP intensity đáng tin,
                                    exploration giảm

Điều khiển bằng một đại lượng duy nhất: ENTROPY của posterior.
  H(b_t) cao → tôi chưa biết → explore nhiều, offer conservative
  H(b_t) thấp → tôi biết rồi → exploit, offer đúng người
Đây là cầu nối trực tiếp giữa doc 24 (belief) và doc 28 (bandit):
uncertainty của representation ĐIỀU KHIỂN mức exploration.
[Design synthesis]
```

---

## 7. Concept drift trong quần thể người chơi

Segmentation là mô hình mỏng manh nhất trước drift, vì cluster boundary được học một lần rồi áp dụng mãi mãi.

### 7.1 Các nguồn drift, theo mức độ gây hại

```text
1. LIVEOPS EVENT DISTORTION  (gây hại nhất, và thường bị bỏ qua)
   Trong một double-XP weekend hoặc một sale lớn:
     - playtime tăng 2-3×, purchase rate tăng, ARPDAU nhảy vọt
     - MỌI behavioral feature dịch chuyển đồng thời
   Hệ quả: cluster assignment thay đổi hàng loạt trong 48h,
   rồi trở về — nhưng nếu policy đã hành động dựa trên
   assignment sai đó thì thiệt hại đã xảy ra.
   → Đây là drift do CAN THIỆP CỦA CHÍNH TA, không phải
     do người chơi thay đổi. Phải xử lý riêng.

2. COHORT / CAMPAIGN COMPOSITION SHIFT
   UA team đổi ngân sách sang một network khác → thành phần
   người chơi mới đổi hoàn toàn trong 2 tuần, dù không ai
   thay đổi hành vi. Cluster size drift là ARTIFACT của
   marketing mix, không phải tín hiệu sản phẩm.
   → Luôn phân rã metric theo cohort trước khi kết luận.

3. SEASONALITY
   Đa mức: giờ trong ngày → ngày trong tuần → mùa lễ
   (Giáng sinh/Black Friday ở US-EU, kỳ nghỉ hè, back-to-school).
   Ngày lễ ảnh hưởng KHÁC NHAU theo quốc gia — model toàn cầu
   không có feature holiday theo quốc gia sẽ diễn giải sai.

4. VERSION / CONTENT DRIFT
   Patch đổi economy, thêm level, đổi giá → phá vỡ ý nghĩa
   của feature ("level 50" tháng này ≠ "level 50" tháng trước).

5. GENUINE POPULATION DRIFT (chậm nhất, thật nhất)
   Game trưởng thành → cohort mới khác cohort đầu,
   người chơi cũ mệt mỏi, meta thay đổi.
```

### 7.2 Feature drift-robust: ba loại baseline

Đây là nguyên tắc thiết kế quan trọng nhất của mục này.

```text
Cho một đại lượng x (ví dụ session_length hôm nay):

  ABSOLUTE            x_t = 23.4 phút
    ưu: dễ hiểu, so sánh được giữa các user
    nhược: drift theo mọi thứ ở 7.1; vô dụng khi so sánh
           một tuần thường với tuần LiveOps

  PERSONAL BASELINE   z_personal = (x_t - μ_u) / σ_u
    μ_u, σ_u = trung bình/độ lệch của CHÍNH user u (rolling 28d)
    ưu: bất biến với mức nền cá nhân; "user này hôm nay
        BẤT THƯỜNG so với chính họ" — đây là tín hiệu intent
        mạnh nhất và ổn định nhất
    nhược: cần lịch sử (không dùng được khi cold start);
           nhạy với user có variance rất thấp → clip σ_u

  COHORT BASELINE     z_cohort = (x_t - μ_{c,t}) / σ_{c,t}
    μ_{c,t} = trung bình cohort c TẠI THỜI ĐIỂM t
    ưu: TỰ ĐỘNG khử LiveOps distortion và seasonality —
        nếu cả cohort tăng 2×, z_cohort không đổi
    nhược: cần population statistics cập nhật; che mất
           hiệu ứng toàn cục thật (đôi khi ta MUỐN thấy)

KHUYẾN NGHỊ: đưa CẢ BA vào feature vector, không chọn một.
  Model sẽ tự học cái nào hữu ích cho từng downstream task.
  Chi phí: 3× số cột cho các feature cốt lõi (~15-20 feature),
  hoàn toàn chấp nhận được.

Bổ sung: một cờ tường minh `is_liveops_active`,
`liveops_type`, `days_into_event` — để model học được
"trong event thì luật khác" thay vì bị nhiễu.
```

### 7.3 Phát hiện và phản ứng với drift

```text
GIÁM SÁT (chạy hàng ngày):
  - PSI (Population Stability Index) cho từng feature:
      PSI = Σ_bins (p_now - p_ref) · ln(p_now / p_ref)
      < 0.1 ổn định | 0.1-0.25 cảnh báo | > 0.25 drift nghiêm trọng
      (ngưỡng heuristic tiêu chuẩn trong credit scoring, được
       dùng rộng rãi làm quy ước ngành)
  - Cluster size distribution: chi-square vs baseline
  - Assignment churn rate: % user đổi cluster tuần này
  - PREDICTION drift ≠ INPUT drift: giám sát cả hai riêng.
    Input ổn định + prediction drift = model đang gặp
    tổ hợp feature mới.

PHẢN ỨNG (theo thứ tự ưu tiên):
  1. Feature engineering drift-robust (mục 7.2) — phòng bệnh
  2. Retrain định kỳ theo lịch (hàng tháng) với sliding window,
     KHÔNG retrain phản ứng theo alert (dễ đuổi theo nhiễu)
  3. Freeze cluster boundary, chỉ retrain assignment model
     → giữ ổn định ngữ nghĩa cho ops (tên segment không đổi
       nghĩa mỗi tháng) — điểm này rất quan trọng cho lòng tin
  4. LiveOps-aware gating: trong thời gian event, chuyển
     decision engine sang chế độ conservative (giảm exploration,
     dùng cohort baseline feature, tăng guardrail)
  5. Champion/challenger thường trực: model mới luôn chạy
     shadow trước khi thay thế
```

---

## 8. Interpretability cho ops team

Đây không phải yêu cầu "nice to have". Nếu Control Plane hiển thị một quyết định mà product manager không giải thích nổi cho giám đốc studio, tính năng đó sẽ bị tắt. Interpretability là điều kiện để **được phép chạy**.

### 8.1 Feature attribution

```text
SHAP (Lundberg & Lee, NeurIPS 2017): phân bổ đóng góp dựa trên
Shapley value từ lý thuyết trò chơi hợp tác. Tính chất được đảm
bảo: local accuracy (tổng attribution = prediction - baseline),
missingness, consistency. TreeSHAP tính chính xác trong thời gian
đa thức cho tree ensemble → gần như miễn phí với LightGBM/XGBoost.
[Primary research]

Dùng ở hai mức:
  GLOBAL (mean |SHAP| theo feature) → "model này nhìn vào cái gì"
    → ops dùng để sanity-check: nếu top feature là
      `days_since_install` thì model chỉ đang học lifecycle,
      không học hành vi → cảnh báo đỏ
  LOCAL (SHAP của một user) → "vì sao user NÀY nhận offer này"
    → dòng giải thích trên dashboard:
      "P(purchase)=0.31 (baseline 0.08). Đóng góp chính:
       +0.11 mở store 4 lần trong 2 ngày
       +0.07 fail level 5 lần liên tiếp
       -0.04 chưa từng mua"

CẢNH BÁO cần ghi rõ cho ops:
  - SHAP giải thích MODEL, không giải thích THẾ GIỚI.
    Nó là attribution tương quan, không phải hiệu ứng nhân quả.
    "Mở store nhiều làm tăng P(mua)" là cách đọc SAI.
  - Với feature tương quan mạnh, SHAP chia đóng góp theo cách
    phụ thuộc vào phương pháp (interventional vs conditional)
    → hai cách cho ra kết quả khác nhau; phải chọn và ghi rõ.
  - Không tối ưu hoá theo SHAP. Nó là công cụ chẩn đoán.
```

### 8.2 Cluster profiling và đặt tên

```text
QUY TRÌNH ĐẶT TÊN (chuẩn hoá, không tuỳ hứng):
  1. Với mỗi cluster k và mỗi feature f, tính effect size
     so với population:  d = (μ_{k,f} - μ_f) / σ_f   (Cohen's d)
  2. Xếp hạng theo |d|, lấy top 5 phân biệt nhất
  3. Kiểm tra bằng lift trên OUTCOME (conversion, ARPDAU, D7)
  4. Đặt tên theo HÀNH VI + XU HƯỚNG, không theo số:
     ✓ "Grinder không chi tiêu"     ✓ "Whale đang nguội"
     ✓ "Người xem ad thay vì mua"   ✓ "Người mới chơi dồn dập"
     ✗ "Cluster 3"  ✗ "Segment A"  ✗ "High-value users"
       (từ "high-value" chung chung không cho biết PHẢI LÀM GÌ)
  5. Mỗi tên phải kèm: kích thước (%), 3 feature đặc trưng,
     outcome lift, ĐỘ ỔN ĐỊNH (Jaccard từ mục 1.3),
     và MỘT ACTION được khuyến nghị.

Cluster card mẫu cho Control Plane:
┌──────────────────────────────────────────────────┐
│ WHALE ĐANG NGUỘI                    4.1% (n=8,204)│
│ Ổn định bootstrap: 0.81 ✅                        │
│ Đặc trưng: spend_lifetime p95+ | sessions_7d ↓62% │
│            days_since_purchase 11 (baseline 3)    │
│ Outcome: churn_30d lift 3.2× | ARPU lifetime 47×  │
│ Uplift khả dụng: +8.4% retention với win-back     │
│ Action mặc định: personal win-back, KHÔNG discount │
└──────────────────────────────────────────────────┘
```

### 8.3 Counterfactual explanation

```text
"Điều gì cần khác đi để quyết định này thay đổi?"

  x' = argmin_{x'}  d(x, x')   s.t.  f(x') ≠ f(x)
       với ràng buộc: chỉ đổi feature CÓ THỂ HÀNH ĐỘNG
       (không đổi được country, device, install_date)

Vì sao ops cần cái này hơn SHAP:
  SHAP nói "vì sao mô hình nghĩ vậy" (mô tả)
  Counterfactual nói "cần gì để khác đi" (hành động)

Ví dụ trên dashboard:
  "User này KHÔNG nhận starter pack. Nếu họ mở store thêm
   1 lần nữa HOẶC fail thêm 2 level, họ sẽ nhận."
  → PM ngay lập tức hiểu được ranh giới quyết định,
    và biết ranh giới đó có hợp lý không.

Đây cũng là cơ chế PHÁT HIỆN LỖI tốt nhất: nếu counterfactual
là "nếu user đổi country sang Đức thì được giảm giá", ta vừa
phát hiện một vấn đề công bằng/pháp lý (doc 13).
```

### 8.4 Vì sao Control Plane bắt buộc phải có tầng này

```text
Chuỗi lòng tin trong một studio:
  Ops nhìn quyết định → hiểu → tin → BẬT tính năng
  Ops nhìn quyết định → không hiểu → sợ → TẮT tính năng

Yêu cầu tối thiểu cho Control Plane:
  1. Mọi segment có TÊN người đọc được + cluster card
  2. Mọi quyết định có thể tra ngược thành 3 lý do chính
  3. Mọi model có ngày train, dữ liệu train, phiên bản
  4. Ops có nút OVERRIDE ở mức segment — và override đó
     được ghi log như một arm riêng để đo (biến sự can thiệp
     của con người thành dữ liệu, thay vì thành nhiễu)
  5. Bảng "model nghĩ gì về TÔI" — QA/PM tự tra tài khoản test
```

---

## 9. Segment → policy: ba kiến trúc và đánh đổi mẫu

Đây là quyết định kiến trúc có hệ quả lớn nhất, và thường bị quyết định theo thói quen thay vì theo phân tích.

```text
KIẾN TRÚC A — Segment là CONTEXT FEATURE
  một policy duy nhất: π(a | x) với x chứa segment one-hot/embedding
  ✓ chia sẻ toàn bộ dữ liệu giữa các segment
  ✓ sample-efficient nhất; segment hiếm vẫn học được
  ✓ dễ vận hành: một model, một pipeline
  ✗ giả định hàm reward CHIA SẺ CẤU TRÚC giữa các segment
  ✗ nếu segment thực sự cần policy khác hẳn, linear/shallow
    model sẽ trung bình hoá chúng lại (interaction bị bỏ lỡ)
  → khắc phục một phần: thêm interaction term segment × action
    (LinUCB với feature chéo), hoặc dùng tree/NN có khả năng
    biểu diễn interaction

KIẾN TRÚC B — POLICY RIÊNG mỗi segment
  K bandit độc lập, mỗi cái học riêng
  ✓ linh hoạt tối đa; không có giả định chia sẻ
  ✓ debug dễ; ops hiểu ngay ("policy cho whale")
  ✗ dữ liệu bị chia K phần → thời gian hội tụ tăng ~K lần
  ✗ segment nhỏ (2% population) có thể KHÔNG BAO GIỜ hội tụ
  ✗ user đổi segment → mất toàn bộ lịch sử học được
  → chỉ dùng khi: K nhỏ (<=4), traffic lớn, và có bằng chứng
    heterogeneity mạnh (mục 10)

KIẾN TRÚC C — HIERARCHICAL SHRINKAGE (khuyến nghị)
  θ_k ~ N(θ_global, τ²)          per-segment params
  θ_global ~ prior
  ✓ nội suy TỰ ĐỘNG giữa A và B theo lượng dữ liệu:
      segment ít dữ liệu → θ_k ≈ θ_global (hành xử như A)
      segment nhiều dữ liệu → θ_k tự do (hành xử như B)
  ✓ τ² học từ dữ liệu = "các segment thực sự khác nhau bao nhiêu"
    → đây đồng thời là MỘT PHÉP ĐO tính hữu ích của segmentation
  ✓ tương thích trực tiếp với Thompson Sampling (doc 28):
    sample θ_k từ posterior phân cấp
  ✗ phức tạp hơn về triển khai; cần cẩn thận với prior trên τ

  Ước lượng thực dụng không cần MCMC:
    empirical Bayes / James–Stein style shrinkage
    θ̂_k = θ̂_global + B_k (ȳ_k - θ̂_global),  B_k = τ̂²/(τ̂² + s²_k)
    → cập nhật được trực tuyến, đủ nhẹ cho production
```

```text
QUY TẮC QUYẾT ĐỊNH THỰC HÀNH

  traffic/tuần thấp (<50k decisions)     → A (context feature)
  traffic trung bình                     → C (hierarchical)
  traffic rất cao + heterogeneity đã chứng minh → C, τ² sẽ tự
                                            nới ra thành gần B

  Gần như KHÔNG BAO GIỜ nên chọn B thuần. Nó là lựa chọn mặc
  định theo trực giác và là cách phổ biến nhất để làm chậm
  học tập một cách không cần thiết.

  MỘT ĐIỂM TINH TẾ: nếu dùng belief state SOFT (mục 2), user
  không thuộc một segment mà phân bố trên K segment. Khi đó
  cập nhật bandit nên PHÂN BỔ THEO TRỌNG SỐ:
    for k in 1..K:  update θ_k with weight b_t(k)
  Cách này mượt hơn hard assignment, giảm hẳn vấn đề "mất lịch
  sử khi đổi segment", và tự nhiên hoạt động như một dạng
  shrinkage bổ sung. [Design synthesis]
```

---

## 10. Đánh giá segmentation: chứng minh nó HỮU ÍCH

Silhouette score không phải mục tiêu. Đây là phần quan trọng nhất của doc.

### 10.1 Thang bốn cấp bằng chứng

```text
CẤP 1 — Statistical validity (cần, KHÔNG đủ)
  gap statistic > null | bootstrap Jaccard > 0.75
  | temporal ARI ổn định
  → chỉ chứng minh: "có cấu trúc trong dữ liệu"

CẤP 2 — Predictive lift (mạnh hơn, vẫn chưa đủ)
  So sánh AUC/PR-AUC của:
     M0: baseline features, KHÔNG có segment
     M1: baseline features + segment id
  Nếu ΔAUC ≈ 0 → segment là hàm thừa của feature đã có.
  Đây là kết cục PHỔ BIẾN NHẤT của các dự án segmentation
  và hầu như không bao giờ được kiểm tra.
  → Segment "phát hiện" ra hoá ra chỉ là spend decile.

CẤP 3 — Heterogeneity of treatment effect (đúng câu hỏi)
  Segment hữu ích ⟺ HIỆU ỨNG CỦA HÀNH ĐỘNG khác nhau giữa
  các segment, chứ không phải mức outcome khác nhau.

    Whale có conversion cao hơn F2P    → KHÔNG hữu ích
                                          (đã biết, không cần segment)
    Discount hiệu quả trên F2P nhưng LÀM GIẢM revenue trên whale
                                       → HỮU ÍCH (đảo chiều policy)

  Kiểm định chính thức trên dữ liệu A/B:
    outcome ~ treatment + segment + treatment × segment
    → F-test trên nhóm hệ số tương tác
    → segment CHỈ đáng tồn tại nếu tương tác có ý nghĩa
      (và có ý nghĩa THỰC TIỄN, không chỉ p < 0.05 với n lớn)

  Công cụ tự động tìm heterogeneity:
    Causal tree / causal forest (Athey & Imbens, PNAS 2016):
    recursive partitioning nhắm thẳng vào phương sai của
    TREATMENT EFFECT, với "honest" estimation — một mẫu để
    chia cây, một mẫu khác để ước lượng effect trong mỗi lá,
    tránh overfit khi làm inference. [Primary research]
    → Đây là cách ĐÚNG để phát hiện segment cho monetization:
      để dữ liệu can thiệp tự nói segment nào tồn tại,
      thay vì cluster trên feature rồi hy vọng nó hữu ích.

CẤP 4 — Policy improvement (bằng chứng cuối cùng)
  Chạy A/B thật:
    Arm A: policy KHÔNG dùng segment
    Arm B: policy CÓ dùng segment
  Đo trên metric bắc cầu (doc 05): D30 revenue per install,
  retention, không chỉ conversion tức thời.
  ĐÂY LÀ TIÊU CHÍ DUY NHẤT THỰC SỰ QUAN TRỌNG.
```

### 10.2 Đánh giá offline trước khi tốn traffic

```text
Off-policy evaluation (trước khi chạy A/B):
  IPS:   V̂ = (1/n) Σ_i [ π_new(a_i|x_i) / π_log(a_i|x_i) ] · r_i
  SNIPS: chuẩn hoá theo tổng trọng số → variance thấp hơn
  DR:    kết hợp mô hình reward + IPS → robust hơn
  (chi tiết ở doc 28)

  ĐIỀU KIỆN BẮT BUỘC: π_log phải là stochastic và propensity
  phải được GHI LOG tại thời điểm quyết định. Nếu policy hiện
  tại là deterministic rule, không có OPE nào hợp lệ.
  → Hệ quả kiến trúc: SDK phải log propensity NGAY TỪ NGÀY ĐẦU,
    kể cả khi chưa có bandit. Đây là quyết định không thể sửa
    ngược được về sau.

Xếp hạng heterogeneity trên dữ liệu quan sát:
  Qini curve / uplift curve: sắp người dùng theo uplift dự đoán
  giảm dần, vẽ incremental outcome tích luỹ vs tỷ lệ được
  targeting. Qini coefficient = diện tích so với đường random.
  → Trả lời "nếu chỉ can thiệp 20% người dùng, segmentation
    này giúp tôi chọn đúng 20% nào?" — chính xác là câu hỏi
    kinh doanh khi ngân sách/ngân sách hiển thị bị giới hạn.
  Lưu ý: Qini cần dữ liệu RCT (hoặc propensity hợp lệ), và
  ước lượng của nó nhiễu ở phần đuôi. [Primary research]
```

### 10.3 Danh sách kiểm tra trước khi ship một segmentation

```text
□ Có vượt qua null reference (gap statistic) không?
□ Bootstrap Jaccard mỗi cluster > 0.75?
□ Ổn định qua 4 tuần (ARI, cluster size)?
□ ΔAUC > 0 khi thêm segment vào model dự báo có sẵn?
□ Có tương tác treatment × segment CÓ Ý NGHĨA THỰC TIỄN?
□ Có ít nhất một cặp segment mà ACTION TỐI ƯU KHÁC NHAU?
   (nếu không → segmentation không thay đổi hành vi hệ thống)
□ Segment nhỏ nhất >= 2% và đủ traffic để bandit hội tụ?
□ Mỗi segment có tên, cluster card, action mặc định?
□ Có kiểm tra segment KHÔNG tương quan với thuộc tính nhạy cảm
   theo cách gây rủi ro pháp lý/công bằng? (doc 13)
□ Có kế hoạch retrain + freeze ngữ nghĩa tên segment?
□ Propensity được log để OPE về sau?
```

---

## 11. Khuyến nghị triển khai theo giai đoạn cho SDK

```text
GIAI ĐOẠN 0 — Nền móng (không có ML)
  - Feature store với BA baseline (absolute/personal/cohort) — mục 7.2
  - Propensity logging từ ngày đầu — mục 10.2
  - Rule-based segment thủ công (4-6 nhóm) làm baseline để đánh bại
  - Hierarchical prior theo country × platform × campaign — mục 6.2

GIAI ĐOẠN 1 — Belief state (nâng cấp doc 24)
  - HMM K=4..6 trên per-session observation, forward filtering on-device
  - Prior b_0 từ cohort thay vì uniform
  - Entropy H(b_t) điều khiển mức exploration — mục 6.4
  - Bandit dùng b_t làm context, cập nhật có trọng số — mục 9

GIAI ĐOẠN 2 — Timing (khác biệt hoá lớn nhất)
  - Hawkes exponential kernel univariate cho purchase & session arrival
  - Multivariate Hawkes server-side → excitation matrix cho Control Plane
  - λ*(t) và đạo hàm của nó làm feature timing cho policy — mục 5.4

GIAI ĐOẠN 3 — Representation learning
  - event2vec + pooling làm feature bổ sung (rẻ, làm trước)
  - Sequence encoder multi-task (purchase/churn/LTV/intensity/belief)
  - Time2Vec + gap token cho thời gian bất quy tắc — mục 4.3
  - Two-tower nếu catalog offer đủ lớn để cần retrieval

GIAI ĐOẠN 4 — Causal segmentation
  - Causal forest trên dữ liệu A/B tích luỹ để TÌM segment
    theo heterogeneity thay vì theo feature similarity
  - Chuyển từ "cluster rồi hy vọng" sang "tìm nhóm có hiệu ứng khác"

THỨ TỰ NÀY CÓ CHỦ ĐÍCH: giai đoạn 2 (timing) đứng TRƯỚC
giai đoạn 3 (deep representation) vì nó rẻ hơn, giải thích được
hơn, và tấn công đúng câu hỏi mà đối thủ không trả lời.
```

---

## Không xác minh được (ghi rõ)

1. **Ngưỡng bootstrap Jaccard 0.75 / 0.6** ở mục 1.3 là heuristic được dùng phổ biến trong literature về cluster stability; tôi không xác minh được một nguồn chuẩn duy nhất quy định chính xác các con số này. Nên hiệu chỉnh theo dữ liệu thực tế của từng game.

2. **Ngưỡng PSI 0.1 / 0.25** là quy ước ngành xuất phát từ credit scoring, được dùng rộng rãi nhưng không có cơ sở lý thuyết chặt chẽ. Không xác minh được nguồn gốc nguyên thuỷ.

3. **Con số định lượng cụ thể về sức mạnh dự báo của install-time signal** (mục 6.1) — tôi không xác minh được benchmark công khai đáng tin cho R²/AUC của device tier + country + campaign đối với LTV cá nhân trong mobile game. Phát biểu định tính ("yếu ở mức cá nhân, có ý nghĩa ở mức cohort") là design synthesis dựa trên nguyên lý thống kê, không phải trích dẫn.

4. **Bằng chứng công khai về việc self-excitation của purchase trong mobile game đã được fit bằng Hawkes process ở quy mô production** — tôi không tìm được một case study công bố công khai. Hiện tượng tâm lý (mua lần đầu hạ thấp rào cản cho lần sau) được thảo luận rộng rãi trong doc 29; việc mô hình hoá nó bằng Hawkes là **design synthesis** của doc này, không phải một kết quả đã được công bố cho miền game.

5. **Chi tiết chính xác của replication study về sequential recommendation** (mục 4.1): tôi biết chắc dòng công trình này tồn tại và kết luận chung (baseline được tinh chỉnh kỹ thu hẹp khoảng cách; kết quả nhạy với setup huấn luyện), nhưng không xác minh được ID/venue cụ thể của từng bài trong session này nên cố ý không trích ID.

6. **Số liệu cụ thể trong cluster card mẫu** (mục 8.2) là minh hoạ định dạng, hoàn toàn hư cấu, không phải dữ liệu thực.

7. **Việc dùng ICL thay BIC khi ưu tiên diễn giải** là khuyến nghị dựa trên tính chất đã biết của ICL (phạt entropy phân loại); tôi không xác minh lại nguồn gốc bài báo gốc trong session này.

---

## Nguồn chính

**[Primary research]** — Self-Attentive Sequential Recommendation (SASRec), Kang & McAuley, ICDM 2018 — https://arxiv.org/abs/1808.09781

**[Primary research]** — BERT4Rec: Sequential Recommendation with Bidirectional Encoder Representations from Transformer, Sun et al., CIKM 2019 — https://arxiv.org/abs/1904.06690

**[Primary research]** — Session-based Recommendations with Recurrent Neural Networks (GRU4Rec), Hidasi et al., ICLR 2016 — https://arxiv.org/abs/1511.06939

**[Primary research]** — Time2Vec: Learning a Vector Representation of Time, Kazemi et al., 2019 — https://arxiv.org/abs/1907.05321

**[Primary research]** — The Neural Hawkes Process: A Neurally Self-Modulating Multivariate Point Process, Mei & Eisner, NeurIPS 2017 — https://www.cs.jhu.edu/~hmei/papers/mei+eisner.nips17.poster.pdf

**[Primary research]** — Recurrent Marked Temporal Point Processes: Embedding Event History to Vector, Du et al., KDD 2016 — https://www.researchgate.net/publication/305998324_Recurrent_Marked_Temporal_Point_Processes_Embedding_Event_History_to_Vector

**[Primary research]** — Transformer Hawkes Process, Zuo et al., ICML 2020 — http://proceedings.mlr.press/v119/zuo20a/zuo20a.pdf

**[Primary research]** — Sampling-Bias-Corrected Neural Modeling for Large Corpus Item Recommendations (two-tower, YouTube), Yi et al., RecSys 2019 — https://dl.acm.org/doi/10.1145/3298689.3346996

**[Primary research]** — A Comparison of Methods for Player Clustering via Behavioral Telemetry, Drachen et al., FDG 2013 — https://arxiv.org/abs/1407.3950

**[Primary research]** — Guns and Guardians: Comparative Cluster Analysis and Behavioral Profiling in Destiny, Drachen et al. — https://andersdrachen.com/wp-content/uploads/2014/07/gunsguardians.pdf

**[Primary research]** — Integrating and Inspecting Combined Behavioral Profiling and Social Network Models in Destiny — https://link.springer.com/chapter/10.1007/978-3-319-46100-7_7

**[Primary research]** — Estimating the number of clusters in a data set via the gap statistic, Tibshirani, Walther & Hastie, JRSS-B 2001 — https://rss.onlinelibrary.wiley.com/doi/abs/10.1111/1467-9868.00293

**[Primary research]** — Stop using the elbow criterion for k-means and how to choose the number of clusters instead, Schubert 2022 — https://arxiv.org/pdf/2212.12189

**[Primary research]** — hdbscan: Hierarchical density based clustering (JOSS), McInnes, Healy & Astels — https://www.theoj.org/joss-papers/joss.00205/10.21105.joss.00205.pdf

**[Primary research]** — Density-Based Clustering Based on Hierarchical Density Estimates (HDBSCAN), Campello, Moulavi & Sander, PAKDD 2013 — https://www.researchgate.net/publication/278700103_Density-Based_Clustering_Based_on_Hierarchical_Density_Estimates

**[Primary research]** — Recursive partitioning for heterogeneous causal effects, Athey & Imbens, PNAS 2016 — https://www.pnas.org/doi/10.1073/pnas.1510489113

**[Primary research]** — Improving uplift model evaluation on RCT data (Qini/uplift curves) — https://arxiv.org/pdf/2210.02152

**[Primary research]** — MeLU: Meta-Learned User Preference Estimator for Cold-Start Recommendation, Lee et al., KDD 2019 — https://arxiv.org/pdf/1908.00413

**[Primary research]** — A Meta-Learning Perspective on Cold-Start Recommendations for Items, Vartak et al., NeurIPS 2017 — https://proceedings.neurips.cc/paper/2017/hash/51e6d6e679953c6311757004d8cbbba9-Abstract.html

**[Primary research]** — A Systematic Replicability and Comparative Study of BSARec and SASRec for Sequential Recommendation — https://arxiv.org/html/2506.14692v1

**[Primary research]** — Evaluation of Session-based Recommendation Algorithms, Ludewig & Jannach — https://arxiv.org/pdf/1803.09587

**[Primary research]** — Modeling Events and Interactions through Temporal Processes — A Survey — https://arxiv.org/pdf/2303.06067

**[Primary research]** — Survey on Modeling Intensity Function of Hawkes Process Using Neural Models — https://arxiv.org/pdf/2104.11092

**[Design synthesis]** — Toàn bộ phần mapping segment → policy (mục 9), quy trình đặt tên cluster & cluster card (mục 8.2), ba-baseline feature design (mục 7.2), progressive personalization theo entropy (mục 6.4), lộ trình 5 giai đoạn (mục 11), và việc áp Hawkes vào purchase self-excitation trong mobile game (mục 5.2) là tổng hợp thiết kế của doc này, xây trên các nguồn primary ở trên cộng với doc 04, 05, 24, 28, 29 của KB — không phải trích dẫn nguyên bản từ một công trình đã công bố.
