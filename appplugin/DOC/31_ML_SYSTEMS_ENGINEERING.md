# 31 — ML Systems Engineering: hạ tầng production cho real-time monetization decision engine

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Toàn bộ lớp hạ tầng đứng sau một "monetization decision engine" thời gian thực — tức hệ thống quyết định *ai thấy gì, lúc nào, giá bao nhiêu* (ad placement, offer/IAP, paywall, reward loop) — bao gồm feature store, streaming pipeline, inference serving, monitoring/drift, experimentation platform, decision logging cho off-policy evaluation (OPE), ràng buộc kỹ thuật của Android SDK phía client, phân phối policy artifact, và mô hình chi phí ở 1M/10M/100M DAU. Tài liệu này **không** bàn về thuật toán (bandit, LTV model, pricing) — chỉ bàn về *systems engineering*: cái gì chạy ở đâu, độ trễ bao nhiêu, hỏng thì hỏng thế nào.

Bối cảnh sản phẩm: **sản phẩm cốt lõi là một Android SDK plugin** nhúng vào app của bên thứ ba. Điều đó đảo ngược nhiều ưu tiên so với một backend ML thông thường: ta không kiểm soát vòng đời process, không force-update được client, và mọi ms trên main thread đều là rủi ro ANR ảnh hưởng trực tiếp tới xếp hạng Play Store của *khách hàng*. Mục 7 vì vậy là mục quan trọng nhất.

---

## 0. Bản đồ mười lớp và nguyên tắc chi phối

```text
                    ┌──────────────────────────────────────────────┐
   ANDROID SDK      │  decide() ← policy artifact (on-device)       │
   (host app)       │  event queue (durable) → batch upload         │
                    └───────────────┬──────────────────────────────┘
                                    │ HTTPS, batched, compressed
                    ┌───────────────▼──────────────────────────────┐
   EDGE / GATEWAY   │  auth · rate limit · schema validate · shed   │
                    └───────┬───────────────────────┬──────────────┘
                            │ (a) decision path      │ (b) log path
                ┌───────────▼────────┐      ┌────────▼───────────┐
   ONLINE       │ inference service  │      │  Kafka / Kinesis   │
   (p99 < 50ms) │  ← online store    │      │  (append-only)     │
                └───────────┬────────┘      └────────┬───────────┘
                            │                        │
                ┌───────────▼────────┐      ┌────────▼───────────┐
   NEAR-RT      │  online feature    │◄─────┤ Flink / Spark      │
   (s → min)    │  store (KV)        │      │ windowed agg       │
                └────────────────────┘      └────────┬───────────┘
                                                     │
                ┌────────────────────────────────────▼───────────┐
   OFFLINE      │ Data lake (Parquet/Iceberg) · offline store     │
   (h → d)      │ training · OPE · backfill · experiment analysis │
                └────────────────────────────────────────────────┘
```

Bốn nguyên tắc chi phối toàn bộ tài liệu:

1. **Đường quyết định và đường logging phải tách rời.** Logging chậm hoặc chết không được kéo theo quyết định. Ngược lại, quyết định sai không được làm mất log — log là tài sản duy nhất để đánh giá lại policy sau này.
2. **Training-serving skew là lỗi #1**, không phải model kém. Feature tính bằng SQL lúc train và tính bằng Kotlin lúc serve là hai feature khác nhau.
3. **Mobile events đến muộn là chuyện bình thường, không phải anomaly.** Máy offline, app bị kill, user không mở app 3 ngày. Mọi window aggregation phải thiết kế với giả định đó.
4. **Client không rollback được.** Mọi thứ có thể thay đổi phải nằm trong artifact/config tải về được, không nằm trong bytecode SDK.

---

## 1. Feature store

### 1.1 Hai kho, một định nghĩa

Feature store tồn tại để giải một bài toán duy nhất: **cùng một định nghĩa feature phải sinh ra cùng một giá trị ở hai môi trường có tính chất hoàn toàn khác nhau** — batch (train, throughput-bound, đọc hàng tỷ dòng) và online (serve, latency-bound, đọc 1 entity trong vài ms).

| | Offline store | Online store |
|---|---|---|
| Mục đích | Sinh training set, backfill, OPE | Đọc feature vector lúc `decide()` |
| Storage | Parquet/Iceberg trên S3/GCS, Hive | Redis / DynamoDB / Cassandra / Bigtable |
| Truy vấn | point-in-time join, hàng tỷ dòng | GET theo key, 1–200 features |
| Latency mục tiêu | phút → giờ | p99 vài ms |
| Retention | tháng → năm | giờ → ngày (TTL) |

Uber Michelangelo **Palette** là ví dụ kinh điển của kiến trúc dual-store: Hive cho offline/historical, Cassandra cho online low-latency, với ba pattern tạo feature — batch qua Hive/Spark, near-real-time qua Flink streaming SQL, và "bring your own" từ microservice; Palette được mô tả là host hơn 20.000 feature dùng chung toàn công ty. [Primary engineering blog]

Airbnb **Chronon** (open source 4/2024, được Airbnb, Stripe, Roku dùng production) đi xa hơn: thay vì để engineer viết hai pipeline, Chronon định nghĩa feature một lần qua `GroupBy`/`Join` với windowed/bucketed aggregation, rồi **tự sinh cả batch job lẫn streaming job** từ cùng một spec, đồng thời tự lo backfill point-in-time-correct. Đây là mô hình đáng học nhất cho một team nhỏ: *định nghĩa một lần, compile ra hai runtime*. [Primary engineering blog]

Feast là lựa chọn open-source nhẹ nhất (khai báo feature view, cắm offline store + online store, không tự tính toán); Tecton là bản thương mại có managed streaming transformation. [Primary docs]

### 1.2 Point-in-time correctness — cái bẫy giết mọi model monetization

Sai lầm phổ biến: tạo training set bằng cách join label (user có mua IAP ngày D+7 không) với **giá trị feature hiện tại** trong online store. Kết quả: feature `total_spend_30d` lúc train đã chứa cả khoản chi tiêu *sau* thời điểm quyết định → model học được "user chi tiêu nhiều thì sẽ chi tiêu nhiều", AUC offline 0.95, online không nhích. Đây là leakage kinh điển và nó đặc biệt độc hại với monetization vì hầu hết feature mạnh nhất đều là cumulative spend/engagement.

Cách đúng: mọi feature row phải có `event_timestamp`, và training set sinh bằng **as-of join** (point-in-time join): với mỗi decision log tại thời điểm `t_decision`, lấy giá trị feature mới nhất có `event_timestamp <= t_decision - watermark_lag`. Trừ đi `watermark_lag` là chi tiết hay bị bỏ sót: nếu online store lúc serve thực tế trễ 90 giây so với event stream, thì training set cũng phải mô phỏng đúng độ trễ đó, nếu không model sẽ được train trên feature "tươi hơn thực tế". [Design synthesis]

Cách chắc chắn nhất và rẻ nhất về mặt engineering: **log feature vector đã dùng ngay tại thời điểm quyết định** (xem mục 6). Khi đó training set không cần as-of join gì cả, và training-serving skew về mặt định nghĩa bằng 0. Nhược điểm: không backfill được feature mới, mỗi feature mới phải chờ tích lũy log. Kiến trúc thực dụng là **lai**: logged features cho production model, point-in-time join cho nghiên cứu feature mới.

### 1.3 Freshness tiers

Không phải feature nào cũng cần tươi. Phân tầng để cắt chi phí (ước lượng dưới đây là [Design synthesis]):

| Tier | Ví dụ feature monetization | Đường tính | Freshness | Chi phí tương đối |
|---|---|---|---|---|
| T0 — request-time | placement id, network state, thời gian kể từ ad trước, session depth | tính trên SDK hoặc trong request | 0 ms | ~0 |
| T1 — streaming | impressions_1h, clicks_1h, revenue_session, rewarded_completions_24h | Flink windowed agg → online store | 5–60 s | cao |
| T2 — micro-batch | ctr_7d theo (user × format), fatigue score | Spark 15–30 phút | 15–60 phút | trung bình |
| T3 — daily batch | LTV percentile, cohort, country ARPDAU baseline, embedding | Spark daily | 24 h | thấp |
| T4 — static | app metadata, ad unit config, floor price | config/CDN | ngày | ~0 |

Quy tắc thực dụng: bắt đầu với T0 + T3 (rẻ, đủ để có model hoạt động), chỉ nâng lên T1 cho những feature mà **giá trị thay đổi trong phiên** — với monetization đó là fatigue/frequency-capping signal và revenue-in-session. Đó thường chỉ là 5–15 feature, không phải 200.

### 1.4 Backfill và feature versioning

- **Backfill** là thao tác đắt nhất trong feature store. Với một feature streaming mới, backfill nghĩa là replay toàn bộ event history qua cùng logic aggregation. Chronon giải bằng cách sinh cả batch path và streaming path từ một spec, nên backfill là chạy batch path trên historical partition. Nếu tự xây, **bắt buộc** phải viết logic aggregation ở dạng thư viện dùng chung được cả Flink lẫn Spark gọi, chứ không phải hai bản SQL/Kotlin song song. [Design synthesis]
- **Versioning**: không bao giờ sửa tại chỗ định nghĩa của một feature đang phục vụ production. Đổi định nghĩa = tạo `feature_name__v2`, chạy song song, so sánh phân phối, rồi mới chuyển model sang. Model artifact phải ghi rõ danh sách `(feature_name, version)` nó yêu cầu; serving layer từ chối load model nếu online store thiếu version tương ứng — thà fail-fast lúc deploy còn hơn serve NaN im lặng. [Design synthesis]
- **Null policy** phải là một phần của định nghĩa feature, không phải của model code. `impressions_1h` null với user mới ≠ 0, và cách impute phải giống hệt nhau ở train và serve.

### 1.5 Ngân sách latency online store

[Design synthesis] — mục tiêu thiết kế cho decision path p99 < 50 ms server-side:

```text
gateway auth + parse                 ~2 ms
online store batch GET (1 round-trip) ~5 ms p99   ← gộp mọi feature vào 1 lần đọc
feature assembly + null impute       ~1 ms
model inference (GBDT / small NN)    ~3 ms p99
policy/business rules + guardrails   ~1 ms
serialize + response                 ~2 ms
------------------------------------------------
server-side p99 budget               ~15 ms  (headroom → 50 ms)
```

Điều kiện then chốt: **một round-trip duy nhất tới online store**. N+1 read (mỗi feature group một lần GET) là nguyên nhân số một khiến p99 nổ, vì p99 của N lần đọc độc lập tệ hơn nhiều so với p99 của một lần đọc. Gộp feature theo entity vào một row/hash key.

---

## 2. Streaming pipelines

