# 35 — Policy Artifact Schema: chỉnh brain từ xa, không cần release app

**Ngày viết:** 2026-08-22
**Phạm vi:** Đặc tả JSON để cấu hình Monetization Brain qua Firebase Remote Config. Đây là mảnh nối giữa SDK (client) và Control Plane (doc 26) — nửa còn lại của sản phẩm.

> **Trạng thái:** đã implement (`BrainPolicy.kt`), đã nối vào `FirHelperConfig.parserCfWithRemoteCf()`, **228/228 unit test PASS** (JDK 17, `./gradlew testDebugUnitTest`).
>
> **Cập nhật 2026-08-24 — schema đã từng TRÔI khỏi code và nay được khoá lại bằng test.** Bản đầu của tài liệu này không nhắc tới `experiment`, `reward`, `iap`, `fx_rates`, `remove_ads_products` dù `BrainPolicy` đã đọc cả năm khối. Kiểu trôi này đặc biệt khó chịu: backend sinh artifact theo tài liệu, client **âm thầm bỏ qua** khối nó không biết, và không ai thấy lỗi vì mọi thứ vẫn "chạy".
>
> Nay có **bản mẫu chuẩn** [`DOC/policy_artifact_example.json`](policy_artifact_example.json) và [`PolicyArtifactContractTest`](../src/test/java/com/app/plugin/brain/PolicyArtifactContractTest.kt) nạp chính file đó rồi kiểm tra từng giá trị tới đúng đích — kể cả một phép đối chiếu tập khoá cấp cao nhất, nên thêm khoá vào parser mà quên cập nhật mẫu (hoặc ngược lại) sẽ đỏ ngay.

---

## A. Vì sao cần

Trước tài liệu này, mọi ngưỡng của brain đều hardcode: trần quảng cáo, cooldown, ngưỡng churn, hệ số kịch bản, hồ sơ likelihood. Muốn chỉnh một con số phải build lại app và chờ user cập nhật — với SDK thì còn tệ hơn, phải chờ **studio khách hàng** release. Doc 31 nêu rõ hệ quả: một bug trong SDK v1.2.0 sẽ chạy 12–24 tháng.

Nguyên tắc rút ra (doc 31 mục 6): **bytecode chỉ chứa bộ máy diễn giải; mọi hành vi thay đổi được phải nằm trong dữ liệu.**

## B. Kênh vận chuyển

Dùng lại đúng hạ tầng đã có, không thêm phụ thuộc mới:

```text
Firebase Remote Config
  key: brain_policy_android   (chuỗi JSON)
        ↓
FirHelperConfig.parserCfWithRemoteCf()   ← đã có sẵn, chỉ thêm 1 khối
        ↓
BrainPolicy.apply(json, guardrails)
        ↓
GuardrailEngine / LikelihoodEstimator / PersonalizationScenario
```

Đây là pattern "policy artifact + local evaluation + server fallback" (doc 16 mục D.1, tiền lệ production Adobe Target on-device decisioning).

## C. Schema đầy đủ

Nguồn sự thật là [`DOC/policy_artifact_example.json`](policy_artifact_example.json) — file đó
được unit test nạp và kiểm tra, còn đoạn dưới đây chỉ là bản chú giải.

