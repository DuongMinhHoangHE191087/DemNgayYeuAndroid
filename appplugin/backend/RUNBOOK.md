# Runbook backend Monetization Brain

Mọi lệnh `gcloud`/`bq` dưới đây đi qua `tools/gcp_guard.sh` (viết `G=tools/gcp_guard.sh` từ gốc repo). Guard bắt buộc đúng
một cờ project tường minh, và chỉ nhận project trong `tools/gcp_allowed_projects.txt` (hiện chỉ `test-d-56f09`). **Không bao
giờ** gọi `gcloud`/`bq` trực tiếp: máy dev có thể có project mặc định là production (`speaker-c7f50`). Project production không
bao giờ được đưa vào tài liệu này như một mục tiêu.

Lệnh đánh dấu **[CHỦ]** chỉ chủ dự án chạy, hoặc controller sau khi có go bằng văn bản của chủ; đặt
`GCP_GUARD_OWNER_GO=<id dòng ledger chứa go>` cho đúng lệnh đó (guard từ chối nếu thiếu, và từ chối mọi project ngoài danh sách
dù có go). Tài liệu này không tự thực thi gì.

## 0. Điều kiện tiên quyết về IAM (đọc trước mọi mục khác)

Guard chỉ là lớp phòng thủ thứ hai; **ranh giới thật là IAM**. Thông tin đăng nhập dùng qua guard phải **không có vai trò IAM nào**
trên project production: dùng một service account tạo **chỉ trong `test-d-56f09`** (cấu hình gcloud/ADC riêng, chỉ thấy project đó),
không dùng tài khoản cá nhân của chủ. Kiểm tra trước lần deploy đầu, **chỉ chủ dự án chạy** (guard cố ý không cho lệnh này đi qua):
`gcloud projects get-iam-policy <project-production>` trong shell của chủ phải **không có binding nào** cho service account đó.
Guard chỉ nhận lệnh dạng cố định `<tool> --project=<id>` + tiền tố lệnh trong danh sách + cờ dạng `--tên=giá-trị` (không có `--`,
không đọc SQL từ stdin, không `EXECUTE IMMEDIATE`); gì ngoài danh sách thì thêm có chủ ý vào `spec()` của `tools/gcp_guard.sh`.

Đặt sẵn (chạy từ gốc repo): `export G=tools/gcp_guard.sh P=test-d-56f09 REGION=us-central1 SERVICE=brain-ingest`.

Lưu ý: `deploy_cloudrun.sh` chỉ cấp `BRAIN_API_KEYS`; app đọc biến này từ Task 9, nên chỉ deploy sau khi Task 9 đã vào nhánh.

## 1. Deploy  **[CHỦ]**

Điều kiện: `BRAIN_SERVICE_ACCOUNT` (chỉ BigQuery dataEditor trên dataset), secret `brain-api-keys` (dạng `app_id:key,...`) trong
Secret Manager.

```
export BRAIN_BQ_PROJECT=$P BRAIN_SERVICE_ACCOUNT=<sa>@$P.iam.gserviceaccount.com
GCP_GUARD_OWNER_GO=<ledger-id> bash backend/deploy_cloudrun.sh   # bước `gcloud run deploy` — chỉ chủ / controller sau go bằng văn bản
```

Script giữ `--no-allow-unauthenticated`, `--max-instances 3`, khoá lấy từ `--set-secrets`. Kiểm sau deploy:
`$G gcloud --project=$P run services describe $SERVICE --region=$REGION --format='value(status.url)'` rồi gọi `/health`
kèm token định danh (`auth:true` nghĩa là khoá đã nạp; `auth:false` = chế độ dev, không chấp nhận trên môi trường thật).

**Google mode (`BRAIN_INTEGRITY_MODE=google`, sau O3):** kho nonce và hạn mức `/policy` nằm trong process; dùng `--max-instances=1` hoặc session affinity; kho nonce chung **không** làm ở 2.3.0 (D7-230: chỉ ghi giới hạn, xem `secbs-gioi-han` trong CHANGELOG) nên yêu cầu này còn hiệu lực sau 2.3.0, nếu không ~2/3 cặp nonce -> policy rơi vào hai instance khác nhau và bị từ chối.

