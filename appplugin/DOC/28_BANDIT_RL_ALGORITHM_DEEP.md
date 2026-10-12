# 28 — Bandit & RL Algorithm Zoo: công thức, chế độ hỏng, và bảng chọn thuật toán

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Tham chiếu kỹ thuật sâu cho decision engine của monetization SDK. Giả định người đọc đã nắm Thompson Sampling, epsilon-greedy, IPS/DR cơ bản (doc 05, doc 24). Tài liệu này đi vào **công thức update thật**, **regret bound**, **chế độ hỏng trong production**, và **bảng quyết định "khi nào dùng cái gì"** theo traffic / số action / độ trễ feedback / mức rủi ro.

## Evidence labels

- **[Primary research]** — paper peer-reviewed hoặc preprint gốc, đã xác minh URL.
- **[Primary docs]** — tài liệu kỹ thuật/thư viện chính chủ.
- **[Secondary]** — tổng hợp, survey, blog kỹ thuật.
- **[Design synthesis]** — suy luận thiết kế của chính KB này, **không phải kết quả đo được**. Mọi con số ngưỡng trong bảng quyết định đều là [Design synthesis] trừ khi ghi rõ khác.

---

## 0. Bản đồ nhanh: chọn tầng bài toán trước, chọn thuật toán sau

Sai lầm phổ biến nhất không phải chọn nhầm LinUCB hay LinTS, mà là **đặt sai tầng bài toán**.

```text
TẦNG 1 — A/B test tĩnh          : ít arm, cần p-value, không cần adapt        → frequentist test
TẦNG 2 — MAB (không context)     : arm ít, population đồng nhất                → TS / UCB1
TẦNG 3 — Contextual bandit       : arm ít-vừa, reward phụ thuộc player state   → LinUCB / LinTS / GLM / Neural
TẦNG 4 — Constrained CB          : có ngân sách ad-load, guardrail retention   → BwK / conservative bandit
TẦNG 5 — Continuous action       : giá, discount %, ad-load rate               → Lipschitz / GP-UCB
TẦNG 6 — Sequential (RL/POMDP)   : action hôm nay đổi state ngày mai           → offline RL / POMDP (doc 24)
```

Quy tắc leo tầng [Design synthesis]: **chỉ leo lên tầng cao hơn khi tầng dưới đã bão hòa và bạn có bằng chứng OPE rằng phần giá trị còn lại nằm ở chiều mà tầng dưới không mô hình hóa được.** Tầng 6 tốn gấp 5–10× công sức vận hành so với tầng 3.

---

## A. Contextual bandit family — công thức thật

### A.1. Setup và các định nghĩa regret

```text
Mỗi round t = 1..T:
  - Nhận context x_t (feature vector của player + slot + thời điểm)
  - Chọn action a_t ∈ A_t  (offer, ad placement, paywall variant, NO_ACTION)
  - Nhận reward r_t ∈ R    (revenue, conversion 0/1, hoặc composite score)

Cumulative regret:
  R(T) = Σ_{t=1..T} [ r_t(a*_t) - r_t(a_t) ],  a*_t = argmax_a E[r | x_t, a]

Simple regret (dùng cho best-arm identification):
  r_simple(T) = E[r(a*)] - E[r(â_T)]   với â_T là arm được chọn SAU T round
```

Khác biệt cốt lõi: **cumulative regret phạt mọi round bạn chọn sai; simple regret chỉ phạt kết luận cuối cùng.** Hai mục tiêu này dẫn tới hai họ thuật toán khác nhau (mục F).

### A.2. Baseline: epsilon-greedy, UCB1, TS

```text
epsilon-greedy:
  a_t = argmax_a  mu_hat_a       với xác suất 1 - eps_t
      = uniform(A)               với xác suất eps_t
  eps_t = min(1, c*K / (d^2 * t))  → regret O(K log T / d^2)
  eps cố định → regret TUYẾN TÍNH O(eps * T * gap). Luôn dùng eps giảm dần.

UCB1 (Auer et al.):
  a_t = argmax_a [ mu_hat_a + sqrt( 2 ln t / n_a ) ]
  Regret: O( Σ_{a≠a*} ln T / delta_a )   (distribution-dependent)
          O( sqrt(K T ln T) )            (minimax)

Thompson Sampling (Bernoulli reward):
  theta_a ~ Beta(alpha_a, beta_a)  ; a_t = argmax_a theta_a
  Update: alpha_{a_t} += r_t ; beta_{a_t} += (1 - r_t)
  Regret: O( Σ ln T / delta_a ), khớp cận dưới Lai–Robbins asymptotically
```

**Vì sao TS thường thắng UCB trong monetization** [Design synthesis, phù hợp với quan sát chung trong industry]:
1. TS cho **propensity ngẫu nhiên có thể tính được** → log lại `p_t = P(a_t | x_t)` để làm IPS/DR sau này. UCB là deterministic → propensity = 1 → **không OPE được** nếu không thêm nhiễu.
2. TS chịu **delayed feedback** tốt hơn: batch update chỉ làm posterior hẹp chậm hơn, không phá vỡ logic. UCB với `n_a` chưa cập nhật sẽ over-explore theo cách giật cục.
3. TS dễ thêm **prior nghiệp vụ** (offer mới khởi tạo từ prior của offer cùng nhóm giá).

**Điểm yếu của TS trong SDK:** cần sample từ posterior mỗi request → chi phí RNG on-device nhỏ, không đáng kể; nhưng cần **seed ổn định theo (user_id, decision_point, policy_version)** để một player không nhận hai quyết định khác nhau khi retry request.

### A.3. LinUCB — disjoint và hybrid [Primary research]

Nguồn: Li, Chu, Langford, Schapire, *A Contextual-Bandit Approach to Personalized News Article Recommendation* (WWW 2010), arXiv:1003.0146.

**Disjoint LinUCB** — mỗi arm có vector tham số riêng, không chia sẻ:

```text
Giả định: E[r_{t,a} | x_{t,a}] = x_{t,a}^T * theta_a

Khởi tạo mỗi arm a:
  A_a = I_d          (d x d)
  b_a = 0_d

Mỗi round:
  theta_hat_a = A_a^{-1} b_a
  p_{t,a} = theta_hat_a^T x_{t,a} + alpha * sqrt( x_{t,a}^T A_a^{-1} x_{t,a} )
  a_t = argmax_a p_{t,a}

Update sau khi thấy r_t:
  A_{a_t} += x_{t,a_t} x_{t,a_t}^T
  b_{a_t} += r_t * x_{t,a_t}

alpha = 1 + sqrt( ln(2/delta) / 2 )
Regret: O( sqrt( d T ln((1+T)/delta) ) )  — tuyến tính theo sqrt(d), không theo K
```

Điểm quan trọng cho SDK: **regret không phụ thuộc số arm K** (chỉ qua hằng số) — đó là lý do LinUCB xử lý được catalog offer lớn, miễn là mỗi offer được mô tả bằng feature (giá, loại vật phẩm, tỉ lệ giảm giá, vị trí) thay vì one-hot ID.

**Hybrid LinUCB** — thêm feature dùng chung giữa các arm:

```text
E[r_{t,a}] = z_{t,a}^T * beta  +  x_{t,a}^T * theta_a
             ^ shared            ^ arm-specific

Giữ thêm:  A_0 (k x k), b_0 (k), và mỗi arm có B_a (d x k)

beta_hat  = A_0^{-1} b_0
theta_hat_a = A_a^{-1} ( b_a - B_a beta_hat )

s_{t,a} = z^T A_0^{-1} z
        - 2 z^T A_0^{-1} B_a^T A_a^{-1} x
        + x^T A_a^{-1} x
        + x^T A_a^{-1} B_a A_0^{-1} B_a^T A_a^{-1} x

p_{t,a} = z^T beta_hat + x^T theta_hat_a + alpha * sqrt(s_{t,a})

Update (rút gọn):
  A_0 += B_a^T A_a^{-1} B_a  ;  b_0 += B_a^T A_a^{-1} b_a     (trừ phần cũ)
  A_a += x x^T ; B_a += x z^T ; b_a += r x
  A_0 += z z^T - B_a^T A_a^{-1} B_a  ;  b_0 += r z - B_a^T A_a^{-1} b_a
```

**Khi nào cần hybrid** [Design synthesis]: khi bạn có **nhiều arm cold-start liên tục** (mỗi tuần thêm 5 offer mới). Shared component `beta` học "hiệu ứng chung của discount depth trên toàn catalog" và transfer ngay sang offer mới. Nếu catalog cố định 6–10 offer chạy nhiều tháng → disjoint đủ, và rẻ hơn nhiều về code.

**Chi phí tính toán:** `A_a^{-1}` cập nhật bằng Sherman–Morrison, O(d²) mỗi update thay vì O(d³):

```text
A^{-1}_new = A^{-1} - (A^{-1} x x^T A^{-1}) / (1 + x^T A^{-1} x)
```

Với d ≤ 64 (feature player rút gọn) thì d² = 4096 phép nhân — chạy được cả on-device.

### A.4. LinTS — Thompson Sampling cho linear payoff [Primary research]

Nguồn: Agrawal & Goyal, *Thompson Sampling for Contextual Bandits with Linear Payoffs*, ICML 2013, arXiv:1209.3352. Regret bound `Õ(d^{3/2} sqrt(T))` — worse hơn LinUCB về d, nhưng thường tốt hơn trong thực nghiệm.

```text
B = I_d ; mu_hat = 0_d ; f = 0_d
v^2 = R^2 * (24/eps) * d * ln(1/delta)     (R = sub-Gaussian scale của noise)

Mỗi round:
  mu_tilde ~ N( mu_hat, v^2 * B^{-1} )      ← SAMPLE 1 LẦN cho toàn bộ round
  a_t = argmax_a  x_{t,a}^T mu_tilde

Update:
  B += x_{t,a_t} x_{t,a_t}^T
  f += x_{t,a_t} * r_t
  mu_hat = B^{-1} f
```