### 2.1 Topology

```text
SDK ──batch HTTP──► ingest ──► Kafka topic: raw_events (partition by user_id_hash)
                                  │
                    ┌─────────────┼──────────────────────────┐
                    ▼             ▼                          ▼
            Flink: enrich    Flink: windowed agg      Sink: S3/Iceberg
            + dedupe         (session, 1h, 24h)       (raw, immutable)
                    │             │
                    ▼             ▼
            Kafka: clean_events   online feature store (KV upsert)
                    │
                    ▼
            Kafka: decision_log ──► OPE / experiment analysis
```

Partition key **phải là `user_id_hash`** (hoặc device id hash), không phải random. Lý do: mọi aggregation của monetization đều theo user (frequency cap, fatigue, session revenue), và partition-by-user cho phép Flink giữ state cục bộ, không cần shuffle mỗi window.

Kafka vs Kinesis: Kinesis rẻ hơn về vận hành ở quy mô nhỏ (managed, không phải lo ZooKeeper/KRaft, broker sizing) nhưng có giới hạn shard cứng (throughput per shard) và retention mặc định ngắn; Kafka (hoặc MSK/Confluent Cloud) linh hoạt hơn về retention dài — mà retention dài chính là thứ ta cần cho replay/backfill. [Primary docs]

### 2.2 Watermark và late events — phần quan trọng nhất với mobile

Đây là chỗ mà "mobile analytics" khác hẳn "web analytics". Một event impression có thể được sinh lúc 09:00, nằm trong durable queue của SDK vì máy offline, và chỉ tới server lúc 15:00 hôm sau. Nếu pipeline dùng processing time, mọi metric theo ngày sẽ sai và mọi experiment readout sẽ nhiễu.

Flink cung cấp trừu tượng chuẩn: `WatermarkGenerator`, với `BoundedOutOfOrdernessWatermarks(maxOutOfOrderness)` — tham số này là mức trễ tối đa mà element còn được tính vào window. Element đến sau khi watermark đã vượt qua timestamp của nó là *late element*. `allowedLateness` mặc định bằng 0 → element trễ bị drop (hoặc đẩy sang side output); khi `allowedLateness > 0`, Flink **tính lại window và cập nhật kết quả**, và chỉ những element trễ quá mức đó mới rơi vào side output. [Primary docs]

Thiết kế đề xuất cho SDK monetization [Design synthesis]:

| Đường tiêu thụ | maxOutOfOrderness | allowedLateness | Xử lý late |
|---|---|---|---|
| Online feature (fatigue, session revenue) | 5 s | 0 | drop → side output; feature online chấp nhận approximate |
| Metric dashboard near-RT | 60 s | 5 phút | update window |
| Revenue accounting / billing | — | dùng **batch reconciliation hằng ngày** | không dùng streaming làm nguồn sự thật |
| Experiment readout | — | batch D+1, cutoff theo event_time | chờ đủ, không đọc sớm |

Nguyên tắc: **streaming cho quyết định, batch cho sự thật.** Không bao giờ dùng số liệu streaming để trả tiền hay để kết luận experiment. Side output của late events phải được ghi ra lake và có dashboard riêng — tỉ lệ late event tăng đột biến là tín hiệu sớm nhất cho thấy có bản SDK mới đang queue sai hoặc một thị trường có vấn đề kết nối.

Ngoài ra, **client clock không đáng tin**. SDK phải gửi cả `device_event_time`, `device_uptime_ms` (monotonic) và server ghi `server_receive_time`; pipeline suy ra `corrected_event_time = server_receive_time - (device_send_time - device_event_time)`, tức dùng khoảng thời gian đo bằng monotonic clock chứ không dùng wall clock của thiết bị. Skew hàng giờ và cả timestamp năm 1970 hay 2038 là chuyện xảy ra thật trên fleet Android. [Design synthesis]

### 2.3 Exactly-once vs at-least-once cho monetization logging

Không có một câu trả lời chung; phải chia theo loại dữ liệu:

| Loại | Ngữ nghĩa cần | Cách đạt |
|---|---|---|
| Revenue / purchase / ad payout | **Effectively-once** | at-least-once transport + **idempotent dedupe theo `event_id` (UUID sinh trên client)** ở sink, cửa sổ dedupe ≥ retention của client queue |
| Decision log (cho OPE) | at-least-once + dedupe | như trên; trùng lặp làm lệch trọng số IPS nên phải dedupe trước khi tính |
| Impression/click cho feature | at-least-once, chấp nhận trùng | approximate là đủ |
| Debug/telemetry | at-most-once, sample | drop tự do |

Kafka có transactional producer + `read_committed` consumer cho exactly-once **trong phạm vi Kafka→Flink→Kafka**; Flink checkpoint two-phase-commit mở rộng nó tới sink hỗ trợ transaction. Nhưng chặng **client → ingest** thì *không thể* exactly-once về mặt lý thuyết: client không biết request timeout là do server chưa nhận hay đã nhận nhưng mất response, nên nó buộc phải retry. Vì vậy **`event_id` sinh phía client là bắt buộc**, và đây là quyết định thiết kế SDK, không phải quyết định của backend. [Primary docs] + [Design synthesis]

Cửa sổ dedupe phải ≥ thời gian tối đa mà SDK giữ event trong durable queue (đề xuất 7 ngày, mục 7) — dedupe 1 giờ là vô nghĩa khi client có thể gửi lại event 3 ngày sau.

### 2.4 Schema evolution

Dùng **Protobuf hoặc Avro với schema registry**, không dùng JSON tự do. Lý do cụ thể với SDK: một khi bản SDK v1.4 đã ở trên 40 triệu máy, ta **vĩnh viễn** phải đọc được schema của nó. Tương thích phải là hai chiều:

- **Backward compatible** (reader mới đọc được data cũ): bắt buộc, vì server luôn mới hơn client.
- **Forward compatible** (reader cũ đọc được data mới): cũng bắt buộc, vì SDK cũ phải parse được config/policy artifact mới mà không crash.

Quy tắc cứng: chỉ thêm field optional, không bao giờ tái sử dụng field number đã bỏ (Protobuf `reserved`), không đổi kiểu, không đổi ý nghĩa semantic của một field đang tồn tại (đổi đơn vị từ ms sang s là "compatible" về mặt schema nhưng phá hủy mọi metric — cần field mới). Registry phải chạy compatibility check trong CI, chặn merge. [Primary docs] + [Design synthesis]

Với payload từ client, cân nhắc thêm một lớp `schema_version` tường minh trong envelope để pipeline có thể route event của SDK cũ qua transformation riêng khi cần vá lỗi lịch sử.

---

## 3. Real-time inference serving

### 3.1 Ba nơi có thể chạy model

| | On-device (trong SDK) | Edge (PoP/CDN worker) | Server (region) |
|---|---|---|---|
| Latency tới quyết định | 0.1–5 ms | 10–40 ms RTT | 30–300 ms RTT tuỳ vùng |
| Hoạt động offline | có | không | không |
| Feature khả dụng | chỉ local | local + edge cache | toàn bộ online store |
| Cập nhật model | qua artifact (giờ–ngày) | phút | tức thì |
| Chi phí biến đổi | ~0 | thấp | cao nhất |
| Rủi ro | size, ANR, không rollback | ecosystem hẹp | phụ thuộc mạng |

Với monetization decision engine chạy trong Android SDK, **on-device phải là mặc định cho decision path**, server chỉ dùng cho: (a) huấn luyện, (b) cập nhật policy artifact, (c) các quyết định có giá trị đủ lớn để chịu được RTT (ví dụ giá gói IAP cá nhân hoá hiển thị trên một màn hình store mà user chủ động mở, khác với việc chọn có show interstitial ngay lúc chuyển màn hay không).

Lý do không phải chi phí mà là **UX và tỉ lệ thành công**: một quyết định "có show ad ở transition này không" phải trả lời trong khung thời gian của một chuyển cảnh (~100–300 ms). Ở thị trường US/EU trên mạng ổn định, gọi server p99 ~150 ms có thể chấp nhận; nhưng tail (mạng kém, DNS chậm, cold TLS) sẽ tạo ra một phần đáng kể request timeout, và mọi timeout đều phải fallback — nghĩa là ta vẫn cần một policy on-device đủ tốt. Đã cần nó rồi thì dùng luôn làm đường chính, và dùng server để *cải thiện* nó. [Design synthesis]

### 3.2 Runtime lựa chọn

- **On-device**: policy dạng lookup table/decision tree đã quantize là lựa chọn tốt nhất (vài chục KB, không cần runtime ML nào cả, không rủi ro ABI). Nếu bắt buộc phải có NN: **ONNX Runtime Mobile** hoặc TFLite — nhưng cần chú ý cả hai đều mang theo native `.so` cho nhiều ABI, làm size SDK tăng đáng kể (xem 7.4). Với bài toán monetization, gradient-boosted tree hoặc linear/logistic policy thường đủ và rẻ hơn nhiều bậc. [Design synthesis]
- **Server**: TF Serving và TorchServe phù hợp khi model đúng framework; **NVIDIA Triton** hợp lý khi cần phục vụ nhiều backend (ONNX + XGBoost + PyTorch) sau một endpoint và cần dynamic batching; **ONNX Runtime** server-side là mẫu số chung tốt để tránh khoá vào framework. Với GBDT (LightGBM/XGBoost) thì một service Go/Rust nhúng thư viện inference thường đánh bại mọi model server về latency và chi phí. [Primary docs] + [Design synthesis]

Dynamic batching là con dao hai lưỡi: nó tăng throughput nhưng thêm queueing delay vào p99. Với decision path, đặt `max_batch_delay` rất nhỏ (1–2 ms) hoặc tắt hẳn. [Design synthesis]

### 3.3 Caching

Ba tầng cache, mỗi tầng giải một vấn đề khác nhau [Design synthesis]:

1. **Cache quyết định trên client** (TTL rất ngắn, 30–120 s, key = `user × placement × context bucket`): loại bỏ phần lớn request lặp trong cùng session.
2. **Cache feature vector ở server** (TTL 1–5 s, key = user): gộp nhiều quyết định gần nhau của cùng user vào một lần đọc online store.
3. **Negative cache / circuit breaker**: khi backend lỗi, cache trạng thái "degraded" trong ~30 s để không bắn retry storm.

Cảnh báo: cache quyết định làm **nhiễu propensity logging**. Nếu một quyết định được cache và dùng lại 5 lần, ta có 5 impression nhưng chỉ 1 lần rút thăm. Phải log rõ `decision_id` và `served_from_cache=true` để OPE không đếm trùng propensity (mục 6.2).

### 3.4 Fallback chain và graceful degradation

Mọi tầng phải có tầng dưới, và tầng cuối cùng phải là hằng số không bao giờ lỗi:

```text
L0  contextual bandit on-device (artifact mới nhất)     ← đường chính
 └─ artifact lỗi/thiếu/hết hạn?
L1  policy artifact đóng gói sẵn trong SDK (bundled)     ← luôn tồn tại
 └─ vẫn lỗi (exception, OOM, corrupt)?
L2  heuristic tĩnh: frequency cap + placement rule       ← thuần code, không dữ liệu
 └─ heuristic ném exception?
L3  "không can thiệp": trả về hành vi mặc định của host app
```

Ràng buộc: **L3 phải nằm trong một `try/catch` bao trùm toàn bộ `decide()`**, và catch phải là `Throwable`, không phải `Exception` — một `NoClassDefFoundError` do dependency conflict trong app khách phải dẫn tới "SDK im lặng tự tắt", không phải crash app khách. Đây là khác biệt lớn giữa viết service và viết SDK: trong SDK, **crash của bạn là crash của khách hàng**, và nó tính vào Android vitals của họ (mục 7.5).

Bổ sung: một `kill switch` từ xa (mục 8.5) và một **local circuit breaker** — nếu `decide()` ném exception N lần liên tiếp, tự hạ xuống L2 cho toàn bộ vòng đời process và báo cáo qua telemetry.

---

## 4. Monitoring & drift

### 4.1 Bốn lớp giám sát

```text
Lớp 1  Hạ tầng   : QPS, error rate, p50/p99, saturation, Kafka lag, checkpoint fail
Lớp 2  Dữ liệu   : null rate, cardinality, range, freshness lag per feature (data drift)
Lớp 3  Mô hình   : phân phối prediction, calibration, coverage của mỗi arm (prediction drift)
Lớp 4  Kinh doanh: ARPDAU, eCPM, fill rate, retention D1/D7, session length (guardrail)
```

Thứ tự phát hiện sự cố trong thực tế thường ngược với thứ tự quan trọng: lớp 4 là thứ ta quan tâm nhưng lại phát hiện chậm nhất (nhiều giờ tới nhiều ngày, vì label trễ). Lớp 2 phát hiện nhanh nhất và phần lớn "model xuống cấp" thật ra là "một feature bị null 100% từ 3 giờ sáng". Đầu tư giám sát theo thứ tự 2 → 3 → 1 → 4. [Design synthesis]

### 4.2 Data drift vs concept drift

- **Data drift (covariate shift)**: P(X) đổi, quan hệ P(Y|X) không đổi. Ví dụ: một publisher lớn onboard, mix quốc gia đổi từ 60% US sang 30% US. Model vẫn đúng nhưng phân phối input lệch.
- **Concept drift**: P(Y|X) đổi. Ví dụ: mạng quảng cáo đổi thuật toán đấu giá, hoặc mùa Q4 làm eCPM và hành vi mua đổi cùng lúc. Đây mới là thứ khiến model *sai*.

Phát hiện data drift bằng thống kê so với reference window (thường là training window):

| Chỉ số | Dùng cho | Ngưỡng thực dụng |
|---|---|---|
| **PSI** (Population Stability Index) | feature liên tục đã bin, và điểm prediction | < 0.1 ổn định; 0.1–0.25 cần theo dõi; > 0.25 drift đáng kể |
| **KS test** | feature liên tục, 2 mẫu | nhạy quá ở n lớn → dùng effect size, không dùng p-value |
| **KL / JS divergence** | feature phân loại, cardinality vừa | JS bị chặn [0,1], dễ đặt ngưỡng hơn KL |
| **Chi-square** | feature phân loại | cùng cảnh báo n lớn như KS |

Các ngưỡng PSI trên là quy ước ngành phổ biến từ credit scoring, không phải hằng số vật lý — phải **hiệu chỉnh lại bằng chính dữ liệu của mình**: chạy PSI giữa hai ngày liền kề bình thường để biết baseline noise, rồi đặt ngưỡng cảnh báo ở phân vị cao của phân phối đó. [Design synthesis]

Cảnh báo lớn về n: ở 10M DAU, mọi test giả thuyết đều significant. **Luôn cảnh báo theo effect size, không bao giờ theo p-value.**

### 4.3 Giám sát khi label đến muộn

Đặc thù monetization: label quan trọng nhất (D7 revenue, D30 LTV, churn) đến sau nhiều ngày. Không thể chờ. Ba lớp proxy [Design synthesis]:

1. **Prediction drift (không cần label, tức thì)**: phân phối điểm dự đoán hôm nay so với reference. Nếu mean predicted pCTR nhảy từ 0.031 sang 0.058 trong 1 giờ mà mix traffic không đổi → gần như chắc chắn có bug feature, không phải người dùng đổi hành vi.
2. **Label ngắn hạn làm proxy** (giờ): click, impression completion, first-session revenue. Kiểm tra correlation giữa proxy và label dài hạn định kỳ; nếu correlation tự nó drift, proxy đã hết dùng được.
3. **Calibration theo thời gian** (ngày): với mỗi decile prediction, so predicted vs actual khi label chín. Vẽ reliability curve theo cohort ngày. Model mất calibration trước khi mất ranking power, và với monetization thì calibration mới là thứ quan trọng (vì quyết định đấu giá/hiển thị phụ thuộc giá trị tuyệt đối của expected revenue, không chỉ thứ hạng).

Phải xây **label maturity dashboard**: với mỗi ngày cohort, bao nhiêu % label đã chín. Nếu không có, chắc chắn sẽ có lúc ai đó so sánh performance của tuần này (label chưa chín) với tuần trước (đã chín) và kết luận sai.

### 4.4 Shadow, canary, rollback tự động

```text
Giai đoạn 1  SHADOW   : model mới chạy song song, KHÔNG ảnh hưởng quyết định.
                        So sánh: latency, error rate, phân phối prediction,
                        tỉ lệ agreement với model cũ. Thời lượng: 1–3 ngày.
Giai đoạn 2  CANARY   : 1% → 5% → 20% → 50% → 100%, mỗi bậc giữ ≥ 24h
                        (bắt buộc, để phủ trọn chu kỳ ngày/đêm).
Giai đoạn 3  GUARDRAIL: mỗi bậc kiểm tra guardrail metrics trước khi lên bậc.
Giai đoạn 4  ROLLBACK : tự động nếu chạm bất kỳ trigger nào bên dưới.
```

Shadow mode bắt được ~80% lỗi kỹ thuật (feature thiếu, kiểu sai, latency) nhưng **bắt được 0% lỗi về tác động** — vì shadow không thay đổi hành vi nên không sinh ra feedback loop. Canary bắt phần còn lại. [Design synthesis]

Trigger rollback tự động (đề xuất [Design synthesis], phải hiệu chỉnh theo baseline thật):

| Trigger | Ngưỡng | Cửa sổ | Hành động |
|---|---|---|---|
| SDK crash rate delta | > +0.05pp so với control | 30 phút | rollback tức thì |
| SDK ANR rate delta | > +0.02pp | 30 phút | rollback tức thì |
| p99 decision latency | > 2× baseline | 10 phút | rollback |
| Prediction PSI vs shadow baseline | > 0.25 | 1 giờ | freeze rollout, cảnh báo người |
| Guardrail revenue (ARPDAU) | CI trên < −2% | 24 giờ | rollback |
| Guardrail retention D1 | CI trên < −1% | 48 giờ | rollback |
| Arm coverage (bandit) | có arm nhận < 1% traffic dự kiến | 2 giờ | cảnh báo người |

Nguyên tắc: **trigger về sức khoẻ kỹ thuật (crash/ANR/latency) rollback tự động không cần người; trigger về kinh doanh thì freeze và gọi người.** Lý do: metric kinh doanh nhiễu, rollback tự động theo nó sẽ tạo dao động và làm hỏng tính hợp lệ của experiment.

---

## 5. Experimentation platform engineering

### 5.1 Bucketing và sticky assignment

Yêu cầu cứng: assignment phải **deterministic, stateless, và giống hệt nhau ở client lẫn server**.

```text
bucket = hash(experiment_salt + ":" + unit_id) mod 10000
variant = tra bucket trong bảng phân bổ của experiment
```

Chi tiết dễ sai [Design synthesis]:

- **Hash phải là MurmurHash3 hoặc SHA-1/SHA-256 truncate, không dùng `String.hashCode()` của Java** — hashCode phân phối tệ và không ổn định giữa các ngôn ngữ.
- **Salt riêng cho mỗi experiment**, nếu không hai experiment sẽ có bucket tương quan hoàn toàn và không thể chạy song song độc lập.
- **`unit_id` phải bền**: với Android, đây là điểm đau — GAID có thể reset/opt-out, và ID theo cài đặt sẽ mất khi user reinstall. Phải chọn rõ ràng và ghi vào tài liệu: `installation_id` sinh lần đầu và lưu bền. Việc reinstall làm user rơi vào bucket khác là một nguồn SRM thật.
- **Bucket không được đổi khi thay đổi phân bổ.** Dùng ranged assignment (control = bucket 0–4999) và khi ramp thì mở rộng khoảng, không xáo lại — nếu không, user đang ở treatment sẽ bị đẩy về control, sinh carryover effect.
- **Log assignment như một event riêng** (`exposure`), và chỉ log khi user **thực sự được phơi nhiễm** (đã đến điểm quyết định), không phải khi enroll. Đây là nền tảng của phân tích không thiên lệch.

### 5.2 SRM detection — cổng chất lượng bắt buộc

Sample Ratio Mismatch: tỉ lệ mẫu quan sát khác tỉ lệ thiết kế (ví dụ thiết kế 50/50, thực tế 50.4/49.6 trên 2 triệu user). Fabijan và cộng sự (KDD 2019) xây taxonomy cho SRM theo giai đoạn: giai đoạn **assignment** (bucketing sai, User ID lỗi, carry-over effect), giai đoạn **execution**, giai đoạn **log processing**, và giai đoạn **analysis**. [Primary research]

Với SDK, các nguồn SRM đặc thù rất dễ gặp [Design synthesis]:

- Treatment làm app crash/ANR nhiều hơn → user ở treatment gửi ít event hơn → mất mẫu **không ngẫu nhiên**. Đây là SRM nguy hiểm nhất vì nó vừa là bug vừa che giấu chính bug đó.
- Treatment tốn pin/dữ liệu hơn → event bị drop khi queue đầy.
- Treatment có payload lớn hơn → tỉ lệ upload thất bại cao hơn ở mạng kém → mất mẫu lệch theo thị trường.

Chạy chi-square test trên tỉ lệ mẫu **mỗi ngày, cho mọi experiment**, và **chặn readout** nếu p < 0.001. SRM không phải cảnh báo mềm; nó có nghĩa là kết quả không dùng được. Ngoài tổng thể, phải chạy SRM cắt theo chiều: country, OS version, device tier, SDK version, network type — SRM cục bộ ở một chiều thường lộ ra nguyên nhân ngay.

