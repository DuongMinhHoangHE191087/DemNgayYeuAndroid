# 46 — Hợp đồng Backend: hai chiều dữ liệu, độc lập với stack

**Ngày viết:** 2026-08-24
**Cập nhật:** 2026-08-24 (cùng ngày) — **mốc 1 xong**: ingestion + kho log + đọc bốn North Star Metric. Đã chạy thử end-to-end với uvicorn thật, có Dockerfile và script Cloud Run.
**Cập nhật:** 2026-08-25 — `brain_feedback` mang thêm `reward_value` (nullable, mục B.2) và backend có OPE phía server (`GET /ope/policies`, doc 59).
**Cập nhật:** 2026-09-30 — **1.7.0**: một đường ống `/push` cho mọi event, envelope v2, chuỗi event quảng cáo v2 (mục B.0), backend theo app + rate limit + `/health` + staging/`MERGE` khử trùng + view SQL cho bảng DOC/62 §2.1 + stack cục bộ Docker (mục D.1).
**Cập nhật:** 2026-10-01 — **1.8.0**: `iap_purchase` v2 (mục B.3: `price_micros` là số thực thu, `phase`; USD tính ở server), event `iap_result`/`paywall_*`, `subscription_state` thêm `source`; server→SDK: `POST /rtdn` và `signals` trong phản hồi `/push` (mục C.3); khử trùng IAP theo `iap_purchase:tok:<purchase_token>`.
**Trạng thái:** số test hiện hành lấy từ lần chạy, không ghi cứng ở đây: SDK — DOC/65 và CHANGELOG; backend — `cd backend && pytest -q -m "not integration"` = 246 passed + 1 skip có tên (`tests/test_play_verify.py:137`) và 10 test `integration` (emulator, khoá `docker`), xem [`backend/README.md`](../backend/README.md).

---

## A. Vì sao tài liệu này đứng trước việc chọn stack

Doc 25 mục E liệt kê backend tối thiểu cần 3 thành phần và ghi "cần quyết định stack riêng".
Nhưng **hợp đồng dữ liệu không phụ thuộc stack** — nó giống hệt nhau dù backend viết bằng
Python, Go hay Kotlin. Chốt hợp đồng trước rồi mới chọn stack là đúng thứ tự, và nó cho một
lợi ích cụ thể: mọi thứ ở phía client có thể hoàn tất và kiểm chứng ngay bây giờ.

Trạng thái hiện tại của hai chiều:

```text
   SDK (đã xong)                         BACKEND
   ─────────────                         ───────
   DecisionLogger ──── 5 event ─────────→ (1) Ingestion      ✅ backend/
   DecisionLogQueue    bền vững, có ACK       (1b) Đọc chỉ số   ✅ /metrics/northstar
                                              (2) Feature store   ⬜ chưa
                                              (3) Training/policy ⬜ chưa
   BrainPolicy    ←─ policy artifact ───┘
   (đã có test hợp đồng)
```

Chiều **backend→SDK** đã được khoá bằng test: [`PolicyArtifactContractTest`](../src/test/java/com/app/plugin/brain/PolicyArtifactContractTest.kt)
nạp chính [`DOC/policy_artifact_example.json`](policy_artifact_example.json). Backend chỉ cần
sinh ra file đúng hình dạng đó — xem doc 35.

Chiều **SDK→backend** là phần còn lại của tài liệu này.

## B. Năm event SDK phát ra

Tất cả đi qua `LogEventServer` tới `Constants.LogEvent.BASE_URL`, kèm sequence id và
global user properties có sẵn của pipeline.

### B.0 Một đường ống `/push`, envelope v2, bảng tên (1.7.0)

Mọi event — brain, IAP, paywall, quảng cáo, consent, phiên — đi **một** đường: `LogEventServer` → `LogEventTransport` →
`POST /push` (`@POST("push")`) tới `DATABUCKETS_BASE_URL` (meta-data của app; rỗng = telemetry tắt) → backend → `events_staging` → `MERGE` → `events_raw` → view SQL.
`Constants.LogEvent.BASE_URL` (endpoint cũ) không được đọc ở đâu trong SDK; mọi event đi `DATABUCKETS_BASE_URL`.