**Chi tiết dễ sai trong implementation:** sample `mu_tilde` **một lần mỗi round rồi mới so sánh mọi arm**, không sample lại cho từng arm. Sample lại từng arm phá vỡ tương quan giữa các arm và biến thuật toán thành thứ over-explore nghiêm trọng khi các arm chia sẻ feature.

**Tuning `v` thực tế:** giá trị lý thuyết của `v` gần như luôn quá lớn cho production. Thực hành phổ biến là đặt `v` như một hyperparameter, tune bằng replay/OPE trên log, thường trong khoảng 0.1–1.0 [Design synthesis].

### A.5. GLM / Logistic bandit — thường đúng hơn LinUCB cho monetization

Reward trong monetization phần lớn là **binary conversion** hoặc **revenue lệch cực mạnh (zero-inflated + long tail)**. Mô hình tuyến tính trên reward thô vi phạm giả định noise sub-Gaussian đối xứng.

```text
Logistic bandit:
  P(convert | x, a) = sigma( x_{t,a}^T theta ),  sigma(z) = 1/(1+e^{-z})

  theta_hat_t = argmin_theta  Σ_{s<t} [ -y_s ln sigma(x_s^T theta)
                                        -(1-y_s) ln(1 - sigma(x_s^T theta)) ]
                              + (lambda/2)||theta||^2

  H_t = lambda*I + Σ_{s<t} sigma'(x_s^T theta_hat) x_s x_s^T     (Fisher information)
  UCB: sigma( x^T theta_hat ) + alpha * sqrt( x^T H_t^{-1} x ) * sigma'(...)
```

Kiến trúc thực dụng cho revenue [Design synthesis]:

```text
E[revenue | x, a] = P(purchase | x, a) * E[price | purchase, x, a]
                     ^ logistic bandit    ^ deterministic (giá đã biết) hoặc regression
```

Tách hai phần này làm giảm variance rất mạnh so với bandit trực tiếp trên revenue, và cho phép guardrail riêng cho từng thành phần (ví dụ: cấm tăng ARPPU bằng cách chỉ đẩy giá cao cho whale).

### A.6. NeuralUCB / NeuralTS / Neural-Linear [Primary research]

Nguồn: Zhou, Li, Gu, *Neural Contextual Bandits with UCB-based Exploration*, ICML 2020, arXiv:1911.04462. Đạt regret `Õ(sqrt(T))` — bound near-optimal đầu tiên cho neural bandit.

```text
f(x; W) = mạng neural, gradient g(x; W) = ∇_W f(x; W)  (chiều p = số tham số)

Z_t = lambda*I_p + Σ_{s<t} g(x_s; W_s) g(x_s; W_s)^T / m     (m = width)

UCB_a = f(x_a; W_t) + gamma * sqrt( g(x_a;W_t)^T Z_t^{-1} g(x_a;W_t) / m )
a_t = argmax_a UCB_a

NeuralTS: thay bằng r_tilde_a ~ N( f(x_a;W_t), nu^2 * g^T Z_t^{-1} g / m )
```

**Vấn đề thực tế:** `Z_t` có kích thước p × p với p = số tham số mạng. Với mạng nhỏ nhất cũng là hàng chục nghìn tham số → không khả thi nếu làm đúng nguyên bản. Hai cách thoát:

1. **Diagonal approximation** của `Z_t` — mất tương quan nhưng O(p) bộ nhớ.
2. **Neural-Linear** (thực dụng hơn, được dùng rộng rãi): huấn luyện mạng bình thường offline, **đóng băng mọi layer trừ layer cuối**, rồi chạy LinUCB/LinTS **trên embedding của layer áp chót**.

```text
Neural-Linear:
  phi(x, a) = penultimate embedding (dim d' ~ 32-128), cập nhật theo chu kỳ (offline)
  Bayesian linear regression trên phi:  A += phi phi^T ; b += r*phi
  Exploration = LinTS trên (A, b)
```

Đây là **kiến trúc mặc định nên chọn cho SDK** [Design synthesis]: representation học offline theo nhịp ngày, exploration/exploitation chạy online theo nhịp phút, và artifact gửi xuống device chỉ gồm `(weights mạng đông cứng, A^{-1}, b)`.

**Khi nào neural mới xứng đáng:** khi số feature > ~50 và có **tương tác phi tuyến thật** (ví dụ: hiệu ứng của discount phụ thuộc phi tuyến vào session-depth × ngày kể từ lần mua cuối). Dưới ngưỡng đó, linear + feature engineering thủ công (bin, cross-feature) thường hòa hoặc thắng, với chi phí vận hành thấp hơn nhiều.

### A.7. Tree / GBM bandits

Không có bound đẹp như LinUCB, nhưng cực kỳ thực dụng vì feature monetization đa phần là **tabular, hỗn hợp categorical + đếm, có ngưỡng rõ rệt** — đúng địa hình của GBM.

Ba cách tạo uncertainty cho tree model:

```text
(1) Bootstrap TS (Bootstrapped ensembles)
    Huấn luyện B mô hình (B = 10-30) trên B bootstrap sample của log.
    Mỗi request: chọn ngẫu nhiên b ~ Uniform(1..B), dùng model_b để rank.
    → Xấp xỉ posterior sampling. Propensity xấp xỉ được bằng
      p(a|x) = (1/B) * #{b : argmax_a f_b(x,a) = a}   (tính được offline).

(2) Random forest quantile / variance từ phân tán giữa cây
    mu_hat(x,a) = mean over trees ; sigma_hat(x,a) = std over trees
    UCB = mu_hat + alpha * sigma_hat        (heuristic, KHÔNG có bound)

(3) GBM + residual variance head
    Model 1 dự đoán mean, Model 2 dự đoán |residual| → sigma_hat.
```

**Cảnh báo** [Design synthesis]: (2) và (3) là heuristic. Ensemble variance **under-estimate** uncertainty ở vùng không có dữ liệu (mọi cây cùng extrapolate như nhau, cùng sai) — đúng vùng bạn cần explore nhất. Nếu dùng tree bandit, **bắt buộc** giữ một sàn exploration cưỡng bức: `p_min` cho mọi arm hợp lệ (ví dụ 2–5%) để đảm bảo log không bị deterministic-collapse và OPE còn khả thi.

### A.8. Bảng so sánh regret

```text
Thuật toán       Giả định model      Regret                     Propensity  Chi phí update
---------------  ------------------  -------------------------  ----------  --------------
eps-greedy (dec) không                O(K ln T / d^2)            có, dễ      O(1)
UCB1             không                O(sqrt(K T ln T))          KHÔNG       O(1)
TS (Beta)        Bernoulli            O(Σ ln T / delta_a)        có          O(1)
LinUCB           linear               O(sqrt(d T ln T))          KHÔNG       O(d^2)
LinTS            linear               Õ(d^{3/2} sqrt(T))         có          O(d^2)
GLM-UCB          logistic             Õ(d sqrt(T)) *             KHÔNG       O(d^2)/iter
NeuralUCB        NN realizable        Õ(sqrt(T))                 KHÔNG       nặng
Neural-Linear    NN + linear head     không có bound chặt        có          O(d'^2)
Tree/GBM boot    không                không có bound             xấp xỉ      nặng (batch)
GP-UCB           GP smoothness        Õ(sqrt(d T gamma_T))       KHÔNG       O(n^3)/O(n^2)
```

`*` hằng số của GLM-UCB phụ thuộc `1/kappa` với `kappa = min sigma'(x^T theta)`, có thể rất xấu khi conversion rate cực thấp — một lý do thực tế để chuộng TS-style trên bài toán CVR ~1%.

---

## B. Non-stationarity: player drift, meta change, seasonality

Đây là chỗ hầu hết bandit production chết. Player base tháng 3 không phải player base tháng 6; một update meta game làm đảo giá trị mọi offer; sale Black Friday làm đảo baseline chuyển đổi.

### B.1. Ba dạng non-stationarity, ba cách xử lý khác nhau

```text
(a) Abrupt / switching   : meta patch, đổi giá store, đổi ad network waterfall
    → change-point detection + reset, hoặc sliding-window

(b) Slow drift           : cohort composition đổi dần theo UA mix
    → discounting / exponential forgetting

(c) Seasonal / cyclic    : cuối tuần, giờ trong ngày, sự kiện lặp
    → KHÔNG discount; đưa vào CONTEXT (feature), giữ mô hình stationary
```

Sai lầm phổ biến nhất: dùng discounting để "xử lý" seasonality. Điều đó chỉ làm mô hình quên nhanh hơn và học lại cùng một pattern mỗi tuần. **Cái gì lặp lại và quan sát được → đưa vào `x_t`.** Discounting chỉ dành cho cái không quan sát được.

### B.2. Discounted UCB và Sliding-Window UCB [Primary research]

Nguồn: Garivier & Moulines, *On Upper-Confidence Bound Policies for Non-Stationary Bandit Problems*, ALT 2011, arXiv:0805.3415. Cả hai đều khớp cận dưới sai khác một hệ số logarithm.

```text
Discounted UCB (D-UCB), tham số gamma ∈ (0,1):
  N_t(gamma, a) = Σ_{s≤t} gamma^{t-s} * 1{a_s = a}
  X_t(gamma, a) = Σ_{s≤t} gamma^{t-s} * r_s * 1{a_s = a}
  mu_hat = X_t / N_t
  n_t(gamma) = Σ_a N_t(gamma, a)
  c_t(gamma, a) = 2B * sqrt( xi * ln(n_t(gamma)) / N_t(gamma, a) )
  a_t = argmax_a [ mu_hat + c_t ]

Sliding-Window UCB (SW-UCB), cửa sổ tau:
  Chỉ dùng tau observation gần nhất; công thức UCB như trên với
  N_t(tau, a) = Σ_{s=t-tau+1..t} 1{a_s = a}
  c_t(tau, a) = B * sqrt( xi * ln(min(t,tau)) / N_t(tau,a) )
```