### 5.3 CUPED và giảm phương sai

CUPED (Deng, Xu, Kohavi, Walker, WSDM 2013) dùng dữ liệu **tiền thí nghiệm** làm covariate để giảm phương sai của estimator; kết quả trên hệ thống thí nghiệm của Bing cho thấy giảm phương sai khoảng 50%, tức đạt cùng power với ít mẫu/ít thời gian hơn đáng kể. [Primary research]

```text
Y_cuped = Y − θ (X − E[X]),   θ = Cov(Y, X) / Var(X)
X = cùng metric đó đo trong giai đoạn pre-experiment
```

Với monetization, CUPED đặc biệt mạnh vì revenue per user cực kỳ heavy-tailed và tự tương quan cao theo thời gian: người chi tiêu tuần trước rất có khả năng chi tiêu tuần này. Ràng buộc engineering: cần **lưu trữ metric tiền thí nghiệm cho mọi unit**, tức bảng `user × metric × pre_period` phải sẵn sàng trước khi experiment bắt đầu — đây là yêu cầu hạ tầng, không phải yêu cầu phân tích, và phải được thiết kế từ đầu. User mới (không có pre-period) phải được xử lý tường minh: X = 0 và thêm indicator `is_new_user`, hoặc phân tích tách stratum.

Kèm theo: **winsorize/cap** metric revenue ở phân vị cao (99.9%) trước khi phân tích. Một whale duy nhất có thể lật ngược kết luận của một experiment 5 triệu user. Ghi rõ ngưỡng cap trong pre-registration để không bị nghi ngờ tuỳ tiện.

### 5.4 Sequential testing / always-valid p-values

Vấn đề "peeking": p-value cổ điển chỉ hợp lệ ở cỡ mẫu cố định định trước; theo dõi liên tục và dừng khi thấy p < 0.05 làm phồng false positive rate rất mạnh. Johari, Pekelis, Walsh và Koomen (KDD 2017) đưa ra **always-valid p-values** dựa trên họ **mixture Sequential Probability Ratio Test (mSPRT)**, an toàn cho việc theo dõi liên tục; phương pháp này đã được Optimizely triển khai thương mại. [Primary research]

Với một team nhỏ, đây là hạ tầng đáng đầu tư sớm hơn người ta nghĩ, vì thực tế là **sẽ luôn có người mở dashboard mỗi ngày**. Có hai đường: (a) always-valid p-value/confidence sequence hiển thị trực tiếp trên dashboard; hoặc (b) khoá dashboard, chỉ mở readout ở ngày đã định trước. Đường (b) rẻ hơn về kỹ thuật nhưng gần như luôn thất bại về mặt tổ chức. Chi phí của (a): CI rộng hơn ở giai đoạn đầu — đó chính là cái giá đúng đắn của quyền được nhìn bất cứ lúc nào.

Ngoại lệ quan trọng: sequential test **không** cứu được các trigger rollback ở mục 4.4 — những cái đó là giám sát vận hành, chấp nhận false positive cao vì chi phí rollback thấp.

### 5.5 Switchback và interference

Randomize theo user thất bại khi treatment ảnh hưởng tới **nguồn lực dùng chung**. DoorDash dùng switchback testing chính vì marketplace ba bên: các đơn hàng treatment và control xảy ra cùng lúc trong cùng khu vực không độc lập vì cùng phụ thuộc vào lượng tài xế sẵn có; họ randomize theo đơn vị **time × region** thay vì theo user. [Primary engineering blog]

Trong monetization, interference xuất hiện ở đâu?

- **Đấu giá quảng cáo**: nếu treatment làm tăng lượng ad request, nó có thể làm loãng fill rate/eCPM cho *cả* control trong cùng ad network. Kết quả A/B đo được ở đây là hiệu ứng đã bị làm nhiễu.
- **Ngân sách chiến dịch nhà quảng cáo**: hai nhóm cạnh tranh cùng một túi tiền hữu hạn.
- **Hiệu ứng xã hội trong game**: leaderboard, guild, kinh tế trong game (nếu treatment cho một nhóm nhiều tài nguyên hơn, giá thị trường trong game đổi cho tất cả).

Khi nghi ngờ interference: chuyển sang **switchback theo (thời gian × quốc gia/ad network)**, hoặc **cluster randomization** theo app/publisher. Chi phí phải trả là power thấp hơn nhiều (đơn vị thực tế là số time-region, không phải số user) và cần **cluster-robust standard error** — dùng standard error thông thường trên dữ liệu switchback sẽ cho false positive. [Primary engineering blog]

Ngoài ra, switchback đòi hỏi tính đúng **carryover/burn-in**: sau mỗi lần chuyển trạng thái, bỏ một khoảng đầu window ra khỏi phân tích vì hệ thống chưa vào trạng thái ổn định (fatigue counter, cache, học của ad network).

### 5.6 Multiple testing và guardrail

Mỗi experiment thường soi 20–100 metric × nhiều segment. Ở α = 0.05, hàng chục "phát hiện" giả là điều chắc chắn.

- **Metric chính**: 1 (một). Định trước. Không đổi sau khi nhìn dữ liệu.
- **Metric phụ**: kiểm soát **FDR bằng Benjamini–Hochberg**, không dùng Bonferroni (quá bảo thủ khi metric tương quan).
- **Guardrail**: kiểm định **một phía** với ngưỡng non-inferiority định trước (ví dụ "retention D1 không giảm quá 0.5%"), không phải kiểm định hai phía tìm ý nghĩa.
- **Phân tích theo segment**: coi là *sinh giả thuyết*, không phải *kết luận*. Muốn kết luận thì chạy experiment xác nhận nhắm đúng segment đó.

Bộ guardrail tối thiểu cho một monetization SDK [Design synthesis]: retention D1/D7, session length, session count/DAU, crash-free user rate, ANR-free user rate, cold start time, app size delta, uninstall rate, tỉ lệ user tắt quảng cáo/mua remove-ads, và tỉ lệ khiếu nại/rating 1 sao nếu lấy được. Điểm mấu chốt: **kiếm thêm tiền hôm nay bằng cách đốt retention là thua lỗ**, và chỉ guardrail mới bắt được điều đó — metric chính (ARPDAU) sẽ luôn nói "thắng".

---

## 6. Decision logging ở quy mô lớn

### 6.1 Schema cho off-policy evaluation

Đây là dữ liệu quý nhất trong toàn hệ thống. Nếu ghi thiếu một trường, mọi OPE về sau đều vô hiệu và không thể vá hồi tố.

```text
decision_event {
  // định danh
  decision_id        : uuid       // sinh trên client, khoá dedupe
  request_id         : uuid       // gộp nhiều decision cùng request
  unit_id            : string     // installation_id (đơn vị randomize)
  session_id         : string
  ts_event           : int64      // monotonic-corrected event time
  ts_server          : int64

  // ngữ cảnh (đúng những gì policy đã thấy)
  context_hash       : string     // hash của feature vector, phát hiện skew
  features           : map/blob   // feature vector ĐÃ DÙNG, không tra lại sau
  feature_versions   : map        // name -> version
  candidate_set      : array      // TẤT CẢ hành động khả dụng lúc đó
  eligibility_mask   : array      // vì sao một số bị loại

  // quyết định
  action             : string     // hành động đã chọn
  propensity         : float      // P(action | context) THEO POLICY LOGGING
  policy_id          : string     // hash của artifact
  policy_version     : string
  exploration_flag   : bool       // explore hay exploit
  served_from_cache  : bool
  fallback_level     : enum       // L0/L1/L2/L3 (mục 3.4)
  decide_latency_us  : int

  // thí nghiệm
  experiment_ids     : array<(exp_id, variant)>

  // môi trường
  sdk_version, app_id, os_version, device_tier, country, network_type, locale
}

outcome_event {
  decision_id, ts_event, outcome_type, value, currency, is_estimated
}
```

Hai điểm sống còn:

- **`features` phải là snapshot đã dùng, không phải tham chiếu.** Nếu chỉ lưu `unit_id` rồi tra lại feature store lúc train, ta đã tự tạo lại đúng bài toán point-in-time ở mục 1.2, và với feature streaming thì gần như không thể khôi phục chính xác.
- **`candidate_set` phải đầy đủ.** Không có nó thì mọi OPE (IPS, doubly robust) không có mẫu số, vì không biết policy mới lẽ ra có thể chọn gì.

`context_hash` nên được tính bằng cùng một hàm ở client và ở pipeline train; so khớp hash giữa hai bên là cách rẻ nhất để phát hiện training-serving skew một cách tự động, liên tục.

### 6.2 Những cái bẫy của propensity logging

1. **Log propensity đã làm tròn**: lưu float32 hoặc làm tròn 2 chữ số. Propensity nhỏ nằm ở mẫu số của IPS, nên `0.004` bị làm tròn thành `0.00` là chia cho 0. **Luôn dùng float64 và clip sàn** (ví dụ ε = 1e-4) ngay lúc ghi, đồng thời ghi cả cờ `propensity_clipped`.
2. **Deterministic policy log propensity = 1.0**: về mặt kỹ thuật đúng, nhưng khiến OPE vô dụng (không có overlap). Cần một **epsilon exploration floor** thường trực (2–5%) như một khoản đầu tư hạ tầng, không phải như tính năng thuật toán.
3. **Không log propensity của quyết định đến từ fallback**: quyết định do L2 heuristic sinh ra có propensity khác hoàn toàn L0. Nếu trộn chung, ước lượng lệch. → dùng `fallback_level` để lọc hoặc mô hình hoá.
4. **Cache làm hỏng propensity** (mục 3.3): 5 impression từ 1 lần rút thăm không phải 5 mẫu độc lập.
5. **Log propensity sau khi áp business rule**: nếu policy chọn action A với p=0.7 nhưng rule "không show 2 interstitial trong 60s" chặn lại, thì propensity thật của kết quả cuối không phải 0.7. Phải log propensity **của phân phối cuối cùng sau khi mask**, và log cả mask.
6. **Đổi định nghĩa policy mà không đổi `policy_id`**: khiến dữ liệu của hai policy bị trộn. `policy_id` phải là **hash nội dung của artifact**, không phải một chuỗi do người đặt.

### 6.3 Sampling và chi phí lưu trữ

Không phải mọi decision cần log đủ. Chiến lược đề xuất [Design synthesis]:

| Loại traffic | Tỉ lệ log full-fidelity | Ghi chú |
|---|---|---|
| Explore traffic | **100%** | quý nhất cho OPE, tuyệt đối không sample |
| Exploit traffic | 1–10%, sampling **deterministic theo hash(decision_id)** | cho phép tái lập chính xác |
| Traffic trong experiment | 100% cho unit thuộc experiment | cần đủ power |
| Fallback L2/L3 | 100% (khối lượng nhỏ, giá trị chẩn đoán cao) | |
| Outcome events | **100% luôn luôn** | nhỏ, và mất là mất vĩnh viễn |

Bắt buộc **log tỉ lệ sampling vào chính event** (`sample_rate`), nếu không mọi tổng hợp về sau đều sai hệ số. Sampling phải deterministic (theo hash) chứ không random, để một `decision_id` đã được chọn thì mọi bản sao/retry của nó cũng được chọn — nếu dùng random, dedupe và sampling sẽ đánh nhau.

Chi phí lưu trữ giảm theo thứ tự: (1) chuyển sang **columnar Parquet/Iceberg + zstd** — feature vector nén rất tốt vì lặp lại nhiều; (2) tách `features` blob ra bảng riêng, chỉ join khi train; (3) tiering S3 Standard → IA → Glacier theo tuổi; (4) partition theo `dt/country/sdk_version` để query experiment không quét toàn bộ.

### 6.4 PII minimization và retention

Quy tắc thiết kế [Design synthesis], có tính đến GDPR/ePrivacy (EU) và các luật bang của Mỹ, vì thị trường mục tiêu là US/EU:

- **Không bao giờ log**: raw advertising ID trong bảng phân tích dài hạn, email, số điện thoại, toạ độ GPS chính xác, IP đầy đủ.
- **Pseudonymize ở edge**: `unit_id_hashed = HMAC(secret, installation_id)`, xoay khoá theo chu kỳ. Bảng ánh xạ (nếu cần cho xoá dữ liệu) lưu tách biệt, quyền truy cập riêng, retention ngắn.
- **Giảm độ chính xác ngay tại nguồn**: country/region thay vì city; IP truncate; timestamp làm tròn tới giây; device model gom thành `device_tier`.
- **Consent phải là một trường trong event** (`consent_state`, `gdpr_applies`, TCF string version nếu có), và pipeline phải **enforce ở tầng ingest** — không phải ở tầng dashboard. Event không có consent hợp lệ cho mục đích analytics/personalization phải bị route sang đường xử lý hạn chế hoặc drop, tại edge, trước khi vào lake.
- **Retention theo tầng**: raw event 30–90 ngày → decision log đã pseudonymize 12–18 tháng (cần cho model LTV dài hạn) → aggregate không định danh giữ vô hạn. Retention phải được **thực thi bằng lifecycle policy tự động**, không phải bằng quy trình thủ công.
- **Deletion request** phải xoá được theo `unit_id` trên cả lake — đây là lý do nên dùng **Apache Iceberg/Delta** (hỗ trợ row-level delete) thay vì Parquet trần trên S3, và là quyết định phải ra từ ngày đầu vì migrate sau rất tốn.

---

## 7. Ràng buộc kỹ thuật của Mobile SDK — **mục quan trọng nhất**

SDK không phải là một app thu nhỏ. Nó là **khách trọ trong process của người khác**, và mọi tài nguyên nó tiêu là tài nguyên lấy đi của host app. Đây là nơi mà tài liệu này khác biệt hoàn toàn so với một tài liệu MLOps thông thường.

### 7.1 Ngân sách pin và mạng

Nguyên tắc: **không bao giờ tự khởi tạo hoạt động mạng vì lý do của riêng SDK.** Piggyback lên hoạt động mà user đã tạo ra.

[Design synthesis] — ngân sách đề xuất cho mỗi DAU:

| Hạng mục | Ngân sách/user/ngày | Cơ chế |
|---|---|---|
| Số lần gọi mạng | ≤ 3 | 1 lần lúc app foreground đầu tiên (config + policy), 1–2 lần flush event |
| Payload upload | ≤ 20 KB nén | batch + gzip/zstd |
| Payload download | ≤ 50 KB | policy artifact + config, có ETag/delta |
| Wakelock | 0 | không bao giờ giữ wakelock |
| Background job | ≤ 1/ngày | WorkManager với constraint |
| CPU trên main thread | ≤ 5 ms mỗi decision | phần còn lại đẩy sang background |

Chi tiết Android quan trọng:

- Dùng **WorkManager** cho flush cơ hội, với `Constraints`: `setRequiredNetworkType(CONNECTED)` (cân nhắc `UNMETERED` cho payload lớn), `setRequiresBatteryNotLow(true)`, và backoff. WorkManager tôn trọng Doze/App Standby buckets — đây là ưu điểm, không phải trở ngại: nếu SDK cố lách Doze, nó sẽ bị OEM (Xiaomi, Samsung, Huawei) giết process và đồng thời làm hại chỉ số pin của host app.
- **Không dùng `AlarmManager.setExactAndAllowWhileIdle`** cho việc gửi analytics. Không có gì trong monetization telemetry đáng để đánh thức thiết bị.
- Flush theo **cả ba trigger**: (a) hàng đợi đạt N event, (b) đạt T giây kể từ lần flush trước *khi app đang foreground*, (c) `onStop()` của activity cuối / lifecycle `ON_STOP` — trigger (c) là quan trọng nhất vì nó bắt được thời điểm cuối cùng process còn sống.
- Tôn trọng metered network: khi `ConnectivityManager` báo metered + `isActiveNetworkMetered`, nâng ngưỡng batch và hoãn tải artifact lớn.

### 7.2 Batching, nén và định dạng

- **Batch**: gộp event thành mảng, gửi một request. Trigger: 50 event, hoặc 30 giây foreground, hoặc lúc app vào background, tuỳ cái nào đến trước. [Design synthesis]
- **Nén**: gzip là mẫu số chung an toàn (mọi server đều hỗ trợ, `java.util.zip` có sẵn, 0 KB thêm vào SDK). **zstd** nén tốt hơn và nhanh hơn nhưng cần native lib → tăng size SDK; chỉ chọn nếu SDK đã có native code sẵn vì lý do khác. Với JSON telemetry lặp lại nhiều, gzip thường giảm 70–85%. [Design synthesis]
- **Định dạng**: Protobuf giảm thêm kích thước và loại bỏ chi phí parse, nhưng runtime protobuf-javalite thêm vài trăm KB. Với phần lớn SDK, **JSON + gzip là điểm cân bằng đúng**; chỉ chuyển sang Protobuf khi khối lượng event thực sự lớn. Nếu host app đã dùng Protobuf, chia sẻ dependency.
- **Giới hạn cứng**: payload tối đa mỗi request (ví dụ 1 MB sau nén) — vượt thì chia; event đơn lẻ quá lớn thì drop và đếm vào metric, không bao giờ để một event hỏng chặn cả hàng đợi (head-of-line blocking là chế độ hỏng phổ biến và rất khó chẩn đoán từ xa).

### 7.3 Durable offline queue

Đây là thành phần kỹ thuật khó nhất phía client. Yêu cầu: event không mất khi app bị kill, khi máy offline nhiều ngày, khi app crash giữa lúc ghi.

```text
API layer     enqueue(event)  ← non-blocking, gọi từ bất kỳ thread nào
   │
   ▼
in-memory ring buffer (bounded, ~200 events)
   │  flush định kỳ / khi đầy, trên single background thread
   ▼
durable store: SQLite (WAL) hoặc append-only file
   │  ĐỌC theo batch, đánh dấu in-flight, XÓA sau khi server ACK 2xx
   ▼
uploader (OkHttp) → retry với exponential backoff + jitter
```

Quyết định thiết kế [Design synthesis]:

- **SQLite với WAL** là lựa chọn thực dụng: đã có sẵn trong Android (0 KB thêm), transaction thật, dễ query/xoá theo id. Append-only file + index nhanh hơn nhưng phải tự xử lý corruption (rất thật: máy hết pin giữa lúc ghi, filesystem đầy). Nếu chọn file, phải có checksum mỗi record và khả năng bỏ qua record hỏng để đọc tiếp.
- **Bounded, luôn luôn**: giới hạn theo cả số event (ví dụ 10.000) *và* dung lượng đĩa (ví dụ 5 MB) *và* tuổi (7 ngày). Chính sách khi đầy: **drop oldest low-priority trước**, giữ lại event revenue/outcome. Đếm số event bị drop và gửi con số đó lên như một metric — "silent data loss" là kẻ thù của mọi phân tích.
- **Ngữ nghĩa at-least-once**: chỉ xoá khỏi store sau khi có 2xx. Chấp nhận trùng, dedupe ở server bằng `event_id` (mục 2.3).
- **Một background thread duy nhất** (hoặc một `Executor` single-thread) sở hữu store. Truy cập SQLite từ nhiều thread trong SDK là nguồn của `SQLiteDatabaseLockedException` và ANR.
- **Không bao giờ chạm đĩa trên main thread**, kể cả `open()` database. Khởi tạo store phải lazy và ở background; `enqueue()` từ main thread chỉ được ghi vào ring buffer in-memory.
- **Migration**: schema của store cục bộ cũng cần version; SDK mới phải đọc được store do SDK cũ tạo, hoặc drop có kiểm soát (và đếm).

### 7.4 Ngân sách kích thước SDK

Kích thước không phải chuyện thẩm mỹ — nó có tác động đo được lên kinh doanh của khách hàng. Phân tích của Google Play cho thấy **cứ tăng 6 MB kích thước APK thì tỉ lệ chuyển đổi cài đặt giảm khoảng 1%**, và ở thị trường mới nổi, giảm 10 MB tương quan với tăng khoảng 2.5% install conversion. [Primary engineering blog]

Điều đó biến size thành một con số có thể đàm phán trong hợp đồng: một SDK 8 MB đang lấy đi khoảng 1.3% install conversion của khách hàng — họ sẽ tính ra con số đó.

Ngân sách đề xuất [Design synthesis]:

| Thành phần | Ngân sách |
|---|---|
| Core SDK (DEX, sau R8) | ≤ 250 KB |
| Policy artifact bundled (fallback L1) | ≤ 50 KB |
| Resources (không có drawable lớn) | ≤ 20 KB |
| Transitive dependencies mới thêm | 0 nếu có thể |
| **Tổng download size delta** | **≤ 400 KB** |
| Nếu buộc phải nhúng ML runtime | +2–4 MB/ABI → cần biện minh rất mạnh |

Kỹ thuật cắt size: bật **R8/minify với `consumerProguardFiles`** đóng gói trong AAR (để app khách tự động shrink phần không dùng); tránh Guava/Gson/Protobuf-full; dùng `kotlinx.serialization` hoặc parser thủ công thay vì reflection-based; không nhúng drawable; nếu có native, tách theo ABI qua App Bundle; **theo dõi size delta trong CI** và fail build nếu vượt ngân sách. Cuối cùng: đo bằng số thật — dùng APK Analyzer trên một app mẫu trước/sau khi thêm SDK, và công bố con số đó trong tài liệu tích hợp. Đó là một lợi thế bán hàng.

