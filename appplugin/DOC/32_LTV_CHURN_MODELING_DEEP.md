# 32 — LTV & Churn Modeling (Deep Reference)

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Tài liệu tham chiếu sâu về mô hình hóa *player lifetime value* (LTV/CLV) và *churn* cho game/app mobile, coi đây là **reward-signal backbone** của monetization decision engine. Bao gồm: classical probabilistic CLV (BTYD), deep LTV (ZILN, two-stage, ordinal/quantile, transformer), survival analysis cho churn (đặc biệt là xử lý censoring), sequence models trên event stream, evaluation đúng cách, uncertainty quantification, delayed/censored labels, cold start, phân biệt causal vs predictive LTV, và một staged recipe triển khai thực tế. Không bao gồm: UA bidding mechanics, ad mediation waterfall, kiến trúc data pipeline (xem tài liệu khác trong bộ).

---

## 0. Bản đồ quyết định (đọc trước)

Trước khi chọn mô hình, trả lời 4 câu hỏi. Chọn sai ở đây thì mọi thứ phía sau đều vô nghĩa.

```text
Q1. Reward signal dùng để LÀM GÌ?
    (a) Xếp hạng người chơi (ai đáng nhận offer)      -> cần ranking tốt (Gini/Spearman)
    (b) Định giá tiền tệ (bid cap, budget, ROAS)      -> cần CALIBRATION tuyệt đối
    (c) Reward cho bandit/RL                          -> cần calibration + uncertainty
    (d) Đo tác động của một hành động                 -> cần CAUSAL, không phải predictive

Q2. Horizon là bao lâu? D7 / D30 / D90 / D180 / D360?
    Horizon càng dài -> label càng censored -> càng phải dùng multi-horizon + survival

Q3. Doanh thu gồm gì? IAP-only hay IAP + Ad revenue?
    Ad revenue là DÒNG LIÊN TỤC, không phải transaction -> BTYD không áp dụng được

Q4. Có bao nhiêu install đã "chín" (matured) tới horizon đó?
    < 10k   -> KHÔNG dùng ML. Dùng cohort curve extrapolation.
    10k-100k-> GBM two-stage
    100k-1M -> ZILN + discrete-time hazard
    > 1M    -> sequence encoder + multi-horizon + uncertainty
```

**[Design synthesis]** Sai lầm phổ biến nhất trong ngành không phải chọn sai kiến trúc mạng, mà là dùng một mô hình được tối ưu cho (a) để phục vụ mục đích (b) hoặc (d).

---

## 1. Định nghĩa label trước khi bàn mô hình

Phần lớn lỗi LTV/churn là lỗi **định nghĩa nhãn**, không phải lỗi mô hình.

### 1.1 Định nghĩa LTV chuẩn

```text
LTV_d(i) = Σ  net_iap_revenue(i, t)            [đã trừ store fee, đã trừ refund/chargeback]
         + Σ  user_level_ad_revenue(i, t)      [từ impression-level revenue của mediation]
   với t ∈ (install_time(i), install_time(i) + d days]

Ràng buộc bắt buộc:
   install_time(i) + d  ≤  data_cutoff        (nếu không, nhãn bị censored)
```

Ba cái bẫy:

1. **Gross vs Net.** Apple/Google giữ 30% (15% cho small-business program / năm thứ 2 subscription). Mô hình train trên gross rồi feed vào ROAS bidding sẽ over-bid ~18-43%. **[Design synthesis]**
2. **Refund/chargeback.** Refund đến trễ (thường 1-30 ngày). Nếu snapshot label ngay tại D7, phần refund sau đó không được trừ → label lạc quan có hệ thống ở đuôi trên (whale refund là phổ biến).
3. **Ad revenue user-level.** Nếu chỉ có eCPM trung bình rồi phân bổ theo impression count, bạn đang tạo ra một label gần như là "impression count nhân hằng số" — mô hình sẽ học dự đoán impression, không phải doanh thu. Cần impression-level revenue data từ mediation. **[Design synthesis]**

### 1.2 Định nghĩa churn

Game là **non-contractual**: không có sự kiện "hủy đăng ký". Churn phải được *định nghĩa*, và định nghĩa đó là một quyết định mô hình hóa.

```text
churn_k(i, t) = 1  nếu  không có session nào của i trong (t, t + k days]
                0  nếu ngược lại
                — (censored)  nếu  t + k > data_cutoff

Cách chọn k một cách có nguyên tắc (không bốc thuốc):
  Với mỗi độ dài gap g, tính  P(return | đã inactive g ngày liên tiếp)
  Chọn k = min{ g : P(return | inactive g) < θ },  θ thường 0.03-0.05
  → k phụ thuộc GENRE: hypercasual k ≈ 3-7; midcore/RPG k ≈ 14-28; 4X k ≈ 30+
```

**[Design synthesis]** k phải được chọn trên tập dữ liệu *riêng* (một cohort cũ), không phải trên tập đang train — chọn k để tối ưu metric là data snooping.

---

## 2. Classical probabilistic CLV (BTYD) — và tại sao chúng gãy trong game

Họ mô hình "Buy Till You Die" mô tả hành vi mua trong bối cảnh non-contractual bằng cách kết hợp một quá trình mua và một quá trình "chết" (dropout) tiềm ẩn.

### 2.1 Pareto/NBD (Schmittlein, Morrison & Colombo, 1987)

```text
Giả định:
 A1. Khi còn "sống", số giao dịch của khách hàng ~ Poisson(λ)
 A2. Thời gian sống τ ~ Exponential(μ)          [dropout có thể xảy ra BẤT KỲ LÚC NÀO]
 A3. λ ~ Gamma(r, α)   giữa các khách hàng      [heterogeneity]
 A4. μ ~ Gamma(s, β)   giữa các khách hàng
 A5. λ ⟂ μ

Dữ liệu cần: chỉ 3 con số mỗi khách — (x, t_x, T)
   x   = số giao dịch lặp lại
   t_x = thời điểm giao dịch cuối
   T   = thời gian quan sát

Output: E[Y(t) | x, t_x, T]  = số giao dịch kỳ vọng trong t đơn vị thời gian tới
        P(alive | x, t_x, T)
```

### 2.2 BG/NBD (Fader, Hardie & Lee, 2005 — "Counting Your Customers the Easy Way")

Thay A2 bằng: **sau mỗi giao dịch**, khách hàng dropout với xác suất p; p ~ Beta(a, b). Đổi lại tính khả thi tính toán (không cần Gaussian hypergeometric), nhưng sinh ra một hệ quả lạ: khách hàng có x = 0 (chưa mua lại lần nào) **luôn được coi là còn sống** với xác suất 1 — vì dropout chỉ xảy ra *sau* một giao dịch.

### 2.3 BG/BB (Fader, Hardie & Berger, 2010)

Phiên bản **thời gian rời rạc**: khách hàng có các "transaction opportunities" định kỳ (ví dụ mỗi ngày đăng nhập), mua với xác suất p ~ Beta, dropout với xác suất θ ~ Beta. Về mặt cấu trúc, BG/BB **gần với game nhất** trong bốn mô hình, vì telemetry game vốn là daily discrete.

### 2.4 Gamma-Gamma (Fader & Hardie) — giá trị tiền tệ

```text
Giả định:
 M1. Giá trị mỗi giao dịch z ~ Gamma(p, ν)
 M2. ν ~ Gamma(q, γ)  giữa các khách hàng
 M3. QUAN TRỌNG: giá trị trung bình mỗi giao dịch ĐỘC LẬP với tần suất giao dịch

E[M | m̄, x] = ( (p·x) / (p·x + q − 1) )·m̄  +  ( (q − 1) / (p·x + q − 1) )·(γ·p/(q−1))
              ─────────── weight trên trung bình cá nhân ───────  ── shrink về population ──

CLV ≈ E[số giao dịch tương lai]  ×  E[giá trị mỗi giao dịch]
```

**Kiểm tra bắt buộc trước khi dùng:** corr(frequency, average order value) phải ≈ 0. **[Primary docs]** PyMC-Marketing ghi rõ điều kiện này trong notebook Gamma-Gamma.

### 2.5 Tại sao chúng gãy trong game — chi tiết từng giả định

**[Design synthesis]**, dựa trên cấu trúc giả định của các paper gốc:

| Giả định BTYD | Thực tế game | Hệ quả |
|---|---|---|
| Purchase rate λ **stationary** | LiveOps event (sale, banner mới, battle pass, anniversary) tạo spike 3-10× trong 48-72h | λ không phải hằng số; mô hình gán spike vào "heterogeneity" → over-estimate r/α cho whale, under-estimate cho casual |
| Dropout memoryless (Exp/Geo) | Hazard churn cực kỳ **front-loaded**: D0→D1 mất 60-75%, sau D30 hazard gần phẳng | Exponential không thể fit đồng thời cả hai chế độ; mô hình bị kéo về trung bình, sai ở cả hai đầu |
| Giá trị giao dịch liên tục (Gamma) | IAP là **price ladder rời rạc** ($0.99/4.99/19.99/99.99), khối lượng dồn ở vài điểm | Gamma smooth qua các mode; sai lệch lớn ở mức $99.99 (nơi tập trung doanh thu) |
| Frequency ⟂ monetary | Whale mua **nhiều hơn VÀ đắt hơn**; corr thường +0.3 đến +0.6 | Gamma-Gamma shrink whale về population mean → **under-estimate top decile một cách hệ thống** |
| Không có covariate | Country, device tier, campaign, progression, paywall-hit là các predictor mạnh nhất | Bỏ toàn bộ tín hiệu có giá trị nhất; không thể cold-start |
| Population đồng nhất theo thời gian | UA đổi campaign mix hàng tuần → cohort composition drift | Tham số fit trên cohort cũ không transfer sang cohort mới |
| Doanh thu = chuỗi transaction | Ad revenue là **dòng liên tục** theo impression, không có "giao dịch" | BTYD hoàn toàn không mô hình hóa được ad LTV |