Với `Y_T` change-point trong T round, regret của cả hai là `O( sqrt(Y_T * T) * ln T )` khi tham số được chỉnh theo `Y_T` — nghĩa là **bạn không thể tránh trả giá cho drift**, chỉ có thể trả giá tối ưu.

**Ánh xạ sang tham số production** [Design synthesis]: chọn `gamma` theo half-life mong muốn.

```text
half_life = ln(2) / ln(1/gamma)   (tính bằng số observation của arm đó)

gamma = 0.9995 → half-life ≈ 1386 obs
gamma = 0.999  → half-life ≈  693 obs
gamma = 0.995  → half-life ≈  138 obs
```

Với một segment có ~2000 impression/ngày trên một decision point, `gamma = 0.999` cho half-life khoảng 8 giờ — quá nhanh, sẽ nhiễu. Half-life hợp lý cho monetization thường là **7–21 ngày**; hãy tính ngược từ throughput thực tế của segment nhỏ nhất, không phải segment lớn nhất.

### B.3. Discounted linear bandit (weighted ridge)

Mở rộng trực tiếp sang LinUCB/LinTS bằng cách discount chính các sufficient statistics:

```text
A_t = gamma * A_{t-1} + x_t x_t^T + (1 - gamma) * lambda * I
b_t = gamma * b_{t-1} + r_t * x_t
```

Số hạng `(1-gamma)*lambda*I` giữ `A_t` không bị co về 0 và neo posterior về prior. Không có nó, `A_t` sẽ hội tụ về trạng thái mà uncertainty phồng lên vô hạn ở các hướng không được thăm.

### B.4. Exponential forgetting cho posterior của TS

```text
Beta-Bernoulli TS có forgetting:
  alpha_a ← gamma * alpha_a + (1-gamma) * alpha_0 + r_t
  beta_a  ← gamma * beta_a  + (1-gamma) * beta_0  + (1 - r_t)

Gaussian posterior có forgetting (variance inflation):
  Trước mỗi chu kỳ (ví dụ mỗi ngày):
     Sigma ← Sigma + Q          (Q = process noise, giống Kalman filter)
     hoặc  Sigma ← Sigma / rho  (rho ∈ (0,1), inflate variance)
```

Dạng Kalman (`Sigma += Q`) là dạng nên dùng cho SDK [Design synthesis]: nó có ý nghĩa vật lý rõ (thế giới trôi với tốc độ `Q` mỗi ngày), tách bạch với `gamma`, và cho phép đặt drift rate khác nhau cho từng nhóm feature — ví dụ hệ số của `days_since_install` ổn định, hệ số của `current_meta_tier` trôi nhanh.

### B.5. Change-point detection

Hai họ chính trong literature bandit piecewise-stationary: **CUSUM-UCB** (change detection dựa trên cumulative sum) và **M-UCB** (monitored UCB, so sánh trung bình hai nửa cửa sổ). Cả hai cùng khung: chạy UCB bình thường + một detector cho từng arm; khi detector báo động thì **reset toàn bộ thống kê của arm đó** (hoặc của tất cả arm) và bắt buộc explore lại.

```text
CUSUM (one-sided), cho từng arm a:
  Ước lượng mu_0 từ M mẫu đầu tiên sau reset.
  s_k^+ = (r_k - mu_0) - epsilon
  g_k^+ = max(0, g_{k-1}^+ + s_k^+)
  g_k^- tương tự với dấu ngược
  Báo động khi max(g^+, g^-) > h

M-UCB (monitored):
  Giữ cửa sổ w mẫu gần nhất của arm a.
  Báo động khi | mean(nửa sau w/2) - mean(nửa đầu w/2) | > b
  Sau báo động: reset toàn bộ, forced exploration đều trong K round.
```

**Trade-off nghiệt ngã:** detector nhạy → false alarm → reset liên tục → mất hết dữ liệu đã học. Detector chậm → chịu regret tuyến tính trong khoảng chưa phát hiện. Trong monetization, **phần lớn change-point là biết trước** (bạn ship patch, bạn đổi giá, bạn đổi waterfall). Thực dụng hơn nhiều: **change-point có nhãn từ hệ thống** — đưa `content_version`, `price_tier_version`, `meta_season_id` vào context feature, và trigger reset/re-warm-start bằng deployment event thay vì bằng thống kê [Design synthesis].

### B.6. Drift detection trong production (monitoring, không phải thuật toán)

Bốn tín hiệu cần theo dõi liên tục:

```text
1. Feature drift        : PSI (Population Stability Index) trên từng feature
   PSI = Σ_bins (p_new - p_old) * ln(p_new / p_old)
   PSI < 0.1 ổn định | 0.1-0.25 cần chú ý | > 0.25 drift đáng kể

2. Calibration drift    : reliability curve của model score vs outcome thực
   Chỉ số gọn: Expected Calibration Error (ECE) theo bin, và slope của
   regression outcome ~ predicted (kỳ vọng 1.0)

3. Policy drift         : entropy của action distribution
   H(pi) = -Σ_a p(a) ln p(a). Entropy sụp về 0 = policy collapse,
   không còn khả năng OPE và không còn khả năng phát hiện arm tốt hơn.

4. Reward drift         : mean reward per arm theo cửa sổ trượt,
   với CI; kèm SRM check (sample ratio mismatch) trên phân bổ arm.
```

Trong đó **(3) entropy floor là guardrail rẻ nhất và có giá trị nhất**: đặt sàn `p_min` sao cho mọi arm hợp lệ giữ được xác suất tối thiểu; nếu không, log của bạn sẽ mất khả năng trả lời "arm B lẽ ra tốt hơn không?" chỉ sau vài tuần.

---

## C. Delayed và aggregated feedback

Vấn đề cốt lõi của monetization: **impression xảy ra bây giờ, purchase xảy ra sau vài giờ tới vài ngày, LTV thật chỉ biết sau 30–180 ngày.**

### C.1. Mô hình delayed conversion [Primary research]

Nguồn: Vernade, Cappé, Perchet, *Stochastic Bandit Models for Delayed Conversions*, UAI 2017, arXiv:1706.09186. Mở rộng tuyến tính: Vernade et al., *Linear Bandits with Stochastic Delayed Feedback*, ICML 2020.

```text
Mô hình: action a → conversion Bernoulli(theta_a) → nếu convert, xảy ra sau
         delay D ~ tau (phân phối delay, giả định đã biết hoặc ước lượng riêng)

Tại thời điểm t, quan sát bị CENSOR ở cửa sổ m:
  P(quan sát thấy convert trong m round) = theta_a * tau(D <= m)

→ Naive estimator BIASED XUỐNG một cách hệ thống, và bias khác nhau giữa arm
  nếu delay distribution khác nhau theo arm (rất thường xảy ra:
  offer giá cao có delay dài hơn offer giá thấp).

Corrected estimator:
  theta_hat_a = ( #conversion quan sát được trong cửa sổ m ) /
                ( Σ_{s: a_s = a} tau(D <= t - s) )
                ^ mẫu số = "effective exposure", không phải số impression
```

Đây là điểm sửa quan trọng nhất và cũng dễ bỏ sót nhất: **mẫu số phải là tổng xác suất quan sát được, không phải số lần hiển thị.** Một impression 10 phút trước đóng góp `tau(D <= 10 phút)` vào mẫu số, không phải 1.0.

### C.2. Ba pattern xử lý delay trong production

```text
PATTERN 1 — Proxy reward + calibration
  r_proxy = f(click, tutorial_step, add_to_cart, store_open, first_session_len)
  Học ánh xạ g: r_proxy → E[LTV_d30] trên dữ liệu lịch sử ĐÃ ĐỦ CHÍN.
  Bandit chạy trên g(r_proxy). Re-fit g theo tuần.
  Rủi ro: Goodhart — policy học cách maximize proxy mà không maximize LTV.
  Bắt buộc: kiểm tra tương quan proxy↔LTV THEO ARM, không chỉ toàn cục.

PATTERN 2 — Reward khép cửa sổ (windowed reward)
  Định nghĩa reward = revenue trong T_w giờ sau decision (T_w = 24h hoặc 72h).
  Bandit chỉ update khi cửa sổ đã đóng → delay cố định, dễ suy luận.
  Đánh đổi: chậm hơn; nhưng ổn định và không cần mô hình delay.

PATTERN 3 — Two-stage / hierarchical reward
  Stage 1 (nhanh): bandit trên immediate signal, cập nhật mỗi giờ.
  Stage 2 (chậm): mô hình LTV cập nhật hàng tuần, điều chỉnh PRIOR của stage 1.
  → Kết hợp tốc độ phản ứng và tính đúng đắn dài hạn.
```

**Khuyến nghị cho SDK** [Design synthesis]: Pattern 2 làm mặc định (T_w = 24h hoặc 72h tùy decision point), Pattern 3 khi đã có mô hình LTV đủ tin cậy. Pattern 1 đơn thuần chỉ dùng khi traffic quá thấp để chờ cửa sổ đóng — và luôn kèm guardrail LTV-d7 chạy song song.

### C.3. Attribution window và tác dụng phụ lên bandit

```text
Vấn đề: một purchase có thể bị nhiều decision point cùng "nhận công".
Nếu cả paywall_trigger, offer_popup, và rewarded_ad_prompt đều nhận credit
cho cùng một purchase → tổng reward được log > revenue thật
→ mọi arm trông đều tốt hơn thực tế → policy over-trigger toàn hệ thống.

Ba lựa chọn:
  (a) Last-touch trong cửa sổ W: đơn giản, nhưng thiên vị decision point
      đứng gần thời điểm mua nhất (thường là paywall).
  (b) Chia đều / có trọng số: giảm bias nhưng làm reward mờ, tăng variance.
  (c) TÁCH BÀI TOÁN: mỗi decision point là một bandit riêng với reward riêng
      và guardrail chung ở tầng orchestrator.

(c) là lựa chọn đúng cho kiến trúc SDK. Không cố giải attribution bằng bandit;
giải bằng thiết kế: mỗi bandit sở hữu một reward khép kín, không chồng lấn.
```

