#!/usr/bin/env bash
# Test không cần mạng cho tools/gcp_guard.sh: `gcloud`/`bq` GIẢ đứng trước trên PATH, ghi lại argv và env
# mà chúng nhận. Không có ca nào gọi gcloud/bq thật; project production speaker-c7f50 chỉ xuất hiện ở đây
# như chuỗi cần bị chặn. Thiếu script = FAIL (fail-closed), không bao giờ PASS rỗng.
# Chạy: bash tools/tests/gcp_guard_test.sh   (exit 0 = xanh). Chạy được trên bash 3.2.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
GUARD=${GCP_GUARD_SCRIPT:-$ROOT/tools/gcp_guard.sh}  # GCP_GUARD_SCRIPT: chỉ để chạy test đỏ trên bản guard cũ
PROD=speaker-c7f50
OK=test-d-56f09
TMP=$(mktemp -d "${TMPDIR:-/tmp}/gcp-guard-test.XXXXXX") || { echo "FAIL: mktemp"; exit 1; }
trap 'rm -rf "$TMP"' EXIT
total=0; bad=0

if [ ! -f "$GUARD" ]; then echo "FAIL: thiếu $GUARD (fail-closed)"; exit 1; fi

CALLS=$TMP/calls
mkdir -p "$TMP/bin"
for b in gcloud bq; do
  cat > "$TMP/bin/$b" <<'SHIM'
#!/usr/bin/env bash
# Shim: ghi một dòng mỗi lần bị gọi. Không bao giờ chạm mạng.
printf '%s argv=[%s] CORE=[%s] GOOGLE=[%s] PROMPTS=[%s] BIGQUERYRC=[%s]\n' "$(basename "$0")" "$*" \
  "${CLOUDSDK_CORE_PROJECT-unset}" "${GOOGLE_CLOUD_PROJECT-unset}" "${CLOUDSDK_CORE_DISABLE_PROMPTS-unset}" "${BIGQUERYRC-unset}" >> "$FAKE_CALLS"
exit 0
SHIM
  chmod +x "$TMP/bin/$b"
done

# run_guard [VAR=val ...] -- <args của guard>. Đặt out, rc, ncalls, call (dòng cuối shim ghi).
run_guard() {
  local envs=()
  while [ "$1" != "--" ]; do envs+=("$1"); shift; done
  shift
  : > "$CALLS"
  out=$(env -u CLOUDSDK_CORE_PROJECT -u GOOGLE_CLOUD_PROJECT -u GCLOUD_PROJECT -u CLOUDSDK_ACTIVE_CONFIG_NAME \
    -u GCP_GUARD_ALLOWED -u GCP_GUARD_OWNER_GO \
    ${envs[@]+"${envs[@]}"} PATH="$TMP/bin:$PATH" FAKE_CALLS="$CALLS" bash "$GUARD" "$@" 2>&1)
  rc=$?
  ncalls=$(wc -l < "$CALLS" | tr -d ' ')
  call=$(tail -n 1 "$CALLS")
}
check() { # $1 = mô tả, $2 = exit của phép kiểm, $3 = đầu ra của ca
  total=$((total + 1))
  if [ "$2" -eq 0 ]; then echo "ok     - $1"; else bad=$((bad + 1)); echo "NOT OK - $1"; printf '%s\n' "$3" | sed 's/^/         | /'; fi
}
has() { printf '%s\n' "$1" | grep -qF -- "$2"; }
refused() { [ "$rc" -eq 3 ] && [ "$ncalls" -eq 0 ] && has "$out" 'gcp_guard: REFUSED'; }
allowed() { [ "$rc" -eq 0 ] && [ "$ncalls" -eq 1 ] && has "$out" 'gcp_guard: ALLOWED'; }
ctx() { printf 'rc=%s ncalls=%s\n%s\ncall: %s' "$rc" "$ncalls" "$out" "$call"; }

run_guard -- gcloud run services list
check "không cờ project -> REFUSED, exit 3, 0 lệnh gọi" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project "$PROD" run services list
check "--project $PROD -> REFUSED, 0 lệnh gọi" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$PROD" ls
check "bq --project_id=$PROD -> REFUSED, 0 lệnh gọi" "$(refused; echo $?)" "$(ctx)"
run_guard CLOUDSDK_CORE_PROJECT=$OK GOOGLE_CLOUD_PROJECT=$OK -- gcloud --project="$OK" run services list
check "--project=$OK run services list -> 1 lệnh gọi, CLOUDSDK_CORE_PROJECT/GOOGLE_CLOUD_PROJECT bị gỡ, PROMPTS=1" \
  "$(allowed && has "$call" 'CORE=[unset]' && has "$call" 'GOOGLE=[unset]' && has "$call" 'PROMPTS=[1]' \
    && has "$call" "argv=[--project=$OK run services list]"; echo $?)" "$(ctx)"