### 7.5 Cold start, main thread, ANR và crash

Đây là rủi ro tồn vong. Ngưỡng "bad behavior" của Google Play Console: **user-perceived crash rate 1.09%** và **user-perceived ANR rate 0.47%** trên toàn bộ device model; vượt ngưỡng thì app có khả năng bị giảm khả năng khám phá trên Google Play và trong một số trường hợp bị hiển thị cảnh báo trên trang store listing. Ngoài ra còn ngưỡng theo từng model điện thoại là 8% cho cả hai chỉ số. [Primary docs]

Con số 0.47% cực kỳ chặt. Nếu SDK gây ANR cho 0.1% DAU, nó đã tiêu tốn hơn 20% toàn bộ ngân sách ANR của khách hàng — và họ sẽ gỡ SDK.

Quy tắc cứng [Design synthesis]:

1. **`init()` phải trả về trong < 5 ms trên main thread.** Mọi thứ khác (đọc file, mở DB, network, parse artifact) phải là lazy/async. Cụ thể: `init()` chỉ lưu context, tạo executor, và post một task khởi tạo.
2. **Không dùng `ContentProvider` tự động khởi tạo để làm việc nặng.** Nếu dùng App Startup, phải khai báo là dependency nhẹ. `ContentProvider.onCreate()` chạy **trước `Application.onCreate()`** và thẳng vào critical path của cold start; đây là con đường phổ biến nhất khiến một SDK làm chậm khởi động app.
3. **Không I/O, không `SharedPreferences.commit()`, không JSON parse trên main thread.** `apply()` cũng ghi đĩa async nhưng fsync của nó có thể block ở `onPause` — dùng DataStore hoặc file riêng ở background.
4. **Không lock nào giữ được bởi main thread mà background có thể giữ lâu.** Đây là nguyên nhân ANR khó chẩn đoán nhất: `decide()` trên main thread chờ một lock mà background thread đang giữ trong lúc gọi SQLite.
5. **`decide()` phải có ngân sách thời gian cứng.** Nếu chạy quá X ms (ví dụ 10 ms), trả fallback ngay. Không bao giờ chờ mạng trên main thread, không có ngoại lệ.
6. **Bọc toàn bộ mọi public API trong `try/catch(Throwable)`.** SDK crash = app khách crash = mất khách hàng. Nuốt lỗi, đếm, báo cáo qua telemetry, tự hạ cấp.
7. **Đo cold start delta trong CI**: benchmark app mẫu có/không SDK bằng Macrobenchmark, đặt ngân sách (ví dụ ≤ 10 ms delta P50) và fail build khi vượt.
8. **Tự giám sát ANR**: dùng `ApplicationExitInfo` (API 30+) để đọc `REASON_ANR`/`REASON_CRASH` ở lần khởi động sau, và đối chiếu xem sự kiện đó có xảy ra trong lúc SDK đang làm việc gì không.

### 7.6 SDK versioning và rollback khi không force-update được

Đây là ràng buộc định hình toàn bộ kiến trúc. **Bản SDK đã phát hành là vĩnh viễn.** Không có rollback. Bản v1.2.0 có bug sẽ còn chạy trên hàng triệu máy trong 12–24 tháng, vì bản cập nhật chỉ đến khi *nhà phát triển app khách* nâng phiên bản, build lại, và *người dùng cuối* cập nhật app.

Hệ quả thiết kế:

1. **Mọi hành vi có thể thay đổi phải là dữ liệu, không phải mã.** Nếu một tham số có khả năng cần chỉnh, nó phải đến từ remote config. Nếu một thuật toán có khả năng cần đổi, nó phải là policy artifact tải về. Bytecode chỉ nên chứa *bộ máy diễn giải*, không chứa *chính sách*.
2. **Kill switch từ xa cho mọi tính năng**, kiểm tra ngay khi khởi động, có giá trị mặc định an toàn khi không gọi được server (fail-safe = tắt tính năng mới, giữ hành vi cũ).
3. **Server phải chịu trách nhiệm về tương thích ngược vĩnh viễn.** Endpoint version hoá; không bao giờ xoá field mà SDK cũ cần; hợp đồng schema là forward-compatible (mục 2.4).
4. **Ma trận rollout theo `sdk_version`**: mọi remote config và policy artifact phải có điều kiện theo phiên bản SDK. Khi phát hiện v1.2.0 có bug với artifact định dạng mới, ta tắt định dạng mới **chỉ cho v1.2.0** trong vài phút, không cần đụng tới ai khác.
5. **Semantic versioning nghiêm ngặt + cửa sổ hỗ trợ công bố**: hỗ trợ N-2 minor, thông báo deprecation ít nhất 6 tháng. Với public API, dùng `@RestrictTo`/`internal` tối đa để giảm bề mặt phải giữ mãi.
6. **Kênh phát hành phân tầng**: `alpha` (nội bộ) → `beta` (khách hàng tình nguyện, thường 1–3 app) → `stable`. Không có cách nào khác để bắt lỗi tương tác với các SDK khác (dependency conflict với OkHttp/Kotlin stdlib version là chế độ hỏng số một trong thực tế) ngoài việc chạy trên app thật.
7. **Shade/relocate dependency** khi hợp lý: nếu SDK dùng OkHttp và app khách dùng phiên bản khác, xung đột sẽ nổ ở runtime. Relocate làm tăng size nhưng loại bỏ cả một lớp sự cố không thể debug từ xa.

Bảng so sánh sức mạnh điều khiển từ xa:

| Đòn bẩy | Thời gian tác dụng | Rủi ro | Dùng cho |
|---|---|---|---|
| Remote config flag | phút – giờ | thấp | bật/tắt, tham số, kill switch |
| Policy artifact | giờ | trung bình | đổi chính sách quyết định |
| Server-side decision | tức thì | cao (RTT, phụ thuộc mạng) | quyết định giá trị cao |
| SDK release mới | **3–12 tháng** | rất cao (không rollback) | chỉ đổi bộ máy, sửa lỗi cấu trúc |

---

## 8. Phân phối policy artifact

### 8.1 Artifact là gì

Artifact = biểu diễn có thể serialize của policy mà SDK thực thi. Với monetization decision engine, ba dạng thực dụng theo thứ tự ưu tiên:

1. **Lookup table đã lượng tử hoá**: `(context_bucket) → (action, score)`. Vài KB tới vài chục KB. Không cần runtime ML. Nhanh nhất, an toàn nhất, dễ kiểm tra nhất bằng mắt.
2. **Decision tree / GBDT nhỏ** ở định dạng riêng: vài chục KB tới vài trăm KB, evaluator thuần Kotlin ~200 dòng.
3. **Model NN** (TFLite/ONNX): chỉ khi thực sự cần, và phải trả giá về size + runtime.

Quyết định kiến trúc quan trọng: **giữ artifact ở dạng 1 hoặc 2 càng lâu càng tốt.** Phần lớn giá trị của một monetization policy nằm ở việc *có* cá nhân hoá theo vài chiều đúng, không nằm ở độ tinh vi của model. [Design synthesis]

### 8.2 Kênh phân phối

```text
Training (offline) → artifact build → sign → CDN (immutable, content-addressed)
                                              │
                     Remote Config: pointer  ─┘
                     { policy_url, sha256, version, min_sdk, rollout_pct, ttl }
                                              │
                                     SDK: fetch config → verify → fetch artifact
                                          → verify sha256 + signature → atomic swap
```

**Mẫu đúng: config chứa con trỏ, CDN chứa nội dung.** Không nhét artifact vào remote config. Lý do: (a) remote config có giới hạn kích thước và không tối ưu cho binary; (b) CDN cho phép content-addressed URL (bất biến, cache vĩnh viễn, không invalidation); (c) tách con trỏ khỏi nội dung cho phép rollout theo phần trăm và rollback tức thì bằng cách chỉ đổi con trỏ.

Về giới hạn của Firebase Remote Config: tài liệu chính thức quy định giới hạn số lượng parameter cho mỗi loại template (mức hiện hành theo tài liệu là 3000 parameter/template type) và độ dài khoá parameter tối đa 256 ký tự; các giới hạn cụ thể về tổng kích thước template cần tra trực tiếp trang "Limits on parameters and conditions" vì chúng thay đổi theo thời gian. [Primary docs] — xem mục "Không xác minh được".

Ba lựa chọn kênh:

| Kênh | Ưu | Nhược |
|---|---|---|
| Firebase Remote Config | miễn phí/rẻ, đã có A/B & điều kiện theo app version/country, SDK sẵn | thêm dependency Firebase vào app khách (có thể là deal-breaker), giới hạn kích thước |
| CDN tự quản (CloudFront/Fastly/R2) + config endpoint riêng | không phụ thuộc bên thứ ba, kiểm soát hoàn toàn, rẻ ở quy mô lớn | phải tự xây rollout/điều kiện/kill switch |
| Cả hai (config qua endpoint riêng, artifact qua CDN) | **khuyến nghị** | phải tự xây phần config |

Với một SDK bán cho bên thứ ba, ép khách hàng nhúng Firebase là ma sát tích hợp đáng kể. Endpoint config riêng + CDN thường là lựa chọn đúng.

### 8.3 Kích thước, delta và cache

[Design synthesis]:

- **Ngân sách artifact: ≤ 200 KB nén** cho tải lần đầu; ≤ 50 KB cho cập nhật thường kỳ.
- **HTTP caching đúng cách**: URL content-addressed (`/policy/<sha256>.bin`) + `Cache-Control: immutable, max-age=31536000`. Config endpoint dùng `ETag` + `If-None-Match` → phần lớn lần kiểm tra trả 304 với vài trăm byte.
- **Delta update**: chỉ đáng làm khi artifact > vài trăm KB. Với lookup table, "delta" đơn giản là gửi các entry đã đổi (`patch` dạng list), rẻ hơn nhiều so với bsdiff nhị phân và dễ kiểm chứng.
- **Tần suất kiểm tra**: 1 lần/ngày là đủ cho artifact; config có thể 1 lần mỗi phiên foreground với ETag. Cộng thêm jitter ngẫu nhiên (± vài giờ) để tránh thundering herd đúng 00:00 UTC.
- **Tải ở background, áp dụng ở lần khởi động sau**: không bao giờ hoán đổi policy giữa phiên (làm hỏng tính nhất quán của experiment trong phiên) trừ khi là kill switch.

### 8.4 Chữ ký và toàn vẹn