### C.4. Delay và exploration rate

Delay tương tác xấu với exploration: trong khoảng chưa có feedback, thuật toán tiếp tục hành động dựa trên thông tin cũ. Với TS điều này tương đối lành tính (posterior chưa hẹp lại → tiếp tục explore). Với UCB, `n_a` tăng nhưng `mu_hat_a` chưa cập nhật → **UCB bị co lại dựa trên dữ liệu chưa tồn tại** — đây là chế độ hỏng cụ thể, và là một lý do kỹ thuật rõ ràng để chọn TS khi delay lớn.

Chỉnh sửa đơn giản: **chỉ tăng `n_a` khi reward đã thực sự về**, giữ hàng đợi các impression chưa khép. Đây là ý tưởng của các thuật toán queue-based cho delayed feedback.

---

## D. Batched / offline bandits — ràng buộc bắt buộc của mobile SDK

### D.1. Vì sao không thể update per-impression

```text
- Round-trip mạng mỗi quyết định: không chấp nhận được (ad/offer phải hiện < 100ms,
  và thiết bị có thể offline).
- Battery/data: gửi mỗi impression ngay lập tức là không khả thi; SDK batch log.
- Backend contention: hàng chục nghìn QPS ghi vào cùng posterior của một arm.
- Feedback delay (mục C) khiến "per-impression update" là ảo tưởng ngay cả khi
  hạ tầng cho phép.

→ Kiến trúc bắt buộc: POLICY ARTIFACT.
  Backend học theo batch → sinh artifact → device tải về → device SCORE cục bộ.
```

### D.2. Batched bandit — cái giá về mặt lý thuyết

Kết quả nền tảng của literature batched bandit: với M batch, có thể đạt regret gần bằng fully-sequential khi **M tăng theo cỡ logarithm của T** và **độ dài batch tăng theo cấp số nhân (grid hình học)**.

```text
Grid hình học: batch boundary tại  t_i = ceil( T^{(2 - 2^{1-i}) / (2 - 2^{1-M}) } )
Ý nghĩa thực dụng: batch đầu NGẮN (học nhanh khi còn mù),
batch sau DÀI (đã biết nhiều, không cần cập nhật gấp).
```

Ánh xạ sang cadence refresh artifact [Design synthesis]:

```text
Ngày 0-2 của một arm/offer mới : refresh mỗi 1-4 giờ
Ngày 3-7                        : refresh mỗi 6-12 giờ
Sau tuần 1 (steady state)       : refresh mỗi 24 giờ
Sau change-point có nhãn        : quay lại nhịp ngày 0
```

Điều này khớp trực tiếp với cả lý thuyết batched bandit lẫn thực tế vận hành: bạn không cần (và không nên) refresh policy mỗi giờ cho một offer đã chạy 3 tháng.

### D.3. Cấu trúc policy artifact

```text
artifact = {
  policy_version: string,          // bắt buộc, log kèm mọi decision
  created_at, valid_until,         // TTL: hết hạn → fallback về default policy
  feature_spec: [...],             // tên + phép biến đổi + giá trị mặc định khi thiếu
  segments: [ {rule, model_ref} ], // routing sang model theo segment
  models: {
     model_ref: {
        type: "lints" | "beta_ts" | "neural_linear" | "table",
        params: { A_inv, b, v } | { alpha, beta } | { W, A_inv, b } | lookup table,
        p_min: 0.02,               // sàn exploration, bắt buộc
     }
  },
  guardrails: { max_ad_load, max_offers_per_session, cooldowns, price_bounds },
  fallback: { deterministic default action per decision point }
}
```

Bốn yêu cầu phi-thương-lượng [Design synthesis]:

1. **`policy_version` phải được log cùng mọi decision** — không có nó thì OPE và điều tra sự cố đều bất khả.
2. **`p_min` sàn exploration** — bảo vệ khả năng học và khả năng OPE.
3. **TTL + fallback** — device có thể offline nhiều ngày; artifact cũ 30 ngày nguy hiểm hơn không có artifact.
4. **Propensity `p_t` được tính và log tại device**, không tái tạo lại ở backend. Tái tạo luôn lệch (đua version, feature thiếu, thứ tự arm khác) và làm hỏng mọi ước lượng IPS.

### D.4. Warm-start từ logged data

```text
BƯỚC 1 — Kiểm tra chất lượng log
  - Có propensity không? Nếu KHÔNG, chỉ dùng được direct method (biased),
    không dùng được IPS/DR. Ghi rõ hạn chế này.
  - Overlap: với mỗi (segment, arm), số quan sát >= ngưỡng tối thiểu?
  - Effective sample size: ESS = (Σ w_i)^2 / Σ w_i^2, với w_i = pi_e/pi_0.
    ESS / n < 0.1 → ước lượng không đáng tin, cần shrinkage/clipping mạnh.

BƯỚC 2 — Fit reward model q_hat(x, a) trên toàn bộ log (kể cả không có propensity)
BƯỚC 3 — Khởi tạo posterior từ q_hat, KHÔNG khởi tạo bằng số đếm thô
  LinTS:   A_0 = lambda*I + kappa * Σ x x^T ;  b_0 = kappa * Σ r x
           kappa ∈ (0,1] = HỆ SỐ TIN TƯỞNG vào dữ liệu cũ.
           kappa = 0.1-0.3 khi log đến từ policy khác / thời kỳ khác.
  Beta-TS: alpha_0 = 1 + kappa*n_a*mu_hat_a ; beta_0 = 1 + kappa*n_a*(1-mu_hat_a)

BƯỚC 4 — Chạy shadow mode 3-7 ngày: policy mới tính quyết định nhưng KHÔNG áp dụng;
         so sánh OPE-estimate với outcome thật của policy đang chạy.
BƯỚC 5 — Ramp: 1% → 5% → 20% → 50%, mỗi bậc chờ đủ để guardrail có power.
```

`kappa` là tham số quan trọng bị bỏ quên nhiều nhất. Khởi tạo với `kappa = 1` trên log của policy cũ khiến bandit **cực kỳ tự tin vào một thế giới không còn tồn tại** và gần như không explore nữa.

---

## E. Constrained / safe bandits — ánh xạ trực tiếp sang guardrail engine

### E.1. Bandits with Knapsacks (BwK) [Primary research]

Nguồn: Badanidiyuru, Kleinberg, Slivkins, *Bandits with Knapsacks*, FOCS 2013, arXiv:1305.2545.

```text
Mỗi arm a, ngoài reward r, còn tiêu thụ d loại tài nguyên:
  c_1(a), ..., c_d(a) ~ phân phối chưa biết
Ngân sách B_1, ..., B_d. Quá trình dừng khi MỘT tài nguyên cạn.
Mục tiêu: tối đa tổng reward trước khi cạn.

Kết quả then chốt: policy tối ưu là một PHÂN PHỐI TRỘN trên các arm
(giải LP), KHÔNG phải một arm đơn lẻ. Đây là khác biệt căn bản so với MAB thường.

LP tối ưu (fluid relaxation):
  max_over p   Σ_a p(a) * mu_a
  s.t.         Σ_a p(a) * c_j(a) <= B_j / T   với mọi j
               Σ_a p(a) = 1, p >= 0
```

**Ánh xạ monetization** [Design synthesis] — đây là mô hình tự nhiên nhất cho ad-load và offer pressure:

```text
Tài nguyên 1: "ngân sách khó chịu" (annoyance budget) mỗi session
              c(interstitial) cao, c(rewarded_offer) thấp, c(NO_ACTION) = 0
Tài nguyên 2: số offer tối đa mỗi ngày (fatigue)
Tài nguyên 3: "ngân sách rủi ro churn" — chi phí kỳ vọng về retention
Reward:       revenue (hoặc LTV-proxy đã hiệu chỉnh)
```

Kết luận thiết kế rút ra từ BwK: **guardrail không nên là filter cứng đứng sau bandit** ("bandit chọn xong, guardrail chặn"). Nó nên là **ràng buộc tài nguyên trong chính bài toán tối ưu**, vì lời giải đúng là một *mixture* — ví dụ "70% session hiển thị rewarded nhẹ, 20% interstitial, 10% im lặng" — chứ không phải "luôn chọn arm tốt nhất rồi thi thoảng bị chặn". Kiến trúc filter-sau tạo ra bias hệ thống: bandit học từ log mà arm tốt nhất bị chặn không đều, và propensity đã log không còn khớp với hành vi thật.

**Lagrangian BwK** là cách triển khai thực dụng:

```text
Thay vì giải LP mỗi round, giữ dual variable lambda_j cho từng ràng buộc:
  reward hiệu chỉnh:  r_tilde(a) = r(a) - Σ_j lambda_j * c_j(a)
  chạy bandit bình thường trên r_tilde
  cập nhật dual:  lambda_j ← lambda_j * exp( eta * (c_j_observed - B_j/T) )
                  (multiplicative weights / mirror descent, rồi clip)
```

Đây là một **guardrail engine đúng nghĩa**: `lambda_j` chính là "giá bóng" (shadow price) của guardrail j. Khi ad-load vượt ngân sách, `lambda_ad` tăng, mọi arm quảng cáo tự động bị phạt nặng hơn — trơn tru, không giật cục như hard filter, và **quan sát được**: dashboard `lambda_j` theo thời gian là công cụ chẩn đoán tuyệt vời.

### E.2. Conservative bandits

Ràng buộc: performance tích lũy không được tụt dưới `(1-alpha)` lần baseline **tại mọi thời điểm**, không chỉ ở cuối.