run_guard -- gcloud --project="$OK" config set project x
check "config set project x -> REFUSED (sửa cấu hình toàn cục)" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project="$OK" run deploy svc --source=.
check "run deploy không GCP_GUARD_OWNER_GO -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=ledger-42 -- gcloud --project="$OK" run deploy svc --source=.
check "run deploy có GCP_GUARD_OWNER_GO + project cho phép -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=ledger-42 -- gcloud --project="$PROD" run deploy svc --source=.
check "run deploy có OWNER_GO nhưng project $PROD -> vẫn REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls
check "bq --project_id=$OK ls -> ALLOWED" "$(allowed && has "$call" "argv=[--project_id=$OK ls]"; echo $?)" "$(ctx)"
run_guard CLOUDSDK_CORE_PROJECT=$PROD -- gcloud --project="$OK" run services list
check "env CLOUDSDK_CORE_PROJECT=$PROD -> REFUSED dù cờ đúng" "$(refused; echo $?)" "$(ctx)"
run_guard CLOUDSDK_ACTIVE_CONFIG_NAME=speaker-prod -- gcloud --project="$OK" run services list
check "env CLOUDSDK_ACTIVE_CONFIG_NAME chứa 'speaker' -> REFUSED dù cờ đúng" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project="${OK}x" run services list
check "project ${OK}x (tiền tố của tên cho phép) -> REFUSED, so khớp nguyên chuỗi" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project=other-proj run services list
check "project ngoài danh sách -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project="$OK" run services describe "$PROD"
check "$PROD nằm ở argv khác (không phải cờ project) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project="$OK" --project="$OK" run services list
check "hai cờ project -> REFUSED (đúng một cờ)" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project="$OK" auth login
check "gcloud auth -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" rm -f -t ds.tbl
check "bq rm không OWNER_GO -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project="$OK" projects add-iam-policy-binding "$OK"
check "gcloud projects ... iam-policy -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_ALLOWED=other-proj -- gcloud --project=other-proj run services list
check "GCP_GUARD_ALLOWED không mở rộng được danh sách cứng -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- rm -rf "$TMP/x"
check "binary không phải gcloud|bq -> REFUSED" "$(refused; echo $?)" "$(ctx)"
mkdir -p "$TMP/noallow"; cp "$GUARD" "$TMP/noallow/gcp_guard.sh"
: > "$CALLS"; out=$(PATH="$TMP/bin:$PATH" FAKE_CALLS="$CALLS" bash "$TMP/noallow/gcp_guard.sh" gcloud --project="$OK" run services list 2>&1); rc=$?
ncalls=$(wc -l < "$CALLS" | tr -d ' '); call=
check "thiếu gcp_allowed_projects.txt -> REFUSED (fail-closed)" "$(refused; echo $?)" "$(ctx)"

# ── Review R-11: mỗi đường vòng một ca ──────────────────────────────────────────────────────────────────
G=(gcloud --project="$OK")
run_guard -- "${G[@]}" --flags-file=f.yaml run services list
check "(C1) --flags-file=f -> REFUSED (file cờ giấu project)" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --FLAGS-FILE f.yaml run services list
check "(C1) --FLAGS-FILE f (tách arg, hoa) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" --flagfile=f ls
check "(C1) bq --flagfile=f -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --verbosity info auth login
check "(I1) --verbosity info auth login -> REFUSED (động từ sau giá trị cờ)" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --verbosity info projects list
check "(I1) --verbosity info projects list -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --verbosity info billing accounts list
check "(I1) --verbosity info billing accounts list -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" config configurations create x
check "(I1) config configurations create -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" config configurations activate x
check "(I1) config configurations activate -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" iam service-accounts list
check "(I1) gcloud iam ... -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" config list
check "config list (không nằm trong danh sách lệnh) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services describe projects/123456789012/locations/us-central1/services/x
check "(I2) projects/<số project khác> trong tài nguyên -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services describe projects/$OK/locations/us-central1/services/x
check "(I2) projects/<project cho phép>/... trong tham số run -> REFUSED (cấm '/' ở tham số gcloud run)" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls other-proj:ds
check "(I2) bq ls other-proj:ds -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls --dataset_id=other-proj:ds
check "(I2) bq --dataset_id=other-proj:ds -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'SELECT 1 FROM `other-proj.ds.t`'
check "(I2) bq query FROM other-proj.ds.t -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" mk -f --table "$OK:monetization_brain.events_raw" schema/events_raw.json
check "(T10) bq mk --table không còn trong danh sách (bảng dựng bằng app.bq_setup) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --billing-project=other-proj run services list
check "(I2) --billing-project -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --impersonate-service-account=sa@other-proj.iam.gserviceaccount.com run services list
check "(I2) --impersonate-service-account -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services list --service-account=sa@other-proj.iam.gserviceaccount.com
check "(I2) service account của project khác -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services list --source-project=other-proj
check "(I2) cờ *project* lạ (--source-project) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --configuration=other run services list
check "(m3) --configuration -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services update-traffic svc --to-revisions=r1=100
check "(m1) update-traffic không OWNER_GO -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" secrets versions add brain-api-keys --data-file=-
check "(m1) secrets versions add không OWNER_GO -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=ledger-42 -- "${G[@]}" secrets versions add brain-api-keys --data-file=-
check "(m1) secrets versions add có OWNER_GO -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'DROP TABLE ds.t'
check "(m2) bq query DROP TABLE không OWNER_GO -> REFUSED" "$(refused; echo $?)" "$(ctx)"