Artifact điều khiển hành vi kiếm tiền trong app của người khác. Nếu bị thay thế, hậu quả là vấn đề an ninh của khách hàng.

Yêu cầu tối thiểu [Design synthesis]:

1. **HTTPS + certificate pinning** — nhưng pin phải kèm cơ chế backup pin và thời hạn, vì pin sai/hết hạn trên một SDK không rollback được là sự cố không cứu được. Cân nhắc pin theo CA trung gian thay vì leaf.
2. **SHA-256 của artifact nằm trong config**, kiểm tra sau khi tải, trước khi parse.
3. **Chữ ký bất đối xứng (Ed25519)** trên `(sha256 || version || expiry)`, public key nhúng trong SDK. Điều này bảo vệ ngay cả khi CDN hoặc config endpoint bị xâm nhập.
4. **Trường `expiry` trong artifact**: SDK từ chối artifact quá hạn và rơi về bundled policy. Chống replay artifact cũ.
5. **`min_sdk_version` / `max_sdk_version`** trong artifact: SDK từ chối artifact không dành cho nó.
6. **Parse phòng thủ**: giả định artifact là dữ liệu thù địch. Giới hạn kích thước sau giải nén (chống zip bomb), giới hạn độ sâu, giới hạn số entry, timeout parse. Toàn bộ parse trong `try/catch(Throwable)` với fallback về artifact hiện hành.
7. **Atomic swap**: ghi ra file tạm, verify, rồi `rename()`. Không bao giờ để tồn tại trạng thái artifact ghi dở.

### 8.5 Staged rollout và kill switch

```text
Bậc rollout (theo hash(installation_id) mod 100, KHÔNG random):
  1%  →  giữ 6h   →  kiểm crash/ANR/latency/prediction drift
  5%  →  giữ 12h
 20%  →  giữ 24h  →  bắt đầu đọc guardrail kinh doanh
 50%  →  giữ 24h
100%
Bất kỳ bậc nào chạm trigger (mục 4.4) → đổi con trỏ config về artifact trước đó.
```

Hai loại kill switch, cả hai đều bắt buộc:

- **Kill switch của artifact**: config trỏ về artifact trước. Tác dụng trong vòng một chu kỳ fetch (phút–giờ).
- **Kill switch toàn SDK**: cờ `sdk_enabled=false` → SDK ngừng mọi hoạt động, `decide()` trả về mặc định (L3), ngừng thu thập. Đây là cam kết an toàn phải ghi trong hợp đồng với khách hàng — họ cần biết rằng nếu SDK gây sự cố, có một nút tắt.

Kill switch phải **fail-safe theo hướng tắt tính năng mới**: nếu không gọi được config endpoint, SDK dùng giá trị cached cuối cùng; nếu chưa từng có cache, dùng mặc định bảo thủ được đóng gói sẵn. Và giá trị cached phải có TTL — một SDK giữ mãi cấu hình "bật" từ 8 tháng trước là kill switch không hoạt động.

---

## 9. Kỹ thuật chi phí

**Toàn bộ mục này là [Design synthesis]** — các con số là ước lượng bậc độ lớn để suy luận kiến trúc, không phải báo giá. Chúng sẽ sai theo vùng, theo hợp đồng cam kết, theo nhà cung cấp. Điều đáng tin là **thứ tự các hạng mục**, không phải giá trị tuyệt đối.

### 9.1 Cái gì tạo ra chi phí

Đơn vị chi phí cơ bản không phải DAU mà là **event/ngày** và **decision/ngày**. Giả định mô hình: 30 event/DAU/ngày, 10 decision/DAU/ngày, feature vector ~1 KB thô / ~150 B sau nén columnar.

| DAU | Events/ngày | Decisions/ngày | Log giữ lại (sau sampling) |
|---|---|---|---|
| 1M | 30M | 10M | ~2M decision full-fidelity |
| 10M | 300M | 100M | ~15M |
| 100M | 3B | 1B | ~120M |

### 9.2 Cái gì chiếm ưu thế ở mỗi quy mô

| Quy mô | Hạng mục chi phối | Hạng mục thứ hai | Ghi chú |
|---|---|---|---|
| **1M DAU** | **Con người** (2–4 kỹ sư) — vượt xa mọi thứ khác | Managed services phí cố định (Kafka cluster tối thiểu, Flink, monitoring vendor) | Ở quy mô này, hạ tầng gần như miễn phí so với lương. Tối ưu cho **tốc độ**: mua mọi thứ, chạy on-device để không có chi phí biến đổi. |
| **10M DAU** | **Egress mạng + streaming compute (Flink)** | Lưu trữ log nóng, monitoring vendor tính theo dữ liệu | Vendor observability (Datadog/New Relic) tính theo custom metric/log volume trở nên đắt đột ngột. Chuyển metric sang Prometheus/VictoriaMetrics tự quản. |
| **100M DAU** | **Lưu trữ + query (data lake) và egress** | Streaming compute | Nếu để inference chạy server-side ở quy mô này, compute serving sẽ vượt tất cả — đây là lý do kinh tế mạnh nhất cho on-device. |

### 9.3 Bảy đòn bẩy cắt chi phí, theo thứ tự ROI

1. **Đẩy quyết định xuống on-device.** Loại bỏ hoàn toàn chi phí biến đổi của decision path. Đây là đòn bẩy lớn nhất và cũng là đòn bẩy đúng về mặt UX.
2. **Sampling exploit traffic** (mục 6.3). Giữ 100% explore + 1–10% exploit thường cắt 80–90% khối lượng log mà mất rất ít giá trị thống kê.
3. **Columnar + zstd + partition pruning.** Parquet/Iceberg nén feature vector rất tốt; partition theo `dt/country/sdk_version` khiến query experiment chỉ quét vài phần trăm dữ liệu.
4. **Lifecycle tiering** S3 Standard → IA (30d) → Glacier (90d) → xoá theo retention policy. Kết hợp với mục 6.4 nên đằng nào cũng phải làm.
5. **Cắt số feature streaming (T1).** Mỗi feature streaming là state trong Flink, là checkpoint, là ghi vào online store. Giảm từ 60 xuống 12 feature T1 thường cắt hơn nửa chi phí streaming mà mất rất ít AUC.
6. **Compression + batching phía client** (mục 7.2) giảm cả egress lẫn chi phí ingest, đồng thời tốt cho pin của user — hiếm khi có đòn bẩy nào vừa cắt chi phí vừa cải thiện sản phẩm như vậy.
7. **Bỏ vendor observability tính theo volume** cho metric khối lượng lớn; giữ vendor cho alerting/on-call.

Phản-mẫu chi phí thường gặp: giữ mọi raw event ở dạng nóng "phòng khi cần"; log 100% feature vector cho 100% traffic; chạy Flink với 200 feature vì "đằng nào cũng có pipeline rồi"; dùng vendor A/B testing tính theo MAU khi đã có sẵn hạ tầng log.

---

## 10. Kiến trúc tham chiếu và bảng build-vs-buy

### 10.1 Kiến trúc tham chiếu đầy đủ

```text
╔══════════════════════════ CLIENT (Android SDK, ≤400KB) ═══════════════════════╗
║  public API (try/catch Throwable, ≤5ms main thread)                           ║
║    ├── decide(context) ──► PolicyEngine                                       ║
║    │      L0 artifact ▸ L1 bundled ▸ L2 heuristic ▸ L3 no-op                  ║
║    ├── track(event)   ──► ring buffer ──► SQLite WAL (bounded 10k/5MB/7d)     ║
║    └── ConfigManager  ──► ETag fetch: {policy_url, sha256, sig, flags}        ║
║  background thread: uploader (batch 50 / 30s / ON_STOP, gzip, backoff+jitter) ║
║  WorkManager: opportunistic flush (network + battery-not-low constraints)     ║
╚═══════════════════════════════════════════════════════════════════════════════╝
        │ HTTPS + cert pin                              ▲ CDN (immutable, signed)
        ▼                                               │
╔═══════════════════ EDGE ══════════════════╗   ╔═══════╧══════════════════════╗
║ TLS · auth · rate limit · consent enforce ║   ║ artifact CDN + config service ║
║ schema validate · pseudonymize (HMAC)     ║   ║ rollout %, kill switch, sig   ║
║ load shed (drop telemetry trước revenue)  ║   ╚═══════▲══════════════════════╝
╚════════════════┬══════════════════════════╝           │
                 ▼                                       │ publish
     ┌───────────────────────┐                  ┌────────┴──────────┐
     │ Kafka (partition:     │                  │ artifact builder  │
     │ user_hash, 7d retain) │                  │ + signer + CI     │
     └────┬─────────┬────────┘                  └────────▲──────────┘
          │         │                                     │
   ┌──────▼───┐  ┌──▼─────────────────┐                  │
   │ Flink:   │  │ Sink: Iceberg/S3   │                  │
   │ dedupe   │  │ (raw, immutable,   │                  │
   │ +window  │  │  partitioned)      │                  │
   │ agg      │  └──┬─────────────────┘                  │
   └────┬─────┘     │                                     │
        ▼           ▼                                     │
  ┌──────────┐  ┌────────────────────────────────┐       │
  │ online   │  │ OFFLINE: point-in-time join,   │       │
  │ store KV │  │ training, OPE, experiment      ├───────┘
  │ (TTL)    │  │ analysis (CUPED, SRM, mSPRT)   │
  └────┬─────┘  └────────────────────────────────┘
       │
  ┌────▼────────────────────┐   ┌──────────────────────────────────┐
  │ server inference        │   │ MONITORING: PSI/KS/JS, calibration│
  │ (chỉ cho high-value     │   │ label maturity, SRM daily,        │
  │  decisions) + fallback  │   │ auto-rollback triggers            │
  └─────────────────────────┘   └──────────────────────────────────┘
```

### 10.2 Build vs buy theo từng lớp

Giả định: team 3–6 kỹ sư, sản phẩm là SDK bán cho bên thứ ba, thị trường US/EU. [Design synthesis]