```text
Ràng buộc conservative:
  Σ_{s=1..t} r_s  >=  (1 - alpha) * t * mu_baseline    với mọi t, w.h.p.

Cơ chế: duy trì "budget an toàn"
  budget_t = Σ_{s<=t} r_s - (1-alpha) * t * mu_baseline
  Nếu budget_t đủ lớn để hấp thụ trường hợp xấu nhất của việc explore
     → cho phép chọn arm exploratory
  Ngược lại → chơi baseline arm
```

Đây chính là **"exploration được cấp phép bằng lợi nhuận đã kiếm được"** — mô hình tinh thần rất dễ bán cho stakeholder: "chúng ta chỉ mạo hiểm bằng phần vượt trội mà hệ thống đã tạo ra." `alpha = 0.05` là giá trị khởi điểm hợp lý cho monetization [Design synthesis]: chấp nhận tối đa 5% suy giảm so với policy hiện hành, được đo tích lũy.

### E.3. High-confidence off-policy improvement [Primary research]

Nguồn: Thomas, Theocharous, Ghavamzadeh, *High-Confidence Off-Policy Evaluation* (AAAI 2015) và *High Confidence Policy Improvement* (ICML 2015).

```text
Ý tưởng: KHÔNG deploy policy mới trừ khi ta chứng minh được, với độ tin cậy 1-delta,
rằng nó không tệ hơn policy hiện hành.

  Deploy pi_new  ⟺  LowerBound_{1-delta}( V(pi_new) )  >  V(pi_behavior)

LowerBound tính từ concentration inequality trên IPS/DR estimate:
  - Hoeffding trên importance weight đã clip (lỏng, an toàn)
  - Empirical Bernstein (chặt hơn khi variance thấp)
  - Bias-corrected bootstrap percentile (chặt nhất, chỉ approximate)

Nếu lower bound không vượt baseline → TRẢ VỀ policy cũ (no-solution-found).
```

Đây là **cổng release cho decision engine** và nên được tự động hóa hoàn toàn: một artifact chỉ được ký và phát hành khi vượt qua gate này trên tập log holdout. Chi tiết vận hành quan trọng: bound phải tính trên **log của một exploration policy có randomization đủ** — nếu policy hiện hành đã collapse về deterministic, mọi bound đều vô dụng (lại là lý do cho `p_min`).

### E.4. Ánh xạ tổng hợp sang guardrail engine

```text
Guardrail                    Cơ chế thuật toán đúng            Không nên dùng
---------------------------  --------------------------------  ------------------
Ad-load trần mỗi session     BwK resource + Lagrangian         hard filter sau bandit
Cooldown giữa 2 offer        ràng buộc trên action space
                             (loại khỏi A_t, ghi vào log)      lọc ngầm không log
Không tụt revenue > 5%       conservative bandit budget        chỉ theo dõi dashboard
Không tụt D7 retention       guardrail metric + HCOPE gate     A/B test thủ công
Price floor/ceiling          domain constraint trên action     phạt trong reward
Chặn dark pattern / policy   hard constraint TUYỆT ĐỐI         bất kỳ cơ chế học nào
```

Dòng cuối quan trọng: ràng buộc đạo đức và ràng buộc chính sách store **không bao giờ** được biểu diễn dưới dạng penalty trong reward — penalty luôn có thể bị "mua" bằng đủ revenue. Chúng phải là ràng buộc cứng, áp trước khi bandit nhìn thấy action space, và **được ghi vào log** (arm nào đã bị loại và vì sao) để propensity vẫn đúng.

---

## F. Best-arm identification vs regret minimization

Hai mục tiêu này **xung đột nhau** — thuật toán tối ưu cho cái này không tối ưu cho cái kia.

```text
Regret minimization     : "kiếm nhiều tiền nhất TRONG KHI học"
                          → dành ít mẫu cho arm rõ ràng kém
Best-arm identification : "tìm ra arm tốt nhất nhanh nhất và chắc chắn nhất"
                          → dành NHIỀU mẫu cho các arm ĐANG CẠNH TRANH nhau,
                            kể cả khi đang mất tiền
```

### F.1. Hai chế độ BAI

```text
FIXED CONFIDENCE: cho trước delta, tối thiểu hóa số mẫu để kết luận đúng ≥ 1-delta
  - LUCB: mỗi round lấy mẫu arm tốt nhất hiện tại VÀ arm có UCB cao thứ hai;
          dừng khi LCB(best) > UCB(second).
  - Track-and-Stop: giải bài toán tối ưu để tìm TỈ LỆ LẤY MẪU tối ưu w*(mu),
          bám theo tỉ lệ đó, dừng theo generalized likelihood ratio test.
          Đạt sample complexity tối ưu tiệm cận.

FIXED BUDGET: cho trước N mẫu, tối thiểu hóa xác suất chọn sai
  - Successive Halving / Sequential Halving: chia budget thành log2(K) pha;
          mỗi pha lấy mẫu đều các arm còn lại, loại bỏ nửa kém nhất.
          Rất đơn giản, rất mạnh, đặc biệt khi K lớn.
  - UCB-E, Successive Rejects.
```

### F.2. Khi nào dùng cái nào trong monetization

```text
Dùng BAI khi:
  - Cần chọn MỘT cấu hình để ship cho toàn bộ user (paywall layout, pricing tier
    mặc định, onboarding flow) — quyết định một lần, ảnh hưởng lâu dài.
  - Chi phí duy trì nhiều biến thể là cao (art asset, localization, QA).
  - Cần một câu trả lời "cái nào thắng" để giao tiếp với team ngoài data.
  - Số arm lớn nhưng cần cắt tỉa nhanh → Sequential Halving.

Dùng regret minimization khi:
  - Personalization là mục tiêu: không có "một arm tốt nhất", chỉ có
    "arm tốt nhất CHO CONTEXT NÀY" → BAI vô nghĩa, dùng contextual bandit.
  - Arm liên tục thay đổi (offer theo mùa, live-ops) → không kịp "kết luận".
  - Chi phí explore trực tiếp là doanh thu → cần adaptive.

Pattern lai (thực dụng nhất) [Design synthesis]:
  Pha 1 — Sequential Halving trên 12-20 candidate trong 1-2 tuần,
          cắt xuống 4-6 arm sống sót.
  Pha 2 — Contextual bandit (LinTS) trên các arm sống sót, chạy vĩnh viễn.
  Lý do: contextual bandit với 20 arm trên segment nhỏ sẽ không bao giờ hội tụ;
         cắt tỉa trước bằng BAI làm bài toán khả thi.
```

---

## G. Continuous-action bandits cho pricing

Giá, tỉ lệ giảm giá, ad-load rate, ngưỡng trigger — đều là **biến liên tục**. Rời rạc hóa thành 5 mức là giải pháp mặc định tốt, nhưng có những trường hợp cần làm đúng.

### G.1. Continuum-armed / Lipschitz bandits

```text
Giả định: hàm reward mu: X → R là Lipschitz trên metric space:
  | mu(a) - mu(a') |  <=  L * dist(a, a')

Uniform discretization với lưới eps trên [0,1]^p:
  Regret ~ Õ( T^{(p+1)/(p+2)} )
  p=1 (chỉ giá): T^{2/3} — chậm hơn sqrt(T) đáng kể

Zooming algorithm: rời rạc hóa THÍCH NGHI — chia mịn ở vùng gần optimum,
  giữ thô ở vùng rõ ràng kém.
  Regret ~ Õ( T^{(z+1)/(z+2)} ) với z = zooming dimension <= p,
  và z thường NHỎ HƠN NHIỀU so với p khi hàm reward "đẹp" quanh optimum.
```

**Ý nghĩa thực tế** [Design synthesis]: `T^{2/3}` nghĩa là để đạt cùng độ chính xác, pricing bandit liên tục cần nhiều dữ liệu hơn hẳn bandit rời rạc. Với hầu hết game, **5–7 price point rời rạc là lựa chọn đúng**; continuum chỉ đáng khi bạn tối ưu đồng thời nhiều chiều liên tục (giá × thời điểm × độ sâu discount) trên traffic rất lớn.

### G.2. GP-UCB / Bayesian optimization [Primary research]

Nguồn: Srinivas, Krause, Kakade, Seeger, *Gaussian Process Optimization in the Bandit Setting: No Regret and Experimental Design*, ICML 2010, arXiv:0912.3995.

```text
Prior: f ~ GP(0, k(a, a'))   — kernel mã hóa "giá gần nhau cho reward gần nhau"

Posterior sau t quan sát tại A_t = {a_1..a_t}, y_t:
  mu_t(a)     = k_t(a)^T (K_t + sigma^2 I)^{-1} y_t
  sigma_t^2(a)= k(a,a) - k_t(a)^T (K_t + sigma^2 I)^{-1} k_t(a)
  với k_t(a) = [k(a_1,a), ..., k(a_t,a)]^T, (K_t)_{ij} = k(a_i, a_j)

GP-UCB:
  a_t = argmax_a [ mu_{t-1}(a) + sqrt(beta_t) * sigma_{t-1}(a) ]
  beta_t = 2 ln( |A| t^2 pi^2 / (6 delta) )    (miền hữu hạn)

Regret: Õ( sqrt( T * beta_T * gamma_T ) )
  gamma_T = maximum information gain sau T round, phụ thuộc kernel:
    Linear kernel : gamma_T = O(d ln T)
    RBF kernel    : gamma_T = O( (ln T)^{d+1} )
    Matern(nu)    : gamma_T = O( T^{d(d+1)/(2nu+d(d+1))} ln T )
```

Ba lưu ý triển khai:

1. **Chi phí:** O(t³) để nghịch đảo, O(t²) mỗi truy vấn. Với pricing, số điểm dữ liệu *riêng biệt* (giá × segment × tuần) thường chỉ vài trăm → hoàn toàn khả thi. Đừng nhầm với số impression.
2. **Chọn kernel:** Matérn(ν = 5/2) là mặc định an toàn cho demand curve; RBF giả định hàm trơn vô hạn, quá lạc quan và dẫn tới under-exploration.
3. **`beta_t` lý thuyết quá lớn.** Thực hành phổ biến là dùng `beta_t` cố định nhỏ hơn nhiều, hoặc chuyển sang **Thompson Sampling trên GP** (lấy mẫu một hàm từ posterior) để tránh phải tune `beta`.