```jsonc
{
  // BẮT BUỘC. Thiếu -> artifact bị từ chối. Xuất hiện trong debug view và decision log.
  "version": "policy_2026_08_24_reference",

  // Kill switch personalization. false = mọi player về kịch bản trung tính BALANCED_HYBRID.
  // KHÔNG tắt bandit — muốn brain thôi can thiệp hoàn toàn thì dùng experiment.rollout_fraction=0.
  "enabled": true,

  "guardrails": {
    "max_interstitial_per_session": 4,      // kẹp trần cứng 10
    "min_minutes_between_interstitial": 5,  // kẹp sàn cứng 1
    "max_ads_24h": 30,                      // kẹp trần cứng 60
    "churn_risk_threshold": 0.7             // kẹp [0,1]
  },

  // Ngưỡng chào bán IAP (doc 45 mục G). Cơ sở: doc 29 mục 5.3.
  "iap": {
    "max_offers_per_session": 2,                    // kẹp trần cứng 6
    "min_minutes_between_offers": 15,               // kẹp sàn cứng 3
    "max_purchases_per_session_before_break": 2,    // kẹp trần cứng 5 — circuit breaker
    "frustration_streak_threshold": 4               // kẹp trần cứng 5
  },

  "click_fraud": {
    "max_clicks_per_window": 6,   // kẹp trần cứng 12
    "window_seconds": 300,
    "lockout_seconds": 1800
  },

  // Thí nghiệm & mở rộng dần. Đây là khối quan trọng nhất khi vận hành.
  "experiment": {
    "holdout_fraction": 0.05,          // kẹp [0, 0.5]
    "holdout_salt": "brain_holdout_v1",// đổi = phân nhóm lại TOÀN BỘ dân số
    "exploration_floor": 0.03,         // kẹp [0, 0.5]; đặt 0 sẽ làm OPE vô hiệu
    "rollout_fraction": 0.1            // kẹp [0, 1]; đặt 0 = brain thôi điều khiển mọi player
  },

  // Tham số QUẢN TRỊ, không phải tinh chỉnh kỹ thuật (doc 39). Đẩy đủ mạnh về phía doanh thu
  // sẽ đảo ngược thứ tự ưu tiên và đưa hệ thống về đúng hành vi tham lam nó sinh ra để tránh.
  "reward": {
    "weight_immediate_value": 0.3,
    "weight_continuation": 0.7
  },

  // Trần riêng từng kịch bản. Tên phải khớp enum; tên lạ bị bỏ qua kèm cảnh báo.
  "scenarios": {
    "PROTECT_AND_CONVERT": { "interstitial_cap": 2 },
    "AD_FORWARD":          { "interstitial_cap": 6 },
    "RETENTION_FIRST":     { "interstitial_cap": 1 },
    "BALANCED_HYBRID":     { "interstitial_cap": 3 }
  },

  // Calibrate segment từ xa. PHẢI đủ CẢ 4 type, thiếu thì bỏ qua toàn bộ khối.
  "likelihood_profiles": {
    "HIGH_SKILL_LOW_TOLERANCE":  {"fail_rate":0.15,"rewarded_accept":0.20,"payer_propensity":0.70,"churn_tendency":0.20},
    "HIGH_SKILL_HIGH_TOLERANCE": {"fail_rate":0.15,"rewarded_accept":0.75,"payer_propensity":0.20,"churn_tendency":0.25},
    "LOW_SKILL_HIGH_CHURN_RISK": {"fail_rate":0.60,"rewarded_accept":0.45,"payer_propensity":0.15,"churn_tendency":0.80},
    "CASUAL_STEADY":             {"fail_rate":0.35,"rewarded_accept":0.50,"payer_propensity":0.35,"churn_tendency":0.35}
  },

  // Tỉ giá "1 đơn vị = ? USD". KHÔNG có mã nào ở đây thì SDK không quy đổi (doc 45 mục D.1) —
  // nó để trống tổng USD một cách trung thực thay vì bịa số. USD luôn = 1.0, không ghi đè được.
  "fx_rates": { "EUR": 1.08, "GBP": 1.27, "JPY": 0.0064, "VND": 0.000039 },

  // Sản phẩm nào tắt quảng cáo. Bỏ sót một sub ở đây = subscriber vẫn thấy quảng cáo dù đã
  // trả tiền (anti-pattern doc 27 mục B.3) — nên nó phải sửa được từ xa.
  "remove_ads_products": ["premium_monthly", "premium_yearly", "no_ads_forever"],

  // Độ tin cậy tối thiểu để rời kịch bản trung tính. Mặc định 0.40.
  "min_confidence": 0.4
}
```

Mọi trường ngoài `version` đều **tuỳ chọn** — `{"version":"x","enabled":false}` là artifact hợp lệ.

## D. Bảng kẹp an toàn — phần quan trọng nhất

Artifact **chỉ được làm hệ thống an toàn hơn hoặc bằng**, không bao giờ hung hăng hơn trần biên dịch trong code.

| Trường | Trần cứng | Chiều kẹp | Vì sao |
|---|---|---|---|
| `max_interstitial_per_session` | 10 | trên | nhiều quảng cáo hơn = hung hăng hơn |
| `max_ads_24h` | 60 | trên | như trên |
| `min_minutes_between_interstitial` | 1 | **dưới** | cooldown *ngắn* hơn mới là hung hăng hơn |
| `max_clicks_per_window` | 12 | trên | cho phép nhiều click hơn = bảo vệ yếu hơn |
| `scenarios.*.interstitial_cap` | 10 | trên | như hàng đầu |
| `churn_risk_threshold` | — | kẹp [0,1] | ngoài khoảng là vô nghĩa |
| `iap.max_offers_per_session` | 6 | trên | chào bán nhiều hơn = hung hăng hơn |
| `iap.min_minutes_between_offers` | 3 | **dưới** | cooldown ngắn hơn mới là hung hăng hơn |
| `iap.max_purchases_per_session_before_break` | 5 | trên | ngắt muộn hơn = bảo vệ yếu hơn |
| `iap.frustration_streak_threshold` | 5 | trên | ngưỡng cao hơn = cho phép chào bán khi bế tắc sâu hơn |
| `experiment.holdout_fraction` | — | kẹp [0, 0.5] | holdout quá nửa dân số gần như chắc chắn là lỗi cấu hình |
| `experiment.exploration_floor` | — | kẹp [0, 0.5] | 0 làm OPE vô hiệu; cảnh báo được ghi |
| `experiment.rollout_fraction` | — | kẹp [0, 1] | 100% là đích đến hợp lệ của staged rollout nên không kẹp trần |
| `reward.weight_*` | — | kẹp [0,1] + **kiểm tra tính chất** | nếu trọng số làm "ad rồi mất người" ≥ "im lặng mà giữ người", báo cáo ghi cảnh báo (doc 39) |
| `fx_rates.*` | — | bỏ giá trị ≤ 0 hoặc không hữu hạn | tỉ giá âm tạo doanh thu âm trong báo cáo |