# ── Re-review R-11 (C2, I3, n1) + danh sách lệnh cho phép ─────────────────────────────────────────────────
run_guard -- gcloud compute ssh vm -- --project="$OK"
check "(C2) gcloud compute ssh vm -- --project=... -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq ls -- --project_id="$OK"
check "(C2) bq ls -- --project_id=... -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud run services list -- --project "$OK"
check "(C2) gcloud run services list -- --project X -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services list --
check "'--' ở cuối -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" shell
check "(I3) bq shell -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" beta interactive
check "(I3) gcloud beta interactive -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" alpha interactive
check "(I3) gcloud alpha interactive -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" compute ssh vm
check "gcloud compute ssh (có cờ project hợp lệ) -> REFUSED, ngoài danh sách lệnh" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud run services list --project="$OK"
check "cờ project đứng SAU tham số vị trí -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project="$OK" ls
check "bq --project (không phải --project_id) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'SELECT 1 FROM `other-proj`.ds.t'
check "(n1) bq query FROM other-proj.ds.t (tách backtick) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" storage ls gs://some-bucket
check "gcloud storage ls gs://bucket -> REFUSED (ngoài danh sách lệnh)" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" show "$OK:monetization_brain.events_raw"
check "bq show $OK:dataset.table -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services logs read svc --region=us-central1 --limit=100
check "run services logs read (RUNBOOK) -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" logging read 'severity>=ERROR' --limit=50
check "logging read (RUNBOOK) -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run revisions list --service=svc --region=us-central1
check "run revisions list (RUNBOOK) -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"

# ── Re-review 2 (C3, C4, I4) + dạng cố định ──────────────────────────────────────────────────────────────
run_guard -- "${G[@]}" --format run --account services --trace-token list auth login
check "(C3) cờ ăn giá trị chen trước tiền tố (auth login lọt) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --format run --account services --trace-token list storage cp gs://other-bucket/x .
check "(C3) cờ chen + storage cp gs://... -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" --format logging --account read compute ssh vm
check "(C3) cờ chen + compute ssh -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" --location ls shell
check "(C3) bq --location ls shell -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- gcloud --project "$OK" run services list
check "cờ project dạng tách rời (--project X) -> REFUSED, chỉ nhận '='" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services list --region us-central1
check "đuôi: giá trị tách rời (--region X) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services list --region=us-central1 --format=json
check "đuôi: --region=... --format=... hợp lệ -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services list --unknown=1
check "đuôi: cờ lạ -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services describe a b
check "đuôi: thừa tham số vị trí -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services describe
check "đuôi: thiếu tham số vị trí -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query "EXECUTE IMMEDIATE CONCAT('SELECT * FROM speaker-c7f', '50.ds.t')"
check "(C4) bq query EXECUTE IMMEDIATE CONCAT(...) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query "DECLARE p STRING DEFAULT CONCAT('speaker-c7','f50');  execute   immediate FORMAT('x %s', p)"
check "(C4) bq query DECLARE + execute immediate (khoảng trắng/hoa thường) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query
check "(C4) bq query không có SQL (đọc stdin) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query --external_table_definition=t::gs://b/x 'SELECT 1'
check "(C4) bq query --external_table_definition -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query --flagfile=f 'SELECT 1'
check "(C4) bq query --flagfile -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'SELECT 1; SELECT 2'
check "(C4) bq query nhiều câu lệnh (;) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'EXPORT DATA OPTIONS(uri="gs://b/*") AS SELECT 1'
check "(C4) bq query EXPORT DATA -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'LOAD DATA INTO ds.t FROM FILES(format="CSV", uris=["x"])'
check "(C4) bq query LOAD DATA -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'CREATE EXTERNAL TABLE ds.t OPTIONS()'
check "(C4) bq query CREATE EXTERNAL -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query "SELECT * FROM EXTERNAL_QUERY('c', 'select 1')"
check "(C4) bq query external_query -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query "begin select 1; end"
check "(C4) bq query BEGIN ... -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query "CALL ds.proc()"
check "(C4) bq query CALL -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'SELECT COUNT(*) FROM `test-d-56f09.monetization_brain.events_raw`'
check "bq query SELECT thuần -> REFUSED (query không nằm trong danh sách)" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" logging read 'severity>=ERROR' --organization=123
check "(I4) logging read --organization -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" logging read 'severity>=ERROR' --folder=123
check "(I4) logging read --folder -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" logging read 'severity>=ERROR' --billing-account=AAA
check "(I4) logging read --billing-account -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --service-account=123-compute@developer.gserviceaccount.com
check "(m-a) run deploy --service-account của project khác -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --add-cloudsql-instances=other-proj:us-central1:db
check "(m-b) run deploy --add-cloudsql-instances -> REFUSED (không có trong danh sách)" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --source=gs://other-bucket/src.zip
check "(m-c) run deploy --source=gs://... -> REFUSED (--source chỉ nhận '.')" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --service-account="sa@$OK.iam.gserviceaccount.com" \
  --max-instances=3 --set-env-vars="BRAIN_SINK=bigquery,BRAIN_BQ_DATASET=d" --set-secrets="BRAIN_API_KEYS=brain-api-keys:latest" --no-allow-unauthenticated