**Kết luận thực tiễn:** BTYD vẫn hữu ích trong game ở **đúng một vai trò** — làm *baseline* và *sanity check* ở cấp cohort khi bạn có ít hơn ~50k người chơi trả tiền, và làm mô hình "tần suất mua của payer đã tồn tại" (conditional on being a payer, ngắn hạn D7-D30). Nó **không** nên là reward signal cho decision engine.

### 2.6 Implementations

**[Primary docs]**
- `lifetimes` (Cam Davidson-Pilon): API kinh điển (`BetaGeoFitter`, `ParetoNBDFitter`, `BetaGeoBetaBinomFitter`, `GammaGammaFitter`). **Không còn được maintain** — PyMC-Marketing ghi nhận điều này khi so sánh kết quả.
- **PyMC-Marketing** (`pymc-marketing.io`): thay thế hiện đại, Bayesian đầy đủ. Có `BetaGeoModel`, `ParetoNBDModel`, `GammaGammaModel`, `ShiftedBetaGeoModel`, `ModifiedBetaGeoModel`. Lợi thế lớn: cho ra **posterior** thay vì point estimate → dùng được trực tiếp cho Thompson sampling, và hỗ trợ hierarchical priors (xem §9 cold start).

---

## 3. Deep LTV

### 3.1 Vấn đề phân phối: zero-inflated + heavy-tailed

Phân phối LTV trong game có hai bệnh cùng lúc:

```text
P(LTV = 0)   ≈ 0.95 – 0.99      (95-99% người chơi không bao giờ trả tiền)
P(LTV | > 0) ≈ heavy-tailed     (top 0.1% payer chiếm 30-60% doanh thu)

⇒ MSE loss trên raw LTV:
   - gradient bị chi phối bởi vài whale outlier
   - mô hình học cách dự đoán ≈ ARPU cho tất cả mọi người (giải pháp tối ưu MSE khi
     không phân biệt được) → ranking vô dụng
⇒ MSE trên log(1+LTV):
   - nén đuôi, mô hình bỏ qua whale — chính là nhóm bạn cần dự đoán đúng nhất
   - khi exp() ngược lại để lấy kỳ vọng, có bias hệ thống (Jensen)
```

### 3.2 ZILN — Zero-Inflated LogNormal (Wang, Liu & Miao, Google, arXiv:1912.07753)

**[Primary research]** Ý tưởng: mô hình hóa LTV trực tiếp như hỗn hợp của một khối lượng điểm tại 0 và một lognormal.

```text
X ~ ZILN(p, μ, σ):
    P(X = 0)   = 1 − p
    X | X > 0  ~ LogNormal(μ, σ²)

Mạng DNN có 3 output head:  logit_p(x),  μ(x),  σ(x)   (σ qua softplus để dương)

LOSS (negative log-likelihood):
    L(x; p, μ, σ) =
        − 1{x = 0} · log(1 − p)
        − 1{x > 0} · [ log p  −  log(x · σ · √(2π))  −  (log x − μ)² / (2σ²) ]

Trong thực tế cài đặt bằng:
    L = CrossEntropy(1{x>0}, logit_p)  +  1{x>0} · LogNormalNLL(x; μ, σ)

DỰ ĐOÁN kỳ vọng (đây là phần hay bị làm sai):
    E[X] = p · exp( μ + σ²/2 )
    ⚠ KHÔNG phải p · exp(μ). exp(μ) là MEDIAN của lognormal, không phải mean.
      Bỏ quên σ²/2 gây under-estimate có hệ thống, càng nặng ở nhóm σ lớn (whale).

Quantile (miễn phí, không cần train thêm):
    Q_α(X) = 0                               nếu α ≤ 1 − p
           = exp( μ + σ · Φ⁻¹((α − 1 + p)/p) )  nếu α > 1 − p
```

**Tại sao zero-inflation quan trọng:** nó tách bạch hai nguồn phương sai. `p(x)` học "có trả tiền không" (bài toán phân loại, tín hiệu dày), `(μ, σ)(x)` học "trả bao nhiêu nếu trả" (bài toán hồi quy, tín hiệu thưa nhưng đã sạch zero). Nếu ép một mạng MSE học cả hai, gradient của 97% mẫu zero sẽ nghiền nát tín hiệu của 3% payer.

**Lợi ích kỹ thuật:** một mô hình duy nhất cho ra *cả* churn probability *và* value distribution — giảm engineering complexity so với two-stage, và tự động cho ra uncertainty (σ) dùng được cho bandit (§7).

**[Primary research]** Paper cũng khuyến nghị đúng bộ metric: **normalized Gini coefficient** cho discrimination và **decile chart** cho calibration — xem §6.

### 3.3 Two-stage: P(purchase) × E[value | purchase]

```text
Stage 1: classifier  q(x) = P(payer | x)          — loss: BCE, có thể class-weight
Stage 2: regressor   v(x) = E[LTV | payer, x]     — TRAIN CHỈ TRÊN PAYER
Prediction:          LTV̂(x) = q(x) · v(x)
```

Ưu: đơn giản, hai model độc lập dễ debug, mỗi stage dùng được thuật toán phù hợp nhất (LightGBM cho stage 1, quantile GBM cho stage 2).

Nhược **[Design synthesis]**:
- **Selection bias ở stage 2**: train trên payer nhưng infer trên toàn bộ dân số → covariate shift. Cần importance weighting hoặc ít nhất kiểm tra phân phối feature giữa payer và non-payer.
- **Sai số nhân lên**: 15% error ở mỗi stage → ~30% ở tích.
- **Không share representation**: hai model không học chung feature encoder, lãng phí dữ liệu.
- **Không có joint uncertainty**: Var[q·v] ≠ Var[q]·Var[v].

Two-stage là điểm khởi đầu tốt ở Stage 1-2 của recipe (§10), nhưng nên chuyển sang ZILN khi có đủ dữ liệu.

### 3.4 Ordinal / quantile / multi-distribution approaches

**[Primary research]** Kuaishou (arXiv:2208.13358, CIKM 2022) giải bài toán ở quy mô tỷ người dùng bằng hai thành phần:

- **ODMN (Order Dependency Monotonic Network)**: mô hình hóa **ràng buộc thứ tự** giữa LTV ở các horizon khác nhau. Về bản chất LTV là hàm không giảm theo thời gian: `LTV_7 ≤ LTV_30 ≤ LTV_90 ≤ LTV_180`. Ép ràng buộc này vào kiến trúc (dự đoán các số hạng tăng thêm không âm thay vì dự đoán độc lập) vừa giảm không gian giả thuyết vừa cho ra dự báo nhất quán.
- **MDME (Multi Distribution Multi Experts)**: chia-để-trị — chia phân phối LTV cực kỳ mất cân bằng thành một chuỗi sub-distribution tương đối cân bằng, mỗi expert lo một khoảng, cộng thêm một bộ phân loại chọn khoảng. Biến bài toán hồi quy đuôi nặng thành **phân loại ordinal + hồi quy trong khoảng**.

```text
Sơ đồ ordinal-binning (nguyên lý chung, dùng được độc lập với paper):
  1. Chia LTV > 0 thành K bin theo quantile:  b_1 < b_2 < ... < b_K
  2. Head phân loại: P(bin = k | x), train bằng ordinal loss (K−1 binary
     classifier dạng P(LTV > b_k | x)) thay vì softmax — giữ được thứ tự
  3. Head hồi quy trong bin: E[LTV | bin = k, x]
  4. LTV̂ = Σ_k P(bin = k | x) · E[LTV | bin = k, x]

Lợi ích: outlier chỉ ảnh hưởng tới bin cuối, không làm hỏng gradient toàn cục.
```

**Quantile regression (pinball loss)** — thay thế nhẹ hơn, không cần assume phân phối:

```text
L_τ(y, ŷ) = max( τ·(y − ŷ),  (τ − 1)·(y − ŷ) )

Train nhiều head τ ∈ {0.5, 0.8, 0.9, 0.95, 0.99} đồng thời.
Vấn đề: quantile crossing (Q_0.9 < Q_0.8). Fix bằng:
  - dự đoán Q_0.5 + các delta không âm (softplus) cộng dồn, hoặc
  - isotonic post-processing trên các quantile output
```

### 3.5 Transformer-based LTV

**[Primary research]** Các công trình gần đây đưa sequence encoder vào LTV:

- **SHORE** (arXiv:2506.10487) — LTV dài hạn cho digital games. Dùng dự đoán **short-cycle làm auxiliary task** (LTV-15, LTV-30) để hỗ trợ target dài (LTV-60), kết hợp **order-preserving regression**. Đây chính xác là công thức bạn cần cho bài toán "chỉ có D7 mà phải đoán D180" (§8).
- **Mini-Game LTV in WeChat** (arXiv:2506.11037) — coi LTV như bài toán time-series, đưa các phương pháp forecasting hiện đại vào.
- **USE-LTV** (Baidu Ads, CIKM 2025) — transformer module trích xuất đặc trưng từ chuỗi hành vi *không chắc chắn*, cộng cơ chế dynamic weight.
- **MC-Dropout LTV** (arXiv:2411.15944) — benchmark trên dữ liệu một trong những mobile game được tải nhiều nhất thế giới, tập trung vào uncertainty estimation (§7).