## 2. Rollback  **[CHỦ]**

```
$G gcloud --project=$P run revisions list --service=$SERVICE --region=$REGION
GCP_GUARD_OWNER_GO=<ledger-id> $G gcloud --project=$P run services update-traffic $SERVICE --region=$REGION --to-revisions=<revision-truoc>=100
```

`update-traffic`, `run services update|replace`, `secrets versions add|disable|destroy` và `keys create` cũng cần `GCP_GUARD_OWNER_GO`.

## 3. Xoay khoá API  **[CHỦ]**

1. Tạo phiên bản secret mới chứa cả khoá cũ và mới (`app_id:key_cu,app_id:key_moi`):
   `printf '%s' "$KEYS" | GCP_GUARD_OWNER_GO=<ledger-id> $G gcloud --project=$P secrets versions add brain-api-keys --data-file=-`
2. Deploy lại (mục 1) để revision nạp `brain-api-keys:latest`.
3. Cập nhật khoá ở app tiêu thụ; xác nhận log JSON không còn request dùng khoá cũ (trường `app_id` + `status`).
4. Thêm phiên bản chỉ còn khoá mới, deploy lại, vô hiệu hoá phiên bản cũ:
   `GCP_GUARD_OWNER_GO=<ledger-id> $G gcloud --project=$P secrets versions disable <n> --secret=brain-api-keys`

Không in khoá ra terminal/log/CI; khoá không bao giờ nằm trong tham số dòng lệnh của script repo.

## 4. Cảnh báo ngân sách

Billing thuộc chủ dự án. `gcloud billing` và `gcloud projects` bị guard **luôn** từ chối; tạo/sửa budget alert qua console của
chủ (ngưỡng đề xuất 50/90/100 %, gửi email chủ). Dự án demo `test-d-56f09` không có billing: mọi bước ghi thật vào BigQuery ở
đó là việc của chủ.

## 5. Dựng bảng BigQuery  **[CHỦ]** (với BigQuery thật)

`backend/app/bq_setup.py` đi bằng Python client, không qua `bq` (guard không nhận `bq mk`). Nó tạo-nếu-chưa-có 6 bảng đặc tả
ở `backend/app/bq_tables.py` (`events_staging` hết hạn 2 ngày, `events_raw` 400 ngày, `country_tier`, `fx_daily`,
`admob_report`, `subscription_events` 400 ngày của 1.8.0), đọc lại hạn giữ và danh sách cột từ metadata (sai = dừng),
seed `country_tier` khi bảng rỗng, rồi `CREATE OR REPLACE VIEW`
8 view `backend/sql/views/`. `events_staging`/`events_raw` phân vùng theo `received_date` (ngày UTC server nhận; theo ngày
client thì BigQuery xoá ngay hàng replay tới muộn vào phân vùng đã hết hạn) — không cần migration vì chưa có bảng production
(H5 chưa triển khai). Không bao giờ xoá hay tạo lại bảng. Luật đích giống guard: từ chối production (theo tên project
hoặc env gcloud), project ngoài `tools/gcp_allowed_projects.txt` ∩ danh sách cứng, và thiếu `GCP_GUARD_OWNER_GO`.

```
(cd backend && GCP_GUARD_OWNER_GO=<ledger-id> .venv/bin/python -m app.bq_setup --project=$P)
```

Bảng tạo bởi bản trước mà thiếu cột (vd. 7 cột v2 của `events_*` từ 1.8.0 T12): `bq_setup` dừng TRƯỚC view, rc 1,
`bq_setup: FAIL … thiếu N cột (…)` kèm đúng câu `ALTER TABLE … ADD COLUMN IF NOT EXISTS …` cho từng bảng. Nó không tự
ALTER (R-4: chỉ tạo-nếu-chưa-có); chủ dự án chạy câu đó (chỉ thêm cột, không mất dữ liệu) trong console rồi chạy lại.