check "run deploy đúng dạng deploy_cloudrun.sh + OWNER_GO -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"

# ── Re-review 3 (C5, C6, C7, n-a): bq query bị bỏ khỏi danh sách; tham số bq đúng dạng ─────────────────────────
run_guard -- bq --project_id="$OK" ls other-proj:
check "(C5) bq ls other-proj: -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls "$OK:"
check "(C5) bq ls $OK: (dataset rỗng) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls other-proj:ds.t.x
check "(C5) bq ls other-proj:ds.t.x -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls monetization_brain
check "bq ls <dataset trần> -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls "$OK:monetization_brain"
check "bq ls $OK:dataset -> ALLOWED" "$(allowed; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" mk -f --table "$OK:monetization_brain.events_raw" ../schema.json
check "bq mk với đường dẫn schema chứa '..' -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'EXECUTE/**/IMMEDIATE "SELECT 1"'
check "(C6) bq query EXECUTE/**/IMMEDIATE -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query $'EXECUTE -- c\nIMMEDIATE "SELECT 1"'
check "(C6) bq query EXECUTE -- c<newline>IMMEDIATE -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'SELECT * FROM `other-proj` . ds . t'
check "(C7) bq query other-proj . ds . t -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'SELECT * FROM `other-proj`/* x */.ds.t'
check "(C7) bq query other-proj/* x */.ds.t -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" query 'SELECT * FROM other-proj . ds . t'
check "(C7) bq query other-proj . ds . t -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- "${G[@]}" run services describe namespaces/123456789012/services/x
check "(n-a) run services describe namespaces/<n>/services/x ('/') -> REFUSED" "$(refused; echo $?)" "$(ctx)"

# ── Re-review 4 (C8): tên trần bq là MỘT định danh; "X.Y" không ':' bị bq đọc là project X, dataset Y ──────────
run_guard -- bq --project_id="$OK" mk -f --dataset otherproj.ds
check "(C8) bq mk --dataset otherproj.ds -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" mk -f --dataset other-proj.json
check "(C8) bq mk --dataset other-proj.json -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls otherproj.ds
check "(C8) bq ls otherproj.ds -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" mk -f --dataset schema/events_raw.json
check "(C8) json ở vị trí 1 của bq mk (không phải ô schema) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls schema/events_raw.json
check "(C8) json ở bq ls -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" mk -f --table "$OK:monetization_brain.events_raw" events_raw.json
check "(C8) schema .json không có '/' ở vị trí 2 -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" mk -f --dataset "$OK:monetization_brain"
check "(T10) bq mk --dataset $OK:ds không còn trong danh sách -> REFUSED" "$(refused; echo $?)" "$(ctx)"

# ── 1.8.0 T14 (R-11, R5): --set-env-vars chỉ KEY=VALUE với KEY thuộc danh sách khoá ─────────────────────────
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars="BRAIN_SINK=bigquery,EVIL_KEY=1"
check "(R5) run deploy --set-env-vars có khoá lạ EVIL_KEY -> REFUSED" "$(refused; echo $?)" "$(ctx)"