**[Design synthesis]** Rút ra ba nguyên lý lặp đi lặp lại trong các paper industrial này, độc lập với kiến trúc cụ thể:
1. **Multi-horizon + monotonic** luôn thắng single-horizon.
2. **Auxiliary short-horizon task** là cách rẻ nhất để chống censoring của label dài hạn.
3. **Phân rã phân phối** (zero/bin/expert) luôn thắng hồi quy trực tiếp trên đuôi nặng.

---

## 4. Survival analysis cho churn

Churn về bản chất là bài toán **time-to-event**, không phải phân loại nhị phân. Coi nó là phân loại là cách nhanh nhất để vứt bỏ 30-60% dữ liệu.

### 4.1 Các khái niệm nền

```text
T       = thời gian tới sự kiện (churn), tính theo TENURE (ngày kể từ install)
S(t)    = P(T > t)                      survival function
f(t)    = −S'(t)                        density
h(t)    = f(t) / S(t)                   HAZARD: rate churn tức thời, biết đã sống tới t
H(t)    = ∫₀ᵗ h(u) du                   cumulative hazard
S(t)    = exp(−H(t))

Trong game, h(t) có dạng đặc trưng "bathtub cụt":
   h(0→1) rất cao  (60-75% mất ngay D1)
   h giảm mạnh tới ~D7
   h gần phẳng, thấp sau ~D30
   ⇒ đây là lý do Exponential (h hằng số) và cả Weibull đơn lẻ đều fit kém.
     Cần: piecewise / spline hazard, hoặc mixture cure model.
```

**Cure model / mixture:** một tỉ lệ người chơi thực chất "không bao giờ churn trong horizon quan tâm" (long-term retained). Mô hình `S(t) = π + (1−π)·S_uncured(t)` phản ánh điều này và tránh việc mô hình bị ép phải cho mọi người churn khi t → ∞. **[Design synthesis]**

### 4.2 Kaplan-Meier

```text
Ŝ(t) = Π_{t_j ≤ t}  ( 1 − d_j / n_j )
   d_j = số churn tại thời điểm t_j
   n_j = số còn "at risk" ngay trước t_j  (đã trừ cả churn lẫn CENSORED trước đó)
```

Không tham số, xử lý right-censoring đúng cách, **không có covariate**. Dùng để: vẽ retention curve theo segment, log-rank test so sánh hai nhóm (A/B test retention), và làm sanity check cho mọi mô hình phức tạp hơn.

### 4.3 Cox Proportional Hazards

```text
h(t | x) = h₀(t) · exp(β'x)

Ước lượng β qua partial likelihood — KHÔNG cần đặc tả h₀(t).
HR = exp(β_j) = hazard ratio khi x_j tăng 1 đơn vị.
```

**Giả định PH bị vi phạm gần như chắc chắn trong game.** Ví dụ: "hoàn thành tutorial" có hazard ratio rất mạnh ở D1-D3 và gần như vô nghĩa ở D60. Đó là **time-varying effect**, không phải proportional. Kiểm tra bằng Schoenfeld residuals; nếu vi phạm thì: (a) stratify theo biến vi phạm, (b) thêm interaction `x·log(t)`, hoặc (c) chuyển sang AFT / discrete-time.

### 4.4 AFT (Accelerated Failure Time)

```text
log T = β'x + σ·ε        ε ~ Extreme Value (→Weibull) / Normal (→LogNormal) / Logistic (→LogLogistic)

Diễn giải: exp(β_j) = HỆ SỐ NHÂN vào thời gian sống, không phải vào hazard.
"Người chơi hoàn thành FTUE sống lâu gấp 2.3×" — dễ giải thích với product hơn HR.
```

**[Design synthesis]** AFT với phân phối **log-logistic** hoặc **lognormal** thường fit game tốt hơn Cox, vì cả hai cho phép hazard **không đơn điệu** (tăng rồi giảm), khớp với hình dạng thực tế của retention curve.

### 4.5 Discrete-time hazard — lựa chọn thực dụng nhất cho game

Telemetry game vốn là **daily**. Chuyển dữ liệu sang dạng **person-period** rồi chạy hồi quy logistic là cách đơn giản nhất để có được đồng thời: covariate thay đổi theo thời gian, hazard shape tùy ý, và toàn bộ hạ tầng ML tabular sẵn có (LightGBM/XGBoost).

```text
Từ 1 dòng/người  →  1 dòng/(người, ngày):

player  day  churn_next  level  sessions_3d  balance  paywall_hits  is_liveops_day ...
  u1     1       0         3        2.0        150         1              0
  u1     2       0         5        2.3        80          3              1
  u1     3       1         5        0.7        20          4              1
  u2     1       0        ...
  (dòng dừng lại tại ngày churn HOẶC tại data_cutoff — censored)

Mô hình:  logit( h(t | x_t) ) = α(t) + β'x_t
   α(t): hazard baseline theo tenure — dùng dummy cho t nhỏ (1..14),
         spline/log(t) cho t lớn. ĐỪNG dùng t tuyến tính.

Survival tổng hợp:  Ŝ(T) = Π_{t=1..T} ( 1 − ĥ(t | x_t) )

Dùng link complementary log-log:  cloglog(h) = α(t) + β'x
   → tương đương xấp xỉ Cox PH ở dạng rời rạc (grouped proportional hazards)
```

Ưu điểm quyết định: **time-varying covariates trở nên tự nhiên**. Trạng thái người chơi thay đổi hằng ngày (level, balance, guild, streak thua, số lần đụng paywall) — đây chính là các predictor mạnh nhất, và mô hình phân loại "1 dòng/người" không thể dùng chúng mà không leak.

**Chi phí:** dataset phình ra ~N × average_tenure dòng. Với 1M người chơi × 30 ngày = 30M dòng — vẫn nằm trong tầm LightGBM trên một máy. Có thể subsample dòng censored/negative với importance weight bù lại.

### 4.6 Competing risks

Không phải mọi "biến mất" đều giống nhau:

```text
Risk 1: churn tự nhiên (chán, hết content)
Risk 2: uninstall (thiết bị mất app — quan sát khác nhau)
Risk 3: chuyển sang trạng thái không đo được (opt-out ATT, đổi thiết bị)
Risk 4: ban / fraud
Risk 5 (cạnh tranh THEO HƯỚNG TỐT): chuyển thành payer — thay đổi hoàn toàn hazard
```

Hai cách xử lý:
- **Cause-specific hazard**: fit riêng cho từng nguyên nhân, coi các nguyên nhân khác là censored. Đúng cho câu hỏi *nguyên nhân học* ("yếu tố nào đẩy churn tự nhiên").
- **Subdistribution hazard (Fine & Gray)**: cho ra **CIF** (cumulative incidence function) đúng cách. Đúng cho câu hỏi *dự báo* ("xác suất người này churn tự nhiên trong 14 ngày tới là bao nhiêu, trong thế giới có cả các risk khác").

**[Design synthesis]** Trong decision engine, bạn thường cần CIF, vì action được quyết định trên xác suất tuyệt đối, không phải hazard có điều kiện.

### 4.7 Machine learning survival models

**[Primary research]**
- **Random Survival Forests** (Ishwaran et al., 2008, *Annals of Applied Statistics*): rừng cây với splitting rule tối đa hóa log-rank statistic; mỗi lá cho ra một KM estimate; không giả định PH, tự bắt interaction. Rất mạnh trên dữ liệu tabular game.
- **Survival ensembles cho mobile social games** (Periáñez, Saas, Guitart & Magne, arXiv:1710.02264): áp dụng trực tiếp survival ensembles vào churn prediction trong mobile social game, nhấn mạnh xử lý censored data — người chơi vẫn đang chơi thì chưa churn, không phải "negative".
- **DeepSurv** (Katzman et al., arXiv:1606.00931): MLP thay thế `β'x` trong Cox partial likelihood. Vẫn **giữ nguyên giả định PH** — đây là hạn chế thường bị bỏ qua.
- **DeepHit** (Lee, Zame, Yoon & van der Schaar, AAAI 2018): học **trực tiếp phân phối rời rạc** của thời gian sự kiện, **không giả định PH**, hỗ trợ **competing risks** sẵn. Loss = NLL + ranking loss (khuyến khích xếp hạng đúng theo thời gian). Về mặt khái niệm là lựa chọn phù hợp nhất cho churn game nhiều nguyên nhân — nhưng cần dữ liệu lớn và tune cẩn thận.

### 4.8 Censoring — lỗi phổ biến nhất trong game churn modeling

**[Design synthesis]** Đây là phần đáng đọc kỹ nhất của tài liệu này.