| Lớp | Khuyến nghị | Lý do |
|---|---|---|
| Event ingest gateway | **Build** (nhẹ: Go/Rust sau ALB) | Là bề mặt hợp đồng với SDK; cần kiểm soát schema, consent enforcement, load shedding. Không có sản phẩm nào vừa vặn. |
| Message bus | **Buy** (MSK / Confluent Cloud / Kinesis) | Vận hành Kafka tự quản là một công việc toàn thời gian. |
| Stream processing | **Buy managed Flink** ở giai đoạn đầu | Chuyển sang tự quản chỉ khi chi phí vượt lương một kỹ sư. |
| Data lake | **Buy storage (S3) + build layout (Iceberg)** | Iceberg cho row-level delete (GDPR) là quyết định phải đúng từ đầu. |
| Offline feature store | **Build mỏng trên Iceberg**, hoặc **Feast** nếu muốn chuẩn hoá | Point-in-time join là ~300 dòng SQL nếu schema đúng. |
| Online feature store | **Buy KV managed** (DynamoDB / ElastiCache / Bigtable) | Chỉ cần GET theo key p99 vài ms. |
| Feature definition framework | **Buy/adopt Chronon** nếu cần nhiều feature streaming; nếu không, **build** thư viện aggregation dùng chung | Giá trị chính là *một định nghĩa, hai runtime*. |
| Model training | **Build** (LightGBM/PyTorch + orchestrator) | Không có gì đặc thù. |
| Model registry / artifact build | **Build** | Artifact format là đặc thù của SDK; phải tự sign, tự version, tự gắn min_sdk. |
| Serving runtime (nếu có server) | **Buy** (Triton/ONNX Runtime) hoặc **build** service nhúng LightGBM | GBDT nhúng trong Go/Rust rẻ và nhanh hơn model server. |
| Config & artifact distribution | **Build config service + buy CDN** | Ép khách hàng nhúng Firebase là ma sát bán hàng lớn. |
| Experimentation assignment | **Build** (hash + ranged bucketing, ~200 dòng) | Phải chạy giống hệt ở client và server; vendor không cho điều đó. |
| Experimentation analysis | **Build trên SQL/notebook**, cân nhắc **buy** khi > 50 experiment đồng thời | CUPED + SRM + BH là code có thể viết được; điều khó là kỷ luật, không phải code. |
| Metrics/monitoring hạ tầng | **Buy** ở 1M DAU → **build** (Prometheus/VictoriaMetrics + Grafana) ở 10M+ | Chi phí vendor theo volume tăng phi tuyến. |
| Drift monitoring | **Build** (job hằng ngày tính PSI/JS + alert) | Đơn giản, và ngưỡng phải hiệu chỉnh theo dữ liệu riêng. |
| Crash/ANR của SDK | **Buy** (Crashlytics/Sentry) + **build** self-report qua `ApplicationExitInfo` | Cần cả hai: vendor cho chẩn đoán, self-report cho cảnh báo rollout. |

### 10.3 Thứ tự triển khai đề xuất

```text
Giai đoạn 1 (0–3 tháng)  — "log trước, học sau"
  SDK: durable queue + batching + track() + kill switch + bundled policy L1
  BE : ingest gateway + Kafka + sink Iceberg + schema registry
  ✗ chưa có model, chưa có feature store, chưa có experiment platform
  → Giá trị: bắt đầu tích luỹ decision log đúng schema (mục 6.1) NGAY.
     Đây là tài sản không thể tạo hồi tố.

Giai đoạn 2 (3–6 tháng) — "quyết định trên thiết bị"
  SDK: PolicyEngine + artifact fetch/verify + assignment/bucketing + exposure log
  BE : config service + artifact CDN + signer + staged rollout + SRM daily check
  → Giá trị: có thể chạy A/B thật và ship policy mà không cần release SDK.

Giai đoạn 3 (6–12 tháng) — "học từ log"
  BE : offline feature store + point-in-time join + training + OPE
       drift monitoring + auto-rollback + CUPED + always-valid p-values
  → Giá trị: vòng lặp cải tiến khép kín.

Giai đoạn 4 (12+ tháng) — "tươi hơn"
  BE : Flink streaming features (chỉ 10–15 feature T1) + online store
       server-side inference cho quyết định giá trị cao
  → Chỉ làm khi đã chứng minh được feature tươi mang lại lợi ích đo được.
```

Điểm quan trọng nhất của thứ tự này: **giai đoạn 1 là bắt buộc và không thể bỏ qua**, vì decision log là thứ duy nhất không thể tạo lại về sau. Streaming feature store — thứ mà mọi bài viết MLOps đặt ở trung tâm — nằm ở giai đoạn 4, và nhiều sản phẩm không bao giờ cần đến nó.

---

## Không xác minh được (ghi rõ)

1. **Giới hạn kích thước tổng của Firebase Remote Config template.** Kết quả tìm kiếm cho thấy giới hạn số parameter theo template type (mức được nêu là 3000, khác với con số 2000 trong tài liệu cũ hơn) và độ dài khoá 256 ký tự, nhưng **không xác minh được** con số giới hạn kích thước payload/template cụ thể. Phải tra trực tiếp trang "Limits on parameters and conditions" trước khi thiết kế dựa vào nó.
2. **Toàn bộ các con số latency budget** ở mục 1.5, 3.1 và các bảng ngân sách ở mục 7 là [Design synthesis] — mục tiêu thiết kế, không phải số đo từ hệ thống production nào. Phải hiệu chỉnh bằng benchmark thực tế.
3. **Toàn bộ mô hình chi phí ở mục 9** là ước lượng bậc độ lớn không có nguồn. Không có báo giá vendor, không có số liệu thực. Chỉ thứ tự ưu tiên các hạng mục là kết luận đáng tin.
4. **Ngưỡng PSI 0.1/0.25** là quy ước ngành phổ biến (xuất phát từ credit risk scoring), không phải kết quả từ một nghiên cứu được kiểm chứng cho ngữ cảnh monetization. Phải tự hiệu chỉnh theo baseline noise của dữ liệu mình.
5. **Các ngưỡng rollback tự động** ở mục 4.4 hoàn toàn là [Design synthesis]. Chưa hiệu chỉnh với bất kỳ baseline thực nào.
6. **Tỉ lệ nén gzip 70–85% cho JSON telemetry** là ước lượng kinh nghiệm, chưa đo trên payload cụ thể của SDK này.
7. **Con số về Palette (>20.000 feature)** đến từ tóm tắt kết quả tìm kiếm về blog kỹ thuật của Uber, chưa đọc trực tiếp bài gốc để xác nhận ngữ cảnh và thời điểm của con số.
8. **Chi tiết triển khai của Chronon** (cách nó sinh batch/streaming job từ một spec) mô tả ở đây dựa trên tóm tắt công khai; chưa đọc source code hoặc tài liệu kỹ thuật đầy đủ.
9. **Ngân sách kích thước SDK ≤400 KB** là mục tiêu tự đặt, không phải chuẩn ngành. Các SDK quảng cáo thương mại phổ biến thường lớn hơn nhiều.
10. Chưa xác minh **giới hạn cụ thể về throughput per shard của Kinesis** và **cách tính chi phí retention của Kafka managed** ở thời điểm 2026.

---

## Nguồn chính

**[Primary engineering blog]** — Chronon, Airbnb's ML feature platform, is now open source (Varant Zanoyan, Nikhil Simha Raprolu, Airbnb Tech Blog) — https://medium.com/airbnb-engineering/chronon-airbnbs-ml-feature-platform-is-now-open-source-d9c4dba859e8

**[Primary docs]** — Chronon — Open-Source Feature Platform for Machine Learning — https://chronon.ai/

**[Primary engineering blog]** — Palette Meta Store Journey (Uber Engineering Blog) — https://www.uber.com/us/en/blog/palette-meta-store-journey/

**[Primary engineering blog]** — Michelangelo Palette: A Feature Engineering Platform at Uber (InfoQ presentation) — https://www.infoq.com/presentations/michelangelo-palette-uber/

**[Primary docs]** — Timely Stream Processing (Apache Flink documentation) — https://nightlies.apache.org/flink/flink-docs-stable/docs/concepts/time/

**[Primary docs]** — Builtin Watermark Generators (Apache Flink documentation) — https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/event-time/built_in/

**[Primary docs]** — Streaming Analytics / Learn Flink (Apache Flink documentation) — https://nightlies.apache.org/flink/flink-docs-stable/docs/learn-flink/streaming_analytics/

**[Primary research]** — Diagnosing Sample Ratio Mismatch in Online Controlled Experiments: A Taxonomy and Rules of Thumb for Practitioners (Fabijan, Gupchup, Gupta, Omhover, Qin, Vermeer, Dmitriev — KDD 2019) — https://dl.acm.org/doi/10.1145/3292500.3330722

**[Primary research]** — Diagnosing Sample Ratio Mismatch in A/B Testing (Microsoft Research ExP Platform) — https://www.microsoft.com/en-us/research/group/experimentation-platform-exp/articles/diagnosing-sample-ratio-mismatch-in-a-b-testing/

**[Primary research]** — Improving the Sensitivity of Online Controlled Experiments by Utilizing Pre-Experiment Data (CUPED; Deng, Xu, Kohavi, Walker — WSDM 2013) — https://dl.acm.org/doi/10.1145/2433396.2433413

**[Primary research]** — Peeking at A/B Tests: Why it Matters, and What to Do About It (Johari, Pekelis, Walsh, Koomen — KDD 2017) — https://dl.acm.org/doi/10.1145/3097983.3097992

**[Primary research]** — Always Valid Inference: Continuous Monitoring of A/B Tests (Johari et al., Operations Research) — https://dl.acm.org/doi/10.1287/opre.2021.2135

**[Primary engineering blog]** — Switchback Tests and Randomized Experimentation Under Network Effects at DoorDash — https://careersatdoordash.com/blog/switchback-tests-and-randomized-experimentation-under-network-effects-at-doordash/

**[Primary engineering blog]** — Analyzing Switchback Experiments by Cluster Robust Standard Error to Prevent False Positive Results (DoorDash) — https://careersatdoordash.com/blog/cluster-robust-standard-error-in-switchback-experiments/

**[Primary engineering blog]** — Balancing Network Effects, Learning Effects, and Power in Experiments (DoorDash Engineering) — https://doordash.engineering/2022/02/16/balancing-network-effects-learning-effects-and-power-in-experiments/

**[Primary docs]** — Monitor your app's technical quality with Android vitals (Google Play Console Help) — https://support.google.com/googleplay/android-developer/answer/9844486?hl=en

**[Primary docs]** — Raising the bar on technical quality on Google Play (Android Developers Blog) — https://android-developers.googleblog.com/2022/10/raising-bar-on-technical-quality-on-google-play.html

**[Primary docs]** — ANRs | App quality (Android Developers) — https://developer.android.com/topic/performance/vitals/anr

**[Primary docs]** — Android vitals | App quality (Android Developers) — https://developer.android.com/topic/performance/vitals

**[Primary engineering blog]** — Shrinking APKs, growing installs (Sam Tolomei, Google Play Apps & Games) — https://medium.com/googleplaydev/shrinking-apks-growing-installs-5d3fcba23ce2

**[Primary docs]** — Firebase Remote Config — https://firebase.google.com/docs/remote-config

**[Primary docs]** — Remote Config Parameters and Conditions (Firebase) — https://firebase.google.com/docs/remote-config/parameters
