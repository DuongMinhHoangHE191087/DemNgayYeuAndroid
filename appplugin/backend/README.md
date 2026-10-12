# Monetization Brain — Backend (mốc 1: ingestion + đọc chỉ số)

Nhận decision log từ SDK Android, lưu bền, và trả về bốn North Star Metric. **Chưa train gì
cả**, và đó là chủ đích: không có log sạch thì không train được, và phải tích log một thời
gian trước khi có đủ dữ liệu để tính bất cứ thứ gì.

Hợp đồng dữ liệu: [`../DOC/46_BACKEND_CONTRACT.md`](../DOC/46_BACKEND_CONTRACT.md).
Một đường ống `POST /push` cho mọi event; từ 1.7.0 body mang envelope v2 (`event_id`, `schema_version` = 2, `sdk_version`,
`package_name`, `consent_tier`) — tên event và tham số: DOC/46 §B.0 và bảng event v2 ở
[`../DOC/62_AD_MONETIZATION_STANDARD.md`](../DOC/62_AD_MONETIZATION_STANDARD.md) §2.1. Bảng chỉ số §2.1 sinh từ view SQL `sql/views/`
(nguồn: bảng `events_raw`); báo cáo AdMob nhập qua `app/admob_import.py` (`admob_report`).

## Chạy

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python -r requirements-dev.txt

# Dev: ghi ra file JSONL, không cần GCP, không cần khoá
.venv/bin/python -m uvicorn app.main:app --reload --port 8000

# Production
export BRAIN_API_KEYS="app_id:key,app_id2:key2"   # khoá theo app (server gán app_id từ khoá); khớp DATABUCKETS_API_KEY của từng app
# BRAIN_API_KEY=k (một khoá) = một app app_id=default; có cả hai thì BRAIN_API_KEYS thắng (log một dòng)
export BRAIN_SINK=bigquery
export BRAIN_BQ_PROJECT=...
GCP_GUARD_OWNER_GO=<id ledger> .venv/bin/python -m app.bq_setup   # chỉ chủ dự án, RUNBOOK §5
.venv/bin/python -m uvicorn app.main:app --host 0.0.0.0 --port 8080
```

Stack cục bộ (1.7.0 T10, chỉ dưới khoá `docker`, không bao giờ cùng cổng thiết bị): `docker-compose.yml` =
API (`127.0.0.1:8089`) + bigquery-emulator (`127.0.0.1:19050`, project `local-test`). `app.bq_setup` dựng
bảng (`app/bq_tables.py`, qua Python client) + view (`sql/views/`); `../tools/stack_load.py` nạp JSONL vào
`events_staging` rồi chạy `sql/merge_events.sql`; `../tools/report_daily.sh --date D` ghi 7 bảng DOC/62 §2.1;
`tools/loadtest.py` đo p50/p95/p99 (G13 tính trên sink JSONL). Test cần emulator đánh dấu `integration`
(`BRAIN_IT_BQ_ENDPOINT=http://127.0.0.1:19050 pytest -m integration`). `tests/test_z_health_bq_down.py` tự `docker compose stop bq` rồi `start bq` (chờ sẵn sàng); ghi đè bằng `BRAIN_IT_BQ_STOP_CMD`/`BRAIN_IT_BQ_START_CMD`.

Test: `cd backend && .venv/bin/python -m pytest -q -m "not integration"` (số hiện tại: xem log CI; 2.2.0 SECB: 367 passed + 1 skip có tên
`tests/test_play_verify.py:137`, chỉ skip khi `google-auth` đã cài) — lệnh CI job `backend` chạy, không cần Docker.
13 test `integration` (parity view SQL, MERGE idempotent, nạp emulator, `/health` 503 khi BigQuery dừng) cần bigquery-emulator, chạy dưới khoá `docker`
(mục "Stack cục bộ"). Số lượng lấy từ lần chạy 1.7.0 F2-be2 (`t170-f2be2-pytest.log`, `t170-f2be2-integration.log`); chạy lại để có số hiện tại.

Mở dashboard: `http://localhost:8000/`