Chú ý chiều kẹp của `min_minutes_between_interstitial` — đây là chỗ dễ sai nhất: với hầu hết trường thì "số lớn = nguy hiểm", nhưng với cooldown thì ngược lại.

### Vì sao ràng buộc này tồn tại

Bài học trực tiếp từ teardown app đối thủ (doc 27 mục B.1). Ở app đó, khi config lỗi cú pháp, hệ thống **tự động** bán gói đắt nhất và tăng mật độ quảng cáo gấp 12 lần — im lặng, không ai nhận ra. Nguyên nhân gốc: lớp fallback hung hăng hơn lớp cấu hình chính, và không ai kẹp giá trị.

Ở đây điều đó không thể xảy ra. Đẩy nhầm `max_ads_24h: 100000` lên Firebase thì giá trị thực thi vẫn là 60, và báo cáo ghi rõ *"max_ads_24h=100000 vượt trần cứng 60 → kẹp về 60"*.

## E. Hành vi khi artifact hỏng

| Tình huống | Hành vi |
|---|---|
| JSON sai cú pháp | Từ chối, **giữ nguyên policy đang chạy** |
| Thiếu `version` | Từ chối |
| Rỗng/null | Từ chối |
| Tên kịch bản/latent type lạ | Bỏ qua riêng mục đó, ghi cảnh báo, phần còn lại vẫn áp |
| `likelihood_profiles` thiếu type | Bỏ qua **toàn bộ** khối profile |
| Giá trị vượt trần | Kẹp lại, ghi cảnh báo, vẫn áp |

Điểm cố ý: artifact mới hỏng **không** làm mất artifact cũ đang chạy tốt. Từ chối một bản cập nhật an toàn hơn là rơi về mặc định.

Lý do `likelihood_profiles` phải đủ 4: trộn hồ sơ mới với hồ sơ cũ tạo ra so sánh khập khiễng giữa các segment — likelihood của các type không còn cùng thang đo, và phân loại sẽ lệch một cách khó phát hiện. Sai kiểu đó tệ hơn không đổi gì.

## F. Cách test không cần Firebase

`BrainPlaygroundActivity` có sẵn 4 nút:

- **Áp policy siết chặt** — artifact hợp lệ, mọi giá trị áp nguyên vẹn
- **Áp policy nới lỏng** — cố tình vượt trần, để thấy cơ chế kẹp hoạt động và đọc báo cáo
- **Bật KILL SWITCH** — mọi player lập tức về kịch bản trung tính
- **Xoá policy** — quay về mặc định trong code

Dòng `policy:` ở đầu debug view luôn hiển thị version đang chạy và cờ kill switch.

## G. Còn thiếu so với chuẩn production (doc 31 mục 8)

Đây là bản tối giản. Chuẩn production cần thêm:

1. **Ký số (Ed25519)** — hiện chưa xác thực nguồn gốc artifact. Ai chiếm được Remote Config có thể đẩy policy tùy ý (dù vẫn bị kẹp trong biên an toàn).
2. **Content-addressed + CDN** — Remote Config có giới hạn kích thước; artifact lớn (warm-start prior cho hàng nghìn cặp segment×arm) phải để trên CDN, config chỉ chứa con trỏ.
3. **`min_sdk_version`** — artifact mới không nên áp cho SDK cũ không hiểu schema.
4. ~~**Staged rollout theo `hash(installation_id)`** — hiện áp 100% ngay lập tức.~~ **ĐÃ CÓ (2026-08-24):** `experiment.rollout_fraction` + `HoldoutManager.isInRollout()`, phân nhóm tất định theo FNV-1a trên một không gian băm riêng nên độc lập thống kê với holdout.
5. **`expiry`** — artifact quá hạn nên tự hết hiệu lực thay vì chạy mãi.

## Không xác minh được (ghi rõ)

1. Giới hạn kích thước thực tế của một tham số Firebase Remote Config chưa được xác minh (doc 31 cũng ghi nhận không tìm được con số chính thức) — cần đo thực tế trước khi thiết kế artifact lớn.
2. Các trần cứng (10/60/1/12) là **giá trị thiết kế do tôi chọn**, chưa dựa trên đo đạc — cần rà lại cùng team monetization trước khi coi là chuẩn.
3. Đường đi thật từ Firebase (fetch → apply) chưa chạy trên thiết bị thật: unit test chỉ kiểm tra `BrainPolicy.apply()` với chuỗi JSON dựng sẵn, chưa kiểm tra khâu `remoteConfig.getString("brain_policy_android")` trả về đúng. Cần một lần test trên thiết bị với Firebase Console thật.