### G.3. Ràng buộc đơn điệu trên demand curve

Kiến thức tiên nghiệm mạnh nhất trong pricing: **cầu giảm theo giá.** Không áp ràng buộc này, GP/bandit sẽ tạo ra demand curve không đơn điệu vì nhiễu, dẫn tới đề xuất giá vô lý.

```text
Ba cách áp monotonicity:

(1) Isotonic regression trên conversion rate:
    min Σ w_i (y_i - q_i)^2   s.t.  q_1 >= q_2 >= ... >= q_m  (giá tăng dần)
    Giải bằng PAVA (Pool Adjacent Violators), O(m). Rẻ, mạnh, dễ debug.

(2) Parametric demand model:
    q(p) = exp(-(p/p_0)^gamma)  hoặc logit:  logit(q) = a - b*ln(p), b > 0
    Ràng buộc b > 0 bằng reparam b = exp(beta). Bandit chạy trên (a, beta).
    → Chỉ 2 tham số → hội tụ nhanh hơn GP rất nhiều trên traffic thấp.

(3) Monotone GP: kernel có đạo hàm + virtual derivative observation,
    hoặc chiếu posterior sample lên nón đơn điệu. Đúng nhất, phức tạp nhất.

Doanh thu kỳ vọng:  R(p) = p * q(p)  — TỐI ĐA HÓA CÁI NÀY, không phải q(p).
R(p) đơn điệu KHÔNG đúng; nó lõm-đơn-đỉnh (unimodal) trong hầu hết trường hợp,
cho phép dùng thêm unimodal bandit / golden-section search khi traffic thấp.
```

Với traffic thấp, **(2) gần như luôn thắng** [Design synthesis]: 2 tham số so với vài trăm điểm lưới. Chỉ chuyển sang GP khi có bằng chứng residual có cấu trúc mà mô hình tham số không bắt được.

---

## H. OPE nâng cao — vượt qua IPS/SNIPS/DR

### H.1. Ôn nhanh nền tảng

```text
w_i = pi_e(a_i | x_i) / pi_0(a_i | x_i)

IPS   : V_IPS   = (1/n) Σ w_i r_i                 — unbiased, variance có thể bùng nổ
SNIPS : V_SNIPS = (Σ w_i r_i) / (Σ w_i)           — biased nhẹ, variance thấp hơn nhiều
DM    : V_DM    = (1/n) Σ Σ_a pi_e(a|x_i) q_hat(x_i,a)  — variance thấp, bias theo model
DR    : V_DR    = (1/n) Σ [ q_hat(x_i, pi_e) + w_i (r_i - q_hat(x_i, a_i)) ]
        unbiased nếu MỘT trong hai (pi_0 hoặc q_hat) đúng
```

### H.2. Switch-DR

Nguồn: Wang, Agarwal, Dudík, *Optimal and Adaptive Off-policy Evaluation in Contextual Bandits* (ICML 2017).

```text
Ý tưởng: khi importance weight quá lớn, phần IPS của DR nổ variance.
Chuyển sang direct method ở vùng đó.

V_SwitchDR = (1/n) Σ [ q_hat(x_i, pi_e)
                       + w_i * (r_i - q_hat(x_i,a_i)) * 1{ w_i <= tau } ]

tau: ngưỡng chuyển. tau → inf cho DR; tau → 0 cho DM.
Chọn tau bằng minimize MSE bound ước lượng được từ chính dữ liệu.
```

### H.3. MRDR — More Robust Doubly Robust [Primary research]

Nguồn: Farajtabar, Chow, Ghavamzadeh, *More Robust Doubly Robust Off-policy Evaluation*, ICML 2018, arXiv:1802.03493.

```text
Ý tưởng cốt lõi: đừng fit q_hat bằng MSE thông thường.
Fit q_hat bằng cách TRỰC TIẾP TỐI THIỂU HÓA VARIANCE của chính DR estimator.

  q_hat_MRDR = argmin_q  Var_hat[ V_DR(q) ]

Loss thu được (contextual bandit) có dạng bình phương có trọng số:
  L(q) = Σ_i  w_i * (w_i - 1) * ( r_i - q(x_i, a_i) )^2

Chú ý trọng số w_i(w_i - 1): mẫu có importance weight LỚN được ưu tiên fit tốt
— đúng chỗ mà DR dễ nổ variance nhất. Đây là khác biệt quyết định so với
fit q_hat bằng MSE (coi mọi mẫu như nhau, kể cả mẫu pi_e không bao giờ chọn).

MRDR được chứng minh strongly consistent và asymptotically optimal.
```

Đây là một trong những cải tiến "rẻ nhất trên mỗi đơn vị lợi ích" trong toàn bộ OPE stack: bạn không đổi estimator, chỉ đổi **loss function khi huấn luyện reward model**.

### H.4. DR with shrinkage (DRos / DRps) [Primary research]

Nguồn: Su, Dimakopoulou, Krishnamurthy, Dudík, *Doubly Robust Off-policy Evaluation with Shrinkage*, ICML 2020, arXiv:1907.09623.

```text
Thay w_i bằng weight đã co lại w_hat_i, chọn để tối thiểu hóa cận trên của MSE.

DR với optimistic shrinkage (DRos), tham số lambda >= 0:
  w_hat_i = ( lambda / ( w_i^2 + lambda ) ) * w_i

  lambda → inf  ⇒ w_hat → w      (về đúng DR)
  lambda → 0    ⇒ w_hat → 0      (về đúng DM)

DR với pessimistic shrinkage (DRps): w_hat_i = min(w_i, lambda)  (= clipping)

V_DRos = (1/n) Σ [ q_hat(x_i, pi_e) + w_hat_i * ( r_i - q_hat(x_i, a_i) ) ]
```

Đóng góp then chốt của paper: một **thủ tục chọn model để tune lambda**, được chứng minh **không bao giờ tệ hơn DR**. Framework này thống nhất weight-clipping, weight-shrinkage, và mở rộng sang combinatorial action set. Khung nhìn quan trọng: shrinkage **thích nghi theo chất lượng của reward predictor** — reward model kém thì nó thoái hóa về clipping, reward model tốt thì nó cho một dạng shrinkage mới.

### H.5. Slate / ranking estimators

Khi action là một **slate** (danh sách offer, thứ tự item trong shop), không gian action là tổ hợp và IPS thông thường sụp đổ (importance weight là tích của L thừa số).

```text
PSEUDOINVERSE (PI) estimator — Swaminathan et al., NeurIPS 2017
  Giả định: phần thưởng trung bình có tính CỘNG theo (slot, action):
    E[r | s] = Σ_{l=1..L} phi(l, a_l)

  Ước lượng phi bằng nghiệm pseudoinverse của ma trận marginal:
    V_PI = (1/n) Σ_i  r_i * ( q_i^T Gamma^+ q_pi_e )
    với q = indicator vector của các cặp (slot, action) trong slate,
    Gamma = E_{pi_0}[ q q^T ] (ma trận marginal của logging policy),
    Gamma^+ = Moore-Penrose pseudoinverse.

  Unbiased khi giả định cộng tính đúng; giảm variance rất mạnh so với slate-IPS.

PBM-IPS (Position-Based Model) cho ranking có click:
  Giả định: P(click | item i tại vị trí k) = relevance(i) * examination(k)
  IPS chia cho examination propensity theo VỊ TRÍ, không theo item:
    V_PBM-IPS = (1/n) Σ_i  c_i / theta_{k_i}
  theta_k = xác suất người dùng NHÌN vị trí k (ước lượng qua position swap
  experiment hoặc intervention harvesting).
  → Sửa position bias, không cần biết propensity đầy đủ trên slate.

Control variates cho slate OPE (Vlassis et al., NeurIPS 2021, arXiv:2106.07914)
  mở rộng lớp estimator không chệch bao gồm PI và biến thể self-normalized.
```

### H.6. Confidence interval và chẩn đoán

```text
CI cho IPS/DR:
  (a) Empirical Bernstein — dùng variance thực nghiệm, chặt hơn Hoeffding
      khi variance thấp; cần weight bị chặn (bắt buộc clip trước).
  (b) Bootstrap percentile (BCa) — thực dụng nhất, chỉ approximate
      nhưng thường sát nhất trong thực tế. B = 1000-2000 resample.
  (c) Student-t trên phân phối của per-sample estimate — nhanh, chấp nhận được
      khi n lớn và weight đã clip.

CHẨN ĐOÁN BẮT BUỘC TRƯỚC KHI TIN BẤT KỲ CON SỐ OPE NÀO:
  1. ESS = (Σ w_i)^2 / Σ w_i^2 ;  ESS/n < 0.10 → KHÔNG kết luận.
  2. max(w_i) và tỉ lệ khối lượng nằm ở top 1% weight.
     Nếu 1% mẫu chiếm > 50% tổng weight → estimate do vài mẫu quyết định.
  3. Overlap check: với mỗi (segment, arm), pi_0 > 0 ở mọi nơi pi_e > 0?
     Vi phạm positivity → estimator KHÔNG identifiable, không phải "nhiều nhiễu".
  4. Sanity: chạy estimator với pi_e = pi_0. Kết quả phải khớp mean reward thực.
     Đây là unit test rẻ nhất và bắt được hầu hết bug propensity logging.

CHỌN ESTIMATOR: đừng chọn cố định. Chạy vài estimator, và dùng thủ tục
model selection (như trong paper shrinkage) hoặc so sánh trên tập
"pi_e đã biết kết quả thật" (log của một A/B test cũ) để chọn.
```