```text
LỖI #1 — "Censored = not churned"
   Lấy snapshot hôm nay, gán churn=0 cho mọi người còn active.
   Người install hôm qua và người install 6 tháng trước cùng nhãn 0.
   ⇒ Mô hình học "tenure ngắn = không churn". Đảo ngược hoàn toàn sự thật.
   FIX: hoặc dùng survival, hoặc bắt buộc observation window đầy đủ
        (chỉ giữ người có install_time + k ≤ data_cutoff).

LỖI #2 — Vứt bỏ toàn bộ censored observation
   "Chỉ train trên người đã churn rõ ràng."
   ⇒ Loại bỏ chính nhóm retained tốt nhất. KM/Cox trên tập này over-estimate hazard.
   ⇒ Đây là survivorship bias ngược.
   FIX: censored observation MANG THÔNG TIN ("sống được ít nhất T"). Dùng nó.

LỖI #3 — Nhầm calendar time với tenure time
   Vẽ hazard theo ngày lịch thay vì theo ngày kể từ install.
   ⇒ Trộn lẫn cohort; một đợt UA lớn làm hazard "tăng vọt" một cách giả tạo.
   FIX: mọi trục thời gian trong survival phải là TENURE. Calendar time chỉ
        vào mô hình dưới dạng covariate (is_liveops_day, patch_version).

LỖI #4 — Random train/test split
   Cùng một người chơi xuất hiện ở cả train lẫn test (person-period rows).
   Hoặc: sự kiện tương lai lọt vào train.
   ⇒ AUC 0.95 offline, vô dụng online.
   FIX: split theo COHORT INSTALL DATE, và group-split theo player_id.

LỖI #5 — Bỏ qua LEFT TRUNCATION
   Bắt đầu logging từ ngày X, nhưng dân số có sẵn đã install từ trước.
   Những người này "được chọn vì đã sống sót tới X".
   ⇒ Over-estimate retention nếu không xử lý.
   FIX: dùng dạng counting-process (entry, exit, event) với entry > 0,
        hoặc chỉ dùng cohort install sau X.

LỖI #6 — INFORMATIVE CENSORING (nguy hiểm và tinh vi nhất)
   Giả định nền của mọi mô hình survival: censoring ĐỘC LẬP với sự kiện.
   Trong mobile, điều này bị vi phạm mạnh:
     - ATT/GDPR opt-out: người opt-out ngừng gửi telemetry → trông như churn,
       nhưng có thể vẫn đang chơi
     - Đổi thiết bị / reinstall không nối được ID → churn giả
     - SDK crash trên device tier thấp → mất data ĐÚNG Ở NHÓM churn nhiều nhất
   ⇒ Bias không thể sửa bằng mô hình tốt hơn; phải sửa bằng instrumentation.
   FIX: đo tỉ lệ mất telemetry theo device/geo; nếu tương quan với outcome,
        dùng IPCW (inverse probability of censoring weighting) hoặc ít nhất
        report bias bound. Ghi rõ trong model card.

LỖI #7 — Định nghĩa churn threshold trên chính tập test
   Chọn k = 14 vì nó cho AUC cao nhất.
   FIX: chọn k trên cohort riêng, theo tiêu chí ở §1.2.
```

---

## 5. Sequence models trên event stream

### 5.1 Tại sao cần

Feature tabular kiểu "tổng số session trong 7 ngày" phá hủy thông tin **thứ tự** và **nhịp điệu**. Hai người chơi cùng 20 session/7 ngày nhưng một người `[8,6,4,1,1,0,0]` (đang chết) và một người `[1,1,2,4,4,4,4]` (đang lên) có LTV hoàn toàn khác nhau.

### 5.2 Kiến trúc theo thứ tự chi phí tăng dần

```text
(a) Tabular + trend features  — RẺ, nên làm TRƯỚC
    slope(sessions, 3d), ratio(playtime_last3d / playtime_prev3d),
    time_since_last_session, entropy(hour_of_day), streak_days
    ⇒ bắt được ~60-70% giá trị của sequence model với 1% chi phí

(b) GRU / LSTM trên chuỗi ngày
    input: [T, F] — T ngày × F feature/ngày
    output: hidden state → head ZILN / hazard
    ⇒ ổn định, ít data-hungry, phù hợp T ≤ 30

(c) TCN — Temporal Convolutional Network (Bai, Kolter & Koltun, arXiv:1803.01271)
    dilated causal convolution: receptive field lớn, train song song hoàn toàn,
    gradient ổn định hơn RNN. Với chuỗi dài (T = 90-180 ngày) thường thắng LSTM.

(d) Transformer trên chuỗi EVENT (không phải chuỗi ngày)
    Dòng dõi SASRec (Kang & McAuley, arXiv:1808.09781) —
      unidirectional causal self-attention, next-item prediction
    và BERT4Rec (Sun et al., arXiv:1904.06690) —
      bidirectional, masked-item objective (pretrain), fine-tune xuống task

    Chuyển hoá sang game:
      "item" → event type (level_start, level_fail, iap_view, iap_purchase,
                            rewarded_ad_watch, currency_spend, guild_join, ...)
      Mỗi token = (event_type_emb + value_bucket_emb + Δt_bucket_emb + positional)
      ⚠ Δt (khoảng cách thời gian giữa event) là feature QUAN TRỌNG NHẤT
        mà positional encoding thuần không bắt được. Phải encode tường minh.
```

### 5.3 Pretrain → finetune (mẫu hình mạnh nhất)

**[Design synthesis]** Áp dụng công thức BERT4Rec vào player modeling:

```text
Giai đoạn 1 — PRETRAIN (self-supervised, dùng TOÀN BỘ người chơi, kể cả non-payer)
   Objective: masked event prediction / next event prediction
   ⇒ học được "player embedding" phổ quát, không cần label LTV
   ⇒ tận dụng 97% người chơi không trả tiền — dữ liệu vốn bị lãng phí

Giai đoạn 2 — FINETUNE (supervised, chỉ trên cohort đã matured)
   Gắn head: ZILN cho LTV, discrete hazard cho churn, ordinal cho bin
   ⇒ multi-task: chia sẻ encoder, các head riêng
   ⇒ giải quyết được vấn đề "ít label matured" ở §8
```

### 5.4 Player embedding — sản phẩm phụ có giá trị nhất

Vector từ encoder dùng lại được cho: segmentation (cluster), similar-player lookup (cold start cho người mới bằng k-NN trong embedding space), input cho bandit context, và anomaly/fraud detection. **[Design synthesis]** Đây thường là ROI cao nhất của việc build sequence model, hơn cả độ chính xác LTV tăng thêm.

### 5.5 Cảnh báo thực tế

- Attention weight **không phải** giải thích nhân quả. Đừng đưa attention heatmap cho product team như thể nó là insight về nguyên nhân.
- Sequence model dễ overfit vào **artifact của phiên bản game**. Sau một patch lớn, phân phối event thay đổi → cần retrain hoặc ít nhất recalibrate.
- Chi phí serving: transformer trên chuỗi 500 event × 10M DAU là bài toán infra thật. Cân nhắc precompute embedding hằng đêm + head nhẹ real-time.

---

## 6. Evaluation làm đúng cách

### 6.1 Tại sao AUC là không đủ

```text
AUC chỉ đo THỨ TỰ trên nhãn NHỊ PHÂN. Ba thất bại chí mạng:

1. AUC bất biến với mọi phép biến đổi đơn điệu.
   Model A dự đoán [0.01, 0.02, 0.03], model B dự đoán [0.10, 0.50, 0.90].
   Cùng AUC. Nhưng nếu bạn bid tiền theo prediction, A và B lệch nhau 10×.

2. AUC coi mọi cặp là như nhau.
   Xếp sai một whale $500 và xếp sai một minnow $0.99 phạt như nhau.
   Trong LTV, sai ở whale mới là điều đáng quan tâm.

3. AUC không nói gì về ĐỘ LỚN giá trị.
   Một mô hình churn AUC 0.85 vẫn có thể cho ra reward signal vô dụng cho bandit
   nếu nó không calibrated.
```

### 6.2 Bộ metric tối thiểu cho LTV

**[Primary research]** ZILN paper khuyến nghị normalized Gini + decile chart; phần dưới mở rộng thành bộ đầy đủ.

```text
A. DISCRIMINATION (xếp hạng có đúng không)
   • Normalized Gini:
       Lorenz: sắp xếp user theo ŷ giảm dần, tính cumulative % của y thực tế
       Gini(model) = 2 × AUC_lorenz − 1
       Normalized Gini = Gini(model) / Gini(oracle)   ∈ [0, 1]
       ⇒ Đây là phiên bản của AUC cho biến mục tiêu LIÊN TỤC, có trọng số theo giá trị.
   • Spearman rank correlation ρ(ŷ, y) — thô hơn nhưng dễ giải thích
   • Top-k revenue capture: (Σ y trong top-k% theo ŷ) / (Σ y toàn bộ)
       Ví dụ báo cáo: "top 1% theo prediction bắt được 34% doanh thu thực tế"

B. CALIBRATION (con số có đúng độ lớn không) — QUAN TRỌNG NHẤT khi dùng cho bidding
   • Overall bias ratio:  R = Σŷ / Σy      (mục tiêu 0.95 ≤ R ≤ 1.05)
   • Decile chart: chia 10 bucket theo ŷ, vẽ mean(ŷ) vs mean(y) mỗi bucket.
     Bệnh điển hình: khớp ở decile 1-8, under-predict nặng ở decile 10.
   • Calibration slope: hồi quy y ~ a + b·ŷ. b < 1 ⇒ over-dispersion của prediction.
   • Với head phân loại (p của ZILN, hazard): reliability diagram + ECE
       ECE = Σ_m (|B_m| / n) · | acc(B_m) − conf(B_m) |
       ⚠ Với positive rate ~2%, dùng equal-MASS binning, không equal-width.
   • Sửa calibration: isotonic regression (nhiều data) hoặc Platt/temperature
     scaling (ít data), fit trên tập calibration RIÊNG BIỆT.

C. SAI SỐ ĐỘ LỚN (báo cáo CẢ HAI, không chọn một)
   • Trên raw scale:  MAE, RMSE, và MAE có trọng số doanh thu
   • Trên log scale:  MAE(log(1+ŷ), log(1+y))
   ⇒ Log-only che giấu thảm họa ở whale. Raw-only bị whale chi phối.
     Báo cáo cả hai + top-decile MAE riêng.

D. VỚI MÔ HÌNH PHÂN PHỐI (ZILN, quantile, conformal)
   • Interval coverage: % thực tế nằm trong [Q_0.05, Q_0.95] — mục tiêu 90%
   • Interval width (sharpness) — coverage đạt được bằng interval rộng vô nghĩa
   • PIT histogram: nếu mô hình đúng, PIT ~ Uniform(0,1). Hình U ⇒ under-dispersed.
   • CRPS — proper scoring rule cho toàn bộ phân phối
```