**Envelope v2** (cấp cao nhất của body, thêm bởi `EventEnvelope.wrap`; giữ nguyên `sequenceId`, `eventName`, `eventData`, `ts`, `userProperty`):

| Trường | Ghi chú |
|---|---|
| `event_id` | UUID v4 sinh **lúc dựng**, một chỗ; giữ nguyên khi gửi lại (khoá khử trùng `ev:<event_id>`) |
| `schema_version` | `2`. Backend chỉ đưa hàng `schema_version = 2` vào view chỉ số |
| `sdk_version` | `BuildConfig.SDK_VERSION` |
| `package_name` | `applicationContext.packageName`. Backend **không** tin nó cho `app_id`: `app_id` gán từ khoá API |
| `consent_tier` | `ConsentState.tier().name` lúc dựng |

**Bảng tên event v2** (tham số từng event: bảng "Event v2" ở [DOC/62](62_AD_MONETIZATION_STANDARD.md) §2.1):
`ad_request`, `ad_fill`, `ad_no_fill`; `ad_show_attempt`, `ad_show_fail`, `ad_click`; `ad_show`, `ad_revenue` (`value_micros`, `precision_type`);
`consent_form_shown`, `consent_choice`; `session_end`. Tên cũ `ad_start_load`, `ad_load`, `ad_start_action`, `ad_show_fail_reason`
không còn phát. Event không liệt kê (brain, IAP, paywall…) giữ nguyên tên/tham số. Placement có tiền tố `full_` báo `ad_format=fullscreen`.

Q2 không đổi: sau `DENIED` không event nào tới server (giữ trong RAM, bỏ khi hết process), nên **tỉ lệ đồng ý consent không tính được từ backend** —
nguồn là báo cáo AdMob Privacy & messaging (DOC/62 §2.1).

### B.1 `brain_decision` — quan trọng nhất, không tái tạo được

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `decision_id` | string | khoá join với `brain_feedback` |
| `decision_point` | string | enum `DecisionPoint` |
| `chosen_action_id` | string | |
| `chosen_action_mode` | string | enum `ActionMode` |
| `chosen_action_probability` | double | **propensity** |
| `eligible_actions` | string | tập ứng viên **sau** guardrail, ngăn cách bằng `,` |
| `guardrails_applied` | string | mã guardrail đã nổ |
| `segment` | string | `LatentPlayerType` khả dĩ nhất |
| `segment_confidence` | double | |
| `scenario` | string | `PersonalizationScenario` |
| `is_holdout` | int | 0/1 |
| `policy_version` | string | version của artifact đang chạy |

**Hai trường không bao giờ tái tạo được sau này:** `chosen_action_probability` phụ thuộc trạng
thái posterior tại đúng khoảnh khắc quyết định, và `eligible_actions` phụ thuộc guardrail đã
chạy lúc đó. Thiếu chúng thì mọi estimator OPE (IPS, DR, switch-DR) đều vô hiệu — doc 28 mục H.
Backend **phải** lưu nguyên hai trường này, không được suy diễn lại.

Hệ quả cho thiết kế bảng: `chosen_action_probability` là `double` chứ không phải `float`, và
`eligible_actions` phải giữ nguyên thứ tự — cùng một tập theo thứ tự khác nhau vẫn là cùng một
tập, nhưng khi đối soát log thì thứ tự giúp phát hiện lỗi phía client.

### B.2 `brain_feedback`

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `decision_id` | string | |
| `outcome_event` | string | |
| `outcome_timestamp_ms` | long | |
| `reward_value` | double, **nullable** | thêm 2026-08-25 — xem dưới |

Join với `brain_decision` qua `decision_id`. Outcome đến **sau** quyết định, thường ở cuối
phiên — backend phải chịu được độ trễ này (doc 28 mục C: delayed feedback).

**`reward_value` là mảnh thứ ba của OPE.** Hai trường propensity + eligible của B.1 là tử số
và mẫu số; thiếu reward thì estimator IPS/DR không tính được — và trước bản 2026-08-25
reward chỉ tồn tại trên máy (`RewardModel.estimate`, con số bandit dùng để học). Nay SDK gửi
nó nguyên bản trong event này. Hợp đồng nullable phải giữ đúng: **vắng mặt ≠ 0.0** — bản ghi
của SDK cũ không có trường này, và 0.0 là reward hợp lệ (quảng cáo hiện xong người chơi bỏ
game). Backend parse ra `null` khi vắng mặt, không bao giờ default 0.0 (xem doc 59).