**Cảnh báo bao trùm:** OPE **không** thay thế được A/B test. Nó là bộ lọc rẻ để loại bỏ 90% ứng viên tệ trước khi tốn traffic. Quyết định cuối vẫn cần randomized holdout.

---

## I. Offline RL — khi bandit không đủ

Dùng RL thay bandit chỉ khi **action hôm nay làm thay đổi state ngày mai theo cách quan trọng** — ví dụ: cho quá nhiều free currency hôm nay làm hỏng động lực mua tuần sau; hiển thị paywall quá sớm làm hỏng onboarding.

### I.1. Vấn đề nền tảng: extrapolation error

```text
Q-learning off-policy chuẩn:
  Q(s,a) ← r + gamma * max_{a'} Q(s', a')
                       ^^^^^^^^ ĐÂY LÀ VẤN ĐỀ

max lấy trên MỌI a', kể cả a' KHÔNG HỀ CÓ trong dataset.
Q(s', a') ở đó là giá trị ngoại suy của mạng, thường OVERESTIMATE
(vì max chọn đúng cái bị đánh giá cao nhất, tức nhiễu dương lớn nhất).
Overestimate lan truyền qua bootstrapping → Q-value phân kỳ.

Online RL tự sửa được (thử a', thấy tệ, cập nhật). Offline RL KHÔNG THỂ.
→ Mọi thuật toán offline RL về bản chất là một cách trả lời câu hỏi:
   "làm sao tránh đánh giá action nằm ngoài phân phối dữ liệu?"
```

### I.2. Bốn họ giải pháp

```text
(1) POLICY CONSTRAINT — BCQ, BEAR
    BCQ (Fujimoto et al., 2018): huấn luyện generative model của behavior
    policy; chỉ xét action mà behavior policy có thể sinh ra (cộng nhiễu nhỏ,
    perturbation network bị chặn). "Chỉ chọn giữa những gì đã từng làm."
    BEAR: nới lỏng bằng ràng buộc support qua MMD thay vì matching phân phối.
    → Đơn giản, an toàn, nhưng bị giới hạn bởi chất lượng behavior policy.

(2) VALUE PESSIMISM — CQL  [Primary research, arXiv:2006.04779]
    Thêm regularizer đẩy Q-value XUỐNG ở action không có trong data:

    L_CQL = alpha * E_s [ log Σ_a exp(Q(s,a))  -  E_{a~D}[ Q(s,a) ] ]
            + L_Bellman

    Số hạng đầu: log-sum-exp trên mọi action (đẩy xuống toàn cục)
    trừ đi Q của action THẬT trong dataset (giữ nguyên chỗ có dữ liệu).
    → Chứng minh Q học được là CẬN DƯỚI của giá trị thật của policy.
    alpha điều khiển độ bi quan; alpha quá lớn → policy quá bảo thủ.

(3) IN-SAMPLE LEARNING — IQL  [Primary research, arXiv:2110.06169]
    Tránh HOÀN TOÀN việc đánh giá action ngoài dataset bằng expectile regression:

    L_V   = E_{(s,a)~D} [ L2^tau ( Q_target(s,a) - V(s) ) ]
            với L2^tau(u) = | tau - 1{u < 0} | * u^2      (expectile loss)
    L_Q   = E_{(s,a,s')~D} [ ( r + gamma*V(s') - Q(s,a) )^2 ]
    Policy: advantage-weighted regression
    L_pi  = E_D [ exp( beta * (Q(s,a) - V(s)) ) * (-log pi(a|s)) ]

    tau → 1 xấp xỉ max nhưng CHỈ TRÊN action có trong data.
    → Không bao giờ query Q ở action ngoài phân phối. Ổn định, dễ tune,
      thường là lựa chọn mặc định tốt nhất trong ba họ trên.

(4) SEQUENCE MODELING — Decision Transformer  [arXiv:2106.01345]
    Bỏ hẳn Bellman backup. Mô hình hóa trajectory như chuỗi token:
      (R_1, s_1, a_1, R_2, s_2, a_2, ...)  với R_t = return-to-go
    Huấn luyện autoregressive để dự đoán a_t.
    Inference: đặt return-to-go MONG MUỐN ở đầu, để model sinh action.
    → Không có extrapolation error kiểu Q-learning; nhưng về bản chất là
      conditional imitation, KHÔNG có cơ chế "stitching" mạnh để ghép
      các đoạn trajectory tốt thành một trajectory tốt hơn.
      Yếu khi dữ liệu toàn trajectory kém và cần cải thiện thật sự.
```

### I.3. Chế độ hỏng đã biết trong recommendation/monetization

```text
1. DISTRIBUTION SHIFT TÍCH LŨY
   Policy hơi lệch ở bước 1 → state ở bước 2 lệch nhiều hơn → sai số cộng dồn
   theo horizon. Đây là lý do horizon dài (30-180 ngày) cực kỳ khó.
   Giảm nhẹ: rút ngắn horizon hiệu dụng (gamma nhỏ hơn), hoặc dùng
   reward shaping bằng LTV model đã calibrate thay vì reward thưa dài hạn.

2. REWARD SPARSITY
   Purchase là sự kiện hiếm (1-5% DAU). Signal quá thưa cho credit assignment
   qua hàng trăm bước. → Bắt buộc reward shaping / auxiliary reward.

3. BEHAVIOR POLICY QUÁ HẸP
   Nếu log đến từ policy gần deterministic, mọi thuật toán offline RL đều
   chỉ học được cách BẮT CHƯỚC nó. Không có cải thiện thật.
   → Không có exploration trong log = không có offline RL. Không có ngoại lệ.

4. CONFOUNDING BỞI BIẾN KHÔNG QUAN SÁT
   Nếu quyết định lịch sử phụ thuộc biến không được log (rule thủ công của
   PM, campaign đang chạy), state không đủ và mọi ước lượng đều chệch.
   → Log ĐẦY ĐỦ mọi input đã dẫn tới quyết định, kể cả rule cứng.

5. ĐÁNH GIÁ OFFLINE KHÔNG TIN CẬY
   OPE cho RL nhiều bước có variance TỆ HƠN NHIỀU so với bandit
   (importance weight là TÍCH theo horizon). Nhiều báo cáo "offline RL thắng"
   không sống sót qua A/B test.
   → Với horizon > ~10 bước, coi mọi OPE là tín hiệu định tính.
```

### I.4. Khuyến nghị cho SDK

[Design synthesis] Thứ tự ưu tiên nghiêm ngặt:

```text
1. Contextual bandit + reward = LTV-proxy có calibrate (giải quyết ~80% giá trị)
2. Bandit + constraint (BwK/Lagrangian) cho ad-load & fatigue (~10% nữa)
3. POMDP với action space nhỏ và horizon ngắn, planning offline
   (mô hình Tencent/HKU ở doc 24) — có bằng chứng production
4. Offline RL đầy đủ (IQL) — CHỈ khi có: log đã randomize nhiều tháng,
   > 10M trajectory, và một hạ tầng A/B test đủ mạnh để bác bỏ nó.
```

Lý do đặt POMDP trước offline RL đầy đủ: POMDP với state space nhỏ có thể **plan offline bằng Monte Carlo và nén thành lookup table chạy on-device**, tránh toàn bộ lớp vấn đề extrapolation error của deep offline RL.

---

## J. Bảng quyết định thực dụng

Tất cả ngưỡng dưới đây là [Design synthesis] — điểm khởi đầu để tinh chỉnh, không phải kết quả đo được.

### J.1. Theo traffic của **segment nhỏ nhất bạn muốn cá nhân hóa**

Đây là con số quan trọng, không phải DAU tổng. Nếu bạn có 500k DAU nhưng muốn policy riêng cho 40 segment, segment nhỏ nhất có thể chỉ vài trăm/ngày.

```text
Decision/ngày   Số arm   Khuyến nghị
mỗi segment
--------------  -------  ---------------------------------------------------------
< 200           <= 3     KHÔNG cá nhân hóa. Rule cứng + A/B test toàn cục.
                         Hoặc gộp segment lên tầng cha.
200 - 1k        <= 5     Beta-TS không context (per segment), forced p_min = 0.10
                         Cửa sổ đánh giá >= 2 tuần.
1k - 10k        <= 8     LinTS với d <= 15 feature, hoặc TS phân cấp
                         (hierarchical/partial pooling qua segment cha).
10k - 100k      <= 20    LinTS/GLM-TS với d <= 50, discounted (half-life 14 ngày).
                         Bắt đầu có thể tin OPE.
100k - 1M       <= 50    Neural-Linear (embedding offline hàng ngày + LinTS online).
                         Feature phong phú, segment mịn hơn.
> 1M            > 50     Neural-Linear hoặc GBM ensemble + p_min;
                         cân nhắc BwK cho ràng buộc; POMDP cho sequential.
```

**Quy tắc ngón tay cái về sample size** để phân biệt hai arm chênh nhau `delta` tương đối trên conversion rate `p`:

```text
n_per_arm  ≈  16 * p * (1-p) / (delta * p)^2     (power 80%, alpha 0.05, xấp xỉ)

p = 2%, delta = 10% (2.0% vs 2.2%)  →  n ≈ 78,000 mỗi arm
p = 2%, delta = 20%                 →  n ≈ 19,600 mỗi arm
p = 5%, delta = 10%                 →  n ≈ 30,400 mỗi arm

Đây là lý do "10 segment × 8 arm" thường là hoang tưởng: bạn cần
78k × 8 × 10 ≈ 6.2 triệu decision chỉ để phân biệt hiệu ứng 10%.
Bandit tiết kiệm hơn test tĩnh (dồn mẫu vào arm cạnh tranh), nhưng
không thay đổi bậc độ lớn.
```

### J.2. Theo độ trễ feedback