### 6.3 Đánh giá TẠI ngưỡng quyết định

**[Design synthesis]** Nếu engine chỉ can thiệp vào 5% người chơi rủi ro nhất, thì AUC trên 100% dân số là một con số không ai tiêu được.

```text
Báo cáo tại chính operating point:
   precision@5%, recall@5%, lift@5% = precision@5% / base_rate
   expected_revenue_at_risk@5% = Σ (LTV̂ × P(churn)) trong top 5%
   cost-weighted: EV = Σ [ p_save × ΔLTV − cost_of_offer ] trên nhóm được chọn

Vẽ toàn bộ đường cong lift/precision theo k, rồi chọn k theo ràng buộc ngân sách,
KHÔNG chọn k theo F1.
```

### 6.4 Backtesting theo thời gian

```text
SAI:  random 80/20 split
ĐÚNG: rolling-origin backtest theo COHORT INSTALL DATE

   |───── train cohorts ─────|─ gap ─|── test cohorts ──|
   install ≤ T₀                       T₀+g < install ≤ T₁

   • "gap" bắt buộc: đủ dài để label của test cohort đã matured tại thời điểm
     mô hình được coi là "deploy" — mô phỏng đúng độ trễ thực tế.
   • Lặp origin T₀ qua nhiều mốc (rolling) → phân phối metric, không phải 1 con số.
   • BÁO CÁO ĐỘ SUY GIẢM: metric giảm bao nhiêu khi test cohort xa train cohort
     3 tháng? Đây là chỉ số quyết định TẦN SUẤT RETRAIN.

Backtest cấp COHORT (con số business thực sự dùng):
   Với mỗi cohort install-week w:  predicted_cohort_LTV180(w) vs actual
   ⇒ Bias ở cấp cohort mới là thứ làm vỡ ngân sách UA, không phải per-user MAE.

Kiểm tra chống rò rỉ (chạy tự động trong CI):
   Với MỌI feature f: timestamp(f) ≤ decision_time. Không ngoại lệ.
   Test hiệu quả: shuffle nhãn → mọi metric phải sập về ngẫu nhiên.
   Nếu vẫn > baseline ⇒ có leak trong pipeline.
```

---

## 7. Uncertainty quantification

### 7.1 Hai loại uncertainty — phải tách bạch

```text
ALEATORIC (nhiễu vốn có): "người chơi này CÓ THỂ chi $0 hoặc $200, thật sự không
   biết được." Không giảm được bằng thêm dữ liệu.
   → ZILN cho ra trực tiếp qua σ(x); quantile head cho ra qua độ rộng interval.

EPISTEMIC (thiếu hiểu biết của mô hình): "mô hình chưa từng thấy ai giống người
   chơi này." GIẢM ĐƯỢC bằng thêm dữ liệu.
   → MC dropout, deep ensembles, Bayesian last layer, khoảng cách trong embedding space.

⚠ Bandit exploration bonus phải dùng EPISTEMIC, không phải tổng.
  Khám phá thêm ở một segment có aleatoric cao nhưng epistemic thấp là
  đốt tiền vô ích — bạn đã biết rõ segment đó rồi, nó chỉ vốn dĩ nhiễu.
```

### 7.2 Các phương pháp

**[Primary research]**
- **Quantile regression** (pinball loss, §3.4): rẻ nhất, không giả định phân phối, cho ra aleatoric.
- **ZILN**: cho phân phối dự đoán đầy đủ, quantile ở dạng đóng.
- **MC Dropout** (Gal & Ghahramani, arXiv:1506.02142): giữ dropout bật khi inference, lấy T mẫu forward pass. `Var_epistemic ≈ Var_t(ŷ_t)`. Rẻ nhất trong các phương pháp epistemic. Đã được benchmark cho CLV trên dữ liệu mobile game (arXiv:2411.15944).
- **Deep Ensembles** (Lakshminarayanan et al., arXiv:1612.01474): M mô hình khởi tạo khác nhau. Thường **chính xác hơn MC dropout** về ước lượng epistemic, nhưng đắt M lần cả train lẫn serve. M = 5 là điểm cân bằng phổ biến.
- **Conformal Prediction / CQR** (Romano, Patterson & Candès, arXiv:1905.03222): bọc quanh *bất kỳ* mô hình nào, cho **coverage đảm bảo hữu hạn mẫu, không giả định phân phối**, chỉ cần **exchangeability**.

```text
Split Conformal (LTV):
  1. Chia data: train / calibration / test
  2. Fit mô hình bất kỳ trên train
  3. Trên calibration: tính nonconformity score  s_i = |y_i − ŷ_i|
     (CQR dùng score bất đối xứng: s_i = max(Q̂_lo(x_i) − y_i, y_i − Q̂_hi(x_i)))
  4. q̂ = ⌈(n+1)(1−α)⌉/n quantile của {s_i}
  5. Interval:  [ Q̂_lo(x) − q̂ ,  Q̂_hi(x) + q̂ ]
  ⇒ P(y ∈ interval) ≥ 1 − α

⚠ CẢNH BÁO cho game: exchangeability BỊ VI PHẠM khi có drift (patch, LiveOps,
  đổi UA mix). Coverage đảm bảo sẽ mất hiệu lực. Dùng:
    - weighted conformal (trọng số theo độ mới), hoặc
    - adaptive conformal: cập nhật α_t online dựa trên coverage error gần đây
      α_{t+1} = α_t + γ·(α − 1{y_t ∉ C_t})
```

### 7.3 Uncertainty nuôi exploration bonus của bandit như thế nào

**[Design synthesis]** Đây là mắt xích nối tài liệu này với decision engine.

```text
Contextual bandit chọn action a cho người chơi x. Reward = ΔLTV.

Thompson Sampling:
   Sample  r̃(x, a) ~ PosteriorPredictive(x, a)
   Chọn    a* = argmax_a r̃(x, a)
   ⇒ Cần POSTERIOR, không chỉ point estimate.
     Deep ensemble / MC dropout / PyMC posterior đều cấp được.

UCB:
   score(x, a) = μ̂(x, a) + c · σ̂_epistemic(x, a)
   ⇒ Nếu vô ý dùng σ_total (gồm aleatoric), engine sẽ ưu tiên vĩnh viễn các
     segment nhiễu cao và không bao giờ hội tụ. Đây là failure mode có thật.

Ba vai trò khác của uncertainty:
   (1) GATING: chỉ để engine tự động can thiệp khi interval đủ hẹp; ngoài ra
       fallback về rule an toàn. Áp dụng cho cold-start user (§9).
   (2) BID SHADING: bid theo Q_0.4 thay vì mean khi σ lớn → giảm winner's curse.
   (3) TRIGGER RETRAIN: epistemic uncertainty trung bình tăng dần theo thời gian
       là tín hiệu drift sớm hơn cả khi metric offline suy giảm.
```

---

## 8. Delayed / censored labels: đoán D180 khi chỉ có D7

Đây là ràng buộc trung tâm của LTV modeling. Nếu chờ label D180 chín, mô hình của bạn luôn trễ 6 tháng so với game hiện tại.

### 8.1 Phổ các cách tiếp cận