### B.3 `iap_purchase` (v2, 1.8.0)

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `product_id`, `order_id` | string | `order_id` có thể rỗng ở bản phát lại sau restore/cold start (D15) |
| `price_micros` | long | **số thực thu** của pha vừa tính tiền, nguyên bản của store (micros), chưa quy đổi: trial = 0, intro = giá intro, base = giá gốc (D5) |
| `currency_code` | string | |
| `quantity` | int | |
| `channel` | string | `PLAY_STORE` / `WEB_SHOP` |
| `is_subscription` | int | 0/1 |
| `purchase_token` | string | khoá khử trùng (mục C.3) |
| `phase` | string | `trial` \| `intro` \| `base` \| `inapp` (1.8.0) |
| `offer_tag` | string | tag của offer đã chọn; rỗng với INAPP |
| `paywall_session_id`, `placement`, `variant_id` | string | rỗng khi mua ngoài paywall; cùng `paywall_session_id` với `paywall_impression`/`paywall_choice` |

**Quy đổi USD là việc của backend, không phải của client (D3):** từ 1.8.0 client **không** gửi `usd`; `FxRates.kt` ở client không còn được SDK dùng.
USD = `price_micros` quy đổi theo `fx_daily` của ngày giao dịch ở view `v_arpdau_country`; không có tỉ giá thì hàng vào `iap_unconverted`
(không bỏ), `phase = trial` tính 0. Con số đưa vào báo cáo tài chính phải quy đổi ở nơi có tỉ giá thật, theo đúng ngày giao dịch (doc 45 mục D.1).

**Event mới cùng đợt (hợp đồng E của plan 1.8.0; đều bền và không lấy mẫu, D7):**

| Event | eventData (snake_case) |
|---|---|
| `iap_result` | `outcome` (`success`\|`cancel`\|`pending`\|`failed`\|`already_owned`\|`ack_failed`), `response_code`, `product_id`, `paywall_session_id` |
| `paywall_impression` | như cũ + `paywall_session_id`, `schema_version_cfg` |
| `paywall_holdout_skip` | như cũ + `paywall_session_id` |
| `paywall_skip` | `placement`, `reason` (`session_cap`\|`brain`\|`no_config`) |
| `paywall_choice` | `paywall_session_id`, `placement`, `variant_id`, `product_id` |
| `subscription_state` | như cũ + `source` (`client`\|`server`) |

Không có `paywall_purchase` (trùng doanh thu với `iap_purchase`) và `paywall_close` suy ở server (phiên có impression mà không có choice). Bảng `events_raw`/`events_staging`
thêm 7 cột NULLABLE ở cuối: `phase`, `offer_tag`, `paywall_session_id`, `outcome`, `response_code`, `schema_version_cfg`, `source`; bảng tạo trước 1.8.0 phải chạy
`ALTER TABLE … ADD COLUMN` (`bq_setup` dừng rõ ràng thay vì tự sửa).

### B.4 `ad_revenue` — không do brain sinh ra, nhưng thuộc về đây

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `ad_format`, `ad_platform`, `ad_network`, `placement` | string | |
| `ad_revenue` | double | **số thực, KHÔNG phải micros** — khác hẳn `iap_purchase` |
| `currency_code` | string | |

Event này đã có sẵn trong pipeline ads từ trước. Nó được đưa vào hợp đồng brain vì thiếu nó
thì ARPDAU chỉ có nửa vế IAP — mà ARPDAU là North Star số 1 (doc 26 mục C.1).

**v2 (1.7.0 Task 3):** `ad_revenue` sang `value_micros` (Long) + `currency` + `precision_type` + `imp_id` + `ad_source`/`mediation`/`ad_unit`, bảng ở
DOC/62 §2.1. Từ 1.7.0 chỉ còn dạng v2 này được phát (hàm public `LogEventServer.logAdRevenue` gọi thẳng từ app vẫn gửi với `imp_id` rỗng, không đối soát được).