Deploy: `./deploy_cloudrun.sh` (Cloud Run) — chỉ chủ dự án, hoặc controller sau go bằng văn bản (`GCP_GUARD_OWNER_GO`), mọi lệnh
đi qua `../tools/gcp_guard.sh`; xem [`RUNBOOK.md`](RUNBOOK.md) (§0 điều kiện IAM đọc trước). **Chưa chạy bao giờ** — xem mục "Giới hạn".

## Endpoint

### `POST /push` — ingestion

Khớp `LogService.logActionsToServer` (`@POST("push")`) của SDK.
Xác thực qua header `X-API-KEY`, đúng header mà `LogEventServer` đã gắn sẵn.

Từ 1.7.0 khoá theo app: `BRAIN_API_KEYS="app_id:key,…"`, so khớp `hmac.compare_digest` trên mọi khoá; server gán cột `app_id` từ khoá
(không tin `package_name` của client). Thiếu/sai khoá = 401. Lỗi 400/401/413/429/5xx cùng một dạng
`{"ok":false,"error":{"code","message"},"request_id","detail"}` (`detail` = `message`, cho client đời trước đọc `detail` của FastAPI); body > 256 KB hoặc > 500 event/lô = 413; vượt hạn mức token bucket theo
`app_id` (200 rps, burst 400) hoặc `user_id` (20 rps, burst 60) = 429 + `Retry-After` (giây, ≥ 1). Mỗi request một dòng log JSON, không bao giờ
in khoá.

**Không đặt `BRAIN_API_KEYS` lẫn `BRAIN_API_KEY` = chạy chế độ mở (dev), không kiểm tra; `/health` báo `auth:false`.** Cố ý không sinh khoá ngẫu
nhiên khi thiếu cấu hình: khoá đổi sau mỗi lần khởi động lại sẽ khiến mọi client bị 401 và
bản ghi dồn ứ trong hàng đợi của họ — một sự cố im lặng, rất khó truy.

Nhận cả event lẻ lẫn mảng. SDK hiện gửi từng cái; nhận sẵn mảng để sau này gộp batch không
phải đổi endpoint và không phải điều phối hai phía cùng lúc.

`signals` của `/push` được cache trong tiến trình theo `(app_id, user_id)` 60 giây (`SIGNALS_CACHE_TTL_S`, tối đa 10 000 mục); `/rtdn` ghi cho user nào thì xoá mục của user đó (1.8.0 Task 13b).

### `GET /health` — sẵn sàng thật

Không cần khoá (probe hạ tầng không mang khoá). Gọi `sink.ping()`: JSONL = thư mục ghi được; BigQuery = `get_dataset`. 200
`{"ok":true,"sink":…,"auth":<bool>,"version":<BRAIN_GIT_SHA>}` hoặc 503 `sink_unavailable`. (Đường dẫn là `/health`, không phải `/healthz`.)
BigQuery: một lần `get_dataset`, không thử lại, tối đa 5 s. `/push`: `insert_rows_json` ≤ ~20 s (thử lại 10 s + 10 s mỗi lần gọi). Reader `/metrics/*` (`app/bq_bounds.py`, 1.7.0 F2-be2): `query` ≤ ~20 s, chờ job ≈ ≤ 70 s (`result` 30 s mỗi lần gọi + thử lại 10 s, ngân sách chờ 30 s); đọc trang hàng thêm thời gian theo cỡ kết quả (`SELECT *` khoảng ngày rộng — đường hiếm).

### `GET /metrics/northstar` — bốn chỉ số, luôn cả bốn

Tham số: `date_from`, `date_to` (dạng `YYYY-MM-DD`, lọc theo `client_ts_date`).

**Endpoint này cố ý KHÔNG có tham số chọn tập con chỉ số.** Doc 26 mục C đặt ràng buộc: không
được xem riêng ARPDAU/Revenue mà ẩn ComfortIndex, vì đó chính là cách một đội vô tình tối ưu
ba chỉ số đầu bằng cách hy sinh chỉ số thứ tư. Ràng buộc kiểu này chỉ có tác dụng khi nằm
trong kiến trúc chứ không nằm trong quy ước — ở đây, muốn giấu ComfortIndex thì phải sửa code
và vượt qua một unit test (`test_khong_co_cach_nao_yeu_cau_tap_con`).