```text
(1) CURVE EXTRAPOLATION (không ML) — luôn build cái này TRƯỚC
    Fit đường cong cumulative-revenue-per-install theo tenure cho từng segment:
       LTV(d) = a · d^b            (power law — phổ biến, fit tốt IAP)
       LTV(d) = L∞ · (1 − e^{−d/τ})  (bão hòa — fit tốt ad revenue)
    Ước lượng trên cohort đã chín, áp dụng cho cohort mới.
    ⇒ Rẻ, robust, giải thích được, là BASELINE mà mọi mô hình ML phải vượt qua.
    ⚠ Chọn dạng hàm quan trọng hơn chọn thuật toán: power law không bão hòa
      (LTV → ∞), dễ over-forecast ở d lớn. Kẹp trần bằng dữ liệu quan sát.

(2) MULTIPLIER PER SEGMENT
    k_s = E[LTV180 | segment s] / E[LTV7 | segment s]  ước lượng trên cohort chín
    L̂TV180(i) = LTV7(i) × k_{s(i)}
    ⇒ Vẫn được dùng rộng rãi trong UA. Đơn giản đến bất ngờ mà khó bị đánh bại
      khi segment đủ mịn (country × platform × campaign_type).
    ⚠ Giả định k ổn định. Sau một thay đổi monetization, k đổi ngay lập tức
      nhưng bạn chỉ phát hiện ra sau 180 ngày.

(3) DIRECT SHORT→LONG REGRESSION
    Train f: features(D0..D7) → LTV180, chỉ trên cohort đã chín.
    ⚠ BIAS CỐT LÕI: cohort chín = cohort cài đặt ≥180 ngày trước = một GAME KHÁC
      (khác balance, khác offer, khác UA mix). Đây là dataset shift không tránh khỏi.
      Phải đo và báo cáo, không thể loại bỏ.

(4) MULTI-HORIZON + ORDER-PRESERVING  ← khuyến nghị chính
    Dự đoán đồng thời LTV7, LTV30, LTV90, LTV180 với ràng buộc đơn điệu.
    [Primary research] ODMN (Kuaishou, arXiv:2208.13358) mô hình hóa ORDER
      DEPENDENCY giữa các horizon.
    [Primary research] SHORE (arXiv:2506.10487) dùng horizon ngắn (LTV-15/30)
      làm AUXILIARY TASK cho horizon dài (LTV-60), cộng order-preserving regression.
    Cài đặt thực dụng:
       head_7  = softplus(z_7)
       head_30 = head_7  + softplus(z_30)      # delta không âm
       head_90 = head_30 + softplus(z_90)
       head_180= head_90 + softplus(z_180)
       Loss = Σ_h w_h · ZILN_loss(y_h, head_h)   với w_h = 0 nếu y_h censored
    ⇒ Cohort MỚI (chỉ có nhãn D7) vẫn đóng góp gradient cho encoder chung.
      Đây chính là cách né dataset shift ở (3).

(5) DELAYED FEEDBACK MODEL (Chapelle, KDD 2014)
    [Primary research] "Modeling delayed feedback in display advertising":
    mô hình hóa TƯỜNG MINH độ trễ từ click tới conversion, giả định delay ~
    Exponential với kỳ vọng chưa biết; huấn luyện bằng EM.
    Ý tưởng chuyển vào LTV: một người chưa mua tính đến hôm nay KHÔNG phải
    negative — họ là CENSORED. Likelihood:
       L = Π_{đã convert} p(x)·f_delay(d_i | x)
           × Π_{chưa convert} [ (1 − p(x)) + p(x)·(1 − F_delay(e_i | x)) ]
       với e_i = thời gian đã trôi qua.
    ⇒ Sửa được bias "gán 0 cho người mới" — bias có tính hệ thống và lớn.

(6) PSEUDO-LABELING TỪ CỬA SỔ KHÔNG ĐẦY ĐỦ
    Dùng mô hình chín để gán nhãn giả cho cohort mới, rồi retrain.
    ⚠ Confirmation bias: mô hình học lại chính niềm tin của nó, và sai số tích lũy
      qua các vòng. Nếu dùng:
        - chỉ pseudo-label khi uncertainty thấp (§7)
        - giảm trọng số pseudo-label (0.3-0.5)
        - GIỮ NGUYÊN một nhánh chỉ-nhãn-thật để phát hiện phân kỳ
        - giới hạn số vòng lặp (≤ 2)
```

### 8.2 Label shift correction

```text
Nếu chỉ có nhãn cũ (p_old) nhưng cần dự đoán cho dân số mới (p_new):

Covariate shift (P(x) đổi, P(y|x) giữ nguyên):
   w(x) = p_new(x) / p_old(x)  — ước lượng bằng một classifier phân biệt
   old-vs-new; nếu classifier cho c(x), thì w(x) ∝ c(x)/(1−c(x)).
   Train lại với trọng số w(x).
   ⚠ Cắt trần w (ví dụ w ≤ 20) — nếu không, phương sai nổ.

Prior/label shift (P(y) đổi, P(x|y) giữ nguyên):
   Ước lượng tỉ lệ payer mới bằng EM hoặc confusion-matrix (BBSE-style),
   rồi hiệu chỉnh lại logit của head phân loại:
      logit_new = logit_old + log(π_new/π_old) − log((1−π_new)/(1−π_old))

⚠ Trong game, thường là CẢ HAI cùng lúc (UA mix đổi VÀ monetization đổi).
  Không có phương pháp nào sửa được đồng thời mà không thêm giả định.
  ⇒ Chấp nhận: RECALIBRATE THƯỜNG XUYÊN trên nhãn ngắn hạn (D7) là biện pháp
    phòng vệ thực tế nhất. Giữ discrimination từ mô hình dài hạn, lấy
    calibration từ dữ liệu mới nhất.
```

**[Design synthesis]** Nguyên tắc vận hành: **discrimination học chậm, calibration học nhanh.** Retrain encoder theo quý; recalibrate (isotonic/multiplier) theo tuần trên nhãn D7 mới nhất.

---

## 9. Cold start: LTV chỉ từ tín hiệu lúc cài đặt

### 9.1 Thang tín hiệu theo thời gian

```text
t = 0 (trước khi mở app lần đầu) — chỉ có install-time signals
    geo (country + tier), platform, OS version, device model → RAM/GPU tier,
    connection type, install source (organic/paid), network, campaign_id,
    ad_group, creative_id, install hour-of-day / day-of-week, store locale,
    referrer, tracking-consent status (ATT)
    ⇒ Sức mạnh dự báo: THẤP ở cấp cá nhân, nhưng CAO ở cấp campaign/cohort.
      Đừng hứa hẹn per-user accuracy ở đây.

t = first session (5-15 phút)
    + thời gian hoàn thành FTUE, số bước tutorial hoàn thành, drop-off step,
      độ dài session 1, FPS/load-time p95, crash trong session 1,
      first-session sink usage, có xem rewarded ad không
    ⇒ Nhảy vọt về sức mạnh dự báo. Đây là điểm bắt đầu cá nhân hóa hợp lý.

t = D1-D3
    + return D1 (predictor đơn lẻ mạnh nhất cho retention dài hạn),
      số session, tiến độ level, nhịp session
    ⇒ Đủ tốt cho hầu hết quyết định trong game.

t = D7
    + tín hiệu monetization đầu tiên: view offer, đụng paywall, mua lần đầu
    ⇒ Đủ cho ROAS forecasting cấp cohort.
```

### 9.2 Hierarchical Bayesian shrinkage — công cụ đúng cho cold start

**[Design synthesis]** Vấn đề: campaign mới có 200 install, ARPU quan sát $0.42 do đúng một người mua $80. Ước lượng naive vô dụng.

```text
Partial pooling qua cấu trúc phân cấp:

   global  →  country_tier  →  country  →  network  →  campaign  →  ad_group

   θ_campaign ~ Normal(θ_network, τ²_campaign)
   θ_network  ~ Normal(θ_country, τ²_network)
   θ_country  ~ Normal(θ_global,  τ²_country)

Hệ quả dạng đóng (empirical Bayes, xấp xỉ):
   θ̂_campaign = w · ȳ_campaign + (1 − w) · θ̂_network
   w = n / (n + κ),   κ = σ²_within / τ²_between

   ⇒ n nhỏ  → shrink mạnh về prior của network (an toàn)
   ⇒ n lớn  → tin vào dữ liệu campaign
   ⇒ κ ĐƯỢC HỌC TỪ DỮ LIỆU, không phải đặt tay. Đây là điểm khác biệt so với
     "smoothing bằng hằng số" mà nhiều team làm.

Với LTV đuôi nặng: shrink trên LOG scale hoặc trên các tham số ZILN (p, μ, σ)
riêng biệt, không shrink trên raw mean — raw mean bị một whale làm hỏng.
```

**[Primary docs]** PyMC-Marketing hỗ trợ hierarchical priors natively cho các mô hình CLV; đây là lý do chính để chọn nó thay vì `lifetimes` khi làm cold start.

### 9.3 Transfer learning giữa các title

```text
Cái KHÔNG transfer được:
   - user identity (privacy/ATT — và không nên cố)
   - feature theo game cụ thể (level_id, item_id)
   - giá trị tuyệt đối của LTV (khác genre, khác monetization depth)

Cái transfer được:
   - encoder cho device/geo/campaign (schema chung giữa các title)
   - FORM của hazard curve theo genre (hypercasual vs midcore khác nhau rõ rệt,
     nhưng trong cùng genre thì tương tự)
   - biểu diễn của "grammar hành vi" (nhịp session, dạng progression curve)
   - MAPPING giữa early signal và long-term outcome (D1 retention → D180 LTV)

Công thức thực dụng:
   1. Pretrain encoder trên gộp nhiều title (multi-task, mỗi title một head)
   2. Với title mới: đóng băng encoder, chỉ train head (ít data)
   3. Khi title mới đủ data: unfreeze dần với learning rate thấp
   4. Thêm "title embedding" làm input để encoder biết mình đang ở đâu

Kiểm tra bắt buộc: NEGATIVE TRANSFER. So sánh với baseline chỉ-dùng-title-mới
trên một holdout. Transfer giữa các genre khác nhau thường LÀM TỆ ĐI.
```

### 9.4 Chính sách vận hành cho cold-start user

```text
IF  n_observations(user) < threshold  OR  epistemic_uncertainty > cap:
      dùng segment-level prior (hierarchical posterior mean)
      KHÓA các action rủi ro cao (offer đắt, paywall aggressive)
ELSE:
      dùng per-user prediction
Ghi log cả hai để đo về sau xem prior tốt hơn hay model tốt hơn ở mỗi mức n.
```

---

## 10. Causal LTV vs Predictive LTV

### 10.1 Phân biệt cốt lõi

```text
PREDICTIVE:   pLTV(x) = E[ LTV | X = x ]
              "Người chơi này sẽ chi bao nhiêu?"

CAUSAL:       τ(x) = E[ LTV | do(A = 1), X = x ] − E[ LTV | do(A = 0), X = x ]
              "Hành động A làm THAY ĐỔI chi tiêu của người này bao nhiêu?"

τ(x) và pLTV(x) có thể TƯƠNG QUAN ÂM. Đây không phải trường hợp hiếm —
đây là trường hợp ĐIỂN HÌNH.
```

### 10.2 Bốn nhóm người chơi (khung uplift kinh điển)