Chú ý đơn vị: `iap_purchase` dùng `price_micros` (integer, số thực thu), `ad_revenue` dùng `value_micros` (Long) từ 1.7.0 (dạng v1 là số thực). Ép hai
thứ về cùng đơn vị ở lớp ingestion sẽ giấu mất sự khác biệt đó và tạo sai số làm tròn ở đúng
chỗ khó truy nhất.

### B.5 `brain_comfort`

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `comfort_value` | double | 0..1 |
| `comfort_coverage` | double | **bắt buộc đọc trước `comfort_value`** |
| `components_missing` | string | ngăn cách bằng `;` |
| `policy_aggressiveness` | string | `MÃ=số;MÃ=số` |

Dashboard **không được vẽ `comfort_value` mà bỏ `comfort_coverage`**. Hiện độ phủ là 0.4 —
vẽ một đường trông như đã đo được sự thoải mái của người chơi trong khi ba tín hiệu quan trọng
nhất chưa tồn tại là cách nhanh nhất để cả đội tin vào một con số rỗng.

`policy_aggressiveness` đi cùng event nhưng **không** nằm trong công thức comfort — nó là chỉ
số sức khoẻ chính sách, phải vẽ ở panel riêng.

## C. Độ bền: SDK đã lo, backend cần biết để không phá

`DecisionLogQueue` ghi bản ghi xuống đĩa **trước** khi gửi và chỉ xoá khi server xác nhận
`2xx`. Hệ quả bắt buộc với backend:

1. **Endpoint phải trả 2xx chỉ khi đã ghi bền.** Trả 2xx rồi mới xử lý bất đồng bộ và làm rơi
   bản ghi là phá vỡ toàn bộ cơ chế — client đã xoá bản ghi đó khỏi hàng đợi.
2. **Phải chịu được bản ghi trùng.** App bị kill sau khi server nhận nhưng trước khi client xoá
   sẽ khiến bản ghi được gửi lại. `decision_id` là khoá idempotent tự nhiên.
3. **Bản ghi có thể tới rất muộn** — hàng đợi replay khi app mở lại, có thể vài ngày sau. Ngày
   báo cáo phải dựa trên `timestamp` trong payload, không phải giờ nhận.
   Phân vùng vật lý có hạn giữ thì ngược lại: theo `received_date` (ngày UTC server nhận, 1.7.0 F2-be) — theo ngày client, BigQuery xoá ngay hàng tới muộn vào phân vùng đã hết hạn.

Từ 1.7.0 độ bền đó mở rộng sang event tiền và phễu (`DurableEvents`, CHANGELOG `t6`): `ad_revenue`, `iap_purchase`, `first_open`, `session_start`,
`session_end`, `consent_*` được ghi đĩa (chỉ khi cổng consent cho gửi) và replay **đúng body đã lưu** (`ts`, `sequenceId`, `event_id` giữ nguyên) — nên backend cũng phải chịu
trùng theo `event_id`: `MERGE` khử trùng theo `(IFNULL(app_id,''), dedup_key)`, `dedup_key` = khoá tự nhiên → `ev:<event_id>` → `seq:<user_id>:<sequence_id>:<ts>`.

Vì sao SDK làm chặt vậy trong khi event ads thì "gửi rồi quên": mất vài event ads chỉ làm nhiễu
thống kê, nhưng **mất decision log làm lệch suy luận nhân quả** — các lần mất không ngẫu nhiên
(mạng kém tương quan với thiết bị yếu, vùng địa lý, hành vi người chơi).

## C.3 Chiều server → SDK: `/rtdn` và `signals` (1.8.0)

Nguồn sự thật về vòng đời subscription (grace, hold, gia hạn, hoàn tiền) là Google Play; client chỉ thấy Play báo một sub trong grace như sub đang gia hạn.
Nên từ 1.8.0 có một đường ngược, chỉ để **đặt nhãn** (nhắc người dùng sửa thanh toán, phân tích), không bao giờ cấp hay thu hồi quyền lợi (D9):

```text
Play RTDN ──Pub/Sub push──→ POST /rtdn ──→ bảng subscription_events
                                                  │
SDK ──POST /push (lô event của user)──→ phản hồi 200 mang "signals"
```

**`POST /rtdn`** nhận envelope Pub/Sub push (`message.data` là base64 của `DeveloperNotification`; fixture: `backend/tests/fixtures/rtdn/`):