# ── 1.8.0 T3 (G1): bq chạy với BIGQUERYRC=/dev/null, không đọc ~/.bigqueryrc (project/dataset mặc định của máy dev) ─
run_guard BIGQUERYRC=/tmp/evil-bigqueryrc -- bq --project_id="$OK" ls
check "(G1) gcp_guard: bq chay voi BIGQUERYRC=/dev/null (env BIGQUERYRC của máy bị ghi đè)" \
  "$(allowed && has "$call" 'BIGQUERYRC=[/dev/null]'; echo $?)" "$(ctx)"

# ── 1.8.0 T3 (D19): --set-env-vars dạng phân cách của gcloud ^|^KEY=VALUE|KEY=VALUE (chỉ cờ này), để BRAIN_FX_RATES mang dấu phẩy ──
FXENV='^|^BRAIN_SINK=bigquery|BRAIN_BQ_PROJECT='"$OK"'|BRAIN_FX_RATES=USD:1,VND:25000'
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars="$FXENV"
check "(D19) --set-env-vars=^|^K=V|K=V (BRAIN_FX_RATES có dấu phẩy) + BRAIN_BQ_PROJECT đúng -> ALLOWED, argv nguyên vẹn" \
  "$(allowed && has "$call" "--set-env-vars=$FXENV"; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^BRAIN_SINK=bigquery|BRAIN_FX_RATES=USD:1^x'
check "(D19) giá trị tự do chứa '^' trong dạng ^|^ -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^;^BRAIN_SINK=bigquery;EVIL_KEY=1'
check "(D19) dấu phân cách khác '|' (^;^) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^BRAIN_SINK=bigquery|EVIL_KEY=1'
check "(D19) dạng ^|^ có khoá lạ EVIL_KEY -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^BRAIN_BQ_PROJECT=other-proj|BRAIN_SINK=bigquery'
check "(D19) dạng ^|^ có BRAIN_BQ_PROJECT khác project cho phép -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^'
check "(D19) ^|^ trần, không KEY=VALUE -> REFUSED" "$(refused; echo $?)" "$(ctx)"

# ── 1.8.0 T3 (D23, R-3 I1): ký tự điều khiển trong argv bị từ chối trước mọi phân tích ────────────────────────
NL=$'\n'; CR=$'\r'; TAB=$'\t'
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars="^|^BRAIN_SINK=bigquery${NL}|EVIL_KEY=1|BRAIN_BQ_PROJECT=other-proj"
check "(I1) xuống dòng trong --set-env-vars dạng ^|^ (payload của reviewer) -> REFUSED, 0 lệnh gọi" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars="BRAIN_SINK=bigquery${NL},EVIL_KEY=1,BRAIN_BQ_PROJECT=other-proj"
check "(I1) xuống dòng trong --set-env-vars dạng dấu phẩy -> REFUSED, 0 lệnh gọi" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region="us-central1${CR}" --source=.
check "(I1) \\r trong --region= -> REFUSED, 0 lệnh gọi" "$(refused; echo $?)" "$(ctx)"
run_guard -- bq --project_id="$OK" ls "ds${TAB}x"
check "(I1) tab trong tham số vị trí bq -> REFUSED (mọi lệnh, mọi cờ)" "$(refused; echo $?)" "$(ctx)"

# ── 1.8.0 T3 (D23, R-3 m1): KEY ^[A-Z][A-Z0-9_]*$; đoạn rỗng (đầu/cuối/giữa) bị từ chối ─────────────────────────
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^BRAIN_SINK BRAIN_BQ_PROJECT=other-proj'
check "(m1) KEY có dấu cách -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^brain_sink=x'
check "(m1) KEY chữ thường -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^BRAIN_SINK=bigquery|'
check "(m1) '|' cuối (đoạn rỗng) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='^|^BRAIN_SINK=bigquery||BRAIN_BQ_DATASET=d'
check "(m1) '||' giữa (đoạn rỗng) -> REFUSED" "$(refused; echo $?)" "$(ctx)"
run_guard GCP_GUARD_OWNER_GO=l1 -- "${G[@]}" run deploy svc --region=us-central1 --source=. --set-env-vars='BRAIN_SINK=bigquery,'
check "(m1) ',' cuối dạng dấu phẩy -> REFUSED" "$(refused; echo $?)" "$(ctx)"

echo
if [ "$bad" -eq 0 ]; then echo "PASS $total/$total"; exit 0; fi
echo "FAIL $bad/$total"
exit 1