```text
                        Không nhận offer
                     mua          không mua
   Nhận      mua   [ SURE THING ] [ PERSUADABLE ]   ← chỉ nhóm này tạo giá trị
   offer  không mua [ SLEEPING DOG ] [ LOST CAUSE ]  ← Sleeping Dog: offer LÀM HẠI

SURE THING (whale): pLTV cao nhất, τ ≈ 0 hoặc ÂM.
   Cho whale giảm giá 50% → họ vẫn mua, bạn mất 50% doanh thu.
   ⇒ Targeting theo pLTV cao = ưu tiên chính xác nhóm sai.

SLEEPING DOG: nhắc nhở / push notification làm họ nhớ ra là mình muốn bỏ game.
   Có thật, đo được, và bị bỏ qua trong hầu hết hệ thống.
```

**[Design synthesis]** Đây là lý do nói "predictive LTV là reward signal" chỉ đúng một nửa. pLTV đúng vai trò làm **giá trị nền / trọng số** (whale đáng để can thiệp cẩn thận hơn), nhưng **quyết định action nào** phải dựa trên τ.

### 10.3 Xây target incremental-LTV

```text
Điều kiện tiên quyết: RANDOMIZATION. Không có randomization thì không có
causal estimate — mọi kỹ thuật ML dưới đây đều vô hiệu.
   ⇒ Duy trì exploration fraction (5-10%) HOẶC permanent holdout.

Ước lượng:
 (a) TRANSFORMED OUTCOME (Horvitz-Thompson)
     Y* = Y · ( T − e(x) ) / ( e(x) · (1 − e(x)) ),   e(x) = P(T=1 | x)
     E[Y* | x] = τ(x)  ⇒ hồi quy Y* theo x bằng BẤT KỲ regressor nào
     ⚠ Unbiased nhưng PHƯƠNG SAI KHỦNG KHIẾP với LTV đuôi nặng:
       một whale $500 trong nhánh treatment với e = 0.1 tạo ra Y* = 5000.

 (b) META-LEARNERS (Künzel et al., arXiv:1706.03461)
     T-learner: fit μ₁(x) trên treated, μ₀(x) trên control; τ̂ = μ₁ − μ₀
        → đơn giản; regularization bias không triệt tiêu, tệ khi nhóm lệch size
     S-learner: một model với T là feature
        → có xu hướng shrink τ̂ về 0 nếu regularizer coi nhẹ T
     X-learner: dùng μ₀ để impute cho treated (và ngược lại), rồi kết hợp
        theo e(x) → TỐT NHẤT khi treated/control rất lệch nhau về kích thước
     R-learner / DR-learner: doubly robust, dùng residual — ổn định nhất
        khi nuisance model tốt

 (c) CAUSAL FORESTS (Wager & Athey; generalized random forests)
     splitting rule tối đa hóa HETEROGENEITY của treatment effect thay vì
     độ thuần khiết của outcome; có lý thuyết cho khoảng tin cậy.
```

### 10.4 Bài toán phương sai — và cách sống chung

```text
Vì sao khó: Var(Y) trong LTV rất lớn (đuôi nặng), còn |τ| thường nhỏ (vài %).
   Signal-to-noise của uplift ≈ (τ / σ_Y) — có thể nhỏ hơn 100× so với bài toán
   predictive. Với cùng lượng dữ liệu, uplift model học được ít hơn RẤT NHIỀU.

Sáu biện pháp giảm phương sai (dùng KẾT HỢP):
 1. WINSORIZE / CAP outcome (ví dụ cắt tại p99.5). Có bias, nhưng giảm phương sai
    nhiều hơn phần bias thêm vào → MSE giảm. Báo cáo mức cắt.
 2. SURROGATE OUTCOME: dùng D7 spend hoặc D7 engagement thay cho D180.
    Phương sai thấp hơn nhiều, quan sát được sớm hơn nhiều.
    ⚠ Chỉ hợp lệ nếu surrogate thỏa "surrogacy" — treatment ảnh hưởng
      outcome dài hạn CHỈ QUA surrogate. Giả định mạnh; kiểm tra định kỳ
      bằng một thí nghiệm dài hạn thật.
 3. CUPED / variance reduction bằng pre-period covariate:
       Y_adj = Y − θ·(X_pre − E[X_pre]),   θ = Cov(Y, X_pre)/Var(X_pre)
    Giảm phương sai theo hệ số (1 − ρ²). Với ρ = 0.6 → giảm 36%. Gần như miễn phí.
 4. ĐỔI OUTCOME sang thang ít đuôi hơn: P(payer), số giao dịch, log(1+spend).
    Mất một phần ý nghĩa kinh doanh, đổi lấy khả năng học được.
 5. TĂNG e(x) (tỉ lệ treatment gần 0.5 cực tiểu hóa phương sai của contrast).
 6. PHÂN TÍCH CẤP NHÓM: từ bỏ τ(x) cá nhân hóa; ước lượng τ cho 5-10 segment
    định nghĩa trước. Ít tham vọng hơn nhưng thực sự có ý nghĩa thống kê.

⚠ CLUSTER: nếu can thiệp ở cấp guild/lobby/matchmaking, đơn vị độc lập là
  CLUSTER, không phải người chơi. Dùng cluster-robust SE, nếu không CI sẽ hẹp giả.
```

### 10.5 Kiến trúc lai được khuyến nghị

**[Design synthesis]**

```text
Tầng 1 — pLTV (predictive, dữ liệu dày, model tốt):
   dùng cho: value weighting, bid caps, phân bổ ngân sách, segmentation,
             xác định NGƯỠNG rủi ro/giá trị

Tầng 2 — Uplift (causal, dữ liệu thưa, model thô):
   dùng cho: CHỌN action nào cho ai
   Bắt đầu bằng uplift theo SEGMENT (không cá nhân hóa), nâng cấp dần

Tầng 3 — Permanent holdout 1-5%, không bao giờ nhận can thiệp:
   đo incrementality của TOÀN BỘ engine, không phải từng feature
   ⇒ Đây là con số duy nhất trả lời được "engine này có đáng tồn tại không"
   ⇒ Chi phí ~1-5% doanh thu tiềm năng. Đáng giá tuyệt đối.

Reward cho bandit:
   R = τ̂(x, a)                    nếu có đủ dữ liệu causal cho (x, a)
     = pLTV̂(x) · shrink_factor    nếu không, VÀ có guardrail chống
                                   "ưu ái whale" (cap tần suất, cap chiết khấu)
```

---

## 11. Công thức triển khai theo giai đoạn

### 11.1 Stage 0 — dưới ~10k install: KHÔNG dùng ML

```text
Xây:
  • Cohort retention curve (D1/D3/D7/D14/D30) theo country_tier × platform
  • Cumulative revenue-per-install curve, fit power law + saturation, so sánh
  • ARPDAU, ARPPU, conversion rate, theo segment
  • Dashboard hazard theo tenure (chỉ cần KM)
Ra quyết định bằng: rule + segment averages + hierarchical shrinkage (§9.2)
LÝ DO: với 10k install và 2% conversion, bạn có 200 payer. Bất kỳ mô hình nào
       cũng chỉ đang fit nhiễu, và bạn sẽ tin nó.
```

### 11.2 Stage 1 — 10k-100k install: two-stage GBM

```text
FEATURES (tại decision_time = install + 3 ngày):
  install-time: country_tier, platform, device_ram_tier, os_major, network,
                campaign_type, is_organic, install_hour, locale
  session-1:    ftue_completion_time_s, ftue_steps_done, session1_duration_s,
                session1_crash, load_time_p95_ms, session1_rewarded_ad_viewed
  D0-D3:        n_sessions, total_playtime_s, days_active, returned_d1,
                max_level, session_gap_hours_mean, playtime_slope_3d,
                soft_currency_balance, n_insufficient_funds_events,
                n_store_views, n_rewarded_ads, n_deaths_consecutive_max

LABELS:
  y_payer  = 1{ iap_revenue_d30 > 0 }
  y_value  = net_revenue_d30 (chỉ trên payer)
  y_churn  = 1{ không có session trong (d3, d3+14] }, yêu cầu d3+14 ≤ cutoff

MÔ HÌNH:
  Stage 1: LightGBM binary, scale_pos_weight, early stopping trên cohort sau
  Stage 2: LightGBM regression trên log1p(revenue), CHỈ payer
  pLTV = p̂ · expm1(v̂) · smearing_correction
     (Duan smearing: nhân với mean(exp(residual)) để sửa bias Jensen)

BẮT BUỘC CÓ:
  • decile chart mỗi tuần
  • bias ratio Σŷ/Σy theo cohort, alert khi lệch > 10%
  • isotonic calibration fit trên tập calibration riêng
```

### 11.3 Stage 2 — 100k-1M install: ZILN + discrete-time hazard

```text
LTV:   một mạng ZILN (§3.2), 3 head (logit_p, μ, σ), embedding cho
       categorical cardinality cao (campaign_id, device_model)
       + multi-horizon monotonic head (§8.1 mục 4) cho D7/D30/D90

CHURN: discrete-time hazard trên person-period (§4.5), LightGBM với
       tenure dummies + time-varying covariates
       ⇒ output: h(t) hằng ngày → S(t) → CIF cho từng nguyên nhân nếu cần

THÊM:  • quantile head (τ = 0.5/0.9) cho interval thô
       • rolling-origin backtest tự động trong CI
       • leak test (shuffle label) tự động
       • model card ghi rõ: định nghĩa nhãn, cửa sổ, giả định censoring
```

### 11.4 Stage 3 — trên 1M install: sequence + uncertainty

```text
  • Sequence encoder (GRU → TCN → transformer, theo thứ tự thử)
    trên chuỗi event có Δt encoding tường minh (§5.2d)
  • Pretrain self-supervised trên toàn bộ người chơi, finetune multi-task
  • Deep ensemble M=5 hoặc MC dropout cho epistemic uncertainty
  • Split conformal (adaptive) cho interval có đảm bảo
  • Uncertainty → exploration bonus của bandit (§7.3)
  • Precompute player embedding hằng đêm; head nhẹ chạy real-time
```