```json
{"message":{"attributes":{},"data":"<base64 của {\"version\":\"1.0\",\"packageName\":\"test.sdk.zd\",\"eventTimeMillis\":\"1790737560000\",\"subscriptionNotification\":{\"version\":\"1.0\",\"notificationType\":6,\"purchaseToken\":\"rtdn-fixture-sub-token\"}}>","messageId":"rtdn-fx-06","publishTime":"2026-09-30T03:06:00.000Z"},"subscription":"projects/local-test/subscriptions/rtdn-push"}
```

- Xác thực local: bearer tĩnh `BRAIN_RTDN_TOKEN` (≥ 16 ký tự, so `hmac.compare_digest`; không đặt → 503). Production dùng Pub/Sub OIDC JWT = việc của H7b (OWNER-NOTES #4); bản 1.8.0 chưa có đường đó.
- **2xx chỉ sau khi ghi bền** (như mục C.1). Khử trùng theo `rtdn:<messageId>`; token → `user_id`/`app_id` lấy từ `iap_purchase` đã lưu, nên RTDN cho token chưa biết không tạo tín hiệu.
- `notificationType` 1, 2, 3, 4, 5, 6, 7, 10, 12, 13, 20 → trạng thái (6 = `GRACE`); `voidedPurchaseNotification` → hàng hoàn tiền âm; `testNotification` → 2xx, không ghi.
- `subscriptionsv2` thật (`SubscriptionSource`) chỉ có bản fixture; bản thật trả 503 tới khi H7b có credential.

**`signals` trong phản hồi 200 của `/push`** (nguyên văn từ `test_rtdn.py::test_Q5_push_tra_signals`):

```json
{"ok":true,"accepted":1,"rejected":0,"signals":[{"v":1,"type":"sub_state","product_id":"pro_sub","state":"GRACE","event_ms":1790737560000}]}
```

`state` ∈ `GRACE|ON_HOLD|PAUSED|CANCELED|RECOVERED|RENEWED|RESTARTED|PURCHASED|REVOKED|EXPIRED`. Ràng buộc: ≤ 5 phần tử, sự kiện trong ≤ 7 ngày, trạng thái mới nhất mỗi sản phẩm,
chỉ khi lô có đúng một `user_id` và cùng app với khoá; đọc hỏng → `[]`, không 5xx; cache trong tiến trình theo `(app_id, user_id)` 60 s (`/rtdn` ghi thì xoá mục của user).
Lô không có sub/chưa có RTDN → `"signals":[]`. SDK đọc ≤ 4 096 byte (quá → bỏ cả khối), bỏ tín hiệu `v` ≠ 1, `type`/`state` lạ hoặc `event_ms` không mới hơn lần áp cuối.
Ánh xạ nhãn công khai: GRACE → `IN_GRACE_PERIOD`; ON_HOLD, PAUSED, REVOKED, EXPIRED → `LAPSED`; CANCELED → `CANCELLED`; RECOVERED, RENEWED, RESTARTED, PURCHASED → `ACTIVE`.
Nhãn server mới hơn lần đổi cuối của client thắng restore chỉ thấy PURCHASED trong 7 ngày (D27); `RecurringGrants` không đọc nhãn server (D28).

**Khử trùng token cho `iap_purchase` (D15, D24):** `dedup_key` = `iap_purchase:tok:<purchase_token>` (không có token → `order_id` như trước 1.8.0). Vì vậy bản phát lại sau
restore, pending → paid, hay kill giữa lúc cấp quyền và lúc lưu event (`event_id` mới, có thể thiếu `order_id`) chỉ tính một lần, kể cả so với hàng 1.7.0 đã mang `purchase_token`.
Ở lần mở 1.8.0 đầu tiên mỗi subscriber sẵn có gửi đúng một `iap_purchase` đã khử trùng (token lạ, đã acknowledge: chỉ server, không AppsFlyer). Người chọn `DENIED` không gửi `iap_purchase` từ client (D15):
sự thật về tiền của họ là RTDN/`subscriptionsv2` (H7b).

## D. Ba thành phần backend tối thiểu

Giữ nguyên như doc 25 mục E, nay có thêm chi tiết hợp đồng:

| # | Thành phần | Đầu vào | Đầu ra |
|---|---|---|---|
| 1 | **Ingestion ✅ ĐÃ XÂY** — [`backend/`](../backend/README.md) | event mục B (v2) | bảng thô + view chỉ số, khử trùng theo `dedup_key` (`decision_id` cho brain) |
| 2 | Feature store | event stream | `PlayerState` đầy đủ (doc 04) |
| 3 | Training/policy service | decision log + outcome | artifact JSON đúng doc 35 |

Thành phần 3 chính là chỗ đóng vòng lặp: tính lại tham số Beta per-segment từ log **toàn cục**
rồi đẩy xuống qua Remote Config. Hiện `BanditPolicy` học cục bộ trên từng máy — mỗi người chơi
là một thí nghiệm cô lập, và không có gì tổng hợp lại.

> **Lưu ý về schema artifact:** doc 35 hiện **chưa** có chỗ chứa tham số Beta warm-start
> (alpha/beta theo từng cặp segment×arm). `BanditPolicy.serialize()` đang dùng định dạng
> `key=alpha,beta;...` cho lưu trữ cục bộ. Khi thành phần 3 xuất hiện, artifact cần thêm một
> khối `bandit_prior` — và theo doc 35 mục G.2, artifact lớn nên nằm trên CDN với Remote Config
> chỉ chứa con trỏ, vì giới hạn kích thước một tham số Remote Config chưa được xác minh.

## C.1 Đọc ra: `GET /metrics/northstar`

Chiều đọc của mốc 1. Trả về **cả bốn** North Star Metric, **không có tham số chọn tập con** —
doc 26 mục C cấm xem riêng ARPDAU mà ẩn ComfortIndex, và cách duy nhất khiến lệnh cấm đó có
hiệu lực là đưa nó vào kiến trúc thay vì để trong quy ước.

Ba quyết định đáng ghi lại:

1. **Quy đổi tiền tệ nằm ở lớp phân tích, không ở ingestion.** Có tỉ giá theo ngày, và sửa
   được khi tỉ giá sai mà không phải đụng vào dữ liệu thô.
2. **Không quy đổi được thì báo cáo riêng, không bỏ qua.** Bỏ qua âm thầm sẽ làm ARPDAU thấp
   giả tạo đúng ở thị trường chưa nạp tỉ giá — sai lệch có hệ thống theo vùng địa lý, trông
   hệt như một kết luận về thị trường.
3. **Mẫu số ARPDAU là DAU trung bình theo ngày**, không phải user duy nhất toàn kỳ.

Phép tính viết bằng Python chạy trên hàng thô, **không** viết lại bằng SQL: hai bản cài đặt
của cùng một định nghĩa chỉ số sẽ trôi khỏi nhau, và khi hai dashboard hiện hai con số khác
nhau thì không ai biết bên nào đúng. Đánh đổi về khả năng co giãn được ghi rõ trong
[`backend/README.md`](../backend/README.md).

Ràng buộc "không ẩn bớt chỉ số" được áp **hai lần**: ở API (không có tham số chọn tập con) và
ở UI (không có bộ lọc/nút ẩn/tab, bốn thẻ render từ một mảng cố định). Lặp lại là cần thiết —
một bộ lọc ở UI sẽ vô hiệu hoá ràng buộc ở API mà không ai phải đụng vào API.

## C.2 Brain có tạo ra giá trị không

`holdout_comparison` trong `/metrics/northstar`. Bốn North Star nói brain đang **làm gì**;
chỉ so sánh với nhóm đối chứng nói brain có **đáng làm** hay không.

Ba nguyên tắc chống tự lừa mình:

1. **Người chưa được phân nhóm báo cáo riêng.** Trong lần chạy thử, một người dùng $50 chưa
   phân nhóm nếu bị gộp vào treatment sẽ thổi lift từ 100% lên 122%.
2. **Holdout rỗng thì trả `lift = null`**, không phải một con số vô hạn.
3. **Dưới 200 người mỗi nhóm thì `sufficient_data = false`** — chặn theo cỡ mẫu, KHÔNG phải
   kiểm định thống kê. Ghi rõ ngay trong payload để không ai nhầm.

Dashboard hiện doanh thu/người **cạnh** quảng cáo/người: doanh thu tăng mà quảng cáo mỗi
người cũng tăng vọt thì đó không phải brain thông minh hơn, chỉ là hung hăng hơn.

## D.1 Mốc 1 đã xây (2026-08-24)

Stack đã chốt: **Python + FastAPI + BigQuery** (theo doc 25 mục F.1 — dùng CausalML/EconML có
sẵn cho uplift/OPE thay vì tự viết). Phạm vi mốc 1 cố ý hẹp: **chỉ ingestion**, chưa train.

Lý do phạm vi hẹp: train trên dữ liệu chưa đủ và chưa sạch sẽ sinh ra artifact **tệ hơn giá
trị mặc định trong code** — mà mặc định trong code đã được chọn theo nguyên tắc fail-safe.
Phải tích log thật một thời gian trước đã.

| Thành phần | Ghi chú |
|---|---|
| `POST /push` | Khớp `@POST("push")` của `LogService`, xác thực qua `X-API-KEY` (từ 1.7.0 khoá theo app: `BRAIN_API_KEYS`) |
| `app/models.py` | Sao đúng wire format của `EventRequest.kt` — camelCase ngoài, snake_case trong `userProperty`, `timestamp`→`ts` |
| `app/normalize.py` | 4 event thành hàng có kiểu; event lạ vẫn lưu nguyên vẹn |
| `app/sinks/` | `JsonlSink` (có `fsync`) cho dev và làm lưới an toàn; `BigQuerySink` cho production |
| `schema/events_raw.json` | Phân vùng theo `received_date` (ngày UTC server nhận, 1.7.0 F2-be; `client_ts_date` là cột thường), cluster theo `event_name`,`user_id` |
| `app/metrics.py` + `app/readers.py` | Bốn North Star Metric; cùng một hàm chạy trên cả JSONL lẫn BigQuery |
| `app/static/dashboard.html` | Dashboard tự chứa, không tài nguyên ngoài; bốn thẻ North Star không ẩn được |
| `Dockerfile`, `deploy_cloudrun.sh` | Đóng gói sẵn — **script deploy chưa chạy bao giờ** |

Đã chạy thử end-to-end với uvicorn thật: 401 khi thiếu khoá, 200 khi có, bản ghi xuống đĩa
đủ trường kể cả cột phân vùng.

**1.7.0 (2026-09-30) — backend production-ready trên stack cục bộ:** `app/auth.py` (khoá theo app, `app_id` do server gán), `app/ratelimit.py` (token bucket theo `app_id` và
`user_id`), `app/errors.py` (một dạng lỗi + `request_id`), `GET /health` (không phải `/healthz`), giới hạn body 256 KB / 500 event; `dedup_key` mọi hàng; schema v2
(cột NULLABLE thêm, `raw_event_data` gửi dạng chuỗi JSON); `app/bq_setup.py` dựng bảng qua Python client (thay script setup_bq.sh cũ, đã xoá) và view `backend/sql/views/`; `MERGE`
`backend/sql/merge_events.sql`; `docker-compose.yml` (API + bigquery-emulator, project `local-test`); `tools/report_daily.sh`; `tools/gcp_guard.sh` cho mọi `gcloud`/`bq`
(chỉ project `test-d-56f09`; `bq query` và `bq mk` không nằm trong danh sách lệnh cho phép; điều kiện IAM ở `backend/RUNBOOK.md` §0); `app/admob_import.py` nhập báo cáo AdMob.
G13 đo trên sink JSONL (p95 7,7 ms ở 50 rps); số đo trên emulator chỉ là thông tin.

**Giới hạn đã biết (mốc 1, đã đổi ở 1.7.0 — bản hiện hành trong [`backend/README.md`](../backend/README.md):** chống trùng hiện
là "nỗ lực tốt nhất" qua `insertId` của BigQuery chứ chưa phải bảo đảm — đã thay bằng staging + `MERGE` ở 1.7.0, nhưng MERGE trên BigQuery thật
chưa có lịch; rate limit là trong process; retention theo phân vùng (`events_raw` 400 ngày, staging 2 ngày).

## D.2 Hiệu chỉnh artifact brain và view mới (1.9.0)

**`python -m app.calibrate`** (`backend/app/calibrate.py`) là đường ống từ log thật tới artifact brain; chạy end-to-end trên log gate ở 1.9.0 (H8, `t190-r-be-calibrate.log`):

```
python -m app.calibrate --jsonl <file> | --bq <project.dataset.table> --base <policy.json> --out <FILE> [--k 30] [--min-support 30]
```

- **`--out` là đường dẫn FILE** (không phải thư mục); bảng hỗ trợ ghi cạnh nó ở `<out>.support.md`.
- **Artifact = toàn bộ `--base` với `likelihood_profiles` được thay** (`rewarded_accept`, `payer_propensity`, `churn_tendency` từ proxy hàng v2 theo `segment` của quyết định mới nhất, co về prior với k = 30, kẹp [0.02, 0.98]); `enabled`/`experiment` chép từ base; cây khoá gốc giống mẫu `DOC/policy_artifact_example.json` (14 khoá gốc, 0 lệch); `fail_rate` = prior.
- **`version = calib-<ngày dữ liệu>-<sha8>`** (ngày UTC+7 của dữ liệu mới nhất; tất định theo dữ liệu). Base thiếu `version` = lỗi.
- **Bảng support:** mỗi ô (segment × trường) in số quan sát `n`; ô `n < 30` (`--min-support`) giữ **prior**. Chạy trên log gate (một người dùng, bị loại vì độ tin cậy < 0.4) mọi ô là prior: đường ống chạy, **không có số `mu` đã hiệu chỉnh nào được tuyên bố**; hiệu chỉnh có nghĩa cần dữ liệu app thật (cổng app đầu).
- Đầu vào rỗng = rc 2 (không có ngày dữ liệu để đặt `version`); CLI từ chối project production trước khi tạo client.

**View mới (1.9.0, `backend/sql/views/`, oracle `app/ad_metrics.py`, parity Δ 0):**
- `v_brain_decisions` — quyết định theo điểm × chế độ × holdout × policy. Cột `declined_app_day` = số `ad_show_fail` `show_full_brain_declined` ở **grain app × ngày**, lặp trên mỗi hàng quyết định — **không cộng dồn** giữa các hàng (D38).
- `v_ecpm_network_format` — eCPM (USD) theo ngày × `ad_network` × `ad_format` (nhãn chữ thường của `COALESCE(ad_network, ad_source)`, loại `meta_direct` doanh thu 0). `ad_format` gồm `rewarded_interstitial`.
- Cột `slot` trong `events_raw` (khoá slot của request ở `ad_request`/`ad_fill`/`ad_no_fill`, trước đó chỉ nằm trong `raw_event_data`); dataset có sẵn cần `ALTER TABLE … ADD COLUMN slot` (`bq_setup` dừng và in lệnh).
- Báo cáo ngày `tools/report_daily.sh --date D` nay có **11 bảng** (9 ở 1.8.0 + 2 view trên).

## E. Việc còn lại ở phía client trước khi backend xuất hiện: KHÔNG

Đây là kết luận đáng nói của tài liệu này. Cả hai chiều hợp đồng đã hoàn tất và có test ở phía
client. Không còn hạng mục nào ở repo Android chặn việc bắt đầu backend — thứ chặn là **quyết
định chọn stack**, và đó là quyết định của con người, không phải của code.

## Không xác minh được (ghi rõ)

1. `backend/` **chưa được deploy lên GCP** và chưa nhận một event thật nào; `deploy_cloudrun.sh` chưa chạy bao giờ. Hành vi đến từ test (`pytest -m "not integration"`,
   10 test integration trên emulator) và các lần chạy stack cục bộ. (Ý cũ "endpoint SDK trỏ pipeline log cũ" đã bỏ: `Constants.LogEvent.BASE_URL` không được đọc ở đâu, mọi
   event đi `DATABUCKETS_BASE_URL` — D10, 1 tham chiếu duy nhất tới endpoint cũ, ở `until/Constants.kt`.)
2. Thông lượng: G13 đo p95 trên sink JSONL (50 rps); chưa đo trên BigQuery thật. Cần đo trước khi bật rollout diện rộng.
3. Thành phần 2–3 của mục D (feature store, training/policy service) là **design synthesis** — chưa xây, không có gì để đối chiếu.