Stack cục bộ (không cần go; chỉ dưới khoá `docker`): `docker compose -f backend/docker-compose.yml up -d --build` rồi
`(cd backend && .venv/bin/python -m app.bq_setup --project=local-test --endpoint=http://127.0.0.1:19050)`.

Cửa sổ E-BE: `pytest` (test `test_z_health_bq_down`) tắt rồi khởi động lại emulator, và emulator giữ dữ liệu trong volume nên lần
nạp kế tiếp cộng dồn lên dữ liệu cũ (MERGE vẫn khử trùng, nhưng parity/số hàng lệch fixture). Sau mỗi lượt `pytest` mà còn
cần `tools/stack_load.py`: `docker compose -f backend/docker-compose.yml down -v && docker compose -f backend/docker-compose.yml up -d`
(dựng lại `bq_setup`), rồi mới nạp (2.2.0 BE3; Task R #16).

MERGE staging -> raw (`backend/sql/merge_events.sql`, khử trùng theo `(IFNULL(app_id,''), dedup_key)`) phải chạy ít nhất mỗi
ngày vì staging hết hạn sau 2 ngày. Cục bộ: `tools/stack_load.py`. Trên BigQuery thật chưa có lịch (guard không cho `bq query`):
chủ dự án tạo scheduled query trong console từ đúng file đó — mục mở cho H5.

## 5b. RTDN `POST /rtdn`  **[CHỦ]** cho production (H7b)

Cục bộ/stack thử: bearer tĩnh `BRAIN_RTDN_TOKEN` (>= 16 ký tự; `Authorization: Bearer <token>`, so bằng
`hmac.compare_digest`); không đặt = `/rtdn` trả 503 `not_configured`, không bao giờ mở không xác thực. Fixture:
`backend/tests/fixtures/rtdn/*.json` (envelope Pub/Sub push; `push_iap_purchase.json` là lượt mua của chủ token), vd.
`curl -s -X POST -H "Authorization: Bearer $BRAIN_RTDN_TOKEN" -H 'Content-Type: application/json'
--data @backend/tests/fixtures/rtdn/sub_06_in_grace_period.json http://127.0.0.1:<port>/rtdn`.
**Production dùng OIDC, không dùng bearer tĩnh:** Pub/Sub push subscription có `--push-auth-service-account`, backend kiểm
token OIDC (chữ ký Google, `aud`, `email` của service account) — chưa có trong 1.8.0 (H7b, OWNER-NOTES #4), cùng topic
RTDN trong Play Console và `subscriptionsv2` (`HttpSubscriptionsV2` hiện trả 503). Nếu buộc phải dùng bearer tĩnh trên
Cloud Run, truyền bằng `--set-secrets` (Secret Manager), không bằng `--set-env-vars` (guard D19 chỉ nhận danh sách khoá của
`deploy_cloudrun.sh`).

**Cache `signals` (H5):** mỗi instance giữ cache riêng 60 s theo `(app_id, user_id)` để `/push` không tạo một job BigQuery cho mỗi event (R-13 m1). `/rtdn` chỉ xoá mục ở instance nhận nó, nên với nhiều instance Cloud Run một signal có thể trễ tối đa 60 s ở instance khác — chấp nhận được; đừng đặt `--min-instances`/tải để coi đó là thời gian thực.

## 6. Xem log

```
$G gcloud --project=$P run services logs read $SERVICE --region=$REGION --limit=100
```

Mỗi request một dòng JSON: `severity`, `request_id`, `app_id`, `accepted`, `rejected`, `status`. Lọc lỗi:
`$G gcloud --project=$P logging read 'resource.type="cloud_run_revision" AND severity>=ERROR' --limit=50`.
Khoá API không bao giờ xuất hiện trong log; nếu thấy, coi là sự cố bảo mật và xoay khoá (mục 3).

## 7. Kiểm nhanh guard (không cần mạng)

`bash tools/tests/gcp_guard_test.sh` — gcloud/bq giả trên PATH; CI chạy bước này trong job `verify`.