### 11.5 Stage 4 — tầng causal

```text
  • Bật permanent holdout 1-5% ngay từ ĐẦU (rẻ nhất khi làm sớm)
  • Duy trì exploration fraction có randomization thật, log propensity e(x)
    ⚠ Không log propensity = không bao giờ làm được off-policy evaluation.
      Đây là món nợ kỹ thuật đắt nhất trong toàn bộ hệ thống.
  • Uplift theo segment trước, cá nhân hóa sau
  • CUPED + winsorize + surrogate outcome ngay từ thí nghiệm đầu tiên
  • Báo cáo incrementality cấp engine hằng quý
```

### 11.6 Bảng anti-pattern (dán lên tường)

| Anti-pattern | Hậu quả | Sửa |
|---|---|---|
| Gán churn=0 cho user mới | Mô hình học ngược | Observation window / survival |
| Random train/test split | AUC ảo, sập online | Split theo cohort install date |
| Chỉ báo cáo AUC | Miscalibration không ai thấy | Gini + decile + bias ratio |
| `exp(μ)` thay vì `exp(μ+σ²/2)` | Under-predict có hệ thống | Dùng công thức mean đúng |
| Gross revenue | Over-bid 18-43% | Net sau store fee, trừ refund |
| MSE trên raw LTV | Dự đoán ≈ ARPU cho tất cả | ZILN / ordinal binning |
| Target theo pLTV cao | Trợ giá cho whale, τ ≈ 0 | Uplift model + holdout |
| Không log propensity | Không thể OPE | Log e(x) mọi decision |
| Trộn calendar time vào survival | Cohort mixing | Trục thời gian = tenure |
| Bỏ qua informative censoring | Bias không sửa được bằng model | Đo mất telemetry, IPCW |
| Uncertainty tổng vào UCB | Bandit không hội tụ | Chỉ dùng epistemic |
| Retrain toàn bộ hằng tuần | Bất ổn, khó debug | Encoder theo quý, calibration theo tuần |

---

## 12. Checklist trước khi đưa reward signal vào production

```text
[ ] Nhãn có được tính từ dữ liệu CHỈ có tại decision_time? (leak test đã pass)
[ ] Backtest rolling-origin, không phải random split?
[ ] Bias ratio Σŷ/Σy trong khoảng [0.95, 1.05] trên cohort holdout gần nhất?
[ ] Decile chart không có under-prediction ở decile 10?
[ ] Revenue là NET, đã trừ refund?
[ ] Censoring được xử lý tường minh, và giả định non-informative đã được kiểm tra?
[ ] Có interval/uncertainty, và interval có coverage đúng?
[ ] Có permanent holdout đang chạy?
[ ] Propensity e(x) được log cho mọi quyết định?
[ ] Có model card ghi: định nghĩa nhãn, cửa sổ quan sát, cohort train,
    ngày hết hạn dự kiến, và danh sách giả định có thể vỡ?
[ ] Có alert tự động khi epistemic uncertainty trung bình tăng (drift sớm)?
```

---

## Không xác minh được (ghi rõ)

1. **Con số retention/churn cụ thể theo genre** (ví dụ "D1 retention 25-35% cho midcore"): các dải số trong tài liệu này được nêu ở dạng *khoảng định tính* dựa trên hiểu biết chung của ngành. Chúng **không** được lấy từ một nguồn benchmark cụ thể nào đã kiểm chứng trong phiên nghiên cứu này. Trước khi dùng làm mục tiêu, phải thay bằng benchmark từ dữ liệu của chính bạn hoặc từ một báo cáo có nguồn (data.ai, Sensor Tower, GameAnalytics benchmark) đã được đọc trực tiếp.
2. **Tỉ lệ "top 0.1% chiếm 30-60% doanh thu"** — đúng về mặt định tính (phân phối Pareto), nhưng con số chính xác thay đổi rất mạnh theo genre và thị trường; chưa xác minh từ nguồn gốc trong phiên này.
3. **Mức store fee 15% vs 30%**: quy tắc thay đổi theo chương trình (Small Business Program, subscription năm thứ hai) và theo các thay đổi pháp lý gần đây ở EU/US. Chưa xác minh trạng thái tại 2026-08. Phải kiểm tra tài liệu App Store / Play Console hiện hành.
4. **Trạng thái maintenance của `lifetimes`**: nguồn gián tiếp (PyMC-Marketing ghi nhận "no longer maintained"). Chưa xác minh trực tiếp trên repo GitHub trong phiên này.
5. **Chi tiết kiến trúc bên trong của USE-LTV (Baidu, CIKM 2025)**: chỉ có mô tả tóm tắt từ abstract; chưa đọc bản đầy đủ. Mô tả trong §3.5 giới hạn ở mức abstract nêu ra.
6. **Số hiệu arXiv của SHORE (2506.10487), WeChat Mini-Game LTV (2506.11037), và MC-Dropout CLV (2411.15944)**: xác minh qua kết quả tìm kiếm (tiêu đề + ID khớp), nhưng chưa đọc toàn văn. Nội dung mô tả giới hạn ở phần abstract.
7. **Ngưỡng cụ thể trong staged recipe (10k / 100k / 1M install)**: đây là **[Design synthesis]**, không phải kết quả nghiên cứu. Ngưỡng thực tế phụ thuộc conversion rate — heuristic đúng hơn là "cần ≥ 1.000 payer đã matured trước khi train mô hình value".
8. **Hiệu quả định lượng của các kỹ thuật giảm phương sai** (ví dụ "CUPED giảm 36% khi ρ=0.6"): công thức `(1−ρ²)` là toán học đúng, nhưng giá trị ρ thực tế trong bối cảnh LTV game chưa được đo trong phiên này.

---

## Nguồn chính

- [Primary research] A Deep Probabilistic Model for Customer Lifetime Value Prediction (ZILN; Wang, Liu & Miao, Google) — https://arxiv.org/abs/1912.07753
- [Primary research] Billion-user Customer Lifetime Value Prediction: An Industrial-scale Solution from Kuaishou (ODMN + MDME, CIKM 2022) — https://arxiv.org/abs/2208.13358
- [Primary research] SHORE: A Long-term User Lifetime Value Prediction Model in Digital Games — https://arxiv.org/abs/2506.10487
- [Primary research] Mini-Game Lifetime Value Prediction in WeChat — https://arxiv.org/abs/2506.11037
- [Primary research] Customer Lifetime Value Prediction with Uncertainty Estimation Using Monte Carlo Dropout — https://arxiv.org/abs/2411.15944
- [Primary research] USE-LTV: Customer LifeTime Value Prediction via Uncertain Sequence Modeling in Baidu Ads (CIKM 2025) — https://dl.acm.org/doi/10.1145/3746252.3760876
- [Primary research] Churn Prediction in Mobile Social Games: Towards a Complete Assessment Using Survival Ensembles (Periáñez, Saas, Guitart & Magne) — https://arxiv.org/abs/1710.02264
- [Primary research] DeepSurv: Personalized Treatment Recommender System Using A Cox Proportional Hazards Deep Neural Network (Katzman et al.) — https://arxiv.org/abs/1606.00931
- [Primary research] An Empirical Evaluation of Generic Convolutional and Recurrent Networks for Sequence Modeling (TCN; Bai, Kolter & Koltun) — https://arxiv.org/abs/1803.01271
- [Primary research] Self-Attentive Sequential Recommendation (SASRec; Kang & McAuley) — https://arxiv.org/abs/1808.09781
- [Primary research] BERT4Rec: Sequential Recommendation with Bidirectional Encoder Representations from Transformer (Sun et al.) — https://arxiv.org/abs/1904.06690
- [Primary research] Conformalized Quantile Regression (Romano, Patterson & Candès) — https://arxiv.org/abs/1905.03222
- [Primary research] A Gentle Introduction to Conformal Prediction and Distribution-Free Uncertainty Quantification (Angelopoulos & Bates) — https://arxiv.org/abs/2107.07511
- [Primary research] Dropout as a Bayesian Approximation: Representing Model Uncertainty in Deep Learning (Gal & Ghahramani) — https://arxiv.org/abs/1506.02142
- [Primary research] Simple and Scalable Predictive Uncertainty Estimation using Deep Ensembles (Lakshminarayanan, Pritzel & Blundell) — https://arxiv.org/abs/1612.01474
- [Primary research] Metalearners for estimating heterogeneous treatment effects using machine learning (Künzel, Sekhon, Bickel & Yu) — https://arxiv.org/abs/1706.03461
- [Primary research] On Calibration of Modern Neural Networks (Guo, Pleiss, Sun & Weinberger) — https://arxiv.org/abs/1706.04599
- [Primary research] Modeling Delayed Feedback in Display Advertising (Chapelle, KDD 2014) — https://dl.acm.org/doi/10.1145/2623330.2623634
- [Primary docs] PyMC-Marketing — CLV Quickstart (BG/NBD, Pareto/NBD, Gamma-Gamma, hierarchical priors) — https://www.pymc-marketing.io/en/stable/notebooks/clv/clv_quickstart.html
- [Primary docs] PyMC-Marketing — Gamma-Gamma model notebook (giả định độc lập frequency/monetary) — https://github.com/pymc-labs/pymc-marketing/blob/main/docs/source/notebooks/clv/gamma_gamma.ipynb
- [Primary docs] PyMC-Marketing — trang chính (danh mục mô hình BTYD) — https://www.pymc-marketing.io/en/stable/