Ba điều đáng chú ý trong kết quả:

| | |
|---|---|
| `revenue.unconverted` | Doanh thu ở mã tiền tệ **chưa biết tỉ giá**, báo cáo riêng chứ không bỏ qua |
| `comfort.warning` | Xuất hiện khi độ phủ < 0.6 (hiện luôn xuất hiện, độ phủ là 0.4) |
| `subscription.note` | Nói rõ đây là số lượt MUA, không phải số sub đang active |

**Quy đổi tiền tệ nằm ở đây, không ở ingestion.** Đặt `BRAIN_FX_RATES="EUR=1.08,GBP=1.27"`.
Không nạp tỉ giá thì doanh thu ở mã đó vào `unconverted` — **không** bị âm thầm bỏ qua. Bỏ qua
sẽ làm ARPDAU thấp giả tạo đúng ở những thị trường chưa có tỉ giá, tức sai lệch có hệ thống
theo vùng địa lý: loại sai lệch tệ nhất, vì nó trông hệt như một kết luận về thị trường.

**Mẫu số của ARPDAU là DAU trung bình theo ngày**, không phải số user duy nhất toàn kỳ. Chia
cho tổng user cả kỳ cho ra con số nhỏ hơn nhiều và không so sánh được với bất kỳ báo cáo
ngành nào.

### `GET /` — dashboard vận hành

Một trang HTML tự chứa: không CDN, không font ngoài, không ảnh ngoài. Dashboard ops mà cần
Internet là dashboard sẽ trắng đúng lúc cần nó nhất — sự cố mạng, hoặc firewall của studio.
Có unit test canh điều này.

Bốn thẻ North Star render từ **một mảng cố định** trong JavaScript, và trang **không có bộ
lọc, không có nút ẩn, không có tab**. Muốn giấu ComfortIndex thì phải sửa file và vượt qua hai
unit test. Đây là cùng một ràng buộc doc 26 mục C đã áp ở tầng API, lặp lại ở tầng UI vì một
bộ lọc chỉ số ở UI sẽ vô hiệu hoá ràng buộc kia mà không ai phải đụng vào API.

**Trang không tự xác thực.** Nếu `BRAIN_API_KEY` được đặt thì `/metrics/northstar` trả 401 và
dashboard hiện đúng lỗi đó. Cố ý: nhúng khoá vào HTML để trang "chạy được" là phát khoá API
cho bất kỳ ai mở được URL. Đặt dashboard sau một lớp xác thực hạ tầng (Cloud Run IAP, VPN)
thay vì tự chế xác thực ở đây.

Khi lỗi, trang hiện **lỗi thật** thay vì để trắng hoặc hiện số 0 — số 0 trông giống một kết quả.

### So sánh treatment vs holdout

Nằm trong `/metrics/northstar` dưới khoá `holdout_comparison`, và là thẻ riêng trên dashboard.
Đây là phép đo trả lời câu hỏi quan trọng nhất về brain: *"personalization có tốt hơn không
làm gì không?"* Bốn North Star nói brain đang **làm gì**; chỉ so sánh với holdout nói brain
có **đáng làm** hay không.

Ba chỗ phép đo này dễ nói dối, và cách xử lý:

| Cạm bẫy | Xử lý |
|---|---|
| Người **chưa từng có quyết định nào** (ngoài rollout, hoặc chơi trước khi bật brain) | Báo cáo riêng ở `unassigned`, không nhập vào nhóm nào. Gán họ vào treatment — nhóm đông hơn — sẽ kéo trung bình về dân số chung và che mất hiệu ứng thật |
| **Nhóm holdout rỗng** (`holdout_fraction = 0`) | `lift = null` kèm cảnh báo, thay vì một phép chia cho ra "treatment tốt hơn vô hạn" |
| **Mẫu quá nhỏ** | `sufficient_data = false` khi bất kỳ nhóm nào < 200 người. doc 38 mục F yêu cầu chờ ≥ 2 tuần — công cụ phải cưỡng chế sự thận trọng đó, không chỉ hiện số rồi để người đọc tự nhớ |