```text
Delay điển hình     Khuyến nghị
------------------  ----------------------------------------------------------
< 1 phút            Bất kỳ thuật toán nào. Cập nhật gần-thời-gian-thực khả thi.
(click, ad view)

1 phút - 2 giờ      TS-family (chịu batch tốt). Batch update mỗi 15-60 phút.
(session outcome)   Tránh UCB thuần (n_a cập nhật trước reward).

2 - 72 giờ          Windowed reward (Pattern 2, mục C.2). Batch mỗi 6-24h.
(purchase)          Nếu cần nhanh hơn: proxy reward + calibration hàng tuần,
                    kèm guardrail trên metric thật.

> 7 ngày            KHÔNG dùng làm reward trực tiếp của bandit.
(LTV, retention)    Dùng làm: (a) guardrail metric, (b) mục tiêu để calibrate
                    proxy reward, (c) reward cho mô hình offline chạy chậm
                    điều chỉnh prior.

Delay khác nhau     BẮT BUỘC hiệu chỉnh theo mô hình delay (mục C.1) —
GIỮA CÁC ARM        nếu không, arm có delay ngắn sẽ luôn thắng một cách giả tạo.
```

### J.3. Theo mức rủi ro

```text
Mức rủi ro    Ví dụ                              Cơ chế bắt buộc
------------  ---------------------------------  ---------------------------------
Thấp          Màu nút, thứ tự item trong shop     TS/LinTS, p_min = 0.05,
                                                  guardrail cơ bản
Trung bình    Thời điểm hiện offer, ad frequency  + BwK/Lagrangian cho ngân sách,
                                                  + conservative budget alpha=0.05,
                                                  + holdout 5% giữ nguyên vĩnh viễn
Cao           Giá, discount depth, paywall gate   + HCOPE gate trước mỗi release,
                                                  + price bounds cứng,
                                                  + ramp 1→5→20→50%,
                                                  + kill-switch tự động theo
                                                    guardrail metric
Rất cao       Bất cứ thứ gì chạm compliance,      KHÔNG học. Rule cứng, review
              loot box, đối tượng dễ tổn thương   thủ công, không có exploration.
```

**Holdout vĩnh viễn** đáng nhấn mạnh: giữ 3–5% người dùng ở policy cố định (hoặc random-uniform trong ràng buộc) **mãi mãi**. Đây là nguồn sự thật duy nhất để đo giá trị tuyệt đối mà toàn bộ decision engine tạo ra, và là dữ liệu sạch nhất để warm-start mọi policy tương lai. Chi phí 3–5% traffic là rẻ so với việc không bao giờ trả lời được câu hỏi "hệ thống này có đáng tiền không?".

### J.4. Ma trận tổng hợp nhanh

```text
Bài toán                          Thuật toán đầu tiên nên thử
--------------------------------  --------------------------------------------
Chọn 1 trong 4 paywall layout     Sequential Halving → ship winner
Offer nào cho player này          LinTS trên feature player, d ~ 10-20
Giá tối ưu cho starter pack       Parametric demand (logit) + TS trên 2 tham số
Ad-load bao nhiêu là vừa          BwK/Lagrangian, tài nguyên = annoyance budget
Khi nào nên im lặng               Bandit có NO_ACTION là arm hợp lệ + fatigue cost
Thứ tự 6 item trong shop          Slate: PI estimator để OPE, policy = top-k
                                  theo điểm cá nhân hóa
Chuỗi can thiệp 7 ngày            POMDP horizon ngắn (doc 24), KHÔNG deep RL
Catalog offer đổi mỗi tuần        Hybrid LinUCB / LinTS với feature offer
                                  (không one-hot ID)
```

---

## K. Ghi chú triển khai cho SDK

[Design synthesis] Những chi tiết thường bị bỏ sót và gây hỏng hệ thống:

```text
1. LOG SCHEMA tối thiểu cho mỗi decision:
   decision_id, user_id_hash, timestamp, decision_point,
   policy_version, model_ref, segment_id,
   feature_vector (hoặc feature_hash + snapshot reference),
   candidate_actions[] (SAU khi áp hard constraint),
   excluded_actions[] + lý do loại,
   chosen_action, propensity p(chosen | x),
   full_propensity_vector (nếu K nhỏ — cực kỳ giá trị cho OPE),
   exploration_flag (arm này do explore hay exploit).

2. DETERMINISM: sample TS bằng RNG seed = hash(user_id, decision_id, policy_version).
   Cùng input → cùng output khi retry. Không seed → duplicate request cho hai
   quyết định khác nhau, và propensity log sai.

3. FEATURE SKEW: feature tính ở device phải KHỚP feature dùng khi train.
   Ghi feature_spec_hash vào artifact; device từ chối artifact có hash không khớp
   version SDK. Đây là nguồn bug âm thầm phổ biến nhất.

4. NUMERICAL: giữ A^{-1} trực tiếp (Sherman-Morrison) thay vì nghịch đảo lại.
   Định kỳ (mỗi lần build artifact) kiểm tra tính đối xứng và positive-definite;
   nếu drift, rebuild từ (X^T X + lambda I) trên batch.

5. COLD START mỗi arm mới: khởi tạo từ prior của "arm hàng xóm" theo feature
   (giá gần nhất, loại gần nhất), không phải prior đồng nhất. Giảm mạnh
   chi phí học của mỗi offer mới.

6. KILL SWITCH: remote config cho phép ép policy về fallback deterministic
   trong < 5 phút, không cần app update. Không có nó thì không nên bật bandit.
```

---

## Không xác minh được (ghi rõ)

1. **arXiv ID của BCQ (Fujimoto, Meger, Precup, *Off-Policy Deep Reinforcement Learning without Exploration*, ICML 2019)** — công trình có thật và được mô tả đúng ở mục I.2, nhưng ID cụ thể không được xác minh trong phiên nghiên cứu này nên không đưa vào phần nguồn.
2. **URL/ID của Switch-DR** (Wang, Agarwal, Dudík, *Optimal and Adaptive Off-policy Evaluation in Contextual Bandits*, ICML 2017) — chỉ xác nhận được gián tiếp qua trích dẫn trong kết quả tìm kiếm về MRDR và shrinkage, không mở được trang gốc.
3. **arXiv ID của CUSUM-UCB và M-UCB** (change-point detection bandits) — hai họ thuật toán được mô tả đúng về cơ chế, nhưng ID/URL cụ thể chưa xác minh; công thức CUSUM và M-UCB trình bày ở B.5 là dạng chuẩn của hai kỹ thuật này.
4. **arXiv ID của BEAR và của Zooming algorithm** (Kleinberg, Slivkins, Upfal) — chưa xác minh, chỉ mô tả cơ chế.
5. **Mọi con số ngưỡng trong mục J** (traffic/arm/cadence/alpha/kappa/p_min) là **[Design synthesis]** — suy luận từ bậc độ lớn của sample size và kinh nghiệm kiến trúc, **không phải kết quả đo từ production nào**. Cần hiệu chỉnh bằng dữ liệu thật của từng tựa game.
6. **Công thức sample size ở J.1** là xấp xỉ normal cho two-proportion test, bỏ qua hiệu chỉnh liên tục và multiple comparison; dùng như bậc độ lớn, không dùng để lập kế hoạch chính thức.
7. **Hiệu quả tương đối giữa các estimator OPE** (MRDR vs DRos vs Switch-DR) phụ thuộc mạnh vào bộ dữ liệu; các paper gốc đều báo cáo thắng trên benchmark của chính họ. Không có kết luận phổ quát và không nên giả định thứ hạng nào cố định.
8. **PBM-IPS** được mô tả theo dạng chuẩn của position-based model trong unbiased learning-to-rank; không xác minh được một paper gốc cụ thể trong phiên này.

## Nguồn chính

- A Contextual-Bandit Approach to Personalized News Article Recommendation (LinUCB) — https://arxiv.org/abs/1003.0146
- Unbiased Offline Evaluation of Contextual-bandit-based News Article Recommendation Algorithms (replay estimator) — https://arxiv.org/abs/1003.5956
- Thompson Sampling for Contextual Bandits with Linear Payoffs (LinTS) — https://arxiv.org/abs/1209.3352
- Neural Contextual Bandits with UCB-based Exploration (NeuralUCB) — https://arxiv.org/abs/1911.04462
- Gaussian Process Optimization in the Bandit Setting: No Regret and Experimental Design (GP-UCB) — https://arxiv.org/abs/0912.3995
- On Upper-Confidence Bound Policies for Non-Stationary Bandit Problems (D-UCB, SW-UCB) — https://arxiv.org/abs/0805.3415
- Stochastic Bandit Models for Delayed Conversions — https://arxiv.org/abs/1706.09186
- Bandits with Knapsacks — https://arxiv.org/abs/1305.2545
- High-Confidence Off-Policy Evaluation (AAAI 2015) — https://ojs.aaai.org/index.php/AAAI/article/view/9541
- More Robust Doubly Robust Off-policy Evaluation (MRDR) — https://arxiv.org/abs/1802.03493
- Doubly Robust Off-policy Evaluation with Shrinkage (DRos/DRps) — https://arxiv.org/abs/1907.09623
- Doubly robust off-policy evaluation with shrinkage (PMLR, ICML 2020) — https://proceedings.mlr.press/v119/su20a.html
- Off-policy Evaluation for Slate Recommendation (pseudoinverse estimator) — http://papers.neurips.cc/paper/6954-off-policy-evaluation-for-slate-recommendation.pdf
- Control Variates for Slate Off-Policy Evaluation — https://arxiv.org/abs/2106.07914
- Conservative Q-Learning for Offline Reinforcement Learning (CQL) — https://arxiv.org/abs/2006.04779
- Offline Reinforcement Learning with Implicit Q-Learning (IQL) — https://arxiv.org/abs/2110.06169
- Decision Transformer: Reinforcement Learning via Sequence Modeling — https://arxiv.org/abs/2106.01345
- Open Bandit Dataset and Pipeline (OPE benchmark & thư viện estimator) — https://arxiv.org/abs/2008.07146