Ngưỡng 200 là **chặn thận trọng theo cỡ mẫu, KHÔNG phải kiểm định ý nghĩa thống kê**. Nó
không thay thế việc tính p-value; nó chỉ ngăn kết luận ở quy mô hiển nhiên quá nhỏ.

Dashboard hiện `doanh thu/người` **cạnh** `quảng cáo/người` có chủ đích: doanh thu tăng mà số
quảng cáo mỗi người cũng tăng vọt thì đó không phải brain thông minh hơn, chỉ là hung hăng hơn.

### `GET /policy` — policy theo app (2.1.0)

`X-Api-Key` bắt buộc và kiểm TRƯỚC mọi thứ khác (sai/thiếu = 401, như `/push`); `app_id` lấy từ khoá, không từ query/body.
`X-Integrity-Token` tuỳ chọn. Phản hồi `{app_id, source: app|default, reason, policy}`: policy đã đăng ký
(`BRAIN_POLICY_DIR/<app_id>.json`) chỉ trả (`source=app`) khi verdict Integrity chấp nhận (`app_recognized` và `device_ok`);
mọi trường hợp khác (mode `off`, thiếu token, verifier từ chối hoặc ném lỗi, không có file) = artifact mặc định
`{"version": 0}` + `reason` ra dây thuộc `integrity_off | no_token | not_accepted | no_policy` (fail-closed; mã mịn như
`device_not_ok`/`verifier_error` chỉ vào log server, không kèm token). Log không bao giờ chứa token.

`BRAIN_INTEGRITY_MODE`: `off` (mặc định, không verifier) | `fake` (`FakeVerifier`: token `fake-ok` chấp nhận,
`fake-bad-device` từ chối; cho test/stack cục bộ) | `google` (Play Integrity `decodeIntegrityToken`, import lười
`google.auth`; owner-gated O3, chưa chạy thật). Giá trị khác = không khởi động.

**2.2.0 (SECB) — nửa server.** `/policy` và `/policy/nonce` **không phục vụ khi không cấu hình khoá API**
(503 `auth_off`; `/push` vẫn có chế độ dev) và có hạn mức (429 + `Retry-After`; `/policy` và `/policy/nonce` dùng chung các bucket). **2.3.0 (SECBS):** hai bucket —
theo `(app_id, client)` (`BRAIN_POLICY_RATE_RPS`/`_BURST`, mặc định 1/10) và trần chung theo `app_id`
(`BRAIN_POLICY_APP_RATE_RPS`/`_BURST`, mặc định 20/200); `client` = IP peer, hoặc khi `BRAIN_TRUST_PROXY_HOPS=N` (số nguyên 0..5, mặc định 0 = chỉ peer) là hop thứ N TÍNH TỪ PHẢI của
`X-Forwarded-For` (proxy tin cậy nối thêm vào cuối). Hop không phải IP/thiếu hop = peer + log mã `xff_invalid`
(không bao giờ ghi giá trị header); IPv6 gộp theo /64; khoá client là IP đã parse trong bảng LRU có trần; client đã hết hạn mức của chính nó không tiêu token của app. Mặc định 0 = mọi client sau cùng
một proxy chung bucket peer (hành vi 2.2.0). N quá nhỏ = mọi client chung một bucket (như mặc định); N quá lớn = khoá client do client tự khai được. N chưa kiểm chứng offline: xác nhận N bằng request thật trước khi bật. `nan`/`inf`/N ngoài 0..5/không phải số nguyên = không khởi động.

Luồng google mode: client `POST /policy/nonce` (khoá API) -> `{nonce, expires_in_s}` (32 byte url-safe, dùng một lần, gắn
app_id, TTL `BRAIN_INTEGRITY_NONCE_TTL_S` = 120) -> client xin token Play Integrity với nonce đó (classic `nonce` hoặc
standard `requestHash`) -> `GET /policy` với `X-Integrity-Token`. Server chấp nhận khi: `appRecognitionVerdict =
PLAY_RECOGNIZED`, `deviceRecognitionVerdict` có `MEETS_DEVICE_INTEGRITY`, `requestPackageName` (và `appIntegrity.packageName`)
thuộc gói đăng ký của app_id, nonce của server khớp đúng một trong hai trường và chưa dùng/hết hạn/sai app, `timestampMillis`
(chuỗi số) không cũ hơn `BRAIN_INTEGRITY_MAX_AGE_S` (300) và không ở tương lai quá 60 giây, `appIntegrity.packageName` có mặt, và — khi có khai — cert khớp, `accountDetails.appLicensingVerdict = LICENSED`
(thiếu/UNLICENSED/UNEVALUATED = từ chối; tắt bằng `BRAIN_INTEGRITY_REQUIRE_LICENSED=0`), `deviceRecognitionVerdict` phải là mảng.
Nonce bị đốt ngay lần thử đầu (kể cả sai). Lý do ra dây: `integrity_off | no_token | not_accepted | no_policy | nonce_required`.

`BRAIN_PLAY_PACKAGES="app_id:pkg[|pkg2][:certhex,certhex];…"`: google mode cần một mục cho MỌI app có khoá, nếu không thì
không khởi động. Khi khai nhiều gói (`pkg|pkg2`), server giải mã token lần lượt theo thứ tự khai trong MỘT ngân sách chung 5 s cho cả vòng (timeout mỗi pha HTTP bị kẹp theo phần còn lại) và dừng ở gói đầu giải mã được; mọi gói lỗi = từ chối (log gộp `decode_failed:<pkg>=<mã>,…`, không token/nonce; chờ credential lỗi log `SessionBackoff`, mã ra dây vẫn `not_accepted`). Cert khai bằng hex SHA-256 (như token giấy phép) và được đổi sang base64url (dạng Google trả); khai nhiều
cert để xoay chứng chỉ ký. Tên trường payload theo `googleapis@144.0.0 playintegrity/v1.d.ts`.
**Triển khai google mode: `--max-instances=1` hoặc session affinity** vì kho nonce trong process (giới hạn đã biết, D7-230): nonce cấp ở instance này không dùng được ở instance khác (`deploy_cloudrun.sh` mặc định 3). `BRAIN_INTEGRITY_MODE=fake` bị từ chối khi sink không phải `jsonl` (trừ project `local-test`). Số/bool: `nan`/`inf` bị từ chối; `BRAIN_INTEGRITY_REQUIRE_LICENSED` nhận `0|false|no|off` không phân biệt hoa thường. Mã lỗi mịn (`payload_malformed`, `cert_malformed`, `http_<status>`…) chỉ vào log, mã ra dây không đổi.
Giới hạn (`secbs-gioi-han`): kho nonce và các bucket đều trong process (mỗi instance một bảng; google mode cần `--max-instances=1` hoặc session affinity); client sau cùng một NAT hoặc proxy chưa cấu hình `BRAIN_TRUST_PROXY_HOPS` dùng chung một bucket (chỉ trần app 20/200 chặn tổng); độ dài nonce classic 16–500 byte và hạn ngạch ngày chưa đo.

## Ba ràng buộc bắt buộc, và vì sao

Tất cả đều xuất phát từ một sự thật: `DecisionLogQueue` phía SDK ghi bản ghi xuống đĩa
**trước** khi gửi và chỉ xoá khi thấy `2xx`.

| Ràng buộc | Hệ quả nếu vi phạm |
|---|---|
| **Chỉ trả 2xx sau khi đã ghi bền** | Client xoá bản ghi khỏi hàng đợi. Mất vĩnh viễn. |
| **Chịu được bản ghi trùng** | App bị kill giữa "server nhận" và "client xoá" sẽ gửi lại. |
| **Ngày báo cáo theo `client_ts_ms`, phân vùng theo `received_date`** | Hàng đợi replay nhiều ngày sau: gộp theo giờ nhận dồn tồn đọng vào sai ngày; nhưng phân vùng có hạn giữ theo ngày client làm BigQuery xoá ngay hàng tới muộn (1.7.0 F2-be). Không cần migration: chưa có bảng production (H5 chưa triển khai). |

Ràng buộc thứ nhất là lý do `JsonlSink` gọi `fsync()` chứ không chỉ `flush()`, và là lý do
lỗi ghi được **ném ra** thành 5xx thay vì nuốt đi.

Vì sao chặt hơn hẳn event ads (vốn "gửi rồi quên"): mất vài event ads chỉ làm nhiễu thống kê,
nhưng **mất decision log làm lệch suy luận nhân quả** — các lần mất không ngẫu nhiên (mạng kém
tương quan với thiết bị yếu, vùng địa lý, hành vi người chơi).

## Hai trường phải giữ nguyên vẹn bằng mọi giá

`chosen_action_probability` (propensity) và `eligible_actions` (tập ứng viên **sau** guardrail)
là hai thứ **không bao giờ tái tạo được**: cái đầu phụ thuộc trạng thái posterior tại đúng
khoảnh khắc quyết định, cái sau phụ thuộc guardrail đã chạy lúc đó. Thiếu chúng thì mọi
estimator OPE (IPS, DR, switch-DR) đều vô hiệu.

Vì vậy chúng có kiểu rõ ràng trong schema chứ không nằm lẫn trong một túi JSON tự do, nơi
chuỗi rỗng và số 0 trông giống nhau. `eligible_actions` cũng **không được sắp xếp lại** —
thứ tự giúp phát hiện lỗi phía client khi đối soát.

## Không quy đổi tiền tệ ở đây

`price_micros` + `currency_code` được lưu nguyên bản của store. Client cố ý không quy đổi
(xem doc 45 mục D.1) và server này cũng không: quy đổi thuộc về lớp phân tích, theo tỉ giá
đúng ngày giao dịch. Một bảng tỉ giá cứng trong lớp ingestion sẽ đóng băng sai số vào dữ liệu
thô, nơi không sửa lại được nữa.

## Giới hạn — đọc trước khi đưa lên production

1. **Chống trùng bằng MERGE, phải được lên lịch.** Sink ghi vào `events_staging` (hết hạn 2 ngày);
   `sql/merge_events.sql` khử trùng theo `(IFNULL(app_id,''), dedup_key)` sang `events_raw`. MERGE
   phải chạy ít nhất mỗi ngày (chưa có lịch trên môi trường thật). Hàng v1 có `user_id` rỗng mang khoá
   `seq::<seq>:<ts>` và có thể bị gộp giữa hai máy (v2 dùng `ev:<event_id>`, không bị).
2. **Rate limit trong process, không phân tán.** Token bucket theo `app_id`/`user_id` (mục `/push`); nhiều instance Cloud Run nhân
   hạn mức (deploy đặt `--max-instances 3`). G13 (`backend/tools/loadtest.py`) đo trên sink **JSONL**: p95 7,7 ms ở 50 rps; số đo
   trên bigquery-emulator chỉ là thông tin (quyết định CEO 1.7.0), không phải ngưỡng.
3. **Retention theo phân vùng:** `events_raw` 400 ngày, staging 2 ngày (`app/bq_tables.py`, kiểm
   lại từ metadata). Emulator lưu nhưng không thi hành hạn giữ — chỉ BigQuery thật xoá phân vùng.
4. **`JsonlSink` không xoay vòng file.** Nó dành cho dev và làm lưới an toàn, không phải kho
   lưu dài hạn.
5. **`deploy_cloudrun.sh` chưa chạy bao giờ.** Nó viết theo tài liệu Cloud Run, chưa được kiểm
   chứng. Lần deploy đầu nên chạy thủ công từng bước.
6. **Chỉ số DOC/62 §2.1 có hai bản cài đặt, khoá bằng test.** View SQL (`sql/views/`) là bản chạy;
   `app/ad_metrics.py` là oracle Python; `tests/test_views_parity.py` (integration) đòi Δ = 0. Sửa định
   nghĩa = sửa cả hai. `metrics.compute()` (North Star) vẫn tính bằng Python.
7. **Dashboard chưa có biểu đồ theo thời gian.** Nó hiện tổng hợp của một khoảng, chưa hiện
   xu hướng.
7b. **Chưa có kiểm định ý nghĩa thống kê.** `sufficient_data` chỉ là chặn theo cỡ mẫu. Một
   chênh lệch 3% giữa hai nhóm 500 người vẫn có thể là nhiễu, và công cụ hiện KHÔNG nói được
   điều đó.
8. **Training service chưa có.** Vòng lặp học vẫn chưa khép.
